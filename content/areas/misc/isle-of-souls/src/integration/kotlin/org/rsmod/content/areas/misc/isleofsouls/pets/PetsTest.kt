package org.rsmod.content.areas.misc.isleofsouls.pets

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.events.FollowerCall
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.map.CoordGrid

class PetsTest {
    class Deps
    @Inject
    constructor(
        val pets: Pets,
        val metamorph: PetMetamorph,
        val talk: PetTalk,
        val skilling: SkillingPets,
        val npcList: NpcList,
        val npcRegistry: NpcRegistry,
    )

    /** The pet system the scripts use must be the one the test reads. */
    private object PetModule : AbstractModule() {
        override fun configure() {
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            bind(Pets::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private val spot = CoordGrid(0, 50, 50, 20, 20)

    private fun GameTestState.runPetTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            PetModule,
            PetScript::class,
            PetDropScript::class,
            SkillingPets::class,
            HeldOpScript::class,
        ) { deps ->
            player.clearInv()
            player.teleport(spot)
            advance(1)
            // Npcs left in the world keep their collision in the map the next test shares (the
            // drop-table test's Vorkath blocks a whole patch): delete them when the test ends.
            try {
                body(deps)
            } finally {
                val leftover = buildList { for (npc in deps.npcList) add(npc) }
                leftover.forEach(deps.npcRegistry::del)
            }
        }

    /** Messages are captured per tick: look now, then after each of the next few ticks. */
    private fun GameTestScope.awaitMessage(text: String, ticks: Int = 4) {
        repeat(ticks) {
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
            advance()
        }
        assertMessageSent(text)
    }

    private fun Deps.follower(scope: GameTestScope): Npc =
        checkNotNull(pets.followerOf(scope.player)?.npc) { "no follower out" }

    private fun Npc.isFacingOwner(scope: GameTestScope): Boolean =
        faceEntity.isPlayer && faceEntity.playerSlot == scope.player.slotId

    @Test
    fun GameTestState
        .`a dropped pet appears beside its owner, then walks behind them facing them`() =
        runPetTest { deps ->
            // Away from the npcs other tests leave around `spot`.
            player.teleport(spot.translateZ(15))
            advance(1)
            val pet = deps.pets.pet("vorki")
            assertTrue(deps.pets.spawn(player, pet, pet.base))
            advance(1)
            val npc = deps.follower(this)
            val start = player.coords
            val spawned = npc.coords
            assertNotEquals(start, spawned, "not under its owner")
            assertTrue(npc.isWithinDistance(player, 1), "appears beside its owner: $spawned")
            assertTrue(spawned.x == start.x || spawned.z == start.z, "on a side, not a corner")
            advance(2)
            assertEquals(spawned, npc.coords, "stays put until they move")

            // Walk east, then run back west over the pet. Every tick: the pet is never on its
            // owner's tile, only ever on tiles they walked, keeps looking at them and keeps up.
            val walked = mutableSetOf(start, spawned)
            fun checkFollowing(tick: String) {
                assertNotEquals(player.coords, npc.coords, "under its owner on $tick")
                assertTrue(npc.coords in walked, "off its owner's path on $tick: ${npc.coords}")
                assertTrue(npc.isFacingOwner(this), "faces its owner on $tick")
                assertEquals(NpcMode.None, npc.mode)
                assertTrue(npc.isWithinDistance(player, 2), "keeps up on $tick: ${npc.coords}")
            }
            player.enableWalk()
            player.routeRequest = RouteRequestCoord(start.translateX(6))
            repeat(9) {
                advance(1)
                walked += player.coords
                walked += player.lastProcessedCoord
                checkFollowing("walk tick $it")
            }
            assertEquals(start.translateX(6), player.coords)
            assertTrue(npc.isWithinDistance(player, 1), "stops beside its owner: ${npc.coords}")

            player.enableRun()
            player.routeRequest = RouteRequestCoord(start)
            repeat(6) {
                advance(1)
                walked += player.coords
                walked += player.lastProcessedCoord
                checkFollowing("run tick $it")
            }
            assertTrue(npc.isWithinDistance(player, 1), "stops beside its owner: ${npc.coords}")
        }

    @Test
    fun GameTestState.`metamorphosis only swaps the look of the same npc`() = runPetTest { deps ->
        player.teleport(spot.translateZ(15))
        advance(1)
        val pet = deps.pets.pet("snakeling")
        assertTrue(deps.pets.spawn(player, pet, pet.base))
        advance(1)
        val npc = deps.follower(this)
        player.enableWalk()
        val dest = player.coords.translateX(3)
        player.routeRequest = RouteRequestCoord(dest)
        advance(8)
        assertEquals(dest, player.coords, "walked and stopped")
        val coords = npc.coords
        assertNotEquals(player.coords, coords)

        val magma = pet.form("magma")
        deps.pets.transform(player, magma)
        assertSame(npc, deps.follower(this), "the same npc, not a new one")
        assertEquals(coords, npc.coords, "kept its tile")
        assertEquals(magma.npc.id, npc.visType.id, "wears the new look")
        assertTrue(npc.isFacingOwner(this), "still faces its owner")
        advance(1)
        assertEquals(coords, npc.coords, "did not jump anywhere")

        // Back to its usual look: the transmog is simply dropped.
        deps.pets.transform(player, pet.base)
        assertSame(npc, deps.follower(this))
        assertEquals(pet.base.npc.id, npc.visType.id)
    }

    @Test
    fun GameTestState.`every pet of the collection log has a follower for each look`() =
        runPetTest { deps ->
            val catalog = deps.pets.catalog
            assertEquals(65, catalog.size)
            assertEquals((0 until 65).toList(), catalog.map { it.bit })
            for (pet in catalog) {
                for (form in pet.forms) {
                    assertTrue(npcTypes[form.npc].follower, "${pet.key}/${form.label} follower")
                    assertNotNull(form.op("Pick-up"), "${pet.key}/${form.label} has Pick-up")
                    assertNotNull(
                        deps.talk.conversation(pet, form),
                        "${pet.key}/${form.label} has a Talk-to conversation",
                    )
                }
            }
        }

    @Test
    fun GameTestState.`a new pet follows its owner`() = runPetTest { deps ->
        val pet = deps.pets.pet("vorki")
        assertEquals(PetAward.Followed, deps.pets.award(player, pet))
        assertMessageSent(Pets.red("You have a funny feeling like you're being followed."))
        val npc = deps.follower(this)
        assertEquals(NpcMode.None, npc.mode)
        assertTrue(npc.isFacingOwner(this), "faces its owner")
        assertEquals(pet.base.obj.id, player.vars[pet_varps.follower])
        assertEquals(npc.slotId, player.vars[pet_varps.follower_npc])
        assertTrue(deps.pets.isInsured(player, pet))
    }

    @Test
    fun GameTestState.`a second pet goes into the backpack, then to Probita`() =
        runPetTest { deps ->
            deps.pets.award(player, deps.pets.pet("vorki"))
            val scurry = deps.pets.pet("scurry")
            assertEquals(PetAward.Backpack, deps.pets.award(player, scurry))
            assertTrue(scurry.base.obj in player.inv)
            assertMessageSent(Pets.red("You feel something weird sneaking into your backpack."))

            player.clearInv()
            repeat(player.inv.size) { player.inv[it] = InvObj(objTypes[pet_objs.coal]) }
            val noon = deps.pets.pet("noon")
            assertEquals(PetAward.Lost, deps.pets.award(player, noon))
            assertTrue(noon in deps.pets.reclaimable(player), "Probita has it")

            player.clearInv()
            assertTrue(deps.pets.reclaim(player, noon))
            assertTrue(noon.base.obj in player.inv)
            assertFalse(noon in deps.pets.reclaimable(player))
        }

    @Test
    fun GameTestState.`a pet the player owns is a duplicate`() = runPetTest { deps ->
        val pet = deps.pets.pet("kalphite_princess")
        deps.pets.award(player, pet)
        assertEquals(PetAward.Duplicate, deps.pets.award(player, pet))
        assertMessageSent(Pets.red("You have a funny feeling like you would have been followed..."))
        assertFalse(pet.base.obj in player.inv)

        // Smol Heredit is a main drop: a second one really comes (wiki).
        val smol = deps.pets.pet("smol_heredit")
        deps.pets.dismiss(player)
        deps.pets.award(player, smol)
        assertEquals(PetAward.Backpack, deps.pets.award(player, smol))
    }

    @Test
    fun GameTestState.`dropping a pet brings it out, picking it up puts it back`() =
        runPetTest { deps ->
            val pet = deps.pets.pet("snakeling")
            player.inv[0] = InvObj(objTypes[pet.base.obj])
            eventBus.publish(
                IfOverlayButton(
                    player,
                    components.inv_items,
                    0,
                    objTypes[pet.base.obj],
                    IfButtonOp.Op7,
                )
            )
            advance(1)
            val npc = deps.follower(this)
            assertNull(player.inv[0], "the item left the inventory")

            // A second pet cannot come out while one follows.
            val other = deps.pets.pet("vorki")
            player.inv[1] = InvObj(objTypes[other.base.obj])
            eventBus.publish(
                IfOverlayButton(
                    player,
                    components.inv_items,
                    1,
                    objTypes[other.base.obj],
                    IfButtonOp.Op7,
                )
            )
            awaitMessage("You already have a follower.")
            assertNotNull(player.inv[1])

            when (pet.base.op("Pick-up")) {
                3 -> player.opNpc3(npc)
                4 -> player.opNpc4(npc)
                else -> error("unexpected op")
            }
            advance(4)
            assertNull(deps.pets.followerOf(player), "picked up")
            assertTrue(pet.base.obj in player.inv)
        }

    @Test
    fun GameTestState.`metamorphosis cycles the snakeling's colours`() = runPetTest { deps ->
        val pet = deps.pets.pet("snakeling")
        deps.pets.award(player, pet)
        val npc = deps.follower(this)
        when (pet.base.op("Metamorphosis")) {
            3 -> player.opNpc3(npc)
            4 -> player.opNpc4(npc)
            else -> error("unexpected op")
        }
        advance(4)
        assertEquals("magma", deps.pets.followerOf(player)?.form?.label)
        assertEquals(pet.form("magma").obj.id, player.vars[pet_varps.follower])
    }

    @Test
    fun GameTestState.`metamorphoses that need an item stay locked until it is used`() =
        runPetTest { deps ->
            val olmlet = deps.pets.pet("olmlet")
            assertNull(deps.metamorph.next(player, olmlet, olmlet.base))
            val dust = objTypes[pet_objs.metamorphic_dust]
            val use = deps.metamorph.useItem(player, olmlet, olmlet.base, dust, held = 1)
            assertTrue(use is PetMetamorph.ItemUse.Change)
            deps.metamorph.unlock(player, PetUnlock.Olmlet)
            assertEquals("puppadile", deps.metamorph.next(player, olmlet, olmlet.base)?.label)

            val golem = deps.pets.pet("rock_golem")
            val iron = objTypes[pet_objs.iron_ore]
            val change = deps.metamorph.useItem(player, golem, golem.base, iron, held = 1)
            assertEquals(golem.form("iron"), (change as PetMetamorph.ItemUse.Change).form)

            val heron = deps.pets.pet("heron")
            val flakes = objTypes[pet_objs.spirit_flakes]
            assertTrue(
                deps.metamorph.useItem(player, heron, heron.base, flakes, held = 10)
                    is PetMetamorph.ItemUse.NotEnough
            )
        }

    @Test
    fun GameTestState.`call follower brings a far follower and says when it is close`() =
        runPetTest { deps ->
            deps.pets.award(player, deps.pets.pet("vorki"))
            val npc = deps.follower(this)
            eventBus.publish(FollowerCall(player))
            assertMessageSent("Your follower is already close enough.")

            npc.telejump(spot.translateX(8))
            val call = FollowerCall(player)
            eventBus.publish(call)
            assertTrue(call.handled)
            assertTrue(npc.isWithinDistance(player, 1), "came to its owner")
        }

    @Test
    fun GameTestState.`a follower jumps to its owner after a teleport`() = runPetTest { deps ->
        deps.pets.award(player, deps.pets.pet("vorki"))
        val npc = deps.follower(this)
        player.teleport(spot.translateX(40))
        advance(3)
        assertTrue(npc.isWithinDistance(player, Pets.LEASH_RANGE), "caught up: ${npc.coords}")
    }

    @Test
    fun GameTestState.`the follower is saved over logout`() = runPetTest { deps ->
        val pet = deps.pets.pet("tzrek_jad")
        deps.pets.award(player, pet)
        deps.pets.dismiss(player, forget = false)
        assertNull(deps.pets.followerOf(player))
        assertEquals(pet.base.obj.id, player.vars[pet_varps.follower])
        deps.pets.restore(player)
        assertEquals(pet, deps.pets.followerOf(player)?.pet)
    }

    @Test
    fun GameTestState.`drop table pets are handed out, not dropped`() = runPetTest { deps ->
        val npc = spawnNpc(spot.translateX(2), npcTypes[PetNpcs.vorkath_pet])
        val vorki = objTypes[PetObjs.vorkathpet]
        val claim = NpcDropEvents.Claim(npc, player, mutableListOf(DropResult(vorki, 1)))
        eventBus.publish(claim)
        assertTrue(claim.drops.isEmpty(), "not on the floor")
        assertEquals(deps.pets.pet("vorki"), deps.pets.followerOf(player)?.pet)

        val modify = NpcDropEvents.Modify(npc, player)
        eventBus.publish(modify)
        assertEquals(deps.pets.oddsMultiplier, modify.chanceMultipliers[vorki.id])
    }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`skilling pet chances follow the wiki formula`() = runPetTest { deps ->
        player.statMap.setBaseLevel(stats.mining, 99.toByte())
        val chance = deps.skilling.chanceOneIn(player, SkillingPet.RockGolem, 741_600)
        assertEquals(741_600.0 - 99 * 25, chance)
    }

    @Test
    fun GameTestState.`a following rift guardian takes the altar's colour`() = runPetTest { deps ->
        val rift = deps.pets.pet("rift_guardian")
        deps.pets.award(player, rift)
        eventBus.publish(SkillingPetRoll(player, SkillingPet.RiftGuardian, Int.MAX_VALUE, "nature"))
        assertEquals("nature", deps.pets.followerOf(player)?.form?.label)
        assertMessageSent("You have unlocked a new metamorphosis for your rift guardian.")
    }
}
