package org.rsmod.api.player.stat

import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/**
 * Flat experience added on top of every experience gain, after every rate has been applied, so the
 * bonus itself is not multiplied. Content installs one at startup with [PlayerSkillXP.bonus].
 */
public fun interface XpBonus {
    /** The extra xp [player] gets for gaining [xp] (already multiplied) in [stat], or `0.0`. */
    public fun bonus(player: Player, stat: StatType, xp: Double): Double
}
