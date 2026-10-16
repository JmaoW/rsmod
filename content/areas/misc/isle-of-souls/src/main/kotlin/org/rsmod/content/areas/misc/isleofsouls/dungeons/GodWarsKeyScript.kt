package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GodWarsObjs : ObjReferences() {
    /** An icy key that opens one general's chamber without the kill count, once. */
    val ecumenical_key = find("ecumenical_key")
}

/**
 * The ecumenical key (OSRS Wiki). Any follower killed in the dungeon can drop one, 1 time in
 * [KEY_ODDS]; a player carrying [KEY_LIMIT] of them gets "You already have enough of these." and no
 * key. A chamber's door takes one key instead of the kill count (see [DungeonGateScript]).
 *
 * Left out: the higher limits for the Wilderness diaries, the higher drop rates from the combat
 * achievement tiers and the key shards (they come from Nex). Keys in the bank do not count towards
 * the limit.
 */
class GodWarsKeyScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val tasks: LeagueTaskTracker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Rolled> { roll(hero, npc) }
    }

    /**
     * Rolls the key for a [npc] that [hero] killed, and drops it at the npc's feet if it comes up.
     * Returns whether a key was dropped.
     */
    fun roll(hero: Player, npc: Npc): Boolean {
        val name = npc.visType.internalName ?: return false
        if (GodWarsNames.godOf(name) == null) {
            return false
        }
        if (Dungeon.GOD_WARS_DUNGEON.areas.none { npc.coords in it }) {
            return false
        }
        if (random.of(KEY_ODDS) != 0) {
            return false
        }
        if (carried(hero) >= KEY_LIMIT) {
            hero.mes("You already have enough of these.")
            return false
        }
        objRepo.add(
            GodWarsObjs.ecumenical_key,
            npc.coords,
            constants.lootdrop_duration,
            receiver = hero,
        )
        tasks.complete(hero, TASK_KEY)
        return true
    }

    /** How many ecumenical keys [player] has in their inventory. */
    fun carried(player: Player): Int =
        player.inv.objs.sumOf { if (it.isType(GodWarsObjs.ecumenical_key)) it.count else 0 }

    companion object {
        /** The Wiki's base drop rate: 1 in 60. */
        const val KEY_ODDS = 60

        /** The most keys a player can carry (the Wiki's limit without the diaries). */
        const val KEY_LIMIT = 3

        /** The league task "Obtain an Ecumenical Key". */
        const val TASK_KEY = 1011
    }
}
