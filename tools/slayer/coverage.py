"""Slayer task coverage: for every task a master can assign, how many of its monsters can a player
actually reach on the isle (the isle's own ground, plus the 18 dungeons reached from it)?

Reachable = a walk flood fill (tools/dungeons/collision.py, doors and interactive objects passable)
from the isle plaza and from each dungeon's arrival tile; a spawn counts when a flooded tile on its
level lies within RADIUS tiles (water dwellers and big npcs stand on blocked tiles).

Usage: python tools/slayer/coverage.py        -> tools/slayer/coverage.txt + coverage.json
"""
import collections, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
sys.path.insert(0, os.path.join(ROOT, "tools", "dungeons"))
import collision as K  # noqa: E402

K.SOFT = True
RADIUS = 2

SPAWN_DIR = os.path.join(ROOT, "content", "areas", "misc", "osrs-npc-spawns", "src", "main", "resources",
                         "org", "rsmod", "content", "areas", "misc", "osrsnpcspawns")

# (name, level, start x, z, extra seeds [(level, x, z)])  starts are walkable tiles in front of each arrival
ISLE = ("isle", 0, 2210, 2830, [])
DUNGEONS = [
    ("taverley", 0, 2884, 9797, []),
    ("edgeville", 0, 3097, 9867, []),
    ("varrock", 0, 3237, 9858, []),
    ("revenant", 0, 3217, 10058, [(0, 3244, 10214)]),
    ("stronghold", 0, 2429, 9824, []),
    ("brimhaven", 0, 2713, 9564, [(2, 2643, 9594), (2, 2637, 9510), (2, 2671, 9583),
                                  (0, 2650, 9591), (0, 2637, 9517), (0, 2673, 9583)]),
    ("karamja", 0, 2856, 9568, [(0, 2479, 5175)]),
    ("kraken", 0, 2276, 9988, []),
    ("smoke", 0, 2404, 9415, []),
    ("catacombs", 0, 1665, 10051, []),
    ("forthos", 0, 1831, 9974, []),
    ("ham", 0, 3149, 9652, []),
    ("slayer", 3, 3412, 9932, []),
    ("crash", 0, 2128, 5647, []),
    ("fremennik", 0, 2808, 10002, []),
    ("kalphite", 2, 3484, 9510, []),
    ("mos", 0, 3748, 9373, []),
    ("souls", 0, 2167, 9308, []),
]


def load_syms():
    sym = {}
    for line in open(os.path.join(ROOT, ".data", "symbols", "npc.sym")):
        p = line.rstrip("\n").split("\t")
        if len(p) == 2:
            sym[p[1]] = int(p[0])
    return sym


def load_spawns(sym):
    out = []  # (npcId, level, x, z)
    missing = collections.Counter()
    for fn in ("surface.toml", "underground.toml", "revenants.toml"):
        npc = None
        for line in open(os.path.join(SPAWN_DIR, fn)):
            m = re.match(r"npc = '(.*)'", line)
            if m:
                npc = m.group(1)
                continue
            m = re.match(r"coords = '(\d+)_(\d+)_(\d+)_(\d+)_(\d+)'", line)
            if m:
                lvl, mx, mz, lx, lz = map(int, m.groups())
                if npc not in sym:
                    missing[npc] += 1
                    continue
                out.append((sym[npc], lvl, mx * 64 + lx, mz * 64 + lz))
    return out, missing


def near_set(tiles_by_level):
    """Dilate each level's tile set by RADIUS."""
    out = {}
    for lvl, tiles in tiles_by_level.items():
        s = set()
        for (x, z) in tiles:
            for dx in range(-RADIUS, RADIUS + 1):
                for dz in range(-RADIUS, RADIUS + 1):
                    s.add((x + dx, z + dz))
        out[lvl] = s
    return out


CROSSINGS = os.path.join(ROOT, "content", "areas", "misc", "isle-of-souls", "src", "main", "kotlin", "org", "rsmod",
                         "content", "areas", "misc", "isleofsouls", "dungeons", "DungeonCrossings.kt")


def load_crossings():
    """(key, a, b, agility needed) from the Kotlin table; a and b are (level, x, z)."""
    src = open(CROSSINGS).read()
    out = []
    for m in re.finditer(r'key = "(\w+)".*?a = t\((\d+), (\d+)(?:, (\d+))?\),\s*b = t\((\d+), (\d+)(?:, (\d+))?\),\s*'
                         r'levelAtoB = (\d+),\s*levelBtoA = (\d+)', src, re.S):
        k, ax, az, al, bx, bz, bl, ab, ba = m.groups()
        out.append((k, (int(al or 0), int(ax), int(az)), (int(bl or 0), int(bx), int(bz)), max(int(ab), int(ba))))
    return out


DUNGEON_KT = os.path.join(os.path.dirname(CROSSINGS), "Dungeon.kt")


def load_rects():
    """(dungeon key, x0, z0, x1, z1, level or None) for every DungeonArea in Dungeon.kt."""
    out, key = [], None
    for m in re.finditer(r'key = "(\w+)"|DungeonArea\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)(?:,\s*level = (\d+))?', open(DUNGEON_KT).read()):
        if m.group(1):
            key = m.group(1)
        else:
            x0, z0, x1, z1, lv = m.groups()[1:]
            out.append((key, int(x0), int(z0), int(x1), int(z1), int(lv) if lv else None))
    return out


def expand_crossings(tiles, crossings):
    """Cross every shortcut whose one end the area already holds; returns the keys used."""
    used = []
    changed = True
    while changed:
        changed = False
        for key, a, b, lvl in crossings:
            for src, dst in ((a, b), (b, a)):
                if (src[1], src[2]) in tiles.get(src[0], ()) and (dst[1], dst[2]) not in tiles.get(dst[0], ()):
                    tiles[dst[0]] |= K.flood(dst[0], dst[1], dst[2], 600000)
                    used.append(key)
                    changed = True
    return used


def flood_area(name, lvl, x, z, seeds):
    tiles = collections.defaultdict(set)
    for (l, sx, sz) in [(lvl, x, z)] + seeds:
        if K.blocked_tile(l, sx, sz):
            print(f"  warning: {name} seed {(sx, sz, l)} is blocked", file=sys.stderr)
            continue
        tiles[l] |= K.flood(l, sx, sz, 600000)
    return tiles


def main():
    data = json.load(open(os.path.join(HERE, "slayer_data.json")))
    sym = load_syms()
    spawns, missing = load_spawns(sym)
    print(f"{len(spawns)} spawns read ({sum(missing.values())} with unknown npc syms)")

    crossings = load_crossings()
    print(f"{len(crossings)} crossings parsed")
    base, full = {}, {}
    for (name, lvl, x, z, seeds) in [ISLE] + DUNGEONS:
        tiles = flood_area(name, lvl, x, z, seeds)
        base[name] = near_set(tiles)
        used = expand_crossings(tiles, crossings) if name != "isle" else []
        full[name] = near_set(tiles)
        print(f"  {name}: {sum(len(v) for v in tiles.values())} tiles, crossings used: {sorted(set(used))}")

    def locate(areas):
        where = collections.defaultdict(list)  # npcId -> [area name or None, one per spawn]
        for (nid, lvl, x, z) in spawns:
            hit = None
            for name, near in areas.items():
                if (x, z) in near.get(lvl, ()):
                    hit = name
                    break
            where[nid].append(hit)
        return where

    rects = load_rects()

    def in_rect(l, x, z):
        for (k, x0, z0, x1, z1, lv) in rects:
            if x0 <= x <= x1 and z0 <= z <= z1 and (lv is None or lv == l):
                return k
        return None

    where = locate(full)
    where_base = locate(base)

    # tasks
    tasks = {}
    for mname, m in data["masters"].items():
        for t in m["tasks"]:
            e = tasks.setdefault(t["taskId"], {"name": t["task"], "npcs": set(), "masters": {}, "combat": t["combat"],
                                              "stats": t["stats"], "unlock": t["unlockRow"] is not None,
                                              "lmin": t["leaguesMinCombat"], "lmax": t["leaguesMaxCombat"]})
            e["npcs"] |= set(t["npcs"])
            e["masters"][mname] = (t["weight"], t["min"], t["max"])

    spawns_of = collections.defaultdict(list)
    for sp in spawns:
        spawns_of[sp[0]].append(sp)
    report = {}
    for tid, t in sorted(tasks.items(), key=lambda kv: kv[1]["name"]):
        world = 0
        by_area = collections.Counter()
        unverified = collections.Counter()
        for n in t["npcs"]:
            for hit, (_, l, x, z) in zip(where.get(n, []), spawns_of.get(n, [])):
                world += 1
                if hit:
                    by_area[hit] += 1
                elif in_rect(l, x, z):
                    unverified[in_rect(l, x, z)] += 1
                    by_area["unverified"] += 1
                else:
                    by_area["unreachable"] += 1
        reach = world - by_area.get("unreachable", 0) - by_area.get("unverified", 0)
        report[tid] = {"name": t["name"], "masters": t["masters"], "combat": t["combat"], "stats": t["stats"],
                       "unlock": t["unlock"], "lmin": t["lmin"], "lmax": t["lmax"], "world": world,
                       "reachable": reach, "unverified": by_area.get("unverified", 0), "unverifiedWhere": dict(unverified), "areas": dict(by_area)}

    json.dump(report, open(os.path.join(HERE, "coverage.json"), "w"), indent=1)
    lines = []
    for tid, r in sorted(report.items(), key=lambda kv: (kv[1]["reachable"] + kv[1]["unverified"], kv[1]["reachable"], kv[1]["name"])):
        ms = ",".join(m[:3] for m in r["masters"])
        a = ", ".join(f"{k} {v}" for k, v in sorted(r["areas"].items(), key=lambda kv: -kv[1]) if k not in ("unreachable", "unverified"))
        u = ", ".join(f"{k} {v}" for k, v in r["unverifiedWhere"].items())
        lines.append(f"{r['name']:<22} id {tid:<3} [{ms}] world {r['world']:<4} verified {r['reachable']:<4} unverified {r['unverified']:<3} | {a} | unverified in: {u}")
    open(os.path.join(HERE, "coverage.txt"), "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
