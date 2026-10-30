"""Writes the patched `[proc,wilderness_level]` (clientscript 384) for the Isle of Souls PvP overlay.

The client works out the Wilderness level from the player's coordinates, with the Wilderness's regions
hard-coded in the script, and it is only called by the pvp_icons overlay and its combat range text.
The patch adds a block at the start that returns the value of a server-sent varp when it is above 0,
so the server can turn the overlay on anywhere:

    get_varp <varp>; iconst 0; if_gt 1; jump 2; get_varp <varp>; return

(`if_gt 1` skips the jump when the varp is above 0; otherwise `jump 2` skips the two new instructions
and the cache's own script runs as before.) Inserting at the very start moves no jump target, as all
jumps in the script are relative and land inside it.

Usage: python tools/cs2/patch_wilderness_level.py [VARP_ID]
VARP_ID is `isle_pvp_overlay` in .data/symbols/varp.sym (default 65186).
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from cs2lib import raw, parse, build

OUT = os.path.join(
    os.path.dirname(__file__), "..", "..", "content", "areas", "misc", "isle-of-souls", "src", "main",
    "resources", "org", "rsmod", "content", "areas", "misc", "isleofsouls", "pvp", "cs2",
    "[proc,wilderness_level]",
)
SCRIPT = 384
GET_VARP, ICONST, IF_GT, JUMP, RETURN = 1, 0, 10, 6, 21

varp = int(sys.argv[1]) if len(sys.argv) > 1 else 65186
original = raw(SCRIPT)
script = parse(original)
assert build(script) == original
assert script["ins"][0] != (GET_VARP, varp), "already patched"
block = [(GET_VARP, varp), (ICONST, 0), (IF_GT, 1), (JUMP, 2), (GET_VARP, varp), (RETURN, 0)]
script["ins"][0:0] = block
out = build(script)
assert parse(out)["ins"][: len(block)] == block
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as f:
    f.write(out)
print(len(original), "->", len(out), "bytes,", len(script["ins"]), "instructions ->", os.path.normpath(OUT))
