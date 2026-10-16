#!/usr/bin/env python3
"""Writes the dose chains of the potions Herblore does not make -> MinigamePotionObjs.kt.

    FOOD_TOOL_OUT=<dir with objs.json> python tools/food/gen_potion_refs.py

Each chain is the cache names ("Overload (+)(4)" ... "Overload (+)(1)") of one potion with its doses
from the fullest to the last, found in objs.json (which `objdump.py` writes). A name with a second
set of objs (a minigame's own copy, say) is asked for with a variant number.
"""

import json
import os
import re
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = Path(os.environ.get("FOOD_TOOL_OUT", HERE))
TARGET = (ROOT / "content/skills/herblore/src/main/kotlin/org/rsmod/content/skills/herblore/configs/MinigamePotionObjs.kt")

# kotlin name, cache name without the (n), variant (0 for the lowest ids), doses if not four
CHAINS = [
    # Copies of the potions Herblore makes that other content hands out.
    ("ba_prayer", "Prayer potion", 1),
    ("ba_ranging", "Ranging potion", 1),
    ("ba_sanfew", "Sanfew serum", 1),
    ("ba_saradomin_brew", "Saradomin brew", 1),
    ("ba_stamina", "Stamina potion", 1),
    ("ba_super_combat", "Super combat potion", 1),
    ("ba_super_energy", "Super energy", 1),
    ("ba_super_restore", "Super restore", 1),
    ("deadman_combat", "Combat potion", 1),
    ("blighted_super_restore", "Blighted super restore", 0),
    ("blighted_overload", "Blighted overload", 0),
    # Nightmare Zone, the Gauntlet and the potions that were never in Herblore's scope.
    ("overload", "Overload", 0),
    ("raids_overload_mid", "Overload", 1),
    ("super_ranging", "Super ranging", 0),
    ("super_magic", "Super magic potion", 0),
    ("bastion", "Bastion potion", 0),
    ("battlemage", "Battlemage potion", 0),
    ("divine_bastion", "Divine bastion potion", 0),
    ("divine_battlemage", "Divine battlemage potion", 0),
    ("magic_essence", "Magic essence", 0),
    ("egniol", "Egniol potion", 0),
    ("sanfew", "Sanfew serum", 0),
    ("antidote_plus", "Antidote+", 0),
    ("antidote_plus_plus", "Antidote++", 0),
    ("anti_venom", "Anti-venom", 0),
    ("anti_venom_plus", "Anti-venom+", 0),
    ("ancient_brew", "Ancient brew", 0),
    ("forgotten_brew", "Forgotten brew", 0),
    ("menaphite_remedy", "Menaphite remedy", 0),
    ("guthix_rest", "Guthix rest", 0),
    ("nectar", "Nectar", 0),
    ("ambrosia", "Ambrosia", 0, 2),
    # Chambers of Xeric: weak (-), normal and strong (+) gourd potions.
    ("elder_weak", "Elder (-)", 0),
    ("elder", "Elder potion", 0),
    ("elder_strong", "Elder (+)", 0),
    ("twisted_weak", "Twisted (-)", 0),
    ("twisted", "Twisted potion", 0),
    ("twisted_strong", "Twisted (+)", 0),
    ("kodai_weak", "Kodai (-)", 0),
    ("kodai", "Kodai potion", 0),
    ("kodai_strong", "Kodai (+)", 0),
    ("revitalisation_weak", "Revitalisation (-)", 0),
    ("revitalisation", "Revitalisation potion", 0),
    ("revitalisation_strong", "Revitalisation (+)", 0),
    ("prayer_enhance_weak", "Prayer enhance (-)", 0),
    ("prayer_enhance", "Prayer enhance", 0),
    ("prayer_enhance_strong", "Prayer enhance (+)", 0),
    ("xerics_aid_weak", "Xeric's aid (-)", 0),
    ("xerics_aid", "Xeric's aid", 0),
    ("xerics_aid_strong", "Xeric's aid (+)", 0),
    ("raids_overload_weak", "Overload (-)", 0),
    ("raids_overload_strong", "Overload (+)", 0),
    ("raids_antipoison_weak", "Antipoison (-)", 0),
    ("raids_antipoison", "Antipoison potion", 0),
    ("raids_antipoison_strong", "Antipoison (+)", 0),
]

# Barbarian mixes: two doses each.
MIXES = [
    ("attack_mix", "Attack mix"),
    ("antipoison_mix", "Antipoison mix"),
    ("relicyms_mix", "Relicym's mix"),
    ("strength_mix", "Strength mix"),
    ("combat_mix", "Combat mix"),
    ("restore_mix", "Restore mix"),
    ("energy_mix", "Energy mix"),
    ("defence_mix", "Defence mix"),
    ("agility_mix", "Agility mix"),
    ("prayer_mix", "Prayer mix"),
    ("super_attack_mix", "Superattack mix"),
    ("super_antipoison_mix", "Anti-poison supermix"),
    ("fishing_mix", "Fishing mix"),
    ("super_energy_mix", "Super energy mix"),
    ("super_strength_mix", "Super str. mix"),
    ("magic_essence_mix", "Magic essence mix"),
    ("super_restore_mix", "Super restore mix"),
    ("super_defence_mix", "Super def. mix"),
    ("antidote_plus_mix", "Antidote+ mix"),
    ("antifire_mix", "Antifire mix"),
    ("ranging_mix", "Ranging mix"),
    ("magic_mix", "Magic mix"),
    ("hunting_mix", "Hunting mix"),
    ("zamorak_mix", "Zamorak mix"),
    ("extended_antifire_mix", "Extended antifire mix"),
    ("stamina_mix", "Stamina mix"),
    ("ancient_mix", "Ancient mix"),
    ("super_antifire_mix", "Super antifire mix"),
    ("extended_super_antifire_mix", "Extended super antifire mix"),
]


def main():
    objs = json.load(open(OUT / "objs.json"))
    groups = defaultdict(lambda: defaultdict(list))
    for o in objs:
        if o["noted"] or o["placeholder"] or not o["name"] or o["iop"][0] != "Drink":
            continue
        m = re.match(r"^(.*)\((\d)\)$", o["name"])
        if m:
            groups[m.group(1).rstrip()][int(m.group(2))].append(o)

    def chain(base, variant, doses):
        got = groups.get(base)
        if not got:
            raise SystemExit(f"no potion called {base!r}")
        syms = []
        for n in range(doses, 0, -1):
            found = sorted(got[n], key=lambda o: o["id"])
            if variant >= len(found):
                raise SystemExit(f"{base!r} has no variant {variant} at {n} doses")
            syms.append(found[variant]["sym"])
        return syms

    lines = [
        "// Generated by tools/food/gen_potion_refs.py from the cache. Do not edit by hand.",
        "package org.rsmod.content.skills.herblore.configs",
        "",
        "import org.rsmod.api.type.refs.obj.ObjReferences",
        "import org.rsmod.game.type.obj.ObjType",
        "",
        "/**",
        " * The dose objs of the potions that Herblore does not make but that content such as minigames",
        " * and bosses hands out. A potion is a list of its dose objs, the fullest first.",
        " */",
        "internal object MinigamePotionObjs : ObjReferences() {",
        "    private fun doses(vararg names: String): List<ObjType> = names.map { find(it) }",
        "",
    ]
    for kt, base, variant, *rest in CHAINS:
        syms = chain(base, variant, rest[0] if rest else 4)
        lines.append(f"    val {kt} =")
        lines.append("        doses(" + ", ".join(f'"{s}"' for s in syms) + ")")
    lines.append("")
    for kt, base in MIXES:
        syms = chain(base, 0, 2)
        lines.append(f"    val {kt} = doses(" + ", ".join(f'"{s}"' for s in syms) + ")")
    lines.append("")
    lines.append('    /** What a cup of Guthix rest leaves behind. */')
    lines.append('    val cup_empty = find("cup_empty")')
    lines.append("}")
    text = "\n".join(lines) + "\n"
    TARGET.write_text(text.replace("\n", "\r\n"), encoding="utf-8", newline="")
    print(len(CHAINS) + len(MIXES), "chains ->", TARGET)


if __name__ == "__main__":
    main()
