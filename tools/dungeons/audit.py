"""Checks the isle's dungeon table (Dungeon.kt / DungeonLocs.kt) against the cache.

  * every listed exit is a real object of that type on that tile;
  * every arrival tile is walkable and opens onto a real area;
  * every entrance footprint is clear ground on the isle, with its front tile reachable from the lobby;
  * every generic dungeon ladder (ladder_from_cellar) a player can reach from an arrival lies inside
    some dungeon's area, so the ladder event redirects it to the isle (otherwise it is a leak).

Usage: python tools/dungeons/audit.py
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import collision as K
import census as C

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
SRC = os.path.join(ROOT, "content", "areas", "misc", "isle-of-souls", "src", "main", "kotlin", "org", "rsmod",
                   "content", "areas", "misc", "isleofsouls", "dungeons")
locs_kt = open(os.path.join(SRC, "DungeonLocs.kt")).read()
dun_kt = open(os.path.join(SRC, "Dungeon.kt")).read()
SYM = dict(re.findall(r"val (\w+) = find\(\"(\w+)\"\)", locs_kt))
NAME_ID = {v: k for k, v in C.LOC_SYM.items()}
GENERIC_LADDER = NAME_ID["ladder_from_cellar"]

entries = []
for m in re.finditer(r"^    ([A-Z_]+)\(\n(.*?)\n    \)[,;]", dun_kt, re.S | re.M):
    body = m.group(2)
    g = lambda pat: re.search(pat, body)
    cg = lambda s: tuple(int(v) for v in re.findall(r"-?\d+", s))
    e = {
        "name": m.group(1),
        "key": g(r'key = "(\w+)"').group(1),
        "type": SYM[g(r"entranceType = DungeonLocs\.(\w+)").group(1)],
        "entrance": cg(g(r"entrance = CoordGrid\(([^)]*)\)").group(1)),
        "angle": g(r"angle = LocAngle\.(\w+)").group(1),
        "front": cg(g(r"front = CoordGrid\(([^)]*)\)").group(1)),
        "arrival": cg(g(r"arrival = CoordGrid\(([^)]*)\)").group(1)),
        "areas": [cg(a)[:4] + (cg(a)[4:] or (None,))[:1] for a in re.findall(r"DungeonArea\(([^)]*)\)", body)],
        "onmap": "onMap = true" in body,
        "exits": [(SYM[a], cg(c)) for a, c in re.findall(r"DungeonExit\(DungeonLocs\.(\w+), CoordGrid\(([^)]*)\)", body)],
    }
    entries.append(e)

problems = []
L = C.locs()
census_cache = {}


def locs_at(x, z, lvl):
    mx, mz = x // 64, z // 64
    if (mx, mz) not in census_cache:
        census_cache[(mx, mz)] = C.square_locs(mx, mz)
    return [r for r in census_cache[(mx, mz)] if r[1] == x and r[2] == z and r[3] == lvl]


def in_any_area(x, z, lvl):
    for e in entries:
        for a in e["areas"]:
            if a[0] <= x <= a[2] and a[1] <= z <= a[3] and (a[4] is None or a[4] == lvl):
                return True
    return False


lobby = K.flood(0, 2210, 2850, 60000)  # everything the lobby plaza can walk to
print(f"{len(entries)} dungeons")
for e in entries:
    print(f"== {e['key']}")
    # exits are real
    for sym, (x, z, lvl) in e["exits"]:
        ok = any(r[0] == NAME_ID[sym] for r in locs_at(x, z, lvl))
        if not ok: problems.append(f"{e['key']}: no {sym} at ({x},{z},{lvl})")
    # arrival
    ax, az, al = e["arrival"]
    if K.blocked_tile(al, ax, az):
        problems.append(f"{e['key']}: arrival ({ax},{az},{al}) is blocked")
    else:
        n = len(K.flood(al, ax, az, 20000))
        print(f"   arrival reach (no doors) = {n}")
        if n < 30: problems.append(f"{e['key']}: arrival only reaches {n} tiles")
    if not in_any_area(ax, az, al): problems.append(f"{e['key']}: arrival outside every area")
    # entrance footprint on the isle
    t = L[NAME_ID[e["type"]]]
    ex, ez, _ = e["entrance"]
    rot = {"West": 0, "North": 1, "East": 2, "South": 3}[e["angle"]]
    w, h = (t["sx"], t["sz"]) if rot % 2 == 0 else (t["sz"], t["sx"])
    if e["onmap"]:
        # part of the map: it must be the real object, standing there with that turn
        real = [r for r in locs_at(ex, ez, 0) if r[0] == NAME_ID[e["type"]]]
        if not real: problems.append(f"{e['key']}: no {e['type']} on the map at ({ex},{ez})")
        elif real[0][5] != rot: problems.append(f"{e['key']}: the map turns the entrance {real[0][5]}, the table says {rot}")
    else:
        for dx in range(w):
            for dz in range(h):
                if (ex + dx, ez + dz) not in lobby:
                    problems.append(f"{e['key']}: entrance tile ({ex+dx},{ez+dz}) is not clear, reachable ground")
    fx, fz, _ = e["front"]
    if (fx, fz) not in lobby: problems.append(f"{e['key']}: front ({fx},{fz}) is not reachable ground")
    if not (ex <= fx < ex + w and fz == ez + h) and not (ex <= fx < ex + w and fz == ez - 1):
        print(f"   note: front is not directly beside the entrance footprint ({fx},{fz})")
    # approach flags: the front must be on an open side
    f = t["block"] & 0xF
    r = f if rot == 0 else (((f << rot) & 0xF) | (f >> (4 - rot)))
    if fz == ez + h and (r & 1): problems.append(f"{e['key']}: north side of the entrance is closed by its approach flags")
    # leaks
    K.SOFT = True
    reach = K.flood(al, ax, az, 700000)
    K.SOFT = False
    leaks = set()
    for (x, z) in reach:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for (lid, shape, rot2) in K._locs_at.get((al, x + dx, z + dz), ()):
                    if lid == GENERIC_LADDER and not in_any_area(x + dx, z + dz, al):
                        leaks.add((x + dx, z + dz))
    for l in sorted(leaks): problems.append(f"{e['key']}: reachable generic ladder at {l} is outside every dungeon area")

print()
print("PROBLEMS" if problems else "no problems")
for p in problems: print(" ", p)


# ---------------------------------------------------------------------------------------------
# Crossings (DungeonCrossings.kt): every click is a real object on its tile, both ends are open ground.
# ---------------------------------------------------------------------------------------------
cross_kt = open(os.path.join(SRC, "DungeonCrossings.kt")).read()
CSYM = dict(re.findall(r"val (\w+) = find\(\"(\w+)\"\)", cross_kt))
cross_problems = []
n_cross = 0
for m in re.finditer(r"val (\w+) =\n\s+DungeonCrossing\((.*?)\n        \)\n", cross_kt, re.S):
    body = m.group(2)
    key = re.search(r'key = "(\w+)"', body).group(1)
    a = tuple(int(v) for v in re.search(r"a = t\(([^)]*)\)", body).group(1).split(","))
    b = tuple(int(v) for v in re.search(r"b = t\(([^)]*)\)", body).group(1).split(","))
    n_cross += 1
    for end in (a, b):
        lvl = end[2] if len(end) > 2 else 0
        if K.blocked_tile(lvl, end[0], end[1]):
            cross_problems.append(f"{key}: end {end} is blocked")
    for cm in re.finditer(r"(?:click|stone)\(([^()]*)\)", body):
        args = [a.strip() for a in cm.group(1).split(",") if a.strip()]
        if not args[0].startswith("DungeonCrossLocs."): continue
        sym_name = CSYM[args[0].split(".")[1]]
        x, z = int(args[1]), int(args[2])
        lm = re.search(r"level = (\d+)", cm.group(1))
        lvl = int(lm.group(1)) if lm else 0
        if not any(r[0] == NAME_ID[sym_name] for r in locs_at(x, z, lvl)):
            cross_problems.append(f"{key}: no {sym_name} at ({x},{z},{lvl})")
print()
print(f"{n_cross} crossings checked:", "PROBLEMS" if cross_problems else "no problems")
for p in cross_problems: print(" ", p)
