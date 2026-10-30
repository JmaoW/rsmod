package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.npc.combatPlayDefendAnim
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Npc
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid

/** How the raid's monsters and players animate: flinching, dying and being knocked back. */
class ToaAnimTest {
    private val floor = CoordGrid(3808, 5280, 0)

    private fun Npc.flinch(): EntitySeq {
        pendingSequence = EntitySeq.NULL
        combatPlayDefendAnim()
        return pendingSequence
    }

    @Test
    fun GameTestState.`monsters with no block animation of their own do not flinch`() =
        runRoomTest(ToaRoom.ApmekenPuzzle, ApmekenScript::class) { run ->
            val room = run.runtime()
            // The cache has no block seq for a baboon: npc_anims.toml gives it its idle, which
            // means "no flinch" rather than the human block on a baboon's rig (needs packCache).
            val baboon = room.spawn(apmeken_npcs.brawler, floor, scale = false, points = 0.0)
            assertEquals(baboon.visType.readyAnim, baboon.visType.param(params.defend_anim).id)
            assertEquals(EntitySeq.NULL, baboon.flinch())

            // A still model (no ready animation at all) never flinches either.
            val egg = room.spawn(kephri_npcs.egg, floor.translateX(2), scale = false, points = 0.0)
            assertEquals(-1, egg.visType.readyAnim)
            assertEquals(EntitySeq.NULL, egg.flinch())

            // The crocodile has a real block seq and still plays it.
            val croc =
                room.spawn(crondis_npcs.crocodile, floor.translateX(4), scale = false, points = 0.0)
            val parry = croc.visType.param(params.defend_anim)
            assertEquals(parry.id, croc.flinch().id)
        }

    @Test
    fun GameTestState.`a dying baboon stays until its death animation has played`() =
        runRoomTest(ToaRoom.ApmekenPuzzle, ApmekenScript::class) { run ->
            val room = run.runtime()
            val baboon = room.spawn(apmeken_npcs.brawler, floor, scale = false, points = 0.0)
            assertEquals(4, room.ticksOf(apmeken_seqs.death), "npc_mandrill_despawn01")
            baboon.hitpoints = 0
            baboon.queueDeath()
            // The death animation starts the tick after and plays for four ticks (a fixed three
            // ticks used to take the baboon away before it had finished).
            tickRoom(run, 4)
            assertTrue(baboon.isSlotAssigned, "still playing its death")
            tickRoom(run)
            assertFalse(baboon.isSlotAssigned, "gone once it has played")
        }

    @Test
    fun GameTestState.`a knocked back player faces the way they came`() =
        runRoomTest(ToaRoom.ApmekenPuzzle, ApmekenScript::class) { run ->
            val room = run.runtime()
            run.stand(run.alice, floor)
            room.knockback(run.alice, floor.translateX(2))
            assertEquals(WEST, run.alice.pendingExactMove?.direction)

            run.stand(run.alice, floor)
            room.knockback(run.alice, floor.translateZ(-2))
            assertEquals(NORTH, run.alice.pendingExactMove?.direction)

            // A room that knows better can still say which way to face.
            run.stand(run.alice, floor)
            room.knockback(run.alice, floor.translateX(-2), facing = WEST)
            assertEquals(WEST, run.alice.pendingExactMove?.direction)
        }

    private companion object {
        const val WEST = 512
        const val NORTH = 1024
    }
}
