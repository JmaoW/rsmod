package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.masteries.MasteryKills
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/**
 * Completes the league's level tasks: the first level up, the first level N in any skill, total
 * level, base level in every skill, and combat level.
 *
 * [onLevelUp] is called when a skill levels up, and [check] on login. Both look at every skill, so
 * the tasks for skills that are not built yet complete by themselves once those skills can be
 * trained.
 */
class LeagueLevelTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val masteryTasks: LeagueMasteryTasks) {
    /** A skill of [player] has just gone up a level. */
    fun onLevelUp(player: Player) {
        tracker.complete(player, TASK_FIRST_LEVEL_UP)
        check(player)
    }

    fun check(player: Player) {
        val levels = allStats.associateWith { player.statBase(it) }

        for (task in firstLevelTasks) {
            if (!tracker.isComplete(player, task.taskId)) {
                val reached =
                    levels.any { (stat, level) -> level >= task.level && stat !in task.excluded }
                if (reached) {
                    tracker.complete(player, task.taskId)
                }
            }
        }

        val total = levels.values.sum()
        for ((taskId, goal) in totalLevelTasks) {
            if (total >= goal) {
                tracker.complete(player, taskId)
            }
        }

        val lowest = levels.values.min()
        for ((taskId, goal) in baseLevelTasks) {
            if (lowest >= goal) {
                tracker.complete(player, taskId)
            }
        }

        val combat = PlayerSkillXP.calculateCombatLevel(player)
        if (combat >= MasteryKills.COMBAT_LEVEL) {
            masteryTasks.complete(player, MasteryKills.SLOT_COMBAT_LEVEL)
        }
        for ((taskId, goal) in combatLevelTasks) {
            if (combat >= goal) {
                tracker.complete(player, taskId)
            }
        }
    }

    /** "Achieve Your First Level N": [level] in any skill other than the [excluded] ones. */
    private class FirstLevel(
        val taskId: Int,
        val level: Int,
        val excluded: Set<StatType> = emptySet(),
    )

    private companion object {
        const val TASK_FIRST_LEVEL_UP = 191

        val allStats: List<StatType> =
            listOf(
                stats.attack,
                stats.defence,
                stats.strength,
                stats.hitpoints,
                stats.ranged,
                stats.prayer,
                stats.magic,
                stats.cooking,
                stats.woodcutting,
                stats.fletching,
                stats.fishing,
                stats.firemaking,
                stats.crafting,
                stats.smithing,
                stats.mining,
                stats.herblore,
                stats.agility,
                stats.thieving,
                stats.slayer,
                stats.farming,
                stats.runecrafting,
                stats.hunter,
                stats.construction,
            )

        /** The tasks' descriptions leave these out, as they start above level 1. */
        val notLevelFive = setOf(stats.agility, stats.hitpoints, stats.runecrafting)
        val notLevelTen = setOf(stats.agility, stats.hitpoints)

        val firstLevelTasks =
            listOf(
                FirstLevel(192, 5, notLevelFive),
                FirstLevel(193, 10, notLevelTen),
                FirstLevel(194, 20),
                FirstLevel(195, 30),
                FirstLevel(196, 40),
                FirstLevel(197, 50),
                FirstLevel(198, 60),
                FirstLevel(199, 70),
                FirstLevel(200, 80),
                FirstLevel(201, 90),
                FirstLevel(202, 95),
            )

        /** Task id to the total level it needs. */
        val totalLevelTasks =
            mapOf(
                203 to 100,
                204 to 250,
                205 to 500,
                206 to 750,
                207 to 1000,
                208 to 1250,
                209 to 1500,
                210 to 1750,
                211 to 2000,
                212 to 2100,
                213 to 2200,
                214 to 2277,
            )

        /** Task id to the level every skill needs. */
        val baseLevelTasks =
            mapOf(
                215 to 5,
                216 to 10,
                217 to 20,
                218 to 30,
                219 to 40,
                220 to 50,
                221 to 60,
                222 to 70,
                223 to 80,
                224 to 90,
                225 to 95,
            )

        /** Task id to the combat level it needs. */
        val combatLevelTasks =
            mapOf(
                401 to 10,
                402 to 25,
                403 to 50,
                404 to 75,
                405 to 100,
                406 to 110,
                407 to 120,
                408 to 126,
            )
    }
}
