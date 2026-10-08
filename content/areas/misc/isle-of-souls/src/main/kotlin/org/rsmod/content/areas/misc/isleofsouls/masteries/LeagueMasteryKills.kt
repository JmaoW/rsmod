package org.rsmod.content.areas.misc.isleofsouls.masteries

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the combat mastery tasks that are about killing something: a giant, ten monsters of
 * combat level 100 or more, Scurrius, and a monster that needs a high Slayer level. The kill goes
 * to the player who did the most damage, and to their team, as with any task.
 *
 * The other mastery tasks are completed where their content is: combat level 100 by
 * `LeagueLevelTasks`, TzTok-Jad by `FightCaveCompletions`.
 */
class LeagueMasteryKills @Inject constructor(private val tasks: LeagueMasteryTasks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            kill(hero, npc)
        }
    }

    private fun kill(player: Player, npc: Npc) {
        val type = npc.visType
        if (MasteryKills.isGiant(type.name)) {
            tasks.complete(player, MasteryKills.SLOT_GIANT)
        }
        if (type.vislevel >= MasteryKills.HIGH_LEVEL) {
            tasks.addProgress(
                player,
                MasteryKills.SLOT_HIGH_LEVEL,
                league_progress.mastery_kills_100,
                MasteryKills.HIGH_LEVEL_KILLS,
            )
        }
        if (MasteryKills.isScurrius(type.name)) {
            tasks.complete(player, MasteryKills.SLOT_SCURRIUS)
        }
        if (type.param(params.slayer_levelrequire) >= MasteryKills.HIGH_SLAYER) {
            tasks.complete(player, MasteryKills.SLOT_SLAYER)
        }
    }
}

/** What counts for the combat mastery tasks that content completes. */
object MasteryKills {
    const val SLOT_GIANT = 1
    const val SLOT_HIGH_LEVEL = 2
    const val SLOT_SCURRIUS = 3
    const val SLOT_SLAYER = 4
    const val SLOT_JAD = 5
    const val SLOT_COMBAT_LEVEL = 6

    /** "Defeat 10 monsters with a combat level of 100 or more." */
    const val HIGH_LEVEL = 100
    const val HIGH_LEVEL_KILLS = 10

    /** "Defeat a monster with a Slayer requirement of 55 or more." */
    const val HIGH_SLAYER = 55

    /** "Reach a combat level of 100." */
    const val COMBAT_LEVEL = 100

    /** Hill, moss, fire, ice and the other giants, but not a giant rat or a giant spider. */
    fun isGiant(name: String): Boolean = name.trim().endsWith("giant", ignoreCase = true)

    fun isScurrius(name: String): Boolean = name.trim().equals("Scurrius", ignoreCase = true)
}
