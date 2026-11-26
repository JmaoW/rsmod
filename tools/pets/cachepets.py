#!/usr/bin/env python3
"""Pet data from the vanilla cache: the collection log's pet slots and every follower npc.

    python -I tools/pets/cachepets.py > tools/pets/cache_pets.json
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
for sub in ("tools/spawns", "tools/slayer", "tools/food", "tools/clog"):
    sys.path.insert(0, str(ROOT / sub))
import enums  # noqa: E402
import js5  # noqa: E402
import objdump  # noqa: E402
from coverage import read_struct  # noqa: E402


class Buf:
    def __init__(self, d):
        self.d, self.p = d, 0

    def u8(self):
        self.p += 1
        return self.d[self.p - 1]

    def u16(self):
        self.p += 2
        return int.from_bytes(self.d[self.p - 2:self.p], "big")

    def i32(self):
        self.p += 4
        return int.from_bytes(self.d[self.p - 4:self.p], "big", signed=True)

    def u24(self):
        self.p += 3
        return int.from_bytes(self.d[self.p - 3:self.p], "big")

    def string(self):
        e = self.d.index(0, self.p)
        s = self.d[self.p:e].decode("cp1252")
        self.p = e + 1
        return s


def decode_npc(data):
    b = Buf(data)
    n = {"name": "null", "size": 1, "ops": {}, "follower": False, "heads": [], "params": {}}
    while b.p < len(data):
        c = b.u8()
        if c == 0:
            break
        if c == 1:
            [b.u16() for _ in range(b.u8())]
        elif c == 60:
            n["heads"] = [b.u16() for _ in range(b.u8())]
        elif c == 2:
            n["name"] = b.string()
        elif c == 3:
            b.string()
        elif c == 12:
            n["size"] = b.u8()
        elif c in (100, 101, 200, 201, 202, 205, 206, 207, 208, 209, 212):
            b.u8()
        elif c == 13:
            n["readyanim"] = b.u16()
        elif c == 14:
            n["walkanim"] = b.u16()
        elif c in (15, 16, 74, 75, 76, 77, 78, 79, 97, 98, 103, 114, 116, 124, 204, 211, 213, 214,
                   215, 216):
            b.u16()
        elif c == 17:
            n["walkanim"] = b.u16()
            b.p += 6
        elif c == 18:
            b.u16()
        elif 30 <= c < 35:
            n["ops"][c - 30] = b.string()
        elif c in (40, 41):
            count = b.u8()
            b.p += 4 * count
        elif c == 95:
            b.u16()
        elif c == 122:
            n["follower"] = True
        elif c in (93, 99, 107, 109, 123, 210):
            pass
        elif c == 102:
            flags = b.u8()
            for i in range(flags.bit_length()):
                if flags & (1 << i):
                    b.p += 4 if b.d[b.p] & 0x80 else 2
                    b.p += 1 if b.d[b.p] < 128 else 2
        elif c in (106, 118):
            varbit, varp = b.u16(), b.u16()
            default = b.u16() if c == 118 else None
            count = b.u8()
            forms = [b.u16() for _ in range(count + 1)]
            n["multi"] = {"varbit": varbit, "varp": varp, "default": default, "forms": forms}
        elif c in (115, 117):
            b.p += 8
        elif c == 203:
            count = b.u8() + 1
            b.p += 5 * count
        elif c == 249:
            for _ in range(b.u8()):
                s = b.u8()
                k = b.u24()
                n["params"][k] = b.string() if s else b.i32()
        else:
            raise ValueError(f"npc opcode {c}")
    return n


def syms(name):
    out = {}
    for line in (ROOT / f".data/symbols/{name}.sym").read_text(encoding="utf-8").splitlines():
        p = line.split("\t")
        if len(p) == 2 and p[0].isdigit():
            out[int(p[0])] = p[1]
    return out


def main():
    rt = js5.ref_table(2)
    structs = js5.split_files(js5.read_group(2, 34), rt[34])
    objs = js5.split_files(js5.read_group(2, 10), rt[10])
    npcs = js5.split_files(js5.read_group(2, 9), rt[9])
    osym, nsym = syms("obj"), syms("npc")

    pet_objs = []
    for tab in range(471, 476):
        t = read_struct(structs, tab)
        cats = enums.read_enum(t[683])
        for row in sorted(cats):
            cat = read_struct(structs, cats[row])
            if cat[689].lower() == "all pets":
                items = enums.read_enum(cat[690])
                pet_objs = [items[k] for k in sorted(items)]
    out_objs = []
    for o in pet_objs:
        d = objdump.decode(objs[o])
        out_objs.append({"id": o, "sym": osym.get(o), "name": d.get("name"), "iop": d["iop"],
                         "params": d["params"]})
    followers = {}
    for nid, data in npcs.items():
        n = decode_npc(data)
        if n["follower"]:
            n["sym"] = nsym.get(nid)
            followers[nid] = n
    print(json.dumps({"pets": out_objs, "followers": followers}, indent=1))


if __name__ == "__main__":
    main()
