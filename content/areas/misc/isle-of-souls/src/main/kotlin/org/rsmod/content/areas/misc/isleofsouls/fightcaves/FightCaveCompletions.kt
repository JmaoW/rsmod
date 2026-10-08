package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import jakarta.inject.Inject
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.masteries.MasteryKills
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

/** The league tasks that the Fight Cave feeds. */
class FightCaveCompletions
@Inject
constructor(private val tracker: LeagueTaskTracker, private val masteryTasks: LeagueMasteryTasks) {
    /** [player] was inside the cave when Jad died. */
    fun completed(player: Player) {
        masteryTasks.complete(player, MasteryKills.SLOT_JAD)
        tracker.addProgress(player, TASK_COMPLETE_FIVE, league_progress.fight_caves_5, 5)
        tracker.addProgress(player, TASK_COMPLETE_TEN, league_progress.fight_caves_10, 10)
    }

    /**
     * A magic or ranged [hit] from Jad has landed on [player] (protection prayers already applied).
     * It counts when the matching protection prayer was off and the hit did damage without killing
     * them.
     */
    fun jadHit(player: Player, hit: HitBuilder) {
        val protected =
            when (hit.type) {
                HitType.Magic -> player.vars[varbits.protect_from_magic] == 1
                HitType.Ranged -> player.vars[varbits.protect_from_missiles] == 1
                else -> return
            }
        if (!protected && hit.damage > 0 && hit.damage < player.hitpoints) {
            tracker.complete(player, TASK_JAD_WITHOUT_PRAYER)
        }
    }

    companion object {
        const val TASK_JAD_WITHOUT_PRAYER = 657
        const val TASK_COMPLETE_FIVE = 664
        const val TASK_COMPLETE_TEN = 665
    }
}
