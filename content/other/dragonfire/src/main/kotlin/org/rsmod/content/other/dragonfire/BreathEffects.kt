package org.rsmod.content.other.dragonfire

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The second effect of one of the King Black Dragon's special breaths (OSRS Wiki, "King Black
 * Dragon"): the poison breath poisons, the icy breath freezes and the shocking breath drains stats.
 */
enum class BreathEffect {
    None,
    Poison,
    Freeze,
    Shock,
}

/**
 * A breath with a [BreathEffect] has just landed on [player]. [damage] is what it did after their
 * protection. Whoever owns the effect (poison needs the isle's poison timer, for one) listens for
 * this; the dragonfire module itself applies none of them.
 */
class DragonBreathLanded(
    val player: Player,
    val dragon: Npc,
    val effect: BreathEffect,
    val damage: Int,
) : UnboundEvent
