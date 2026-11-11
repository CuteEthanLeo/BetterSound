"""Downloads the user-approved CC0 sources listed in tools/sources.json into raw_sources/.
Sequential with pauses (BigSoundBank is behind Cloudflare rate limiting). Skips files already present."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw_sources"
SRC = json.loads((ROOT / "tools" / "sources.json").read_text(encoding="utf-8"))
UA = {"User-Agent": "Mozilla/5.0 (compatible; BetterSound-asset-fetch/1.0)"}


def fetch(url: str, dest: Path, tries: int = 6) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            tmp = dest.with_suffix(dest.suffix + ".part")
            tmp.write_bytes(data)
            tmp.replace(dest)
            print(f"ok   {dest.relative_to(RAW)} ({len(data) / 1e6:.2f} MB)", flush=True)
            return True
        except Exception as e:  # noqa: BLE001
            wait = 10 * (i + 1)
            print(f"retry {url} ({e}); waiting {wait}s", flush=True)
            time.sleep(wait)
    print(f"FAIL {url}", flush=True)
    return False


def extract(archive: Path) -> None:
    out = archive.parent / "x"
    if out.exists():
        return
    out.mkdir()
    # Windows' bundled bsdtar reads zip and 7z.
    tar = r"C:\Windows\System32\tar.exe"
    subprocess.run([tar, "-xf", str(archive), "-C", str(out)], check=False)


def main() -> None:
    failed = []
    for p in SRC["packs"]:
        name = p["url"].rsplit("/", 1)[-1].replace("%20", "_").replace("%5B", "").replace("%5D", "")
        dest = RAW / p["name"] / name
        if not fetch(p["url"], dest):
            failed.append(p["url"])
            continue
        if dest.suffix in (".zip", ".7z"):
            extract(dest)
        (RAW / p["name"] / "LICENSE.txt").write_text(p["license"] + "\n" + p["url"] + "\n", encoding="utf-8")
    bsb = SRC["bigsoundbank"]
    for cat, ids in bsb["ids"].items():
        for sid in ids:
            dest = RAW / "bigsoundbank" / cat / f"{sid}.ogg"
            existed = dest.exists()
            if not fetch(bsb["url"].format(id=sid), dest):
                failed.append(sid)
            if not existed:
                time.sleep(3)
    (RAW / "bigsoundbank" / "LICENSE.txt").write_text(bsb["license"] + "\nhttps://bigsoundbank.com/licenses.html\n", encoding="utf-8")
    print("FAILED:", failed if failed else "none")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
