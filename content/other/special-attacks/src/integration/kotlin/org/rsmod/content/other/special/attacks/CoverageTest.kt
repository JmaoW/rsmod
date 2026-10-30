package org.rsmod.content.other.special.attacks

import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.runSpecialTest
import org.rsmod.game.inv.InvObj

class CoverageTest {
    /**
     * Every weapon the cache gives a special attack energy requirement has a special attack, bar
     * the ones other modules register.
     */
    @Test
    fun GameTestState.`every weapon with a special attack energy has a special attack`() =
        runSpecialTest { deps ->
            val weapons = objTypes.values.filter { deps.weapons.getSpecialEnergy(it) != null }
            val missing =
                weapons.filter { deps.registry[InvObj(it)] == null }.mapNotNull { it.internalName }
            val unexplained = missing.filterNot { it in NOT_REGISTERED }
            println("Registered ${weapons.size - missing.size} of ${weapons.size}; missing: $missing")
            check(unexplained.isEmpty()) { "No special attack for: $unexplained" }
        }

    private companion object {
        /**
         * Registered by other modules: the three shields by `dragonfire`, the revenant weapons by
         * `isle-of-souls` (its own `SpecialAttackCoverageTest` checks them all together).
         */
        val NOT_REGISTERED =
            setOf(
                "dragonfire_shield",
                "dragonfire_ward",
                "wyvern_shield",
                "wild_cave_webweaver_charged",
                "wild_cave_ursine_charged",
                "wild_cave_accursed_charged",
                "wild_cave_accursed_charged_recol",
            )
    }
}
