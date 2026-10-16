package org.rsmod.content.skills.magic.utility.scripts

import com.google.inject.ImplementedBy
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * What changes how the alchemy spells work for a player, such as a league relic. By default,
 * nothing does.
 */
@ImplementedBy(NoAlchemyModifiers::class)
interface AlchemyModifiers {
    /** Whether the spells need no runes and no Magic level. */
    fun isFree(player: Player): Boolean

    /** What the spells pay, as a percent of the usual coins. */
    fun coinPercent(player: Player): Int

    /** The chance, in percent, that an item is not used up by the spell. */
    fun keepChance(player: Player): Int

    /** Whether casting on a stack carries on casting until the stack is gone. */
    fun autoRecast(player: Player): Boolean
}

@Singleton
class NoAlchemyModifiers : AlchemyModifiers {
    override fun isFree(player: Player): Boolean = false

    override fun coinPercent(player: Player): Int = 100

    override fun keepChance(player: Player): Int = 0

    override fun autoRecast(player: Player): Boolean = false
}
