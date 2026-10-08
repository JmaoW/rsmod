package org.rsmod.api.testing.scope

import java.util.Collections
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.combat.mastery.CombatMasteryProvider
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.game.entity.Player

/**
 * The [CombatMasteryProvider] that test scopes register, so a test can give a player combat mastery
 * tiers without any content module. Tiers are kept per player, so tests that run at the same time
 * do not affect each other.
 */
public object TestCombatMasteries : CombatMasteryProvider {
    private val tiers = Collections.synchronizedMap(WeakHashMap<Player, IntArray>())

    /** Sets the unlocked [tier] (0 to [CombatMasteries.MAX_TIER]) of [style] for [player]. */
    public fun set(player: Player, style: MasteryStyle, tier: Int) {
        tiers.getOrPut(player) { IntArray(MasteryStyle.entries.size) }[style.ordinal] = tier
    }

    /** Sets every path of [player] to [tier]. */
    public fun setAll(player: Player, tier: Int) {
        for (style in MasteryStyle.entries) {
            set(player, style, tier)
        }
    }

    override fun tier(player: Player, style: MasteryStyle): Int =
        tiers[player]?.get(style.ordinal) ?: 0
}
