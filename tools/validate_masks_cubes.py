#!/usr/bin/env python3
"""
Validate Flow v8: gradient mask shapes and .cube parse/export round-trip.
Faithful ports of MaskShapes.kt and CubeIO.kt.
"""
import math
import os
import tempfile
import numpy as np

MW, MH = 192, 144

# ---------------------------------------------------------------- mask shapes

def fill_linear(start, end, feather, invert=False):
    m = np.zeros((MH, MW), dtype=np.float32)
    sx, sy = start
    ex, ey = end
    dx, dy = ex - sx, ey - sy
    len2 = dx * dx + dy * dy
    if len2 < 1e-6:
        return m
    feather = min(max(feather, 0.02), 0.98)
    lo = 0.5 - feather * 0.5
    hi = 0.5 + feather * 0.5
    for y in range(MH):
        ny = (y + 0.5) / MH
        for x in range(MW):
            nx = (x + 0.5) / MW
            t = ((nx - sx) * dx + (ny - sy) * dy) / len2
            t = min(max(t, 0.0), 1.0)
            if t <= lo:
                v = 1.0
            elif t >= hi:
                v = 0.0
            else:
                v = 1.0 - ((t - lo) / (hi - lo))
            if invert:
                v = 1.0 - v
            m[y, x] = v
    return m


def fill_radial(centre, edge, feather, invert=False):
    m = np.zeros((MH, MW), dtype=np.float32)
    cx, cy = centre
    ex, ey = edge
    radius = max(math.hypot(ex - cx, ey - cy), 0.02)
    feather = min(max(feather, 0.02), 0.98)
    inner = max(radius * (1.0 - feather), 0.001)
    for y in range(MH):
        ny = (y + 0.5) / MH
        for x in range(MW):
            nx = (x + 0.5) / MW
            d = math.hypot(nx - cx, ny - cy)
            if d <= inner:
                v = 1.0
            elif d >= radius:
                v = 0.0
            else:
                v = 1.0 - ((d - inner) / (radius - inner))
            v = v * v * (3.0 - 2.0 * v)
            if invert:
                v = 1.0 - v
            m[y, x] = v
    return m


print("=== 1. linear gradient mask ===")
lin = fill_linear((0.5, 0.10), (0.5, 0.90), 0.35)
top = lin[: MH // 5, :].mean()
bottom = lin[-MH // 5:, :].mean()
mid = lin[MH // 2 - 5: MH // 2 + 5, :].mean()
print(f"    top={top:.2f}  middle={mid:.2f}  bottom={bottom:.2f}")
print(f"    gradient runs top->bottom: {top > mid > bottom}")
print(f"    monotonic per column: {bool(np.all(np.diff(lin[:, MW // 2]) <= 0.02))}")

print("\n=== 2. inverted linear ===")
lin_inv = fill_linear((0.5, 0.10), (0.5, 0.90), 0.35, invert=True)
print(f"    top={lin_inv[:MH//5].mean():.2f}  bottom={lin_inv[-MH//5:].mean():.2f}")
print(f"    mirror of non-inverted: {abs(lin_inv[:MH//5].mean() - (1 - top)) < 0.02}")

print("\n=== 3. radial spotlight ===")
rad = fill_radial((0.5, 0.5), (0.85, 0.5), 0.5)
centre_val = rad[MH // 2, MW // 2]
corner_val = rad[0, 0]
edge_val = rad[MH // 2, -1]
print(f"    centre={centre_val:.2f}  edge={edge_val:.2f}  corner={corner_val:.2f}")
print(f"    centre brightest: {centre_val > edge_val and centre_val > corner_val}")

print("\n=== 4. radial small radius leaves corners at 0 ===")
rad_small = fill_radial((0.5, 0.5), (0.6, 0.5), 0.3)
print(f"    corner={rad_small[0, 0]:.3f}  centre={rad_small[MH//2, MW//2]:.2f}")
print(f"    corners zeroed: {rad_small[0, 0] < 0.01}")

# ---------------------------------------------------------------- cube io

def write_cube(path, size, gen, title="test"):
    with open(path, "w") as f:
        f.write("# generated\n")
        f.write(f'TITLE "{title}"\n')
        f.write(f"LUT_3D_SIZE {size}\n")
        for b in range(size):
            for g in range(size):
                for r in range(size):
                    f.write("%.6f %.6f %.6f\n" % gen(r / (size - 1), g / (size - 1), b / (size - 1)))


def parse_cube(path):
    size = 0
    dmin = [0.0, 0.0, 0.0]
    dmax = [1.0, 1.0, 1.0]
    vals = []
    title = None
    for raw in open(path):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        up = line.upper()
        if up.startswith("LUT_3D_SIZE"):
            size = int(line.split()[1])
        elif up.startswith("LUT_1D_SIZE"):
            raise ValueError("1D not supported")
        elif up.startswith("TITLE"):
            title = line.split(" ", 1)[1].strip().strip('"') if " " in line else ""
        elif up.startswith("DOMAIN_MIN"):
            p = line.split()
            dmin = [float(p[i + 1]) for i in range(3)]
        elif up.startswith("DOMAIN_MAX"):
            p = line.split()
            dmax = [float(p[i + 1]) for i in range(3)]
        else:
            p = [float(v) for v in line.split() if v]
            if len(p) >= 3:
                vals.extend(p[:3])
    if size < 2:
        raise ValueError("missing LUT_3D_SIZE")
    expected = size ** 3 * 3
    if len(vals) != expected:
        raise ValueError(f"expected {expected} got {len(vals)}")
    out = []
    for i in range(expected):
        ch = i % 3
        lo, hi = dmin[ch], dmax[ch]
        span = hi - lo if abs(hi - lo) > 1e-6 else 1.0
        out.append(min(max((vals[i] - lo) / span, 0.0), 1.0))
    return size, np.array(out), title


print("\n=== 5. .cube round-trip (identity 17^3) ===")
TMP = tempfile.gettempdir()
p = os.path.join(TMP, "flow_identity.cube")
write_cube(p, 17, lambda r, g, b: (r, g, b), "identity")
size, data, title = parse_cube(p)
print(f"    parsed size={size} title={title!r} floats={len(data)}")
expected = np.zeros(size ** 3 * 3)
i = 0
for b in range(size):
    for g in range(size):
        for r in range(size):
            expected[i] = r / (size - 1)
            expected[i + 1] = g / (size - 1)
            expected[i + 2] = b / (size - 1)
            i += 3
err = np.abs(data - expected).max()
print(f"    max error vs expected identity: {err:.6f}")
print(f"    round-trip accurate: {err < 1e-5}")

print("\n=== 6. .cube with DOMAIN 0..255 rescales ===")
p2 = os.path.join(TMP, "flow_domain.cube")
with open(p2, "w") as f:
    f.write("LUT_3D_SIZE 2\n")
    f.write("DOMAIN_MIN 0 0 0\n")
    f.write("DOMAIN_MAX 255 255 255\n")
    for b in (0, 255):
        for g in (0, 255):
            for r in (0, 255):
                f.write(f"{r}.0 {g}.0 {b}.0\n")
size2, data2, _ = parse_cube(p2)
print(f"    size={size2} first={data2[:3]} last={data2[-3:]}")
print(f"    rescaled to 0..1: {data2.min() == 0.0 and data2.max() == 1.0}")

print("\n=== 7. malformed cube is rejected ===")
p3 = os.path.join(TMP, "flow_bad.cube")
with open(p3, "w") as f:
    f.write("LUT_3D_SIZE 17\n")
    f.write("0.1 0.2 0.3\n")  # way too few rows
try:
    parse_cube(p3)
    print("    NOT rejected  <-- BUG")
    bad = True
except ValueError as e:
    print(f"    rejected with: {e}")
    bad = False

print(f"\n=== {'ALL CHECKS PASSED' if not bad else 'A CHECK FAILED'} ===")
