#!/usr/bin/env python3
"""Picks the league kill tasks that the spawned world can complete, for LeagueKillTasks.kt.

    python tools/drops/kill_tasks.py          # prints the table and writes kill_tasks.txt

A task is a kill task when its name starts with "Defeat" or "Kill" and names one kind of monster
(optionally with a count). Place words are stripped ("Defeat a Kurask in Tirannwn" becomes "Defeat a
Kurask"), because every monster is in the one open world; tasks that need a special way of killing
(a spell, a weapon, a time limit, kicking, a Slayer task) are left out. A task is kept only when
that monster is spawned, using spawned_npcs.json from spawned_monsters.py.
"""

import json
import re
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
ROOT = TOOL_DIR.parents[1]

PLACES = [
    "Tirannwn", "Fremennik Province", "Kandarin", "Asgarnia", "Morytania", "the Wilderness",
    "Wilderness", "Kourend", "Karamja", "Varlamore underground", "Varlamore", "Barbarian Village",
    "the Wizards' Tower", "Wizards' Tower", "the Smoke Devil Dungeon", "the Kharidian Desert",
    "Kharidian Desert", "the Slayer Tower", "the Crash Site Cavern", "Kraken Cove",
    "the Mount Karuulm Slayer Dungeon", "the Lithkren Vault", "the Ancient Cavern",
    "the Asgarnian Ice Dungeon", "the Taverley Dungeon", "the Kalphite Lair",
    "the God Wars Dungeon", "the Abyssal Nexus", "Edgeville Dungeon", "Varrock Sewers",
    "Darkmeyer", "Karamja", "the Jormungand's Prison", "the Fremennik Province",
]
# What makes a kill task special: not just "kill one of these".
SPECIAL = re.compile(
    r"spell|fists|kicking|holding|one hit|seconds|superior|slayer|without|combined|every|each|"
    r"unique|echo|\bor\b|,|&|all three|any |wintertodt|tempoross|barbarian assault|pest control|"
    r"treasure trail|riders|while|using|with a|with the|as part", re.I)
# Bosses with their own scripts and tasks (Zulrah, Fight Caves, Inferno).
HANDLED = {540, 553, 554, 1397}
# Bosses whose fights are not designed yet (content plan 3.4, boss order is still open): their
# kill-count ladders wait for the boss, but the single "defeat one" tasks of tier 1-2 stay.
BOSSES = {
    "abyssal sire", "zalcano", "k'ril tsutsaroth", "general graardor", "kree'arra",
    "commander zilyana", "kalphite queen", "venenatis", "scorpia", "callisto", "vet'ion",
    "chaos elemental", "chaos fanatic", "crazy archaeologist", "king black dragon",
    "corporeal beast", "vardorvis", "duke sucellus", "phantom muspah", "giant mole",
}

PATTERN = re.compile(r"^(Defeat|Kill)\s+(?:(an?|the|\d[\d,]*)\s+)?(.+?)(?:\s+(\d[\d,]*)\s+times?)?$", re.I)


def strip_place(name):
    for place in sorted(PLACES, key=len, reverse=True):
        name = re.sub(r"\s+(?:in|at|on|beneath|underneath|within)\s+" + re.escape(place) + r"$",
                      "", name, flags=re.I)
    return name


def main():
    tasks = json.loads((ROOT / "docs/raging_echoes_tasks.json").read_text(encoding="utf-8"))["tasks"]
    spawned = json.loads((TOOL_DIR / "spawned_npcs.json").read_text(encoding="utf-8"))
    names = {}
    for v in spawned.values():
        names[v["name"].lower()] = names.get(v["name"].lower(), 0) + v["spawns"]

    def match(text):
        text = text.lower().strip()
        for candidate in (text, text.rstrip("s"), text[:-3] + "y" if text.endswith("ies") else text,
                          text.replace("bunnies", "bunny"), text.replace("crabs", "crab")):
            if candidate in names:
                return candidate
        return None

    rows = []
    for t in tasks:
        name = t["name"]
        if t["taskId"] in HANDLED or SPECIAL.search(name):
            continue
        cleaned = strip_place(name)
        m = PATTERN.match(cleaned)
        if not m:
            continue
        verb, article, monster, times = m.groups()
        count = 1
        if article and article[0].isdigit():
            count = int(article.replace(",", ""))
        if times:
            count = int(times.replace(",", ""))
        monster = re.sub(r"^the\s+", "", monster, flags=re.I)
        hit = match(monster)
        if not hit:
            continue
        if hit in BOSSES and (t["difficulty"] > 2 or count > 1):
            continue
        rows.append((t["difficulty"], t["taskId"], name, cleaned, hit, count, t["structId"]))
    rows.sort()
    # Once the place is gone two tasks can read the same ("Defeat a Moss Giant" and "... in
    # Tirannwn"). Keep the easiest one, as was done for the duplicate mining and fishing tasks.
    seen, unique = set(), []
    for row in rows:
        if row[3].lower() not in seen:
            seen.add(row[3].lower())
            unique.append(row)
    dropped = [r for r in rows if r not in unique]
    rows = unique
    out = [f"{len(rows)} kill tasks (tier, id, original name, new name, monster, count)"]
    out += [f"dropped as a duplicate: {r[1]} {r[2]!r}" for r in dropped]
    out += [f"{r[0]}  {r[1]:5d}  {r[2]!r:60}  {r[3]!r:40} {r[4]!r:24} x{r[5]}" for r in rows]
    (TOOL_DIR / "kill_tasks.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"{len(rows)} kill tasks written to kill_tasks.txt")

    tiers = {1: "Easy", 2: "Medium", 3: "Hard", 4: "Elite", 5: "Master"}
    kotlin = {"list": [], "kills": [], "edits": [], "structs": [], "enum": []}
    for tier, task_id, original, cleaned, monster, count, struct in rows:
        kotlin["list"].append(f'LeagueTask({task_id}, LeagueTier.{tiers[tier]}, "{cleaned}"),')
        kotlin["enum"].append(str(struct))
        kotlin["kills"].append(
            f'KillTask({task_id}, "{monster}"' + (f", {count}" if count > 1 else "") + "),")
        if cleaned != original:
            kotlin["edits"].append(f'Edit({task_id}, "{cleaned}", "{cleaned}"),')
            kotlin["structs"].append(f"{struct}\tleague_task_{task_id}")
    (TOOL_DIR / "kill_tasks_kotlin.txt").write_text(
        "\n\n".join(f"// {k}\n" + "\n".join(v) for k, v in kotlin.items()) + "\n",
        encoding="utf-8")


if __name__ == "__main__":
    main()
