"""Lists what the generated spawns are missing. Writes tools/spawns/gaps.txt.

The source spawn list is from August 2023, so it cannot know about newer NPCs. Two views:
  1. attackable cache npcs with an id above the highest id in the source (added since),
  2. attackable cache npcs whose name has no written spawn at all (older monsters the source lacks,
     or that it only has in a disguised form such as Rocks/Whirlpool for the Kraken).

Run after convert.py:  python tools/spawns/gaps.py
"""

import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import convert  # noqa: E402

OUT = os.path.join(HERE, "gaps.txt")


def written_ids():
    sym = {}
    for line in open(convert.NPC_SYM):
        parts = line.split()
        if len(parts) >= 2:
            sym[parts[1]] = int(parts[0])
    ids = collections.Counter()
    for name in ("surface.toml", "underground.toml"):
        text = open(os.path.join(convert.RESOURCES, name)).read()
        for m in re.finditer(r"npc = '(\w+)'", text):
            ids[sym[m.group(1)]] += 1
    return ids


def main():
    npcs = convert.load_cache_npcs()
    source = json.load(open(convert.SOURCE, encoding="utf-8"))
    top_source_id = max(s["id"] for s in source)
    written = written_ids()
    written_names = collections.Counter()
    for i, c in written.items():
        for n in convert.cache_names(npcs, i):
            written_names[n.lower()] += c

    def attackable(v):
        return v["name"] and v["level"] > 0 and any("ttack" in (o or "") for o in v["ops"])

    out = []
    newer = collections.defaultdict(list)
    for i, v in npcs.items():
        if i > top_source_id and attackable(v):
            newer[v["name"]].append((i, v["level"]))
    out.append(f"1. Attackable npcs added to the cache after the source list (id > {top_source_id}): "
               f"{len(newer)} names, none of them spawned.\n")
    for name, rows in sorted(newer.items(), key=lambda kv: kv[1][0][0]):
        out.append(f"   {rows[0][0]:6d}  {name}  (combat {rows[0][1]}, {len(rows)} type(s))")

    missing = collections.defaultdict(list)
    for i, v in npcs.items():
        if i <= top_source_id and attackable(v) and not written_names.get(v["name"].lower()):
            missing[v["name"]].append((i, v["level"]))
    out.append(f"\n2. Older attackable npc names with no written spawn: {len(missing)} names.\n")
    for name, rows in sorted(missing.items(), key=lambda kv: -kv[1][0][1]):
        out.append(f"   {rows[0][0]:6d}  {name}  (combat {rows[0][1]}, {len(rows)} type(s))")
    open(OUT, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(f"gaps: {len(newer)} newer names, {len(missing)} older names without spawns -> {OUT}")


if __name__ == "__main__":
    main()
