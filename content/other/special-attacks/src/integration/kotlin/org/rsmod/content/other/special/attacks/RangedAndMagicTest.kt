package org.rsmod.content.other.special.attacks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.energyLeft
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.equip
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.equipRanged
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.fireRanged
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.fireStaff
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.recordHits
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.runSpecialTest
import org.rsmod.content.other.special.attacks.SpecialAttackHarness.spawnDummy
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class RangedAndMagicTest {
    private val tank =
        npcTypeFactory.create {
            name = "Ranged special attack dummy"
            vislevel = 50
            attack = 100
            strength = 100
            defence = 200
            magic = 200
            ranged = 100
            hitpoints = 20_000
            defaultMode = NpcMode.None
        }

    /** A target nobody ever hits with an ordinary roll. */
    private val fortress =
        npcTypeFactory.create {
            name = "Impossible special attack dummy"
            vislevel = 50
            defence = 20_000
            magic = 20_000
            hitpoints = 20_000
            defaultMode = NpcMode.None
        }

    private fun GameTestScope.spawnTank(): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), tank)
        player.placeAt(npc.coords.translateX(-3))
        return npc
    }

    private fun GameTestScope.arrows(): Int = player.worn[Wearpos.Quiver.slot]?.count ?: 0

    @Test
    fun GameTestState.`snapshot is two arrows`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.magic_shortbow, "rune_arrow")
        val hits = recordHits()
        assertTrue(fireRanged(deps, npc))
        assertEquals(2, hits.size)
        assertEquals(100 - 2, arrows())
        assertEquals(1000 - 550, player.energyLeft())
    }

    @Test
    fun GameTestState.`snapshot needs two arrows`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.magic_shortbow, "rune_arrow", count = 1)
        assertFalse(fireRanged(deps, npc))
        assertEquals(1000, player.energyLeft())
    }

    @Test
    fun GameTestState.`powershot cannot miss`() = runSpecialTest { deps ->
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), fortress)
        player.placeAt(npc.coords.translateX(-3))
        equipRanged(sobjs.magic_longbow, "rune_arrow")
        val hits = recordHits()
        repeat(20) {
            player.setVarp(varps.sa_energy, 1000)
            fireRanged(deps, npc)
        }
        assertTrue(hits.any { it.damage > 0 }) { "Not one of 20 powershots dealt damage." }
    }

    @Test
    fun GameTestState.`soulshot drains the magic level by the damage`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.daganoth_cave_magic_shortbow, "rune_arrow")
        val hits = recordHits()
        repeat(10) {
            player.setVarp(varps.sa_energy, 1000)
            fireRanged(deps, npc)
        }
        val total = hits.sumOf { it.damage }
        assertEquals(maxOf(1, 200 - total), npc.magicLvl)
    }

    @Test
    fun GameTestState.`duality throws two knives`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.dragon_knife)
        player.worn[Wearpos.RightHand.slot] = org.rsmod.game.inv.InvObj(sobjs.dragon_knife, 50)
        val hits = recordHits()
        assertTrue(fireRanged(deps, npc))
        assertEquals(2, hits.size)
        assertEquals(48, player.worn[Wearpos.RightHand.slot]?.count)
    }

    @Test
    fun GameTestState.`concentrated shot slows the next attack`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.heavy_ballista, "dragon_javelin")
        player.actionDelay = player.currentMapClock + 7
        assertTrue(fireRanged(deps, npc))
        assertEquals(player.currentMapClock + 7 + 4, player.actionDelay)
    }

    @Test
    fun GameTestState.`armadyl eye shoots one bolt`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equipRanged(sobjs.acb, "xbows_crossbow_bolts_runite")
        val hits = recordHits()
        assertTrue(fireRanged(deps, npc))
        assertEquals(1, hits.size)
        assertEquals(1000 - 500, player.energyLeft())
    }

    @Test
    fun GameTestState.`chainhit costs ten percent more for each target`() = runSpecialTest { deps ->
        // Outside multi-combat nothing chains, so the axe costs its own ten percent only.
        val npc = spawnTank()
        equipRanged(sobjs.rune_thrownaxe)
        player.worn[Wearpos.RightHand.slot] = org.rsmod.game.inv.InvObj(sobjs.rune_thrownaxe, 10)
        assertTrue(fireRanged(deps, npc))
        assertEquals(900, player.energyLeft())
    }

    @Test
    fun GameTestState.`immolate hurts the target`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.nightmare_staff_volatile)
        repeat(10) {
            player.setVarp(varps.sa_energy, 1000)
            fireStaff(deps, npc)
        }
        advance(6)
        assertTrue(npc.hitpoints < 20_000) { "Immolate dealt nothing in 10 casts." }
    }

    @Test
    fun GameTestState.`invocate gives prayer back`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.nightmare_staff_eldritch)
        player.setCurrentLevel(stats.prayer, 10)
        repeat(10) {
            player.setVarp(varps.sa_energy, 1000)
            fireStaff(deps, npc)
        }
        assertTrue(player.stat(stats.prayer) > 10) { "Invocate gave no prayer." }
    }

    @Test
    fun GameTestState.`pulsate deals between 75 and 150`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.verzik_special_weapon)
        val before = npc.hitpoints
        assertTrue(fireStaff(deps, npc))
        advance(6)
        val dealt = before - npc.hitpoints
        assertTrue(dealt in 75..150) { "Pulsate dealt $dealt." }
    }

    @Test
    fun GameTestState.`soul rend drains the magic level`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.eye_of_ayak)
        repeat(10) {
            player.setVarp(varps.sa_energy, 1000)
            fireStaff(deps, npc)
        }
        advance(6)
        assertTrue(npc.magicLvl < 200) { "Soul Rend drained nothing in 10 casts." }
    }
}
