package org.rsmod.content.areas.misc.isleofsouls.masteries

import jakarta.inject.Inject
import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Development helpers for trying out combat masteries. */
internal class MasteryDebugCommands
@Inject
constructor(private val masteries: LeagueMasteryTracker, private val tasks: LeagueMasteryTasks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("masterytask") {
            modLevel = modlevels.admin
            desc = "Complete a combat mastery task (1 to ${MasteryData.TASK_COUNT})"
            invalidArgs = "Use as ::masterytask slot (ex: ::masterytask 6)"
            cheat {
                val slot = args[0].toInt()
                if (tasks.complete(player, slot)) {
                    player.mes("Completed mastery task $slot.")
                } else {
                    player.mes("Mastery task $slot is not a task, or is already complete.")
                }
            }
        }

        onCommand("masteryset") {
            modLevel = modlevels.admin
            desc = "Set the unlocked tier of a combat mastery path, ignoring points"
            invalidArgs = "Use as ::masteryset melee|ranged|magic tier (ex: ::masteryset melee 3)"
            cheat {
                val style = MasteryStyle.entries.firstOrNull { it.name.equals(args[0], true) }
                val tier = args[1].toInt()
                if (style == null || tier !in 0..CombatMasteries.MAX_TIER) {
                    player.mes("Use ::masteryset melee|ranged|magic 0-${CombatMasteries.MAX_TIER}.")
                    return@cheat
                }
                masteries.setTier(player, style, tier)
                player.mes("Set ${style.name} mastery tier to $tier.")
            }
        }

        onCommand("masteryreset") {
            modLevel = modlevels.admin
            desc = "Clear every unlocked combat mastery and every finished mastery task"
            cheat {
                masteries.reset(player)
                tasks.reset(player)
                player.mes("Cleared combat masteries and mastery tasks.")
            }
        }
    }
}
