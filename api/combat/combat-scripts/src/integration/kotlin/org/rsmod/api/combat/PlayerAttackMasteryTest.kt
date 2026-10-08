package org.rsmod.api.combat

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varobjs
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.scope.TestCombatMasteries
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.utils.bits.withBits

class PlayerAttackMasteryTest {
    class Deps
    @Inject
    constructor(
        val manager: PlayerAttackManager,
        val speeds: WeaponSpeeds,
        val zoneUpdates: ZoneUpdateMap,
        val charges: CombatChargeManager,
    )

    private object StylesModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runMasteryTest(body: GameTestScope.(Deps, Npc) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            StylesModule,
            WeaponAttackStylesScript::class,
            NpcHitScript::class,
        ) { deps ->
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), dummy)
            player.placeAt(npc.coords.translateX(1))
            player.stats[stats.strength] = 99
            player.stats[stats.magic] = 99
            body(deps, npc)
        }

    private fun Deps.meleeMaxHit(player: Player, npc: Npc) =
        manager.calculateMeleeMaxHit(player, npc, null, null, 1.0)

    private fun Deps.rollMelee(player: Player, npc: Npc) =
        manager.rollMeleeMaxHit(player, npc, null, null, 1.0)

    @Test
    fun GameTestState.`melee rolls once without a mastery`() = runMasteryTest { deps, npc ->
        val maxHit = deps.meleeMaxHit(player, npc)
        assertTrue(maxHit > 5, "test needs a max hit above 5, got $maxHit")

        random.next = 3
        random.next = 4 // would be the second roll
        assertEquals(3, deps.rollMelee(player, npc))
    }

    @Test
    fun GameTestState.`melee one can roll twice and keep the highest`() =
        runMasteryTest { deps, npc ->
            TestCombatMasteries.set(player, MasteryStyle.Melee, 1)

            // First roll 3, chance roll 10 (under 25), second roll 5.
            random.next = 3
            random.next = 10
            random.next = 5
            assertEquals(5, deps.rollMelee(player, npc))

            // The chance roll misses: the first roll stands.
            random.next = 4
            random.next = 90
            assertEquals(4, deps.rollMelee(player, npc))
        }

    @Test
    fun GameTestState.`passive accuracy doubles the chance to hit`() = runMasteryTest { deps, npc ->
        fun hits(): Int {
            var count = 0
            repeat(4_000) {
                if (deps.manager.rollMeleeAccuracy(player, npc, null, null, null, 1.0)) {
                    count++
                }
            }
            return count
        }

        val without = hits()
        TestCombatMasteries.set(player, MasteryStyle.Melee, 3)
        val with = hits()

        assertTrue(without in 1..3_990, "expected some but not all hits without, got $without")
        assertTrue(
            with > without * 3 / 2,
            "expected far more hits with the passive: $without -> $with",
        )
    }

    @Test
    fun GameTestState.`ranged one lifts a low roll to thirty percent of the max hit`() =
        runMasteryTest { deps, npc ->
            player.stats[stats.ranged] = 99
            TestCombatMasteries.set(player, MasteryStyle.Ranged, 1)
            val maxHit = deps.manager.calculateRangedMaxHit(player, npc, null, null, 1.0, 0)
            assertTrue(maxHit >= 10, "test needs a max hit of at least 10, got $maxHit")

            random.next = 1
            assertEquals(
                maxHit * 30 / 100,
                deps.manager.rollRangedMaxHit(player, npc, null, null, 1.0, 0),
            )
        }

    @Test
    fun GameTestState.`attack rate masteries shorten the weapon delay by path`() =
        runMasteryTest { deps, _ ->
            val speeds = deps.speeds
            // Unarmed has the default attack rate of 4 ticks; spells take 5 and powered staves 4.
            assertEquals(4, speeds.playerAttackDelay(player, MasteryStyle.Melee))
            assertEquals(5, speeds.playerSpellDelay(player))
            assertEquals(4, speeds.playerStaffDelay(player))

            TestCombatMasteries.set(player, MasteryStyle.Melee, 3)
            assertEquals(3, speeds.playerAttackDelay(player, MasteryStyle.Melee))
            assertEquals(4, speeds.playerAttackDelay(player, MasteryStyle.Ranged))
            assertEquals(5, speeds.playerSpellDelay(player))

            TestCombatMasteries.set(player, MasteryStyle.Melee, 5)
            assertEquals(2, speeds.playerAttackDelay(player, MasteryStyle.Melee))

            TestCombatMasteries.set(player, MasteryStyle.Magic, 3)
            assertEquals(4, speeds.playerSpellDelay(player))
            assertEquals(3, speeds.playerStaffDelay(player))
            assertEquals(4, speeds.playerMagicDelay(player, 5)) // Tumeken's Shadow

            TestCombatMasteries.set(player, MasteryStyle.Magic, 5)
            assertEquals(2, speeds.playerSpellDelay(player))
            assertEquals(2, speeds.playerStaffDelay(player))
            assertEquals(2, speeds.playerMagicDelay(player, 5))
        }

    private val punch = CombatAttack.Melee(null, null, null, CombatStance.Stance1)

    @Test
    fun GameTestState.`melee two queues a silent echo that lands after the hit`() =
        runMasteryTest { deps, npc ->
            TestCombatMasteries.set(player, MasteryStyle.Melee, 2)
            val before = npc.hitpoints

            // Chance roll 0 (an echo), accuracy roll 0 (always lands), damage roll 4.
            random.next = 0
            random.next = 0
            random.next = 4
            assertEquals(listOf(4), deps.manager.queueMeleeEchoes(player, npc, punch))

            advance(ticks = 1)
            assertEquals(before, npc.hitpoints)
            advance(ticks = 1)
            assertEquals(before - 4, npc.hitpoints)
        }

    @Test
    fun GameTestState.`melee six echoes chain one tick apart`() = runMasteryTest { deps, npc ->
        TestCombatMasteries.set(player, MasteryStyle.Melee, 6)
        val before = npc.hitpoints

        // Two echoes (chance, accuracy, damage each), then a chance roll that ends the chain.
        for (roll in intArrayOf(0, 0, 3, 0, 0, 5, 99)) {
            random.next = roll
        }
        assertEquals(listOf(3, 5), deps.manager.queueMeleeEchoes(player, npc, punch))

        advance(ticks = 2)
        assertEquals(before - 3, npc.hitpoints)
        advance(ticks = 1)
        assertEquals(before - 8, npc.hitpoints)
    }

    private fun Deps.ghosts(npc: Npc): List<MapProjAnimV2> =
        zoneUpdates[ZoneKey.from(npc.coords)]?.toList().orEmpty().filterIsInstance<MapProjAnimV2>()

    @Test
    fun GameTestState.`an echo sends a ghost that arrives as the echo lands`() =
        runMasteryTest { deps, npc ->
            TestCombatMasteries.set(player, MasteryStyle.Melee, 2)
            val before = deps.ghosts(npc).size

            random.next = 0
            random.next = 0
            random.next = 4
            deps.manager.queueMeleeEchoes(player, npc, punch)

            val ghost = deps.ghosts(npc).drop(before).single()
            assertTrue(ghost.id in 3185..3187, "expected a league melee mastery ghost: ${ghost.id}")
            // The echo lands 2 game cycles after the hit is queued: 60 client cycles. The ghost
            // flies for 41, so it leaves at 19.
            assertEquals(60, ghost.endTime)
            assertEquals(19, ghost.startTime)
        }

    @Test
    fun GameTestState.`each echo in a chain sends its own ghost one tick later`() =
        runMasteryTest { deps, npc ->
            TestCombatMasteries.set(player, MasteryStyle.Melee, 6)
            val before = deps.ghosts(npc).size

            for (roll in intArrayOf(0, 0, 3, 0, 0, 5, 99)) {
                random.next = roll
            }
            deps.manager.queueMeleeEchoes(player, npc, punch)

            val ghosts = deps.ghosts(npc).drop(before)
            assertEquals(listOf(60, 90), ghosts.map { it.endTime })
            assertEquals(listOf(19, 49), ghosts.map { it.startTime })
        }

    @Test
    fun GameTestState.`a missed echo still sends its ghost`() = runMasteryTest { deps, npc ->
        TestCombatMasteries.set(player, MasteryStyle.Melee, 2)
        val before = deps.ghosts(npc).size

        // Chance roll 0 (an echo), accuracy roll 9999. A very high defence keeps the hit chance
        // under that.
        npc.defenceLvl = 10_000
        random.next = 0
        random.next = 9_999
        assertEquals(listOf(0), deps.manager.queueMeleeEchoes(player, npc, punch))

        assertEquals(1, deps.ghosts(npc).size - before)
    }

    @Test
    fun GameTestState.`no ghost without an echo`() = runMasteryTest { deps, npc ->
        val before = deps.ghosts(npc).size

        deps.manager.queueMeleeEchoes(player, npc, punch)

        assertEquals(before, deps.ghosts(npc).size)
    }

    @Test
    fun GameTestState.`no echoes without the mastery`() = runMasteryTest { deps, npc ->
        val before = npc.hitpoints

        random.next = 0
        assertEquals(emptyList<Int>(), deps.manager.queueMeleeEchoes(player, npc, punch))

        advance(ticks = 3)
        assertEquals(before, npc.hitpoints)
    }

    @Test
    fun GameTestState.`melee four sometimes heals from a hit`() = runMasteryTest { deps, npc ->
        player.setBaseLevel(stats.hitpoints, 99)
        player.setCurrentLevel(stats.hitpoints, 50)
        TestCombatMasteries.set(player, MasteryStyle.Melee, 4)

        // First roll 10, no re-roll (99), then the heal chance roll 0 (under 5).
        random.next = 10
        random.next = 99
        random.next = 0
        assertEquals(10, deps.rollMelee(player, npc))

        // 40% of 10 is 4. The healing passive that tier four includes makes it 4.8, so 4.
        assertEquals(54, player.hitpoints)
    }

    @Test
    fun GameTestState.`ranged four heals on every fifth hit`() = runMasteryTest { deps, npc ->
        player.setBaseLevel(stats.hitpoints, 99)
        player.setCurrentLevel(stats.hitpoints, 50)
        player.stats[stats.ranged] = 99
        TestCombatMasteries.set(player, MasteryStyle.Ranged, 4)

        repeat(4) {
            random.next = 7
            deps.manager.rollRangedMaxHit(player, npc, null, null, 1.0, 0)
        }
        assertEquals(50, player.hitpoints)

        random.next = 7
        deps.manager.rollRangedMaxHit(player, npc, null, null, 1.0, 0)
        // Five hitpoints and the healing passive's 20%.
        assertEquals(56, player.hitpoints)
    }

    @Test
    fun GameTestState.`magic four heals a tenth of a big hit`() = runMasteryTest { deps, npc ->
        player.setBaseLevel(stats.hitpoints, 99)
        player.setCurrentLevel(stats.hitpoints, 50)
        TestCombatMasteries.set(player, MasteryStyle.Magic, 4)
        // Magic two (included in tier four) raises the first attack's max hit by 40%.
        val maxHit = deps.manager.calculateStaffMaxHit(player, npc, 20, 1.4)

        random.next = maxHit
        val damage = deps.manager.rollStaffMaxHit(player, npc, 20, 1.0)

        // Magic one adds half again to a roll over 90% of the max hit.
        assertEquals(maxHit + maxHit / 2, damage)
        val heal = (damage / 10) * 120 / 100
        assertEquals(50 + heal, player.hitpoints)
    }

    @Test
    fun GameTestState.`the first passive saves a weapon charge`() = runMasteryTest { deps, _ ->
        val varobj = varobjs.tumeken_charges
        player.worn[Wearpos.RightHand.slot] =
            InvObj(objs.tumekens_shadow, vars = 0.withBits(varobj.bits, 10))
        TestCombatMasteries.set(player, MasteryStyle.Melee, 1)

        // A roll under 95 saves the charge, and the use still counts as a success.
        random.next = 94
        assertTrue(
            deps.charges.attemptDetractWeapon(player, varobj) is ObjChargeManager.Uncharge.Success
        )
        assertEquals(10, deps.charges.getWeaponCharges(player, varobj))

        random.next = 95
        deps.charges.attemptDetractWeapon(player, varobj)
        assertEquals(9, deps.charges.getWeaponCharges(player, varobj))
    }

    @Test
    fun GameTestState.`without the passive a charge is always used and no roll is made`() =
        runMasteryTest { deps, _ ->
            val varobj = varobjs.tumeken_charges
            player.worn[Wearpos.RightHand.slot] =
                InvObj(objs.tumekens_shadow, vars = 0.withBits(varobj.bits, 10))

            deps.charges.attemptDetractWeapon(player, varobj)

            assertEquals(9, deps.charges.getWeaponCharges(player, varobj))
        }

    @Test
    fun GameTestState.`magic one raises a big staff hit by half`() = runMasteryTest { deps, npc ->
        TestCombatMasteries.set(player, MasteryStyle.Magic, 1)
        // A staff max hit comes from the base max hit given, so this does not depend on gear.
        val maxHit = deps.manager.calculateStaffMaxHit(player, npc, 20, 1.0)
        assertTrue(maxHit >= 10, "test needs a max hit of at least 10, got $maxHit")

        random.next = maxHit
        assertEquals(maxHit + maxHit / 2, deps.manager.rollStaffMaxHit(player, npc, 20, 1.0))

        random.next = 1
        assertEquals(1, deps.manager.rollStaffMaxHit(player, npc, 20, 1.0))
    }

    @Test
    fun GameTestState.`magic six maxes a hit on a target about to die`() =
        runMasteryTest { deps, npc ->
            TestCombatMasteries.set(player, MasteryStyle.Magic, 6)
            npc.hitpoints = 3 // under any max hit

            random.next = 1
            val damage = deps.manager.rollStaffMaxHit(player, npc, 20, 1.0)

            // Magic two (first attack, +40%) raises the max hit the roll is made against.
            val raised = deps.manager.calculateStaffMaxHit(player, npc, 20, 1.4)
            assertEquals(raised + raised / 2, damage)
        }

    private companion object {
        private val dummy =
            npcTypeFactory.create {
                name = "Mastery dummy"
                hitpoints = 500
                defaultMode = NpcMode.None
            }
    }
}
