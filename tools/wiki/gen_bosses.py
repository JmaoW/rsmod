#!/usr/bin/env python3
"""Builds the generated half of the wiki's boss pages -> wiki/data/bosses-generated.js

    python tools/wiki/gen_bosses.py

What it reads, so a change to any of these needs a re-run:
- the vanilla cache: npc stats and sizes, item names, the collection log (tab structs 471/472/474);
- api/cache-enricher npcs.toml: weaknesses, immunities, attack type and Slayer XP;
- the drop tables (content/other/drop-tables: osrs.toml, boosted.toml, isle.toml) and
  .data/league/event-settings.json `dropMultiplier` (the relic drop boost);
- the boss modules' Kotlin for where each entrance stands, and the wave files of the Fight Caves
  and the Inferno with the event settings' modes;
- the league task list (through gen_skills.tasks).

The hand-written half of each page (how to get in, the fight, loot rules, differences) is
wiki/data/bosses.js. Page and fight ids here and there must match.
"""
import collections
import datetime
import json
import os
import re
import sys
import tomllib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_skills as GS  # noqa: E402
import gen_relics  # noqa: E402
import sprites  # noqa: E402

ROOT = GS.ROOT
R = GS.R
js5 = sprites.js5  # the cs2 cache reader (configs, enums, structs)
spawn_js5 = GS.js5  # the other reader, for npc configs

OUT = os.path.join(ROOT, "wiki", "data", "bosses-generated.js")
ISLE = R.ISLE_KT
DROPS = os.path.join(ROOT, "content", "other", "drop-tables", "src", "main", "resources", "org", "rsmod",
                     "content", "other", "drops")
NPCS_TOML = os.path.join(ROOT, "api", "cache-enricher", "src", "main", "resources", "org", "rsmod", "api",
                         "cache", "enricher", "npc", "npcs.toml")
ISLE_RES = os.path.join(ROOT, "content", "areas", "misc", "isle-of-souls", "src", "main", "resources", "org",
                        "rsmod", "content", "areas", "misc", "isleofsouls")


def kt(*parts):
    return GS.read(os.path.join(ISLE, *parts))


def coord(src, name):
    """(x, z, level) of `val <name> = CoordGrid(x, z, level)` in a Kotlin source."""
    m = re.search(r"\b" + name + r" = CoordGrid\((\d+), (\d+), (\d+)\)", src)
    if not m:
        raise KeyError(f"no CoordGrid called {name}")
    return tuple(map(int, m.groups()))


# ---------------------------------------------------------------- the pages
#
# fights: id -> what to generate for one boss (or one encounter) on a page.
#   npcs    the npcs whose stats are shown: cache internal names, or "name:<cache name>" for the
#           first npc of that name with hitpoints
#   tables  [(label, drop source, npcs filter or None)]: drop tables, by `source` in the drop files.
#           A filter picks the [[monster]] blocks that list exactly those npcs (Obor's chest); None
#           picks the blocks with no `npcs` list (the kill table, with isle.toml's additions).
#   hp      hitpoints set by the code rather than the cache (said in a comment where)
#   labels  names for the stat rows when the cache names are the same (the Kalphite Queen's forms)
#
# pages: id -> fights, collection log categories (cache names), league task ids, entrances.
# Entrances are (label, x, z, level): the tile a player stands on to use it.

FIGHTS = {
    "zulrah": dict(npcs=["snakeboss_boss_ranged"], tables=[("Zulrah", "Zulrah", None)]),
    "vorkath": dict(npcs=["vorkath"], tables=[("Vorkath", "Vorkath", None)], hp={"vorkath": 750}),  # Vorkath.kt sets 750 at spawn
    "kalphite_queen": dict(npcs=["kalphite_queen", "kalphite_flyingqueen"], labels=["Crawling form", "Airborne form"], tables=[("Kalphite Queen", "Kalphite Queen", None)]),
    "king_black_dragon": dict(npcs=["name:King Black Dragon"], tables=[("King Black Dragon", "King Black Dragon", None)]),
    # ScurriusConfigs: the instance boss has no stats in the cache; the NpcEditor gives it 500.
    "scurrius": dict(npcs=["rat_boss_instance"], hp={"rat_boss_instance": 500},
                     tables=[("Top damage", "Scurrius", None),
                             ("Everyone else in the lair", "Scurrius (non-MVP)", ["rat_boss_giant_rat_patrol_control"])]),
    "obor": dict(npcs=["name:Obor"], tables=[("Obor (kill)", "Obor", None), ("Obor's chest", "Obor", ["gb_hillgiant_chest"])]),
    "bryophyta": dict(npcs=["name:Bryophyta"], tables=[("Bryophyta (kill)", "Bryophyta", None),
                                                       ("Bryophyta's chest", "Bryophyta", ["gb_mossgiant_chest"])]),
    "dagannoth_rex": dict(npcs=["name:Dagannoth Rex"], tables=[("Dagannoth Rex", "Dagannoth Rex", None)]),
    "dagannoth_prime": dict(npcs=["name:Dagannoth Prime"], tables=[("Dagannoth Prime", "Dagannoth Prime", None)]),
    "dagannoth_supreme": dict(npcs=["name:Dagannoth Supreme"], tables=[("Dagannoth Supreme", "Dagannoth Supreme", None)]),
    "spinolyp": dict(npcs=["name:Spinolyp"], tables=[("Spinolyp", "Spinolyp", None)]),
    "callisto": dict(npcs=["name:Callisto"], tables=[("Callisto", "Callisto", None)]),
    "artio": dict(npcs=["name:Artio"], tables=[("Artio", "Artio", None)]),
    "venenatis": dict(npcs=["name:Venenatis"], tables=[("Venenatis", "Venenatis", None)]),
    "spindel": dict(npcs=["name:Spindel"], tables=[("Spindel", "Spindel", None)]),
    "vetion": dict(npcs=["name:Vet'ion"], tables=[("Vet'ion", "Vet'ion", None)]),
    "calvarion": dict(npcs=["name:Calvar'ion"], tables=[("Calvar'ion", "Calvar'ion", None)]),
    "kreearra": dict(npcs=["name:Kree'arra", "name:Flight Kilisa", "name:Wingman Skree", "name:Flockleader Geerin"],
                     tables=[("Kree'arra", "Kree'arra", None), ("Flight Kilisa", "Flight Kilisa", None),
                             ("Wingman Skree", "Wingman Skree", None), ("Flockleader Geerin", "Flockleader Geerin", None)]),
    "graardor": dict(npcs=["name:General Graardor", "name:Sergeant Strongstack", "name:Sergeant Steelwill", "name:Sergeant Grimspike"],
                     tables=[("General Graardor", "General Graardor", None), ("Sergeant Strongstack", "Sergeant Strongstack", None),
                             ("Sergeant Steelwill", "Sergeant Steelwill", None), ("Sergeant Grimspike", "Sergeant Grimspike", None)]),
    "zilyana": dict(npcs=["name:Commander Zilyana", "name:Starlight", "name:Growler", "name:Bree"],
                    tables=[("Commander Zilyana", "Commander Zilyana", None), ("Starlight", "Starlight", None),
                            ("Growler", "Growler", None), ("Bree", "Bree", None)]),
    "kril": dict(npcs=["name:K'ril Tsutsaroth", "name:Tstanon Karlak", "name:Zakl'n Gritch", "name:Balfrug Kreeyath"],
                 tables=[("K'ril Tsutsaroth", "K'ril Tsutsaroth", None), ("Tstanon Karlak", "Tstanon Karlak", None),
                         ("Zakl'n Gritch", "Zakl'n Gritch", None), ("Balfrug Kreeyath", "Balfrug Kreeyath", None)]),
    "jad": dict(npcs=["tzhaar_fightcave_swarm_boss"], tables=[]),
    "zuk": dict(npcs=["name:TzKal-Zuk", "inferno_jad"], labels=["TzKal-Zuk", "JalTok-Jad (waves 67 and 68, and with Zuk)"], tables=[]),
    "hunllef": dict(npcs=["crystal_hunllef_melee", "crystal_hunllef_melee_hm"], tables=[]),
    "sol": dict(npcs=["colosseum_sol_p1"], tables=[]),
    "toa": dict(npcs=[], tables=[]),
    # Not built yet: stats only, so the planned page has something to show.
    "cerberus": dict(npcs=["name:Cerberus"], tables=[]),
    "grotesque_guardians": dict(npcs=["name:Dusk", "name:Dawn"], tables=[]),
    "thermonuclear_smoke_devil": dict(npcs=["name:Thermonuclear smoke devil"], tables=[]),
    "hespori": dict(npcs=["name:Hespori"], tables=[]),
}

# The echo bosses' own uniques, by their drop source (osrs.toml), keyed by the page they belong to.
ECHOES = {
    "kalphite_queen": "Echo Kalphite Queen", "king_black_dragon": "Echo King Black Dragon",
    "gauntlet": "Echo Corrupted Hunllef", "colosseum": "Echo Sol Heredit", "cerberus": "Echo Cerberus",
    "grotesque_guardians": "Echo Grotesque Guardians", "thermonuclear_smoke_devil": "Echo Thermonuclear smoke devil",
    "hespori": "Echo Hespori", "dagannoth_kings": None,
}


def isle_entrances():
    """Where each boss is entered, from the boss code: {key: (label, x, z, level)}."""
    out = {}
    zul = kt("zulrah", "Zulrah.kt")
    out["zulrah"] = ("Sacrificial boat", *coord(zul, "boat"))
    out["vorkath"] = ("Torfinn's boat", *coord(kt("vorkath", "Vorkath.kt"), "coords"))
    out["dk"] = ("Ladder down", *coord(kt("dagannothkings", "DagannothKings.kt"), "coords"))
    out["kbd"] = ("Lever", *coord(kt("kingblackdragon", "KingBlackDragon.kt"), "coords"))
    out["toa"] = ("Pyramid door", *coord(kt("toa", "ToaLobbyScript.kt"), "ENTRANCE"))
    out["gauntlet"] = ("Portal", *coord(kt("gauntlet", "GauntletScript.kt"), "PORTAL"))
    out["colosseum"] = ("Arch", *coord(kt("colosseum", "ColosseumArena.kt"), "isleEntrance"))
    lairs = kt("wilderness", "WildernessLairs.kt")
    for name, x, z, lvl in re.findall(r"wild_locs\.(\w+)_entrance,\s*CoordGrid\((\d+), (\d+), (\d+)\)", lairs):
        out["wild_" + name] = (name.replace("vetion", "Vet'ion").replace("calvarion", "Calvar'ion").title()
                               .replace("Vet'Ion", "Vet'ion").replace("Calvar'Ion", "Calvar'ion") + "'s lair door",
                               int(x), int(z), int(lvl))
    # Doc-recorded spots (isle_of_souls_*.md) where the code uses the cache's own map loc:
    out["kq"] = ("Rope tunnel", 3509, 9497, 2)            # KalphiteScript: the tunnel on level 2 of the Kalphite Lair
    out["obor"] = ("Obor's gate", 3095, 9831, 0)          # GiantConfigs: gate at 3095,9832-3 in Edgeville Dungeon
    out["bryophyta"] = ("Bryophyta's gate", 3174, 9900, 0)  # gate at 3174,9901 in the Varrock Sewers
    out["scurrius"] = ("Broken bars", 3281, 9867, 0)      # ScurriusScript: the bars at 3282,9867
    return out


PAGES = {
    "zulrah": dict(fights=["zulrah"], clog=["Zulrah"], tasks=[540, 553, 554, 1397, 1390], entrances=["zulrah"]),
    "vorkath": dict(fights=["vorkath"], clog=["Vorkath"], tasks=[585, 602, 605, 608, 609, 610], entrances=["vorkath"]),
    "kalphite_queen": dict(fights=["kalphite_queen"], clog=["Kalphite Queen"], tasks=[864, 874, 875], entrances=["kq"]),
    "king_black_dragon": dict(fights=["king_black_dragon"], clog=["King Black Dragon"], tasks=[1000], entrances=["kbd"]),
    "scurrius": dict(fights=["scurrius"], clog=["Scurrius"], tasks=[1648, 1649, 1650], entrances=["scurrius"]),
    "giants": dict(fights=["obor", "bryophyta"], clog=["Obor", "Bryophyta"], tasks=[472, 476, 477], entrances=["obor", "bryophyta"]),
    "dagannoth_kings": dict(fights=["dagannoth_rex", "dagannoth_prime", "dagannoth_supreme", "spinolyp"], clog=["Dagannoth Kings"],
                            tasks=[596, 597, 599, 601, 604, 607, 613, 614, 615], entrances=["dk"]),
    "wilderness": dict(fights=["callisto", "artio", "venenatis", "spindel", "vetion", "calvarion"],
                       clog=["Callisto and Artio", "Venenatis and Spindel", "Vet'ion and Calvar'ion"],
                       tasks=[992, 1404, 1405, 1406, 988, 1398, 1399, 1400, 996, 1401, 1402, 1403, 1003, 993, 989, 997, 1256],
                       entrances=["wild_callisto", "wild_artio", "wild_venenatis", "wild_spindel", "wild_vetion", "wild_calvarion"]),
    "god_wars": dict(fights=["kreearra", "graardor", "zilyana", "kril"],
                     clog=["Kree'arra", "General Graardor", "Commander Zilyana", "K'ril Tsutsaroth"],
                     tasks=[797, 793, 801, 788, 809, 810, 811, 1011, 794, 798, 790, 791, 795, 796, 789, 784, 802], entrances=[]),
    "fight_caves": dict(fights=["jad"], clog=["The Fight Caves"], tasks=[664, 665, 653, 657], entrances=[], waves="fightcaves"),
    "inferno": dict(fights=["zuk"], clog=["The Inferno"], tasks=[661, 667, 668, 669, 654], entrances=[], waves="inferno"),
    "toa": dict(fights=["toa"], clog=["Tombs of Amascut"], tasks=[1186, 1187, 1188, 1189, 1228, 1230, 1231, 1232, 1233, 1236],
                entrances=["toa"]),
    "gauntlet": dict(fights=["hunllef"], clog=["The Gauntlet"], tasks=[534, 535, 547, 548, 530, 1512, 531, 546, 549, 1396],
                     entrances=["gauntlet"]),
    "colosseum": dict(fights=["sol"], clog=["Fortis Colosseum"],
                      tasks=[1655, 1662, 1664, 1665, 1666, 1667, 1668, 1669, 1670, 1671, 1672, 1683, 1684], entrances=["colosseum"]),
    "cerberus": dict(fights=["cerberus"], clog=["Cerberus"], tasks=[], entrances=[]),
    "grotesque_guardians": dict(fights=["grotesque_guardians"], clog=["Grotesque Guardians"], tasks=[], entrances=[]),
    "thermonuclear_smoke_devil": dict(fights=["thermonuclear_smoke_devil"], clog=["Thermonuclear Smoke Devil"], tasks=[], entrances=[]),
    "hespori": dict(fights=["hespori"], clog=["Hespori"], tasks=[], entrances=[]),
}


# ---------------------------------------------------------------- npc stats

def parse_npc(d):
    """Name, combat level, size and the six combat stats of a cache npc config."""
    out = {"name": None, "level": 0, "size": 1, "stats": [1, 1, 1, 1, 1, 1]}
    p = 0
    while p < len(d):
        op = d[p]
        p += 1
        if op == 0:
            break
        elif op == 1 or op == 60:
            p += 1 + 2 * d[p]
        elif op == 2:
            out["name"], p = GS.convert._cstr(d, p)
        elif op == 3:
            _, p = GS.convert._cstr(d, p)
        elif op == 12:
            out["size"] = d[p]
            p += 1
        elif op in (13, 14, 15, 16, 18):
            p += 2
        elif op == 17:
            p += 8
        elif 30 <= op <= 34:
            _, p = GS.convert._cstr(d, p)
        elif op in (40, 41):
            p += 1 + 4 * d[p]
        elif 74 <= op <= 79:
            out["stats"][op - 74] = int.from_bytes(d[p:p + 2], "big")
            p += 2
        elif op in (93, 99, 107, 109, 111, 122, 123):
            pass
        elif op == 95:
            out["level"] = int.from_bytes(d[p:p + 2], "big")
            p += 2
        elif op in (97, 98, 103, 112, 114, 124):
            p += 2
        elif op in (100, 101, 119):
            p += 1
        elif op == 102:
            flags = d[p]
            p += 1
            for i in range(8):
                if flags & (1 << i):
                    p += 4 if d[p] & 0x80 else 2
                    p += 2 if d[p] & 0x80 else 1
        elif op in (106, 118):
            p += 4
            if op == 118:
                p += 2
            n = d[p]
            p += 1 + 2 * (n + 1)
        elif op == 113:
            p += 4
        elif op in (115, 121):
            p += 8 if op == 115 else 1 + 4 * d[p]
        else:
            break  # the params (249) and anything new come after the stats
    return out


NPCS = None
ENRICH = None


def npc_types():
    global NPCS
    if NPCS is None:
        files = spawn_js5.split_files(spawn_js5.read_group(2, 9), spawn_js5.ref_table(2)[9])
        NPCS = {fid: parse_npc(b) for fid, b in files.items()}
    return NPCS


def enrich():
    """npcs.toml configs by internal name (weaknesses, immunities, Slayer XP)."""
    global ENRICH
    if ENRICH is None:
        ENRICH = {c["npc"]: c for c in tomllib.load(open(NPCS_TOML, "rb")).get("config", []) if "npc" in c}
    return ENRICH


STAT_NAMES = ["attack", "defence", "strength", "hitpoints", "ranged", "magic"]


def find_npc(spec):
    types = npc_types()
    if spec.startswith("name:"):
        want = spec[5:].lower()
        ids = [i for i, t in sorted(types.items()) if (t["name"] or "").lower() == want and t["level"] > 0]
        best = [i for i in ids if types[i]["stats"][3] > 1] or ids
        if not best:
            raise KeyError(f"no npc named {spec[5:]}")
        return best[0]
    if spec not in GS.NPC_ID:
        raise KeyError(f"no npc called {spec}")
    return GS.NPC_ID[spec]


def stats_row(spec, hp_override):
    nid = find_npc(spec)
    t = npc_types()[nid]
    sym = GS.NPC_SYM.get(nid, "")
    e = enrich().get(sym, {})
    st = dict(zip(STAT_NAMES, t["stats"]))
    row = {"name": t["name"], "level": t["level"], "size": t["size"],
           "hitpoints": hp_override.get(sym) or hp_override.get(spec) or st["hitpoints"],
           "attack": st["attack"], "strength": st["strength"], "defence": st["defence"],
           "ranged": st["ranged"], "magic": st["magic"]}
    if e.get("elemental_weakness_type"):
        row["weakness"] = f"{e['elemental_weakness_type']} spells, {e.get('elemental_weakness_percent', 0)}%"
    if e.get("attack_type"):
        row["attackType"] = e["attack_type"]
    immune = []
    if e.get("poison_immunity"):
        immune.append("poison")
    if e.get("venom_immunity") == "Immune":
        immune.append("venom")
    elif e.get("venom_immunity"):
        immune.append("venom (turns into poison)")
    if e.get("cannon_immunity"):
        immune.append("cannons")
    if e.get("burn_immunity"):
        immune.append("burns")
    if (e.get("freeze_resistance") or 0) >= 100:
        immune.append("freezing")
    if immune:
        row["immune"] = immune
    if e.get("slayer_xp"):
        row["slayerXp"] = e["slayer_xp"]
    return row


# ---------------------------------------------------------------- drop tables

def load_drops():
    files = [tomllib.load(open(os.path.join(DROPS, f), "rb")) for f in ("osrs.toml", "boosted.toml", "revenants.toml", "isle.toml")]
    by_source = collections.defaultdict(list)
    boosts = set()
    for f in files:
        for b in f.get("boost", []):
            boosts.add((b["source"].lower(), b["item"].lower()))
        for m in f.get("monster", []):
            src = m.get("source") or m.get("name")
            if src:
                by_source[src.lower()].append(m)
    shared = {s["name"].lower(): s for f in files for s in f.get("shared", [])}
    return by_source, boosts, shared


def chance(text):
    text = str(text).strip()
    if "/" in text:
        a, b = text.split("/")
        return float(a) / float(b)
    if text.endswith("%"):
        return float(text[:-1]) / 100
    return float(text)


def one_in(p):
    """'1/50' style text for a chance."""
    if p >= 1:
        return "Always"
    if p <= 0:
        return "Never"
    n = 1 / p
    return "1/" + (f"{n:,.1f}".rstrip("0").rstrip(".") if n < 20 else f"{round(n):,}")


def table(label, source, npcs, data, multipliers, uniques):
    by_source, boosts, _ = data
    blocks = [m for m in by_source.get(source.lower(), []) if not m.get("variant")]
    if npcs is None:
        blocks = [m for m in blocks if not m.get("npcs")]
    else:
        blocks = [m for m in blocks if sorted(m.get("npcs", [])) == sorted(npcs)]
    if not blocks:
        raise KeyError(f"no drop table for {source} {npcs or ''}")
    # A later block adds lines (isle.toml), unless it says replace.
    merged = {"always": [], "main": [], "tertiary": [], "rolls": 1}
    for m in blocks:
        if m.get("replace"):
            merged = {"always": [], "main": [], "tertiary": [], "rolls": 1}
        merged["rolls"] = m.get("main_rolls", m.get("mainRolls", merged["rolls"]))
        for k in ("always", "main", "tertiary"):
            merged[k] += m.get(k, [])

    def row(e, kind):
        name = e.get("item") or e.get("table") or GS.obj_name(e.get("obj"))
        r = {"item": name, "qty": e.get("qty", "1"), "noted": bool(e.get("noted")), "table": bool(e.get("table"))}
        r["boosted"] = bool(e.get("boosted")) or (source.lower(), name.lower()) in boosts
        r["unique"] = name.lower() in uniques or r["boosted"]
        if kind == "always":
            r["base"] = 1.0
        else:
            r["base"] = chance(e["chance"])
            r["text"] = e["chance"] if "/" in str(e["chance"]) else one_in(r["base"])
        return r

    out = {"label": label, "rolls": merged["rolls"],
           "always": [row(e, "always") for e in merged["always"]],
           "main": [row(e, "main") for e in merged["main"]],
           "tertiary": [row(e, "tertiary") for e in merged["tertiary"]]}
    # What the relic boost does: a boosted main line is that many times as likely, and the main table
    # is scaled back to 100% if the boost pushes it over (DropRoller).
    for tier, mult in multipliers.items():
        total = sum(r["base"] * (mult if r["boosted"] else 1) for r in out["main"])
        div = max(1.0, total)
        for r in out["main"]:
            eff = r["base"] * (mult if r["boosted"] else 1) / div
            if r["boosted"]:
                r["t" + tier] = one_in(eff)
                r.setdefault("_eff", {})[tier] = eff
        for r in out["tertiary"]:
            if r["boosted"]:
                eff = min(1.0, r["base"] * mult)
                r["t" + tier] = one_in(eff)
                r.setdefault("_eff", {})[tier] = eff
    return out


def per_kill(tables, multipliers):
    """The notable drops of a fight with their chance per kill: [{item, base, t1, t4, boosted}].
    Main-table lines count once per main roll; the same item from several lines adds up."""
    acc = collections.OrderedDict()
    for t in tables:
        for kind in ("main", "tertiary"):
            rolls = t["rolls"] if kind == "main" else 1
            for r in t[kind]:
                if not r["unique"] or r["table"]:
                    continue
                a = acc.setdefault(r["item"], {"item": r["item"], "boosted": r["boosted"], "miss": {"base": 1.0},
                                               "from": []})
                if t["label"] not in a["from"]:
                    a["from"].append(t["label"])
                a["miss"]["base"] *= (1 - r["base"]) ** rolls
                for tier in multipliers:
                    eff = r.get("_eff", {}).get(tier, r["base"])
                    a["miss"][tier] = a["miss"].get(tier, 1.0) * (1 - eff) ** rolls
    out = []
    for a in acc.values():
        row = {"item": a["item"], "boosted": a["boosted"], "base": one_in(1 - a["miss"]["base"]), "from": a["from"]}
        if a["boosted"]:
            for tier in multipliers:
                row["t" + tier] = one_in(1 - a["miss"][tier])
        out.append(row)
    return out


def strip(t):
    for kind in ("main", "tertiary"):
        for r in t[kind]:
            r.pop("_eff", None)
            r.pop("base", None)
    for r in t["always"]:
        r.pop("base", None)
    return t


# ---------------------------------------------------------------- collection log

def collection_log():
    """{category name: [item names]} for the Bosses, Raids and Minigames tabs."""
    structs = js5.read_config_group(34)
    enums = js5.read_config_group(8)
    out = {}
    for tab in (471, 472, 474):
        tab_params = js5.dec_struct(structs[tab])
        for _, cs in sorted(js5.dec_enum(enums[tab_params[683]])["entries"].items()):
            c = js5.dec_struct(structs[cs])
            objs = [v for _, v in sorted(js5.dec_enum(enums[c[690]])["entries"].items())]
            out[c[689]] = [GS.obj_name(GS.OBJ_SYM[o]) if o in GS.OBJ_SYM else str(o) for o in objs]
    return out


# ---------------------------------------------------------------- waves

def waves(kind, settings):
    mode = settings.get({"fightcaves": "fightCaveMode", "inferno": "infernoMode"}[kind], "full")
    fn = {"fightcaves": "fight_caves_waves_{}.toml", "inferno": "inferno_waves_{}.toml"}[kind].format(mode)
    data = tomllib.load(open(os.path.join(ISLE_RES, kind, fn), "rb"))
    rows = [w.get("monsters", []) for w in data.get("wave", [])]
    counts = collections.Counter(m for w in rows for m in w)
    return {"mode": mode, "count": len(rows), "monsters": dict(counts),
            "list": rows if len(rows) <= 20 else None}


# ---------------------------------------------------------------- entrances

# Entrances the walk model (reach.py) gets wrong, by entrance key: (area key, title, why).
REACH_OVERRIDES = {
    # The Soul Wars arena is reached through the blue and red portals of the lobby's waiting areas,
    # which the flood fill does not follow.
    "toa": ("arena", "Soul Wars arena", "through the waiting-area portals"),
    "gauntlet": ("arena", "Soul Wars arena", "through the waiting-area portals"),
    # tools/dungeons/collision.py makes corner wall pieces (loc shapes 1 and 3) block two whole sides,
    # where the game blocks only the corner, which seals the sewer passage to the bars.
    "scurrius": ("varrock", "Varrock Sewers", "corner walls block too much in collision.py"),
}


def place(reach, key, label, x, z, lvl):
    """Where an entrance is: the area it is in (if a player can walk there) and a direction text."""
    area = None
    for dx, dz in [(0, 0), (0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1), (0, 2), (2, 0), (0, -2), (-2, 0)]:
        area = reach.where(lvl, x + dx, z + dz)
        if area:
            break
    if not area and key in REACH_OVERRIDES:
        area, title, _ = REACH_OVERRIDES[key]
        return {"label": label, "x": x, "z": z, "level": lvl, "area": area, "areaTitle": title, "dir": None}
    return {"label": label, "x": x, "z": z, "level": lvl, "area": area,
            "areaTitle": reach.titles.get(area) if area else None,
            "dir": GS.compass(x, z) if area == "isle" else None}


def kt_defaults(path, keys):
    """`val key: Type = value` defaults of a settings class, for keys event-settings.json leaves out."""
    src = kt(*path)
    out = {}
    for k in keys:
        m = re.search(r"val " + k + r": \w+ = ([\w.\"]+)", src)
        if m:
            v = m.group(1).strip('"')
            out[k] = v == "true" if v in ("true", "false") else float(v) if "." in v else int(v) if v.isdigit() else v
    return out


def event_settings(settings):
    """The event settings the boss pages talk about, with the code's defaults filled in."""
    toa = kt_defaults(("toa", "ToaSettings.kt"), ["pathsRequired", "uniqueMultiplier", "teamUniquesFromLevel"])
    toa.update(settings.get("toa", {}))
    return {
        "fightCaveMode": settings.get("fightCaveMode", "full"),
        "infernoMode": settings.get("infernoMode", "full"),
        "zulrahScaleMultiplier": settings.get("zulrahScaleMultiplier", 1.0),
        "gauntletNodesDeplete": settings.get("gauntletNodesDeplete", True),
        "pvp": settings.get("pvp", {}),
        "toa": toa,
    }


# Boss entrances page text can point at with {dir:<key>}, on top of gen_skills' landmarks.
LANDMARKS = {"zulrah_boat": "zulrah", "vorkath_boat": "vorkath", "dk_ladder": "dk", "colosseum_arch": "colosseum"}


def landmarks(reach, spots):
    out = GS.entrances()
    for key, spot in LANDMARKS.items():
        _, x, z, _ = spots[spot]
        out[key] = {"x": x, "z": z, "dir": GS.compass(x, z)}
    return out


def main():
    settings = json.load(open(os.path.join(ROOT, ".data", "league", "event-settings.json"), encoding="utf-8"))
    nums = gen_relics.numbers(settings)
    multipliers = {k: v for k, v in nums["drops"].items()}
    reach = R.Reach()
    all_tasks = {t["id"]: t for t in GS.tasks(settings)}
    data = load_drops()
    clog = collection_log()
    spots = isle_entrances()

    fights = {}
    for fid, f in FIGHTS.items():
        uniques = set()
        for page in PAGES.values():
            if fid in page["fights"]:
                for c in page["clog"]:
                    uniques |= {n.lower() for n in clog.get(c, [])}
        tables = [table(label, src, npcs, data, multipliers, uniques) for label, src, npcs in f["tables"]]
        stats = [stats_row(s, f.get("hp", {})) for s in f["npcs"]]
        for row, label in zip(stats, f.get("labels", [])):
            row["label"] = label
        fights[fid] = {"stats": stats,
                       "uniques": per_kill(tables, multipliers),
                       "tables": [strip(t) for t in tables]}

    pages = {}
    for pid, p in PAGES.items():
        cats = [{"name": c, "items": clog[c]} for c in p["clog"]]
        dropped = {r["item"].lower() for fid in p["fights"] for t in fights[fid]["tables"] for k in ("always", "main", "tertiary")
                   for r in t[k]}
        for c in cats:
            c["fromDrops"] = [n.lower() in dropped for n in c["items"]]
        tasks = []
        for tid in p["tasks"]:
            t = all_tasks.get(tid)
            if t is None:
                print(f"warning: task {tid} ({pid}) is not in the league task list")
                continue
            tasks.append({k: t[k] for k in ("id", "tier", "points", "name", "desc", "live")})
        page = {"clog": cats, "tasks": tasks, "entrances": [place(reach, k, *spots[k]) for k in p["entrances"]]}
        if p.get("waves"):
            page["waves"] = waves(p["waves"], settings)
        echo = ECHOES.get(pid)
        if echo:
            page["echo"] = strip(table("Echo", echo, None, data, multipliers, set()))
            page["echo"]["inherits"] = [m.get("inherit") for m in data[0].get(echo.lower(), []) if m.get("inherit")][:1]
        pages[pid] = page

    out = {
        "generated": datetime.date.today().isoformat(),
        "dropMultiplier": multipliers,
        "respawnSpeed": settings.get("respawnSpeed", 1.0),
        "settings": event_settings(settings),
        "fights": fights,
        "pages": pages,
        "entrances": landmarks(reach, spots),
    }
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("// Generated by tools/wiki/gen_bosses.py. Do not edit; change the game code and re-run.\n")
        fh.write("window.ISLE_BOSSES_GEN = ")
        json.dump(out, fh, indent=1, ensure_ascii=False)
        fh.write(";\n")
    for pid, p in pages.items():
        fs = ", ".join(f"{fid} ({len(fights[fid]['stats'])} npcs, {len(fights[fid]['tables'])} tables)"
                       for fid in PAGES[pid]["fights"])
        ents = ", ".join(f"{e['label']} -> {e['area']}" for e in p["entrances"]) or "-"
        print(f"{pid}: {fs}; {len(p['tasks'])} tasks; entrances: {ents}")
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
