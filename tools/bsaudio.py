"""DSP helpers for the BetterSound asset pipeline (numpy + scipy + soundfile).

Run tools with `python -I tools/<script>.py` so nothing is imported from the data folders.
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100


def db(x: float) -> float:
    return 20.0 * math.log10(max(x, 1e-12))


def undb(d: float) -> float:
    return 10.0 ** (d / 20.0)


def load(path: Path, mono: bool = True, sr: int = SR) -> np.ndarray:
    """Load any libsndfile-readable file (wav/flac/ogg/mp3/aiff), resample, return float32 [n] or [n,2]."""
    x, file_sr = sf.read(str(path), dtype="float32", always_2d=True)
    if mono:
        x = x.mean(axis=1, keepdims=True)
    elif x.shape[1] == 1:
        x = np.repeat(x, 2, axis=1)
    elif x.shape[1] > 2:
        x = x[:, :2]
    if file_sr != sr:
        g = math.gcd(sr, file_sr)
        x = signal.resample_poly(x, sr // g, file_sr // g, axis=0).astype(np.float32)
    x = x - x.mean(axis=0, keepdims=True)  # remove DC
    return x[:, 0] if mono else x


def highpass(x: np.ndarray, fc: float, sr: int = SR, order: int = 4) -> np.ndarray:
    if fc <= 0:
        return x
    sos = signal.butter(order, fc, btype="highpass", fs=sr, output="sos")
    return signal.sosfiltfilt(sos, x, axis=0).astype(np.float32)


def lowpass(x: np.ndarray, fc: float, sr: int = SR, order: int = 4) -> np.ndarray:
    sos = signal.butter(order, fc, btype="lowpass", fs=sr, output="sos")
    return signal.sosfiltfilt(sos, x, axis=0).astype(np.float32)


def high_shelf(x: np.ndarray, fc: float, gain_db: float, sr: int = SR) -> np.ndarray:
    """Simple high-shelf: add a high-passed copy (gives footsteps extra 'crisp' presence)."""
    if gain_db == 0:
        return x
    hp = highpass(x, fc, sr, order=2)
    return (x + (undb(gain_db) - 1.0) * hp).astype(np.float32)


def envelope(x: np.ndarray, sr: int = SR, win_ms: float = 5.0) -> np.ndarray:
    mono = x if x.ndim == 1 else x.mean(axis=1)
    w = max(1, int(sr * win_ms / 1000))
    n = len(mono) // w
    if n == 0:
        return np.zeros(1, dtype=np.float32)
    frames = mono[: n * w].reshape(n, w)
    return np.sqrt((frames ** 2).mean(axis=1) + 1e-12)


def active_rms(x: np.ndarray, rel_db: float = -26.0) -> float:
    """RMS over the part of the signal within rel_db of its peak (ignores silence/tails)."""
    mono = x if x.ndim == 1 else x.mean(axis=1)
    a = np.abs(mono)
    if a.max() <= 0:
        return 0.0
    act = mono[a > a.max() * undb(rel_db)]
    return float(np.sqrt((act ** 2).mean())) if len(act) else 0.0


def fade(x: np.ndarray, in_ms: float, out_ms: float, sr: int = SR) -> np.ndarray:
    x = x.copy()
    ni, no = int(sr * in_ms / 1000), int(sr * out_ms / 1000)
    if ni > 0:
        ramp = np.sin(np.linspace(0, math.pi / 2, ni)) ** 2
        x[:ni] *= ramp if x.ndim == 1 else ramp[:, None]
    if no > 0:
        ramp = np.cos(np.linspace(0, math.pi / 2, no)) ** 2
        x[-no:] *= ramp if x.ndim == 1 else ramp[:, None]
    return x


def trim(x: np.ndarray, thresh_db: float = -50.0, sr: int = SR, pre_ms: float = 4.0, post_ms: float = 30.0) -> np.ndarray:
    env = envelope(x, sr, 2.0)
    if env.max() <= 0:
        return x
    on = np.nonzero(env > env.max() * undb(thresh_db))[0]
    if len(on) == 0:
        return x
    w = int(sr * 0.002)
    s = max(0, on[0] * w - int(sr * pre_ms / 1000))
    e = min(len(x), (on[-1] + 1) * w + int(sr * post_ms / 1000))
    return x[s:e]


def split_hits(x: np.ndarray, sr: int = SR, min_gap_ms: float = 140.0, rise_db: float = 9.0,
               floor_db: float = -32.0, max_len_ms: float = 700.0, min_len_ms: float = 60.0,
               tail_db: float = -42.0) -> list[np.ndarray]:
    """Cut a recording of repeated impacts (footsteps, knocks) into individual hits.

    Onsets = frames where the 5 ms envelope jumps by rise_db over the previous ~30 ms minimum and
    exceeds floor_db relative to the file peak. Each hit ends at its decay below tail_db or the next onset.
    """
    env = envelope(x, sr, 5.0)
    if env.max() <= 0:
        return []
    env_db = 20 * np.log10(env / env.max())
    hop = int(sr * 0.005)
    look = 6
    onsets: list[int] = []
    last = -10 ** 9
    gap = int(min_gap_ms / 5)
    for i in range(look, len(env_db)):
        if env_db[i] < floor_db:
            continue
        if env_db[i] - env_db[i - look:i].min() >= rise_db and i - last >= gap:
            # move to local peak start
            onsets.append(i)
            last = i
    hits = []
    for k, o in enumerate(onsets):
        nxt = onsets[k + 1] if k + 1 < len(onsets) else len(env_db)
        limit = min(nxt - 2, o + int(max_len_ms / 5))
        peak_i = o + int(np.argmax(env_db[o:min(limit, o + 20)])) if limit > o else o
        end = peak_i
        while end < limit and env_db[end] > env_db[peak_i] + tail_db:
            end += 1
        s = max(0, (o - 2) * hop)
        e = min(len(x), (end + 4) * hop)
        if (e - s) * 1000 / sr >= min_len_ms:
            hits.append(x[s:e].copy())
    return hits


def pick_variants(hits: list[np.ndarray], n: int, spread_db: float = 6.0) -> list[np.ndarray]:
    """Keep up to n hits with consistent loudness (median +- spread_db), preferring the strongest/cleanest."""
    if not hits:
        return []
    levels = np.array([db(active_rms(h)) for h in hits])
    med = np.median(levels)
    good = [h for h, l in zip(hits, levels) if abs(l - med) <= spread_db]
    if len(good) < min(n, 3):
        good = hits
    # spread picks across the recording for variety
    if len(good) <= n:
        return good
    idx = np.linspace(0, len(good) - 1, n).round().astype(int)
    return [good[i] for i in idx]


def normalize_to(x: np.ndarray, target_rms_db: float, max_peak_db: float = -0.8) -> np.ndarray:
    r = active_rms(x)
    if r <= 0:
        return x
    g = undb(target_rms_db) / r
    peak = float(np.abs(x).max()) * g
    if peak > undb(max_peak_db):
        g *= undb(max_peak_db) / peak
    return (x * g).astype(np.float32)


# ----------------------------------------------------------------------------------------------
# Perceived loudness (ITU-R BS.1770 K-weighting). Plain RMS under-rates bright, transient sounds
# (vanilla's grass step) and over-rates rumbly beds, so the mix is balanced on these instead.
# ----------------------------------------------------------------------------------------------
def _k_filters(sr: int = SR) -> list[tuple[list[float], list[float]]]:
    # High shelf (head effect), coefficients derived for any sample rate as in libebur128.
    G, f0, Q = 3.999843853973347, 1681.974450955533, 0.7071752369554196
    K = math.tan(math.pi * f0 / sr)
    Vh = 10.0 ** (G / 20.0)
    Vb = Vh ** 0.4996667741545416
    a0 = 1.0 + K / Q + K * K
    shelf = ([(Vh + Vb * K / Q + K * K) / a0, 2.0 * (K * K - Vh) / a0, (Vh - Vb * K / Q + K * K) / a0],
             [1.0, 2.0 * (K * K - 1.0) / a0, (1.0 - K / Q + K * K) / a0])
    # RLB high-pass.
    f0, Q = 38.13547087602444, 0.5003270373238773
    K = math.tan(math.pi * f0 / sr)
    a0 = 1.0 + K / Q + K * K
    hp = ([1.0, -2.0, 1.0], [1.0, 2.0 * (K * K - 1.0) / a0, (1.0 - K / Q + K * K) / a0])
    return [shelf, hp]


def k_weight(x: np.ndarray, sr: int = SR) -> np.ndarray:
    y = x if x.ndim == 1 else x.mean(axis=1)
    for b, a in _k_filters(sr):
        y = signal.lfilter(b, a, y)
    return y


def lufs(x: np.ndarray, sr: int = SR) -> float:
    """Integrated (ungated) K-weighted loudness of a whole clip, for continuous beds."""
    y = k_weight(x, sr)
    return -0.691 + 10.0 * math.log10(float((y ** 2).mean()) + 1e-20)


def momentary_max(x: np.ndarray, sr: int = SR, win_ms: float = 100.0) -> float:
    """Loudest 100 ms K-weighted window, for short one-shots (footsteps, breaks)."""
    y = k_weight(x, sr)
    w = max(1, min(len(y), int(sr * win_ms / 1000)))
    e = np.convolve(y ** 2, np.ones(w) / w, mode="valid")
    return -0.691 + 10.0 * math.log10(float(e.max()) + 1e-20)


def normalize_lufs(x: np.ndarray, target: float, max_peak_db: float = -3.0) -> np.ndarray:
    g = undb(target - lufs(x))
    peak = float(np.abs(x).max()) * g
    if peak > undb(max_peak_db):
        g *= undb(max_peak_db) / peak
    return (x * g).astype(np.float32)


def normalize_momentary(x: np.ndarray, target: float, max_peak_db: float = -0.8) -> np.ndarray:
    g = undb(target - momentary_max(x))
    peak = float(np.abs(x).max()) * g
    if peak > undb(max_peak_db):
        g *= undb(max_peak_db) / peak
    return (x * g).astype(np.float32)


def peaking(x: np.ndarray, fc: float, gain_db: float, q: float = 0.7, sr: int = SR) -> np.ndarray:
    """RBJ peaking EQ (single pass). Used to carve a pocket for footsteps out of noisy ambience beds."""
    A_ = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * fc / sr
    alpha = math.sin(w0) / (2.0 * q)
    b = [1.0 + alpha * A_, -2.0 * math.cos(w0), 1.0 - alpha * A_]
    a = [1.0 + alpha / A_, -2.0 * math.cos(w0), 1.0 - alpha / A_]
    b = [v / a[0] for v in b]
    a = [v / a[0] for v in a]
    return signal.lfilter(b, a, x, axis=0).astype(np.float32)


def speed(x: np.ndarray, factor: float) -> np.ndarray:
    """Tape-style speed change (factor < 1 = slower and lower)."""
    up, down = 100, int(round(100 * factor))
    return signal.resample_poly(x, up, down, axis=0).astype(np.float32)


def loudest_window(x: np.ndarray, length_ms: float, sr: int = SR, lead: float = 0.3) -> np.ndarray:
    """The length_ms window around the loudest moment (starting `lead` of the window before it)."""
    L = int(sr * length_ms / 1000)
    if len(x) <= L:
        return x.copy()
    env = envelope(x, sr, 5.0)
    w = max(1, int(length_ms / 5 / 2))
    sm = np.convolve(env, np.ones(w) / w, mode="same")
    c = int(np.argmax(sm)) * int(sr * 0.005)
    s = max(0, min(len(x) - L, c - int(L * lead)))
    return x[s:s + L].copy()


def mix(a: np.ndarray, b: np.ndarray, gain_b_db: float = 0.0, offset_ms: float = 0.0, sr: int = SR) -> np.ndarray:
    """a + b (scaled, delayed by offset_ms), result as long as the longer of the two."""
    o = int(sr * offset_ms / 1000)
    n = max(len(a), o + len(b))
    out = np.zeros(n, dtype=np.float32)
    out[:len(a)] += a
    out[o:o + len(b)] += b * undb(gain_b_db)
    return out


def make_loop(x: np.ndarray, length_s: float, xfade_s: float = 3.0, sr: int = SR, offset_s: float = 0.0) -> np.ndarray:
    """Seamless loop: take length_s (+ xfade) from offset, then equal-power crossfade the tail into the head."""
    L, F = int(length_s * sr), int(xfade_s * sr)
    o = int(offset_s * sr)
    need = L + F
    if len(x) - o < need:
        o = 0
        if len(x) < need:
            L = max(int(len(x) * 0.8), sr)
            F = min(F, len(x) - L)
            need = L + F
    seg = x[o:o + need].copy()
    head, tail = seg[:F], seg[L:L + F]
    t = np.linspace(0, math.pi / 2, F)
    fi, fo = np.sin(t), np.cos(t)
    if seg.ndim == 2:
        fi, fo = fi[:, None], fo[:, None]
    out = seg[:L].copy()
    out[:F] = head * fi + tail * fo
    return out.astype(np.float32)


def concat_xfade(parts: list[np.ndarray], xfade_s: float = 1.5, sr: int = SR) -> np.ndarray:
    out = parts[0]
    F = int(xfade_s * sr)
    for p in parts[1:]:
        f = min(F, len(out), len(p))
        t = np.linspace(0, math.pi / 2, f)
        fi, fo = np.sin(t), np.cos(t)
        if out.ndim == 2:
            fi, fo = fi[:, None], fo[:, None]
        mid = out[-f:] * fo + p[:f] * fi
        out = np.concatenate([out[:-f], mid, p[f:]])
    return out.astype(np.float32)


def write_ogg(path: Path, x: np.ndarray, sr: int = SR, quality: float = 0.5) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    x = np.clip(x, -1.0, 1.0)
    # soundfile maps compression_level 0..1 -> vorbis quality 1..0 (lower = better quality)
    channels = 1 if x.ndim == 1 else x.shape[1]
    # libsndfile's Vorbis encoder overflows the stack on huge single writes -> write in blocks.
    with sf.SoundFile(str(path), "w", sr, channels, format="OGG", subtype="VORBIS",
                      compression_level=1.0 - quality) as f:
        for i in range(0, len(x), 8192):
            f.write(x[i:i + 8192])
