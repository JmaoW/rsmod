#!/usr/bin/env python3
"""Builds the generated half of the wiki's Relics page -> wiki/data/relics-generated.js

    python tools/wiki/gen_relics.py

What it reads, so a change to any of these needs a re-run:
- RelicData.kt for the relic names, tiers and cache structs;
- the vanilla cache for each relic's icon (struct param 884, a sprite), saved as
  wiki/assets/relics/<name>.png;
- .data/league/event-settings.json for the point thresholds, XP and drop multipliers (a missing
  key falls back to the default in EventSettings.kt, as the server does);
- the league task list (through gen_skills.tasks) for how many points the tasks are worth.

The hand-written half (what each relic does) is wiki/data/relics.js.
"""
import datetime
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "tools", "cs2"))
import gen_skills  # noqa: E402
import sprites  # noqa: E402

js5 = sprites.js5  # the cs2 cache reader (tools/spawns has another module called js5)

OUT = os.path.join(ROOT, "wiki", "data", "relics-generated.js")
ICON_DIR = os.path.join(ROOT, "wiki", "assets", "relics")
ISLE = os.path.join(ROOT, "content", "areas", "misc", "isle-of-souls", "src", "main", "kotlin",
                    "org", "rsmod", "content", "areas", "misc", "isleofsouls")
RELIC_DATA = os.path.join(ISLE, "relics", "RelicData.kt")
SETTINGS_KT = os.path.join(ISLE, "event", "EventSettings.kt")
SETTINGS_JSON = os.path.join(ROOT, ".data", "league", "event-settings.json")

# Struct param holding the relic's large (120 x 120) icon on the relics screen.
ICON_PARAM = 884


def read(path):
    return open(path, encoding="utf-8").read()


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower().replace("'", "")).strip("-")


def kt_lists(src, field):
    """The `listOf(listOf(...), ...)` value of `val <field>` in RelicData.kt, as nested lists."""
    body = re.search(r"val " + field + r"[^=]*=\s*listOf\((.*?)\n\s*\)\n", src, re.S).group(1)
    return [re.findall(r'"([^"]+)"|(\d+)', inner) for inner in re.findall(r"listOf\(([^()]*)\)", body)]


def defaults():
    """EventSettings.kt's defaults, for keys the settings file leaves out."""
    src = read(SETTINGS_KT)

    def tier_map(name):
        body = re.search(name + r" = mapOf\(([^)]*)\)", src).group(1)
        return {k: float(v) for k, v in re.findall(r"(\d+) to ([\d.]+)", body)}

    return {
        "xpBase": float(re.search(r"DEFAULT_XP_BASE = ([\d.]+)", src).group(1)),
        "xpTiers": tier_map("DEFAULT_XP_TIERS"),
        "combatBonus": float(re.search(r"DEFAULT_COMBAT_BONUS = ([\d.]+)", src).group(1)),
        "combatBonusTier": int(re.search(r"DEFAULT_COMBAT_BONUS_TIER = (\d+)", src).group(1)),
        "dropTiers": tier_map("DEFAULT_DROP_TIERS"),
        "thresholds": [int(x) for x in re.search(r"DEFAULT_THRESHOLDS = listOf\(([^)]*)\)", src).group(1).split(",")],
    }


def numbers(settings):
    d = defaults()
    xp = settings.get("xpMultiplier", {})
    drops = settings.get("dropMultiplier", {})
    return {
        "thresholds": settings.get("relicPointThresholds", d["thresholds"]),
        "xp": {"base": xp.get("base", d["xpBase"]),
               "byRelicTier": {str(k): float(v) for k, v in xp.get("byRelicTier", d["xpTiers"]).items()},
               "combatBonus": xp.get("combatBonus", d["combatBonus"]),
               "combatBonusTier": xp.get("combatBonusTier", d["combatBonusTier"])},
        "drops": {str(k): float(v) for k, v in drops.get("byRelicTier", d["dropTiers"]).items()},
    }


def relics():
    src = read(RELIC_DATA)
    names = kt_lists(src, "names")
    struct_ids = kt_lists(src, "relicStructs")
    structs = js5.read_config_group(34)
    os.makedirs(ICON_DIR, exist_ok=True)
    out = []
    for t, tier in enumerate(names):
        for o, (name, _) in enumerate(tier):
            sid = int(struct_ids[t][o][1])
            params = js5.dec_struct(structs[sid])
            icon = None
            frames = sprites.frames(params[ICON_PARAM]) if ICON_PARAM in params else []
            if frames:
                w, h, rgba = frames[0]
                icon = f"assets/relics/{slug(name)}.png"
                sprites.write_png(os.path.join(ROOT, "wiki", icon), w, h, rgba)
            out.append({"tier": t + 1, "option": o + 1, "name": name, "struct": sid, "icon": icon})
    return out


def task_totals(settings):
    ts = gen_skills.tasks(settings)
    live = [t for t in ts if t["live"]]
    return {"count": len(ts), "points": sum(t["points"] or 0 for t in ts),
            "liveCount": len(live), "livePoints": sum(t["points"] or 0 for t in live)}


def main():
    settings = json.load(open(SETTINGS_JSON, encoding="utf-8")) if os.path.exists(SETTINGS_JSON) else {}
    data = {"generated": datetime.date.today().isoformat()}
    data.update(numbers(settings))
    data["tasks"] = task_totals(settings)
    data["relics"] = relics()
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("// Generated by tools/wiki/gen_relics.py. Do not edit; change the game code or settings and re-run.\n")
        f.write("window.ISLE_RELICS_GEN = ")
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write(";\n")
    print(f"{len(data['relics'])} relics, {sum(1 for r in data['relics'] if r['icon'])} icons, "
          f"thresholds {data['thresholds']}, tasks {data['tasks']}")
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
