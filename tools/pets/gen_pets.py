#!/usr/bin/env python3
"""Generates the isle's pet catalog (`pets/PetCatalog.kt`) from the cache and the table below.

    python -I tools/pets/cachepets.py > tools/pets/cache_pets.json
    python -I tools/pets/gen_pets.py

Every pet in the collection log's "All Pets" page is listed here in the order of enum 985 (the pet
insurance list: its index is the pet's bit in the `pet_insurance_*` bitmasks). Each pet has its
forms: the item and follower npc of every look it can take, by cache symbol. The script checks that
every item exists, that every npc is a follower in the cache, and that the first item of each pet
is the one enum 985 names.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/slayer"))
import enums  # noqa: E402

OUT = (ROOT / "content/areas/misc/isle-of-souls/src/main/kotlin/org/rsmod/content/areas/misc"
       / "isleofsouls/pets/PetCatalog.kt")

# (key, name, [(obj symbol, npc symbol, form label), ...]); the first form is the one the pet is
# received as. Order = enum 985.
PETS = [
    ("chaos_elemental", "Pet chaos elemental", [("chaoselepet", "chaos_elemental_pet", "")]),
    ("dagannoth_supreme", "Pet dagannoth supreme", [("supremepet", "supreme_pet", "")]),
    ("dagannoth_prime", "Pet dagannoth prime", [("primepet", "prime_pet", "")]),
    ("dagannoth_rex", "Pet dagannoth rex", [("rexpet", "rex_pet", "")]),
    ("penance_queen", "Pet penance queen", [("penancepet", "penance_pet", "")]),
    ("kreearra", "Pet kree'arra", [("armadylpet", "armadyl_pet", "")]),
    ("general_graardor", "Pet general graardor", [("bandospet", "bandos_pet", "")]),
    ("zilyana", "Pet zilyana", [("saradominpet", "saradomin_pet", "")]),
    ("kril_tsutsaroth", "Pet k'ril tsutsaroth", [("zamorakpet", "zamorak_pet", "")]),
    ("baby_mole", "Baby mole", [
        ("molepet", "mole_pet", "mole"),
        ("molepet_naked", "mole_pet_naked", "mole-rat"),
    ]),
    ("prince_black_dragon", "Prince black dragon", [("kbdpet", "kbd_pet", "")]),
    ("kalphite_princess", "Kalphite princess", [
        ("kqpet_walking", "kq_pet_walking", "walking"),
        ("kqpet_flying", "kq_pet_flying", "airborne"),
    ]),
    ("smoke_devil", "Pet smoke devil", [
        ("smokepet", "smoke_pet", "thermonuclear"),
        ("smokepet_old", "smoke_pet_old", "normal"),
    ]),
    ("kraken", "Pet kraken", [("krakenpet", "kraken_pet", "")]),
    ("dark_core", "Pet dark core", [
        ("corepet", "core_pet", "dark core"),
        ("corppet", "corp_pet", "corporeal critter"),
    ]),
    ("snakeling", "Pet snakeling", [
        ("snakepet", "snake_pet_green", "green"),
        ("snakepet_orange", "snake_pet_orange", "magma"),
        ("snakepet_blue", "snake_pet_blue", "tanzanite"),
    ]),
    ("chompy_chick", "Chompy chick", [("chompybird_pet", "chompy_bird_pet", "")]),
    ("venenatis_spiderling", "Venenatis spiderling", [
        ("venenatis_pet", "venenatispet", "regular"),
        ("venenatis_pet_legacy", "venenatispet_legacy", "legacy"),
    ]),
    ("callisto_cub", "Callisto cub", [
        ("callisto_pet", "callistopet", "regular"),
        ("callisto_pet_legacy", "callistopet_legacy", "legacy"),
    ]),
    ("vetion_jr", "Vet'ion jr.", [
        ("vetion_pet", "vetionpet", "normal"),
        ("vetion_pet2", "vetionpet_2", "reborn"),
        ("vetion_pet_legacy", "vetionpet_legacy", "normal legacy"),
        ("vetion_pet2_legacy", "vetionpet_2_legacy", "reborn legacy"),
    ]),
    ("scorpias_offspring", "Scorpia's offspring", [("scorpia_pet", "scorpiapet", "")]),
    ("tzrek_jad", "Tzrek-jad", [
        ("jad_pet", "jadpet", "tzrek-jad"),
        ("jad_pet_inferno", "jadpet_inferno", "jalrek-jad"),
    ]),
    ("hellpuppy", "Hellpuppy", [("hell_pet", "hellpet", "")]),
    ("abyssal_orphan", "Abyssal orphan", [("abyssalsire_pet", "abyssalsire_pet", "")]),
    ("heron", "Heron", [
        ("skillpetfish", "skillpet_fish", "heron"),
        ("skillpetfish_tempoross", "skillpet_fish_tempoross", "great blue heron"),
    ]),
    ("rock_golem", "Rock golem", [
        ("skillpetmining", "skillpet_mining_default", "rock"),
        ("skillpetmining_tin", "skillpet_mining_tin", "tin"),
        ("skillpetmining_copper", "skillpet_mining_copper", "copper"),
        ("skillpetmining_iron", "skillpet_mining_iron", "iron"),
        ("skillpetmining_blurite", "skillpet_mining_blurite", "blurite"),
        ("skillpetmining_silver", "skillpet_mining_silver", "silver"),
        ("skillpetmining_coal", "skillpet_mining_coal", "coal"),
        ("skillpetmining_gold", "skillpet_mining_gold", "gold"),
        ("skillpetmining_mithril", "skillpet_mining_mithril", "mithril"),
        ("skillpetmining_granite", "skillpet_mining_granite", "granite"),
        ("skillpetmining_adamantite", "skillpet_mining_adamantite", "adamantite"),
        ("skillpetmining_runite", "skillpet_mining_runite", "runite"),
        ("skillpetmining_amethyst", "skillpet_mining_amethyst", "amethyst"),
        ("skillpetmining_lovakite", "skillpet_mining_lovakite", "lovakite"),
        ("skillpetmining_elemental", "skillpet_mining_elemental", "elemental"),
        ("skillpetmining_daeyalt", "skillpet_mining_daeyalt", "daeyalt"),
    ]),
    ("beaver", "Beaver", [
        ("skillpetwc", "skillpetwc", "normal"),
        ("skillpet_wc_oak", "skillpet_wc_oak", "oak"),
        ("skillpet_wc_willow", "skillpet_wc_willow", "willow"),
        ("skillpet_wc_teak", "skillpet_wc_teak", "teak"),
        ("skillpet_wc_maple", "skillpet_wc_maple", "maple"),
        ("skillpet_wc_mahogany", "skillpet_wc_mahogany", "mahogany"),
        ("skillpet_wc_arctic", "skillpet_wc_arctic", "arctic pine"),
        ("skillpet_wc_yew", "skillpet_wc_yew", "yew"),
        ("skillpet_wc_magic", "skillpet_wc_magic", "magic"),
        ("skillpet_wc_redwood", "skillpet_wc_redwood", "redwood"),
        ("skillpet_wc_pheasant", "skillpet_wc_pheasant", "pheasant"),
        ("skillpet_wc_fox", "skillpet_wc_fox", "fox"),
    ]),
    ("baby_chinchompa", "Baby chinchompa", [
        ("skillpethunter_red", "skillpet_hunter_red", "red"),
        ("skillpethunter_grey", "skillpet_hunter_grey", "grey"),
        ("skillpethunter_black", "skillpet_hunter_black", "black"),
        ("skillpethunter_gold", "skillpet_hunter_gold", "gold"),
    ]),
    ("bloodhound", "Bloodhound", [("bloodhound_pet", "bloodhoundpet", "")]),
    ("giant_squirrel", "Giant squirrel", [
        ("skillpetagility", "skillpet_agility", "giant"),
        ("skillpetagility_dark", "skillpet_agility_dark", "dark"),
        ("skillpetagility_bone", "skillpet_agility_bone", "bone"),
    ]),
    ("tangleroot", "Tangleroot", [
        ("skillpetfarming", "skillpet_farming", "normal"),
        ("skillpetfarming_crystal", "skillpet_farming_crystal", "crystal"),
        ("skillpetfarming_dragon", "skillpet_farming_dragon", "dragonfruit"),
        ("skillpetfarming_herb", "skillpet_farming_herb", "herb"),
        ("skillpetfarming_lily", "skillpet_farming_lily", "white lily"),
        ("skillpetfarming_redwood", "skillpet_farming_redwood", "redwood"),
    ]),
    ("rift_guardian", "Rift guardian", [
        ("skillpetrunecrafting_fire", "skillpet_runecrafting_fire", "fire"),
        ("skillpetrunecrafting_air", "skillpet_runecrafting_air", "air"),
        ("skillpetrunecrafting_mind", "skillpet_runecrafting_mind", "mind"),
        ("skillpetrunecrafting_water", "skillpet_runecrafting_water", "water"),
        ("skillpetrunecrafting_earth", "skillpet_runecrafting_earth", "earth"),
        ("skillpetrunecrafting_body", "skillpet_runecrafting_body", "body"),
        ("skillpetrunecrafting_cosmic", "skillpet_runecrafting_cosmic", "cosmic"),
        ("skillpetrunecrafting_chaos", "skillpet_runecrafting_chaos", "chaos"),
        ("skillpetrunecrafting_nature", "skillpet_runecrafting_nature", "nature"),
        ("skillpetrunecrafting_law", "skillpet_runecrafting_law", "law"),
        ("skillpetrunecrafting_death", "skillpet_runecrafting_death", "death"),
        ("skillpetrunecrafting_soul", "skillpet_runecrafting_soul", "soul"),
        ("skillpetrunecrafting_astral", "skillpet_runecrafting_astral", "astral"),
        ("skillpetrunecrafting_blood", "skillpet_runecrafting_blood", "blood"),
        ("skillpetrunecrafting_wrath", "skillpet_runecrafting_wrath", "wrath"),
        ("skillpetrunecrafting_gotr", "skillpet_runecrafting_gotr", "greatish guardian"),
    ]),
    ("rocky", "Rocky", [
        ("skillpetthieving", "skillpet_thieving", "raccoon"),
        ("skillpetthieving_panda", "skillpet_thieving_panda", "red panda"),
        ("skillpetthieving_tanuki", "skillpet_thieving_tanuki", "tanuki"),
    ]),
    ("phoenix", "Phoenix", [
        ("phoenixpet", "phoenix_pet", "regular"),
        ("phoenixpet_blue", "phoenix_pet_blue", "blue"),
        ("phoenixpet_green", "phoenix_pet_green", "green"),
        ("phoenixpet_purple", "phoenix_pet_purple", "purple"),
        ("phoenixpet_white", "phoenix_pet_white", "white"),
    ]),
    ("olmlet", "Olmlet", [
        ("olmpet", "raids_olm_pet", "olmlet"),
        ("dogadilepet", "dogadile_pet", "puppadile"),
        ("tektonpet", "tekton_pet", "tektiny"),
        ("tektonenragedpet", "tekton_enraged_pet", "enraged tektiny"),
        ("vanguardpet", "vanguard_pet", "vanguard"),
        ("vasapet", "vasa_pet", "vasa minirio"),
        ("vespulapet", "vespula_pet", "vespina"),
        ("vespulaflyingpet", "vespula_flying_pet", "flying vespina"),
    ]),
    ("skotos", "Skotos", [("skotizopet", "skotizo_pet", "")]),
    ("jal_nib_rek", "Jal-nib-rek", [
        ("infernopet", "inferno_pet", "jal-nib-rek"),
        ("infernopet_zuk", "zuk_pet", "tzrek-zuk"),
    ]),
    ("herbi", "Herbi", [("herbiboarpet", "herbiboar_pet", "")]),
    ("noon", "Noon", [
        ("dawnpet", "dawn_pet", "noon"),
        ("duskpet", "dusk_pet", "midnight"),
    ]),
    ("vorki", "Vorki", [("vorkathpet", "vorkath_pet", "")]),
    ("lil_zik", "Lil' zik", [
        ("verzikpet", "verzik_pet", "lil' zik"),
        ("maidenpet", "verzik_pet_maiden", "lil' maiden"),
        ("bloatpet", "verzik_pet_bloat", "lil' bloat"),
        ("nylocaspet", "verzik_pet_nylocas", "lil' nylo"),
        ("sotetsegpet", "verzik_pet_sotetseg", "lil' sot"),
        ("xarpuspet", "verzik_pet_xarpus", "lil' xarp"),
    ]),
    ("ikkle_hydra", "Ikkle hydra", [
        ("hydrapet", "hydra_pet", "serpentine"),
        ("hydrapet_electric", "hydra_pet_electric", "electric"),
        ("hydrapet_fire", "hydra_pet_fire", "fire"),
        ("hydrapet_extinguished", "hydra_pet_extinguished", "extinguished"),
    ]),
    ("sraracha", "Sraracha", [
        ("sarachnispet", "sarachnispet", "red"),
        ("sarachnispet_orange", "sarachnispet_orange", "orange"),
        ("sarachnispet_blue", "sarachnispet_blue", "blue"),
    ]),
    ("youngllef", "Youngllef", [
        ("gauntletpet", "gauntlet_pet", "youngllef"),
        ("gauntletpet_corrupt", "gauntlet_pet_corrupt", "corrupted youngllef"),
    ]),
    ("smolcano", "Smolcano", [("zalcanopet", "zalcano_pet", "")]),
    ("little_nightmare", "Little nightmare", [
        ("nightmarepet", "nightmare_pet", "little nightmare"),
        ("nightmarepet_parasite", "nightmare_pet_parasite", "little parasite"),
    ]),
    ("lil_creator", "Lil' creator", [
        ("soulwarspet_blue", "soulwars_pet_blue", "lil' creator"),
        ("soulwarspet_red", "soulwars_pet_red", "lil' destructor"),
    ]),
    ("tiny_tempor", "Tiny tempor", [("temporosspet", "tempoross_pet", "")]),
    ("nexling", "Nexling", [("nexpet", "nex_pet", "")]),
    ("abyssal_protector", "Abyssal protector", [("abyssalpet", "abyssal_pet", "")]),
    ("tumekens_guardian", "Tumeken's guardian", [
        ("wardenpet_tumeken", "warden_pet_tumeken", "tumeken's guardian"),
        ("wardenpet_elidinis", "warden_pet_elidinis", "elidinis' guardian"),
        ("wardenpet_akkha", "warden_pet_akkha", "akkhito"),
        ("wardenpet_baba", "warden_pet_baba", "babi"),
        ("wardenpet_kephri", "warden_pet_kephri", "kephriti"),
        ("wardenpet_zebak", "warden_pet_zebak", "zebo"),
        ("wardenpet_tumeken_destroyed", "warden_pet_tumeken_destroyed",
         "tumeken's damaged guardian"),
        ("wardenpet_elidinis_destroyed", "warden_pet_elidinis_destroyed",
         "elidinis' damaged guardian"),
    ]),
    ("muphin", "Muphin", [
        ("muspahpet", "muspah_pet", "ranged"),
        ("muspahpet_melee", "muspah_pet_melee", "melee"),
        ("muspahpet_shielded", "muspah_pet_shielded", "shielded"),
    ]),
    ("wisp", "Wisp", [("whispererpet", "whisperer_pet", "")]),
    ("butch", "Butch", [("vardorvispet", "vardorvis_pet", "")]),
    ("baron", "Baron", [("dukesucelluspet", "duke_sucellus_pet", "")]),
    ("lilviathan", "Lil'viathan", [("leviathanpet", "leviathan_pet", "")]),
    ("scurry", "Scurry", [("scurriuspet", "scurrius_pet", "")]),
    ("smol_heredit", "Smol heredit", [("solhereditpet", "solheredit_pet", "")]),
    ("quetzin", "Quetzin", [("quetzalpet", "quetzal_pet", "")]),
    ("nid", "Nid", [
        ("araxxorpet", "araxxor_pet", "nid"),
        ("araxxorpet_cute", "araxxor_pet_cute", "rax"),
    ]),
    ("huberte", "Huberte", [("hueypet", "huey_pet", "")]),
    ("moxi", "Moxi", [("amoxliatlpet", "amoxliatl_pet", "")]),
    ("bran", "Bran", [
        ("rtbrandapet", "rtbranda_pet", "bran"),
        ("rteldricpet", "rteldric_pet", "ric"),
    ]),
    ("yami", "Yami", [("yamapet", "yama_pet", "")]),
    ("dom", "Dom", [("dompet", "dom_pet", "")]),
]


def syms(name):
    out = {}
    for line in (ROOT / f".data/symbols/{name}.sym").read_text(encoding="utf-8").splitlines():
        p = line.split("\t")
        if len(p) == 2 and p[0].isdigit():
            out[p[1]] = int(p[0])
    return out


def kotlin_name(sym):
    return sym if sym[0].isalpha() else "_" + sym


def main():
    data = json.loads((ROOT / "tools/pets/cache_pets.json").read_text(encoding="utf-8"))
    followers = {n["sym"]: n for n in data["followers"].values()}
    obj_ids, npc_ids = syms("obj"), syms("npc")
    insurance = enums.read_enum(985)
    if len(insurance) != len(PETS):
        raise SystemExit(f"enum 985 lists {len(insurance)} pets, the table {len(PETS)}")

    errors = []
    for bit, (key, _, forms) in enumerate(PETS):
        if obj_ids.get(forms[0][0]) != insurance[bit]:
            errors.append(f"{key}: bit {bit} is obj {insurance[bit]}, not {forms[0][0]}")
        for obj, npc, _ in forms:
            if obj not in obj_ids:
                errors.append(f"{key}: no obj `{obj}`")
            if npc not in followers:
                errors.append(f"{key}: `{npc}` is not a follower npc")
    if errors:
        raise SystemExit("\n".join(errors))

    lines = [
        "package org.rsmod.content.areas.misc.isleofsouls.pets",
        "",
        "import org.rsmod.api.type.refs.npc.NpcReferences",
        "import org.rsmod.api.type.refs.obj.ObjReferences",
        "",
        "// GENERATED by tools/pets/gen_pets.py from the cache (enum 985, follower npcs). Do not",
        "// hand-edit: change the table in the script and run it again.",
        "",
        "object PetObjs : ObjReferences() {",
    ]
    seen = set()
    for _, _, forms in PETS:
        for obj, _, _ in forms:
            if obj not in seen:
                seen.add(obj)
                lines.append(f'    val {kotlin_name(obj)} = find("{obj}")')
    lines += ["}", "", "object PetNpcs : NpcReferences() {"]
    seen = set()
    for _, _, forms in PETS:
        for _, npc, _ in forms:
            if npc not in seen:
                seen.add(npc)
                lines.append(f'    val {kotlin_name(npc)} = find("{npc}")')
    lines += [
        "}",
        "",
        "/** Every pet in the collection log, in the order of the pet insurance enum (985). */",
        "object PetCatalog {",
        "    val pets: List<Pet> =",
        "        listOf(",
    ]
    for bit, (key, name, forms) in enumerate(PETS):
        lines.append(f'            Pet(')
        lines.append(f'                key = "{key}",')
        lines.append(f"                bit = {bit},")
        lines.append(f'                name = "{name}",')
        lines.append("                forms =")
        lines.append("                    listOf(")
        for obj, npc, label in forms:
            ops = followers[npc]["ops"]
            op_args = ", ".join(f'{int(i) + 1} to "{t}"' for i, t in sorted(ops.items()))
            lines.append(
                f"                        PetForm(PetObjs.{kotlin_name(obj)}, "
                f'PetNpcs.{kotlin_name(npc)}, "{label}", mapOf({op_args})),'
            )
        lines.append("                    ),")
        lines.append("            ),")
    lines += ["        )", "}", ""]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\r\n".join(lines), encoding="utf-8", newline="")
    print(f"wrote {OUT} ({len(PETS)} pets)")


if __name__ == "__main__":
    main()
