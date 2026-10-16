package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.type.obj.ObjType

typealias lunar_objs = LunarObjs

typealias lunar_seqs = LunarSeqs

typealias lunar_spotanims = LunarSpotanims

/** What the Lunar item spells use or make. The names are the internal obj names. */
object LunarObjs : ObjReferences() {
    val uncooked_redberry_pie = find("uncooked_redberry_pie")
    val redberry_pie = find("redberry_pie")
    val uncooked_meat_pie = find("uncooked_meat_pie")
    val meat_pie = find("meat_pie")
    val uncooked_mud_pie = find("uncooked_mud_pie")
    val mud_pie = find("mud_pie")
    val uncooked_apple_pie = find("uncooked_apple_pie")
    val apple_pie = find("apple_pie")
    val uncooked_garden_pie = find("uncooked_garden_pie")
    val garden_pie = find("garden_pie")
    val uncooked_fish_pie = find("uncooked_fish_pie")
    val fish_pie = find("fish_pie")
    val uncooked_admiral_pie = find("uncooked_admiral_pie")
    val admiral_pie = find("admiral_pie")
    val uncooked_wild_pie = find("uncooked_wild_pie")
    val wild_pie = find("wild_pie")
    val uncooked_summer_pie = find("uncooked_summer_pie")
    val summer_pie = find("summer_pie")

    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val teak_logs = find("teak_logs")
    val mahogany_logs = find("mahogany_logs")
    val plank = find("woodplank")
    val oak_plank = find("plank_oak")
    val teak_plank = find("plank_teak")
    val mahogany_plank = find("plank_mahogany")

    val seaweed = find("seaweed")
    val giant_seaweed = find("giant_seaweed")
    val soda_ash = find("soda_ash")
    val bucket_sand = find("bucket_sand")
    val molten_glass = find("molten_glass")

    val cow_hide = find("cow_hide")
    val leather = find("leather")
    val green_dragonhide = find("dragonhide_green")
    val blue_dragonhide = find("dragonhide_blue")
    val red_dragonhide = find("dragonhide_red")
    val black_dragonhide = find("dragonhide_black")
    val green_dragon_leather = find("dragon_leather")
    val blue_dragon_leather = find("dragon_leather_blue")
    val red_dragon_leather = find("dragon_leather_red")
    val black_dragon_leather = find("dragon_leather_black")

    val flax = find("flax")
    val bow_string = find("bow_string")

    val vial_empty = find("vial_empty")
    val vial_water = find("vial_water")
    val bucket_empty = find("bucket_empty")
    val bucket_water = find("bucket_water")
    val jug_empty = find("jug_empty")
    val jug_water = find("jug_water")
    val bowl_empty = find("bowl_empty")
    val bowl_water = find("bowl_water")
    val fishbowl_empty = find("fishbowl_empty")
    val fishbowl_water = find("fishbowl_water")
    val clay = find("clay")
    val soft_clay = find("softclay")

    val amulet_of_glory = find("amulet_of_glory")
    val amulet_of_glory_1 = find("amulet_of_glory_1")
    val amulet_of_glory_2 = find("amulet_of_glory_2")
    val amulet_of_glory_3 = find("amulet_of_glory_3")
    val amulet_of_glory_4 = find("amulet_of_glory_4")
    val combat_bracelet = find("jewl_bracelet_of_combat")
    val combat_bracelet_1 = find("jewl_bracelet_of_combat_1")
    val combat_bracelet_2 = find("jewl_bracelet_of_combat_2")
    val combat_bracelet_3 = find("jewl_bracelet_of_combat_3")
    val combat_bracelet_4 = find("jewl_bracelet_of_combat_4")
    val skills_necklace = find("jewl_necklace_of_skills")
    val skills_necklace_1 = find("jewl_necklace_of_skills_1")
    val skills_necklace_2 = find("jewl_necklace_of_skills_2")
    val skills_necklace_3 = find("jewl_necklace_of_skills_3")
    val skills_necklace_4 = find("jewl_necklace_of_skills_4")

    val unstrung_gold_amulet = find("unstrung_gold_amulet")
    val strung_gold_amulet = find("strung_gold_amulet")
    val unstrung_sapphire_amulet = find("unstrung_sapphire_amulet")
    val strung_sapphire_amulet = find("strung_sapphire_amulet")
    val unstrung_emerald_amulet = find("unstrung_emerald_amulet")
    val strung_emerald_amulet = find("strung_emerald_amulet")
    val unstrung_ruby_amulet = find("unstrung_ruby_amulet")
    val strung_ruby_amulet = find("strung_ruby_amulet")
    val unstrung_diamond_amulet = find("unstrung_diamond_amulet")
    val strung_diamond_amulet = find("strung_diamond_amulet")
    val unstrung_dragonstone_amulet = find("unstrung_dragonstone_amulet")
    val strung_dragonstone_amulet = find("strung_dragonstone_amulet")
    val unstrung_onyx_amulet = find("unstrung_onyx_amulet")
    val strung_onyx_amulet = find("strung_onyx_amulet")
    val unstrung_zenyte_amulet = find("unstrung_zenyte_amulet")
    val strung_zenyte_amulet = find("zenyte_amulet")
    val unstrung_opal_amulet = find("unstrung_opal_amulet")
    val strung_opal_amulet = find("strung_opal_amulet")
    val unstrung_jade_amulet = find("unstrung_jade_amulet")
    val strung_jade_amulet = find("strung_jade_amulet")
    val unstrung_topaz_amulet = find("unstrung_topaz_amulet")
    val strung_topaz_amulet = find("strung_topaz_amulet")
    val unstrung_symbol = find("nostringstar")
    val strung_symbol = find("stringstar")
    val unstrung_emblem = find("nostringsnake")
    val strung_emblem = find("stringsnake")
}

object LunarSeqs : SeqReferences() {
    /** The cast most Lunar spells use. */
    val cast = find("lunar_human_magic_summon2")
    val cast_self = find("lunar_human_magic_summon1")
    val cast_group = find("quest_lunar_spell_cast_spell_on_group")
    val cast_energy = find("quest_lunar_spell_energy_exchange")
    val humidify = find("dream_player_humidify_spell")
    val plank_make = find("dream_player_make_plank_spell")
    val spellbook_swap = find("dream_player_spellbook_swap")
    val imbue = find("human_cast_selfimbue")
}

object LunarSpotanims : SpotanimReferences() {
    val bake_pie = find("quest_lunar_bakepie_spotanim")
    val flax = find("quest_lunar_magic_flax_strim")
    val superglass = find("quest_lunar_magic_superglass")
    val string_jewellery = find("quest_lunar_spellbook_string_jewelery_spot_anim")
    val tan_leather = find("lunar_tan_leather")
    val recharge_dragonstone = find("lunar_enchant_dragonstone")
    val humidify = find("dream_humidify_spell_spotanim")
    val plank_make = find("dream_plank_make_spell_spotanim")
    val spellbook_swap = find("dream_spellbook_spotanim")
    val vengeance = find("quest_lunar_spellbook_vengeance_spot_anim")
    val vengeance_other = find("quest_lunar_spellbook_vengeance_other_spot_anim")
    val heal_group = find("quest_lunar_spellbook_heal_group_spot_anim")
    val energy_transfer = find("quest_lunar_spellbook_energy_heal_spot_anim")
}

/** A raw pie, the pie it bakes into, and the Cooking level and experience for it. */
class PieRecipe(val raw: ObjType, val cooked: ObjType, val level: Int, val xp: Double)

/** A log, the plank it is sawn into and the coins Plank Make charges. */
class PlankRecipe(val log: ObjType, val plank: ObjType, val cost: Int)

/** A hide and the leather Tan Leather makes of it. */
class HideRecipe(val hide: ObjType, val leather: ObjType)

/** An empty container and its full form, for Humidify. */
class FillRecipe(val empty: ObjType, val full: ObjType)

/** Dragonstone jewellery that [items] (from empty up) are topped up to [full]. */
class RechargeRecipe(val items: List<ObjType>, val full: ObjType)

object LunarRecipes {
    val pies: List<PieRecipe> =
        listOf(
            PieRecipe(lunar_objs.uncooked_redberry_pie, lunar_objs.redberry_pie, 10, 78.0),
            PieRecipe(lunar_objs.uncooked_meat_pie, lunar_objs.meat_pie, 20, 110.0),
            PieRecipe(lunar_objs.uncooked_mud_pie, lunar_objs.mud_pie, 29, 128.0),
            PieRecipe(lunar_objs.uncooked_apple_pie, lunar_objs.apple_pie, 30, 130.0),
            PieRecipe(lunar_objs.uncooked_garden_pie, lunar_objs.garden_pie, 34, 138.0),
            PieRecipe(lunar_objs.uncooked_fish_pie, lunar_objs.fish_pie, 47, 164.0),
            PieRecipe(lunar_objs.uncooked_admiral_pie, lunar_objs.admiral_pie, 70, 210.0),
            PieRecipe(lunar_objs.uncooked_wild_pie, lunar_objs.wild_pie, 85, 240.0),
            PieRecipe(lunar_objs.uncooked_summer_pie, lunar_objs.summer_pie, 95, 260.0),
        )

    val planks: List<PlankRecipe> =
        listOf(
            PlankRecipe(lunar_objs.logs, lunar_objs.plank, 70),
            PlankRecipe(lunar_objs.oak_logs, lunar_objs.oak_plank, 175),
            PlankRecipe(lunar_objs.teak_logs, lunar_objs.teak_plank, 350),
            PlankRecipe(lunar_objs.mahogany_logs, lunar_objs.mahogany_plank, 1_050),
        )

    val hides: List<HideRecipe> =
        listOf(
            HideRecipe(lunar_objs.cow_hide, lunar_objs.leather),
            HideRecipe(lunar_objs.green_dragonhide, lunar_objs.green_dragon_leather),
            HideRecipe(lunar_objs.blue_dragonhide, lunar_objs.blue_dragon_leather),
            HideRecipe(lunar_objs.red_dragonhide, lunar_objs.red_dragon_leather),
            HideRecipe(lunar_objs.black_dragonhide, lunar_objs.black_dragon_leather),
        )

    val fills: List<FillRecipe> =
        listOf(
            FillRecipe(lunar_objs.vial_empty, lunar_objs.vial_water),
            FillRecipe(lunar_objs.bucket_empty, lunar_objs.bucket_water),
            FillRecipe(lunar_objs.jug_empty, lunar_objs.jug_water),
            FillRecipe(lunar_objs.bowl_empty, lunar_objs.bowl_water),
            FillRecipe(lunar_objs.fishbowl_empty, lunar_objs.fishbowl_water),
            FillRecipe(lunar_objs.clay, lunar_objs.soft_clay),
        )

    /** Unstrung amulets (and the symbols and emblems) and the strung form of each. */
    val strings: List<Pair<ObjType, ObjType>> =
        listOf(
            lunar_objs.unstrung_gold_amulet to lunar_objs.strung_gold_amulet,
            lunar_objs.unstrung_sapphire_amulet to lunar_objs.strung_sapphire_amulet,
            lunar_objs.unstrung_emerald_amulet to lunar_objs.strung_emerald_amulet,
            lunar_objs.unstrung_ruby_amulet to lunar_objs.strung_ruby_amulet,
            lunar_objs.unstrung_diamond_amulet to lunar_objs.strung_diamond_amulet,
            lunar_objs.unstrung_dragonstone_amulet to lunar_objs.strung_dragonstone_amulet,
            lunar_objs.unstrung_onyx_amulet to lunar_objs.strung_onyx_amulet,
            lunar_objs.unstrung_zenyte_amulet to lunar_objs.strung_zenyte_amulet,
            lunar_objs.unstrung_opal_amulet to lunar_objs.strung_opal_amulet,
            lunar_objs.unstrung_jade_amulet to lunar_objs.strung_jade_amulet,
            lunar_objs.unstrung_topaz_amulet to lunar_objs.strung_topaz_amulet,
            lunar_objs.unstrung_symbol to lunar_objs.strung_symbol,
            lunar_objs.unstrung_emblem to lunar_objs.strung_emblem,
        )

    /** The dragonstone jewellery Recharge Dragonstone fills to four charges. */
    val recharges: List<RechargeRecipe> =
        listOf(
            RechargeRecipe(
                listOf(
                    lunar_objs.amulet_of_glory,
                    lunar_objs.amulet_of_glory_1,
                    lunar_objs.amulet_of_glory_2,
                    lunar_objs.amulet_of_glory_3,
                ),
                lunar_objs.amulet_of_glory_4,
            ),
            RechargeRecipe(
                listOf(
                    lunar_objs.combat_bracelet,
                    lunar_objs.combat_bracelet_1,
                    lunar_objs.combat_bracelet_2,
                    lunar_objs.combat_bracelet_3,
                ),
                lunar_objs.combat_bracelet_4,
            ),
            RechargeRecipe(
                listOf(
                    lunar_objs.skills_necklace,
                    lunar_objs.skills_necklace_1,
                    lunar_objs.skills_necklace_2,
                    lunar_objs.skills_necklace_3,
                ),
                lunar_objs.skills_necklace_4,
            ),
        )
}
