"""Checks the generated assets: JSON parses, every referenced .ogg exists, positional sounds are mono,
every sound id the Java code uses is defined. Exit code 1 on any problem."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "common" / "resources" / "assets"
problems: list[str] = []


def check_defs(ns: str) -> dict:
    p = ASSETS / ns / "sounds.json"
    if not p.exists():
        problems.append(f"missing {p}")
        return {}
    try:
        defs = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        problems.append(f"{p}: invalid JSON: {e}")
        return {}
    for event, d in defs.items():
        if not d.get("sounds"):
            problems.append(f"{ns}:{event} has no sounds")
        for s in d.get("sounds", []):
            if isinstance(s, dict) and s.get("type") == "event":
                continue
            name = s if isinstance(s, str) else s["name"]
            sns, _, path = name.rpartition(":")
            sns = sns or ns
            f = ASSETS / sns / "sounds" / f"{path}.ogg"
            if not f.exists():
                problems.append(f"{ns}:{event} -> missing file {f.relative_to(ROOT)}")
                continue
            info = sf.info(str(f))
            stream = isinstance(s, dict) and s.get("stream", False)
            # Looping beds are played non-positionally and may be stereo; everything else must be mono.
            if info.channels != 1 and not stream:
                problems.append(f"{f.relative_to(ROOT)} is {info.channels}-channel but used positionally")
    return defs


mc = check_defs("minecraft")
bs = check_defs("bettersound")

# Every "bettersound:..." id referenced from Java must be defined.
for java in ROOT.glob("common/java/**/*.java"):
    for sid in re.findall(r'"bettersound:([a-z0-9_.]+)"', java.read_text(encoding="utf-8")):
        if sid not in bs:
            problems.append(f"{java.name}: sound id bettersound:{sid} not defined in sounds.json")

oggs = list((ASSETS / "bettersound" / "sounds").rglob("*.ogg"))
size = sum(f.stat().st_size for f in oggs) / 1e6
print(f"minecraft overrides: {len(mc)} events, bettersound events: {len(bs)}, files: {len(oggs)} ({size:.1f} MB)")
if problems:
    print("\n".join("PROBLEM: " + p for p in problems))
    sys.exit(1)
print("OK")
