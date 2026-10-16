package org.rsmod.content.areas.misc.isleofsouls.event

import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.advanced.onAdvanceStat
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueLevelTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskCompleted
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Connects the things that happen in the game to the league announcements, and gives admins the
 * freeze and announcement commands.
 *
 * Note: the level-up announcement uses the engine's default "advance stat" script, and only one
 * default script can be bound, so the league's level tasks are also checked from it. Content that
 * needs its own should bind per-skill scripts instead.
 */
class LeagueEventScript
@Inject
constructor(
    private val announcer: LeagueAnnouncer,
    private val standings: LeagueStandings,
    private val event: LeagueEvent,
    private val levelTasks: LeagueLevelTasks,
    private val skillcapes: LeagueSkillcapes,
) : PluginScript() {
    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        onEvent<LeagueTaskCompleted> {
            announcer.taskCompleted(this)
            standings.takeLeadChange()?.let(announcer::leadTaken)
        }

        onAdvanceStat { announcement ->
            val stat = announcement.args
            announcer.levelReached(player, stat, player.statBase(stat))
            skillcapes.onLevelUp(player, stat, player.statBase(stat))
            levelTasks.onLevelUp(player)
        }

        onEvent<PlayerDeathEvents.Died> { announcer.died(player) }

        onEvent<NpcDropEvents.Rolled> {
            for (drop in drops) {
                announcer.drop(hero, drop.obj, drop.count, drop.boosted)
            }
        }

        onCommand("leaguefreeze") {
            modLevel = modlevels.admin
            desc = "Freeze the league standings where they are now"
            cheat {
                val snapshot = standings.live()
                if (!event.freeze(snapshot)) {
                    player.mes(EventMessages.alreadyFrozen())
                    return@cheat
                }
                announcer.frozen(snapshot)
                player.mes(EventMessages.frozen())
            }
        }

        onCommand("leagueunfreeze") {
            modLevel = modlevels.admin
            desc = "Make the league standings live again"
            cheat {
                if (!event.unfreeze()) {
                    player.mes(EventMessages.notFrozen())
                    return@cheat
                }
                announcer.unfrozen()
                standings.takeLeadChange()?.let(announcer::leadTaken)
                player.mes(EventMessages.unfrozen())
            }
        }

        onCommand("announcereload") {
            modLevel = modlevels.admin
            desc = "Re-read announcements.json after editing it"
            cheat {
                val problem = announcer.reload()
                player.mes(
                    if (problem == null) {
                        EventMessages.announcementsReloaded()
                    } else {
                        EventMessages.announcementsNotReloaded(problem)
                    }
                )
            }
        }
    }
}
