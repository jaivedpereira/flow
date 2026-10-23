#!/usr/bin/env python3
"""Validate the Flow curve LUT (Fritsch–Carlson monotone cubic)."""
import math

def lut(points):
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
        if d[i - 1] * d[i] <= 0:
            m[i] = 0.0
        else:
            m[i] = (d[i - 1] + d[i]) / 2

    for i in range(n - 1):
        if d[i] == 0:
            m[i] = 0.0
            m[i + 1] = 0.0
            continue
        a = m[i] / d[i]
        b = m[i + 1] / d[i]
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
        h00 = 2 * t3 - 3 * t2 + 1
        h10 = t3 - 2 * t2 + t
        h01 = -2 * t3 + 3 * t2
        h11 = t3 - t2
        y = h00 * ys[seg] + h10 * h * m[seg] + h01 * ys[seg + 1] + h11 * h * m[seg + 1]
        out.append(int(min(1.0, max(0.0, y)) * 255))
    return out


tests = {
    "identity": [(0, 0), (1, 1)],
    "S-contrast": [(0, 0), (0.3, 0.22), (0.7, 0.8), (1, 1)],
    "lift": [(0, 0.12), (1, 1)],
    "fade": [(0, 0.08), (1, 0.92)],
}

print("=== curve LUT validation ===")
for name, pts in tests.items():
    l = lut(pts)
    mono = all(l[i] <= l[i + 1] for i in range(255))
    print(f"  {name:12} start={l[0]:3d} mid={l[128]:3d} end={l[255]:3d} "
          f"monotonic={mono} {'OK' if mono else 'BUG: non-monotonic'}")

# identity must be exactly identity
ident = lut([(0, 0), (1, 1)])
exact = all(ident[i] == i for i in range(256))
print(f"\n  identity exact (lut[i]==i): {exact}")

# lift must raise black, keep white
lift = lut([(0, 0.12), (1, 1)])
print(f"  lift raises black: {lift[0] > 0} ({lift[0]})")
print(f"  lift keeps white:  {lift[255] == 255}")

# fade must pull both ends inward
fade = lut([(0, 0.08), (1, 0.92)])
print(f"  fade lifts black: {fade[0] > 0} ({fade[0]})")
print(f"  fade lowers white: {fade[255] < 255} ({fade[255]})")

# no overshoot: all values within [0,255] and monotonic
ok = all(0 <= v <= 255 for v in fade) and all(fade[i] <= fade[i+1] for i in range(255))
print(f"  fade in range + monotonic: {ok}")
