# Validation scripts

Every algorithm in Flow was checked numerically before it shipped. These are the
exact scripts that were run — they are ports of the Kotlin implementations to
numpy, so a wrong algorithm shows up as a wrong number instead of a photo that
looks *almost* right.

Run any of them with plain Python 3 plus numpy (and Pillow for the ones that read
images):

```bash
pip install numpy Pillow
python validate_effects.py
python validate_curves.py
python validate_hsl.py path/to/photo.jpg
python validate_masks_cubes.py
python validate_presets.py
```

| Script | What it proves |
|---|---|
| `validate_effects.py` | Each of the 13 effects produces real structure, not a flat frame. Prints std-dev and unique-value counts and writes a contact sheet. |
| `validate_curves.py` | The Fritsch–Carlson monotone cubic LUT never overshoots and never goes backwards, for identity / S / lift / fade. |
| `validate_hsl.py` | Neutral shifts are lossless, band isolation works, grey is immune, and luma is preserved when desaturating. |
| `validate_masks_cubes.py` | Linear and radial gradients have the expected falloff, `.cube` round-trips exactly, `DOMAIN_*` rescales, malformed files are rejected. |
| `validate_presets.py` | The built-in curve presets are distinct, monotone, and match their names' intent. |

## LUT extraction

`decrypt_xray3.py` and `build_lutpack.py` are the tools that produced
`app/src/main/assets/luts.bin`. See the "How the LUTs were made" section of the
README for the rights discussion — these are here for transparency, not as an
invitation.

## README assets

`make_readme_assets.py` regenerates everything in `docs/img/` — the banner, the
animated before/after, the effect grid, the HSL wheel, and the phone mockups.
