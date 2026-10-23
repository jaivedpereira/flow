#!/usr/bin/env python3
"""
Build the Flow LUT pack from decrypted VSCO colours.

Binary layout (little-endian):
    "FLOWLUT1"           8 bytes magic
    u32  count
    count * {
        u16 key_len,  key   (UTF-8, e.g. "a1")
        u16 name_len, name  (UTF-8, e.g. "A1 / Analog")
        u16 group_len,group (UTF-8, e.g. "Analog")
        u32  data_len
        data  (17^3 * 6 bytes, interleaved u16 RGB)
    }
"""
import os
import sys
import zlib
import struct
from Crypto.Cipher import AES

KEY = bytes.fromhex("00ff59DE2D761C28E42387A31A41422C")
IV = bytes.fromhex("217344b492d2de9a2c713180d1dbeb7e")
N = 17
NC = N * N * N
LUT_BYTES = NC * 6

SRC = sys.argv[1]
OUT = sys.argv[2]

def decrypt(data):
    plain = AES.new(KEY, AES.MODE_CBC, IV).decrypt(data)
    if plain:
        pad = plain[-1]
        if 1 <= pad <= 16 and plain.endswith(bytes([pad]) * pad):
            plain = plain[:-pad]
    return plain

GROUP_HINTS = {
    "Legacy", "Analog", "Aesthetic", "Essentials", "Portrait", "Landscape",
    "Architecture", "Experimental", "Black & White", "Black and White",
    "Minimalist", "Film", "Street", "Nature", "Fashion", "Food", "Travel",
}

def parse_header(h):
    """Return (name, group) from the metadata strings inside the header."""
    txt = h.decode("latin-1", "replace")
    parts = [p.strip() for p in txt.split("\x00") if p.strip()]
    clean = []
    for p in parts:
        # skip binary noise and long internal ids
        if all(32 <= ord(c) < 127 for c in p) and 1 < len(p) < 48:
            clean.append(p)
    name = ""
    group = ""
    for p in clean:
        if " / " in p:
            name = p
            break
    for p in clean:
        if p in GROUP_HINTS or (p in ("VSCO", "vsco") and len(clean) > 3):
            continue
        if p and not p.isdigit() and not p.endswith("_vsco") and "presets_vsco" not in p:
            if p.lower() not in ("vsco tools", "vscotools", "tools"):
                if len(p) > 2 and p not in ("VSCO",):
                    group = p
    return name, group

entries = []
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
            print(f"  skip {key}: too small")
            continue
        hdr_len = len(blob) - n_luts * LUT_BYTES
        body = blob[hdr_len:hdr_len + LUT_BYTES]
        name, group = parse_header(blob[:hdr_len])
        if not name:
            name = key.upper()
        if " / " in name:
            group = name.split(" / ", 1)[1].strip()
        entries.append((key, name, group or "VSCO", body))
    except Exception as e:
        print(f"  err {key}: {type(e).__name__}")

pack = bytearray()
pack += b"FLOWLUT1"
pack += struct.pack("<I", len(entries))
for key, name, group, data in entries:
    kb, nb, gb = key.encode(), name.encode(), group.encode()
    pack += struct.pack("<HHH", len(kb), len(nb), len(gb))
    pack += kb + nb + gb
    pack += struct.pack("<I", len(data))
    pack += data

open(OUT, "wb").write(pack)
print(f"entries: {len(entries)}")
print(f"pack: {len(pack):,} bytes ({len(pack)/1024/1024:.1f} MB) -> {OUT}")
print("sample:", [(k, n, g) for k, n, g, _ in entries[:6]])
