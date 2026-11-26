package org.rsmod.content.areas.misc.isleofsouls.pets

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueGuardian
import org.rsmod.content.areas.misc.isleofsouls.relics.RelicItem
import org.rsmod.content.areas.misc.isleofsouls.relics.RelicVarBits
import org.rsmod.game.entity.NpcList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.map.CoordGrid

/** A pet and a guardian out together walk in one line behind their owner, the pet first. */
class FollowerLineTest {
    class Deps
    @Inject
    constructor(
        val pets: Pets,
        val guardian: LeagueGuardian,
        val npcList: NpcList,
        val npcRegistry: NpcRegistry,
    )

    private object LineModule : AbstractModule() {
        override fun configure() {
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            bind(Pets::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueGuardian::class.java).`in`(Scopes.SINGLETON)
        }
    }

    /** Open ground on the test map. */
    private val start = CoordGrid(0, 50, 50, 20, 35)

    @Test
    fun GameTestState.`the pet walks right behind its owner and the guardian behind the pet`() =
        // The guardian's script loads first: the line must not depend on the load order.
        runInjectedGameTest(
            Deps::class,
            LineModule,
            LeagueGuardian::class,
            PetScript::class,
            PetDropScript::class,
            HeldOpScript::class,
        ) { deps ->
            try {
                player.clearInv()
                player.teleport(start)
                advance(1)
                VarPlayerIntMapSetter.set(
                    player,
                    RelicVarBits.selection[RelicItem.Guardian.tier],
                    RelicItem.Guardian.option,
                )
                player.inv[0] = InvObj(isle_objs.guardian_horn)
                val horn = objTypes[player.inv[0]!!]
                eventBus.publish(
                    IfOverlayButton(player, components.inv_items, 0, horn, IfButtonOp.Op2)
                )
                advance(1)
                val guardian = checkNotNull(deps.guardian.guardianOf(player))
                val vorki = deps.pets.pet("vorki")
                assertTrue(deps.pets.spawn(player, vorki, vorki.base))
                advance(1)
                val pet = checkNotNull(deps.pets.followerOf(player)?.npc)

                player.enableWalk()
                player.routeRequest = RouteRequestCoord(start.translateX(8))
                repeat(14) {
                    advance(1)
                    assertNotEquals(pet.coords, guardian.coords, "never on one tile, tick $it")
                    assertNotEquals(player.coords, pet.coords, "pet not under its owner, tick $it")
                    assertNotEquals(player.coords, guardian.coords, "guardian not under, tick $it")
                }
                assertEquals(start.translateX(8), player.coords, "the owner arrived")
                assertEquals(1, pet.coords.chebyshevDistance(player.coords), "pet: ${pet.coords}")
                assertEquals(
                    1,
                    guardian.coords.chebyshevDistance(pet.coords),
                    "guardian right behind the pet: ${guardian.coords}, pet ${pet.coords}",
                )
                assertEquals(
                    2,
                    guardian.coords.chebyshevDistance(player.coords),
                    "guardian second in line: ${guardian.coords}",
                )
            } finally {
                val leftover = buildList { for (npc in deps.npcList) add(npc) }
                leftover.forEach(deps.npcRegistry::del)
            }
        }
}
