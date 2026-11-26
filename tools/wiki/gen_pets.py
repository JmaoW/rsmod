#!/usr/bin/env python3
"""Writes the event wiki's pet data (wiki/data/pets-generated.js).

    python -I tools/wiki/gen_pets.py

Every pet of the collection log (tools/pets/gen_pets.py, the same table the game uses) with its
looks, where it comes from on the isle (hand-written below, only for content that is built) and
how its look changes (from the game's metamorphosis rules in PetMetamorph.kt, written out here).
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/pets"))
import gen_pets  # noqa: E402

OUT = ROOT / "wiki/data/pets-generated.js"

# key -> (source, chance) for every pet that can be had on the isle as built. Chances are the
# game's own; the event's petOdds setting multiplies them.
SOURCES = {
    "tzrek_jad": ("TzHaar Fight Cave: beating TzTok-Jad", "1/200"),
    "jal_nib_rek": ("The Inferno: beating TzKal-Zuk", "1/100"),
    "snakeling": ("Zulrah", "1/4,000"),
    "scurry": ("Scurrius (the player who did the most damage)", "1/3,000"),
    "kalphite_princess": ("Kalphite Queen", "1/3,000"),
    "callisto_cub": ("Callisto / Artio", "1/1,500 / 1/2,800"),
    "venenatis_spiderling": ("Venenatis / Spindel", "1/1,500 / 1/2,800"),
    "vetion_jr": ("Vet'ion / Calvar'ion", "1/1,500 / 1/2,800"),
    "prince_black_dragon": ("King Black Dragon", "1/3,000"),
    "dagannoth_rex": ("Dagannoth Rex", "1/5,000"),
    "dagannoth_prime": ("Dagannoth Prime", "1/5,000"),
    "dagannoth_supreme": ("Dagannoth Supreme", "1/5,000"),
    "vorki": ("Vorkath", "1/3,000"),
    "smol_heredit": ("Fortis Colosseum: beating Sol Heredit, or trading a spare quiver to Minimus",
                     "1/200"),
    "tumekens_guardian": ("Tombs of Amascut (one roll for the team, by points and raid level)",
                          "varies"),
    "lil_zik": ("Theatre of Blood (by the player's standing)", "1/650 or rarer"),
    "olmlet": ("Chambers of Xeric (the player who gets a unique)", "1/53 of uniques"),
    "youngllef": ("The Gauntlet / Corrupted Gauntlet", "1/2,000 / 1/800"),
    "general_graardor": ("General Graardor", "1/5,000"),
    "zilyana": ("Commander Zilyana", "1/5,000"),
    "kreearra": ("Kree'arra", "1/5,000"),
    "kril_tsutsaroth": ("K'ril Tsutsaroth", "1/5,000"),
    "rock_golem": ("Mining (one roll per ore; it comes in the ore's colour)",
                   "1 in (base - level x 25)"),
    "beaver": ("Woodcutting (one roll per log)", "1 in (base - level x 25)"),
    "heron": ("Fishing (one roll per catch)", "1 in (base - level x 25)"),
    "rift_guardian": ("Runecraft (one roll per essence; in the altar's colour)",
                      "1 in (base - level x 25)"),
    "baby_chinchompa": ("Hunter: catching chinchompas (in their colour)",
                        "1 in (base - level x 25)"),
    "giant_squirrel": ("Agility: every Brimhaven Agility Arena ticket", "1 in (26,404 - level x 25)"),
    "rocky": ("Thieving: pickpocketing and stalls", "1 in (base - level x 25)"),
}

MORPHS = {
    "kalphite_princess": "Metamorphosis",
    "smoke_devil": "Metamorphosis",
    "dark_core": "Metamorphosis",
    "snakeling": "Metamorphosis",
    "venenatis_spiderling": "Metamorphosis (legacy look)",
    "callisto_cub": "Metamorphosis (legacy look)",
    "vetion_jr": "Metamorphosis",
    "jal_nib_rek": "Metamorphosis",
    "noon": "Metamorphosis",
    "ikkle_hydra": "Metamorphosis",
    "lil_creator": "Metamorphosis",
    "bran": "Metamorphosis",
    "baby_chinchompa": "Metamorphosis (red, grey, black; 1 in 10,000 to turn gold)",
    "tzrek_jad": "Metamorphosis once all of TzHaar-Ket-Rak's Challenges are done (not on the isle)",
    "youngllef": "Metamorphosis once the Corrupted Gauntlet is beaten",
    "olmlet": "Use metamorphic dust on it, then Metamorphosis",
    "lil_zik": "Use sanguine dust on it, then Metamorphosis",
    "little_nightmare": "Use a parasitic egg on it",
    "muphin": "Use charged ice on it, then Metamorphosis",
    "nid": "Use coagulated venom on it",
    "sraracha": "Use an orange or blue egg sac on it, then Metamorphosis",
    "tumekens_guardian": "Metamorphosis (Elidinis' guardian); each remnant unlocks more",
    "giant_squirrel": "Use a dark or calcified acorn on it, then Metamorphosis",
    "phoenix": "Use 250 firelighters of a colour on it, then Metamorphosis",
    "heron": "3,000 spirit flakes for the great blue heron (then 1); a raw fish turns it back",
    "beaver": "A funky shaped log unlocks colours by logs; a pheasant egg or fox whistle "
              "unlocks more",
    "rock_golem": "Use an ore on it (a plain rock turns it back)",
    "tangleroot": "Use a seed on it (the seed is used up)",
    "rocky": "Use redberries, poison ivy berries or white berries on it",
    "baby_mole": "Use a mole claw on it (a mole skin turns it back)",
    "rift_guardian": "Craft runes at an altar while it follows (Locking keeps the colour); a "
                     "guardian's eye for the greatish guardian",
}


def main():
    pets = []
    for key, name, forms in gen_pets.PETS:
        source = SOURCES.get(key)
        pets.append({
            "key": key,
            "name": name[0].upper() + name[1:],
            "looks": [label for _, _, label in forms if label],
            "source": source[0] if source else None,
            "chance": source[1] if source else None,
            "morph": MORPHS.get(key),
        })
    data = {"pets": pets}
    text = ("// GENERATED by tools/wiki/gen_pets.py. Do not edit; change the script and run it.\n"
            "window.ISLE_PETS = " + json.dumps(data, indent=1, ensure_ascii=False) + ";\n")
    OUT.write_text(text, encoding="utf-8")
    built = sum(1 for p in pets if p["source"])
    print(f"wrote {OUT}: {len(pets)} pets, {built} with a source on the isle")


if __name__ == "__main__":
    main()
