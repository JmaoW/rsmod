"""Reads the vanilla cache and lists the interactive locs (ladders, stairs, doors...) inside a rectangle.

Usage: python tools/dungeons/census.py X0 Z0 X1 Z1 [--all]   (tile coordinates, inclusive)
"""
import json, os, struct, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "spawns"))
import js5  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
XTEA = json.load(open(os.path.join(ROOT, ".data", "cache", "xteas.json")))
SQ = {x["name"]: x for x in XTEA}
LOC_SYM = {}
for line in open(os.path.join(ROOT, ".data", "symbols", "loc.sym")):
    p = line.rstrip("\n").split("\t")
    if len(p) == 2:
        LOC_SYM[int(p[0])] = p[1]


def cstr(d, p):
    e = d.index(0, p)
    return d[p:e].decode("latin1"), e + 1


def parse_loc(d):
    o = {"name": "", "ops": [None] * 5, "multi": [], "sx": 1, "sz": 1, "solid": True, "block": 0, "shapes": []}
    p = 0
    while p < len(d):
        op = d[p]; p += 1
        if op == 0: break
        elif op == 1:
            n = d[p]; o["shapes"] = [d[p + 1 + 3 * k + 2] for k in range(n)]; p += 3 * n + 1
        elif op == 2: o["name"], p = cstr(d, p)
        elif op == 3: _, p = cstr(d, p)
        elif op == 5:
            if not o["shapes"]: o["shapes"] = [10]
            p += 2 * d[p] + 1
        elif op == 14: o["sx"] = d[p]; p += 1
        elif op == 15: o["sz"] = d[p]; p += 1
        elif op == 69: o["block"] = d[p]; p += 1
        elif op in (19, 28, 29, 39, 75, 81): p += 1
        elif op == 17: o["solid"] = False
        elif op in (18, 21, 22, 23, 27, 62, 64, 73, 74, 89): pass
        elif op in (24, 65, 66, 67, 68, 70, 71, 72, 82): p += 2
        elif 30 <= op <= 34:
            o["ops"][op - 30], p = cstr(d, p)
        elif op in (40, 41): p += 4 * d[p] + 1
        elif op == 42: p += d[p] + 1
        elif op == 60: p += 2
        elif op == 61: p += 2
        elif op in (77, 92):
            p += 4
            if op == 92: p += 2
            n = d[p]; p += 1
            o["multi"] = [int.from_bytes(d[p + 2 * k:p + 2 * k + 2], "big") for k in range(n + 1)]
            p += 2 * (n + 1)
        elif op == 78: p += 4
        elif op == 79:
            p += 6; n = d[p]; p += 1 + 2 * n
        elif op == 249:
            n = d[p]; p += 1
            for _ in range(n):
                s = d[p]; p += 4
                if s: _, p = cstr(d, p)
                else: p += 4
        else:
            break
    return o


_locs = None
def locs():
    global _locs
    if _locs is None:
        files = js5.split_files(js5.read_group(2, 6), js5.ref_table(2)[6])
        _locs = {i: parse_loc(b) for i, b in files.items()}
    return _locs


def xtea(data, key):
    out = bytearray()
    for i in range(0, len(data) - len(data) % 8, 8):
        v0, v1 = struct.unpack(">II", data[i:i + 8])
        s = 0xC6EF3720
        for _ in range(32):
            v1 = (v1 - ((((v0 << 4) ^ (v0 >> 5)) + v0) ^ (s + key[(s >> 11) & 3]))) & 0xFFFFFFFF
            s = (s - 0x9E3779B9) & 0xFFFFFFFF
            v0 = (v0 - ((((v1 << 4) ^ (v1 >> 5)) + v1) ^ (s + key[s & 3]))) & 0xFFFFFFFF
        out += struct.pack(">II", v0, v1)
    return bytes(out) + data[len(out):]


def smart(d, p):
    if d[p] < 128: return d[p], p + 1
    return int.from_bytes(d[p:p + 2], "big") - 32768, p + 2


def smart_incr(d, p):
    total = 0
    while True:
        v, p = smart(d, p)
        if v != 32767:
            return total + v, p
        total += 32767


def square_locs(mx, mz):
    e = SQ.get(f"l{mx}_{mz}")
    if not e: return []
    raw = js5.read_raw(5, e["group"])
    comp = raw[0]; ln = int.from_bytes(raw[1:5], "big")
    body = raw[5:5 + ln + 4] if comp else raw[5:5 + ln]
    if comp == 0:
        data = xtea(raw[5:5 + ln], e["key"])
    else:
        enc = xtea(raw[5:5 + ln + 4], e["key"])
        ulen = int.from_bytes(enc[:4], "big")
        import gzip, bz2
        data = bz2.decompress(b"BZh1" + enc[4:]) if comp == 1 else gzip.decompress(enc[4:])
    out = []
    p = 0; lid = -1
    while p < len(data):
        d, p = smart_incr(data, p)
        if d == 0: break
        lid += d
        pos = 0
        while True:
            pd, p = smart(data, p)
            if pd == 0: break
            pos += pd - 1
            a = data[p]; p += 1
            out.append((lid, mx * 64 + ((pos >> 6) & 63), mz * 64 + (pos & 63), pos >> 12, a >> 2, a & 3))
    return out


def census(x0, z0, x1, z1):
    L = locs()
    res = []
    for mx in range(x0 >> 6, (x1 >> 6) + 1):
        for mz in range(z0 >> 6, (z1 >> 6) + 1):
            for (lid, x, z, lvl, shape, rot) in square_locs(mx, mz):
                if x0 <= x <= x1 and z0 <= z <= z1:
                    res.append((lid, x, z, lvl, shape, rot))
    return res


if __name__ == "__main__":
    x0, z0, x1, z1 = map(int, sys.argv[1:5])
    show_all = "--all" in sys.argv
    L = locs()
    byname = collections.defaultdict(list)
    for (lid, x, z, lvl, shape, rot) in census(x0, z0, x1, z1):
        t = L.get(lid)
        if not t: continue
        ops = [o for o in t["ops"] if o and o.lower() not in ("examine",)]
        if not ops and not t["multi"] and not show_all: continue
        byname[(lid, LOC_SYM.get(lid, "?"), t["name"], "/".join(ops))].append((x, z, lvl, rot))
    for k, v in sorted(byname.items(), key=lambda kv: kv[0][1]):
        print(k[0], k[1], f"'{k[2]}'", k[3], len(v), v[:6])
