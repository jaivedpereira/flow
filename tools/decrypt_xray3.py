#!/usr/bin/env python3
"""
Decrypt VSCO .xray colour cubes — final layout.

Recovered from com.vsco.imaging.colorcubes + com.vsco.crypto:
  asset -> AES-128-CBC/PKCS7 -> zlib inflate -> header + LUT(s)

LUT: 17^3 entries, INTERLEAVED RGB (u16 little-endian), 29478 bytes each.
Header length = total - n_luts * 29478 (starts with a 16-byte hash then metadata
strings: preset group, name, code).
"""
import os
import sys
import zlib
import struct
from Crypto.Cipher import AES

KEY = bytes.fromhex("00ff59DE2D761C28E42387A31A41422C")
IV = bytes.fromhex("217344b492d2de9a2c713180d1dbeb7e")
N = 17
N_COLORS = N * N * N
LUT_BYTES = N_COLORS * 6  # 29478

SRC = sys.argv[1]
DST = sys.argv[2]
os.makedirs(DST, exist_ok=True)

def decrypt(data):
    plain = AES.new(KEY, AES.MODE_CBC, IV).decrypt(data)
    if plain:
        pad = plain[-1]
        if 1 <= pad <= 16 and plain.endswith(bytes([pad]) * pad):
            plain = plain[:-pad]
    return plain

def parse_header(h: bytes):
    """Pull human-readable name/group out of the header blob."""
    txt = h.decode("latin-1", "replace")
    parts = [p for p in txt.split("\x00") if p.strip()]
    name = ""
    for p in parts:
        if "/" in p and len(p) < 40:
            name = p
            break
    return name

def write_cube(path, vals, name):
    out = [f"# {name}\n", f"LUT_3D_SIZE {N}\n"]
    for i in range(N_COLORS):
        r = vals[i * 3] / 65535.0
        g = vals[i * 3 + 1] / 65535.0
        b = vals[i * 3 + 2] / 65535.0
        out.append(f"{r:.6f} {g:.6f} {b:.6f}\n")
    open(path, "wb").write("".join(out).encode("utf-8"))

ok = 0
fail = 0
log = []
manifest = []
for f in sorted(os.listdir(SRC)):
    if not f.endswith(".xray"):
        continue
    key = f[:-5]
    raw = open(os.path.join(SRC, f), "rb").read()
    try:
        dec = decrypt(raw)
        try:
            blob = zlib.decompress(dec)
        except Exception:
            blob = dec
        n_luts = len(blob) // LUT_BYTES
        if n_luts < 1:
            log.append(f"  FAIL {key}: too small ({len(blob)})")
            fail += 1
            continue
        header_len = len(blob) - n_luts * LUT_BYTES
        body = blob[header_len:header_len + LUT_BYTES]
        vals = struct.unpack_from("<%dH" % (N_COLORS * 3), body, 0)

        # sanity: at least 98% within 0..65535 is guaranteed by u16;
        # instead check that shadows are dark and highlights are bright.
        lo = (vals[0] + vals[1] + vals[2]) / 3
        hi_idx = (N_COLORS - 1) * 3
        hi = (vals[hi_idx] + vals[hi_idx + 1] + vals[hi_idx + 2]) / 3
        if not (lo < 12000 and hi > 45000):
            log.append(f"  WARN {key}: suspicious range lo={lo:.0f} hi={hi:.0f}")
            fail += 1
            continue

        disp = parse_header(blob[:header_len]) or key
        write_cube(os.path.join(DST, key + ".cube"), vals, disp)
        manifest.append({"key": key, "name": disp, "n_luts": n_luts})
        ok += 1
        if ok <= 6:
            log.append(f"  OK   {key:12} name={disp!r} luts={n_luts} hdr={header_len}")
    except Exception as e:
        log.append(f"  ERR  {key}: {type(e).__name__} {e}")
        fail += 1

for l in log:
    print(l)
print(f"\nconverted: {ok}   failed: {fail}")

import json
json.dump(manifest, open(os.path.join(DST, "_manifest.json"), "w"), indent=1)
print("manifest written")
