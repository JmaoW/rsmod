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


def obj_name(sym):
    """The in-game name of the obj with internal name `sym`."""
    global _objs
    if _objs is None:
        files = js5.ref_table(2)[10]
        group = js5.split_files(js5.read_group(2, 10), files)
        _objs = {oid: objdump.decode(data).get("name") for oid, data in group.items()}
    oid = OBJ_ID.get(sym)
    if oid is None:
        raise KeyError(f"no obj named {sym}")
    return _objs.get(oid) or sym


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

def places(reach, points):
    """Groups reachable (level, x, z, label) points into places: [{area, title, level, x, z, box, things}]."""
    out = []
    by_area = collections.defaultdict(list)
    for (area, lvl, x, z, label) in points:
        by_area[area].append((lvl, x, z, label))
    order = [k for k, _, _ in reach.areas]
    for area in sorted(by_area, key=order.index):
        for g in R.clusters(by_area[area]):
            xs = [p[1] for p in g]
            zs = [p[2] for p in g]
            counts = collections.Counter(p[3] for p in g)
            x, z = round(sum(xs) / len(xs)), round(sum(zs) / len(zs))
            out.append({"area": area, "title": reach.titles[area], "level": g[0][0], "x": x, "z": z,
                        "box": [min(xs), min(zs), max(xs), max(zs)],
                        "dir": compass(x, z) if area == "isle" else None,
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
    picks, seen = [], set()
    for f, lvl in re.findall(r"standard\(\s*objs\.(\w+),\s*levelReq = (\d+)", picks_kt):
        name = obj_name(BASE_OBJS[f])
        if name in seen or is_tool_variant(BASE_OBJS[f], name):
            continue
        seen.add(name)
        picks.append({"name": name, "level": int(lvl)})
    picks.sort(key=lambda p: (p["level"], p["name"]))

    found = reach.locs(set(loc_label))
    pts = [(area, lvl, x, z, loc_label[lid]) for (lid, lvl, x, z, area) in found]
    return {
        "tables": [
            {"id": "rocks", "title": "Rocks", "cols": ["Level", "Rock", "Gives", "XP each"],
             "rows": [[r["level"], r["rock"], r["gives"], r["xp"]] for r in rows]},
            {"id": "pickaxes", "title": "Pickaxes", "cols": ["Level", "Pickaxe"],
             "rows": [[p["level"], p["name"]] for p in picks]},
        ],
        "gems": {"chance": gem_chance, "gems": gems},
        "places": places(reach, pts),
        "tasks": skill_tasks(all_tasks, "mining"),
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
        },
    }
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
