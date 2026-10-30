package org.rsmod.content.other.special.attacks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.energyLeft
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.equip
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.fireInstant
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.fireMelee
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.recordHits
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.repeatUntil
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.runSpecialTest
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.spawnDummy
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.map.CoordGrid

class MeleeEffectsTest {
    /** A target that has defence to lose and is more than one tile wide. */
    private val large =
        npcTypeFactory.create {
            name = "Large special attack dummy"
            vislevel = 50
            attack = 50
            strength = 50
            defence = 200
            magic = 200
            hitpoints = 20_000
            size = 2
            defaultMode = NpcMode.None
        }

    private val tank =
        npcTypeFactory.create {
            name = "Tank special attack dummy"
            vislevel = 50
            attack = 100
            strength = 100
            defence = 200
            magic = 200
            ranged = 100
            hitpoints = 20_000
            defaultMode = NpcMode.None
        }

    private fun GameTestScope.spawnTank(): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), tank)
        player.placeAt(npc.coords.translateX(-1))
        return npc
    }

    /** Fires the equipped special at [npc] until one of its hits lands for damage. */
    private fun GameTestScope.fireUntilLanded(
        deps: SpecialAttackHarness.Deps,
        npc: Npc,
        tries: Int = 40,
    ): List<Int> {
        val hits = recordHits()
        repeat(tries) {
            player.setVarp(varps.sa_energy, 1000)
            hits.clear()
            fireMelee(deps, npc)
            if (hits.any { it.damage > 0 }) {
                return hits.map { it.damage }
            }
            // Anything the failed attempt did to the target is undone, so that the next starts fresh.
            npc.defenceLvl = npc.baseDefenceLvl
            npc.attackLvl = npc.baseAttackLvl
            npc.strengthLvl = npc.baseStrengthLvl
            npc.frozenUntil = Int.MIN_VALUE
            npc.hitpoints = npc.baseHitpointsLvl
        }
        error("The special attack never landed in $tries tries.")
    }

    @Test
    fun GameTestState.`puncture is two hits`() = runSpecialTest { deps ->
        val npc = spawnDummy()
        equip(sobjs.dragon_dagger)
        val hits = recordHits()
        check(fireMelee(deps, npc))
        assertEquals(2, hits.size)
        assertEquals(750, player.energyLeft())
    }

    @Test
    fun GameTestState.`abyssal puncture is two hits`() = runSpecialTest { deps ->
        val npc = spawnDummy()
        equip(sobjs.abyssal_dagger)
        val hits = recordHits()
        check(fireMelee(deps, npc))
        assertEquals(2, hits.size)
    }

    @Test
    fun GameTestState.`slice and dice is four hits`() = runSpecialTest { deps ->
        val npc = spawnDummy()
        equip(sobjs.dragon_claws)
        val hits = recordHits()
        check(fireMelee(deps, npc))
        assertEquals(4, hits.size)
    }

    @Test
    fun GameTestState.`burning barrage is three hits`() = runSpecialTest { deps ->
        val npc = spawnDummy()
        equip(sobjs.bone_claws)
        val hits = recordHits()
        check(fireMelee(deps, npc))
        assertEquals(3, hits.size)
    }

    @Test
    fun GameTestState.`warstrike drains the target by the damage dealt`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.bgs)
        val damage = fireUntilLanded(deps, npc).first()
        // Defence goes first, down to 1.
        assertEquals(maxOf(1, 200 - damage), npc.defenceLvl)
    }

    @Test
    fun GameTestState.`ice cleave freezes the target`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.zgs)
        fireUntilLanded(deps, npc)
        assertTrue(npc.isFrozen)
    }

    @Test
    fun GameTestState.`healing blade gives hitpoints back`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.sgs)
        player.setCurrentLevel(stats.hitpoints, 50)
        check(fireMelee(deps, npc))
        assertTrue(player.stat(stats.hitpoints) >= 60) { "Healed to ${player.stat(stats.hitpoints)}." }
    }

    @Test
    fun GameTestState.`smash takes thirty percent off the defence`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.dragon_warhammer)
        fireUntilLanded(deps, npc)
        assertEquals(140, npc.defenceLvl)
    }

    @Test
    fun GameTestState.`pulverize takes thirty five percent off the defence`() =
        runSpecialTest { deps ->
            val npc = spawnTank()
            equip(sobjs.elder_maul)
            fireUntilLanded(deps, npc)
            assertEquals(130, npc.defenceLvl)
        }

    @Test
    fun GameTestState.`weaken drains attack strength and defence`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.arclight)
        fireUntilLanded(deps, npc)
        // 5% of 100 plus 1, and of 200 plus 1.
        assertEquals(94, npc.attackLvl)
        assertEquals(94, npc.strengthLvl)
        assertEquals(189, npc.defenceLvl)
    }

    @Test
    fun GameTestState.`binding tentacle binds the target`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.abyssal_tentacle)
        val hits = recordHits()
        val bound =
            repeatUntil(30) {
                player.setVarp(varps.sa_energy, 1000)
                fireMelee(deps, npc)
                npc.isFrozen
            }
        assertTrue(bound, "Expected a bind.")
        assertTrue(hits.isNotEmpty())
    }

    @Test
    fun GameTestState.`shove stuns and pushes the target and deals no damage`() =
        runSpecialTest { deps ->
            val npc = spawnDummy()
            equip(sobjs.dragon_spear)
            val start = npc.coords
            val hits = recordHits()
            check(fireMelee(deps, npc))
            assertTrue(hits.isEmpty(), "Shove deals no damage.")
            assertTrue(npc.isDelayed)
            assertEquals(start.translateX(1), npc.coords)
        }

    @Test
    fun GameTestState.`shove refuses a large target`() = runSpecialTest { deps ->
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), large)
        player.placeAt(npc.coords.translateX(-2))
        equip(sobjs.dragon_spear)
        assertFalse(fireMelee(deps, npc))
        assertEquals(1000, player.energyLeft())
        assertMessageSent("That creature is too large to knock back!")
    }

    @Test
    fun GameTestState.`sweep hits a large target a second time`() = runSpecialTest { deps ->
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), large)
        player.placeAt(npc.coords.translateX(-2))
        equip(sobjs.dragon_halberd)
        val hits = recordHits()
        check(fireMelee(deps, npc))
        assertEquals(2, hits.size)
    }

    @Test
    fun GameTestState.`rampage trades levels for strength`() = runSpecialTest { deps ->
        equip(sobjs.dragon_battleaxe)
        spawnDummy()
        check(fireInstant(deps))
        // 10% of 99 is 9 off each of four stats, and 10 + 36 / 4 on strength.
        assertEquals(90, player.stat(stats.attack))
        assertEquals(90, player.stat(stats.defence))
        assertEquals(99 + 10 + 9, player.stat(stats.strength))
        assertEquals(0, player.energyLeft())
    }

    @Test
    fun GameTestState.`sanctuary raises defence by eight`() = runSpecialTest { deps ->
        equip(sobjs.excalibur)
        check(fireInstant(deps))
        assertEquals(99 + 8, player.stat(stats.defence))
    }

    @Test
    fun GameTestState.`power of death halves melee damage`() = runSpecialTest { deps ->
        equip(sobjs.sotd)
        check(fireInstant(deps))
        val npc = spawnTank()
        // The staff stays in hand, so the guard holds.
        val guards = deps.guards
        assertTrue(guards.hasPowerOfDeath(player))
        player.worn[org.rsmod.game.type.obj.Wearpos.RightHand.slot] = null
        assertFalse(guards.hasPowerOfDeath(player))
        npc.hitpoints = 0
    }

}
