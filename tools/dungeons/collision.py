"""Approximate walk collision from the cache (terrain flags + loc footprints), enough to flood-fill a
dungeon from a tile and see what it can reach without passing an interactive object.

Usage: python tools/dungeons/collision.py X Z [LEVEL]    -> reach report for the area round that tile
"""
import os, sys, collections
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "spawns"))
import census as C
import convert as V  # noqa: E402  (Terrain reader)

_terrain = V.Terrain()
_edges = {}   # (level, x, z) -> set of blocked sides 'W','N','E','S'
_solid = set()  # (level, x, z)
_loaded = set()
_locs_at = collections.defaultdict(list)  # (level, x, z) -> [(lid, shape)]
_soft = set()  # (level, x, z) tiles/edges owned by interactive locs



def eff_level(level, x, z):
    sq = _terrain.square(x // 64, z // 64)
    if sq and level < 3 and sq.get((level + 1, x % 64, z % 64), 0) & 2:
        return level - 1 if False else level
    return level


def load(mx, mz):
    if (mx, mz) in _loaded:
        return
    _loaded.add((mx, mz))
    L = C.locs()
    sq = _terrain.square(mx, mz)
    if not sq:
        return
    for (lvl, lx, lz), s in sq.items():
        if s & 1:
            # blocked terrain; bridge tiles shift the visible level down by one
            ll = lvl - 1 if (lvl > 0 and False) else lvl
            _solid.add((lvl, mx * 64 + lx, mz * 64 + lz))
    for (lid, x, z, lvl, shape, rot) in C.square_locs(mx, mz):
        t = L.get(lid)
        if not t: continue
        # bridge: a tile flagged on level 1 pulls its level-1 contents down to level 0
        if lvl > 0 and sq.get((1, x % 64, z % 64), 0) & 2 and lvl == 1:
            lvl = 0
        _locs_at[(lvl, x, z)].append((lid, shape, rot))
        if not t["solid"]: continue
        interactive = any(o and o.lower() != "examine" for o in t["ops"]) or bool(t["multi"])
        if shape in (10, 11) or (shape == 22 and False):
            sx, sz = (t["sx"], t["sz"]) if rot % 2 == 0 else (t["sz"], t["sx"])
            for dx in range(sx):
                for dz in range(sz):
                    _solid.add((lvl, x + dx, z + dz))
                    if interactive: _soft.add((lvl, x + dx, z + dz))
        elif shape == 9:
            _solid.add((lvl, x, z))
            if interactive: _soft.add((lvl, x, z))
        elif shape in (0, 1, 2, 3):
            sides = {0: "W", 1: "N", 2: "E", 3: "S"}
            e = _edges.setdefault((lvl, x, z), set())
            e.add(sides[rot])
            if shape in (1, 2, 3):
                e.add(sides[(rot + 1) % 4])
            if interactive: _soft.add((lvl, x, z, "edge"))


SOFT = False


def blocked_tile(level, x, z):
    load(x // 64, z // 64)
    if (level, x, z) in _solid:
        if SOFT and (level, x, z) in _soft: return False
        return True
    sq = _terrain.square(x // 64, z // 64)
    return sq is None


def can_step(level, x, z, dx, dz):
    nx, nz = x + dx, z + dz
    if blocked_tile(level, nx, nz): return False
    side = {(-1, 0): "W", (1, 0): "E", (0, 1): "N", (0, -1): "S"}[(dx, dz)]
    opp = {"W": "E", "E": "W", "N": "S", "S": "N"}[side]
    if not (SOFT and (level, x, z, "edge") in _soft):
        if side in _edges.get((level, x, z), ()): return False
    if not (SOFT and (level, nx, nz, "edge") in _soft):
        if opp in _edges.get((level, nx, nz), ()): return False
    return True


def flood(level, x, z, limit=400000):
    load(x // 64, z // 64)
    seen = {(x, z)}
    q = collections.deque([(x, z)])
    while q and len(seen) < limit:
        cx, cz = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (cx + dx, cz + dz) in seen: continue
            if can_step(level, cx, cz, dx, dz):
                seen.add((cx + dx, cz + dz)); q.append((cx + dx, cz + dz))
    return seen


def touching(level, tiles, radius=1):
    """Interactive locs whose footprint tile is within `radius` of a reachable tile."""
    L = C.locs()
    found = collections.defaultdict(set)
    for (x, z) in tiles:
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                for (lid, shape, rot) in _locs_at.get((level, x + dx, z + dz), ()):
                    t = L[lid]
                    ops = [o for o in t["ops"] if o and o.lower() != "examine"]
                    if ops:
                        found[(lid, C.LOC_SYM.get(lid, "?"), "/".join(ops)[:24])].add((x + dx, z + dz))
    return found


def report(x, z, level=0, radius=1):
    tiles = flood(level, x, z)
    xs = [a for a, _ in tiles]; zs = [b for _, b in tiles]
    print(f"start ({x},{z},{level}) blocked={blocked_tile(level, x, z)} reach={len(tiles)} "
          f"bbox x {min(xs)}-{max(xs)} z {min(zs)}-{max(zs)}")
    for k, v in sorted(touching(level, tiles, radius).items(), key=lambda kv: kv[0][1]):
        print(f"  {k[1]:<40} [{k[2]}] x{len(v)} {sorted(v)[:3]}")
    return tiles


if __name__ == "__main__":
    a = sys.argv[1:]
    report(int(a[0]), int(a[1]), int(a[2]) if len(a) > 2 else 0)
