#!/usr/bin/env python3
"""Which spawned attackable npcs have no drop table yet.

    python tools/drops/coverage.py

Reads spawned_npcs.json (from spawned_monsters.py) and the npc ids of every table in osrs.toml and
isle.toml, and writes tools/drops/coverage.txt: the uncovered npc types grouped by name, biggest
spawn counts first, with the wiki page the osrsreboxed db knows for them and whether the db has a
drop table for that id (a cross-check: if it does, the wiki import missed it).
"""

import collections
import json
import re
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
ROOT = TOOL_DIR.parents[1]
DROPS = ROOT / "content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops"
CACHE = TOOL_DIR / ".cache" / "rb_npc-drops.json"


def table_ids():
    ids = set()
    for name in ("osrs.toml", "isle.toml"):
        path = DROPS / name
        if not path.exists():
            continue
        for match in re.finditer(r"^ids = \[([^\]]*)\]", path.read_text(encoding="utf-8"), re.M):
            ids.update(int(x) for x in re.findall(r"\d+", match.group(1)))
    return ids


def main():
    spawned = json.loads((TOOL_DIR / "spawned_npcs.json").read_text(encoding="utf-8"))
    db = json.loads(CACHE.read_text(encoding="utf-8"))["npcs"] if CACHE.exists() else {}
    covered = table_ids()

    rows = collections.defaultdict(lambda: {"spawns": 0, "ids": [], "db": 0, "pages": set()})
    total_spawns = sum(v["spawns"] for v in spawned.values())
    covered_spawns = 0
    for key, v in spawned.items():
        npc_id = int(key)
        if npc_id in covered:
            covered_spawns += v["spawns"]
            continue
        row = rows[v["name"].lower()]
        row["spawns"] += v["spawns"]
        row["ids"].append(npc_id)
        row["pages"].add(v["page"] or "-")
        if str(npc_id) in db and db[str(npc_id)].get("tables"):
            row["db"] += 1

    lines = [f"{len(spawned) - sum(len(r['ids']) for r in rows.values())} of {len(spawned)} "
             f"spawned attackable npc types have a drop table "
             f"({covered_spawns} of {total_spawns} spawns).",
             f"{len(rows)} names have none. Columns: spawns, ids, ids the db has a table for, "
             f"name, wiki page(s).", ""]
    for name, row in sorted(rows.items(), key=lambda kv: -kv[1]["spawns"]):
        lines.append(f"{row['spawns']:5d}  {len(row['ids']):3d}  {row['db']:3d}  {name}  "
                     f"[{'; '.join(sorted(row['pages']))}]")
    (TOOL_DIR / "coverage.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines[:2]))


if __name__ == "__main__":
    main()
