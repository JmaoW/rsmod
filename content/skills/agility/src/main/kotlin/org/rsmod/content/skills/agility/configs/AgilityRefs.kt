package org.rsmod.content.skills.agility.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

typealias agility_objs = AgilityObjs

object AgilityObjs : ObjReferences() {
    val mark_of_grace = find("grace")
    val graceful_hood = find("graceful_hood")
    val graceful_cape = find("graceful_cape")
    val graceful_top = find("graceful_top")
    val graceful_legs = find("graceful_legs")
    val graceful_gloves = find("graceful_gloves")
    val graceful_boots = find("graceful_boots")
    val graceful_hood_worn = find("graceful_hood_worn")
    val graceful_cape_worn = find("graceful_cape_worn")
    val graceful_top_worn = find("graceful_top_worn")
    val graceful_legs_worn = find("graceful_legs_worn")
    val graceful_gloves_worn = find("graceful_gloves_worn")
    val graceful_boots_worn = find("graceful_boots_worn")

    /** Each graceful piece as carried, then as worn. */
    val graceful_pieces =
        listOf(
            graceful_hood to graceful_hood_worn,
            graceful_cape to graceful_cape_worn,
            graceful_top to graceful_top_worn,
            graceful_legs to graceful_legs_worn,
            graceful_gloves to graceful_gloves_worn,
            graceful_boots to graceful_boots_worn,
        )
}

/**
 * How many laps each course has been run, for the lap count message. Needs `packCache` and a line
 * in `.data/symbols/varp.sym`.
 */
object AgilityVarps : VarpReferences() {
    val gnome_laps = find("agility_laps_gnome")
    val draynor_laps = find("agility_laps_draynor")
    val varrock_laps = find("agility_laps_varrock")
    val falador_laps = find("agility_laps_falador")
    val seers_laps = find("agility_laps_seers")
    val ardougne_laps = find("agility_laps_ardougne")

    /** 1 once the lap count message has been turned off at Grace. */
    val lap_counter_off = find("agility_lap_counter_off")

    fun laps(courseKey: String): VarpType? =
        when (courseKey) {
            "gnome" -> gnome_laps
            "draynor" -> draynor_laps
            "varrock" -> varrock_laps
            "falador" -> falador_laps
            "seers" -> seers_laps
            "ardougne" -> ardougne_laps
            else -> null
        }
}

internal object AgilityVarpBuilder : VarpBuilder() {
    init {
        build("agility_laps_gnome") {}
        build("agility_laps_draynor") {}
        build("agility_laps_varrock") {}
        build("agility_laps_falador") {}
        build("agility_laps_seers") {}
        build("agility_laps_ardougne") {}
        build("agility_lap_counter_off") {}
    }
}
