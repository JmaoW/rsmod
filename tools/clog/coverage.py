#!/usr/bin/env python3
"""Which collection log slots can be filled in the event as built -> tools/clog/coverage.txt.

    python tools/clog/coverage.py

The log is item-based: a slot fills when its item is received from a drop or reward. A slot is
counted as reachable when its item either

* appears (by display name) in a monster drop table (osrs.toml / isle.toml), or
* is named by its obj symbol in the Kotlin source of the content modules (a reward, a shop-less
  grant, a pet roll...).

Everything else is listed per category so a missing reward path is easy to see. The numbers are an
estimate: an item that appears in a table but on the wrong monster still counts.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/spawns"))
sys.path.insert(0, str(ROOT / "tools/slayer"))
sys.path.insert(0, str(ROOT / "tools/food"))
import enums  # noqa: E402
import js5  # noqa: E402
import objdump  # noqa: E402

CONFIG_ARCHIVE, OBJ_GROUP, STRUCT_GROUP = 2, 10, 34
DROPS = ROOT / "content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops"
OUT = Path(__file__).resolve().parent / "coverage.txt"
TABS = ["Bosses", "Raids", "Clues", "Minigames", "Other"]


def read_struct(group, struct_id):
    data = group.get(struct_id)
    if data is None:
        return None
    p, out = 0, {}
    while p < len(data):
        code = data[p]
        p += 1
        if code == 0:
            break
        if code != 249:
            raise ValueError(f"unknown struct code {code}")
        count = data[p]
        p += 1
        for _ in range(count):
            is_str = data[p]
            key = int.from_bytes(data[p + 1:p + 4], "big")
            p += 4
            if is_str:
                end = data.index(0, p)
                out[key] = data[p:end].decode("cp1252")
                p = end + 1
            else:
                out[key] = int.from_bytes(data[p:p + 4], "big", signed=True)
                p += 4
    return out


def main():
    structs = js5.split_files(js5.read_group(CONFIG_ARCHIVE, STRUCT_GROUP), js5.ref_table(CONFIG_ARCHIVE)[STRUCT_GROUP])
    objs = js5.split_files(js5.read_group(CONFIG_ARCHIVE, OBJ_GROUP), js5.ref_table(CONFIG_ARCHIVE)[OBJ_GROUP])
    sym = {}
    for line in (ROOT / ".data/symbols/obj.sym").read_text(encoding="utf-8").splitlines():
        p = line.split("\t")
        if len(p) == 2 and p[0].isdigit():
            sym[int(p[0])] = p[1]

    table_text = ""
    for name in ("osrs.toml", "isle.toml"):
        path = DROPS / name
        if path.exists():
            table_text += path.read_text(encoding="utf-8")
    dropped = {m.lower() for m in re.findall(r'item\s*=\s*"([^"]+)"', table_text)}

    code = ""
    for path in (ROOT / "content").rglob("*.kt"):
        if "/build/" in path.as_posix() or "collection-log" in path.as_posix():
            continue
        code += path.read_text(encoding="utf-8", errors="ignore")
    code_words = set(re.findall(r'"([a-z0-9_]+)"', code))

    names = {}

    def name_of(obj_id):
        if obj_id not in names:
            names[obj_id] = objdump.decode(objs[obj_id]).get("name", str(obj_id))
        return names[obj_id]

    lines = []
    totals = {}
    for tab_index, tab_name in enumerate(TABS):
        tab = read_struct(structs, 471 + tab_index)
        category_enum = enums.read_enum(tab[683])
        reach = slots = 0
        for row in sorted(category_enum):
            category = read_struct(structs, category_enum[row])
            items = enums.read_enum(category[690]) or {}
            missing = []
            for key in sorted(items):
                obj = items[key]
                slots += 1
                if name_of(obj).lower() in dropped or sym.get(obj) in code_words:
                    reach += 1
                else:
                    missing.append(name_of(obj))
            lines.append(f"{tab_name} / {category[689]}: {len(items) - len(missing)} of {len(items)} reachable")
            if missing:
                lines.append("    missing: " + ", ".join(missing))
        totals[tab_name] = (reach, slots)

    head = ["Collection log slots reachable through a drop table or a reward in the code", ""]
    for tab_name, (reach, slots) in totals.items():
        head.append(f"{tab_name}: {reach} of {slots}")
    all_reach = sum(r for r, _ in totals.values())
    all_slots = sum(s for _, s in totals.values())
    head += [f"Total: {all_reach} of {all_slots}", ""]
    OUT.write_text("\n".join(head + lines) + "\n", encoding="utf-8")
    print("\n".join(head))


if __name__ == "__main__":
    main()
