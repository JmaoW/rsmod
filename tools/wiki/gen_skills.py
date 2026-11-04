#!/usr/bin/env python3
"""Builds the generated half of the wiki's skill pages -> wiki/data/skills-generated.js

    python tools/wiki/gen_skills.py

What it reads, so a change to any of these needs a re-run:
- the skill modules' Kotlin config tables (levels, XP, loot), parsed as text;
- the vanilla cache (item and npc names, map objects) and .data/symbols;
- the world npc spawns (content/areas/misc/osrs-npc-spawns);
- the league task list (LeagueTaskList.kt and friends, IsleOfSoulsTaskTexts) and docs/raging_echoes_tasks.json
  for each task's skill and description;
- .data/league/event-settings.json for task points and XP rates.

"Where to train" only lists what a player can actually walk to (see reach.py). The hand-written half
of each page (overview, gear, relics, differences, place names) is wiki/data/skills.js.
"""
import collections
import datetime
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "tools", "spawns"))
sys.path.insert(0, os.path.join(ROOT, "tools", "food"))
import reach as R  # noqa: E402
import js5  # noqa: E402
import convert  # noqa: E402
import objdump  # noqa: E402

OUT = os.path.join(ROOT, "wiki", "data", "skills-generated.js")
SKILLS = os.path.join(ROOT, "content", "skills")
API_REFS = os.path.join(ROOT, "api", "config", "src", "main", "kotlin", "org", "rsmod", "api", "config", "refs")
SPAWN_DIR = os.path.join(ROOT, "content", "areas", "misc", "osrs-npc-spawns", "src", "main", "resources",
                         "org", "rsmod", "content", "areas", "misc", "osrsnpcspawns")


def read(path):
    return open(path, encoding="utf-8").read()


def kt(module, *parts):
    return os.path.join(SKILLS, module, "src", "main", "kotlin", "org", "rsmod", "content", "skills", module, *parts)


# ---------------------------------------------------------------- cache and symbols

def load_sym(kind):
    by_id, by_name = {}, {}
    for line in open(os.path.join(ROOT, ".data", "symbols", f"{kind}.sym"), encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if len(p) == 2 and p[0].isdigit():
            by_id[int(p[0])] = p[1]
            by_name[p[1]] = int(p[0])
    return by_id, by_name


OBJ_SYM, OBJ_ID = load_sym("obj")
NPC_SYM, NPC_ID = load_sym("npc")
LOC_SYM, LOC_ID = load_sym("loc")

_objs = None


def obj(sym):
    """The decoded cache config of the obj with internal name `sym` (name, params...)."""
    global _objs
    if _objs is None:
        files = js5.ref_table(2)[10]
        group = js5.split_files(js5.read_group(2, 10), files)
        _objs = {oid: objdump.decode(data) for oid, data in group.items()}
    oid = OBJ_ID.get(sym)
    if oid is None:
        raise KeyError(f"no obj named {sym}")
    return _objs.get(oid) or {}


def obj_name(sym):
    """The in-game name of the obj with internal name `sym`."""
    return obj(sym).get("name") or sym


PARAM_LEVELREQUIRE = 23


def tool_table(syms, levels=None):
    """[[level, name]] for a skill's tools, leaving out variants (is_tool_variant) and repeated names.
    The level is `levels[sym]` when given, else the obj's own `levelrequire` param from the cache."""
    rows, seen = [], set()
    for sym in syms:
        name = obj_name(sym)
        if name in seen or is_tool_variant(sym, name):
            continue
        seen.add(name)
        lvl = (levels or {}).get(sym) or max(1, obj(sym).get("params", {}).get(PARAM_LEVELREQUIRE, 1))
        rows.append([lvl, name])
    rows.sort(key=lambda r: (r[0], r[1]))
    return rows


def refs(path):
    """field -> internal name for every `val field = find("name")` in a Kotlin references file."""
    return dict(re.findall(r'val (\w+) = find(?:<\w+>)?\("([^"]+)"\)', read(path)))


BASE_OBJS = refs(os.path.join(API_REFS, "BaseObjs.kt"))


def blocks(src, call):
    """The argument text of every top-level `call(` ... `)` in src, parentheses balanced."""
    out = []
    for m in re.finditer(r"\b" + re.escape(call) + r"\(", src):
        depth, i = 1, m.end()
        while depth:
            c = src[i]
            depth += c == "("
            depth -= c == ")"
            i += 1
        out.append(src[m.end():i - 1])
    return out


def num(text, field, default=None):
    m = re.search(r"\b" + field + r" = (-?[\d.]+)", text)
    return (float(m.group(1)) if "." in m.group(1) else int(m.group(1))) if m else default


def pct(part, whole=1.0):
    """A chance as a percent: two decimals, or as many as it takes for a tiny chance not to show as 0."""
    v = 100.0 * part / whole
    if v == 0:
        return 0
    digits = 2
    while round(v, digits) == 0:
        digits += 1
    return round(v, digits)


# Tool variants left out of the wiki's tool lists: ornament-kit and league versions, uncharged or
# inactive ones, and minigame-only tools (the Gauntlet's, Zalcano's). Matched on the internal name and
# on the shown name.
TOOL_VARIANT_SYMS = ("trailblazer", "zalcano", "gauntlet", "_empty", "_inactive", "_uncharged", "_pretty", "_or")
TOOL_VARIANT_NAMES = ("(or)", "(uncharged)", "(inactive)", "Corrupted ", "Echo ")


def is_tool_variant(sym, name):
    return any(k in sym for k in TOOL_VARIANT_SYMS) or any(k in name for k in TOOL_VARIANT_NAMES)


def fmt_xp(x):
    return int(x) if float(x).is_integer() else round(x, 1)


# ---------------------------------------------------------------- spawns

def spawns():
    """[(npc id, level, x, z)] for every world spawn."""
    out = []
    for fn in ("surface.toml", "underground.toml", "revenants.toml"):
        npc = None
        for line in open(os.path.join(SPAWN_DIR, fn), encoding="utf-8"):
            m = re.match(r"npc = '(.*)'", line)
            if m:
                npc = m.group(1)
                continue
            m = re.match(r"coords = '(\d+)_(\d+)_(\d+)_(\d+)_(\d+)'", line)
            if m and npc in NPC_ID:
                lvl, mx, mz, lx, lz = map(int, m.groups())
                out.append((NPC_ID[npc], lvl, mx * 64 + lx, mz * 64 + lz))
    return out


# ---------------------------------------------------------------- tasks

TIERS = ["Easy", "Medium", "Hard", "Elite", "Master"]
SKILL_IDS = {"attack": 1, "strength": 2, "ranged": 3, "magic": 4, "defence": 5, "hitpoints": 6, "prayer": 7,
             "agility": 8, "herblore": 9, "thieving": 10, "crafting": 11, "runecraft": 12, "mining": 13,
             "smithing": 14, "fishing": 15, "cooking": 16, "firemaking": 17, "woodcutting": 18, "fletching": 19,
             "slayer": 20, "farming": 21, "construction": 22, "hunter": 23}


def tasks(settings):
    isle = R.ISLE_KT
    found = {}
    for dirpath, _, files in os.walk(isle):
        for f in files:
            if f.endswith(".kt"):
                for m in re.finditer(r'LeagueTask\(\s*(\d+),\s*LeagueTier\.(\w+),\s*"((?:[^"\\]|\\.)*)"', read(os.path.join(dirpath, f))):
                    found[int(m.group(1))] = (m.group(2), m.group(3).replace('\\"', '"'))
    vanilla = {t["taskId"]: t for t in json.load(open(os.path.join(ROOT, "docs", "raging_echoes_tasks.json"), encoding="utf-8"))["tasks"]}
    edits = {}
    for m in re.finditer(r'Edit\((\d+), (null|"(?:[^"\\]|\\.)*"), (null|"(?:[^"\\]|\\.)*")\)',
                         read(os.path.join(isle, "configs", "IsleOfSoulsTaskEdits.kt"))):
        edits[int(m.group(1))] = None if m.group(3) == "null" else m.group(3)[1:-1]
    points = settings.get("taskPoints", {})
    live = triggered_ids(isle)
    out = []
    for tid, (tier, name) in found.items():
        v = vanilla.get(tid, {})
        out.append({"id": tid, "tier": tier, "points": points.get(tier.lower()), "name": name,
                    "desc": edits.get(tid) or v.get("description") or name, "skill": v.get("skillId"),
                    "live": tid in live})
    return out


TASK_TABLES = ("LeagueTaskList.kt", "IsleOfSoulsTaskEdits.kt", "IsleOfSoulsTaskEnums.kt")


def triggered_ids(isle):
    """Task ids that some trigger can complete: whole numbers in the files that use the task tracker,
    leaving out the task tables themselves, comments and coordinates. A heuristic; a stray number can
    mark a task that has no trigger."""
    ids = set()
    for dirpath, _, files in os.walk(isle):
        for f in files:
            if not f.endswith(".kt") or f in TASK_TABLES:
                continue
            src = read(os.path.join(dirpath, f))
            if not re.search(r"LeagueTaskTracker|tracker\.", src):
                continue
            src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
            src = re.sub(r"//[^\n]*", "", src)
            src = re.sub(r"\b(CoordGrid|DungeonArea|t)\([^)]*\)", "", src)
            ids |= {int(n) for n in re.findall(r"\b(\d{2,4})\b", src)}
    return ids


def skill_tasks(all_tasks, skill):
    sid = SKILL_IDS[skill]
    rows = [t for t in all_tasks if t["skill"] == sid]
    rows.sort(key=lambda t: (TIERS.index(t["tier"]), t["name"]))
    return [{k: t[k] for k in ("id", "tier", "points", "name", "desc", "live")} for t in rows]


# ---------------------------------------------------------------- places

def places(reach, points, by_label=False):
    """Groups reachable (level, x, z, label) points into places: [{area, title, level, x, z, box, things}].
    With `by_label`, each kind of thing is grouped on its own (trees, where every forest is mixed)."""
    out = []
    by_area = collections.defaultdict(list)
    for (area, lvl, x, z, label) in points:
        by_area[area].append((lvl, x, z, label))
    order = [k for k, _, _ in reach.areas]
    for area in sorted(by_area, key=order.index):
        pts = by_area[area]
        groups = [g for label in sorted({p[3] for p in pts}) for g in R.clusters([p for p in pts if p[3] == label])]             if by_label else R.clusters(pts)
        for g in groups:
            xs = [p[1] for p in g]
            zs = [p[2] for p in g]
            counts = collections.Counter(p[3] for p in g)
            x, z = round(sum(xs) / len(xs)), round(sum(zs) / len(zs))
            out.append({"area": area, "title": reach.titles[area], "level": g[0][0], "x": x, "z": z,
                        "box": [min(xs), min(zs), max(xs), max(zs)],
                        "dir": (("all over the isle" if max(max(xs) - min(xs), max(zs) - min(zs)) > 80 else compass(x, z))
                                if area == "isle" else None),
                        "things": [{"name": n, "count": c} for n, c in sorted(counts.items())]})
    return out


LOBBY = (2210, 2850)  # the middle of the Soul Wars lobby plaza, where players start


def compass(x, z):
    """'about 60 tiles south-west of the lobby', for a tile on the isle's surface."""
    import math
    dx, dz = x - LOBBY[0], z - LOBBY[1]
    dist = math.hypot(dx, dz)
    if dist < 15:
        return "at the lobby"
    names = ["east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east"]
    d = names[round(math.degrees(math.atan2(dz, dx)) / 45) % 8]
    return f"about {int(round(dist, -1))} tiles {d} of the lobby"


def entrances():
    """Where each way off the isle starts: {area key: {x, z, dir}}. Dungeons from Dungeon.kt, the rest by hand
    (the Mining Guild ladder in MiningGuildScript, Aubury in RunecraftSites)."""
    src = read(R.DUNGEON_KT)
    out = {}
    for m in re.finditer(r'key = "(\w+)",.*?entrance = CoordGrid\((\d+), (\d+), (\d+)\)', src, re.S):
        out[m.group(1)] = (int(m.group(2)), int(m.group(3)))
    out["guild"] = (2204, 2786)
    out["essence"] = (2214, 2862)
    out["morulrek"] = out.get("karamja")
    out["tower"] = (2131, 2994)  # the Crumbling Tower's basement trapdoor (CrumblingTower.kt)
    # Landmarks the page text can point at with {dir:<key>}: the starter objects and npcs, and the altar.
    starter = read(os.path.join(R.ISLE_KT, "starter", "StarterScript.kt"))
    for const, x, z in re.findall(r"val (\w+) = CoordGrid\((\d+), (\d+), 0\)", starter):
        out[const.lower()] = (int(x), int(z))
    prayer_kt = read(os.path.join(R.ISLE_KT, "prayer", "LeaguePrayerScript.kt"))
    x, z = re.search(r"val ALTAR = CoordGrid\((\d+), (\d+), 0\)", prayer_kt).groups()
    out["altar"] = (int(x), int(z))
    slayer_cfg = read(os.path.join(SKILLS, "slayer", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "slayer",
                                   "configs", "SlayerMasterConfigs.kt"))
    for name, x, z in re.findall(r"val (\w+) = CoordGrid\((\d+), (\d+), \d+\)", slayer_cfg[slayer_cfg.index("object SlayerSpots"):]):
        out[name] = (int(x), int(z))
    agil = os.path.join(SKILLS, "agility", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "agility", "configs")
    out["grace"] = tuple(map(int, re.search(r"val tile = CoordGrid\((\d+), (\d+), 0\)", read(os.path.join(agil, "GraceConfigs.kt"))).groups()))
    out["arena_hut"] = tuple(map(int, re.search(r"val DOOR = CoordGrid\((\d+), (\d+), 0\)", read(os.path.join(agil, "ArenaHut.kt"))).groups()))
    return {k: {"x": x, "z": z, "dir": compass(x, z) if z < 4000 else None} for k, (x, z) in out.items()}


# ---------------------------------------------------------------- mining

def mining(reach, all_tasks):
    rocks_kt = read(kt("mining", "configs", "MiningRocks.kt"))
    rock_locs = refs(kt("mining", "configs", "MiningRocks.kt"))

    rows, loc_label = [], {}
    for b in blocks(rocks_kt, "orePair"):
        if "levelReq = " not in b:
            continue  # the function itself
        r1, r2, prod = re.findall(r"(RockLocs\.\w+|objs\.\w+)", b)[:3]
        product = obj_name(BASE_OBJS[prod.split(".")[1]])
        rows.append({"level": num(b, "levelReq"), "rock": product.replace(" ore", "") + " rocks", "gives": product,
                     "xp": fmt_xp(num(b, "xp"))})
        for r in (r1, r2):
            loc_label[LOC_ID[rock_locs[r.split(".")[1]]]] = rows[-1]["rock"]
    for b in blocks(rocks_kt, "rock"):
        if "MiningOreObjs.amethyst" in b and "levelReq = " in b:
            rows.append({"level": num(b, "levelReq"), "rock": "Amethyst crystals", "gives": obj_name("amethyst"),
                         "xp": fmt_xp(num(b, "xp"))})
            for f in ("amethyst_rock_1", "amethyst_rock_2"):
                loc_label[LOC_ID[rock_locs[f]]] = "Amethyst crystals"
            break

    # Rune essence: the runecraft module turns the essence mine's rock into an ore.
    rc_edits = read(os.path.join(SKILLS, "runecraft", "src", "main", "kotlin", "org", "rsmod", "content", "skills",
                                 "runecraft", "configs", "RunecraftEdits.kt"))
    rc_locs = {}
    for f in os.listdir(os.path.join(SKILLS, "runecraft", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "runecraft", "configs")):
        rc_locs.update(refs(os.path.join(SKILLS, "runecraft", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "runecraft", "configs", f)))
    ess_xp = float(re.search(r"ESSENCE_XP = ([\d.]+)", rc_edits).group(1))
    rows.append({"level": 1, "rock": "Rune essence", "gives": "Rune essence, or Pure essence from level 30", "xp": fmt_xp(ess_xp)})
    loc_label[LOC_ID[rc_locs["essence_rock"]]] = "Rune essence"
    rows.sort(key=lambda r: (r["level"], r["rock"]))

    gems_kt = read(kt("mining", "configs", "MiningGems.kt"))
    gem_refs = refs(kt("mining", "configs", "MiningGems.kt"))
    gem_w = [(obj_name(gem_refs[f]), int(w)) for f, w in re.findall(r"Entry\(MiningGemObjs\.(\w+), (\d+)\)", gems_kt)]
    total = sum(w for _, w in gem_w)
    gem_chance = int(re.search(r"CHANCE = (\d+)", gems_kt).group(1))
    gems = [{"gem": g, "share": pct(w, total)} for g, w in gem_w]

    picks_kt = read(kt("mining", "configs", "MiningPickaxes.kt"))
    pick_levels = {BASE_OBJS[f]: int(lvl) for f, lvl in re.findall(r"standard\(\s*objs\.(\w+),\s*levelReq = (\d+)", picks_kt)}
    picks = tool_table(list(pick_levels), pick_levels)

    found = reach.locs(set(loc_label))
    pts = [(area, lvl, x, z, loc_label[lid]) for (lid, lvl, x, z, area) in found]
    return {
        "tables": [
            {"id": "rocks", "title": "Rocks", "cols": ["Level", "Rock", "Gives", "XP each"],
             "rows": [[r["level"], r["rock"], r["gives"], r["xp"]] for r in rows]},
            {"id": "pickaxes", "title": "Pickaxes", "cols": ["Level", "Pickaxe"], "rows": picks},
        ],
        "gems": {"chance": gem_chance, "gems": gems},
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "mining"),
    }


# ---------------------------------------------------------------- woodcutting

TREE_LABELS = {"regular": "Tree", "dead": "Dead tree", "burnt": "Burnt tree", "evergreen": "Evergreen tree",
               "jungle": "Jungle tree", "achey": "Achey tree", "oak": "Oak tree", "willow": "Willow tree",
               "teak": "Teak tree", "juniper": "Juniper tree", "maple": "Maple tree", "hollow": "Hollow tree",
               "arctic": "Arctic pine", "mahogany": "Mahogany tree", "yew": "Yew tree", "magic": "Magic tree",
               "redwood": "Redwood tree"}


def woodcutting(reach, all_tasks):
    path = kt("woodcutting", "configs", "TreeLocs.kt")
    src = read(path)
    tree_refs = refs(path)
    # Each kind of tree is a function: level, XP and logs, or a call to regular() with other logs.
    kinds = {}
    for m in re.finditer(r"private fun (\w+)\(type: LocType, stump: LocType[^)]*\) \{(.*?)\n    \}", src, re.S):
        name, body = m.groups()
        if "levelrequire" in body:
            kinds[name] = {"level": int(re.search(r"levelrequire\] = (\d+)", body).group(1)),
                           "xp": float(re.search(r"toFineXP\(([\d.]+)\)", body).group(1)),
                           "logs": re.search(r"skill_productitem\] = (?:objs\.(\w+)|logs)", body).group(1) or "logs"}
        else:
            call = re.search(r"regular\(type, stump(?:, objs\.(\w+))?\)", body)
            kinds[name] = dict(kinds.get("regular", {}), logs=(call.group(1) if call and call.group(1) else "logs"), base="regular")
    rows, loc_label = {}, {}
    for kind, tree, logs in re.findall(r"^\s+(\w+)\(trees\.(\w+), stumps\.\w+(?:, objs\.(\w+))?\)", src, re.M):
        k = dict(kinds["regular"]) if kinds[kind].get("base") else dict(kinds[kind])
        k["logs"] = logs or (kinds[kind]["logs"] if kinds[kind].get("base") else k["logs"])
        label = "Achey tree" if logs == "achey_tree_logs" else TREE_LABELS.get(kind, kind.title() + " tree")
        logs_sym = BASE_OBJS.get(k["logs"], k["logs"])
        rows.setdefault(label, [k["level"], label, obj_name(logs_sym), fmt_xp(k["xp"])])
        loc_label[LOC_ID[tree_refs[tree]]] = label
    table = sorted(rows.values(), key=lambda r: (r[0], r[1]))

    axes_kt = read(kt("woodcutting", "configs", "WoodcuttingAxes.kt"))
    axes = tool_table([BASE_OBJS[f] for f in re.findall(r"edit\(objs\.(\w+)\)", axes_kt)],
                      {BASE_OBJS["blessed_axe"]: 35})

    found = reach.locs(set(loc_label))
    pts = [(area, lvl, x, z, loc_label[lid]) for (lid, lvl, x, z, area) in found]
    return {
        "tables": [
            {"id": "trees", "title": "Trees", "cols": ["Level", "Tree", "Gives", "XP each"], "rows": table},
            {"id": "axes", "title": "Axes", "cols": ["Level", "Axe"], "rows": axes},
        ],
        "places": places(reach, pts, by_label=True),
        "tasks": skill_tasks(all_tasks, "woodcutting"),
    }


# ---------------------------------------------------------------- fishing

# What each fishing method is called on the page: (spot, tool).
FISH_METHODS = {
    "SmallNet": ("Net/Bait spot: Net", "Small fishing net"),
    "SeaBait": ("Net/Bait spot: Bait", "Fishing rod and fishing bait"),
    "RiverBait": ("Lure/Bait spot: Bait", "Fishing rod and fishing bait"),
    "FlyRod": ("Lure/Bait spot: Lure", "Fly fishing rod and feathers"),
    "LobsterPot": ("Cage/Harpoon spot: Cage", "Lobster pot"),
    "Harpoon": ("Cage/Harpoon or monkfish spot: Harpoon", "Harpoon"),
    "BigNet": ("Big Net/Harpoon spot: Big Net", "Big fishing net"),
    "SharkHarpoon": ("Big Net/Harpoon spot: Harpoon", "Harpoon"),
    "MonkfishNet": ("Monkfish spot: Net", "Small fishing net"),
    "Karambwanji": ("Karambwanji spot", "Small fishing net"),
    "Karambwan": ("Karambwan spot", "Karambwan vessel with karambwanji"),
    "Anglerfish": ("Anglerfish spot", "Fishing rod and sandworms"),
}
SPOT_PAIRS = {("Small Net", "Bait"): "Net/Bait spot", ("Net", "Bait"): "Net/Bait spot",
              ("Lure", "Bait"): "Lure/Bait spot", ("Cage", "Harpoon"): "Cage/Harpoon spot",
              ("Big Net", "Harpoon"): "Big Net/Harpoon spot", ("Net", "Harpoon"): "Big Net/Harpoon spot"}


def fishing(reach, all_tasks, world_spawns):
    base = os.path.join(SKILLS, "fishing", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "fishing")
    src = read(os.path.join(base, "configs", "FishingCatches.kt"))
    objs_block = src[src.index("object FishingObjs"):src.index("object FishingSeqs")]
    fobjs = dict(re.findall(r'val (\w+) = find\("([^"]+)"\)', objs_block))
    fish = {}
    for m in re.finditer(r"val (\w+) =\s*FishDef\(\s*FishingObjs\.(\w+),\s*(\d+),\s*([\d.]+),", src):
        fish[m.group(1)] = (int(m.group(3)), obj_name(fobjs[m.group(2)]).replace("Raw ", "").capitalize(), fmt_xp(float(m.group(4))))
    rows = []
    for method, names in re.findall(r"FishingMethod\.(\w+) to listOf\(([^)]*)\)", src):
        spot, tool = FISH_METHODS[method]
        for f in re.findall(r"\w+", names):
            lvl, name, xp = fish[f]
            rows.append([lvl, name, spot, tool, xp])
    rows.sort(key=lambda r: (r[0], r[1]))

    # Dragon and infernal harpoons have no level param in the cache; 61 is the OSRS Wiki's.
    harpoons = tool_table([fobjs[f] for f in re.findall(r"val (\w*harpoon\w*) = find", objs_block)],
                          {"dragon_harpoon": 61, "infernal_harpoon": 61})

    # Spots: the ones FishingSpotSpawns places, and world spawns of any npc the fishing script handles.
    npcs = convert.load_cache_npcs()
    special = {}
    for field, label in (("monkfish_spots", "Monkfish spot"), ("karambwanji_spots", "Karambwanji spot"),
                         ("karambwan_spots", "Karambwan spot"), ("anglerfish_spots", "Anglerfish spot")):
        block = re.search(field + r" =\s*(?:listOf\()?(.*?)\)\n", src, re.S).group(1)
        for sym in re.findall(r'find\("([^"]+)"\)', block):
            special[NPC_ID[sym]] = label

    def spot_label(nid):
        if nid in special:
            return special[nid]
        t = npcs.get(nid) or {}
        # The cache reader packs the options together, so the first two real ones are op1 and op3.
        ops = [o for o in (t.get("ops") or []) if o and o.lower() != "hidden"] + [None, None]
        if not (t.get("name") or "").lower().endswith("fishing spot"):
            return None
        return SPOT_PAIRS.get((ops[0], ops[1]))

    pts = []
    spawns_kt = read(os.path.join(base, "scripts", "FishingSpotSpawns.kt"))
    consts = {k: int(v) for k, v in re.findall(r"const val (\w+) = (\d+)", spawns_kt)}
    placed = [(consts[c], 0, int(x), int(z)) for c, x, z in re.findall(r'Spot\("[^"]+", (\w+), CoordGrid\((\d+), (\d+), 0\)\)', spawns_kt)]
    for (nid, lvl, x, z) in placed + list(world_spawns):
        label = spot_label(nid)
        if label:
            area = reach.where(lvl, x, z, radius=3)
            if area:
                pts.append((area, lvl, x, z, label))
    return {
        "tables": [
            {"id": "fish", "title": "Fish", "cols": ["Level", "Fish", "Spot and option", "You need", "XP each"], "rows": rows},
            {"id": "harpoons", "title": "Harpoons", "cols": ["Level", "Harpoon"], "rows": harpoons},
        ],
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "fishing"),
    }


# ---------------------------------------------------------------- firemaking

def firemaking(all_tasks):
    src = read(kt("firemaking", "configs", "FiremakingLogs.kt"))
    rows = [[int(l), obj_name(BASE_OBJS[f]), fmt_xp(float(x))]
            for f, l, x in re.findall(r"FiremakingLog\(objs\.(\w+), (\d+), ([\d.]+)\)", src)]
    rows.sort(key=lambda r: (r[0], r[1]))
    low = int(re.search(r"LOW = (\d+)", src).group(1))
    high = int(re.search(r"HIGH = (\d+)", src).group(1))
    certain = next(l for l in range(1, 100) if (1 + int(low * (99 - l) / 98 + high * (l - 1) / 98 + 0.5)) >= 256)
    return {
        "tables": [{"id": "logs", "title": "Logs", "cols": ["Level", "Logs", "XP each"], "rows": rows}],
        "lightChance": {"level1": pct(1 + low, 256), "certainFrom": certain},
        "places": [],
        "tasks": skill_tasks(all_tasks, "firemaking"),
    }


# ---------------------------------------------------------------- prayer

def prayer(reach, all_tasks):
    base = os.path.join(SKILLS, "prayer", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "prayer")
    src = read(os.path.join(base, "configs", "PrayerBones.kt"))
    bone_refs = refs(os.path.join(base, "configs", "PrayerBones.kt"))
    mult = float(re.search(r"OFFER_MULTIPLIER = ([\d.]+)", read(os.path.join(base, "scripts", "Altars.kt"))).group(1))
    rows = []
    for owner, f, xp in re.findall(r"PrayerBone\((objs|PrayerBoneObjs)\.(\w+), ([\d.]+)\)", src):
        sym = BASE_OBJS[f] if owner == "objs" else bone_refs[f]
        rows.append([obj_name(sym), fmt_xp(float(xp)), fmt_xp(round(float(xp) * mult, 1))])
    rows.sort(key=lambda r: (r[1], r[0]))

    # Altars: the two the isle places, and every map altar ("Altar" or "Chaos altar" with Pray-at) a player can reach.
    league = read(os.path.join(R.ISLE_KT, "prayer", "LeaguePrayerScript.kt"))
    pts = []
    for const, label in (("ALTAR", "Altar"), ("CHAOS_ALTAR", "Chaos altar")):
        x, z, lvl = map(int, re.search(r"val " + const + r" = CoordGrid\((\d+), (\d+), (\d+)\)", league).groups())
        area = reach.where(lvl, x, z)
        if area:
            pts.append((area, lvl, x, z, label))
    L = R.C.locs()
    wanted = {lid for lid, t in L.items() if t["name"] in ("Altar", "Chaos altar") and "Pray-at" in t["ops"]}
    for (lid, lvl, x, z, area) in reach.locs(wanted):
        pts.append((area, lvl, x, z, L[lid]["name"]))
    return {
        "tables": [{"id": "bones", "title": "Bones", "cols": ["Bones", "XP buried", "XP on an altar"], "rows": rows}],
        "altarMultiplier": mult,
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "prayer"),
    }


# ---------------------------------------------------------------- hunter

TRAP_NAMES = {"bird": "Bird snare", "box": "Box trap", "deadfall": "Deadfall", "netCreature": "Net trap", "pit": "Pitfall"}
HUNTER_SITES = {"hunting_deadfall_boulder": "Deadfall boulder", "hunting_sapling_up_swamp": "Young tree (net trap)",
                "hunting_sapling_up_orange": "Young tree (net trap)", "hunting_sapling_up_red": "Young tree (net trap)",
                "hunting_sapling_up_black": "Young tree (net trap)", "hunting_sapling_up_mountain": "Young tree (net trap)"}


def hunter(reach, all_tasks, world_spawns):
    base = os.path.join(SKILLS, "hunter", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "hunter")
    cre = read(os.path.join(base, "configs", "HunterCreatures.kt"))
    cre = cre[cre.index("val all"):]
    traps = []
    names = {}
    for kind, key, name, lvl, xp in re.findall(r'(bird|box|deadfall|netCreature|pit)\(\s*"(\w+)",\s*"([^"]+)",\s*(\d+),\s*([\d.]+),', cre):
        traps.append([int(lvl), name, TRAP_NAMES[kind], fmt_xp(float(xp))])
        names[name.lower()] = name
    traps.sort(key=lambda r: (r[0], r[1]))

    cat = read(os.path.join(base, "configs", "HunterCatchables.kt"))
    flies = []
    for name, lvl, xp, hands, hxp in re.findall(r'Butterfly\(\s*"([^"]+)",\s*(\d+),\s*([\d.]+),\s*(\d+),\s*([\d.]+),', cat):
        flies.append([int(lvl), name, fmt_xp(float(xp)), int(hands), fmt_xp(float(hxp))])
        names[name.lower()] = name
    imps = []
    for key, lvl, hands, xp in re.findall(r'Impling\("(\w+)", (\d+), (\d+), ([\d.]+),', cat):
        imps.append([int(lvl), key + " impling", int(hands), fmt_xp(float(xp))])
        names[(key + " impling").lower()] = key + " impling"
    imps.sort(key=lambda r: (r[0], r[1]))
    kebbits = []
    for name, lvl, xp in re.findall(r'Kebbit\("([^"]+)", (\d+), ([\d.]+),', cat):
        kebbits.append([int(lvl), name, fmt_xp(float(xp))])
        names[name.lower()] = name

    bh = read(os.path.join(base, "scripts", "Birdhouses.kt"))
    hrefs = read(os.path.join(base, "configs", "HunterRefs.kt"))
    hobjs = dict(re.findall(r'val (\w+) = find\("([^"]+)"\)', hrefs[hrefs.index("object HunterObjs"):hrefs.index("object HunterLocs")]))
    houses = [[int(h), obj_name(hobjs[f]), int(c), fmt_xp(float(cx)), fmt_xp(float(x))]
              for t, f, h, c, cx, x in re.findall(r'Tier\("(\w+)", o\.(\w+), o\.\w+, (\d+), (\d+), ([\d.]+), ([\d.]+),', bh)]

    # Places: creatures from the world spawns, and the trap sites on the map.
    npcs = convert.load_cache_npcs()

    def name_of(nid, depth=0):
        t = npcs.get(nid) or {}
        n = (t.get("name") or "").lower()
        if n in names:
            return names[n]
        if depth < 2:
            for m in t.get("multi") or []:
                if m not in (-1, 65535):
                    r = name_of(m, depth + 1)
                    if r:
                        return r
        return None

    pts = []
    for (nid, lvl, x, z) in world_spawns:
        label = name_of(nid)
        if label:
            area = reach.where(lvl, x, z)
            if area:
                pts.append((area, lvl, x, z, label))
    site_ids = {LOC_ID[s]: label for s, label in HUNTER_SITES.items()}
    for sym, lid in LOC_ID.items():
        if re.fullmatch(r"hunting_pitfall_\d+", sym):
            site_ids[lid] = "Pitfall"
        elif re.fullmatch(r"birdhouse_[1-4]", sym):
            site_ids[lid] = "Birdhouse spot"
    for (lid, lvl, x, z, area) in reach.locs(set(site_ids)):
        pts.append((area, lvl, x, z, site_ids[lid]))
    return {
        "tables": [
            {"id": "traps", "title": "Trapping", "cols": ["Level", "Creature", "Trap", "XP each"], "rows": traps},
            {"id": "butterflies", "title": "Butterflies and moths", "cols": ["Level with a net", "Butterfly", "XP", "Level barehanded", "XP barehanded"], "rows": sorted(flies)},
            {"id": "implings", "title": "Implings", "cols": ["Level with a net", "Impling", "Level barehanded", "XP"], "rows": imps},
            {"id": "falconry", "title": "Falconry", "cols": ["Level", "Kebbit", "XP"], "rows": kebbits},
            {"id": "birdhouses", "title": "Birdhouses", "cols": ["Hunter level", "Birdhouse", "Crafting level to build", "Crafting XP", "Hunter XP"], "rows": houses},
        ],
        "places": places(reach, pts, by_label=True),
        "tasks": skill_tasks(all_tasks, "hunter"),
    }


# ---------------------------------------------------------------- smithing

def smithing(reach, all_tasks):
    base = os.path.join(SKILLS, "smithing", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "smithing")
    sobjs = refs(os.path.join(base, "configs", "SmithingRefs.kt"))
    pobjs = refs(os.path.join(base, "configs", "SmithingProductObjs.kt"))
    bars_src = read(os.path.join(base, "configs", "SmeltingBars.kt"))
    smelt = []
    for b in blocks(bars_src, "SmeltingBar"):
        if "level = " not in b:
            continue
        bar = re.search(r"SmithingObjs\.(\w+)", b).group(1)
        ores = ", ".join(f"{c} {obj_name(sobjs[o]).lower()}" for o, c in re.findall(r"SmeltingIngredient\(SmithingObjs\.(\w+), (\d+)\)", b))
        chance = re.search(r"successChance = ([\d.]+)", b)
        smelt.append([num(b, "level"), obj_name(sobjs[bar]), ores, fmt_xp(num(b, "xp")),
                      f"{int(float(chance.group(1)) * 100)}%" if chance else "Always"])
    smelt.sort(key=lambda r: r[0])

    metals_src = read(os.path.join(base, "configs", "SmithingMetals.kt"))
    metals = re.findall(r"(\w+)\(\d+, SmithingObjs\.(\w+), ([\d.]+), \"\w+\"\)", metals_src)
    metal_names = [m[0] for m in metals]
    prod_src = read(os.path.join(base, "configs", "SmithingProducts.kt"))
    grid = {}  # label -> {"bars", "qty", metal: level}
    order = []
    for metal, slot, f, label, lvl, bars, qty in re.findall(
            r"SmithingProduct\(\s*SmithingMetal\.(\w+),\s*(\d+),\s*p\.(\w+),\s*\"([^\"]+)\",\s*(\d+),\s*(\d+),\s*(\d+),?\s*\)", prod_src):
        key = label
        if key not in grid:
            grid[key] = {"bars": int(bars), "qty": int(qty), "slot": int(slot)}
            order.append(key)
        grid[key][metal] = int(lvl)
    order.sort(key=lambda k: grid[k]["slot"])
    anvil = []
    for k in order:
        g = grid[k]
        name = k + (f" ({g['qty']})" if g["qty"] > 1 else "")
        anvil.append([name, g["bars"]] + [g.get(m, "") for m in metal_names])
    xp_rows = [[m, obj_name(sobjs[bar]), fmt_xp(float(x))] for m, bar, x in metals]

    L = R.C.locs()
    kinds = {lid: ("Furnace" if t["name"] == "Furnace" else "Anvil") for lid, t in L.items()
             if (t["name"] == "Furnace" and "Smelt" in t["ops"][:3]) or (t["name"] == "Anvil" and "Smith" in t["ops"])}
    pts = [(area, lvl, x, z, kinds[lid]) for (lid, lvl, x, z, area) in reach.locs(set(kinds))]
    starter = read(os.path.join(R.ISLE_KT, "starter", "StarterScript.kt"))
    fx, fz = map(int, re.search(r"val FURNACE = CoordGrid\((\d+), (\d+), 0\)", starter).groups())
    pts.append((reach.where(0, fx, fz) or "isle", 0, fx, fz, "Furnace"))
    return {
        "tables": [
            {"id": "smelting", "title": "Smelting", "cols": ["Level", "Bar", "Ores", "XP", "Works"], "rows": smelt},
            {"id": "anvil", "title": "Anvil", "cols": ["Item", "Bars"] + metal_names, "rows": anvil,
             "note": "The numbers under each metal are the Smithing level needed. XP is the bars used times the metal's XP per bar (below)."},
            {"id": "barxp", "title": "XP per bar", "cols": ["Metal", "Bar", "XP per bar"], "rows": xp_rows},
        ],
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "smithing"),
    }


# ---------------------------------------------------------------- cooking

def split_args(text):
    """Top-level comma-separated arguments of a call's argument text."""
    out, depth, cur, q = [], 0, "", False
    for c in text:
        if c == '"':
            q = not q
        if not q:
            depth += c in "(["
            depth -= c in ")]"
            if c == "," and depth == 0:
                out.append(cur.strip())
                cur = ""
                continue
        cur += c
    if cur.strip():
        out.append(cur.strip())
    return out


def cook_chance(low, high, level):
    level = max(1, min(99, level))
    return int((low * (99 - level) + high * (level - 1)) / 98.0 + 0.5) / 256.0


def stop_burn(rate, level):
    if not rate:
        return None
    return next((l for l in range(level, 100) if cook_chance(rate[0], rate[1], l) >= 1.0), None)


COOK_SIGS = {
    "meat": ["raw", "cooked", "burnt"],
    "fish": ["raw", "cooked", "burnt", "level", "xp", "low", "high"],
    "fish9": ["raw", "cooked", "burnt", "level", "xp", "fireLow", "fireHigh", "rangeLow", "rangeHigh"],
    "baked": ["raw", "cooked", "burnt", "level", "xp", "low", "high"],
    "CookableFood": ["raw", "cooked", "burnt", "level", "xp", "fire", "range"],
}


def cooking(reach, all_tasks):
    base = os.path.join(SKILLS, "cooking", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "cooking", "configs")
    foods_src = read(os.path.join(base, "CookingFoods.kt"))
    extras_src = read(os.path.join(base, "CookingExtras.kt"))
    crefs = {"CookingObjs": refs(os.path.join(base, "CookingFoods.kt")),
             "CookingExtraObjs": refs(os.path.join(base, "CookingExtras.kt"))}
    body = foods_src[foods_src.index("object CookingFoods"):foods_src.index("private val byRawId")] + \
        extras_src[extras_src.index("object CookingExtras"):]
    rows = {}
    for m in re.finditer(r"\b(meat|fish|baked|CookableFood)\(", body):
        kind = m.group(1)
        depth, i = 1, m.end()
        while depth:
            depth += body[i] == "("
            depth -= body[i] == ")"
            i += 1
        args = split_args(body[m.end():i - 1])
        if not args or "ObjType" in args[0] or "Objs." not in args[0]:
            continue  # a function definition, not a call
        sig = COOK_SIGS["fish9" if kind == "fish" and len(args) == 9 else kind]
        v = {}
        for n, a in enumerate(args):
            if re.match(r"\w+ = ", a):
                k, a = a.split(" = ", 1)
            else:
                k = sig[n]
            v[k] = a
        if kind == "meat":
            v.update(level="1", xp="30.0", low="128", high="512")

        def rate(text):
            r = re.match(r"CookRate\((?:low = )?(-?\d+), (?:high = )?(-?\d+)\)", text or "")
            return (int(r.group(1)), int(r.group(2))) if r else None

        if "low" in v:
            fire = None if kind == "baked" else (int(v["low"]), int(v["high"]))
            rng = (int(v["low"]), int(v["high"]))
        elif "fireLow" in v:
            fire, rng = (int(v["fireLow"]), int(v["fireHigh"])), (int(v["rangeLow"]), int(v["rangeHigh"]))
        else:
            fire, rng = rate(v.get("fire")), rate(v.get("range"))
        owner, field = v["cooked"].split(".")
        if field.startswith("gauntlet"):
            continue  # the Gauntlet's own paddlefish, on the Gauntlet page
        rowner, rfield = v["raw"].split(".")
        level = int(v["level"])
        name = obj_name(crefs[owner][field])
        sf, sr = stop_burn(fire, level), stop_burn(rng, level)
        rows.setdefault(name, [level, name, obj_name(crefs[rowner][rfield]), fmt_xp(float(v["xp"])),
                               "Can't" if fire is None else (sf or "Never"), sr or "Never"])
    cooked = sorted(rows.values(), key=lambda r: (r[0], r[1]))

    prep_src = read(os.path.join(base, "CookingPrep.kt"))
    prep = {}

    def add(product, level, xp, inputs, tools=()):
        product = product.split("#")[0]
        ins = " + ".join(i.split("#")[0] for i in inputs)
        if tools:
            ins += " (with a " + ", ".join(t.lower() for t in tools) + ")"
        key = (product, level)
        if key in prep:
            prep[key][2] += " or " + ins
        else:
            prep[key] = [level, product, ins, fmt_xp(xp)]

    for b in blocks(prep_src, "recipe"):
        args = split_args(b)
        if len(args) < 3 or not args[0].startswith('"'):
            continue
        strs = [a.strip('"') for a in args[3:] if a.startswith('"')]
        tools = re.findall(r'"([^"]+)"', next((a for a in args if a.startswith("tools")), ""))
        if args[0] == '"dough"' or "dough" == args[0]:
            continue
        add(args[0].strip('"'), int(args[1]), float(args[2]), strs, tools)
    for b in blocks(prep_src, "pie"):
        args = split_args(b)
        if not args or not args[0].isdigit():
            continue
        last = "Pie shell"
        for step in args[1:]:
            ing, made = re.findall(r'"([^"]+)"', step)
            add(made, int(args[0]), 0.0, [last, ing])
            last = made
    for dough, lvl in re.findall(r'"(\w+ \w+)" to (\d+)', prep_src[prep_src.index("private val doughs"):prep_src.index("private val pies")]):
        add(dough, int(lvl), 0.0, ["Pot of flour", "a bucket, bowl or jug of water"])
    prep_rows = sorted(prep.values(), key=lambda r: (r[0], r[1]))

    # Ranges, and fires that are part of the map, by the cooking script's own rule (Cooking.heatSource).
    L = R.C.locs()
    heat = {}
    for lid, t in L.items():
        n = (t["name"] or "").lower()
        if n in ("range", "cooking range"):
            heat[lid] = "Range"
        elif n == "fire" and not (t["ops"][0] or "").strip():
            heat[lid] = "Fire (always lit)"
    pts = [(area, lvl, x, z, heat[lid]) for (lid, lvl, x, z, area) in reach.locs(set(heat))]
    return {
        "tables": [
            {"id": "cook", "title": "Cooking on a fire or range", "cols": ["Level", "Food", "From", "XP", "Stops burning on a fire", "Stops burning on a range"], "rows": cooked},
            {"id": "prep", "title": "Preparing food", "cols": ["Level", "Make", "From", "XP"], "rows": prep_rows,
             "note": "Use one item on the other. Most steps give no XP; the XP comes when you cook the result."},
        ],
        "places": places(reach, pts, by_label=True),
        "tasks": skill_tasks(all_tasks, "cooking"),
    }


# ---------------------------------------------------------------- fletching

def fletching(all_tasks):
    base = os.path.join(SKILLS, "fletching", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "fletching", "configs")
    frefs_src = read(os.path.join(base, "FletchingRefs.kt"))
    fobjs = dict(re.findall(r'val (\w+) = find\("([^"]+)"\)', frefs_src[frefs_src.index("object FletchingObjs"):]))
    src = read(os.path.join(base, "FletchingRecipes.kt"))
    body = src[src.index("private fun MutableList<FletchingRecipe>.addShafts"):]

    def n(ref):
        return obj_name(fobjs[ref.split(".")[1]])

    def calls(name):
        for b in blocks(body, name):
            args = split_args(b)
            if args and args[0].startswith("o."):
                yield args

    logs, strings, xbows, ammo, tips = [], [], [], [], []
    for a in calls("cut"):
        count = next((int(x.split("= ")[1]) for x in a if x.startswith("logCount")), 1)
        made = n(a[1]) + (f" ({a[2]})" if int(a[2]) > 1 else "")
        logs.append([int(a[3]), made, f"{count} {n(a[0]).lower()}" if count > 1 else n(a[0]), fmt_xp(float(a[4]))])
    for a in calls("string"):
        strings.append([int(a[2]), n(a[1]), n(a[0]) + " + bow string", fmt_xp(float(a[3]))])
    for a in calls("crossbow"):
        lvl, xp = int(a[4]), float(a[5])
        sxp = next((float(x.split("= ")[1]) for x in a if x.startswith("stringXp")), xp / 2)
        xbows.append([lvl, n(a[2]), n(a[0]) + " + " + n(a[1]).lower() + " (with a hammer)", fmt_xp(xp)])
        strings.append([lvl, n(a[3]), n(a[2]) + " + crossbow string", fmt_xp(sxp)])
    for a in calls("attach"):
        slayer = any("SlayerUnlocks" in x for x in a)
        made = n(a[2]) + (" (needs the Broader Fletching Slayer unlock)" if slayer else "")
        ammo.append([int(a[4]), made, n(a[0]) + " + " + n(a[1]).lower(), fmt_xp(float(a[5])), int(a[3])])
    for a in calls("cutTips"):
        tips.append([int(a[3]), f"{n(a[1])} ({a[2]})", n(a[0]) + " (with a chisel)", fmt_xp(float(a[4]))])
    key = lambda r: (r[0], r[1])
    return {
        "tables": [
            {"id": "logs", "title": "Cutting logs", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(logs, key=key),
             "note": "Use a knife on the logs."},
            {"id": "stringing", "title": "Stringing", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(strings, key=key)},
            {"id": "crossbows", "title": "Crossbows", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(xbows, key=key)},
            {"id": "ammo", "title": "Arrows, darts, bolts and javelins", "cols": ["Level", "Make", "From", "XP each", "Per action"], "rows": sorted(ammo, key=key),
             "note": "XP is for each one made; one action makes up to the number shown."},
            {"id": "tips", "title": "Bolt tips", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(tips, key=key)},
        ],
        "places": [],
        "tasks": skill_tasks(all_tasks, "fletching"),
    }


# ---------------------------------------------------------------- runecraft

def runecraft(all_tasks):
    base = os.path.join(SKILLS, "runecraft", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "runecraft", "configs")
    # Only the RunecraftObjs block: the varbit and loc references reuse the same field names.
    rrefs = {}
    for f in os.listdir(base):
        text = read(os.path.join(base, f))
        if "object RunecraftObjs" in text:
            block = text[text.index("object RunecraftObjs"):]
            block = block[:block.index("\n}") + 2]
            rrefs.update(re.findall(r'val (\w+) = find\("([^"]+)"\)', block))
    src = read(os.path.join(base, "RuneAltars.kt"))
    altars, tiaras, by_key = [], [], {}
    for b in blocks(src, "RuneAltar"):
        if "level = " not in b:
            continue
        key = re.search(r'"(\w+)"', b).group(1)
        rune = obj_name(rrefs[re.search(r"RunecraftObjs\.(\w+)", b).group(1)])
        extra = [int(x) for x in re.findall(r"\d+", re.search(r"extraRuneLevels = (?:listOf\(([^)]*)\)|emptyList\(\))", b).group(1) or "")]
        extra = [x for x in extra if x <= 99]
        pure = "pureOnly = true" in b
        lvl, xp = num(b, "level"), num(b, "xp")
        by_key[key] = lvl
        altars.append([lvl, rune, fmt_xp(xp), "Pure only" if pure else "Rune or pure",
                       ", ".join(str(x) for x in extra) if extra else "-"])
        tiara = re.search(r"tiara = RunecraftObjs\.(\w+)", b)
        if tiara and num(b, "tiaraXp"):
            tiaras.append([obj_name(rrefs[tiara.group(1)]), fmt_xp(num(b, "tiaraXp"))])
    combos = []
    for f, lvl, a, b2, lo, hi in re.findall(r"ComboRune\(RunecraftObjs\.(\w+), (\d+), (\w+), (\w+), ([\d.]+), ([\d.]+)\)", src):
        low_altar, high_altar = sorted([a, b2], key=lambda k: by_key[k])
        combos.append([int(lvl), obj_name(rrefs[f]), f"{low_altar.title()} or {high_altar.title()} altar",
                       f"{fmt_xp(float(lo))} at the {low_altar} altar, {fmt_xp(float(hi))} at the {high_altar} altar"])
    pouches = []
    for b in blocks(read(os.path.join(base, "Pouches.kt")), "Pouch"):
        if "level = " not in b:
            continue
        f = re.search(r"RunecraftObjs\.(\w+)", b).group(1)
        name = obj_name(rrefs[f])
        cap = "8 at level 25, rising to 40 at 85" if "colossal" in f else str(num(b, "fixedCapacity"))
        pouches.append([num(b, "level"), name, cap])
    return {
        "tables": [
            {"id": "runes", "title": "Runes", "cols": ["Level", "Rune", "XP per essence", "Essence", "More runes per essence at"], "rows": sorted(altars)},
            {"id": "combos", "title": "Combination runes", "cols": ["Level", "Rune", "Where", "XP per essence"], "rows": sorted(combos),
             "note": "Pure essence, the other element's runes and its talisman, used on the altar. Each one works half the time; a binding necklace makes it always work."},
            {"id": "tiaras", "title": "Tiaras", "cols": ["Tiara", "XP"], "rows": tiaras,
             "note": "Use a blank tiara and the talisman on the altar."},
            {"id": "pouches", "title": "Essence pouches", "cols": ["Level", "Pouch", "Holds"], "rows": sorted(pouches)},
        ],
        "places": [],
        "tasks": skill_tasks(all_tasks, "runecraft"),
    }


# ---------------------------------------------------------------- crafting and herblore (from the dumps)

DUMPS = os.path.join(HERE, "dumps")


def load_dump(name):
    """A JSON dump written by a skill module's WikiDumpTest (see tools/wiki/README.md), or None."""
    path = os.path.join(DUMPS, name + ".json")
    if not os.path.exists(path):
        print(f"  no {name} dump: run its WikiDumpTest first (tools/wiki/README.md)", file=sys.stderr)
        return None
    return json.load(open(path, encoding="utf-8"))


def input_text(inputs):
    parts = []
    for i in inputs:
        names = " or ".join(dict.fromkeys(obj_name(o) for o in i["options"]))
        parts.append((f"{i['count']} " if i["count"] > 1 else "") + names)
    return " + ".join(parts)


CRAFT_TABLES = [
    ("gems", "Gems and snelms", ["Chisel"], "Use a chisel on an uncut gem or a snail shell. Opal, jade and red topaz can be crushed, which gives a little XP."),
    ("leather", "Leather, dragonhide and snakeskin", ["Sew"], "Use a needle on the leather; each piece also uses thread (one spool lasts 5 items)."),
    ("jewellery", "Jewellery", ["Mould"], "Use a gold or silver bar on a furnace with the mould (and gem) in your inventory."),
    ("stringing", "Stringing amulets", ["String"], None),
    ("glass", "Glass", ["Melt", "Blow"], "Use a bucket of sand and soda ash on a furnace for molten glass, then a glassblowing pipe on it."),
    ("pottery", "Pottery", ["Soften", "Shape", "Fire"], "Soften clay with water, shape it on a potter's wheel, then fire it in a pottery oven."),
    ("spinning", "Spinning and weaving", ["Spin", "Weave"], None),
    ("other", "Other crafts", ["Assemble"], None),
]


def crafting(reach, all_tasks):
    dump = load_dump("crafting")
    if not dump:
        return None
    seen, by_method = set(), collections.defaultdict(list)
    for r in dump["recipes"]:
        key = (r["product"], r["level"], json.dumps(r["inputs"]))
        if key in seen:
            continue
        seen.add(key)
        made = obj_name(r["product"]) + (f" ({r['output']})" if r["output"] > 1 else "")
        if r.get("slayerUnlock") is not None:
            made += " (needs a Slayer unlock)"
        tools = [obj_name(t) for t in r["tools"]]
        frm = input_text(r["inputs"]) + (f" (with a {', '.join(t.lower() for t in tools)})" if tools else "")
        by_method[r["method"]].append([r["level"], made, frm, fmt_xp(r["xp"])])
    tables = []
    for tid, title, methods, note in CRAFT_TABLES:
        rows = sorted((row for m in methods for row in by_method.get(m, [])), key=lambda x: (x[0], x[1]))
        if rows:
            t = {"id": tid, "title": title, "cols": ["Level", "Make", "From", "XP"], "rows": rows}
            if note:
                t["note"] = note
            tables.append(t)
    tan = [[obj_name(t["hide"]), obj_name(t["leather"]), f"{t['cost']} coin" + ("s" if t["cost"] > 1 else "")] for t in dump["tanning"]]
    tables.append({"id": "tanning", "title": "Tanning", "cols": ["Hide", "Leather", "Cost each"], "rows": tan,
                   "note": "A tanner turns hides into leather for coins. No level needed and no XP."})

    L = R.C.locs()
    kinds = {}
    for lid, t in L.items():
        name = t["name"] or ""
        letters = "".join(c for c in name if c.isalpha()).lower()
        sym = LOC_SYM.get(lid, "")
        if name.lower() == "spinning wheel":
            kinds[lid] = "Spinning wheel"
        elif name.lower() == "loom":
            kinds[lid] = "Loom"
        elif letters == "potterswheel":
            kinds[lid] = "Potter's wheel"
        elif "potteryoven" in sym or "pottery_oven" in sym:
            kinds[lid] = "Pottery oven"
        elif name == "Furnace" and "Smelt" in t["ops"][:3]:
            kinds[lid] = "Furnace"
    pts = [(area, lvl, x, z, kinds[lid]) for (lid, lvl, x, z, area) in reach.locs(set(kinds))]
    starter = read(os.path.join(R.ISLE_KT, "starter", "StarterScript.kt"))
    fx, fz = map(int, re.search(r"val FURNACE = CoordGrid\((\d+), (\d+), 0\)", starter).groups())
    pts.append((reach.where(0, fx, fz) or "isle", 0, fx, fz, "Furnace"))
    return {"tables": tables, "places": places(reach, pts), "tasks": skill_tasks(all_tasks, "crafting")}


def collapse_doses(rows):
    """One row per potion instead of one per dose: "Divine super attack potion (any dose)", XP as a range."""
    out = {}
    for lvl, made, frm, xp in rows:
        m = re.match(r"(.*)\((\d)\)$", made)
        if not m:
            out[(lvl, made)] = [lvl, made, frm, xp]
            continue
        k = (lvl, m.group(1))
        if k not in out:
            plain = re.sub(r"\(\d\)", "", re.sub(r"\b\d+ ", "", frm))
            out[k] = [lvl, m.group(1), plain, xp, xp, [lvl, made, frm, xp]]
        else:
            out[k][3] = min(out[k][3], xp)
            out[k][4] = max(out[k][4], xp)
            out[k][5] = None
    rows = []
    for r in out.values():
        if len(r) == 6 and r[5]:
            rows.append(r[5])  # only one dose can be made: show it as it is
        elif len(r) == 6:
            xp = r[3] if r[3] == r[4] else f"{r[3]}-{r[4]}"
            rows.append([r[0], r[1] + " (any dose)", r[2] + ", one per dose", xp])
        else:
            rows.append(r)
    return sorted(rows, key=lambda x: (x[0], x[1]))


def herblore(all_tasks):
    dump = load_dump("herblore")
    if not dump:
        return None
    clean = sorted([[h["cleanLevel"], h["name"], obj_name(h["grimy"]), fmt_xp(h["cleanXp"])] for h in dump["herbs"]])
    groups = collections.defaultdict(list)
    for r in dump["recipes"]:
        made = obj_name(r["product"])
        if r["outputMax"] > r["output"]:
            made += f" ({r['output']}-{r['outputMax']})"
        elif r["output"] > 1:
            made += f" ({r['output']})"
        tools = [obj_name(t) for t in r["tools"]]
        frm = " + ".join((f"{i['count']} " if i["count"] > 1 else "") + obj_name(i["obj"]) for i in r["inputs"])
        if tools:
            frm += " (with a " + ", ".join(t.lower() for t in tools) + ")"
        groups[r["method"]].append([r["level"], made, frm, fmt_xp(r["xp"])])
    key = lambda x: (x[0], x[1])
    return {
        "tables": [
            {"id": "clean", "title": "Cleaning herbs", "cols": ["Level", "Herb", "From", "XP"], "rows": clean,
             "note": "Click a grimy herb to clean it."},
            {"id": "unf", "title": "Unfinished potions", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(groups["Unfinished"], key=key)},
            {"id": "potions", "title": "Potions", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(groups["Potion"], key=key)},
            {"id": "upgrades", "title": "Upgrades and mixes", "cols": ["Level", "Make", "From", "XP"], "rows": collapse_doses(groups["Upgrade"])},
            {"id": "grinding", "title": "Grinding", "cols": ["Level", "Make", "From", "XP"], "rows": sorted(groups["Grind"], key=key),
             "note": "Use a pestle and mortar on the item."},
        ],
        "places": [],
        "tasks": skill_tasks(all_tasks, "herblore"),
    }


# ---------------------------------------------------------------- combat skills

def monsters(reach, world_spawns):
    """Every attackable npc a player can reach: [[level, name, area title, count]], one row per name, level and area."""
    npcs = convert.load_cache_npcs()
    counts = collections.Counter()
    for (nid, lvl, x, z) in world_spawns:
        t = npcs.get(nid) or {}
        if "Attack" in (t.get("ops") or []) and t.get("name"):
            area = reach.where(lvl, x, z)
            if area:
                counts[(t.get("level", 0), t["name"], reach.titles[area])] += 1
    return sorted([[lvl, name, area, n] for (lvl, name, area), n in counts.items()], key=lambda r: (r[0], r[1], r[2]))


def combat_tasks(all_tasks, *skills):
    rows = []
    for s in skills:
        rows += skill_tasks(all_tasks, s)
    rows.sort(key=lambda t: (TIERS.index(t["tier"]), t["name"]))
    return rows


TELEPORT_PLACES = {"blueGraveyard": "Lobby, blue team graveyard", "redGraveyard": "Lobby, red team graveyard",
                   "fightCaves": "Lobby plaza", "miningGuild": "Mining Guild", "mageOfZamorak": "Beside the Mage of Zamorak",
                   "aubury": "Beside Aubury", "essenceMine": "Rune essence mine", "abyss": "The middle of the Abyss"}
SPELLBOOKS = {"0": "Standard", "1": "Ancient", "2": "Lunar", "3": "Arceuus"}


def magic(all_tasks):
    tsv = os.path.join(ROOT, "docs", "isle_of_souls_magic_spells.tsv")
    books = collections.defaultdict(list)
    spell_names = {}
    for line in open(tsv, encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if len(p) < 6:
            continue
        spell_names[p[1]] = p[2]
        if p[5] not in SPELLBOOKS:
            continue
        # Every book has several Home Teleport objs (one per destination); they are one spell.
        row = [int(p[4]), "Home Teleport" if p[2].endswith("Home Teleport") else p[2]]
        if row not in books[SPELLBOOKS[p[5]]]:
            books[SPELLBOOKS[p[5]]].append(row)
    src = read(os.path.join(R.ISLE_KT, "magic", "IsleTeleportDestinations.kt"))
    tele = []
    for field, place in re.findall(r"objs\.(spell_\w+) to (\w+(?:\(RuneAltars\.\w+\))?)", src):
        m = re.match(r"altar\(RuneAltars\.(\w+)\)", place)
        where = f"Inside the {m.group(1)} altar" if m else TELEPORT_PLACES.get(place, place)
        tele.append([spell_names.get(BASE_OBJS[field]) or obj_name(BASE_OBJS[field]), where])
    tables = [{"id": "spells-" + b.lower(), "title": f"{b} spellbook", "cols": ["Level", "Spell"],
               "rows": sorted(books[b])} for b in ("Standard", "Ancient", "Lunar", "Arceuus") if books[b]]
    tables.append({"id": "teleports", "title": "Where teleports go", "cols": ["Spell", "Takes you to"], "rows": tele,
                   "note": "Teleports keep their real level, runes and XP, but land somewhere on the isle."})
    return {"tables": tables, "places": [], "tasks": combat_tasks(all_tasks, "magic")}


# ---------------------------------------------------------------- agility and slayer

ARENA_NAMES = {"Ledge": "Ledge", "Log": "Log balance", "Rope": "Rope balance", "Bars": "Monkey bars",
               "Hands": "Hand holds", "Plank": "Plank", "Swing": "Rope swing", "Pillar": "Stepping pillars",
               "LowWall": "Low wall", "Pads": "Pressure pads", "Spikes": "Floor spikes", "Saw": "Spinning blades",
               "Darts": "Dart trap", "Blade": "Swinging blade"}


def agility(all_tasks):
    src = read(os.path.join(SKILLS, "agility", "src", "main", "kotlin", "org", "rsmod", "content", "skills", "agility",
                            "configs", "ArenaConfigs.kt"))
    rows = []
    for kind, lvl, xp, rest in re.findall(r"ArenaKind\.(\w+) ->\s*ArenaStat\(\s*(\d+),\s*([\d.]+),([^)]*)\)", src):
        args = split_args(rest)
        sure = int(args[3]) if len(args) > 3 and args[3].isdigit() else None
        never_fail = args[0].strip() == "256" and args[1].strip() == "256"
        if float(xp) == 0:
            continue
        rows.append([int(lvl), ARENA_NAMES.get(kind, kind), fmt_xp(float(xp)),
                     "Never" if never_fail else (f"From level {sure}" if sure and sure < 99 else "At 99")])
    fee = int(re.search(r"const val FEE = (\d+)", src).group(1))
    return {
        "tables": [{"id": "obstacles", "title": "Arena obstacles and traps", "cols": ["Level", "Obstacle", "XP", "Stops failing"],
                    "rows": sorted(rows)}],
        "fee": fee,
        "places": [],
        "tasks": skill_tasks(all_tasks, "agility"),
    }


def slayer(reach, all_tasks, world_spawns):
    data = json.load(open(os.path.join(ROOT, "tools", "slayer", "slayer_data.json"), encoding="utf-8"))
    reach_count = collections.Counter()
    for (nid, lvl, x, z) in world_spawns:
        if reach.where(lvl, x, z):
            reach_count[nid] += 1
    masters = list(data["masters"])
    tasks = {}
    for m, md in data["masters"].items():
        for t in md["tasks"]:
            e = tasks.setdefault(t["taskId"], {"name": t["task"], "npcs": set(), "by": {}, "slayer": 1, "combat": t["combat"]})
            e["npcs"] |= set(t["npcs"])
            e["by"][m] = f"{t['weight']} ({t['min']}-{t['max']})"
            for lvl, stat in t["stats"]:
                if stat == 18:
                    e["slayer"] = max(e["slayer"], lvl)
    rows, reachable = [], []
    for tid, e in sorted(tasks.items(), key=lambda kv: kv[1]["name"]):
        n = sum(reach_count[i] for i in e["npcs"])
        rows.append([e["slayer"], e["name"]] + [e["by"].get(m, "") for m in masters] + [n])
        reachable.append(n > 0)
    order = sorted(range(len(rows)), key=lambda i: (rows[i][0], rows[i][1]))
    rows = [rows[i] for i in order]
    reachable = [reachable[i] for i in order]
    mrows = [[m.title(), md.get("combat", 0), md.get("slayer", 0) or 1] for m, md in data["masters"].items()]
    return {
        "tables": [
            {"id": "masters", "title": "Slayer masters", "cols": ["Master", "Combat level needed", "Slayer level needed"], "rows": mrows},
            {"id": "tasks", "title": "Slayer tasks", "cols": ["Slayer level", "Task"] + [f"{m.title()}: weight (amount)" for m in masters] + ["Monsters you can reach"],
             "rows": rows, "reachable": reachable,
             "note": "A higher weight means the master gives that task more often. The last column counts the monsters of that task standing somewhere you can walk to."},
        ],
        "places": [],
        "tasks": skill_tasks(all_tasks, "slayer"),
    }


# ---------------------------------------------------------------- thieving

def drop_list(text, objs):
    """[(item name, amount text, weight)] for every Drop(o.x, a..b[, w]) in text."""
    out = []
    for f, a, b, w in re.findall(r"Drop\(o\.(\w+), (\d+)\.\.(\d+)(?:, ([\d.]+))?", text):
        amount = a if a == b else f"{a}-{b}"
        out.append((obj_name(objs[f]), amount, float(w) if w else 1.0))
    return out


def chance_rows(drops):
    total = sum(w for _, _, w in drops) or 1
    rows = [{"item": n, "amount": a, "pct": pct(w, total)} for n, a, w in drops]
    rows.sort(key=lambda r: -r["pct"])
    return rows


def section(text, field):
    """The text of `field = listOf(...)` inside a block, or ''."""
    m = re.search(r"\b" + field + r" =\s*listOf\(", text)
    if not m:
        return ""
    depth, i = 1, m.end()
    while depth:
        depth += text[i] == "("
        depth -= text[i] == ")"
        i += 1
    return text[m.end():i - 1]


# Names for targets and stalls that would otherwise share a name, by their key in the Kotlin table.
THIEVING_NAMES = {
    "knight": "Knight of Ardougne", "knight_of_varlamore": "Knight of Varlamore",
    "elf_lletya": "Elf (Lletya)", "elf_prifddinas": "Elf (Prifddinas)", "digsite_workman": "Digsite workman",
    "mor_ul_rek_gem": "TzHaar gem stall", "mor_ul_rek_ore": "TzHaar ore stall",
}


def success(low, high, level):
    """The Wiki's interpolated pickpocket chance at `level`, as a whole percent (PickpocketTarget.chance)."""
    level = max(1, min(99, level))
    value = int(low * (99 - level) / 98.0 + high * (level - 1) / 98.0 + 0.5)
    return max(0, min(100, round(100 * (value + 1) / 256)))


def thieving(reach, all_tasks, world_spawns):
    objs = refs(kt("thieving", "configs", "ThievingObjs.kt"))
    stall_locs = refs(kt("thieving", "configs", "Stalls.kt"))
    npcs = convert.load_cache_npcs()

    def names_of(nid, depth=0):
        t = npcs.get(nid)
        if not t or depth > 2:
            return []
        out = [(t.get("name") or "", t.get("ops") or [])]
        for m in t.get("multi") or []:
            if m not in (-1, 65535):
                out += names_of(m, depth + 1)
        return out

    targets, pts = [], []
    for b in blocks(read(kt("thieving", "configs", "PickpocketTargets.kt")), "PickpocketTarget"):
        names = re.findall(r'"([^"]+)"', section(b, "npcs"))
        option = (re.search(r'option = "([^"]+)"', b) or [None, "Pickpocket"])[1]
        ids = [int(i) for i in re.findall(r"\d+", section(b, "npcIds"))]
        pouch = re.search(r"pouch = CoinPouch\(o\.(\w+), (\d+)\.\.(\d+)\)", b)
        stun = re.search(r"stun = (\d+)\.\.(\d+)", b)
        key = re.search(r'key = "([^"]+)"', b).group(1)
        label = re.search(r'name = "([^"]+)"', b).group(1)
        label = THIEVING_NAMES.get(key) or label[0].upper() + label[1:]
        always = [{"item": n, "amount": a} for n, a, _ in drop_list(section(b, "always"), objs)]
        first = [{"item": n, "amount": a, "pct": pct(w)} for n, a, w in drop_list(section(b, "first"), objs)]
        targets.append({
            "name": label, "npcs": names, "level": num(b, "level"), "xp": fmt_xp(num(b, "xp")),
            "stun": stun.group(1) if stun.group(1) == stun.group(2) else f"{stun.group(1)}-{stun.group(2)}",
            "successAtLevel": success(num(b, "low"), num(b, "high"), num(b, "level")),
            "success99": success(num(b, "low"), num(b, "high"), 99),
            "fireCape": "needsFireCape = true" in b,
            "pouch": None if not pouch else {"name": obj_name(objs[pouch.group(1)]),
                                              "coins": pouch.group(2) if pouch.group(2) == pouch.group(3) else f"{pouch.group(2)}-{pouch.group(3)}"},
            "always": always, "rare": first, "table": chance_rows(drop_list(section(b, "table"), objs)),
        })
        lower = {n.lower() for n in names}
        for (nid, lvl, x, z) in world_spawns:
            if ids and nid not in ids:
                continue
            if any(n.lower() in lower and any(o and o.lower() == option.lower() for o in ops) for n, ops in names_of(nid)):
                area = reach.where(lvl, x, z)
                if area:
                    pts.append((area, lvl, x, z, label))
    targets.sort(key=lambda t: (t["level"], t["name"]))

    stalls, loc_label = [], {}
    for b in blocks(read(kt("thieving", "configs", "Stalls.kt")), "Stall"):
        if "level = " not in b:
            continue
        key = re.search(r'key = "([^"]+)"', b).group(1)
        label = re.search(r'name = "([^"]+)"', b).group(1)
        label = THIEVING_NAMES.get(key) or label[0].upper() + label[1:]
        for f in re.findall(r"StallLoc\(l\.(\w+),", b):
            loc_label[LOC_ID[stall_locs[f]]] = label
        stalls.append({"name": label, "level": num(b, "level"), "xp": fmt_xp(num(b, "xp")),
                       "loot": chance_rows(drop_list(section(b, "loot"), objs))})
    stalls.sort(key=lambda s: (s["level"], s["name"]))
    for (lid, lvl, x, z, area) in reach.locs(set(loc_label)):
        pts.append((area, lvl, x, z, loc_label[lid]))

    reachable = {p[4] for p in pts}
    for t in targets:
        t["reachable"] = t["name"] in reachable
    for s in stalls:
        s["reachable"] = s["name"] in reachable
    return {
        "tables": [
            {"id": "pickpocket", "title": "Pickpocketing", "cols": ["Level", "Target", "XP", "Stun damage", "Success at its level / 99"],
             "rows": [[t["level"], t["name"], t["xp"], t["stun"], f"{t['successAtLevel']}% / {t['success99']}%"] for t in targets],
             "reachable": [t["reachable"] for t in targets]},
            {"id": "stalls", "title": "Stalls", "cols": ["Level", "Stall", "XP"],
             "rows": [[s["level"], s["name"], s["xp"]] for s in stalls],
             "reachable": [s["reachable"] for s in stalls]},
        ],
        "targets": targets,
        "stalls": stalls,
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "thieving"),
    }


# ---------------------------------------------------------------- main

def main():
    settings = json.load(open(os.path.join(ROOT, ".data", "league", "event-settings.json"), encoding="utf-8"))
    xp = settings.get("xpMultiplier", {})
    reach = R.Reach()
    all_tasks = tasks(settings)
    world = spawns()
    data = {
        "generated": datetime.date.today().isoformat(),
        "xp": {"base": xp.get("base", 5.0), "byRelicTier": xp.get("byRelicTier", {}),
               "combatBonus": xp.get("combatBonus", 2.0), "combatBonusTier": xp.get("combatBonusTier", 3)},
        "respawnSpeed": settings.get("respawnSpeed", 1.0),
        "areas": {k: t for k, t, _ in reach.areas},
        "entrances": entrances(),
        "skills": {
            "mining": mining(reach, all_tasks),
            "thieving": thieving(reach, all_tasks, world),
            "woodcutting": woodcutting(reach, all_tasks),
            "fishing": fishing(reach, all_tasks, world),
            "firemaking": firemaking(all_tasks),
            "prayer": prayer(reach, all_tasks),
            "hunter": hunter(reach, all_tasks, world),
            "smithing": smithing(reach, all_tasks),
            "cooking": cooking(reach, all_tasks),
            "fletching": fletching(all_tasks),
            "runecraft": runecraft(all_tasks),
            "crafting": crafting(reach, all_tasks),
            "herblore": herblore(all_tasks),
            "melee": {"tables": [{"id": "monsters", "title": "Monsters you can reach", "cols": ["Combat level", "Monster", "Where", "How many"],
                                  "rows": monsters(reach, world)}],
                      "places": [], "tasks": combat_tasks(all_tasks, "attack", "strength", "defence")},
            "ranged": {"tables": [], "places": [], "tasks": combat_tasks(all_tasks, "ranged")},
            "hitpoints": {"tables": [], "places": [], "tasks": combat_tasks(all_tasks, "hitpoints")},
            "magic": magic(all_tasks),
            "agility": agility(all_tasks),
            "slayer": slayer(reach, all_tasks, world),
        },
    }
    # Leave out a skill whose dump is missing, and drop rows that came out the same twice.
    data["skills"] = {k: v for k, v in data["skills"].items() if v}
    for skill in data["skills"].values():
        for t in skill["tables"]:
            if "reachable" in t:
                continue
            seen, rows = set(), []
            for row in t["rows"]:
                if json.dumps(row) not in seen:
                    seen.add(json.dumps(row))
                    rows.append(row)
            t["rows"] = rows
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("// Generated by tools/wiki/gen_skills.py. Do not edit; change the game code and re-run.\n")
        f.write("window.ISLE_SKILLS_GEN = ")
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write(";\n")
    for key, s in data["skills"].items():
        print(f"{key}: {', '.join(str(len(t['rows'])) + ' ' + t['id'] for t in s['tables'])}, "
              f"{len(s['places'])} places, {len(s['tasks'])} tasks")
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
