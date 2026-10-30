#!/usr/bin/env python3
"""Marks npcs with the combat "type" params the damage formulas read.

    python tools/npc-attributes/derive_attributes.py            # writes npc_attributes.toml
    python tools/npc-attributes/derive_attributes.py --fetch    # download the osrsreboxed file again

`CombatNpcAttributeCollector` turns npc params into attributes (salve amulet vs undead, the demonbane
weapons vs demons, ...), but nothing set the params on any npc, so none of those bonuses applied. The
cache already carries `draconic`, `golem` and `kalphite` (they are Jagex's own, so this tool leaves
them alone). It writes the rest to
api/cache-enricher/src/main/resources/org/rsmod/api/cache/enricher/npc/npc_attributes.toml, which
`DefaultNpcCacheEnricher` applies when the cache is packed:

    undead, demon, shade, leafy, xerician   osrsreboxed-db `attributes` (the wiki infobox attributes)
    rat                                     osrsreboxed-db category "rats" / "brine rats" (rat bone weapons)
    demonbane_resistant                     the wiki's "Demon (attribute)" page: only Duke Sucellus has
                                            reduced demonbane effectiveness (70%, so Arclight gives 49%
                                            instead of 70%); Ice Demon, Yama and Void Flares are *more*
                                            effective, which the engine has no param for
    tormented_demon                         npcs named "Tormented Demon"
    amascutnpc                              the Amascut boss npcs (listed below; osrsreboxed has none)

Ids come from osrsreboxed-db (shared with tools/drops) and are turned into the internal names that
npc_anims.toml uses through .data/symbols/npc.sym. An id osrsreboxed knows but the cache does not
(newer than this cache) is skipped.
"""

import argparse
import json
import sys
import urllib.request
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
REPO = TOOL_DIR.parent.parent
CACHE_DIR = REPO / "tools" / "drops" / ".cache"
RB_URL = "https://raw.githubusercontent.com/RSPSApp/osrsreboxed-db/master/docs/monsters-complete.json"
USER_AGENT = "rsmod-isle-of-souls-npcattributes/0.1"
NPC_SYM = REPO / ".data" / "symbols" / "npc.sym"
OUT = (
    REPO
    / "api/cache-enricher/src/main/resources/org/rsmod/api/cache/enricher/npc/npc_attributes.toml"
)

# osrsreboxed attribute -> param name.
ATTRIBUTES = {
    "undead": "undead",
    "demon": "demon",
    "shade": "shade",
    "leafy": "leafy",
    "xerician": "xerician",
}
RAT_CATEGORIES = {"rats", "brine rats"}
DEMONBANE_RESISTANT_NAMES = {"duke sucellus"}
# osrsreboxed has no Tombs of Amascut npcs. The calculators treat every monster of the raid alike
# (their defence rolls grow with the raid level), so the whole block of the raid's monsters in the
# cache (npc ids 11696-11805: the puzzle monsters, the four bosses, the Wardens and their adds) and
# Amascut herself (11835) are tagged. The block is read from npc.sym in main().
AMASCUT_NPC_IDS = list(range(11696, 11806)) + [11835]

# Order the sections are written in.
SECTIONS = [
    "undead",
    "demon",
    "demonbane_resistant",
    "shade",
    "leafy",
    "xerician",
    "rat",
    "tormented_demon",
    "amascutnpc",
]


def load_monsters(fetch):
    CACHE_DIR.mkdir(exist_ok=True)
    path = CACHE_DIR / "rb_monsters-complete.json"
    if fetch or not path.exists():
        request = urllib.request.Request(RB_URL, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=300) as response:
            path.write_bytes(response.read())
    return json.loads(path.read_text(encoding="utf-8"))


def load_symbols():
    names = {}
    for line in NPC_SYM.read_text(encoding="utf-8").splitlines():
        if line.strip():
            npc_id, name = line.split("\t")
            names[int(npc_id)] = name
    return names


def classify(monster):
    """The param names that apply to one osrsreboxed monster."""
    found = set()
    for attribute in monster.get("attributes") or []:
        if attribute in ATTRIBUTES:
            found.add(ATTRIBUTES[attribute])
    if RAT_CATEGORIES & set(monster.get("category") or []):
        found.add("rat")
    name = (monster.get("name") or "").lower()
    if name in DEMONBANE_RESISTANT_NAMES:
        found.add("demonbane_resistant")
    if name.startswith("tormented demon"):
        found.add("tormented_demon")
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--fetch", action="store_true", help="download the osrsreboxed file again")
    args = parser.parse_args()

    monsters = load_monsters(args.fetch)
    symbols = load_symbols()

    sections = {name: {} for name in SECTIONS}
    skipped = 0
    for monster in monsters.values():
        npc_id = monster["id"]
        params = classify(monster)
        if not params:
            continue
        internal = symbols.get(npc_id)
        if internal is None:
            skipped += 1
            continue
        for param in params:
            sections[param][internal] = monster["name"]

    for npc_id in AMASCUT_NPC_IDS:
        internal = symbols.get(npc_id)
        if internal is None:
            sys.exit(f"Amascut npc missing from npc.sym: {npc_id}")
        sections["amascutnpc"][internal] = "Tombs of Amascut"

    lines = [
        "# Generated by tools/npc-attributes/derive_attributes.py. Do not edit; re-run the script.",
        "# Combat \"type\" params the damage formulas read (see CombatNpcAttributeCollector).",
        "# `npc_name = 1`, one section per param. `draconic`, `golem` and `kalphite` come with the cache.",
    ]
    for section in SECTIONS:
        lines.append("")
        lines.append(f"[{section}]")
        for internal in sorted(sections[section]):
            label = sections[section][internal].replace("\n", " ")
            lines.append(f'"{internal}" = 1 # {label}')
    OUT.write_text("\r\n".join(lines) + "\r\n", encoding="utf-8", newline="")

    for section in SECTIONS:
        print(f"{section:20s} {len(sections[section])}")
    print(f"skipped (id not in the cache): {skipped}")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    sys.exit(main())
