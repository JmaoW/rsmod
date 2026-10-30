package org.rsmod.content.areas.misc.isleofsouls.vorkath

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.skills.prayer.scripts.Burying
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The collection log's Vorkath kill count, which the head reward goes by. */
object VorkathVarps : VarpReferences() {
    val kills = find("clog_kc_vorkath_kills")
}

/**
 * Vorkath's rewards beyond its drop table:
 * - **Vorkath's head** is guaranteed on a player's 10th kill and on every 50th (50, 100, 150...),
 *   on top of the table's own 1 in 50 (an isle change: the real game guarantees only the 50th). The
 *   count is the collection log's, which goes up for the player who gets the drops just before they
 *   are rolled; a head the table already gave is not doubled.
 * - **Ava's assembler**: the head used on Ava's accumulator, with 75 mithril arrows in the pack.
 *   The real one is made by Ava in Draynor Manor, who is not on the isle.
 * - **Dragonbone necklace**: worn, it restores Prayer points as bones are buried (wiki): 1 to 5 by
 *   the kind of bone.
 */
class VorkathRewards @Inject constructor(private val objTypes: ObjTypeList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Modify> {
            if (npc.type.id == vorkath_npcs.boss.id && isHeadKill(hero.vars[VorkathVarps.kills])) {
                guaranteed += DropResult(vorkath_objs.head, 1)
            }
        }
        onOpHeldU(vorkath_objs.head, vorkath_objs.accumulator) { assemble() }
        onEvent<Burying.BoneBuried> { restorePrayer(player, bone) }
    }

    fun ProtectedAccess.assemble() {
        if (invTotal(inv, vorkath_objs.mithril_arrow) < ASSEMBLER_ARROWS) {
            mes("You need $ASSEMBLER_ARROWS mithril arrows to make Ava's assembler.")
            return
        }
        val removed =
            invDel(
                inv,
                vorkath_objs.head,
                1,
                vorkath_objs.accumulator,
                1,
                vorkath_objs.mithril_arrow,
                ASSEMBLER_ARROWS,
            )
        if (removed.failure) {
            return
        }
        invAdd(inv, vorkath_objs.assembler)
        mes("You fit Vorkath's head and the arrows to the accumulator, making Ava's assembler.")
    }

    private fun restorePrayer(player: Player, bone: ObjType) {
        val wearing =
            player.worn.objs.any { it != null && it.isType(vorkath_objs.dragonbone_necklace) }
        if (!wearing) {
            return
        }
        val points = prayerPoints(objTypes[bone].name)
        if (points > 0) {
            player.statHeal(stats.prayer, points, 0)
        }
    }

    companion object {
        const val FIRST_HEAD_KILL = 10
        const val HEAD_EVERY = 50
        const val ASSEMBLER_ARROWS = 75

        fun isHeadKill(kills: Int): Boolean =
            kills == FIRST_HEAD_KILL || (kills > 0 && kills % HEAD_EVERY == 0)

        /** The Prayer points the dragonbone necklace restores for a bone called [name] (wiki). */
        fun prayerPoints(name: String): Int = NECKLACE_POINTS[name.lowercase()] ?: 0

        private val NECKLACE_POINTS: Map<String, Int> = buildMap {
            for (bone in
                listOf("bones", "burnt bones", "monkey bones", "wolf bones", "bat bones")) {
                put(bone, 1)
            }
            for (bone in
                listOf(
                    "big bones",
                    "jogre bones",
                    "zogre bones",
                    "wyrmling bones",
                    "shaikahan bones",
                    "small ninja monkey bones",
                )) {
                put(bone, 2)
            }
            for (bone in
                listOf("babydragon bones", "bull bones", "wyrm bones", "strykewyrm bones")) {
                put(bone, 3)
            }
            for (bone in
                listOf(
                    "dragon bones",
                    "frost dragon bones",
                    "wyvern bones",
                    "drake bones",
                    "dagannoth bones",
                    "hydra bones",
                    "fayrg bones",
                    "raurg bones",
                    "lava dragon bones",
                )) {
                put(bone, 4)
            }
            for (bone in listOf("superior dragon bones", "giant bones", "ourg bones")) {
                put(bone, 5)
            }
        }
    }
}
