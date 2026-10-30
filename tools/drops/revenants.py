"""Writes the revenant drop tables to content/other/drop-tables/.../drops/revenants.toml.

Every revenant shares the wiki's `Template:Revenants/Drops`, but its chances depend on the monster's
combat level (the template takes `combat=`, and each revenant page passes its own). The importer
(`wiki_import.py`) cannot read that template, so this script copies its formulas and evaluates them
for each revenant. Rarity denominators are rounded to 2 decimals, as the wiki's `round 2` does.

Each revenant gets four tables, the wiki's four states:
- the usual table (off task, unskulled);
- `skulled`: the skulled rates of the unique and statuette rows; no dragon med helm;
- `on-task`: the on-task unique table, which replaces the off-task one;
- `on-task skulled`: both of the above.
The engine picks one from the killer's skull and Slayer task (see DropVariant).

Revenant ether is 2 to N in the wiki, but only even amounts; the data can only say 2-N, so odd
amounts can drop too. The wiki's boosted-on-ghost-task rule is not modelled (revenants only).

The Isle of Souls event gives more ether than the wiki (the revenant weapons cost thousands of it to
charge, and the event is only a day long). That is not in this file: `revenantEtherMultiplier` in
`.data/league/event-settings.json` multiplies the amount when it drops. `ETHER_SCALE` stays 1 so the
data is the wiki's.

Usage: python tools/drops/revenants.py
"""
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "content", "other", "drop-tables", "src", "main", "resources",
                   "org", "rsmod", "content", "other", "drops", "revenants.toml")

# How many times the wiki's amount of revenant ether the table holds (the event boost is a setting).
ETHER_SCALE = 1

# (wiki page, npc ids, combat level the page passes to the template)
REVENANTS = [
    ("Revenant goblin", [7931], 15),
    ("Revenant pyrefiend", [7932], 52),
    ("Revenant hobgoblin", [7933], 60),
    ("Revenant cyclops", [7934], 82),
    ("Revenant imp", [7881], 7),
    ("Revenant hellhound", [7935], 90),
    ("Revenant demon", [7936], 98),
    ("Revenant ork", [7937], 105),
    ("Revenant dark beast", [7938], 120),
    ("Revenant knight", [7939], 126),
    ("Revenant dragon", [7940], 135),
    # Not implemented yet. The wiki page passes the dragon's values to the template, and its own
    # special drops (the totem or emblem, two dragon rolls) are not part of this table.
    ("Revenant maledictus", [11246], 135),
]

# The four states: (variant key or None, skulled, on task).
STATES = [
    (None, False, False),
    ("skulled", True, False),
    ("on-task", False, True),
    ("on-task skulled", True, True),
]

# Items the wiki's boosted list names as groups or by name: `boosted` is set here because the
# importer's `[[boost]]` lines match single item names only.
BOOSTED = {
    "Amulet of avarice", "Craw's bow (u)", "Thammaron's sceptre (u)", "Viggora's chainmace (u)",
    "Ancient emblem", "Ancient totem", "Ancient statuette", "Ancient crystal", "Ancient medallion",
    "Ancient effigy", "Ancient relic",
}


def r2(x):
    return round(x, 2)


def fmt(p):
    return f"{p:.8g}"


def revenant_lines(combat, skulled, on_task):
    """The template's drop lines for one combat level and state: (name, qty, noted, chance)."""
    sq = math.floor(math.sqrt(combat))
    a = math.floor(2200 / sq)
    b = 15 + math.floor((combat + 60) ** 2 / 200)
    m_dc = (min(a, b) - 1) / a  # chance of the mediocre table
    m_sc = 198 / m_dc  # standard chance denominator of the mediocre table
    rows = []

    def add(name, qty, denom, noted=False):
        rows.append((name, qty, noted, 1 / r2(denom)))

    # Unique table: 2/5 amulet, 1/5 each weapon. The wiki's base is 26.667 off task, 5.333 on
    # task, halved again by a skull's rates (14.667 and 2.933 are the skulled bases).
    if on_task:
        unique_base = 2.933 if skulled else 5.333
    else:
        unique_base = 14.667 if skulled else 26.667
    add("Amulet of avarice", "1", a * unique_base * 2.5)
    add("Craw's bow (u)", "1", a * unique_base * 5)
    add("Thammaron's sceptre (u)", "1", a * unique_base * 5)
    add("Viggora's chainmace (u)", "1", a * unique_base * 5)

    # Ancient statuettes: a skull lowers the base from 40 to 22.
    statuette_base = 22 if skulled else 40
    add("Ancient emblem", "1", a * statuette_base / 6)
    add("Ancient totem", "1", a * statuette_base / 4)
    add("Ancient statuette", "1", a * statuette_base / 2)
    add("Ancient crystal", "1", a * statuette_base / 3)
    add("Ancient medallion", "1", a * statuette_base)
    add("Ancient effigy", "1", a * statuette_base)
    add("Ancient relic", "1", a * statuette_base)

    # Weapons and armour.
    add("Bracelet of ethereum (uncharged)", "1", m_sc / 30)
    add("Battlestaff", "4", m_sc / 10)
    add("Rune full helm", "2", m_sc / 4)
    add("Rune platebody", "2", m_sc / 4)
    add("Rune platelegs", "2", m_sc / 4)
    add("Rune kiteshield", "2", m_sc / 4)
    add("Rune warhammer", "2", m_sc / 4)
    add("Dragon platelegs", "1-2", m_sc / 1)
    add("Dragon plateskirt", "1-2", m_sc / 1)
    add("Dragon dagger", "2", m_sc / 2)
    add("Dragon longsword", "2", m_sc / 2)
    if not skulled:  # the wiki says "Never" when skulled
        add("Dragon med helm", "1", a * 40 / 13)

    # Resources.
    add("Blighted super restore(4)", "1-3", m_sc / 6)
    add("Blighted manta ray", "10-15", m_sc / 8)
    add("Coal", "30-60", m_sc / 12, noted=True)
    add("Adamantite bar", "4-6", m_sc / 12)
    add("Runite ore", "2-4", m_sc / 8)
    add("Runite bar", "2-3", m_sc / 6)
    add("Black dragonhide", "4", m_sc / 8)
    add("Mahogany plank", "8-16", m_sc / 6, noted=True)
    add("Yew logs", "20-40", m_sc / 8, noted=True)
    add("Magic logs", "8-16", m_sc / 4, noted=True)
    add("Uncut dragonstone", "2-5", m_sc / 2)
    add("Yew seed", "2-6", a * statuette_base / 4)
    add("Magic seed", "2-6", a * statuette_base / 4)

    # Other.
    if combat < 132:
        add("Coins", f"1-{1 + 25 * sq}", a / (a - b))
    add("Revenant cave teleport", "1-5", m_sc / 10)
    add("Dragonstone bolt tips", "20-40", m_sc / 6)
    add("Onyx bolt tips", "3-6", m_sc / 6)
    add("Law rune", "20-45", m_sc / 10)
    add("Death rune", "30-60", m_sc / 10)
    add("Blood rune", "50-100", m_sc / 10)

    # Secondary: the blighted sacks and friends.
    add("Blighted ancient ice sack", "1-10", (250 - combat) / 10)
    add("Blighted entangle sack", "1-10", (250 - combat) / 20)
    add("Blighted teleport spell sack", "1-10", (250 - combat) / 10)
    add("Blighted vengeance sack", "1-10", (250 - combat) / 10)
    add("Blighted manta ray", "1-2", (250 - combat) / 15)
    add("Blighted anglerfish", "1-2", (250 - combat) / 15)
    add("Blighted karambwan", "1-2", (250 - combat) / 10)
    add("Blighted super restore(4)", "1", (250 - combat) / 5)
    add("Revenant cave teleport", "1", (250 - combat) / 5)

    # Tertiary.
    if combat < 10:
        looting = 1 / 15
    elif combat > 50:
        looting = 1 / 3
    else:
        looting = 1 / math.floor(150 / combat)
    rows.append(("Looting bag", "1", False, looting))
    return rows, sq


# The champion scrolls are on the imp, goblin and hobgoblin pages only (1/5000 each).
SCROLLS = {
    "Revenant imp": "Imp champion scroll",
    "Revenant goblin": "Goblin champion scroll",
    "Revenant hobgoblin": "Hobgoblin champion scroll",
}


def main():
    out = [
        "# Generated by tools/drops/revenants.py from the wiki's Revenants/Drops template. Do not edit;\n",
        "# re-run the script. Every revenant shares the table; the chances are per combat level, and the\n",
        "# variant tables are the skulled and on-task rates (see DropVariant).\n",
    ]
    for page, ids, combat in REVENANTS:
        for variant, skulled, on_task in STATES:
            lines, sq = revenant_lines(combat, skulled, on_task)
            if page in SCROLLS:
                lines.append((SCROLLS[page], "1", False, 1 / 5000))
            out.append("\n[[monster]]\n")
            out.append(f'source = "{page}"  # combat {combat}\n')
            if variant is not None:
                out.append(f'variant = "{variant}"\n')
            out.append(f"ids = [{', '.join(str(i) for i in ids)}]\n")
            out.append(f'always = [{{ item = "Revenant ether", qty = "{2 * ETHER_SCALE}-{(sq + 1) * 2 * ETHER_SCALE}" }}]\n')
            out.append("tertiary = [\n")
            for item, qty, noted, chance in lines:
                extra = ", noted = true" if noted else ""
                boosted = ", boosted = true" if item in BOOSTED else ""
                out.append(
                    f'  {{ item = "{item}", qty = "{qty}", chance = "{fmt(chance)}"{extra}{boosted} }},\n'
                )
            out.append("]\n")
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(out))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
