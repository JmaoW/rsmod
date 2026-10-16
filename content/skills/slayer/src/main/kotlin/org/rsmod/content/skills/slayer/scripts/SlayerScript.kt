package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.formulas.SlayerTaskOverrides
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.objs
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerSpots
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Counts kills for the player's task. */
class SlayerScript @Inject constructor(private val engine: SlayerEngine) : PluginScript() {
    override fun ScriptContext.startup() {
        // A player the hooks make "always on task" gets the on-task bonuses (a Slayer helmet's) on
        // every monster they could be assigned.
        SlayerTaskOverrides.checks += { player, type -> engine.countsAsOnTask(player, type) }
        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            val onTask = engine.isOnTask(hero, npc)
            engine.onKill(hero, npc)
            if (npc.visType.id in engine.superiorIds) {
                engine.publish(SlayerEvents.SuperiorKilled(hero, npc, onTask))
            }
        }
    }
}

/**
 * Development helpers:
 * - `::slayertask <master> [task]` gives a task from a master (any task whose name contains the
 *   text, ignoring the requirements), or a rolled one with no task name
 * - `::slayerkit` gives an enchanted gem, a Slayer helmet and ring and 100,000 coins; `::slayerkit
 *   protect`, `finish` and `hunt` give the gear for those rules
 * - `::slayermasters` teleports to the three masters
 * - `::slayerstatus` prints the current task, points and streak
 * - `::slayerpoints <n>` sets the Slayer reward points
 * - `::slayerrewards [master]` opens the rewards screen as that master (Vannaka by default)
 * - `::slayercancel` drops the task
 */
internal class SlayerDebugCommands
@Inject
constructor(
    private val engine: SlayerEngine,
    private val rewards: SlayerRewards,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("slayertask") {
            modLevel = modlevels.admin
            desc = "Get a Slayer task from a master"
            invalidArgs = "Use as ::slayertask <turael|vannaka|duradel> [task]"
            cheat {
                val master = args.getOrNull(0)?.let { engine.tables.master(it) }
                if (master == null) {
                    player.mes("Masters: ${engine.tables.masters.keys.joinToString(", ")}")
                    return@cheat
                }
                engine.cancel(player)
                val text = args.drop(1).joinToString(" ").lowercase()
                val forced =
                    if (text.isBlank()) null
                    else master.tasks.firstOrNull { text in it.task.lowerName }
                if (text.isNotBlank() && forced == null) {
                    player.mes("${master.displayName} has no task matching `$text`.")
                    return@cheat
                }
                val (assignment, refusal) = engine.assign(player, master, forced)
                if (assignment == null) {
                    player.mes("No task: $refusal.")
                } else {
                    player.mes(
                        "Your new task is to kill ${assignment.amount} ${assignment.task.lowerName}."
                    )
                }
            }
        }
        onCommand("slayerkit") {
            modLevel = modlevels.admin
            desc = "Give Slayer items: no argument for the basics, then protect, finish or hunt"
            invalidArgs = "Use as ::slayerkit [protect|finish|hunt]"
            cheat {
                val kit = args.getOrNull(0)?.lowercase()
                val o = SlayerObjs
                val items: List<Pair<ObjType, Int>>? =
                    when (kit) {
                        null -> listOf(o.gem to 1, o.helm to 1, o.ring to 1, objs.coins to 100_000)
                        // Gear that protects from what hits back.
                        "protect" ->
                            listOf(
                                o.nose_peg to 1,
                                o.earmuffs to 1,
                                o.facemask to 1,
                                o.spiny_helmet to 1,
                                o.mirror_shield to 1,
                                o.witchwood_icon to 1,
                                o.insulated_boots to 1,
                                o.slayer_gloves to 1,
                                o.reinforced_goggles to 1,
                            )
                        // What finishes the monsters that cannot be killed by damage alone.
                        "finish" ->
                            listOf(
                                o.rock_hammer to 1,
                                o.rock_thrownhammer to 10,
                                o.bag_of_salt to 10,
                                o.ice_cooler to 10,
                            )
                        // What it takes to harm turoth, kurask and harpie bug swarms.
                        "hunt" ->
                            listOf(
                                o.leaf_bladed_spear to 1,
                                o.broad_arrows to 100,
                                o.staff to 1,
                                o.bug_lantern to 1,
                                o.tinderbox to 1,
                            )
                        else -> null
                    }
                if (items == null) {
                    player.mes("Use as ::slayerkit [protect|finish|hunt]")
                    return@cheat
                }
                for ((obj, count) in items) {
                    player.invAdd(player.inv, obj, count, strict = false)
                }
                player.mes("Gave the Slayer kit.")
            }
        }
        onCommand("slayermasters") {
            modLevel = modlevels.admin
            desc = "Teleport to the Slayer masters (Turael)"
            cheat { protectedAccess.launch(player) { telejump(SlayerSpots.turael) } }
        }
        onCommand("slayerstatus") {
            modLevel = modlevels.admin
            desc = "Show the Slayer task, points and streak"
            cheat {
                val current = engine.current(player)
                player.mes(if (current == null) "No Slayer task." else engine.describe(current))
                val blocked =
                    engine.tables.masters.values.joinToString { master ->
                        "${master.key} ${engine.blocked(player, master)}"
                    }
                player.mes(
                    "Points: ${engine.points(player)}, streak: ${engine.streak(player)}, " +
                        "blocked: $blocked."
                )
            }
        }
        onCommand("slayerpoints") {
            modLevel = modlevels.admin
            desc = "Set the Slayer reward points"
            invalidArgs = "Use as ::slayerpoints <amount>"
            cheat {
                val amount = args.getOrNull(0)?.toIntOrNull()
                if (amount == null) {
                    player.mes("Use as ::slayerpoints <amount>")
                    return@cheat
                }
                engine.spendPoints(player, engine.points(player))
                engine.addPoints(player, amount)
                player.mes("Slayer points: ${engine.points(player)}.")
            }
        }
        onCommand("slayerrewards") {
            modLevel = modlevels.admin
            desc = "Open the Slayer rewards screen"
            invalidArgs = "Use as ::slayerrewards [turael|vannaka|duradel]"
            cheat {
                val master =
                    engine.tables.master(args.getOrNull(0) ?: "vannaka")
                        ?: return@cheat player.mes(
                            "Use as ::slayerrewards [turael|vannaka|duradel]"
                        )
                protectedAccess.launch(player) { rewards.open(this, master) }
            }
        }
        onCommand("slayercancel") {
            modLevel = modlevels.admin
            desc = "Drop the Slayer task"
            cheat {
                engine.cancel(player)
                player.mes("Slayer task dropped.")
            }
        }
    }
}
