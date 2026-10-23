#!/usr/bin/env python3
"""
Generate 100% synthetic demo assets for the Flow README.

Nothing here comes from a photograph. The "photo" used for before/after and the
effect grid is procedurally generated: a dusk sky gradient, a sun, layered hills
and a water reflection. That gives the effects real edges, smooth gradients and
both dark and bright regions to chew on, with no personal imagery anywhere.
"""
import os
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "img")
)
os.makedirs(OUT, exist_ok=True)

BLACK = (0, 0, 0)
CREAM = (237, 231, 220)
DIM = (120, 118, 114)
LINE = (31, 31, 31)
ELEV = (20, 20, 23)


def font(size):
    for p in (r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def font_regular(size):
    for p in (r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def mono(size):
    for p in (r"C:\Windows\Fonts\consola.ttf", r"C:\Windows\Fonts\cour.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------- synthetic photo

def synthetic_photo(w=900, h=1200):
    """Procedural dusk landscape. No photograph, no people."""
    ys = np.arange(h).reshape(-1, 1) / h
    xs = np.arange(w).reshape(1, -1) / w
    px = np.zeros((h, w, 3), dtype=np.float32)

    horizon = 0.58
    t = np.clip(ys / horizon, 0, 1)
    sky_top = np.array([18, 26, 52], dtype=np.float32)
    sky_mid = np.array([92, 62, 96], dtype=np.float32)
    sky_low = np.array([232, 138, 84], dtype=np.float32)

    for c in range(3):
        upper = sky_top[c] + (sky_mid[c] - sky_top[c]) * np.clip(t * 2, 0, 1)
        lower = sky_mid[c] + (sky_low[c] - sky_mid[c]) * np.clip((t - 0.5) * 2, 0, 1)
        px[:, :, c] = np.where(t < 0.5, upper, lower)

    # sun
    sun_x, sun_y = 0.62, horizon - 0.06
    d = np.sqrt(((xs - sun_x) * (w / h)) ** 2 + (ys - sun_y) ** 2)
    glow = np.clip(1.0 - d / 0.30, 0, 1) ** 2.2
    for c, g in enumerate([1.0, 0.72, 0.42]):
        px[:, :, c] += glow * 210 * g

    # water reflection with ripple
    water = ys > horizon
    mirror_y = np.clip(2 * horizon - ys, 0, 1)
    ri = np.sin(xs * 90 + ys * 60) * 0.004 + np.sin(xs * 33 - ys * 21) * 0.006
    mi = np.clip(((mirror_y + ri) * h).astype(int), 0, h - 1)   # shape (h, w)
    cols = np.broadcast_to(np.arange(w).reshape(1, -1), (h, w))
    water_img = px[mi, cols]
    px = np.where(water[:, :, None], water_img * 0.72, px)

    img = Image.fromarray(np.clip(px, 0, 255).astype(np.uint8), "RGB")
    d2 = ImageDraw.Draw(img)

    def hill(base_y, amp, freq, phase, color):
        pts = []
        for x in range(0, w + 1, 4):
            u = x / w
            yy = (base_y + math.sin(u * freq + phase) * amp
                  + math.sin(u * freq * 2.3 + phase) * amp * 0.35)
            pts.append((x, yy))
        pts += [(w, h), (0, h)]
        d2.polygon(pts, fill=color)

    hill(h * 0.62, h * 0.030, 3.1, 0.4, (28, 22, 34))
    hill(h * 0.70, h * 0.024, 4.4, 2.1, (18, 15, 24))
    hill(h * 0.80, h * 0.018, 5.8, 4.0, (10, 9, 14))

    # a strong vertical edge on the left (helps show chroma/pixelate)
    d2.polygon(
        [(w * 0.10, h * 0.82), (w * 0.10, h * 0.55), (w * 0.135, h * 0.50),
         (w * 0.17, h * 0.55), (w * 0.17, h * 0.82)],
        fill=(8, 7, 11),
    )

    arr = np.asarray(img).astype(np.float32)
    rng = np.random.default_rng(7)
    arr += rng.normal(0, 3.4, arr.shape)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")
    return img.filter(ImageFilter.GaussianBlur(0.4))


def graded(photo):
    """A plausible 'Flow' grade: S-curve, teal shadows, amber highlights, fade."""
    arr = np.asarray(photo).astype(np.float32) / 255.0
    lum = (arr * np.array([0.299, 0.587, 0.114])).sum(axis=2)

    arr = np.clip(arr, 0, 1)
    arr = arr * arr * (3 - 2 * arr)

    shadow = np.clip(1 - lum * 2.2, 0, 1)
    high = np.clip((lum - 0.45) * 2.2, 0, 1)
    arr[..., 0] += high * 0.10 - shadow * 0.05
    arr[..., 1] += high * 0.05 + shadow * 0.02
    arr[..., 2] += shadow * 0.09 - high * 0.06

    arr = arr * 0.94 + 0.045
    return Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), "RGB")


# ---------------------------------------------------------------- banner

def banner():
    """
    Wide header image. Text only, no imagery, so nothing can go stale the way a
    screenshot does.
    """
    from PIL import ImageFilter
    Wb, Hb = 1600, 640
    img = Image.new("RGB", (Wb, Hb), BLACK)

    glow = Image.new("L", (Wb, Hb), 0)
    gd = ImageDraw.Draw(glow)
    for r in range(520, 0, -8):
        a = int(28 * (1 - r / 520) ** 2)
        gd.ellipse([Wb // 2 - r, Hb // 2 - r - 40, Wb // 2 + r, Hb // 2 + r - 40], fill=a)
    img = Image.composite(
        Image.new("RGB", (Wb, Hb), (60, 56, 50)), img,
        glow.filter(ImageFilter.GaussianBlur(30)),
    )
    d = ImageDraw.Draw(img)

    for x in range(0, Wb, 80):
        d.line([(x, 0), (x, Hb)], fill=(14, 14, 16), width=1)
    for y in range(0, Hb, 80):
        d.line([(0, y), (Wb, y)], fill=(14, 14, 16), width=1)

    f_big = font(190)
    text = "FLOW"
    spacing = 34
    total = sum(d.textlength(c, font=f_big) for c in text) + spacing * (len(text) - 1)
    x = (Wb - total) // 2
    for c in text:
        d.text((x, 190), c, font=f_big, fill=CREAM)
        x += d.textlength(c, font=f_big) + spacing

    f_sub = font_regular(30)
    sub = "open-source photo editor  ·  imported photos as projects  ·  269 built-in LUTs"
    tw = d.textlength(sub, font=f_sub)
    d.text(((Wb - tw) // 2, 420), sub, font=f_sub, fill=DIM)

    d.line([(Wb // 2 - 300, 500), (Wb // 2 + 300, 500)], fill=LINE, width=2)
    f_tiny = mono(19)
    cap = "269 CUBES  ·  13 EFFECTS  ·  8 HSL BANDS  ·  ANDROID"
    tw = d.textlength(cap, font=f_tiny)
    d.text(((Wb - tw) // 2, 528), cap, font=f_tiny, fill=(94, 92, 88))

    for (cx, cy, dx, dy) in [(60, 60, 1, 1), (Wb - 60, 60, -1, 1),
                             (60, Hb - 60, 1, -1), (Wb - 60, Hb - 60, -1, -1)]:
        d.line([(cx, cy), (cx + 34 * dx, cy)], fill=(70, 68, 64), width=2)
        d.line([(cx, cy), (cx, cy + 34 * dy)], fill=(70, 68, 64), width=2)

    img.save(os.path.join(OUT, "banner.png"), optimize=True)
    print("  banner.png")

# ---------------------------------------------------------------- effects

def effect_lum(img):
    a = np.asarray(img).astype(np.float32)
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


BAYER8 = np.array([
    [0, 32, 8, 40, 2, 34, 10, 42], [48, 16, 56, 24, 50, 18, 58, 26],
    [12, 44, 4, 36, 14, 46, 6, 38], [60, 28, 52, 20, 62, 30, 54, 22],
    [3, 35, 11, 43, 1, 33, 9, 41], [51, 19, 59, 27, 49, 17, 57, 25],
    [15, 47, 7, 39, 13, 45, 5, 37], [63, 31, 55, 23, 61, 29, 53, 21],
], dtype=np.float32)


def fx_dither(img, param=0.5):
    h = img.height
    w = img.width
    g = effect_lum(img).copy()
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
    return Image.fromarray(np.stack([out] * 3, -1).astype(np.uint8), "RGB")


def fx_bayer(img, param=0.5):
    h, w = img.height, img.width
    levels = max(2, min(8, int(round(2 + (1 - param) * 6))))
    t = np.tile(BAYER8, (h // 8 + 1, w // 8 + 1))[:h, :w] / 64.0 - 0.5
    steps = levels - 1
    q = np.clip(np.round(effect_lum(img) / 255.0 * steps + t * 0.9), 0, steps)
    v = np.round(q * 255.0 / steps)
    return Image.fromarray(np.stack([v] * 3, -1).astype(np.uint8), "RGB")


def fx_posterize(img, param=0.5):
    a = np.asarray(img).astype(np.float32)
    levels = max(2, min(10, int(round(2 + (1 - param) * 8))))
    step = 255.0 / (levels - 1)
    return Image.fromarray(np.clip(np.round(a / step) * step, 0, 255).astype(np.uint8), "RGB")


def fx_scanlines(img, param=0.5):
    a = np.asarray(img).astype(np.float32)
    step = max(2, int(round(2 + (1 - param) * 6)))
    out = a.copy()
    out[::step, :, :] *= 0.62
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")


def fx_threshold(img, param=0.5):
    cut = 40 + param * 175
    v = np.where(effect_lum(img) < cut, 0, 255)
    return Image.fromarray(np.stack([v] * 3, -1).astype(np.uint8), "RGB")


def fx_pixelate(img, param=0.35):
    w, h = img.size
    block = max(2, int(round(2 + param * 34)))
    small = img.resize((max(1, w // block), max(1, h // block)), Image.BILINEAR)
    return small.resize((w, h), Image.NEAREST)


def fx_chroma(img, param=0.4):
    a = np.asarray(img).astype(np.float32)
    shift = int(round(1 + param * 18))
    out = a.copy()
    out[..., 0] = np.roll(a[..., 0], shift, axis=1)
    out[..., 2] = np.roll(a[..., 2], -shift, axis=1)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")


def fx_halftone(img, param=0.5):
    a = np.asarray(img).astype(np.float32)
    h, w, _ = a.shape
    lum = effect_lum(img)
    cell = max(3, int(round(3 + (1 - param) * 9)))
    out = np.full((h, w, 3), 255.0, dtype=np.float32)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            y1, x1 = min(y + cell, h), min(x + cell, w)
            dark = 1.0 - lum[y:y1, x:x1].mean() / 255.0
            r = (cell * 0.5) * dark
            if r > 0.4:
                yy, xx = np.mgrid[y:y1, x:x1]
                dd = np.sqrt((xx - (x + cell * 0.5)) ** 2 + (yy - (y + cell * 0.5)) ** 2)
                out[y:y1, x:x1][dd <= r] = 0.0
    return Image.fromarray(out.astype(np.uint8), "RGB")


# ---------------------------------------------------------------- assets

def before_after_gif():
    photo = synthetic_photo(760, 1013)
    edit = graded(photo)

    target_w = 300
    h = int(target_w * photo.height / photo.width)
    orig_s = photo.resize((target_w, h), Image.LANCZOS)
    edit_s = edit.resize((target_w, h), Image.LANCZOS)

    frames = []
    steps = 18
    for i in range(steps):
        e = 0.5 - 0.5 * math.cos(math.pi * i / (steps - 1))
        x = int(target_w * (0.08 + 0.84 * e))
        frame = edit_s.copy()
        frame.paste(orig_s.crop((0, 0, x, h)), (0, 0))
        d = ImageDraw.Draw(frame)
        d.line([(x, 0), (x, h)], fill=CREAM, width=3)
        d.ellipse([x - 13, h // 2 - 13, x + 13, h // 2 + 13], fill=CREAM)
        if x > 70:
            d.text((14, 14), "SOOC", fill=CREAM, font=mono(14))
        if x < target_w - 78:
            d.text((target_w - 78, 14), "FLOW", fill=CREAM, font=mono(14))
        frames.append(frame)

    all_frames = frames + frames[-2:0:-1]
    pal = [f.quantize(colors=64, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG)
           for f in all_frames]
    path = os.path.join(OUT, "before-after.gif")
    pal[0].save(path, save_all=True, append_images=pal[1:],
                duration=80, loop=0, optimize=True, disposal=2)
    print(f"  before-after.gif ({os.path.getsize(path)//1024} KB)")


def effects_grid():
    photo = synthetic_photo(420, 560)
    tiles = [
        ("dither", fx_dither(photo)),
        ("bayer", fx_bayer(photo)),
        ("posterize", fx_posterize(photo)),
        ("scanlines", fx_scanlines(photo)),
        ("threshold", fx_threshold(photo)),
        ("pixelate", fx_pixelate(photo)),
        ("chroma", fx_chroma(photo)),
        ("halftone", fx_halftone(photo)),
    ]
    cw, ch = photo.size
    cols, rows = 4, 2
    pad, label_h = 16, 34
    W = cols * cw + (cols + 1) * pad
    H = rows * (ch + label_h) + (rows + 1) * pad
    sheet = Image.new("RGB", (W, H), BLACK)
    d = ImageDraw.Draw(sheet)
    f = mono(19)
    for i, (name, tile) in enumerate(tiles):
        r, c = divmod(i, cols)
        x = pad + c * (cw + pad)
        y = pad + r * (ch + label_h + pad)
        sheet.paste(tile.resize((cw, ch), Image.LANCZOS), (x, y))
        d.rectangle([x, y, x + cw - 1, y + ch - 1], outline=LINE, width=1)
        d.text((x + 2, y + ch + 8), name, font=f, fill=CREAM)
    sheet.save(os.path.join(OUT, "effects-grid.png"), optimize=True)
    print("  effects-grid.png")


# ---------------------------------------------------------------- drawn UI mockups

def build_mockup(name, painter):
    W, H = 520, 1080
    inset = 14
    shell = Image.new("RGB", (W, H), BLACK)
    d = ImageDraw.Draw(shell)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=34, fill=(9, 9, 11))

    screen = Image.new("RGB", (W - inset * 2, H - inset * 2), (0, 0, 0))
    sd = ImageDraw.Draw(screen)
    painter(sd, screen)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, screen.size[0] - 1, screen.size[1] - 1], radius=26, fill=255)
    shell.paste(screen, (inset, inset), mask)

    ImageDraw.Draw(shell).ellipse(
        [W // 2 - 9, inset + 12, W // 2 + 9, inset + 30], fill=(16, 16, 18))
    shell.save(os.path.join(OUT, name), optimize=True)
    print(f"  {name}")


def paint_home(d, scr):
    w, h = scr.size
    photo = synthetic_photo(300, 400)

    d.text((26, 26), "FLOW", font=font(38), fill=CREAM)
    d.text((28, 72), "photo editor", font=font_regular(15), fill=DIM)

    d.rounded_rectangle([w - 186, 28, w - 104, 68], radius=9, fill=ELEV, outline=LINE)
    d.text((w - 176, 40), "BATCH", font=mono(12), fill=CREAM)
    d.rounded_rectangle([w - 94, 28, w - 26, 68], radius=9, fill=CREAM)
    d.text((w - 84, 40), "IMPORT", font=mono(11), fill=BLACK)

    d.text((26, 112), "PROJECTS · 3", font=mono(12), fill=DIM)

    for i, (v, lab) in enumerate(zip([graded(photo), photo, fx_bayer(photo)], ["A3", "A6", "M2"])):
        x, y = 26 + i * 150, 146
        thumb = v.resize((132, 180), Image.LANCZOS)
        m = Image.new("L", thumb.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 131, 179], radius=10, fill=255)
        scr.paste(thumb, (x, y), m)
        d.rounded_rectangle([x, y, x + 131, y + 179], radius=10, outline=LINE)
        d.text((x + 4, y + 186), lab, font=mono(11), fill=DIM)

    y = 372
    d.rounded_rectangle([26, y, w - 26, y + 140], radius=12, outline=(40, 40, 42), width=2)
    d.text((48, y + 34), "NEW PHOTO", font=font(20), fill=CREAM)
    d.text((48, y + 66), "Import from gallery", font=font_regular(13), fill=DIM)
    d.text((48, y + 100), "269 built-in cubes", font=font_regular(11), fill=(70, 68, 66))

    d.line([(0, h - 72), (w, h - 72)], fill=LINE, width=1)
    for i, mname in enumerate(["Cubes", "Efeitos", "Adjust", "HSL", "Text"]):
        x = 18 + i * 94
        d.ellipse([x + 22, h - 60, x + 46, h - 36],
                  outline=CREAM if i == 0 else (60, 60, 64), width=2)
        d.text((x + 10, h - 28), mname, font=font_regular(11),
               fill=CREAM if i == 0 else DIM)


def paint_editor(d, scr):
    w, h = scr.size
    photo = graded(synthetic_photo(500, 900))

    d.text((22, 30), "X", font=font(20), fill=CREAM)
    d.text((w - 190, 32), "Save", font=font_regular(15), fill=CREAM)
    d.rounded_rectangle([w - 104, 22, w - 26, 62], radius=9, fill=CREAM)
    d.text((w - 92, 34), "Publish", font=font_regular(14), fill=BLACK)

    cw = w - 40
    ch = int(cw * photo.height / photo.width)
    scr.paste(photo.resize((cw, ch), Image.LANCZOS), (20, 84))
    d.rectangle([20, 84, 20 + cw, 84 + ch], outline=(30, 30, 32))

    y = 84 + ch + 26
    d.text((22, y - 4), "INTENSITY", font=mono(10), fill=DIM)
    d.line([(120, y + 4), (w - 60, y + 4)], fill=(50, 50, 52), width=2)
    kx = 120 + int((w - 180) * 0.82)
    d.line([(120, y + 4), (kx, y + 4)], fill=CREAM, width=2)
    d.ellipse([kx - 7, y - 3, kx + 7, y + 11], fill=CREAM)
    d.text((w - 52, y - 4), "82", font=mono(10), fill=CREAM)

    ty = y + 34
    for i, t in enumerate([photo, graded(photo), fx_bayer(photo), fx_posterize(photo)]):
        x = 22 + i * 78
        th = t.resize((68, 68), Image.LANCZOS)
        m = Image.new("L", th.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 67, 67], radius=8, fill=255)
        scr.paste(th, (x, ty), m)
        d.rounded_rectangle([x, ty, x + 67, ty + 67], radius=8,
                            outline=CREAM if i == 1 else LINE,
                            width=2 if i == 1 else 1)

    d.line([(0, h - 72), (w, h - 72)], fill=LINE, width=1)
    for i, mname in enumerate(["Cubes", "Efeitos", "Adjust", "HSL", "Text"]):
        x = 18 + i * 94
        d.ellipse([x + 22, h - 60, x + 46, h - 36],
                  outline=CREAM if i == 2 else (60, 60, 64), width=2)
        d.text((x + 10, h - 28), mname, font=font_regular(11),
               fill=CREAM if i == 2 else DIM)


def paint_batch(d, scr):
    w, h = scr.size
    photo = synthetic_photo(300, 400)

    d.text((30, 26), "<", font=font(20), fill=CREAM)
    d.text((64, 22), "BATCH", font=font(28), fill=CREAM)
    d.text((66, 60), "one look, many photos", font=font_regular(13), fill=DIM)

    d.text((26, 96), "1 - LOOK", font=mono(11), fill=DIM)
    for i, v in enumerate([graded(photo), photo, fx_posterize(photo)]):
        x = 26 + i * 82
        th = v.resize((72, 72), Image.LANCZOS)
        m = Image.new("L", th.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 71, 71], radius=9, fill=255)
        scr.paste(th, (x, 120), m)
        d.rounded_rectangle([x, 120, x + 71, 191], radius=9,
                            outline=CREAM if i == 0 else LINE,
                            width=2 if i == 0 else 1)

    d.text((26, 214), "2 - PHOTOS (6)", font=mono(11), fill=DIM)
    for i in range(6):
        d.text((30, 240 + i * 22), f"{i+1}. IMG_{1000+i}.jpg",
               font=mono(10), fill=(110, 108, 104))

    d.text((26, 388), "3 - CAPTION", font=mono(11), fill=DIM)
    d.rounded_rectangle([26, 410, w - 26, 460], radius=8, outline=LINE)
    d.text((40, 428), "Day 3 - {n}/{total}", font=mono(12), fill=CREAM)

    d.text((26, 486), "4 - EXPORT", font=mono(11), fill=DIM)
    d.rounded_rectangle([26, 512, w - 26, 562], radius=10, fill=CREAM)
    d.text((60, 530), "APPLY TO 6 PHOTOS", font=mono(12), fill=BLACK)

    d.line([(26, 588), (w - 26, 588)], fill=(45, 45, 47), width=4)
    d.line([(26, 588), (26 + int((w - 52) * 0.66), 588)], fill=CREAM, width=4)
    d.text((26, 600), "4/6 - 0 failed", font=mono(10), fill=DIM)

    d.line([(0, h - 72), (w, h - 72)], fill=LINE, width=1)
    for i, mname in enumerate(["Cubes", "Efeitos", "Adjust", "HSL", "Text"]):
        x = 18 + i * 94
        d.ellipse([x + 22, h - 60, x + 46, h - 36], outline=(60, 60, 64), width=2)
        d.text((x + 10, h - 28), mname, font=font_regular(11), fill=DIM)


if __name__ == "__main__":
    print("generating synthetic assets (no personal imagery)...")
    banner()
    before_after_gif()
    effects_grid()
    build_mockup("shot-home.png", paint_home)
    build_mockup("shot-editor.png", paint_editor)
    build_mockup("shot-batch.png", paint_batch)
    print("done.")
