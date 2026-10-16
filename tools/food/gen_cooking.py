#!/usr/bin/env python3
"""Turns the wiki's cooking pages into Kotlin -> CookingExtras.kt (the food that is cooked).

    FOOD_TOOL_OUT=<dir with objs.json, wiki_cooking.json, wiki_burn.json> python tools/food/gen_cooking.py

`wiki_cooking.py` fetches the pages and `objdump.py` the cache. For each food the wiki gives the
Cooking level and experience and, for most, the success chart (the chance to cook it without
burning, low at level 1 and high at level 99, out of 256), which is used as it is. Food without a
chart, mostly the Hunter meats, gets a chance that is made up from the level the wiki says it stops
burning at: 55% at the level it can first be cooked (about what the foods that do have a chart
have) and rising in a line to 100% at the stop level.
"""

import json
import math
import os
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = Path(os.environ.get("FOOD_TOOL_OUT", HERE))
TARGET = (ROOT / "content/skills/cooking/src/main/kotlin/org/rsmod/content/skills/cooking/configs/CookingExtras.kt")

# wiki title -> the burnt item's name when it is not "Burnt <cooked name>".
BURNT = {
    "Manta ray": "Burnt manta ray", "Sea turtle": "Burnt sea turtle", "Dark crab": "Burnt dark crab",
    "Lava eel": "Burnt eel", "Cave eel": "Burnt cave eel", "Cooked slimy eel": "Burnt eel",
    "Rainbow fish": "Burnt rainbow fish", "Ugthanki meat": "Burnt meat", "Cooked chompy": "Ruined chompy",
    "Roast bird meat": "Burnt bird meat", "Roast beast meat": "Burnt beast meat",
    "Thin snail meat": "Burnt snail", "Lean snail meat": "Burnt snail", "Fat snail meat": "Burnt snail",
    "Baked potato": "Burnt potato", "Cooked sweetcorn": "Burnt sweetcorn", "Plain pizza": "Burnt pizza",
    "Cake": "Burnt cake", "Stew": "Burnt stew", "Curry": "Burnt curry", "Garden pie": "Burnt pie",
    "Fish pie": "Burnt pie", "Admiral pie": "Burnt pie", "Wild pie": "Burnt pie",
    "Summer pie": "Burnt pie", "Mushroom pie": "Burnt pie", "Botanical pie": "Burnt pie",
    "Dragonfruit pie": "Burnt pie", "Cooked fishcake": "Burnt fishcake", "Scrambled egg": "Burnt egg",
    "Fried mushrooms": "Burnt mushroom", "Fried onions": "Burnt onion",
}
HUNTER_MEATS = [
    "Cooked wild kebbit", "Cooked barb-tailed kebbit", "Cooked dashing kebbit",
    "Cooked sunlight antelope", "Cooked moonlight antelope", "Cooked pyre fox", "Cooked larupia",
    "Cooked graahk", "Cooked kyatt",
]
for _t in HUNTER_MEATS:
    BURNT[_t] = "Burnt meat"
RAIDS = ["Pysk fish (0)", "Suphi fish (1)", "Leckish fish (2)", "Brawk fish (3)", "Mycil fish (4)",
         "Roqed fish (5)", "Kyren fish (6)"]
BATS = ["Guanic bat (0)", "Prael bat (1)", "Giral bat (2)", "Phluxia bat (3)", "Kryket bat (4)",
        "Murng bat (5)", "Psykk bat (6)"]
for _t in RAIDS:
    BURNT[_t] = "Burnt fish"
for _t in BATS:
    BURNT[_t] = "Burnt bat"

# Safe cook levels the wiki gives for foods that have no success chart: (fire, range) or one number.
SAFE = {
    "Lava eel": 54, "Cooked slimy eel": 59, "Cave eel": 74, "Rainbow fish": 64,
    "Ugthanki meat": (None, 95), "Cooked chompy": 99, "Roast bird meat": 99, "Roast beast meat": 99,
    "Thin snail meat": 47, "Fat snail meat": 56, "Cooked wild kebbit": 50, "Cooked larupia": 59,
    "Cooked barb-tailed kebbit": 63, "Cooked graahk": 75, "Cooked fishcake": 62, "Cooked kyatt": 86,
    "Sea turtle": 99, "Dark crab": 99, "Manta ray": 99, "Cooked dashing kebbit": 99,
    "Cooked moonlight antelope": 99, "Cooked sweetcorn": (54, 50), "Stew": (58, 55),
    "Curry": (74, 70), "Baked potato": (41, 38), "Fried onions": (None, 73), "Fried mushrooms": (None, 85),
    # Chambers of Xeric: the wiki says level + 1.
    "Pysk fish (0)": 2, "Suphi fish (1)": 16, "Leckish fish (2)": 31, "Brawk fish (3)": 46,
    "Mycil fish (4)": 61, "Roqed fish (5)": 76, "Kyren fish (6)": 91, "Guanic bat (0)": 2,
    "Prael bat (1)": 16, "Giral bat (2)": 31, "Phluxia bat (3)": 46, "Kryket bat (4)": 61,
    "Murng bat (5)": 76, "Psykk bat (6)": 91,
}
# Cooked on a fire only (a spit is needed), with the spit handed back.
SPIT = {"Cooked chompy": "Skewered chompy", "Roast bird meat": "Skewered bird meat",
        "Roast beast meat": "Skewered beast"}
RANGE_ONLY = {"Plain pizza", "Cake", "Garden pie", "Fish pie", "Admiral pie", "Wild pie",
              "Summer pie", "Mushroom pie", "Botanical pie", "Dragonfruit pie", "Baked potato", "Stew",
              "Curry", "Scrambled egg", "Fried onions", "Fried mushrooms"}
BAKED = RANGE_ONLY - {"Stew", "Curry", "Scrambled egg", "Fried onions", "Fried mushrooms"}
SKIP = {"Cooked jubbly", "Cooked crab meat", "Plain pizza"}
LEAVES = {"Cake": "Cake tin"}
PIE_NAMES = {"Garden pie": "Raw garden pie", "Fish pie": "Raw fish pie", "Admiral pie": "Raw admiral pie",
             "Wild pie": "Raw wild pie", "Summer pie": "Raw summer pie",
             "Mushroom pie": "Uncooked mushroom pie", "Botanical pie": "Uncooked botanical pie",
             "Dragonfruit pie": "Uncooked dragonfruit pie"}


def snake(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def made_up(level, safe):
    """(low, high) with 55% at [level], rising in a line to 100% at [safe]."""
    if safe <= level + 1:
        return 256, 256
    slope = 0.45 / (safe - level)
    low = 0.55 - slope * (level - 1)
    if low >= 0:
        high = 1.0 + slope * (99 - safe)
    else:
        # Too steep to start from 55%: start from 0% at level 1 and reach 100% at [safe].
        low, high = 0.0, 98 / (safe - 1)
    return round(256 * low), round(256 * high)


def main():
    objs = json.load(open(OUT / "objs.json"))
    by_name = {}
    for o in objs:
        if o["noted"] or o["placeholder"] or not o["name"]:
            continue
        by_name.setdefault(o["name"], o)  # objs are sorted by id: keep the lowest
    wiki = json.load(open(OUT / "wiki_cooking.json"))

    def sym(name, why):
        found = by_name.get(name)
        if found is None:
            raise SystemExit(f"no obj called {name!r} ({why})")
        return found["sym"]

    objs_kt, rows_kt, problems = {}, [], []
    for title in [t for t in wiki if t in cooked_titles()]:
        if title in SKIP:
            continue
        page = wiki[title]
        recipe = next((r for r in page["recipes"] if r.get("skill1") == "Cooking" and
                       r.get("facilities", "") in ("Cooking range", "Fire", "")), None)
        if recipe is None:
            problems.append(f"{title}: no cooking recipe")
            continue
        level, xp = int(recipe["skill1lvl"]), float(recipe["skill1exp"])
        raw_name = SPIT.get(title) or recipe["mat1"].split("#")[0]
        if title in PIE_NAMES:
            raw_name = PIE_NAMES[title]
        cooked_name = recipe["output1"]
        burnt_name = BURNT.get(title, "Burnt " + cooked_name.lower())
        try:
            raw, done, burnt = (sym(raw_name, "raw"), sym(cooked_name, "cooked"), sym(burnt_name, "burnt"))
        except SystemExit as err:
            problems.append(f"{title}: {err}")
            continue
        chart = page["chart"]
        safe = SAFE.get(title)
        fire_safe, range_safe = (safe if isinstance(safe, tuple) else (safe, safe)) if safe else (None, None)
        rates = {}
        for key, c, safe_level in (("fire", chart.get("Fire"), fire_safe), ("range", chart.get("Range"), range_safe)):
            if c:
                rates[key] = (c["low"], c["high"])
            elif safe_level:
                rates[key] = made_up(level, safe_level)
        # A food that only has one set of numbers is cooked the same on both.
        if "range" not in rates and "fire" in rates:
            rates["range"] = rates["fire"]
        if "fire" not in rates and "range" in rates and title not in RANGE_ONLY:
            rates["fire"] = rates["range"]
        if title in RANGE_ONLY:
            rates.pop("fire", None)
        if title in SPIT:
            rates["range"] = rates["fire"]
        if "range" not in rates:
            problems.append(f"{title}: no success data")
            continue
        verb = "bake" if title in BAKED else "cook"
        fire_kt = "null" if "fire" not in rates else f"CookRate({rates['fire'][0]}, {rates['fire'][1]})"
        for name, s in ((snake(raw_name), raw), (snake(cooked_name), done), (snake(burnt_name), burnt)):
            objs_kt[name] = s
        leaves = LEAVES.get(title)
        if title in SPIT:
            leaves = "Iron spit"
        leaves_kt = ""
        if leaves:
            objs_kt[snake(leaves)] = sym(leaves, "leaves")
            leaves_kt = f"            leaves = CookingExtraObjs.{snake(leaves)},\n"
        rows_kt.append(
            "        CookableFood(\n"
            f"            raw = CookingExtraObjs.{snake(raw_name)},\n"
            f"            cooked = CookingExtraObjs.{snake(cooked_name)},\n"
            f"            burnt = CookingExtraObjs.{snake(burnt_name)},\n"
            f"            level = {level},\n"
            f"            xp = {xp},\n"
            f"            fire = {fire_kt},\n"
            f"            range = CookRate({rates['range'][0]}, {rates['range'][1]}),\n"
            + (f'            verb = "{verb}",\n' if verb != "cook" else "")
            + leaves_kt
            + "        ),"
        )

    lines = [
        "// Generated by tools/food/gen_cooking.py from the OSRS wiki's cooking pages and the cache. Do",
        "// not edit by hand: change the generator and run it again.",
        "package org.rsmod.content.skills.cooking.configs",
        "",
        "import org.rsmod.api.type.refs.obj.ObjReferences",
        "",
        "internal object CookingExtraObjs : ObjReferences() {",
    ]
    for name, s in sorted(objs_kt.items()):
        lines.append(f'    val {name} = find("{s}")')
    lines += ["}", "", "/** The food that is cooked besides the fish, meat and baked goods in [CookingFoods]. */",
              "internal object CookingExtras {", "    val all: List<CookableFood> =", "        listOf("]
    lines += rows_kt
    lines += ["        )", "}"]
    TARGET.write_text("\n".join(lines).replace("\n", "\r\n") + "\r\n", encoding="utf-8", newline="")
    print(len(rows_kt), "foods ->", TARGET)
    for p in problems:
        print("PROBLEM", p)


def cooked_titles():
    from wiki_cooking import COOKED

    return set(COOKED)


if __name__ == "__main__":
    import sys

    sys.path.insert(0, str(HERE))
    main()
