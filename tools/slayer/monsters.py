#!/usr/bin/env python3
"""Joins osrsreboxed-db slayer data with the npcs the isle spawns -> tools/slayer/monsters.json.

    python tools/slayer/monsters.py

Inputs (all already on disk from the drop tooling):
    tools/drops/.cache/rb_monsters-complete.json   osrsreboxed-db: slayer level/xp/category/masters
    tools/drops/spawned_npcs.json                  every spawned attackable npc id with spawn count

Output: {npcId: {name, spawns, slayerLevel, slayerXp, categories, masters, combat, hitpoints}} for
every spawned npc the wiki marks as a Slayer monster. `categories` is osrsreboxed's list, which
`tasks.py` matches against the master task names.
"""

import json
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
DROPS = TOOL_DIR.parent / "drops"


def load():
    rb = json.loads((DROPS / ".cache/rb_monsters-complete.json").read_text(encoding="utf-8"))
    spawned = json.loads((DROPS / "spawned_npcs.json").read_text(encoding="utf-8"))
    return rb, spawned


def main():
    rb, spawned = load()
    out = {}
    for npc_id, info in spawned.items():
        mon = rb.get(npc_id)
        if mon is None or not mon.get("slayer_monster"):
            continue
        out[npc_id] = {
            "name": mon["name"],
            "spawns": info["spawns"],
            "slayerLevel": mon.get("slayer_level") or 0,
            "slayerXp": mon.get("slayer_xp") or 0.0,
            "categories": mon.get("category") or [],
            "masters": mon.get("slayer_masters") or [],
            "combat": mon.get("combat_level") or 0,
            "hitpoints": mon.get("hitpoints") or 0,
        }
    (TOOL_DIR / "monsters.json").write_text(json.dumps(out, indent=1), encoding="utf-8")
    print(f"{len(spawned)} spawned npcs, {len(out)} are Slayer monsters")
    cats = {}
    for npc_id, mon in out.items():
        for cat in mon["categories"]:
            cats.setdefault(cat, []).append(mon)
    print(f"{len(cats)} categories")
    for cat, mons in sorted(cats.items()):
        names = sorted({m["name"] for m in mons})
        print(f"  {cat}: {sum(m['spawns'] for m in mons)} spawns, {len(names)} names: {', '.join(names[:6])}")


if __name__ == "__main__":
    main()
