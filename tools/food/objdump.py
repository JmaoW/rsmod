#!/usr/bin/env python3
"""Dumps every obj in the vanilla cache that has an "Eat" or "Drink" inventory op -> tools/food/edibles.json
(and every obj -> tools/food/objs.json).

    python tools/food/objdump.py
"""
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get("FOOD_TOOL_OUT", Path(__file__).parent))
sys.path.insert(0, str(ROOT / "tools/spawns"))
import js5  # noqa: E402

CONFIG_ARCHIVE, OBJ_GROUP = 2, 10


class Buf:
    def __init__(self, d):
        self.d, self.p = d, 0

    def u8(self):
        v = self.d[self.p]; self.p += 1; return v

    def u16(self):
        v = int.from_bytes(self.d[self.p:self.p + 2], "big"); self.p += 2; return v

    def i32(self):
        v = int.from_bytes(self.d[self.p:self.p + 4], "big", signed=True); self.p += 4; return v

    def string(self):
        e = self.d.index(0, self.p)
        s = self.d[self.p:e].decode("cp1252"); self.p = e + 1; return s


def decode(data):
    b = Buf(data)
    o = {"iop": [""] * 5, "params": {}}
    while b.p < len(data):
        c = b.u8()
        if c == 0:
            break
        if c == 1: b.u16()
        elif c == 2: o["name"] = b.string()
        elif c == 3: b.string()
        elif c in (4, 5, 6, 7, 8): b.u16()
        elif c == 11: o["stackable"] = True
        elif c == 12: o["cost"] = b.i32()
        elif c in (13, 14, 27): b.u8()
        elif c == 16: o["members"] = True
        elif c in (23, 25): b.u16(); b.u8()
        elif c in (24, 26): b.u16()
        elif 30 <= c < 35: b.string()
        elif 35 <= c < 40: o["iop"][c - 35] = b.string()
        elif c in (40, 41):
            for _ in range(b.u8()): b.u16(); b.u16()
        elif c == 42: b.u8()
        elif c == 43:
            b.u8()
            while b.u8() != 0: b.string()
        elif c == 65: pass
        elif c == 75: b.u16()
        elif c in (78, 79, 90, 91, 92, 93, 94, 95): b.u16()
        elif c == 97: o["certlink"] = b.u16()
        elif c == 98: o["certtemplate"] = b.u16()
        elif 100 <= c < 110: b.u16(); b.u16()
        elif c in (110, 111, 112): b.u16()
        elif c in (113, 114, 115): b.u8()
        elif c in (139, 140): b.u16()
        elif c == 148: o["placeholderlink"] = b.u16()
        elif c == 149: o["placeholdertemplate"] = b.u16()
        elif c == 200:
            for _ in range(b.u8()): b.u16()
        elif c in (201, 202, 203): b.i32()
        elif c in (204, 205): b.u16()
        elif c == 206: pass
        elif c == 207: b.u16()
        elif c == 208: b.u8()
        elif c == 209: b.u16()
        elif c == 210: o["transformlink"] = b.u16()
        elif c == 211: o["transformtemplate"] = b.u16()
        elif c == 212: b.u8()
        elif c == 249:
            for _ in range(b.u8()):
                is_str = b.u8()
                key = int.from_bytes(bytes([0]) + b.d[b.p:b.p + 3], "big"); b.p += 3
                o["params"][key] = b.string() if is_str else b.i32()
        else:
            raise ValueError(f"unknown obj code {c}")
    return o


def main():
    syms = {}
    for line in (ROOT / ".data/symbols/obj.sym").read_text(encoding="utf-8").splitlines():
        p = line.split("\t")
        if len(p) == 2 and p[0].isdigit():
            syms[int(p[0])] = p[1]
    files = js5.ref_table(CONFIG_ARCHIVE)[OBJ_GROUP]
    group = js5.split_files(js5.read_group(CONFIG_ARCHIVE, OBJ_GROUP), files)
    out = []
    for oid, data in sorted(group.items()):
        o = decode(data)
        if any(op.lower() in ("eat", "drink") for op in o["iop"]) and "certtemplate" not in o \
                and "placeholdertemplate" not in o:
            out.append({"id": oid, "sym": syms.get(oid), "name": o.get("name"), "iop": o["iop"],
                        "members": o.get("members", False), "transform": o.get("transformlink")})
    (OUT / "edibles.json").write_text(json.dumps(out, indent=1), encoding="utf-8")
    print(len(out), "edible objs")
    every = []
    for oid, data in sorted(group.items()):
        o = decode(data)
        every.append({"id": oid, "sym": syms.get(oid), "name": o.get("name"), "iop": o["iop"],
                      "noted": "certtemplate" in o, "placeholder": "placeholdertemplate" in o,
                      "stackable": o.get("stackable", False), "members": o.get("members", False)})
    (OUT / "objs.json").write_text(json.dumps(every), encoding="utf-8")
    print(len(every), "objs")


if __name__ == "__main__":
    main()
