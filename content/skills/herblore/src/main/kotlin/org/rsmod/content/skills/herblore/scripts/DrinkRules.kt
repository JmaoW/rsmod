package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.game.entity.Player

/**
 * Content decides where a player may not drink a potion (a raid invocation, supplies that only work
 * in their minigame). A rule answers with the message to show when [Player] may not drink the
 * potion, or `null` when they may. With no rules every potion can be drunk.
 */
@Singleton
class DrinkRules @Inject constructor() {
    private val rules = ArrayList<(player: Player, potion: Potion) -> String?>()

    fun register(rule: (player: Player, potion: Potion) -> String?) {
        rules += rule
    }

    /** Why [player] may not drink [potion], or `null` if they may. */
    fun denial(player: Player, potion: Potion): String? =
        rules.firstNotNullOfOrNull { it(player, potion) }

    companion object {
        /** Whether [potion] heals Hitpoints (brews, nectar, Guthix rests, ambrosia, mixes). */
        fun restoresHitpoints(potion: Potion): Boolean = potion.heal > 0 || heals(potion.effect)

        private fun heals(effect: PotionEffect): Boolean =
            when (effect) {
                is PotionEffect.Heal,
                PotionEffect.SaradominBrew,
                PotionEffect.Ambrosia -> true
                is PotionEffect.Composite -> effect.effects.any(::heals)
                else -> false
            }
    }
}
