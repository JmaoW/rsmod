#!/usr/bin/env python3
"""Fetches the wiki pages of the cooking recipes -> <FOOD_TOOL_OUT>/wiki_cooking.json.

    FOOD_TOOL_OUT=<dir> python tools/food/wiki_cooking.py

For each page it keeps the Recipe templates (skill, level, xp, materials, tools, outputs) and the
"Skilling success chart" (the chance to cook without burning, low and high out of 256, per heat
source). `gen_cooking.py` turns that into Kotlin. A descriptive User-Agent is needed: the wiki
blocks the default one.
"""

import json
import os
import re
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = Path(os.environ.get("FOOD_TOOL_OUT", HERE))
UA = "rsmod-league-dev/1.0 (ordinaryoak2012@gmail.com)"

# The things that are cooked on a fire or range: the cooked item's page has the success chart.
COOKED = [
    "Manta ray", "Sea turtle", "Dark crab", "Lava eel", "Cave eel", "Cooked slimy eel", "Rainbow fish",
    "Cooked jubbly", "Ugthanki meat", "Cooked chompy", "Roast bird meat", "Roast beast meat",
    "Thin snail meat", "Lean snail meat", "Fat snail meat", "Cooked wild kebbit",
    "Cooked barb-tailed kebbit", "Cooked dashing kebbit", "Cooked sunlight antelope",
    "Cooked moonlight antelope", "Cooked pyre fox", "Cooked larupia", "Cooked graahk",
    "Cooked kyatt", "Baked potato", "Cooked sweetcorn", "Plain pizza", "Cake", "Stew", "Curry",
    "Garden pie", "Fish pie", "Admiral pie", "Wild pie", "Summer pie", "Mushroom pie",
    "Botanical pie", "Dragonfruit pie", "Pysk fish (0)", "Suphi fish (1)", "Leckish fish (2)",
    "Brawk fish (3)", "Mycil fish (4)", "Roqed fish (5)", "Kyren fish (6)", "Guanic bat (0)",
    "Prael bat (1)", "Giral bat (2)", "Phluxia bat (3)", "Kryket bat (4)", "Murng bat (5)",
    "Psykk bat (6)", "Cooked fishcake", "Cooked crab meat", "Scrambled egg", "Fried mushrooms",
    "Fried onions",
]

# The things that are made by combining items.
MADE = [
    "Bread dough", "Pastry dough", "Pizza base", "Pitta dough", "Pie shell", "Uncooked apple pie",
    "Uncooked berry pie", "Uncooked meat pie", "Incomplete pizza", "Uncooked pizza", "Meat pizza",
    "Anchovy pizza", "Pineapple pizza", "Uncooked cake", "Chocolate cake", "Uncooked stew",
    "Uncooked curry", "Potato with butter", "Potato with cheese", "Chilli potato", "Egg potato",
    "Mushroom potato", "Tuna potato", "Chopped tuna", "Spicy sauce", "Chilli con carne",
    "Egg and tomato", "Mushroom & onion", "Tuna and corn", "Minced meat", "Scrambled egg",
    "Fried mushrooms", "Fried onions", "Chopped onion", "Chopped tomato", "Onion & tomato",
    "Raw garden pie", "Raw fish pie", "Raw admiral pie", "Raw wild pie", "Raw summer pie",
    "Raw mushroom pie", "Raw botanical pie", "Raw dragonfruit pie", "Part garden pie (tomato)",
    "Part garden pie (onion)", "Part fish pie (trout)", "Part fish pie (cod)", "Part admiral pie (salmon)",
    "Part admiral pie (tuna)", "Part wild pie (bear meat)", "Part wild pie (chompy meat)",
    "Part summer pie (strawberry)", "Part summer pie (watermelon)", "Part mushroom pie (mushroom)",
    "Part botanical pie (sweetcorn)", "Part dragonfruit pie (dragonfruit)", "Jug of wine",
    "Unfermented wine", "Chocolate dust", "Cooked sweetcorn", "Poison karambwan", "Sliced banana",
    "Pot of cream", "Pat of butter", "Cheese", "Pot of flour", "Incomplete stew", "Uncooked egg",
    "Sliced mushrooms", "Part wild pie (raw bear meat)", "Part wild pie (raw chompy)",
    "Uncooked mushroom pie", "Uncooked botanical pie", "Uncooked dragonfruit pie", "Bowl of water",
]


def fetch(title):
    cache = OUT / "wiki_pages"
    cache.mkdir(parents=True, exist_ok=True)
    path = cache / (re.sub(r"[^A-Za-z0-9]+", "_", title) + ".txt")
    if path.exists():
        return path.read_text(encoding="utf-8")
    url = ("https://oldschool.runescape.wiki/api.php?action=query&prop=revisions&rvprop=content"
           "&rvslots=main&format=json&formatversion=2&titles=" + urllib.parse.quote(title))
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.load(resp)
    page = data["query"]["pages"][0]
    text = "" if page.get("missing") else page["revisions"][0]["slots"]["main"]["content"]
    path.write_text(text, encoding="utf-8")
    return text


def parse_recipes(text):
    out = []
    for m in re.finditer(r"\{\{Recipe\s*\n(.*?)\n\}\}", text, re.S):
        fields = {}
        for line in m.group(1).split("\n"):
            line = line.strip()
            if line.startswith("|") and "=" in line:
                key, _, value = line[1:].partition("=")
                fields[key.strip()] = value.strip()
        out.append(fields)
    return out


def parse_chart(text):
    m = re.search(r"\{\{Skilling success chart(.*?)\n\}\}", text, re.S)
    if not m:
        return {}
    chart = {}
    block = m.group(1)
    for i in range(1, 6):
        label = re.search(rf"\|label{i}=([^|\n]*)", block)
        low = re.search(rf"\|low{i}=(\d+)", block)
        high = re.search(rf"\|high{i}=(\d+)", block)
        req = re.search(rf"\|req{i}=(\d+)", block)
        if label and low and high:
            chart[label.group(1).strip()] = {"low": int(low.group(1)), "high": int(high.group(1)),
                                             "req": int(req.group(1)) if req else None}
    return chart


def main():
    result = {}
    for title in COOKED + MADE:
        text = fetch(title)
        result[title] = {"recipes": parse_recipes(text), "chart": parse_chart(text), "missing": not text}
    (OUT / "wiki_cooking.json").write_text(json.dumps(result, indent=1), encoding="utf-8")
    missing = [t for t, v in result.items() if v["missing"]]
    print(len(result), "pages;", "missing:", missing)


if __name__ == "__main__":
    main()
