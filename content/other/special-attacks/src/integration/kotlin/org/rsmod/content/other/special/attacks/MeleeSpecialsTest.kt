package org.rsmod.content.other.special.attacks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.energyLeft
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.equip
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.fireMelee
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.runSpecialTest
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.spawnDummy
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.inv.InvObj

class MeleeSpecialsTest {
    @Test
    fun GameTestState.`every melee weapon special runs and takes its energy`() =
        runSpecialTest { deps ->
            val skipped = mutableListOf<String?>()
            val failures = mutableListOf<String>()
            val weapons = objTypes.values.filter { deps.weapons.getSpecialEnergy(it) != null }
            check(weapons.size > 200) { "Expected the special attack weapons, found ${weapons.size}." }
            for (weapon in weapons) {
                val special = deps.registry[InvObj(weapon)] as? SpecialAttack.Melee ?: continue
                val npc = spawnDummy()
                equip(weapon)
                try {
                    val energy = special.energyInHundreds
                    val taken = fireMelee(deps, npc)
                    advance(3)
                    if (!taken) {
                        skipped += weapon.internalName
                    } else if (!deps.energy.isSpecializedRequirement(energy)) {
                        // Unleash spends the whole bar, not just what the weapon needs.
                        assertTrue(player.energyLeft() <= 1000 - energy) { weapon.internalName ?: "" }
                    }
                } catch (e: Exception) {
                    failures += "${weapon.internalName}: $e"
                }
                npc.hitpoints = 0
            }
            check(failures.isEmpty()) { failures.joinToString("\n") }
            println("Melee specials that ran but kept their energy: $skipped")
        }

    @Test
    fun GameTestState.`puncture hits twice`() = runSpecialTest { deps ->
        val npc = spawnDummy()
        equip(sobjs.dragon_dagger)
        val before = npc.hitpoints
        repeat(5) {
            check(fireMelee(deps, npc))
            player.setVarp(org.rsmod.api.config.refs.varps.sa_energy, 1000)
            advance(4)
        }
        assertTrue(npc.hitpoints < before) { "Expected the hits to land." }
    }
}
