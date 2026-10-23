#!/usr/bin/env python3
"""Offline validation of the Flow effect algorithms (numpy port of Effects.kt)."""
import sys
import numpy as np
from PIL import Image

SRC = sys.argv[1]
OUT = sys.argv[2]

img = Image.open(SRC).convert("RGB")
img.thumbnail((420, 420))
a = np.asarray(img).astype(np.float32)
h, w, _ = a.shape
print(f"input: {w}x{h}")

lum = (a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114)


def dither_error(a, param=0.5):
    g = lum.copy()
    thr = 110 + param * 60
    out = np.zeros_like(g)
    for y in range(h):
        for x in range(w):
            old = g[y, x]
            new = 0.0 if old < thr else 255.0
            out[y, x] = new
            err = old - new
            if x + 1 < w:
                g[y, x + 1] += err * 7 / 16
            if y + 1 < h:
                if x > 0:
                    g[y + 1, x - 1] += err * 3 / 16
                g[y + 1, x] += err * 5 / 16
                if x + 1 < w:
                    g[y + 1, x + 1] += err * 1 / 16
    return np.stack([out] * 3, axis=-1)


BAYER8 = np.array([
    [0, 32, 8, 40, 2, 34, 10, 42],
    [48, 16, 56, 24, 50, 18, 58, 26],
    [12, 44, 4, 36, 14, 46, 6, 38],
    [60, 28, 52, 20, 62, 30, 54, 22],
    [3, 35, 11, 43, 1, 33, 9, 41],
    [51, 19, 59, 27, 49, 17, 57, 25],
    [15, 47, 7, 39, 13, 45, 5, 37],
    [63, 31, 55, 23, 61, 29, 53, 21],
], dtype=np.float32)


def bayer(a, param=0.5):
    levels = int(round(2 + (1 - param) * 6))
    levels = max(2, min(8, levels))
    t = np.tile(BAYER8, (h // 8 + 1, w // 8 + 1))[:h, :w] / 64.0 - 0.5
    steps = levels - 1
    q = np.clip(np.round(lum / 255.0 * steps + t * 0.9), 0, steps)
    v = np.round(q * 255.0 / steps)
    return np.stack([v] * 3, axis=-1)


def posterize(a, param=0.5):
    levels = int(round(2 + (1 - param) * 8))
    levels = max(2, min(10, levels))
    step = 255.0 / (levels - 1)
    return np.clip(np.round(a / step) * step, 0, 255)


def scanlines(a, param=0.5):
    step = int(round(2 + (1 - param) * 6))
    out = a.copy()
    out[::step, :, :] *= 0.62
    return out


def threshold(a, param=0.5):
    cut = 40 + param * 175
    v = np.where(lum < cut, 0, 255)
    return np.stack([v] * 3, axis=-1)


def pixelate(a, param=0.35):
    block = int(round(2 + param * 34))
    small = img.resize((max(1, w // block), max(1, h // block)), Image.BILINEAR)
    return np.asarray(small.resize((w, h), Image.NEAREST)).astype(np.float32)


def chroma(a, param=0.4):
    shift = int(round(1 + param * 18))
    out = a.copy()
    out[..., 0] = np.roll(a[..., 0], shift, axis=1)
    out[..., 2] = np.roll(a[..., 2], -shift, axis=1)
    return out


def halftone(a, param=0.5):
    cell = int(round(3 + (1 - param) * 9))
    out = np.full((h, w, 3), 255.0, dtype=np.float32)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            y1 = min(y + cell, h)
            x1 = min(x + cell, w)
            block = lum[y:y1, x:x1]
            dark = 1.0 - block.mean() / 255.0
            r = (cell * 0.5) * dark
            if r > 0.4:
                yy, xx = np.mgrid[y:y1, x:x1]
                d = np.sqrt((xx - (x + cell * 0.5)) ** 2 + (yy - (y + cell * 0.5)) ** 2)
                sub = out[y:y1, x:x1]
                sub[d <= r] = 0.0
    return out


tests = [
    ("dither", dither_error(a)),
    ("bayer", bayer(a)),
    ("posterize", posterize(a)),
    ("scanlines", scanlines(a)),
    ("threshold", threshold(a)),
    ("pixelate", pixelate(a)),
    ("chroma", chroma(a)),
    ("halftone", halftone(a)),
]

print("\n=== effect validation ===")
cols = 4
rows = (len(tasks := [(n, v) for n, v in tests]) + cols - 1) // cols
sheet = Image.new("RGB", (w * cols, h * rows), (0, 0, 0))
for i, (name, out) in enumerate(tests):
    arr = np.clip(out if out is not None else a, 0, 255).astype(np.uint8)
    tile = Image.fromarray(arr, "RGB")
    sheet.paste(tile, ((i % cols) * w, (i // cols) * h))
    std = float(arr.std())
    uniq = len(np.unique(arr[..., 0]))
    mean = arr.mean()
    print(f"  {name:12} mean={mean:6.1f} std={std:6.1f} unique_R={uniq:4d} "
          f"{'OK' if std > 1.0 else 'FLAT (bug!)'}")

sheet.save(OUT)
print(f"\ncontact sheet -> {OUT}")
