package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.game.type.varbit.VarBitType

/**
 * The combat mastery varbits the client reads. They sit inside persisted varps, so the screen and
 * the Leagues summary see them directly and they are saved with the player.
 */
object MasteryVarBits : VarBitReferences() {
    /** Highest unlocked tier (`0` to `6`) of each path. */
    val melee_progress = find("league_combat_mastery_melee_progress")
    val ranged_progress = find("league_combat_mastery_ranged_progress")
    val magic_progress = find("league_combat_mastery_magic_progress")

    /** Mastery points the player can spend now. The client needs `> 0` to offer "Select". */
    val points_to_spend = find("league_combat_mastery_points_to_spend")

    /**
     * Shown as `earned / 10` on the bar, where 10 is the number of mastery tasks. It holds the
     * number of completed tasks, not points.
     */
    val points_earned = find("league_combat_mastery_points_earned")

    /** `1` once mastery task `n + 1` is done. The Points tab highlights the finished tasks. */
    val point_unlock =
        List(MasteryData.TASK_COUNT) { find("league_combat_mastery_point_unlock_${it + 1}") }

    /** The clicked mastery as `card + 1` (`0` means none). The client never reads it. */
    val last_viewed = find("league_combat_mastery_last_viewed")

    fun progress(style: MasteryStyle): VarBitType =
        when (style) {
            MasteryStyle.Melee -> melee_progress
            MasteryStyle.Ranged -> ranged_progress
            MasteryStyle.Magic -> magic_progress
        }
}
