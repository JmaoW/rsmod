#!/usr/bin/env python3
"""Reads every npc config out of the vanilla cache -> tools/slayer/cache_npcs.json.

    python tools/slayer/npcdump.py

Only the fields the Slayer tools need: name, combat level, category (opcode 18) and params
(opcode 249, e.g. slayer level / xp). Opcodes are those of NpcTypeDecoder; the vanilla cache has
no server-only codes (200-216).
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/spawns"))
import js5  # noqa: E402

TOOL_DIR = Path(__file__).resolve().parent
NPC_GROUP = 9


class Buf:
    def __init__(self, data):
        self.d, self.p = data, 0

    def u8(self):
        v = self.d[self.p]
        self.p += 1
        return v

    def u16(self):
        v = int.from_bytes(self.d[self.p:self.p + 2], "big")
        self.p += 2
        return v

    def i32(self):
        v = int.from_bytes(self.d[self.p:self.p + 4], "big", signed=True)
        self.p += 4
        return v

    def u24(self):
        v = int.from_bytes(self.d[self.p:self.p + 3], "big")
        self.p += 3
        return v

    def skip(self, n):
        self.p += n

    def string(self):
        end = self.d.index(0, self.p)
        s = self.d[self.p:end].decode("cp1252")
        self.p = end + 1
        return s

    def peek(self):
        return self.d[self.p]


def decode(data):
    b = Buf(data)
    npc = {"name": "null", "category": None, "level": 0, "params": {}, "ops": {}}
    while b.p < len(data):
        c = b.u8()
        if c == 0:
            break
        if c in (1, 60):
            b.skip(2 * b.u8())
        elif c == 2:
            npc["name"] = b.string()
        elif c == 3:
            b.string()
        elif c in (12, 100, 101, 200, 201, 202, 205, 206, 207, 208, 209, 212):
            b.skip(1)
        elif c in (13, 14, 15, 16, 74, 75, 76, 77, 78, 79, 97, 98, 103, 114, 116, 124, 204, 211,
                   213, 214, 215, 216):
            b.skip(2)
        elif c == 17:
            b.skip(8)
        elif c == 18:
            npc["category"] = b.u16()
        elif 30 <= c < 35:
            npc["ops"][c - 30] = b.string()
        elif c in (40, 41):
            b.skip(4 * b.u8())
        elif c == 95:
            npc["level"] = b.u16()
        elif c in (93, 99, 107, 109, 122, 123, 210):
            pass
        elif c == 102:
            flags = b.u8()
            count = flags.bit_length()
            for i in range(count):
                if flags & (1 << i):
                    # nullable large smart, then small smart plus one
                    if b.peek() & 0x80:
                        b.skip(4)
                    else:
                        b.skip(2)
                    b.skip(1 if b.peek() < 128 else 2)
        elif c in (106, 118):
            b.skip(4)
            if c == 118:
                b.skip(2)
            b.skip(2 * (b.u8() + 1))
        elif c == 115:
            b.skip(8)
        elif c == 117:
            b.skip(8)
        elif c == 203:
            b.skip(5 * (b.u8() + 1))
        elif c == 249:
            for _ in range(b.u8()):
                is_string = b.u8()
                key = b.u24()
                npc["params"][key] = b.string() if is_string else b.i32()
        else:
            raise ValueError(f"unknown npc opcode {c} at {b.p}")
    return npc


def main():
    files = js5.ref_table(2)[NPC_GROUP]
    group = js5.split_files(js5.read_group(2, NPC_GROUP), files)
    out = {}
    for npc_id, data in group.items():
        out[str(npc_id)] = decode(data)
    (TOOL_DIR / "cache_npcs.json").write_text(json.dumps(out), encoding="utf-8")
    cat = sum(1 for n in out.values() if n["category"] is not None)
    print(f"{len(out)} npcs, {cat} with a category")


if __name__ == "__main__":
    main()
