"""Which tiles a player can walk to on the isle and in the places it leads to.

A walk flood fill (tools/dungeons/collision.py, doors and other interactive objects passable) from a
start tile in each area: the isle itself, the rune essence mine, the Mining Guild, Mor Ul Rek and every
dungeon in Dungeon.kt. Agility shortcuts inside the dungeons (DungeonCrossings.kt) are crossed too.
This is the same model as tools/slayer/coverage.py, so it shares its limits: ladders and stairs inside a
dungeon are not followed, and the Catacombs are undercounted.

Used by gen_skills.py; run on its own to print the areas:  python tools/wiki/reach.py
"""
import collections
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "dungeons"))
import collision as K  # noqa: E402
import census as C  # noqa: E402

K.SOFT = True

ISLE_KT = os.path.join(ROOT, "content", "areas", "misc", "isle-of-souls", "src", "main", "kotlin", "org", "rsmod",
                       "content", "areas", "misc", "isleofsouls")
DUNGEON_KT = os.path.join(ISLE_KT, "dungeons", "Dungeon.kt")
CROSSINGS_KT = os.path.join(ISLE_KT, "dungeons", "DungeonCrossings.kt")

# Areas that are not in Dungeon.kt: (key, title, level, x, z). Order matters: a tile that two areas
# reach belongs to the first.
EXTRA_AREAS = [
    ("isle", "Isle of Souls", 0, 2210, 2830),
    ("essence", "Rune essence mine", 0, 2910, 4830),
    ("guild", "Mining Guild", 0, 3019, 9737),
    ("morulrek", "Mor Ul Rek", 0, 2479, 5175),
]

# Start tiles that differ from Dungeon.kt's arrival (the arrival is blocked or misses part of the area),
# from tools/slayer/coverage.py.
START_OVERRIDES = {
    "taverley": [(0, 2884, 9797)],
    "edgeville": [(0, 3097, 9867)],
    "revenant": [(0, 3217, 10058), (0, 3244, 10214)],
    "brimhaven": [(0, 2713, 9564), (2, 2643, 9594), (2, 2637, 9510), (2, 2671, 9583),
                  (0, 2650, 9591), (0, 2637, 9517), (0, 2673, 9583)],
    "mos": [(0, 3748, 9373)],
}

# Titles that differ from Dungeon.kt's, where this tool splits an area off on its own.
TITLE_OVERRIDES = {"karamja": "Karamja Dungeon"}

RADIUS = 2  # a thing counts as reachable when a walkable tile is this close (rocks and npcs stand on blocked tiles)


def dungeons():
    """(key, title, [(level, x, z)]) for every Dungeon in Dungeon.kt."""
    src = open(DUNGEON_KT, encoding="utf-8").read()
    out = []
    for m in re.finditer(r'key = "(\w+)",\s*title = "([^"]+)",.*?arrival = CoordGrid\((\d+), (\d+), (\d+)\)', src, re.S):
        key, title, x, z, lvl = m.groups()
        out.append((key, TITLE_OVERRIDES.get(key, title), START_OVERRIDES.get(key, [(int(lvl), int(x), int(z))])))
    return out


def crossings():
    src = open(CROSSINGS_KT, encoding="utf-8").read()
    out = []
    for m in re.finditer(r'key = "(\w+)".*?a = t\((\d+), (\d+)(?:, (\d+))?\),\s*b = t\((\d+), (\d+)(?:, (\d+))?\)', src, re.S):
        k, ax, az, al, bx, bz, bl = m.groups()
        out.append(((int(al or 0), int(ax), int(az)), (int(bl or 0), int(bx), int(bz))))
    return out


def _free_near(level, x, z):
    for r in range(0, 4):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if not K.blocked_tile(level, x + dx, z + dz):
                    return (x + dx, z + dz)
    return None


def _flood(starts, cross):
    tiles = collections.defaultdict(set)
    for (lvl, x, z) in starts:
        free = _free_near(lvl, x, z)
        if free is None:
            print(f"  warning: no free tile near {(x, z, lvl)}", file=sys.stderr)
            continue
        tiles[lvl] |= K.flood(lvl, free[0], free[1], 600000)
    changed = True
    while changed:
        changed = False
        for a, b in cross:
            for src, dst in ((a, b), (b, a)):
                if (src[1], src[2]) in tiles.get(src[0], ()) and (dst[1], dst[2]) not in tiles.get(dst[0], ()):
                    tiles[dst[0]] |= K.flood(dst[0], dst[1], dst[2], 600000)
                    changed = True
    return tiles


class Reach:
    """Every area's walkable tiles. `where(level, x, z)` says which area (key) reaches a tile, or None."""

    def __init__(self):
        cross = crossings()
        self.areas = []  # (key, title, {level: set of tiles})
        for key, title, lvl, x, z in EXTRA_AREAS:
            self.areas.append((key, title, _flood([(lvl, x, z)], [])))
        for key, title, starts in dungeons():
            self.areas.append((key, title, _flood(starts, cross)))
        self.titles = {k: t for k, t, _ in self.areas}

    def where(self, level, x, z, size_x=1, size_z=1, radius=RADIUS):
        for key, _, tiles in self.areas:
            lt = tiles.get(level)
            if not lt:
                continue
            for dx in range(-radius, size_x + radius):
                for dz in range(-radius, size_z + radius):
                    if (x + dx, z + dz) in lt:
                        return key
        return None

    def locs(self, wanted):
        """[(loc id, level, x, z, area key)] for every map loc whose id is in `wanted` that a player can reach.
        Only squares the flood fill loaded are searched, which covers every reachable tile."""
        L = C.locs()
        out = []
        for (lvl, x, z), entries in list(K._locs_at.items()):
            for (lid, shape, rot) in entries:
                if lid not in wanted:
                    continue
                t = L.get(lid, {})
                sx, sz = (t.get("sx", 1), t.get("sz", 1)) if rot % 2 == 0 else (t.get("sz", 1), t.get("sx", 1))
                area = self.where(lvl, x, z, sx, sz)
                if area:
                    out.append((lid, lvl, x, z, area))
        return out


def clusters(points, gap=12):
    """Groups (level, x, z, payload) points that lie within `gap` tiles of each other (single linkage)."""
    groups = []
    for p in sorted(points):
        hit = [g for g in groups if g[0][0] == p[0] and any(abs(q[1] - p[1]) <= gap and abs(q[2] - p[2]) <= gap for q in g)]
        if not hit:
            groups.append([p])
            continue
        first = hit[0]
        first.append(p)
        for g in hit[1:]:
            first.extend(g)
            groups.remove(g)
    return groups


if __name__ == "__main__":
    r = Reach()
    for key, title, tiles in r.areas:
        print(f"{key:<10} {title:<28} {sum(len(v) for v in tiles.values())} tiles")
