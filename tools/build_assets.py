"""Builds all BetterSound audio + sounds.json + CREDITS.md from raw_sources/ (user-approved CC0 downloads).

    python -I tools/build_assets.py

Output: common/resources/assets/{minecraft,bettersound}/sounds.json, assets/bettersound/sounds/**.ogg, CREDITS.md
"""
from __future__ import annotations

import json
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bsaudio as A  # noqa: E402
import vanilla as V  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw_sources"
BSB = RAW / "bigsoundbank"
ASSETS = ROOT / "common" / "resources" / "assets"
OUT = ASSETS / "bettersound" / "sounds"
KI = "kenney_impact/x/kenney-impact-sounds-for-godot-main/addons/kenney impact sounds"

used_sources: dict[str, set[str]] = {}   # license key -> files


def note_use(path: Path) -> None:
    rel = path.relative_to(RAW)
    used_sources.setdefault(rel.parts[0], set()).add(str(rel).replace("\\", "/"))


def files(*patterns: str) -> list[Path]:
    out: list[Path] = []
    for p in patterns:
        out += sorted(RAW.glob(p))
    return [f for f in out if f.suffix.lower() in (".ogg", ".wav", ".flac", ".aif", ".aiff", ".mp3")]


def bsb(cat: str, *ids: str) -> list[Path]:
    return [BSB / cat / f"{i}.ogg" for i in ids if (BSB / cat / f"{i}.ogg").exists()]


# ----------------------------------------------------------------------------------------------
# Hit extraction
# ----------------------------------------------------------------------------------------------
@dataclass
class HitSpec:
    split: list[Path] = field(default_factory=list)    # long recordings -> cut into individual hits
    whole: list[Path] = field(default_factory=list)    # one file = one hit
    n: int = 10
    hp: float = 90.0            # high-pass (rumble/handling noise)
    lp: float = 0.0             # optional low-pass
    shelf_db: float = 2.5       # presence boost above 3 kHz ("crisp")
    max_ms: float = 650.0
    min_ms: float = 70.0
    rise_db: float = 9.0
    gap_ms: float = 220.0
    tail_db: float = -40.0


def hit_candidates(spec: HitSpec) -> list[tuple[float, np.ndarray]]:
    cands: list[tuple[float, np.ndarray]] = []
    for f in spec.split:
        x = A.load(f)
        x = A.highpass(x, spec.hp)
        env = A.envelope(x)
        floor = float(np.percentile(env, 15)) + 1e-9
        hits = A.split_hits(x, min_gap_ms=spec.gap_ms, rise_db=spec.rise_db, max_len_ms=spec.max_ms,
                            min_len_ms=spec.min_ms, tail_db=spec.tail_db)
        for h in hits:
            snr = A.db(float(np.abs(h).max())) - A.db(floor)
            cands.append((snr, h))
        if hits:
            note_use(f)
    for f in spec.whole:
        x = A.highpass(A.load(f), spec.hp)
        x = A.trim(x, -45.0)
        if len(x) > int(A.SR * spec.max_ms / 1000):
            x = x[: int(A.SR * spec.max_ms / 1000)]
        if len(x) >= int(A.SR * spec.min_ms / 1000):
            cands.append((30.0, x))
        note_use(f)
    return cands


def build_hits(name: str, spec: HitSpec, target_db: float) -> list[str]:
    cands = hit_candidates(spec)
    if not cands:
        print(f"  !! {name}: no source audio")
        return []
    # Best signal-to-noise first, keep consistent loudness, spread across sources.
    cands.sort(key=lambda c: -c[0])
    top = [h for snr, h in cands if snr >= 18.0] or [h for _, h in cands]
    top = top[: max(spec.n * 3, spec.n)]
    picks = A.pick_variants(top, spec.n)
    out = []
    folder = OUT / name
    if folder.exists():
        shutil.rmtree(folder)
    for i, h in enumerate(picks, 1):
        if spec.lp:
            h = A.lowpass(h, spec.lp)
        h = A.high_shelf(h, 3000.0, spec.shelf_db)
        h = A.fade(h, 2.0, min(40.0, len(h) / A.SR * 1000 * 0.3))
        h = A.normalize_to(h, target_db)
        A.write_ogg(folder / f"{i}.ogg", h)
        out.append(f"bettersound:{name}/{i}")
    print(f"  {name}: {len(out)} variants (from {len(cands)} candidates)")
    return out


# ----------------------------------------------------------------------------------------------
# Loops & one-shots
# ----------------------------------------------------------------------------------------------
def build_loop(name: str, sources: list[Path], length_s: float = 45.0, target_db: float = -24.0,
               hp: float = 40.0, lp: float = 0.0, stereo: bool = True) -> str | None:
    parts = []
    for f in sources:
        x = A.load(f, mono=not stereo)
        x = A.highpass(x, hp)
        if lp:
            x = A.lowpass(x, lp)
        # skip the first/last seconds (handling noise), normalise each part so they blend
        trim_s = int(min(3.0, len(x) / A.SR * 0.1) * A.SR)
        x = x[trim_s: len(x) - trim_s] if len(x) > 4 * trim_s else x
        parts.append(A.normalize_to(x, target_db))
        note_use(f)
    if not parts:
        print(f"  !! loop {name}: no source audio")
        return None
    x = A.concat_xfade(parts, 2.5) if len(parts) > 1 else parts[0]
    length = min(length_s * len(parts), len(x) / A.SR - 4.0, 150.0)
    loop = A.make_loop(x, length, xfade_s=3.0)
    loop = A.normalize_to(loop, target_db, max_peak_db=-3.0)
    A.write_ogg(OUT / "ambient" / "loop" / f"{name}.ogg", loop, quality=0.45)
    print(f"  loop {name}: {len(loop) / A.SR:.1f}s {'stereo' if loop.ndim == 2 else 'mono'}")
    return f"bettersound:ambient/loop/{name}"


def build_snippets(name: str, sources: list[Path], n: int, seg_s: float, target_db: float, hp: float = 200.0) -> list[str]:
    """Pick the n loudest distinct windows (e.g. bird calls) from long recordings as mono one-shots."""
    cands = []
    for f in sources:
        x = A.highpass(A.load(f), hp)
        w = int(seg_s * A.SR)
        hop = w // 2
        for s in range(0, max(1, len(x) - w), hop):
            seg = x[s:s + w]
            env = A.envelope(seg, win_ms=20)
            contrast = A.db(float(env.max())) - A.db(float(np.median(env)) + 1e-9)
            cands.append((contrast, seg))
        note_use(f)
    cands.sort(key=lambda c: -c[0])
    out = []
    folder = OUT / "ambient" / name
    if folder.exists():
        shutil.rmtree(folder)
    for i, (_, seg) in enumerate(cands[:n], 1):
        seg = A.fade(seg, 300.0, 600.0)
        seg = A.normalize_to(seg, target_db, max_peak_db=-2.0)
        A.write_ogg(folder / f"{i}.ogg", seg)
        out.append(f"bettersound:ambient/{name}/{i}")
    print(f"  {name}: {len(out)} snippets")
    return out


# ----------------------------------------------------------------------------------------------
# Recipe
# ----------------------------------------------------------------------------------------------
def vdb(event: str, default: float) -> float:
    v = V.loudness_db(event)
    return v if v is not None else default


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    mc: dict[str, dict] = {}
    bs: dict[str, dict] = {}

    def entries(names: list[str], pitch: float = 1.0, volume: float = 1.0, **extra) -> list:
        out = []
        for n in names:
            e = {"name": n}
            if abs(pitch - 1.0) > 1e-3:
                e["pitch"] = round(pitch, 3)
            if abs(volume - 1.0) > 1e-3:
                e["volume"] = round(volume, 3)
            e.update(extra)
            out.append(e)
        return out

    def override(event: str, names: list[str], pitch: float = 1.0, volume: float = 1.0) -> None:
        if not names:
            return
        d = {"replace": True, "sounds": entries(names, pitch, volume)}
        sub = V.subtitle(event)
        if sub:
            d["subtitle"] = sub
        mc[event] = d

    print("Footsteps")
    step = {
        "wood": build_hits("step/wood", HitSpec(
            split=bsb("step_wood", "1515", "1516", "1517", "1518", "0376", "0165"),
            whole=files(f"{KI}/footstep_wood_*.ogg", "kdd_steps/x/wood*.ogg"), n=12, hp=110, shelf_db=4.5, max_ms=520, min_ms=140), vdb("block.wood.step", -19.0)),
        "stone": build_hits("step/stone", HitSpec(
            split=bsb("step_stone", "0514", "1318", "1319", "0606", "0179"),
            whole=files(f"{KI}/footstep_concrete_*.ogg", "fantozzi_steps/x/*/flac/*Stone*.flac"), n=12, shelf_db=3.5, max_ms=480, min_ms=140),
            vdb("block.stone.step", -22.0)),
        "grass": build_hits("step/grass", HitSpec(
            split=bsb("step_grass", "1253", "1254", "0854", "0855"),
            whole=files(f"{KI}/footstep_grass_*.ogg"), n=12, lp=9000, shelf_db=0.5, max_ms=600, rise_db=7.0, min_ms=140),
            vdb("block.grass.step", -30.0) + 4.0),
        "gravel": build_hits("step/gravel", HitSpec(
            split=bsb("step_gravel", "0510", "1117", "3202", "3216"),
            whole=files("snow_gravel_steps/x/*/*gravel_0*.flac", "kdd_steps/x/gravel*.ogg"), n=12, shelf_db=2.0, max_ms=600, rise_db=7.0, min_ms=140),
            vdb("block.gravel.step", -24.0)),
        "sand": build_hits("step/sand", HitSpec(
            whole=files("fantozzi_steps/x/*/flac/*Sand*.flac"), n=6, shelf_db=1.0, max_ms=600),
            vdb("block.sand.step", -26.0)),
        "snow": build_hits("step/snow", HitSpec(
            split=bsb("step_snow", "3363", "0208", "2890"),
            whole=files(f"{KI}/footstep_snow_*.ogg"), n=12, shelf_db=1.5, max_ms=600, rise_db=7.0, min_ms=140),
            vdb("block.snow.step", -27.0)),
        "wool": build_hits("step/wool", HitSpec(
            whole=files(f"{KI}/footstep_carpet_*.ogg"), n=5, shelf_db=0.0, max_ms=350),
            vdb("block.wool.step", -19.0)),
        "mud": build_hits("step/mud", HitSpec(
            split=bsb("step_mud", "0495", "1837"), whole=files("kdd_steps/x/mud*.ogg"), n=8, shelf_db=0.0, max_ms=650, rise_db=7.0),
            vdb("block.mud.step", -24.0)),
        "metal": build_hits("step/metal", HitSpec(
            whole=files("metal_steps/x/*.wav", "metal_footstep_*/*.ogg"), n=8, shelf_db=1.5, max_ms=500),
            vdb("block.metal.step", -20.0)),
        "ladder": build_hits("step/ladder", HitSpec(
            split=bsb("wood_creak", "3212", "3213", "3214", "3215"), n=8, shelf_db=1.0, max_ms=600, rise_db=8.0),
            vdb("block.ladder.step", -20.0)),
        "leaves": build_hits("step/leaves", HitSpec(
            split=bsb("leaves", "0137", "2889"), whole=files("kdd_steps/x/leaves*.ogg"), n=8, shelf_db=1.0, max_ms=600, rise_db=7.0),
            vdb("block.grass.step", -30.0) + 4.0),
    }

    print("Place / break")
    dig = {
        "wood": build_hits("dig/wood", HitSpec(
            split=bsb("impacts", "1841", "1842", "0536", "0705"),
            whole=files(f"{KI}/impact_plank_medium_*.ogg", f"{KI}/impact_wood_heavy_*.ogg"), n=10, max_ms=550, shelf_db=2.0),
            vdb("block.wood.break", -12.0)),
        "stone": build_hits("dig/stone", HitSpec(
            split=bsb("impacts", "1835", "1836", "1022"),
            whole=files(f"{KI}/impact_mining_*.ogg"), n=10, max_ms=550, shelf_db=2.0), vdb("block.stone.break", -16.0)),
        "grass": build_hits("dig/grass", HitSpec(
            split=bsb("impacts", "1839", "1840", "1305"), whole=files(f"{KI}/impact_soft_*.ogg"), n=8, max_ms=600, rise_db=7.0),
            vdb("block.grass.break", -18.0)),
        "glass": build_hits("dig/glass", HitSpec(
            split=bsb("impacts", "0148", "0771", "1643"), whole=files(f"{KI}/impact_glass_heavy_*.ogg"), n=8, max_ms=900, hp=150),
            vdb("block.glass.break", -14.0)),
        "metal": build_hits("dig/metal", HitSpec(
            split=bsb("impacts", "1771"), whole=files(f"{KI}/impact_metal_heavy_*.ogg"), n=8, max_ms=700),
            vdb("block.metal.break", -16.0)),
    }

    print("Doors / containers")
    door_open = build_hits("door/wood_open", HitSpec(
        whole=bsb("door_wood", "1702", "1703") + files("kenney_rpg/x/OGG/doorOpen_*.ogg"), n=5, max_ms=1100, hp=60, shelf_db=1.0),
        vdb("block.wooden_door.open", -16.0))
    door_close = build_hits("door/wood_close", HitSpec(
        whole=bsb("door_wood", "1704", "1705", "1706") + files("kenney_rpg/x/OGG/doorClose_*.ogg"), n=6, max_ms=900, hp=60, shelf_db=1.0),
        vdb("block.wooden_door.close", -16.0))
    trap_open = build_hits("door/trapdoor_open", HitSpec(
        whole=bsb("trapdoor_chest", "0538", "0305", "1499"), n=3, max_ms=900, hp=70),
        vdb("block.wooden_trapdoor.open", -16.0))
    trap_close = build_hits("door/trapdoor_close", HitSpec(
        whole=bsb("trapdoor_chest", "0539", "0046", "1500"), n=3, max_ms=800, hp=70),
        vdb("block.wooden_trapdoor.close", -16.0))
    chest_open = build_hits("container/chest_open", HitSpec(
        whole=files("open_chest/*.wav") + bsb("trapdoor_chest", "0046"), n=2, max_ms=1300, hp=60),
        vdb("block.chest.open", -12.0))
    chest_close = build_hits("container/chest_close", HitSpec(
        whole=bsb("trapdoor_chest", "1500", "0539"), n=2, max_ms=900, hp=60), vdb("block.chest.close", -12.0))
    iron_door = build_hits("door/iron", HitSpec(whole=bsb("door_iron", "0616"), n=1, max_ms=1500, hp=60),
                           vdb("block.iron_door.open", -16.0))

    print("Weather")
    rain_drops = build_snippets("rain_patter", bsb("amb_rain", "1290", "1289", "1019"), 8, 2.0, vdb("weather.rain", -25.0), hp=150)

    # ---------------- vanilla overrides ----------------
    families = {
        "wood": [("wood", 1.0), ("bamboo_wood", 1.08), ("cherry_wood", 1.05), ("nether_wood", 0.9)],
        "stone": [("stone", 1.0), ("deepslate", 0.86), ("deepslate_bricks", 0.9), ("deepslate_tiles", 0.92),
                  ("polished_deepslate", 0.9), ("tuff", 0.95), ("tuff_bricks", 0.97), ("polished_tuff", 0.97),
                  ("nether_bricks", 0.88), ("mud_bricks", 0.95), ("calcite", 1.1), ("dripstone_block", 1.05),
                  ("basalt", 0.9), ("bone_block", 1.1)],
        "grass": [("grass", 1.0), ("moss", 0.95), ("moss_carpet", 1.0), ("azalea", 1.05), ("flowering_azalea", 1.05)],
        "gravel": [("gravel", 1.0), ("suspicious_gravel", 1.0), ("rooted_dirt", 0.95)],
        "sand": [("sand", 1.0), ("suspicious_sand", 1.0)],
        "snow": [("snow", 1.0), ("powder_snow", 1.1)],
        "wool": [("wool", 1.0)],
        "mud": [("mud", 1.0), ("muddy_mangrove_roots", 1.0), ("packed_mud", 1.05)],
        "metal": [("metal", 1.0), ("iron", 1.05)],
        "ladder": [("ladder", 1.0)],
        "glass": [("glass", 1.0)],
        "leaves": [("azalea_leaves", 1.0), ("cherry_leaves", 1.03)],
    }
    dig_for = {"wood": "wood", "ladder": "wood", "stone": "stone", "grass": "grass", "gravel": "grass", "sand": "grass",
               "snow": "grass", "wool": None, "mud": "grass", "metal": "metal", "glass": "glass", "leaves": "grass"}
    step_for = {"glass": "stone"}  # glass blocks: hard tile-like steps
    for fam, members in families.items():
        st = step.get(step_for.get(fam, fam), [])
        dg = dig.get(dig_for[fam]) if dig_for[fam] else None
        for group, pitch in members:
            override(f"block.{group}.step", st, pitch)
            override(f"block.{group}.hit", st, pitch, 0.8)
            override(f"block.{group}.fall", st, pitch * 0.92)
            if dg:
                override(f"block.{group}.break", dg, pitch)
                override(f"block.{group}.place", dg, pitch * 1.05)

    for kind, (op, cl) in {"wooden_door": (door_open, door_close), "wooden_trapdoor": (trap_open, trap_close),
                           "fence_gate": (trap_open, trap_close)}.items():
        override(f"block.{kind}.open", op)
        override(f"block.{kind}.close", cl)
    for wood, p in (("bamboo_wood", 1.08), ("cherry_wood", 1.05), ("nether_wood", 0.9)):
        for kind, (op, cl) in {"door": (door_open, door_close), "trapdoor": (trap_open, trap_close),
                               "fence_gate": (trap_open, trap_close)}.items():
            override(f"block.{wood}_{kind}.open", op, p)
            override(f"block.{wood}_{kind}.close", cl, p)
    override("block.chest.open", chest_open)
    override("block.chest.close", chest_close)
    override("block.iron_door.open", iron_door, 1.0)
    override("block.iron_door.close", iron_door, 0.92)
    # Quieter than vanilla: BetterSound also plays its own rain bed loop on top of these.
    override("weather.rain", rain_drops, 1.0, 0.5)
    override("weather.rain.above", rain_drops, 1.0, 0.35)
    # keep only events that exist in vanilla (unknown events are harmless but noisy)
    known = V.sounds_json()
    mc = {k: v for k, v in mc.items() if k in known}

    # ---------------- ambience (bettersound namespace) ----------------
    print("Ambience")
    loops = {
        "forest_day": build_loop("forest_day", bsb("amb_birds", "2715", "2749", "0100"), 50, -25.0),
        "plains_day": build_loop("plains_day", bsb("amb_birds", "1859") + bsb("amb_wind", "0904"), 50, -28.0),
        "night": build_loop("night", bsb("amb_night", "1470", "1880", "0110"), 50, -26.0),
        "wind": build_loop("wind", bsb("amb_wind", "0595", "0146", "0904"), 50, -24.0, hp=30),
        "rain": build_loop("rain", bsb("amb_rain", "1290", "1289"), 50, -23.0),
        "rain_indoor": build_loop("rain_indoor", bsb("amb_rain", "1293", "1019"), 45, -26.0, lp=6000),
        "cave": build_loop("cave", bsb("amb_cave", "2135", "2136"), 60, -27.0, hp=25),
        "stream": build_loop("stream", bsb("amb_river", "0823", "0864", "2754", "2755"), 40, -25.0),
        "ocean": build_loop("ocean", bsb("amb_ocean", "1046", "1047", "1446"), 50, -24.0, hp=30),
    }
    for name, ref in loops.items():
        if ref:
            bs[f"ambient.loop.{name}"] = {"sounds": [{"name": ref, "stream": True}]}
    birds = build_snippets("bird", bsb("amb_birds", "2715", "2749", "0100"), 12, 3.5, -22.0, hp=900)
    owls = build_snippets("owl", bsb("owl", "0429", "0936"), 4, 4.0, -22.0, hp=150)
    drips = build_hits("ambient/drip", HitSpec(split=bsb("amb_cave", "1384", "1385", "1386", "1387"), n=10,
                                               max_ms=900, hp=200, shelf_db=0.0, gap_ms=150), -20.0)
    # Audible radius = max(volume, 1) x attenuation_distance (default 16); calls are placed up to 20 / 28 blocks away.
    reach = {"ambient.bird": 32, "ambient.owl": 48}
    for ev, names in (("ambient.bird", birds), ("ambient.owl", owls), ("ambient.drip", drips)):
        if names:
            entries = [{"name": n, **({"attenuation_distance": reach[ev]} if ev in reach else {})} for n in names]
            bs[ev] = {"sounds": entries, "subtitle": f"subtitles.bettersound.{ev}"}

    # ---------------- write ----------------
    (ASSETS / "minecraft").mkdir(parents=True, exist_ok=True)
    (ASSETS / "minecraft" / "sounds.json").write_text(json.dumps(mc, indent=1), encoding="utf-8")
    (ASSETS / "bettersound" / "sounds.json").write_text(json.dumps(bs, indent=1), encoding="utf-8")
    lang = ASSETS / "bettersound" / "lang"
    lang.mkdir(parents=True, exist_ok=True)
    (lang / "en_us.json").write_text(json.dumps({
        "subtitles.bettersound.ambient.bird": "Bird sings", "subtitles.bettersound.ambient.owl": "Owl hoots",
        "subtitles.bettersound.ambient.drip": "Water drips"}, indent=1), encoding="utf-8")
    (lang / "zh_cn.json").write_text(json.dumps({
        "subtitles.bettersound.ambient.bird": "鸟鸣", "subtitles.bettersound.ambient.owl": "猫头鹰叫",
        "subtitles.bettersound.ambient.drip": "水滴声"}, indent=1, ensure_ascii=False), encoding="utf-8")
    write_credits()
    print(f"\nminecraft overrides: {len(mc)}  bettersound events: {len(bs)}")


def write_credits() -> None:
    lines = ["# BetterSound - sound credits", "",
             "All sounds are real recordings released under **CC0 1.0 / public domain**. Attribution is not required",
             "but given gratefully. They were cut, cleaned, level-matched and looped by tools/build_assets.py.",
             "BigSoundBank license page (https://bigsoundbank.com/licenses.html) checked 2026-11-09: CC0 1.0 Universal, attribution appreciated but not required.", ""]
    for key in sorted(used_sources):
        lic_file = RAW / key / "LICENSE.txt"
        lic = lic_file.read_text(encoding="utf-8").strip().replace("\n", " - ") if lic_file.exists() else "CC0"
        lines.append(f"## {key}")
        lines.append(f"License/source: {lic}")
        lines.append("")
        for f in sorted(used_sources[key]):
            lines.append(f"- {f}")
        lines.append("")
    text = "\n".join(lines)
    (ROOT / "CREDITS.md").write_text(text, encoding="utf-8")
    (ASSETS / "bettersound" / "CREDITS.md").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
