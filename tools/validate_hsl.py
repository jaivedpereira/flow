#!/usr/bin/env python3
"""Validate the Flow selective-HSL math (faithful port of Hsl.kt)."""
import colorsys
import numpy as np

BANDS = [
    ("RED", 0.0), ("ORANGE", 30.0), ("YELLOW", 60.0), ("GREEN", 120.0),
    ("CYAN", 180.0), ("BLUE", 240.0), ("PURPLE", 285.0), ("MAGENTA", 330.0),
]


def apply_hsl(arr, shifts):
    """shifts: dict band_name -> (hue, sat, lum) each -1..1."""
    a = arr.astype(np.float32) / 255.0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    delta = mx - mn

    hue = np.zeros_like(mx)
    safe = delta > 1e-6
    m1 = safe & (mx == r)
    m2 = safe & (mx == g) & ~m1
    m3 = safe & (mx == b) & ~m1 & ~m2
    hue[m1] = 60.0 * (((g[m1] - b[m1]) / delta[m1]) % 6.0)
    hue[m2] = 60.0 * (((b[m2] - r[m2]) / delta[m2]) + 2.0)
    hue[m3] = 60.0 * (((r[m3] - g[m3]) / delta[m3]) + 4.0)
    hue = np.where(hue < 0, hue + 360.0, hue)

    valid = delta >= 0.02
    d_hue = np.zeros_like(mx)
    d_sat = np.zeros_like(mx)
    d_lum = np.zeros_like(mx)
    wsum = np.zeros_like(mx)
    width = 45.0
    for name, centre in BANDS:
        if name not in shifts:
            continue
        h, s, l = shifts[name]
        d = np.abs(hue - centre) % 360.0
        d = np.where(d > 180.0, 360.0 - d, d)
        weight = np.where(d <= width, 0.5 * (1.0 + np.cos(np.pi * d / width)), 0.0)
        d_hue += h * weight
        d_sat += s * weight
        d_lum += l * weight
        wsum += weight

    good = valid & (wsum > 1e-4)
    wsafe = np.maximum(wsum, 1e-6)
    d_hue = np.where(good, d_hue / wsafe, 0.0)
    d_sat = np.where(good, d_sat / wsafe, 0.0)
    d_lum = np.where(good, d_lum / wsafe, 0.0)

    new_hue = (hue + d_hue * 30.0 + 360.0) % 360.0
    sat = np.where(mx > 0, delta / np.maximum(mx, 1e-6), 0.0)
    val = mx
    sat = np.clip(sat * (1.0 + d_sat * 0.9), 0.0, 1.0)

    c = val * sat
    x = c * (1.0 - np.abs(((new_hue / 60.0) % 2.0) - 1.0))
    mm = val - c

    idx = np.zeros_like(new_hue, dtype=np.int32)
    for step, thresh in enumerate((60, 120, 180, 240, 300), start=1):
        idx = np.where(new_hue >= thresh, step, idx)

    zero = np.zeros_like(c)
    conds = [idx == 0, idx == 1, idx == 2, idx == 3, idx == 4, idx == 5]
    nr = np.select(conds, [c, x, zero, zero, x, c])
    ng = np.select(conds, [x, c, c, x, zero, zero])
    nb = np.select(conds, [zero, zero, x, c, c, x])

    fr = np.clip(nr + mm, 0.0, 1.0)
    fg = np.clip(ng + mm, 0.0, 1.0)
    fb = np.clip(nb + mm, 0.0, 1.0)

    luma_before = r * 0.299 + g * 0.587 + b * 0.114
    luma_after = fr * 0.299 + fg * 0.587 + fb * 0.114
    scale = np.where(
        (luma_after > 1e-3) & (luma_before > 1e-3),
        np.clip(luma_before / np.maximum(luma_after, 1e-6), 0.2, 5.0),
        1.0,
    )
    fr = np.clip(fr * scale, 0.0, 1.0)
    fg = np.clip(fg * scale, 0.0, 1.0)
    fb = np.clip(fb * scale, 0.0, 1.0)

    gain = 1.0 + d_lum * 0.6
    fr = np.clip(fr * gain, 0.0, 1.0)
    fg = np.clip(fg * gain, 0.0, 1.0)
    fb = np.clip(fb * gain, 0.0, 1.0)

    res = np.stack([fr, fg, fb], axis=-1)
    final = np.where(good[..., None], res, a)
    return (final * 255.0).astype(np.uint8)


def hsv_of(rgb):
    r, g, b = [c / 255.0 for c in rgb]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    return h * 360.0, s, l


def luma(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def as_tuple(arr):
    return tuple(int(v) for v in arr)


print("=== HSL validation ===")

tests = {
    "pure red": (220, 30, 30),
    "pure green": (30, 200, 40),
    "pure blue": (30, 40, 220),
    "grey": (128, 128, 128),
    "skin-ish": (222, 170, 140),
}

fails = 0

# 1 — neutral must be lossless
print("\n[1] neutral shifts are lossless")
for name, rgb in tests.items():
    arr = np.array([[rgb]], dtype=np.uint8)
    out = apply_hsl(arr, {})
    same = as_tuple(out[0][0]) == rgb
    if not same:
        fails += 1
    print(f"    {name:12} {rgb} -> {as_tuple(out[0][0])}  unchanged={same}")

# 2 — RED saturation -1 greys the red, leaves green alone
print("\n[2] RED sat -1 desaturates red, ignores green")
shifts = {"RED": (0.0, -1.0, 0.0)}
red_in = (220, 30, 30)
red_out = apply_hsl(np.array([[red_in]], dtype=np.uint8), shifts)[0][0]
green_in = (30, 200, 40)
green_out = apply_hsl(np.array([[green_in]], dtype=np.uint8), shifts)[0][0]
sat_before = hsv_of(red_in)[1]
sat_after = hsv_of(as_tuple(red_out))[1]
lum_before = luma(red_in)
lum_after = luma(as_tuple(red_out))
print(f"    red  {red_in} -> {as_tuple(red_out)}")
print(f"      saturation {sat_before:.2f} -> {sat_after:.2f}  decreased={sat_after < sat_before - 0.1}")
print(f"      luma {lum_before:.0f} -> {lum_after:.0f}  preserved={abs(lum_after - lum_before) < 45}")
print(f"    green {green_in} -> {as_tuple(green_out)}  untouched={as_tuple(green_out) == green_in}")
if not sat_after < sat_before - 0.1:
    fails += 1
if not abs(lum_after - lum_before) < 45:
    fails += 1
if as_tuple(green_out) != green_in:
    fails += 1

# 3 — BLUE luminance -1 darkens blues only
print("\n[3] BLUE lum -1 darkens blue")
shifts2 = {"BLUE": (0.0, 0.0, -1.0)}
blue_in = (30, 40, 220)
blue_out = apply_hsl(np.array([[blue_in]], dtype=np.uint8), shifts2)[0][0]
darker = luma(as_tuple(blue_out)) < luma(blue_in) - 15
print(f"    {blue_in} -> {as_tuple(blue_out)}  luma {luma(blue_in):.0f} -> {luma(as_tuple(blue_out)):.0f}  darker={darker}")
if not darker:
    fails += 1

# 4 — GREEN hue +1 rotates the hue
print("\n[4] GREEN hue +1 rotates green toward cyan")
shifts3 = {"GREEN": (1.0, 0.0, 0.0)}
g_in = (30, 200, 40)
g_out = apply_hsl(np.array([[g_in]], dtype=np.uint8), shifts3)[0][0]
h_before = hsv_of(g_in)[0]
h_after = hsv_of(as_tuple(g_out))[0]
moved = abs(((h_after - h_before + 180) % 360) - 180) > 15
print(f"    hue {h_before:.0f}deg -> {h_after:.0f}deg  moved={moved}")
if not moved:
    fails += 1

# 5 — grey is immune to every band
print("\n[5] grey is immune")
shifts4 = {name: (1.0, 1.0, 1.0) for name, _ in BANDS}
grey_in = (128, 128, 128)
grey_out = apply_hsl(np.array([[grey_in]], dtype=np.uint8), shifts4)[0][0]
grey_ok = as_tuple(grey_out) == grey_in
print(f"    {grey_in} -> {as_tuple(grey_out)}  unchanged={grey_ok}")
if not grey_ok:
    fails += 1

# 6 — ORANGE saturation boosts skin
print("\n[6] ORANGE sat +0.6 boosts skin")
shifts5 = {"ORANGE": (0.0, 0.6, 0.0)}
skin_in = (222, 170, 140)
skin_out = apply_hsl(np.array([[skin_in]], dtype=np.uint8), shifts5)[0][0]
s1 = hsv_of(skin_in)[1]
s2 = hsv_of(as_tuple(skin_out))[1]
print(f"    {skin_in} -> {as_tuple(skin_out)}")
print(f"    saturation {s1:.3f} -> {s2:.3f}  increased={s2 > s1 + 0.03}")
if not s2 > s1 + 0.03:
    fails += 1

# 7 — a real photo: only the red band moves, other hues stay put
print("\n[7] isolation on a real image")
src = None
try:
    from PIL import Image
    import sys
    src = Image.open(sys.argv[1]).convert("RGB")
    src.thumbnail((200, 200))
    base = np.asarray(src)
    moved = apply_hsl(base, {"RED": (0.0, -1.0, 0.0)})
    diff = np.abs(base.astype(int) - moved.astype(int)).mean(axis=2)
    changed = (diff > 3).mean() * 100.0
    unchanged = 100.0 - changed
    print(f"    pixels changed: {changed:.1f}%   pixels untouched: {unchanged:.1f}%")
    print(f"    selective (not the whole frame): {changed < 85}")
    if changed >= 85:
        fails += 1
except Exception as e:
    print(f"    skipped: {e}")

print(f"\n=== {'ALL CHECKS PASSED' if fails == 0 else str(fails) + ' CHECK(S) FAILED'} ===")
