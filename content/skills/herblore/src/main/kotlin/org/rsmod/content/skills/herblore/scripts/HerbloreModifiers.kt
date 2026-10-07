package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/** Changes to how herblore works for a player, such as the ones a league relic gives. */
interface HerbloreMod {
    /** Extra doses a newly made potion has, on top of the usual three. */
    fun extraDoses(player: Player): Int = 0

    /** The chance, out of 100, that the secondary ingredient of a recipe is not used up. */
    fun secondarySaveChance(player: Player): Int = 0
}

/** Holds the [HerbloreMod]s that content has registered, and answers for all of them. */
@Singleton
class HerbloreModifiers @Inject constructor(private val random: GameRandom) {
    private val mods = ArrayList<HerbloreMod>()

    fun register(mod: HerbloreMod) {
        mods += mod
    }

    fun extraDoses(player: Player): Int = mods.sumOf { it.extraDoses(player) }

    /** Rolls whether the secondary ingredient of the next recipe is kept. */
    fun savesSecondary(player: Player): Boolean {
        val chance = mods.sumOf { it.secondarySaveChance(player) }.coerceAtMost(100)
        return chance > 0 && random.of(100) < chance
    }
}
