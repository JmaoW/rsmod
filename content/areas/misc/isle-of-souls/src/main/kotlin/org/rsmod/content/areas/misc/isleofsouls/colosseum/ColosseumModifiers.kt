package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.rsmod.game.type.varbit.VarBitType

/**
 * The 14 modifiers Minimus offers between waves. [key] is the modifier's key in cache enum 5312,
 * which is what the intermission screen's script takes; [tierVarbit] is the varbit the screen reads
 * a tiered modifier's tier from (script 4980). [glory] is the Glory a wave earns per tier (wiki,
 * "Fortis Colosseum/Modifiers"; the cache's struct param 1903 holds the same).
 */
enum class ColosseumModifier(
    val key: Int,
    val label: String,
    val maxTier: Int,
    val glory: Int,
    private val varbit: (() -> VarBitType)?,
) {
    Mantimayhem(0, "Mantimayhem", 3, 150, { colosseum_varbits.tier_mantimayhem }),
    Reentry(1, "Reentry", 3, 150, { colosseum_varbits.tier_reentry }),
    Bees(2, "Bees!", 3, 150, { colosseum_varbits.tier_bees }),
    Volatility(3, "Volatility", 3, 100, { colosseum_varbits.tier_volatility }),
    Blasphemy(4, "Blasphemy", 3, 100, { colosseum_varbits.tier_blasphemy }),
    Relentless(5, "Relentless", 3, 200, { colosseum_varbits.tier_relentless }),
    Quartet(6, "Quartet", 1, 100, null),
    Totemic(7, "Totemic", 1, 200, null),
    Doom(8, "Doom", 3, 200, { colosseum_varbits.tier_doom }),
    DynamicDuo(9, "Dynamic Duo", 1, 150, null),
    Solarflare(10, "Solarflare", 3, 250, { colosseum_varbits.tier_solarflare }),
    Myopia(11, "Myopia", 3, 200, { colosseum_varbits.tier_myopia }),
    Frailty(12, "Frailty", 3, 200, { colosseum_varbits.tier_frailty }),
    RedFlag(13, "Red Flag", 1, 250, null);

    val tierVarbit: VarBitType?
        get() = varbit?.invoke()

    /** The lowest wave it is offered before, and the highest (the wiki's offer rules). */
    val firstWave: Int
        get() = if (this == RedFlag || this == DynamicDuo) LATE_FROM_WAVE else 1

    val lastWave: Int
        get() =
            when (this) {
                // Wave 12 is Sol Heredit alone: nothing these change is in it (wiki).
                DynamicDuo,
                Mantimayhem,
                Reentry,
                RedFlag -> LAST_REGULAR_WAVE
                else -> Int.MAX_VALUE
            }

    companion object {
        /** Red Flag and Dynamic Duo are offered from wave 7 (wiki). */
        const val LATE_FROM_WAVE = 7

        /** The last wave of ordinary monsters before Sol Heredit's. */
        const val LAST_REGULAR_WAVE = 11

        /** The wiki: the first offer is always Blasphemy, Relentless and Frailty. */
        val FIRST_OFFER = listOf(Blasphemy, Relentless, Frailty)

        /**
         * How much likelier an upgrade of a picked modifier is than a new one. The wiki only says
         * upgrades are favoured; tsps uses twice as likely, and so does this.
         */
        const val UPGRADE_WEIGHT = 2

        private val byKey = entries.associateBy { it.key }

        fun ofKey(key: Int): ColosseumModifier? = byKey[key]
    }
}

/** A run's modifiers and their tiers. */
class ColosseumModifierSet {
    private val tiers = LinkedHashMap<ColosseumModifier, Int>()

    fun tier(modifier: ColosseumModifier): Int = tiers[modifier] ?: 0

    fun has(modifier: ColosseumModifier): Boolean = tier(modifier) > 0

    fun maxed(modifier: ColosseumModifier): Boolean = tier(modifier) >= modifier.maxTier

    fun add(modifier: ColosseumModifier) {
        tiers[modifier] = minOf(modifier.maxTier, tier(modifier) + 1)
    }

    /** Every modifier's Glory times its tier: what each cleared wave earns from them. */
    fun glory(): Int = tiers.entries.sumOf { (modifier, tier) -> modifier.glory * tier }

    val active: Map<ColosseumModifier, Int>
        get() = tiers

    /**
     * The three modifiers offered before [wave], in the enum's key order (as the capture shows
     * them). [roll] gives a number from 0 until its argument.
     */
    fun offer(wave: Int, roll: (Int) -> Int): List<ColosseumModifier> {
        if (wave == 1) {
            return ColosseumModifier.FIRST_OFFER.sortedBy { it.key }
        }
        val pool =
            ColosseumModifier.entries
                .filter { !maxed(it) && wave >= it.firstWave && wave <= it.lastWave }
                .map { it to if (has(it)) ColosseumModifier.UPGRADE_WEIGHT else 1 }
                .toMutableList()
        val picked = ArrayList<ColosseumModifier>()
        while (picked.size < OFFER_SIZE && pool.isNotEmpty()) {
            var at = roll(pool.sumOf { it.second })
            val index = pool.indexOfFirst { (_, weight) -> (at - weight).also { at = it } < 0 }
            picked += pool.removeAt(if (index < 0) pool.lastIndex else index).first
        }
        return picked.sortedBy { it.key }
    }

    companion object {
        const val OFFER_SIZE = 3
    }
}
