package org.rsmod.content.skills.herblore.configs

import org.rsmod.game.type.obj.ObjType

/**
 * A herb: [grimy] is cleaned into [clean] (a Herblore level of [cleanLevel] for [cleanXp]
 * experience), and [clean] goes into a vial of water to make [unfinished] (a level of
 * [unfinishedLevel], no experience).
 */
class Herb(
    val name: String,
    val grimy: ObjType,
    val clean: ObjType,
    val unfinished: ObjType,
    val cleanLevel: Int,
    val cleanXp: Double,
    val unfinishedLevel: Int,
)

/** Every herb, with the levels and experience from the wiki's Herblore page. */
object Herbs {
    val guam = Herb("Guam leaf", o.grimy_guam, o.guam, o.guam_unf, 3, 2.5, 3)
    val marrentill =
        Herb("Marrentill", o.grimy_marrentill, o.marrentill, o.marrentill_unf, 5, 3.8, 5)
    val tarromin = Herb("Tarromin", o.grimy_tarromin, o.tarromin, o.tarromin_unf, 11, 5.0, 12)
    val harralander =
        Herb("Harralander", o.grimy_harralander, o.harralander, o.harralander_unf, 20, 6.3, 22)
    val ranarr = Herb("Ranarr weed", o.grimy_ranarr, o.ranarr, o.ranarr_unf, 25, 7.5, 30)
    val toadflax = Herb("Toadflax", o.grimy_toadflax, o.toadflax, o.toadflax_unf, 30, 8.0, 34)
    val irit = Herb("Irit leaf", o.grimy_irit, o.irit, o.irit_unf, 40, 8.8, 45)
    val avantoe = Herb("Avantoe", o.grimy_avantoe, o.avantoe, o.avantoe_unf, 48, 10.0, 50)
    val kwuarm = Herb("Kwuarm", o.grimy_kwuarm, o.kwuarm, o.kwuarm_unf, 54, 11.3, 55)
    val huasca = Herb("Huasca", o.grimy_huasca, o.huasca, o.huasca_unf, 58, 11.8, 58)
    val snapdragon =
        Herb("Snapdragon", o.grimy_snapdragon, o.snapdragon, o.snapdragon_unf, 59, 11.8, 63)
    val cadantine = Herb("Cadantine", o.grimy_cadantine, o.cadantine, o.cadantine_unf, 65, 12.5, 66)
    val lantadyme = Herb("Lantadyme", o.grimy_lantadyme, o.lantadyme, o.lantadyme_unf, 67, 13.1, 69)
    val dwarf_weed =
        Herb("Dwarf weed", o.grimy_dwarf_weed, o.dwarf_weed, o.dwarf_weed_unf, 70, 13.8, 72)
    val torstol = Herb("Torstol", o.grimy_torstol, o.torstol, o.torstol_unf, 75, 15.0, 78)

    val all =
        listOf(
            guam,
            marrentill,
            tarromin,
            harralander,
            ranarr,
            toadflax,
            irit,
            avantoe,
            kwuarm,
            huasca,
            snapdragon,
            cadantine,
            lantadyme,
            dwarf_weed,
            torstol,
        )
}

private typealias o = HerbloreObjs
