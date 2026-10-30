"""Writes the patched `[proc,pvp_icons_layout]` (clientscript 386) for the Isle of Souls PvP overlay.

The overlay shows a skull (`pvp_icons:46`, sprite 1046) with a red "no" circle over it (`pvp_icons:47`,
sprite 1047). In the ordinary world layout the script makes the circle opaque every time it draws, so
the skull always reads "disabled". The patch adds a block just before the script's final `return` that
hides the circle (`if_settrans 255`) while the server-sent varp is above 0, which is while the player is
in a PvP zone:

    get_varp <varp>; iconst 0; if_gt 1; jump 3; iconst 255; iconst <pvp_icons:47>; if_settrans

(`if_gt 1` skips the jump when the varp is above 0; otherwise `jump 3` skips the three instructions.)
Every jump in the script that went to the final `return` now lands on the block, so it runs on every path
except the early return for world 80, where the overlay is hidden anyway.

Usage: python tools/cs2/patch_pvp_icons_layout.py [VARP_ID]
VARP_ID is `isle_pvp_overlay` in .data/symbols/varp.sym (default 65186).
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from cs2lib import raw, parse, build

OUT = os.path.join(
    os.path.dirname(__file__), "..", "..", "content", "areas", "misc", "isle-of-souls", "src", "main",
    "resources", "org", "rsmod", "content", "areas", "misc", "isleofsouls", "pvp", "cs2",
    "[proc,pvp_icons_layout]",
)
SCRIPT = 386
GET_VARP, ICONST, IF_GT, JUMP, RETURN, IF_SETTRANS = 1, 0, 10, 6, 21, 2103
CIRCLE = (90 << 16) | 47  # pvp_icons:47

varp = int(sys.argv[1]) if len(sys.argv) > 1 else 65186
original = raw(SCRIPT)
script = parse(original)
assert build(script) == original
ins = script["ins"]
last = len(ins) - 1
assert ins[last] == (RETURN, 0), "the script must end with a return"
block = [
    (GET_VARP, varp),
    (ICONST, 0),
    (IF_GT, 1),
    (JUMP, 3),
    (ICONST, 255),
    (ICONST, CIRCLE),
    (IF_SETTRANS, 0),
]
assert ins[last - len(block) : last] != block, "already patched"
ins[last:last] = block
out = build(script)
assert parse(out)["ins"][last : last + len(block)] == block
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as f:
    f.write(out)
print(len(original), "->", len(out), "bytes,", len(ins), "instructions ->", os.path.normpath(OUT))
