"""Writes the Soul Wars arena's playable tiles: every tile walkable from either team's graveyard portal
(team barriers and other interactive objects count as open). The arena is ringed by a wall of map-edge
forest, so ground behind that wall is not reached and stays out.

The arena's bridges (to the middle island) are on the map as "bridge" tiles: a tile flagged 2 on level 1
is walked on at level 0 using the level 1 tile's own flags, while the level 0 flags under it (water)
stay blocked. `collision.py` does not do that, so it is applied here before the flood.

Usage: python tools/dungeons/soulwars_arena.py [OUTPUT]
Default output: the isle-of-souls module's pvp/soul_wars_arena.txt resource, one line per z:
`z x0-x1 x0-x1 ...` (inclusive x ranges), level 0.
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "spawns"))
import collision as K

OUT = os.path.join(
    os.path.dirname(__file__), "..", "..", "content", "areas", "misc", "isle-of-souls", "src", "main",
    "resources", "org", "rsmod", "content", "areas", "misc", "isleofsouls", "pvp", "soul_wars_arena.txt",
)
PORTALS = [(2142, 2905), (2272, 2919)]  # arena arrival tiles of the blue and red portals

for mx in (33, 34, 35):  # the arena's three map squares, z 45
    square = K._terrain.square(mx, 45)
    for (level, lx, lz), flags in list(square.items()):
        if level == 1 and flags & 2:
            square[(0, lx, lz)] = flags & ~2

K.SOFT = True
tiles = set()
for x, z in PORTALS:
    tiles |= K.flood(0, x, z, 400000)

rows = {}
for x, z in tiles:
    rows.setdefault(z, []).append(x)
lines = []
for z in sorted(rows):
    xs = sorted(rows[z])
    ranges, start, prev = [], xs[0], xs[0]
    for x in xs[1:]:
        if x != prev + 1:
            ranges.append((start, prev))
            start = x
        prev = x
    ranges.append((start, prev))
    lines.append("%d %s" % (z, " ".join("%d-%d" % r for r in ranges)))

out = sys.argv[1] if len(sys.argv) > 1 else OUT
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", newline="\n") as f:
    f.write("\n".join(lines) + "\n")
print(len(tiles), "tiles,", len(lines), "rows ->", out)
