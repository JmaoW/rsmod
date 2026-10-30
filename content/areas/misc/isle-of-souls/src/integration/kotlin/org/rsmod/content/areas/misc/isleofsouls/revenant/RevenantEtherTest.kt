package org.rsmod.content.areas.misc.isleofsouls.revenant

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropResult
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.inv.WornOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonRevenantScript
import org.rsmod.content.areas.misc.isleofsouls.dungeons.RevenantNpcs
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

internal object WornTestComponents : ComponentReferences() {
    val hands_slot = find("wornitems:slot9")
    val weapon_slot = find("wornitems:slot3")
}

class RevenantEtherTest {
    private object EtherModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val bracelet: RevenantBraceletScript,
        val tracker: LeagueTaskTracker,
        val teams: LeagueTeams,
    )

    private fun GameTestState.runEtherTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            EtherModule,
            RevenantEtherScript::class,
            RevenantBraceletScript::class,
            HeldOpScript::class,
            HeldUOpScript::class,
        ) { deps ->
            player.clearInv()
            body(deps)
        }

    /** Uses the item in [first] on the item in [second]. */
    private fun GameTestScope.useOn(first: Int, second: Int) {
        val comp = Component(components.inv_items.packed)
        val firstObj = objTypes[player.inv[first]!!]
        val secondObj = objTypes[player.inv[second]!!]
        eventBus.publish(IfOverlayButtonT(player, first, firstObj, second, secondObj, comp, comp))
    }

    /** Clicks option [op] (1 to 5) of the item in [slot]. */
    private fun GameTestScope.option(slot: Int, op: Int) {
        val obj = objTypes[player.inv[slot]!!]
        val button =
            when (op) {
                1 -> IfButtonOp.Op2
                2 -> IfButtonOp.Op3
                3 -> IfButtonOp.Op4
                4 -> IfButtonOp.Op6
                else -> IfButtonOp.Op7
            }
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, obj, button))
    }

    private fun GameTestScope.ether(): Int =
        player.inv.sumOf { if (it?.id == ether_objs.ether.id) it.count else 0 }

    private fun GameTestScope.give(slot: Int, obj: ObjType, count: Int = 1) {
        player.inv[slot] = InvObj(obj, count)
    }

    private fun GameTestScope.answerCount(count: Int) {
        advance(ticks = 2)
        player.resumeCountDialog(count)
        advance(ticks = 2)
    }

    private fun GameTestScope.answerChoice(option: Int) {
        advance(ticks = 2)
        player.resumePauseButton(components.chatmenu_pbutton, option)
        advance(ticks = 2)
    }

    // The weapons.

    @Test
    fun GameTestState.`ether activates a weapon with 1000 and the rest can be spent`() =
        runEtherTest {
            give(0, ether_objs.craw_uncharged)
            give(1, ether_objs.ether, 3_000)

            useOn(0, 1)
            answerCount(2_500)

            val bow = player.inv[0]
            assertTrue(bow != null && bow.id == ether_objs.craw.id, "the bow is charged now")
            assertEquals(2_500, RevenantEther.stored(bow))
            assertEquals(1_500, RevenantEther.usable(bow))
            assertEquals(500, ether())
        }

    @Test
    fun GameTestState.`a weapon is not activated with less than 1000 ether`() = runEtherTest {
        give(0, ether_objs.viggora_uncharged)
        give(1, ether_objs.ether, 999)

        useOn(0, 1)
        advance(ticks = 3)

        assertEquals(ether_objs.viggora_uncharged.id, player.inv[0]!!.id)
        assertEquals(999, ether())
    }

    @Test
    fun GameTestState.`a weapon asked for less than 1000 to activate keeps the ether`() =
        runEtherTest {
            give(0, ether_objs.viggora_uncharged)
            give(1, ether_objs.ether, 5_000)

            useOn(0, 1)
            answerCount(400)

            assertEquals(ether_objs.viggora_uncharged.id, player.inv[0]!!.id)
            assertEquals(5_000, ether())
        }

    @Test
    fun GameTestState.`a charged weapon takes more ether up to 17000 in all`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(
                InvObj(ether_objs.thammaron),
                ether_objs.thammaron,
                RevenantEther.ACTIVATION + 15_000,
            )
        give(1, ether_objs.ether, 5_000)

        useOn(0, 1)
        answerCount(5_000)

        assertEquals(17_000, RevenantEther.stored(player.inv[0]))
        assertEquals(16_000, RevenantEther.usable(player.inv[0]))
        assertEquals(4_000, ether())
    }

    @Test
    fun GameTestState.`uncharging gives all the ether back, the activation too`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(InvObj(ether_objs.craw), ether_objs.craw, 1_000 + 777)

        option(0, 5)
        answerChoice(1)

        assertEquals(ether_objs.craw_uncharged.id, player.inv[0]!!.id)
        assertEquals(0, RevenantEther.stored(player.inv[0]))
        assertEquals(1_777, ether())
    }

    @Test
    fun GameTestState.`cancelling the uncharge keeps the weapon charged`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(InvObj(ether_objs.craw), ether_objs.craw, 1_000 + 777)

        option(0, 5)
        answerChoice(2)

        assertEquals(ether_objs.craw.id, player.inv[0]!!.id)
        assertEquals(1_777, RevenantEther.stored(player.inv[0]))
        assertEquals(0, ether())
    }

    @Test
    fun GameTestState.`dismantling an uncharged weapon gives 7500 ether`() = runEtherTest {
        give(0, ether_objs.thammaron_uncharged)

        option(0, 3)
        answerChoice(1)

        assertFalse(player.inv.any { it?.id == ether_objs.thammaron_uncharged.id })
        assertEquals(7_500, ether())
    }

    @Test
    fun GameTestState.`checking a charged weapon says what is left to spend`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(InvObj(ether_objs.craw), ether_objs.craw, 1_000 + 321)

        option(0, 3)

        // The event is published outside a tick, so its message is still there.
        assertMessageSent("Your Craw's bow has 321 revenant ether left to use.")
    }

    @Test
    fun GameTestState.`swapping a sceptre keeps its ether`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(InvObj(ether_objs.thammaron), ether_objs.thammaron, 4_321)

        option(0, 4)
        advance(ticks = 2)

        assertEquals(ether_objs.thammaron_a.id, player.inv[0]!!.id)
        assertEquals(4_321, RevenantEther.stored(player.inv[0]))

        option(0, 4)
        advance(ticks = 2)
        assertEquals(ether_objs.thammaron.id, player.inv[0]!!.id)
        assertEquals(4_321, RevenantEther.stored(player.inv[0]))
    }

    // The upgrades.

    @Test
    fun GameTestState.`claws make an ursine chainmace from a level 85 smith`() = runEtherTest {
        give(0, ether_objs.viggora_uncharged)
        give(1, ether_objs.claws_of_callisto)
        player.stats[stats.smithing] = 85

        useOn(0, 1)
        advance(ticks = 2)

        assertEquals(ether_objs.ursine_uncharged.id, player.inv[0]!!.id)
        assertEquals(null, player.inv[1])
    }

    @Test
    fun GameTestState.`an upgrade needs 85 in its skill`() = runEtherTest {
        give(0, ether_objs.craw_uncharged)
        give(1, ether_objs.fangs_of_venenatis)
        player.stats[stats.fletching] = 84

        useOn(0, 1)
        advance(ticks = 2)

        assertEquals(ether_objs.craw_uncharged.id, player.inv[0]!!.id)
        assertEquals(ether_objs.fangs_of_venenatis.id, player.inv[1]!!.id)

        player.stats[stats.fletching] = 85
        useOn(0, 1)
        advance(ticks = 2)
        assertEquals(ether_objs.webweaver_uncharged.id, player.inv[0]!!.id)
        assertEquals(null, player.inv[1])
    }

    @Test
    fun GameTestState.`a skull makes an accursed sceptre and dismantling takes it off`() =
        runEtherTest {
            give(0, ether_objs.thammaron_uncharged)
            give(1, ether_objs.skull_of_vetion)
            player.stats[stats.crafting] = 99

            useOn(0, 1)
            advance(ticks = 2)
            assertEquals(ether_objs.accursed_uncharged.id, player.inv[0]!!.id)

            option(0, 3)
            answerChoice(1)

            assertEquals(ether_objs.thammaron_uncharged.id, player.inv[0]!!.id)
            assertTrue(
                player.inv.any { it?.id == ether_objs.skull_of_vetion.id },
                "the skull comes back",
            )
            assertEquals(0, ether())
        }

    // The bracelet of ethereum.

    @Test
    fun GameTestState.`ether charges a bracelet up to 16000 with no activation`() = runEtherTest {
        give(0, ether_objs.bracelet_uncharged)
        give(1, ether_objs.ether, 20_000)

        useOn(0, 1)
        answerCount(20_000)

        assertEquals(ether_objs.bracelet.id, player.inv[0]!!.id)
        assertEquals(16_000, RevenantEther.stored(player.inv[0]))
        assertEquals(4_000, ether())
    }

    @Test
    fun GameTestState.`the bracelet's absorption toggles on and off, charged or not`() =
        runEtherTest {
            give(0, ether_objs.bracelet_uncharged)
            assertFalse(EtherCharges.absorbing(player.inv[0]))

            option(0, 3)
            advance(ticks = 2)
            assertTrue(EtherCharges.absorbing(player.inv[0]))
            assertEquals(ether_objs.bracelet_uncharged.id, player.inv[0]!!.id)

            option(0, 3)
            advance(ticks = 2)
            assertFalse(EtherCharges.absorbing(player.inv[0]))

            player.inv[0] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 100)
            option(0, 4)
            advance(ticks = 2)
            assertTrue(EtherCharges.absorbing(player.inv[0]))
            assertEquals(100, RevenantEther.stored(player.inv[0]), "the ether stays")
        }

    @Test
    fun GameTestState.`an uncharged bracelet dismantles into 250 ether`() = runEtherTest {
        give(0, ether_objs.bracelet_uncharged)

        option(0, 4)
        answerChoice(1)

        assertEquals(250, ether())
    }

    @Test
    fun GameTestState.`a bracelet is uncharged for all of its ether`() = runEtherTest {
        player.inv[0] =
            EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 1_234)

        option(0, 5)
        answerChoice(1)

        assertEquals(ether_objs.bracelet_uncharged.id, player.inv[0]!!.id)
        assertEquals(1_234, ether())
    }

    @Test
    fun GameTestState.`an absorbing bracelet takes the ether a revenant drops`() =
        runEtherTest { deps ->
            val worn =
                EtherCharges.withAbsorbing(
                    EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 100),
                    true,
                )
            player.worn[Wearpos.Hands.slot] = worn
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])
            val drops =
                mutableListOf(
                    DropResult(ether_objs.ether, 50),
                    DropResult(ether_objs.bracelet_uncharged, 1),
                )

            val absorbed = deps.bracelet.absorb(player, revenant, drops)

            assertEquals(50, absorbed)
            assertEquals(listOf(DropResult(ether_objs.bracelet_uncharged, 1)), drops)
            assertEquals(150, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))
            assertTrue(
                EtherCharges.absorbing(player.worn[Wearpos.Hands.slot]),
                "it keeps absorbing",
            )
        }

    @Test
    fun GameTestState.`an absorbing bracelet takes ether from a drop table roll`() =
        runEtherTest { deps ->
            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withAbsorbing(InvObj(ether_objs.bracelet_uncharged), true)
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])
            // Drop tables roll unpacked obj types, which are not equal to the hashed references.
            val drops = mutableListOf(DropResult(objTypes[ether_objs.ether], 40))

            assertEquals(40, deps.bracelet.absorb(player, revenant, drops))
            assertTrue(drops.isEmpty())
            assertEquals(40, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))
        }

    @Test
    fun GameTestState.`a bracelet that is full leaves the rest of the ether to drop`() =
        runEtherTest { deps ->
            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withAbsorbing(
                    EtherCharges.withStored(
                        InvObj(ether_objs.bracelet),
                        ether_objs.bracelet,
                        15_990,
                    ),
                    true,
                )
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])
            val drops = mutableListOf(DropResult(ether_objs.ether, 25))

            val absorbed = deps.bracelet.absorb(player, revenant, drops)

            assertEquals(10, absorbed)
            assertEquals(listOf(DropResult(ether_objs.ether, 15)), drops)
            assertEquals(16_000, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))
        }

    @Test
    fun GameTestState.`an uncharged absorbing bracelet charges up from the drops`() =
        runEtherTest { deps ->
            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withAbsorbing(InvObj(ether_objs.bracelet_uncharged), true)
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])
            val drops = mutableListOf(DropResult(ether_objs.ether, 40))

            deps.bracelet.absorb(player, revenant, drops)

            assertTrue(drops.isEmpty())
            assertEquals(ether_objs.bracelet.id, player.worn[Wearpos.Hands.slot]!!.id)
            assertEquals(40, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))
        }

    @Test
    fun GameTestState.`a bracelet that is not absorbing or not worn takes nothing`() =
        runEtherTest { deps ->
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])

            val notWorn = mutableListOf(DropResult(ether_objs.ether, 40))
            assertEquals(0, deps.bracelet.absorb(player, revenant, notWorn))

            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 100)
            val off = mutableListOf(DropResult(ether_objs.ether, 40))
            assertEquals(0, deps.bracelet.absorb(player, revenant, off))
            assertEquals(listOf(DropResult(ether_objs.ether, 40)), off)
        }

    @Test
    fun GameTestState.`only a revenant's ether is absorbed`() = runEtherTest { deps ->
        player.worn[Wearpos.Hands.slot] =
            EtherCharges.withAbsorbing(InvObj(ether_objs.bracelet_uncharged), true)
        val man = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes.values.first { it.name == "Man" })
        val drops = mutableListOf(DropResult(ether_objs.ether, 40))

        assertEquals(0, deps.bracelet.absorb(player, man, drops))
        assertEquals(1, drops.size)
    }

    @Test
    fun GameTestState
        .`a charged bracelet makes revenants tolerant and an uncharged one does not`() =
        runEtherTest {
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.dragon])
            val man =
                spawnNpc(CoordGrid(3217, 10066, 0), npcTypes.values.first { it.name == "Man" })
            val tolerance = RevenantTolerance()

            assertFalse(tolerance.tolerates(revenant, player))

            player.worn[Wearpos.Hands.slot] = InvObj(ether_objs.bracelet_uncharged)
            assertFalse(tolerance.tolerates(revenant, player))

            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 5)
            assertTrue(tolerance.tolerates(revenant, player))
            assertFalse(tolerance.tolerates(man, player), "only revenants are tolerant")
        }

    @Test
    fun GameTestState.`the bracelet takes three quarters off a hit and an ether for it`() =
        runEtherTest {
            assertEquals(0, EtherCharges.braceletDamage(0))
            assertEquals(0, EtherCharges.braceletDamage(1))
            assertEquals(1, EtherCharges.braceletDamage(2))
            assertEquals(5, EtherCharges.braceletDamage(20))
            assertEquals(8, EtherCharges.braceletDamage(30))

            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 2)
            assertTrue(EtherCharges.spendBraceletEther(player))
            assertEquals(1, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))
            assertTrue(EtherCharges.spendBraceletEther(player))
            assertEquals(
                ether_objs.bracelet_uncharged.id,
                player.worn[Wearpos.Hands.slot]!!.id,
                "the last ether is spent",
            )
            assertFalse(EtherCharges.spendBraceletEther(player), "nothing left to spend")
        }

    @Test
    fun GameTestState.`a revenant hit on a bracelet wearer is cut by three quarters`() =
        runInjectedGameTest(
            Deps::class,
            EtherModule,
            DungeonRevenantScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { _ ->
            val coords = CoordGrid(3217, 10062, 0)
            allocZoneCollision(coords)
            val stand = coords.translateX(npcTypes[RevenantNpcs.dragon].size)
            allocZoneCollision(stand)
            player.placeAt(stand)
            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 10_000)
            advance(ticks = 3)
            val revenant = spawnNpc(coords, npcTypes[RevenantNpcs.dragon])
            player.stats[stats.hitpoints] = 99
            val before = RevenantEther.stored(player.worn[Wearpos.Hands.slot])
            revenant.opPlayer2(player)

            var worst = 0
            repeat(200) {
                advance()
                worst = maxOf(worst, 99 - player.stats[stats.hitpoints])
                player.stats[stats.hitpoints] = 99
            }

            val used = before - RevenantEther.stored(player.worn[Wearpos.Hands.slot])
            assertTrue(used > 5, "the bracelet spends an ether on each attack: $used")
            assertTrue(worst <= 8, "a hit of up to 30 is cut to 8 at most, not $worst")
        }

    /** The worn bracelet's Check and Toggle-absorption, which are `wear_op1` and `wear_op2`. */
    @Test
    fun GameTestState.`the worn bracelet checks its ether and toggles absorption`() =
        runInjectedGameTest(
            Deps::class,
            EtherModule,
            RevenantEtherScript::class,
            RevenantBraceletScript::class,
            WornOpScript::class,
        ) { _ ->
            player.clearInv()
            player.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(InvObj(ether_objs.bracelet), ether_objs.bracelet, 100)
            assertFalse(EtherCharges.absorbing(player.worn[Wearpos.Hands.slot]))

            clickWornBracelet(IfButtonOp.Op2)
            assertMessageSent("Your bracelet of ethereum has 100 revenant ether.")

            clickWornBracelet(IfButtonOp.Op3)
            assertTrue(EtherCharges.absorbing(player.worn[Wearpos.Hands.slot]))
            assertMessageSent("Your bracelet of ethereum will absorb the ether revenants drop.")
            assertEquals(100, RevenantEther.stored(player.worn[Wearpos.Hands.slot]))

            clickWornBracelet(IfButtonOp.Op3)
            advance()
            assertFalse(EtherCharges.absorbing(player.worn[Wearpos.Hands.slot]))
        }

    @Test
    fun GameTestState.`the worn uncharged bracelet toggles absorption too`() =
        runInjectedGameTest(
            Deps::class,
            EtherModule,
            RevenantEtherScript::class,
            RevenantBraceletScript::class,
            WornOpScript::class,
        ) { _ ->
            player.clearInv()
            player.worn[Wearpos.Hands.slot] = InvObj(ether_objs.bracelet_uncharged)

            clickWornBracelet(IfButtonOp.Op3)
            advance()
            assertTrue(EtherCharges.absorbing(player.worn[Wearpos.Hands.slot]))
            assertEquals(
                ether_objs.bracelet_uncharged.id,
                player.worn[Wearpos.Hands.slot]!!.id,
                "it stays uncharged",
            )
        }

    /** A worn charged weapon's Check is its `wear_op1`. */
    @Test
    fun GameTestState.`a worn revenant weapon checks its ether`() =
        runInjectedGameTest(
            Deps::class,
            EtherModule,
            RevenantEtherScript::class,
            WornOpScript::class,
        ) { _ ->
            player.clearInv()
            for (weapon in EtherItems.weapons) {
                val stored = RevenantEther.ACTIVATION + 250
                player.worn[Wearpos.RightHand.slot] =
                    EtherCharges.withStored(InvObj(weapon.charged), weapon.charged, stored)
                val obj = objTypes[player.worn[Wearpos.RightHand.slot]!!]
                eventBus.publish(
                    IfOverlayButton(player, WornTestComponents.weapon_slot, 0, obj, IfButtonOp.Op2)
                )
                assertMessageSent("Your ${weapon.name} has 250 revenant ether left to use.")
            }
        }

    /** Clicks [op] on the bracelet in the equipment tab. */
    private fun GameTestScope.clickWornBracelet(op: IfButtonOp) {
        val obj = objTypes[player.worn[Wearpos.Hands.slot]!!]
        eventBus.publish(IfOverlayButton(player, WornTestComponents.hands_slot, 0, obj, op))
    }

    // The tasks.

    @Test
    fun GameTestState.`all three revenant weapons dropped complete the obtain task`() =
        runEtherTest { deps ->
            val revenant = spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])

            // Drops carry unpacked obj types, as the drop tables give them.
            deps.bracelet.countWeapons(
                player,
                revenant,
                listOf(DropResult(objTypes[ether_objs.craw_uncharged], 1)),
            )
            deps.bracelet.countWeapons(
                player,
                revenant,
                listOf(DropResult(objTypes[ether_objs.viggora_uncharged], 1)),
            )
            assertFalse(deps.tracker.isComplete(player, 1019))

            deps.bracelet.countWeapons(
                player,
                revenant,
                listOf(DropResult(objTypes[ether_objs.thammaron_uncharged], 1)),
            )
            assertTrue(deps.tracker.isComplete(player, 1019))
        }

    @Test
    fun GameTestState.`a weapon from anything but a revenant does not count`() =
        runEtherTest { deps ->
            val man =
                spawnNpc(CoordGrid(3217, 10062, 0), npcTypes.values.first { it.name == "Man" })
            for (weapon in
                listOf(
                    ether_objs.craw_uncharged,
                    ether_objs.viggora_uncharged,
                    ether_objs.thammaron_uncharged,
                )) {
                deps.bracelet.countWeapons(player, man, listOf(DropResult(objTypes[weapon], 1)))
            }
            assertFalse(deps.tracker.isComplete(player, 1019))
        }
}
