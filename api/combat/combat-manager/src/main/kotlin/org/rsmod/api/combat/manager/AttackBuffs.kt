package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.Player

/**
 * A boost to a player's next melee attack that a special attack gave them: a multiplier on the
 * accuracy roll and damage added to the hit. It lasts until the player makes a melee hit or [until]
 * (a map clock) comes, whichever is first.
 */
public class AttackBuff(
    public val accuracy: Double = 1.0,
    public val flatDamage: Int = 0,
    public val until: Int,
)

/**
 * The [AttackBuff] each player has, if any. [PlayerAttackManager] reads the buff when it rolls a
 * melee attack and uses it up when the hit is queued.
 */
@Singleton
public class AttackBuffs @Inject constructor() {
    private val buffs = WeakHashMap<Player, AttackBuff>()

    /** Gives [player] the [buff], in place of any they had. */
    public fun give(player: Player, buff: AttackBuff) {
        buffs[player] = buff
    }

    /** The buff [player] has, or `null` if they have none or it has run out. */
    public fun of(player: Player): AttackBuff? {
        val buff = buffs[player] ?: return null
        if (buff.until <= player.currentMapClock) {
            buffs.remove(player)
            return null
        }
        return buff
    }

    /** Uses up the buff of [player]. */
    public fun consume(player: Player) {
        buffs.remove(player)
    }
}
