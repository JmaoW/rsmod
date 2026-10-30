package org.rsmod.content.other.special.attacks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.manager.PlayerPoisonEvents
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.hit.queueHit
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
import org.rsmod.content.other.special.attacks.support.DotKind
import org.rsmod.content.other.special.attacks.support.SpecialPlayerEvents
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class StatusAndExoticTest {
    private val tank =
        npcTypeFactory.create {
            name = "Status special attack dummy"
            vislevel = 50
            attack = 100
            strength = 100
            defence = 200
            magic = 200
            hitpoints = 20_000
            defaultMode = NpcMode.None
        }

    private fun GameTestScope.spawnTank(): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), tank)
        player.placeAt(npc.coords.translateX(-1))
        return npc
    }

    @Test
    fun GameTestState.`burn hurts a little each interval`() = runSpecialTest { deps ->
        val npc = spawnTank()
        assertTrue(deps.dots.burn(npc, player, damage = 3, interval = 2))
        assertTrue(deps.dots.has(npc, DotKind.Burn))
        advance(10)
        assertEquals(20_000 - 3, npc.hitpoints)
        assertFalse(deps.dots.has(npc, DotKind.Burn))
    }

    @Test
    fun GameTestState.`a weaker dose does not replace a stronger one`() = runSpecialTest { deps ->
        val npc = spawnTank()
        assertTrue(deps.dots.burn(npc, player, damage = 10, interval = 2))
        assertFalse(deps.dots.burn(npc, player, damage = 4, interval = 2))
        assertEquals(10, deps.dots.remaining(npc, DotKind.Burn))
    }

    @Test
    fun GameTestState.`poison hits every thirty ticks and weakens`() = runSpecialTest { deps ->
        val npc = spawnTank()
        assertTrue(deps.dots.poison(npc, player, startDamage = 2))
        advance(31)
        assertEquals(20_000 - 2, npc.hitpoints)
        // Ten hits in all: five of 2 and five of 1.
        assertEquals(2 * 5 + 1 * 5 - 2, deps.dots.remaining(npc, DotKind.Poison))
    }

    @Test
    fun GameTestState.`bleed comes in chunks`() = runSpecialTest { deps ->
        val npc = spawnTank()
        assertTrue(deps.dots.bleed(npc, player, damage = 25, chunk = 10, interval = 1))
        advance(5)
        assertEquals(20_000 - 25, npc.hitpoints)
    }

    @Test
    fun GameTestState.`a poisoned dagger poisons now and then`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.dragon_dagger_p_plus_plus)
        val poison = deps.poison
        assertEquals(6, poison.poisonOf(player.worn[Wearpos.RightHand.slot], null))
        val poisoned =
            repeatUntil(60) {
                player.setVarp(varps.sa_energy, 1000)
                fireMelee(deps, npc)
                deps.dots.has(npc, DotKind.Poison)
            }
        assertTrue(poisoned, "Sixty dragon dagger (p++) specials never poisoned.")
    }

    @Test
    fun GameTestState.`the poison of poisoned weapons follows their name`() = runSpecialTest { deps ->
        val poison = deps.poison
        assertEquals(4, poison.poisonOf(org.rsmod.game.inv.InvObj(sobjs.dragon_dagger_p), null))
        assertEquals(5, poison.poisonOf(org.rsmod.game.inv.InvObj(sobjs.dragon_spear_p_plus), null))
        assertNull(poison.poisonOf(org.rsmod.game.inv.InvObj(sobjs.dragon_dagger), null))
        assertEquals(4, poison.poisonOf(org.rsmod.game.inv.InvObj(sobjs.abyssal_tentacle), null))
    }

    @Test
    fun GameTestState.`power of death halves melee damage and only melee`() =
        runSpecialTest { deps ->
            equip(sobjs.sotd)
            assertTrue(fireInstant(deps))
            val before = player.stat(stats.hitpoints)
            player.queueHit(1, HitType.Melee, 20)
            player.queueHit(1, HitType.Ranged, 20)
            advance(3)
            assertEquals(before - 10 - 20, player.stat(stats.hitpoints))
        }

    @Test
    fun GameTestState.`spear wall makes the wielder immune to melee`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.vestas_spear)
        assertTrue(fireMelee(deps, npc))
        val before = player.stat(stats.hitpoints)
        player.queueHit(1, HitType.Melee, 20)
        advance(3)
        assertEquals(before, player.stat(stats.hitpoints))
    }

    @Test
    fun GameTestState.`wrath makes a npc take a quarter more damage`() = runSpecialTest { deps ->
        val npc = spawnTank()
        deps.guards.makeVulnerable(npc, ticks = 10, percent = 25)
        npc.queueHit(player, 1, HitType.Melee, 40)
        advance(3)
        assertEquals(20_000 - 50, npc.hitpoints)
    }

    @Test
    fun GameTestState.`sever locks the protection prayers of a player`() = runSpecialTest { deps ->
        val other = registerPlayer(CoordGrid(0, 50, 50, 11, 10))
        player.placeAt(CoordGrid(0, 50, 50, 10, 10))
        other.setVarBit(varbits.protect_from_melee, 1)
        equip(sobjs.dragon_scimitar)
        val locked =
            repeatUntil(40) {
                player.setVarp(varps.sa_energy, 1000)
                fireMelee(deps, other)
                deps.prayerLocks.isLocked(other)
            }
        assertTrue(locked, "Sever never landed.")
        assertEquals(0, other.vars[varbits.protect_from_melee])
    }

    @Test
    fun GameTestState.`energy drain takes a tenth of the run energy`() = runSpecialTest { deps ->
        val other = registerPlayer(CoordGrid(0, 50, 50, 11, 10))
        player.placeAt(CoordGrid(0, 50, 50, 10, 10))
        other.runEnergy = 10_000
        player.runEnergy = 2_000
        equip(sobjs.abyssal_whip)
        val drained =
            repeatUntil(40) {
                player.setVarp(varps.sa_energy, 1000)
                fireMelee(deps, other)
                other.runEnergy < 10_000
            }
        assertTrue(drained, "The whip never drained any run energy.")
        assertEquals(9_000, other.runEnergy)
        assertEquals(3_000, player.runEnergy)
    }

    @Test
    fun GameTestState.`tumekens light needs fifty prayer and cures`() = runSpecialTest { deps ->
        equip(sobjs.keris_partisan_sun)
        player.setCurrentLevel(stats.prayer, 10)
        assertFalse(fireInstant(deps))
        assertEquals(1000, player.energyLeft())

        player.setCurrentLevel(stats.prayer, 99)
        player.setCurrentLevel(stats.hitpoints, 40)
        player.setCurrentLevel(stats.attack, 50)
        assertTrue(fireInstant(deps))
        assertEquals(49, player.stat(stats.prayer))
        assertEquals(118, player.stat(stats.hitpoints))
        assertEquals(99, player.stat(stats.attack))
        assertEquals(10_000, player.runEnergy)
    }

    @Test
    fun GameTestState.`virulence needs a sickness`() = runSpecialTest { deps ->
        equip(sobjs.noxious_halberd)
        assertFalse(fireInstant(deps))
        assertEquals(1000, player.energyLeft())
        // A player's poison belongs to content that owns it, which answers the special.
        eventBus.subscribeUnbound(SpecialPlayerEvents.CureSickness::class.java) { damage += 4 }
        assertTrue(fireInstant(deps))
        assertNotNull(deps.buffs.of(player))
        assertEquals(4, deps.buffs.of(player)!!.flatDamage)
    }

    @Test
    fun GameTestState.`poisoning a player asks content that owns the player's poison`() =
        runSpecialTest { deps ->
            val victim = registerPlayer(CoordGrid(0, 50, 50, 12, 10))
            assertFalse(deps.dots.poison(victim, player, 4), "nothing owns the poison")
            assertFalse(deps.dots.has(victim, DotKind.Poison), "no special attack dot on a player")

            val asked = mutableListOf<PlayerPoisonEvents.Apply>()
            eventBus.subscribeUnbound(PlayerPoisonEvents.Apply::class.java) {
                applied = true
                asked += this
            }
            assertTrue(deps.dots.poison(victim, player, 4))
            assertEquals(4, asked.single().damage)
            assertEquals(victim, asked.single().target)
            assertFalse(deps.dots.has(victim, DotKind.Poison))
        }

    @Test
    fun GameTestState.`virulence asks content that owns sickness`() = runSpecialTest { deps ->
        eventBus.subscribeUnbound(SpecialPlayerEvents.CureSickness::class.java) { damage += 6 }
        equip(sobjs.noxious_halberd)
        assertTrue(fireInstant(deps))
        assertEquals(6, deps.buffs.of(player)!!.flatDamage)
    }

    @Test
    fun GameTestState.`behead needs a soul stack`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.soulreaper)
        assertFalse(fireMelee(deps, npc))
        deps.stacks.gain(player)
        deps.stacks.gain(player)
        player.setCurrentLevel(stats.hitpoints, 50)
        val hits = recordHits()
        assertTrue(fireMelee(deps, npc))
        assertEquals(1, hits.size)
        assertEquals(0, deps.stacks.count(player))
        assertEquals(66, player.stat(stats.hitpoints))
    }

    @Test
    fun GameTestState.`entice buffs the wielder and their allies`() = runSpecialTest { deps ->
        deps.pvp.register { _, _ -> "Allies." }
        val ally = registerPlayer(CoordGrid(0, 50, 50, 12, 10))
        player.placeAt(CoordGrid(0, 50, 50, 10, 10))
        advance(1)
        equip(sobjs.soulflame_horn)
        assertTrue(fireInstant(deps))
        assertNotNull(deps.buffs.of(player))
        assertNotNull(deps.buffs.of(ally))
        assertEquals(1000 - 250 - 100, player.energyLeft())
    }

    @Test
    fun GameTestState.`macuahuitl needs its set`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.dual_macuahuitl)
        assertFalse(fireMelee(deps, npc))
        assertMessageSent("This special attack requires the full Bloodrager set to be worn.")
    }

    @Test
    fun GameTestState.`khopesh strikes twice`() = runSpecialTest { deps ->
        val npc = spawnTank()
        equip(sobjs.thunder_khopesh)
        val hits = recordHits()
        assertTrue(fireMelee(deps, npc))
        assertEquals(2, hits.size)
    }

    @Test
    fun GameTestState.`the dummy stays put for the other tests`() = runSpecialTest { _ ->
        val npc = spawnDummy()
        assertEquals(10_000, npc.hitpoints)
    }
}
