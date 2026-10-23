#!/usr/bin/env python3
"""
Validate Flow v9: curve library presets, additive mask blending,
and batch caption placeholder substitution.
"""
import math
import numpy as np

# ---------------------------------------------------------------- curve lut

def curve_lut(points):
    pts = sorted(points, key=lambda p: p[0])
    n = len(pts)
    if n < 2:
        return list(range(256))
    xs = [max(0.0, min(1.0, p[0])) for p in pts]
    ys = [max(0.0, min(1.0, p[1])) for p in pts]
    d = []
    for i in range(n - 1):
        dx = max(1e-5, xs[i + 1] - xs[i])
        d.append((ys[i + 1] - ys[i]) / dx)
    m = [0.0] * n
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for i in range(1, n - 1):
        m[i] = 0.0 if d[i - 1] * d[i] <= 0 else (d[i - 1] + d[i]) / 2
    for i in range(n - 1):
        if d[i] == 0:
            m[i] = m[i + 1] = 0.0
            continue
        a, b = m[i] / d[i], m[i + 1] / d[i]
        s = a * a + b * b
        if s > 9:
            t = 3 / math.sqrt(s)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
    out = []
    for i in range(256):
        x = i / 255
        seg = 0
        while seg < n - 2 and x > xs[seg + 1]:
            seg += 1
        h = max(1e-5, xs[seg + 1] - xs[seg])
        t = min(1.0, max(0.0, (x - xs[seg]) / h))
        t2, t3 = t * t, t * t * t
        y = (2*t3 - 3*t2 + 1) * ys[seg] + (t3 - 2*t2 + t) * h * m[seg] \
            + (-2*t3 + 3*t2) * ys[seg + 1] + (t3 - t2) * h * m[seg + 1]
        out.append(int(round(min(1.0, max(0.0, y)) * 255)))
    return out


BUILT_IN = {
    "Neutral": [(0, 0), (1, 1)],
    "Film S": [(0, 0), (0.25, 0.19), (0.75, 0.82), (1, 1)],
    "Lifted Black": [(0, 0.10), (1, 0.98)],
    "Faded White": [(0, 0.04), (1, 0.93)],
    "Punch": [(0, 0), (0.3, 0.24), (0.7, 0.78), (1, 1)],
    "Matte": [(0, 0.13), (0.5, 0.52), (1, 0.90)],
}

print("=== 1. built-in curve presets ===")
fails = 0
luts = {}
for name, pts in BUILT_IN.items():
    l = curve_lut(pts)
    luts[name] = l
    mono = all(l[i] <= l[i + 1] for i in range(255))
    inrange = all(0 <= v <= 255 for v in l)
    print(f"    {name:14} start={l[0]:3d} mid={l[128]:3d} end={l[255]:3d} "
          f"monotonic={mono} inrange={inrange}")
    if not (mono and inrange):
        fails += 1

print("\n=== 2. presets are actually distinct ===")
names = list(luts)
distinct = True
for i in range(len(names)):
    for j in range(i + 1, len(names)):
        if luts[names[i]] == luts[names[j]]:
            print(f"    !! {names[i]} == {names[j]}")
            distinct = False
print(f"    all {len(names)} presets differ: {distinct}")
if not distinct:
    fails += 1

print("\n=== 3. tonal intent matches the name ===")
neutral = luts["Neutral"]
print(f"    Neutral is identity: {neutral == list(range(256))}")
if neutral != list(range(256)):
    fails += 1
lifted = luts["Lifted Black"]
print(f"    Lifted Black raises shadows: {lifted[0] > 15} ({lifted[0]})")
print(f"    Lifted Black keeps highlights: {lifted[255] > 240} ({lifted[255]})")
if not (lifted[0] > 15 and lifted[255] > 240):
    fails += 1
faded = luts["Faded White"]
print(f"    Faded White pulls the top down: {faded[255] < 245} ({faded[255]})")
if faded[255] >= 245:
    fails += 1
punch = luts["Punch"]
print(f"    Punch steeper than Neutral at 25%: {punch[64] < neutral[64]}")
print(f"    Punch steeper than Neutral at 75%: {punch[191] > neutral[191]}")
if not (punch[64] < neutral[64] and punch[191] > neutral[191]):
    fails += 1

# ---------------------------------------------------------------- additive mask

print("\n=== 4. additive mask (max, not replace) ===")
MW, MH = 192, 144

def fill_linear(existing, start, end, feather, additive):
    m = np.array(existing, dtype=np.float32, copy=True)
    sx, sy = start
    ex, ey = end
    dx, dy = ex - sx, ey - sy
    len2 = dx * dx + dy * dy
    if len2 < 1e-6:
        return m
    feather = min(max(feather, 0.02), 0.98)
    lo, hi = 0.5 - feather * 0.5, 0.5 + feather * 0.5
    for y in range(MH):
        ny = (y + 0.5) / MH
        for x in range(MW):
            nx = (x + 0.5) / MW
            t = min(max(((nx - sx) * dx + (ny - sy) * dy) / len2, 0.0), 1.0)
            v = 1.0 if t <= lo else (0.0 if t >= hi else 1.0 - ((t - lo) / (hi - lo)))
            m[y, x] = max(m[y, x], v) if additive else v
    return m

# start with a horizontal painted band (simulating brush strokes)
base = np.zeros((MH, MW), dtype=np.float32)
base[MH // 2 - 8: MH // 2 + 8, :] = 1.0

replace = fill_linear(base, (0.5, 0.05), (0.5, 0.95), 0.3, additive=False)
add = fill_linear(base, (0.5, 0.05), (0.5, 0.95), 0.3, additive=True)

band = slice(MH // 2 - 4, MH // 2 + 4)
print(f"    painted band value  replace={replace[band, :].mean():.2f}  add={add[band, :].mean():.2f}")
print(f"    add keeps the band: {add[band, :].mean() > 0.9}")
print(f"    replace wipes it:   {replace[band, :].mean() < 0.9}")
top_row_add = add[: MH // 5, :].mean()
top_row_rep = replace[: MH // 5, :].mean()
print(f"    top strip: replace={top_row_rep:.2f} add={top_row_add:.2f}")
print(f"    both cover the top: {top_row_add > 0.9 and top_row_rep > 0.9}")
if not (add[band, :].mean() > 0.9 and replace[band, :].mean() < 0.9):
    fails += 1

# ---------------------------------------------------------------- placeholders

print("\n=== 5. batch caption placeholders ===")

def resolve(caption, index, total, name):
    return (caption
            .replace("{n}", str(index + 1))
            .replace("{total}", str(total))
            .replace("{name}", name))

cases = [
    ("{n} / {total}", 0, 12, "IMG_001", "1 / 12"),
    ("Shot {name}", 4, 12, "IMG_005", "Shot IMG_005"),
    ("#{n}", 10, 12, "x", "#11"),
    ("no placeholder", 0, 3, "y", "no placeholder"),
    ("{n}-{n}", 2, 3, "z", "3-3"),
]
for caption, idx, total, name, expected in cases:
    got = resolve(caption, idx, total, name)
    ok = got == expected
    print(f"    {caption!r:22} -> {got!r:22} expected={expected!r:22} {'OK' if ok else 'FAIL'}")
    if not ok:
        fails += 1

print(f"\n=== {'ALL CHECKS PASSED' if fails == 0 else str(fails) + ' CHECK(S) FAILED'} ===")
