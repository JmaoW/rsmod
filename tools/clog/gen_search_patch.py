#!/usr/bin/env python3
"""Builds the patched client script that makes the collection log search show what was collected.

    python tools/clog/gen_search_patch.py

The log's search draws every result through `[proc,collection_search_display_item]` (4098) with the
count `-1`, which draws the item grey. The count is meant to arrive later from the server, which
would have to run `[clientscript,collection_delayed_transmit]` (4100) once per obtained item; the
server cannot know what was searched, so that never happens. This patch makes the proc read the
count itself, from the log inventory (620) the server already keeps, exactly as the category view
does. The inserted block, run after the proc has mapped an aliased item to the obj the log stores
(`enum 3721`), is:

    if count == -1:
        total = inv_total(620, obj)
        if total > 0:
            count = total

The rest of the proc then draws the item opaque with its count and the "Check" message. Writes
`content/interfaces/collection-log/src/main/resources/.../cs2/[proc,collection_search_display_item]`.
Needs `./gradlew packCache` to reach the cache the client reads.
"""

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/spawns"))
import js5  # noqa: E402

SCRIPT_ID = 4098
OUT = (
    ROOT
    / "content/interfaces/collection-log/src/main/resources/org/rsmod/content/interfaces/collectionlog/cs2"
    / "[proc,collection_search_display_item]"
)
JUMPS = (6, 7, 8, 9, 10, 31, 32)
INSERT_AT = 48  # right after `i4 = enum_3721(i4) ?: i4`
INT_ARG_COUNT = 8  # `count` is argument 5, `obj` argument 4, local 9 is free here


def parse(b):
    sw_len = struct.unpack(">H", b[-2:])[0]
    sw_start = len(b) - 2 - sw_len
    f = sw_start - 12
    n, il, sl, ia, sa = struct.unpack(">iHHHH", b[f:f + 12])
    p = 0
    while b[p] != 0:
        p += 1
    name = b[:p].decode("latin1")
    p += 1
    ins = []
    while p < f:
        op = struct.unpack(">H", b[p:p + 2])[0]
        p += 2
        if op == 3:
            q = p
            while b[q] != 0:
                q += 1
            val = b[p:q].decode("latin1")
            p = q + 1
        elif op < 100 and op not in (21, 38, 39):
            val = struct.unpack(">i", b[p:p + 4])[0]
            p += 4
        else:
            val = b[p]
            p += 1
        ins.append((op, val))
    assert p == f and len(ins) == n, (p, f, len(ins), n)
    return dict(name=name, ins=ins, il=il, sl=sl, ia=ia, sa=sa, sw=b[sw_start:len(b) - 2])


def build(s):
    out = bytearray(s["name"].encode("latin1") + b"\0")
    for op, val in s["ins"]:
        out += struct.pack(">H", op)
        if op == 3:
            out += val.encode("latin1") + b"\0"
        elif op < 100 and op not in (21, 38, 39):
            out += struct.pack(">i", val)
        else:
            out += bytes([val])
    out += struct.pack(">iHHHH", len(s["ins"]), s["il"], s["sl"], s["ia"], s["sa"])
    out += s["sw"] + struct.pack(">H", len(s["sw"]))
    return bytes(out)


def main():
    original = js5.read_group(12, SCRIPT_ID)
    s = parse(original)
    assert build(s) == original, "script does not round-trip"
    ins = s["ins"]
    # Sanity-check that the script is the one the patch was written for.
    assert ins[38:48] == [(0, 3721), (33, 4), (3408, 0), (34, 8), (33, 8), (0, -1), (7, 1), (6, 2), (33, 8), (34, 4)], ins[38:48]
    assert ins[48:51] == [(0, 79), (0, 79), (0, 2108)], ins[48:51]

    block = [
        (33, 5), (0, -1), (7, 10),  # if count != -1, skip the block
        (0, 620), (33, 4), (3303, 0), (34, 9),  # local9 = inv_total(620, obj)
        (33, 9), (0, 0), (10, 1),  # if local9 > 0
        (6, 2),  # otherwise skip the assignment
        (33, 9), (34, 5),  # count = local9
    ]
    for i, (op, v) in enumerate(ins):
        if op in JUMPS:
            target = i + 1 + v
            crosses = (i < INSERT_AT <= target) or (target <= INSERT_AT <= i)
            assert not crosses or target == INSERT_AT and i < INSERT_AT, ("jump crosses", i, target)
    ins[INSERT_AT:INSERT_AT] = block
    out = build(s)
    parse(out)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(out)
    print(f"4098: {len(original)} -> {len(out)} bytes, {len(ins)} instructions")


if __name__ == "__main__":
    main()
