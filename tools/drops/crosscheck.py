#!/usr/bin/env python3
"""Cross-checks the imported drop tables against osrsreboxed-db.

    python tools/drops/crosscheck.py

For every spawned attackable npc id that has a table in osrs.toml and in the db, compares the sets
of item names and writes tools/drops/crosscheck.txt: the monsters where the two disagree, worst
first. The wiki import is the source of truth (the db is a second reading of the same wiki), so a
difference is a prompt to look, not an error. Common, expected differences are left out: the rare
and gem drop tables and the herb, seed and talisman tables (the db keeps them as nested tables
that the db file does not expand), clue scrolls and keys, and the word-rarity items that the
import approximates.
"""

import collections
import json
import re
import sys
import tomllib
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOL_DIR))
import wiki_import  # noqa: E402  (for the item name aliases)
ROOT = TOOL_DIR.parents[1]
DROPS = ROOT / "content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops"
DB = TOOL_DIR / ".cache" / "rb_npc-drops.json"

IGNORE = re.compile(
    r"clue scroll|brimstone key|^nothing$|^coins$|casket|key \(|looting bag|champion scroll|ensouled|"
    r"^grimy |seed$|spore$|talisman$", re.I)


def ours():
    data = tomllib.loads((DROPS / "osrs.toml").read_text(encoding="utf-8"))
    shared = {s["name"]: s for s in data.get("shared", [])}

    def names(entries, seen):
        out = set()
        for e in entries:
            if "item" in e:
                out.add(e["item"].lower())
            elif "obj" in e:
                out.add(e["obj"].lower())
            elif "table" in e and e["table"] not in seen and e["table"] in shared:
                # Nested tables are the rare/gem/extra rolls; their items are not compared.
                pass
        return out

    by_id = {}
    for m in data.get("monster", []):
        items = set()
        for key in ("always", "main", "tertiary"):
            items |= names(m.get(key, []), set())
        for npc_id in m.get("ids", []):
            by_id[npc_id] = (m.get("source", "?"), items)
    return by_id


def db_items(db, npc_id):
    entry = db["npcs"].get(str(npc_id))
    if not entry:
        return None
    items = set()
    for table_id in entry.get("tables", []):
        table = db["tables"].get(table_id, {})
        for part in ("entries", "tertiary"):
            for e in table.get(part, []):
                name = e.get("name")
                if name:
                    key = name.lower()
                    if key in wiki_import.OBJ_ALIASES:
                        items.add(wiki_import.OBJ_ALIASES[key].lower())
                    else:
                        items.add(wiki_import.ITEM_ALIASES.get(key, name).lower())
    return items


def main():
    db = json.loads(DB.read_text(encoding="utf-8"))
    spawned = json.loads((TOOL_DIR / "spawned_npcs.json").read_text(encoding="utf-8"))
    mine = ours()

    rows = []
    compared = 0
    only_ours_total = only_db_total = 0
    seen_sources = set()
    for key, spawn in spawned.items():
        npc_id = int(key)
        if npc_id not in mine:
            continue
        source, items = mine[npc_id]
        other = db_items(db, npc_id)
        if other is None:
            continue
        label = (source, spawn["name"])
        if label in seen_sources:
            continue
        seen_sources.add(label)
        compared += 1
        missing = sorted(x for x in other - items if not IGNORE.search(x))
        extra = sorted(x for x in items - other if not IGNORE.search(x))
        only_db_total += len(missing)
        only_ours_total += len(extra)
        if missing or extra:
            rows.append((len(missing) + len(extra), source, spawn["name"], spawn["spawns"], missing, extra))

    rows.sort(key=lambda r: (-r[0], r[1]))
    out = [f"Compared {compared} monsters with a table in both the import and the db.",
           f"{len(rows)} differ. Items only the db has: {only_db_total}. Items only the import has: {only_ours_total}.",
           "", "differences, source / npc name / spawns: missing from the import | only in the import", ""]
    for n, source, name, spawns, missing, extra in rows:
        out.append(f"{source} / {name} / {spawns}:")
        if missing:
            out.append("    db only:     " + ", ".join(missing[:12]) + (" ..." if len(missing) > 12 else ""))
        if extra:
            out.append("    import only: " + ", ".join(extra[:12]) + (" ..." if len(extra) > 12 else ""))
    (TOOL_DIR / "crosscheck.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print("\n".join(out[:2]))


if __name__ == "__main__":
    main()
