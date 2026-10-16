#!/usr/bin/env python3
"""Checks that the item names in the food module resolve in the cache.

    FOOD_TOOL_OUT=<dir with objs.json> python tools/food/check_food.py

Reads the names out of FoodData.kt and Drinks.kt (name, bites, leaves) and looks each up in
objs.json, which `objdump.py` writes. Prints the foods that have no edible obj, and any obj that two
foods would both claim. Foods the cache lacks are expected (Sailing fish) and skipped in game.
"""

import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = Path(os.environ.get("FOOD_TOOL_OUT", HERE))
SRC = ROOT / "content/other/food/src/main/kotlin/org/rsmod/content/other/food"


def key(name):
    return "".join(c for c in name if ord(c) < 128)


def main():
    objs = json.load(open(OUT / "objs.json"))
    by_name = defaultdict(list)
    for o in objs:
        if o["noted"] or o["placeholder"] or not o["name"]:
            continue
        by_name[key(o["name"])].append(o)

    def edible(name):
        return [o for o in by_name.get(key(name), []) if o["iop"][0] in ("Eat", "Drink")]

    claims = defaultdict(list)
    unresolved = []
    text = "".join((SRC / f).read_text(encoding="utf-8") for f in ("FoodData.kt", "Drinks.kt"))
    # A FoodDef( or ale( / drink( call: take its name and the bites / leaves it names.
    for m in re.finditer(r'(?:FoodDef\(\s*name = |\bale\(\s*|\bdrink\(\s*)"([^"]+)"', text):
        name = m.group(1)
        tail = text[m.end(): m.end() + 900]
        end = tail.find("FoodDef(")
        block = tail if end < 0 else tail[:end]
        bites = re.search(r"bites = listOf\(([^)]*)\)", block)
        names = re.findall(r'"([^"]+)"', bites.group(1)) if bites else [name]
        for n in names:
            objs_found = edible(n)
            if not objs_found:
                unresolved.append((name, n))
            for o in objs_found:
                claims[o["id"]].append(f"{name}/{n}")
    # The preparation recipes: every quoted name in CookingPrep.kt, `3x Name` and `Name#2` included.
    prep = (ROOT / "content/skills/cooking/src/main/kotlin/org/rsmod/content/skills/cooking/configs/CookingPrep.kt")
    anyitem = defaultdict(list)
    for o in objs:
        if not (o["noted"] or o["placeholder"] or not o["name"]):
            anyitem[key(o["name"])].append(o)
    missing_prep = []
    for m in re.finditer(r'"([^"]+)"', prep.read_text(encoding="utf-8")):
        text = re.sub(r"^\d+x ", "", m.group(1))
        name, _, nth = text.partition("#")
        if " " not in text and not name[:1].isupper():
            continue
        found = anyitem.get(key(name), [])
        if not found or len(found) < int(nth or 1):
            if name[:1].isupper() and not name.startswith(("What", "Ticks", "Fires")):
                missing_prep.append(text)
    print("recipe names with no obj:", sorted(set(missing_prep)))
    print("unresolved (no edible obj):")
    for name, n in unresolved:
        print("  ", name, "->", n)
    print("claimed twice:")
    for oid, who in claims.items():
        if len(set(who)) > 1:
            print("  ", oid, who)


if __name__ == "__main__":
    sys.exit(main())
