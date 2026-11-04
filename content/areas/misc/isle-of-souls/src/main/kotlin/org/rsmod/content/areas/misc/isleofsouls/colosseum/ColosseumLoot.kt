package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.rsmod.game.type.obj.ObjType

/** One item and how many of it, in a run's loot. */
data class ColosseumItem(val obj: ObjType, val count: Int)

/**
 * One line of a wave's table: [weight] out of the table's total. A [fanatic] line is the three
 * Sunfire fanatic pieces together, with duplicate protection deciding which one comes.
 */
class ColosseumLootRow(
    val obj: ObjType?,
    val min: Int,
    val max: Int,
    val weight: Int,
    val fanatic: Boolean = false,
)

/**
 * The rewards chest's tables (wiki, "Rewards Chest (Fortis Colosseum)", read 2026-10-30). Each
 * wave's loot is one roll on its table, made before the wave so the intermission screen can show
 * it. Wave 1 is always 80 sunfire splinters; wave 12 also gives Dizana's quiver and a 1 in 200
 * chance of Smol Heredit. Left out: the Varlamore token on wave 3 (its rate "varies").
 *
 * The wiki lists multiples of unstackable items without saying whether they are noted; they come
 * noted here, as boss chests' do.
 */
object ColosseumLoot {
    private val o = colosseum_objs

    /** The wiki: Smol Heredit is 1 in 200 for beating the Colosseum, and for a quiver handed in. */
    const val PET_ODDS = 200

    /** The wiki: wave 1 always gives this many sunfire splinters. */
    const val FIRST_WAVE_SPLINTERS = 80

    /** Sunfire splinters Minimus gives for a spare quiver (wiki). */
    const val QUIVER_SPLINTERS = 4_000

    private fun item(obj: ObjType, count: Int, weight: Int) =
        ColosseumLootRow(obj, count, count, weight)

    private fun echo(single: Int, multiple: Int) =
        listOf(
            ColosseumLootRow(o.echo_crystal, 1, 1, single),
            ColosseumLootRow(o.echo_crystal, 2, 3, multiple),
        )

    private fun fanatic(perPiece: Int) =
        ColosseumLootRow(null, 1, 1, perPiece * FANATIC_PIECES, fanatic = true)

    private fun tonalztics(weight: Int) = item(o.tonalztics_uncharged, 1, weight)

    /** Waves 2 to 4's common table. */
    private fun commonLow(weight: Int) =
        listOf(
            item(o.rune_platebody, 1, weight),
            item(o.death_rune, 150, weight),
            item(o.chaos_rune, 150, weight),
            item(o.sunfire_splinters, 150, weight),
            item(o.steel_cannonball, 80, weight),
            item(o.rune_kiteshield_noted, 4, weight),
            item(o.rune_chainbody, 1, weight),
        )

    /** Waves 3 to 8's second table. */
    private fun commonMid(weight: Int) =
        listOf(
            item(o.onyx_bolts, 30, weight),
            item(o.snapdragon_seed, 1, weight),
            item(o.dragon_platelegs, 1, weight),
            item(o.sunfire_splinters, 500, weight),
            item(o.earth_orb_noted, 80, weight),
            item(o.rune_2h_sword_noted, 2, weight),
            item(o.gold_ore_noted, 150, weight),
        )

    /** Waves 7 and 9's table. */
    private fun commonHigh(weight: Int) =
        listOf(
            item(o.dragon_bolts_unf, 200, weight),
            item(o.ranarr_seed, 4, weight),
            item(o.sunfire_splinters, 1_100, weight),
            item(o.dragon_arrowtips, 150, weight),
            item(o.adamantite_ore_noted, 100, weight),
            item(o.death_rune, 250, weight),
        )

    /** Waves 8 and 10's table. */
    private fun commonTop(weight: Int) =
        listOf(
            item(o.onyx_bolts, 50, weight),
            item(o.dragon_platelegs_noted, 2, weight),
            item(o.sunfire_splinters, 1_750, weight),
            item(o.rune_kiteshield_noted, 5, weight),
            item(o.runite_ore_noted, 12, weight),
            item(o.death_rune, 300, weight),
        )

    /** Waves 9 and 11's table. */
    private fun commonElite(weight: Int) =
        listOf(
            item(o.onyx_bolts, 75, weight),
            item(o.sunfire_splinters, 2_500, weight),
            item(o.dragon_platelegs_noted, 3, weight),
            item(o.death_rune, 300, weight),
            item(o.rune_warhammer_noted, 5, weight),
        )

    /** Waves 10 and 12's table. */
    private fun commonFinal(weight: Int) =
        listOf(
            item(o.onyx_bolts, 100, weight),
            item(o.rune_warhammer_noted, 8, weight),
            item(o.dragon_platelegs_noted, 3, weight),
            item(o.sunfire_splinters, 3_500, weight),
            item(o.dragon_arrowtips, 250, weight),
        )

    /** Each wave's table, from wave 2 (wave 1 is fixed). */
    val tables: Map<Int, List<ColosseumLootRow>> by lazy {
        mapOf(
            2 to commonLow(1),
            3 to commonLow(9) + commonMid(1),
            4 to commonLow(5535) + commonMid(615) + echo(126, 14) + fanatic(70),
            5 to commonMid(2725) + echo(63, 7) + fanatic(35),
            6 to commonMid(2375) + echo(63, 7) + fanatic(35),
            7 to commonMid(5832) + commonHigh(756) + echo(189, 21) + fanatic(105) + tonalztics(35),
            8 to
                commonMid(14472) + commonTop(1876) + echo(567, 63) + fanatic(315) + tonalztics(105),
            9 to commonHigh(3180) + commonElite(424) + echo(135, 15) + fanatic(75) + tonalztics(25),
            10 to commonTop(2340) + commonFinal(312) + echo(135, 15) + fanatic(75) + tonalztics(25),
            11 to
                commonElite(360) +
                    listOf(
                        item(o.steel_cannonball, 2_000, 50),
                        item(o.dragon_plateskirt_noted, 5, 50),
                        item(o.dragon_arrowtips, 350, 50),
                        item(o.onyx_bolts, 150, 50),
                    ) +
                    echo(27, 3) +
                    fanatic(15) +
                    tonalztics(5),
            12 to
                commonFinal(792) +
                    listOf(
                        item(o.uncut_onyx, 1, 110),
                        item(o.dragon_plateskirt_noted, 5, 110),
                        item(o.rune_2h_sword_noted, 9, 110),
                        item(o.runite_ore_noted, 35, 110),
                    ) +
                    echo(135, 15) +
                    fanatic(75) +
                    tonalztics(25),
        )
    }

    /** The three fanatic pieces, in the order duplicate protection breaks ties. */
    val fanaticPieces: List<ObjType>
        get() = listOf(o.sunfire_helm, o.sunfire_body, o.sunfire_legs)

    /**
     * Rolls wave [wave]'s loot. [roll] gives a number from 0 until its argument; [fanaticOwned]
     * says how many of a fanatic piece the player has had, so the piece they have fewest of comes.
     */
    fun roll(wave: Int, roll: (Int) -> Int, fanaticOwned: (ObjType) -> Int): List<ColosseumItem> {
        if (wave <= 1) {
            return listOf(ColosseumItem(o.sunfire_splinters, FIRST_WAVE_SPLINTERS))
        }
        val table = tables[minOf(wave, ColosseumWaves.FINAL_WAVE)] ?: return emptyList()
        val total = table.sumOf { it.weight }
        var at = roll(total)
        val row = table.first { (at - it.weight).also { left -> at = left } < 0 }
        val loot = ArrayList<ColosseumItem>()
        if (row.fanatic) {
            val fewest = fanaticPieces.minOf(fanaticOwned)
            val pieces = fanaticPieces.filter { fanaticOwned(it) == fewest }
            loot += ColosseumItem(pieces[roll(pieces.size)], 1)
        } else {
            val obj = checkNotNull(row.obj)
            val count = if (row.max > row.min) row.min + roll(row.max - row.min + 1) else row.min
            loot += ColosseumItem(obj, count)
        }
        if (wave >= ColosseumWaves.FINAL_WAVE) {
            loot += ColosseumItem(o.quiver_uncharged, 1)
        }
        return loot
    }

    private const val FANATIC_PIECES = 3
}
