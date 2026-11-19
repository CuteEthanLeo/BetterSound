"""Reads the local vanilla sound definitions/loudness so replacements keep subtitles and mix balance.

Uses the player's own Minecraft install (%APPDATA%/.minecraft/assets). Nothing from vanilla is copied into the mod.
"""
from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path

import bsaudio as A

ASSETS = Path(os.environ.get("APPDATA", "")) / ".minecraft" / "assets"


# Reference vanilla version for subtitles / loudness matching and for deciding which events exist.
# Asset index "17" = Minecraft 1.21 / 1.21.1. Override with BS_VANILLA_INDEX=<id> (see assets/indexes/*.json).
INDEX_ID = os.environ.get("BS_VANILLA_INDEX", "17")


@lru_cache(maxsize=1)
def _index() -> dict:
    p = ASSETS / "indexes" / f"{INDEX_ID}.json"
    if not p.exists():
        avail = sorted(q.stem for q in (ASSETS / "indexes").glob("*.json"))
        raise SystemExit(f"Vanilla asset index {p} not found (available: {avail}). "
                         "Launch Minecraft 1.21.1 once, or set BS_VANILLA_INDEX.")
    d = json.loads(p.read_text(encoding="utf-8"))["objects"]
    if "minecraft/sounds.json" not in d:
        raise SystemExit(f"Asset index {INDEX_ID} has no minecraft/sounds.json")
    print(f"vanilla reference: asset index {INDEX_ID}")
    return d


def _obj(key: str) -> Path | None:
    o = _index().get(key)
    if not o:
        return None
    h = o["hash"]
    return ASSETS / "objects" / h[:2] / h


@lru_cache(maxsize=1)
def sounds_json() -> dict:
    return json.loads(_obj("minecraft/sounds.json").read_text(encoding="utf-8"))


def subtitle(event: str) -> str | None:
    e = sounds_json().get(event)
    return e.get("subtitle") if e else None


@lru_cache(maxsize=None)
def loudness_k(event: str) -> float | None:
    """Mean loudest-100 ms K-weighted loudness (LUFS, including per-entry volume) of a vanilla event's files."""
    e = sounds_json().get(event)
    if not e:
        return None
    vals = []
    for s in e["sounds"][:8]:
        if isinstance(s, dict) and s.get("type") == "event":
            continue
        name, vol = (s, 1.0) if isinstance(s, str) else (s["name"], s.get("volume", 1.0))
        p = _obj(f"minecraft/sounds/{name.split(':')[-1]}.ogg")
        if p is None or not p.exists():
            continue
        vals.append(A.momentary_max(A.load(p)) + A.db(vol))
    return sum(vals) / len(vals) if vals else None


@lru_cache(maxsize=None)
def loudness_db(event: str) -> float | None:
    """Mean active RMS (dBFS, including per-entry volume) of a vanilla event's files."""
    e = sounds_json().get(event)
    if not e:
        return None
    vals = []
    for s in e["sounds"][:6]:
        name, vol = (s, 1.0) if isinstance(s, str) else (s["name"], s.get("volume", 1.0))
        if isinstance(s, dict) and s.get("type") == "event":
            continue
        p = _obj(f"minecraft/sounds/{name.split(':')[-1]}.ogg")
        if p is None or not p.exists():
            continue
        x = A.load(p)
        r = A.active_rms(x)
        if r > 0:
            vals.append(A.db(r * vol))
    return sum(vals) / len(vals) if vals else None
