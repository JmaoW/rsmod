package org.rsmod.content.skills.herblore.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.type.obj.ObjType

/**
 * Everything herblore uses or makes. The names are the internal obj names from the cache. A potion
 * is a list of its four dose objs, four doses first.
 */
object HerbloreObjs : ObjReferences() {
    val vial_water = find("vial_water")
    val vial_empty = find("vial_empty")
    val pestle_and_mortar = find("pestle_and_mortar")
    val chisel = find("chisel")

    val grimy_guam = find("unidentified_guam")
    val grimy_marrentill = find("unidentified_marentill")
    val grimy_tarromin = find("unidentified_tarromin")
    val grimy_harralander = find("unidentified_harralander")
    val grimy_ranarr = find("unidentified_ranarr")
    val grimy_toadflax = find("unidentified_toadflax")
    val grimy_irit = find("unidentified_irit")
    val grimy_avantoe = find("unidentified_avantoe")
    val grimy_kwuarm = find("unidentified_kwuarm")
    val grimy_huasca = find("unidentified_huasca")
    val grimy_snapdragon = find("unidentified_snapdragon")
    val grimy_cadantine = find("unidentified_cadantine")
    val grimy_lantadyme = find("unidentified_lantadyme")
    val grimy_dwarf_weed = find("unidentified_dwarf_weed")
    val grimy_torstol = find("unidentified_torstol")

    val guam = find("guam_leaf")
    val marrentill = find("marentill")
    val tarromin = find("tarromin")
    val harralander = find("harralander")
    val ranarr = find("ranarr_weed")
    val toadflax = find("toadflax")
    val irit = find("irit_leaf")
    val avantoe = find("avantoe")
    val kwuarm = find("kwuarm")
    val huasca = find("huasca")
    val snapdragon = find("snapdragon")
    val cadantine = find("cadantine")
    val lantadyme = find("lantadyme")
    val dwarf_weed = find("dwarf_weed")
    val torstol = find("torstol")

    val guam_unf = find("guamvial")
    val marrentill_unf = find("marrentillvial")
    val tarromin_unf = find("tarrominvial")
    val harralander_unf = find("harralandervial")
    val ranarr_unf = find("ranarrvial")
    val toadflax_unf = find("toadflaxvial")
    val irit_unf = find("iritvial")
    val avantoe_unf = find("avantoevial")
    val kwuarm_unf = find("kwuarmvial")
    val huasca_unf = find("huascavial")
    val snapdragon_unf = find("snapdragonvial")
    val cadantine_unf = find("cadantinevial")
    val lantadyme_unf = find("lantadymevial")
    val dwarf_weed_unf = find("dwarfweedvial")
    val torstol_unf = find("torstolvial")

    val eye_of_newt = find("eye_of_newt")
    val unicorn_horn_dust = find("unicorn_horn_dust")
    val limpwurt_root = find("limpwurt_root")
    val red_spiders_eggs = find("red_spiders_eggs")
    val chocolate_dust = find("chocolate_dust")
    val white_berries = find("white_berries")
    val toads_legs = find("toads_legs")
    val goat_horn_dust = find("ground_desert_goat_horn")
    val snape_grass = find("snape_grass")
    val mort_myre_fungus = find("mortmyremushroom")
    val kebbit_teeth_dust = find("huntingbeast_sabreteeth_dust")
    val aldarium = find("aldarium")
    val dragon_scale_dust = find("dragon_scale_dust")
    val wine_of_zamorak = find("wine_of_zamorak")
    val potato_cactus = find("cactus_potato")
    val jangerberries = find("jangerberries")
    val crushed_nest = find("crushed_bird_nest")
    val amylase = find("amylase")
    val lava_shard = find("lava_shard")
    val crystal_dust = find("sote_crystal_dust")

    val unicorn_horn = find("unicorn_horn")
    val desert_goat_horn = find("desert_goat_horn")
    val chocolate_bar = find("chocolate_bar")
    val blue_dragon_scale = find("blue_dragon_scale")
    val lava_scale = find("lava_scale")
    val bird_nest_egg_red = find("bird_nest_egg_red")
    val bird_nest_egg_green = find("bird_nest_egg_green")
    val bird_nest_egg_blue = find("bird_nest_egg_blue")
    val bird_nest_seeds = find("bird_nest_seeds")
    val bird_nest_ring = find("bird_nest_ring")
    val bird_nest_empty = find("bird_nest_empty")

    val attack = doses("4dose1attack", "3dose1attack", "2dose1attack", "1dose1attack")
    val strength = doses("strength4", "3dose1strength", "2dose1strength", "1dose1strength")
    val defence = doses("4dose1defense", "3dose1defense", "2dose1defense", "1dose1defense")
    val restore =
        doses("4dosestatrestore", "3dosestatrestore", "2dosestatrestore", "1dosestatrestore")
    val prayer =
        doses(
            "4doseprayerrestore",
            "3doseprayerrestore",
            "2doseprayerrestore",
            "1doseprayerrestore",
        )
    val super_attack = doses("4dose2attack", "3dose2attack", "2dose2attack", "1dose2attack")
    val super_strength =
        doses("4dose2strength", "3dose2strength", "2dose2strength", "1dose2strength")
    val super_defence = doses("4dose2defense", "3dose2defense", "2dose2defense", "1dose2defense")
    val ranging =
        doses(
            "4doserangerspotion",
            "3doserangerspotion",
            "2doserangerspotion",
            "1doserangerspotion",
        )
    val magic = doses("4dose1magic", "3dose1magic", "2dose1magic", "1dose1magic")
    val antipoison =
        doses("4doseantipoison", "3doseantipoison", "2doseantipoison", "1doseantipoison")
    val super_antipoison =
        doses("4dose2antipoison", "3dose2antipoison", "2dose2antipoison", "1dose2antipoison")
    val energy = doses("4dose1energy", "3dose1energy", "2dose1energy", "1dose1energy")
    val super_energy = doses("4dose2energy", "3dose2energy", "2dose2energy", "1dose2energy")
    val super_restore = doses("4dose2restore", "3dose2restore", "2dose2restore", "1dose2restore")
    val agility = doses("4dose1agility", "3dose1agility", "2dose1agility", "1dose1agility")
    val fishing =
        doses(
            "4dosefisherspotion",
            "3dosefisherspotion",
            "2dosefisherspotion",
            "1dosefisherspotion",
        )
    val hunter = doses("4dosehunting", "3dosehunting", "2dosehunting", "1dosehunting")
    val combat = doses("4dosecombat", "3dosecombat", "2dosecombat", "1dosecombat")
    val super_combat = doses("4dose2combat", "3dose2combat", "2dose2combat", "1dose2combat")
    val antifire =
        doses("4dose1antidragon", "3dose1antidragon", "2dose1antidragon", "1dose1antidragon")
    val extended_antifire =
        doses("4dose2antidragon", "3dose2antidragon", "2dose2antidragon", "1dose2antidragon")
    val saradomin_brew =
        doses(
            "4dosepotionofsaradomin",
            "3dosepotionofsaradomin",
            "2dosepotionofsaradomin",
            "1dosepotionofsaradomin",
        )
    val zamorak_brew =
        doses(
            "4dosepotionofzamorak",
            "3dosepotionofzamorak",
            "2dosepotionofzamorak",
            "1dosepotionofzamorak",
        )
    val stamina = doses("4dosestamina", "3dosestamina", "2dosestamina", "1dosestamina")
    val goading = doses("4dosegoading", "3dosegoading", "2dosegoading", "1dosegoading")
    val prayer_regeneration =
        doses(
            "4dose1prayer_regeneration",
            "3dose1prayer_regeneration",
            "2dose1prayer_regeneration",
            "1dose1prayer_regeneration",
        )
    val divine_super_attack =
        doses("4dosedivineattack", "3dosedivineattack", "2dosedivineattack", "1dosedivineattack")
    val divine_super_strength =
        doses(
            "4dosedivinestrength",
            "3dosedivinestrength",
            "2dosedivinestrength",
            "1dosedivinestrength",
        )
    val divine_super_defence =
        doses(
            "4dosedivinedefence",
            "3dosedivinedefence",
            "2dosedivinedefence",
            "1dosedivinedefence",
        )
    val divine_ranging =
        doses("4dosedivinerange", "3dosedivinerange", "2dosedivinerange", "1dosedivinerange")
    val divine_magic =
        doses("4dosedivinemagic", "3dosedivinemagic", "2dosedivinemagic", "1dosedivinemagic")
    val divine_super_combat =
        doses("4dosedivinecombat", "3dosedivinecombat", "2dosedivinecombat", "1dosedivinecombat")

    private fun doses(four: String, three: String, two: String, one: String): List<ObjType> =
        listOf(find(four), find(three), find(two), find(one))
}

internal object HerbloreSeqs : SeqReferences() {
    /** Putting a herb or a secondary into a vial. */
    val mix = find("human_herbing_vial")

    /** Grinding with a pestle and mortar. */
    val grind = find("human_herbing_grind")

    /** The same animation the food module uses for eating. */
    val drink = find("human_eat")
}

internal object HerbloreQueues : QueueReferences() {
    /** Fires when the next potion of a herblore job is ready to be made. */
    val make = find("herblore_make")
}

internal object HerbloreTimers : TimerReferences() {
    /** Ticks every cycle for a player who has a potion effect running. */
    val effects = find("herblore_effects")
}
