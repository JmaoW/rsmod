#!/usr/bin/env python3
"""Builds the Slayer data set for the isle -> tools/slayer/slayer_data.json + report.txt.

    python tools/slayer/dbdump.py        # once: cache_tables.json
    python tools/slayer/npcdump.py       # once: cache_npcs.json
    python tools/slayer/parse_masters.py # once: masters.json (wiki, used as a cross-check + points)
    python tools/slayer/build_data.py

The cache (rev 233) is the source of truth for tasks, weights, amounts, requirements and the rewards
store; the wiki masters.json supplies the points table and is compared against the cache. Tasks are
kept only if at least one npc of that task is spawned in the world (tools/drops/spawned_npcs.json).
"""

import collections
import json
import re
from pathlib import Path

import enums

TOOL_DIR = Path(__file__).resolve().parent
DROPS = TOOL_DIR.parent / "drops"

MASTER_IDS = {"turael": 1, "vannaka": 3, "duradel": 5}
STAT_SLAYER, STAT_MAGIC = 18, 6  # stat ids used in min_stat_requirement_all

# Names of the superior monsters (wiki "Superior slayer monster"); matched by npc name in the cache.
SUPERIORS = {
    "Crushing hand": "Crawling hands", "Chasm crawler": "Cave crawlers",
    "Screaming banshee": "Banshees", "Screaming twisted banshee": "Banshees",
    "Giant rockslug": "Rockslugs", "Cockathrice": "Cockatrice", "Flaming pyrelord": "Pyrefiends",
    "Monstrous basilisk": "Basilisks", "Malevolent mage": "Infernal mages",
    "Insatiable Bloodveld": "Bloodveld", "Insatiable mutated Bloodveld": "Bloodveld",
    "Vitreous jelly": "Jellies", "Vitreous warped Jelly": "Jellies",
    "Cave abomination": "Cave horrors", "Abhorrent spectre": "Aberrant spectres",
    "Repugnant spectre": "Aberrant spectres", "Choke devil": "Dust devils",
    "King kurask": "Kurask", "Nuclear smoke devil": "Smoke devils",
    "Marble gargoyle": "Gargoyles", "Night beast": "Dark beasts",
    "Greater abyssal demon": "Abyssal demons", "Nechryael": None,
    "Basilisk Sentinel": "Basilisks", "Deviant spectre": None,
}
SUPERIOR_NAMES = {k.lower() for k, v in SUPERIORS.items() if v}
SUPERIOR_TASK = {k.lower(): v for k, v in SUPERIORS.items()}

# Umbrella tasks whose members have their own task ids (cache param 50 on the npc).
# Cave krakens are spawned as whirlpools (DungeonKrakenScript places 25), so they have no spawn of
# their own in the world spawn list.
SPAWN_OVERRIDES = {92: 25}
GROUPS = {127: [58, 59, 60, 93, 108, 109]}  # metal dragons: bronze, iron, steel, mithril, adamant, rune


def vals(row, col):
    c = row["cols"].get(col)
    return c["values"] if c else None


def first(row, col, default=None):
    v = vals(row, col)
    return v[0][0] if v else default


def norm(name):
    name = re.sub(r"[^a-z ]", "", name.lower())
    return re.sub(r"s$", "", name.strip())


SYMBOLS = TOOL_DIR.parents[1] / ".data/symbols"
RESOURCES = (TOOL_DIR.parents[1] /
             "content/skills/slayer/src/main/resources/org/rsmod/content/skills/slayer")


def tsv(path, header, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    tab, nl = chr(9), chr(10)
    lines = [tab.join(header)] + [tab.join("" if c is None else str(c) for c in r) for r in rows]
    with open(path, "w", encoding="utf-8", newline=nl) as out:
        out.write(nl.join(lines) + nl)


def export_runtime(masters, task_rows, tables, by_task, superiors, unlock_rows):
    """Writes the tab-separated files the slayer module loads at startup."""
    unlock_bit = {row: first(r, "bit") for row, r in unlock_rows.items()}
    task_ids = {}
    master_rows = []
    for master, m in masters.items():
        for t in m["tasks"]:
            bit = unlock_bit.get(t["unlockRow"]) if t["unlockRow"] is not None else None
            master_rows.append((master, t["taskId"], t["weight"], t["min"], t["max"], bit))
            task_ids[t["taskId"]] = t
    task_out = []
    for tid, t in sorted(task_ids.items()):
        row = task_rows[next(r for r in task_rows if first(task_rows[r], "id") == tid)]
        members = [tid] + GROUPS.get(tid, [])
        ext = t["extension"]
        stats = ",".join(f"{lvl}:{stat}" for lvl, stat in t["stats"])
        any_stats = ",".join(f"{lvl}:{stat}" for lvl, stat in t["statsAny"])
        task_out.append((tid, t["task"], ",".join(map(str, members)), t["combat"], stats, any_stats,
                         t["leaguesMinCombat"], t["leaguesMaxCombat"],
                         unlock_bit.get(ext[0]) if ext else None, ext[1] if ext else None,
                         ext[2] if ext else None, t["spawns"]))
    tsv(RESOURCES / "tasks.tsv",
        ["id", "name", "members", "combat", "stats", "statsAny", "leaguesMin", "leaguesMax",
         "extendBit", "extendMin", "extendMax", "spawns"], task_out)
    tsv(RESOURCES / "master_tasks.tsv",
        ["master", "task", "weight", "min", "max", "unlockBit"], master_rows)
    meta = []
    for master, m in masters.items():
        p = m["points"]
        meta.append((master, m["combat"], m["slayer"], m["blockCost"],
                     p.get("1", 0), p.get("10", 0), p.get("50", 0), p.get("100", 0),
                     p.get("250", 0), p.get("1000", 0)))
    tsv(RESOURCES / "masters.tsv",
        ["master", "combat", "slayer", "blockCost", "p1", "p10", "p50", "p100", "p250", "p1000"], meta)
    # Slayer XP per task npc, a fallback for npcs the enricher config has no slayer_xp for.
    xp = sorted({(m["id"], m["slayerXp"]) for t in task_ids.values()
                 for tid2 in [t["taskId"]] + GROUPS.get(t["taskId"], [])
                 for m in by_task.get(tid2, []) if m["slayerXp"]})
    tsv(RESOURCES / "npc_xp.tsv", ["npc", "xp"], xp)
    tsv(RESOURCES / "superiors.tsv", ["npc", "task", "name"],
        [(s["npc"], s["taskId"], s["name"]) for s in superiors])

    # The rewards store: every unlock with the kind of thing it is, and the items it sells.
    ext_bits = {unlock_bit.get(t["extension"][0]) for t in task_ids.values() if t["extension"]}
    task_bits = {unlock_bit.get(t["unlockRow"]) for t in task_ids.values()
                 if t["unlockRow"] is not None}
    perk_bits_that_work = {0, 1, 2, 5, 35}
    objs = {}
    for line in open(SYMBOLS / "obj.sym", encoding="utf-8"):
        parts = line.strip().split(chr(9))
        if len(parts) == 2 and parts[0].isdigit():
            objs[int(parts[0])] = parts[1]
    unlock_out = []
    for r in sorted(unlock_rows.values(), key=lambda r: first(r, "bit")):
        bit = first(r, "bit")
        if bit in ext_bits:
            kind, works = "extend", 1
        elif bit in task_bits:
            kind, works = "task", 1
        else:
            kind, works = "perk", 1 if bit in perk_bits_that_work else 0
        pos = vals(r, "list_position")[0] if vals(r, "list_position") else [-1, -1]
        unlock_out.append((bit, first(r, "name"), first(r, "cost"), 1 if first(r, "refundable") == 1 else 0,
                           kind, works, pos[0], pos[1],
                           first(r, "description").replace(chr(9), " ")))
    tsv(RESOURCES / "unlocks.tsv",
        ["bit", "name", "cost", "refundable", "kind", "works", "tab", "index", "description"], unlock_out)
    item_out = []
    ids, qty, cost = enums.read_enum(840), enums.read_enum(841), enums.read_enum(842)
    for k in sorted(ids):
        item_out.append((objs[ids[k]], qty.get(ids[k], 1), cost.get(ids[k])))
    tsv(RESOURCES / "reward_items.tsv", ["obj", "quantity", "cost"], item_out)


def main():
    tables = json.loads((TOOL_DIR / "cache_tables.json").read_text(encoding="utf-8"))
    npcs = json.loads((TOOL_DIR / "cache_npcs.json").read_text(encoding="utf-8"))
    spawned = json.loads((DROPS / "spawned_npcs.json").read_text(encoding="utf-8"))
    wiki = json.loads((TOOL_DIR / "masters.json").read_text(encoding="utf-8"))
    rb = json.loads((DROPS / ".cache/rb_monsters-complete.json").read_text(encoding="utf-8"))

    task_rows = {r["row"]: r for r in tables["slayer_task"]}
    unlock_rows = {r["row"]: r for r in tables["slayer_unlock"]}
    report = []

    # npcs per task id (cache param 50), with spawn counts
    by_task = collections.defaultdict(list)
    for npc_id, npc in npcs.items():
        tid = npc["params"].get("50")
        if tid is None:
            continue
        by_task[tid].append({
            "id": int(npc_id), "name": npc["name"], "level": npc["level"],
            "spawns": spawned.get(npc_id, {}).get("spawns", 0),
            "slayerXp": rb.get(npc_id, {}).get("slayer_xp"),
            "slayerReq": rb.get(npc_id, {}).get("slayer_level"),
        })

    masters = {}
    for master, mid in MASTER_IDS.items():
        rows = [r for r in tables["slayer_master_task"]
                if first(r, "master_id") == mid and (r["name"] or "").startswith(master + "_")]
        tasks, dropped = [], []
        for r in rows:
            task = task_rows[first(r, "task")]
            tid = first(task, "id")
            members = [m for t in [tid] + GROUPS.get(tid, []) for m in by_task.get(t, [])]
            spawned_members = [m for m in members if m["spawns"] > 0]
            if not spawned_members and tid in SPAWN_OVERRIDES:
                spawned_members = [dict(m, spawns=SPAWN_OVERRIDES[tid] // max(1, len(members))) for m in members]
            ext = vals(task, "extension_min_max")
            entry = {
                "row": r["row"], "task": first(task, "name_uppercase"), "taskId": tid,
                "weight": first(r, "weight"), "min": first(r, "min_amount"),
                "max": first(r, "max_amount"),
                "combat": first(task, "min_comlevel", 0),
                "stats": vals(task, "min_stat_requirement_all") or [],
                "statsAny": vals(task, "min_stat_requirement_any") or [],
                "leaguesMinCombat": first(task, "leagues_min_comlevel"),
                "leaguesMaxCombat": first(task, "leagues_max_comlevel"),
                "unlockRow": first(r, "task_unlock"),
                "extension": ext[0] if ext else None,
                "npcs": [m["id"] for m in spawned_members],
                "spawns": sum(m["spawns"] for m in spawned_members),
            }
            (tasks if spawned_members else dropped).append(entry)
        masters[master] = {"masterId": mid, "tasks": tasks, "dropped": dropped,
                           "points": wiki[master.capitalize()]["points"], "blockCost": wiki[master.capitalize()]["blockCost"],
                           "combat": wiki[master.capitalize()]["combat"], "slayer": wiki[master.capitalize()]["slayer"]}

        # Cross-check against the wiki table.
        wiki_tasks = {norm(t["name"]): t for t in wiki[master.capitalize()]["tasks"]}
        if not wiki_tasks:
            report.append(f"== {master}: cache {len(rows)} tasks ({len(tasks)} kept, {len(dropped)} dropped); no wiki table")
            for d in dropped:
                report.append(f"   dropped (no spawned npc): {d['task']} (task id {d['taskId']})")
            continue
        cache_tasks = {norm(t["task"]): t for t in tasks + dropped}
        report.append(f"== {master}: cache {len(rows)} tasks ({len(tasks)} kept, {len(dropped)} dropped "
                      f"for no spawns); wiki {len(wiki_tasks)}")
        only_wiki = sorted(set(wiki_tasks) - set(cache_tasks))
        only_cache = sorted(set(cache_tasks) - set(wiki_tasks))
        if only_wiki:
            report.append(f"   wiki only (cache rev 233 lacks them): {', '.join(only_wiki)}")
        if only_cache:
            report.append(f"   cache only: {', '.join(only_cache)}")
        diffs = []
        for key in sorted(set(wiki_tasks) & set(cache_tasks)):
            w, c = wiki_tasks[key], cache_tasks[key]
            problems = []
            if w["weight"] != c["weight"]:
                problems.append(f"weight wiki {w['weight']} cache {c['weight']}")
            if w["amount"] and [c["min"], c["max"]] != w["amount"]:
                problems.append(f"amount wiki {w['amount']} cache {[c['min'], c['max']]}")
            if w["combat"] != c["combat"]:
                problems.append(f"combat wiki {w['combat']} cache {c['combat']}")
            cache_slayer = max([s[0] for s in c["stats"] if s[1] == STAT_SLAYER], default=0)
            if w["slayer"] != cache_slayer:
                problems.append(f"slayer wiki {w['slayer']} cache {cache_slayer}")
            if problems:
                diffs.append(f"   {key}: {'; '.join(problems)}")
        report.append(f"   {len(diffs)} differences wiki vs cache (cache wins):")
        report.extend(diffs)
        for d in dropped:
            report.append(f"   dropped (no spawned npc): {d['task']} (task id {d['taskId']}, "
                          f"{len(by_task.get(d['taskId'], []))} npc types)")

    # Superiors: npcs of a kept task id whose name is a known superior.
    superiors = []
    for tid, members in by_task.items():
        for m in members:
            if m["name"].lower() in SUPERIOR_NAMES:
                superiors.append({"npc": m["id"], "name": m["name"], "level": m["level"],
                                  "taskId": tid, "task": SUPERIOR_TASK[m["name"].lower()]})
    superiors.sort(key=lambda s: (s["task"], s["npc"]))

    # Rewards store.
    names = enums.read_enum(834)
    costs = enums.read_enum(836)
    store = []
    for r in tables["slayer_unlock"]:
        bit = first(r, "bit")
        store.append({
            "row": r["row"], "name": first(r, "name"), "bit": bit, "cost": first(r, "cost"),
            "description": first(r, "description"), "refundable": first(r, "refundable") == 1,
            "icon": first(r, "icon"), "position": vals(r, "list_position")[0] if vals(r, "list_position") else None,
            "rowName": r["name"],
        })
        if names and names.get(bit) != first(r, "name"):
            report.append(f"store: bit {bit} name table says {names.get(bit)!r}, row says {first(r, 'name')!r}")
        if costs and costs.get(bit) != first(r, "cost"):
            report.append(f"store: bit {bit} cost enum {costs.get(bit)}, row {first(r, 'cost')}")
    store.sort(key=lambda s: s["bit"])
    items = []
    ids, qty, cost = enums.read_enum(840), enums.read_enum(841), enums.read_enum(842)
    for k in sorted(ids):
        items.append({"obj": ids[k], "quantity": qty.get(ids[k], 1), "cost": cost.get(ids[k])})

    data = {"masters": masters, "superiors": superiors, "unlocks": store, "itemRewards": items,
            "extendEnum": enums.read_enum(273)}
    (TOOL_DIR / "slayer_data.json").write_text(json.dumps(data, indent=1), encoding="utf-8")


    export_runtime(masters, task_rows, tables, by_task, superiors, unlock_rows)
    for master, m in masters.items():
        report.append("")
        report.append(f"## {master} (cache master id {m['masterId']}), combat {m['combat']} slayer "
                      f"{m['slayer']}, block {m['blockCost']}, points {m['points']}")
        for t in sorted(m["tasks"], key=lambda t: -t["weight"]):
            report.append(f"   {t['task']:26} w{t['weight']:>2} {t['min']:>3}-{t['max']:<3} "
                          f"{len(t['npcs']):>3} npc types, {t['spawns']:>4} spawns")
    report.append("")
    report.append(f"{len(superiors)} superior-named npc types in the cache with a task id:")
    for s in superiors:
        report.append(f"   npc {s['npc']:>5} {s['name']} (level {s['level']}) -> {s['task']}")
    (TOOL_DIR / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report[:80]))


if __name__ == "__main__":
    main()
