package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** The fourth round of playtest fixes (2026-10-29). */
class ToaFourthPlaytestTest {
    @Test
    fun GameTestState.`a killed volatile baboon's death is its burst`() =
        runRoomTest(ToaRoom.ApmekenPuzzle, ApmekenScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3808, 5280, 0))
            tickRoom(run)
            val room = run.runtime() as ApmekenPuzzleRuntime
            room.start()
            // Wave two has the first volatile baboon.
            tickRoom(run, ApmekenPuzzleRuntime.FIRST_WAVE_DELAY + 1)
            var guard = 0
            while (
                room.baboonNpcs().none { it.type.id == apmeken_npcs.volatile.id } && guard++ < 10
            ) {
                for (npc in room.baboonNpcs()) {
                    kill(run, npc)
                }
                run.alice.stats[stats.hitpoints] = 99
                tickRoom(run, ApmekenPuzzleRuntime.NEXT_WAVE_DELAY)
            }
            val volatile = room.baboonNpcs().first { it.type.id == apmeken_npcs.volatile.id }
            // The tick clears an npc's animation before a test can read it, so this checks the
            // timing: the usual death keeps a baboon four ticks, the burst only until it goes off.
            assertEquals(4, room.ticksOf(apmeken_seqs.death))
            volatile.hitpoints = 0
            volatile.queueDeath()
            tickRoom(run)
            assertTrue(volatile.isSlotAssigned, "while the burst plays")
            assertTrue(volatile in room.npcs, "the room still holds it, for a wipe")
            tickRoom(run, ApmekenPuzzleRuntime.EXPLODE_TICKS)
            assertFalse(volatile.isSlotAssigned, "gone as it bursts, not after a death")
        }

    @Test
    fun GameTestState.`a boulder stops colliding once the hit that breaks it is queued`() =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as BaBaRuntime
            room.fightEnabled = false
            room.start()
            val boss = checkNotNull(room.baba)
            boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
            tickRoom(run, 16)
            val boulder = room.boulderNpcs().first()
            val hit = TestHits.hit(HitType.Ranged, boulder.hitpoints)
            room.modifyHit(boulder, hit, run.alice)
            assertTrue(room.isBoulderDoomed(boulder))

            // Alice waits right in its way: it rolls through her.
            val tile = run.real(boulder).translate(-1, 1)
            run.stand(run.alice, tile)
            run.alice.stats[stats.hitpoints] = 99
            tickRoom(run, 2)
            assertEquals(tile, run.real(run.alice), "not knocked back")
            assertEquals(99, run.alice.hitpoints)
            assertTrue(boulder.isSlotAssigned, "it goes when the hit lands, as before")
        }

    @Test
    fun GameTestState.`a core hit bigger than the core leaves it standing and shows in full`() =
        runRoomTest(ToaRoom.WardensFirst, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5160, 1))
            tickRoom(run)
            val room = run.runtime() as WardensRuntime
            room.fightEnabled = false
            room.setReady(run.alice)
            val obelisk = checkNotNull(room.obelisk)
            room.modifyHit(obelisk, TestHits.hit(HitType.Ranged, obelisk.hitpoints + 10), run.alice)
            tickRoom(run, 25)
            val warden = checkNotNull(room.fighting)
            val magicWeak = room.fightingWarden == Warden.Elidinis
            val right =
                TestHits.hit(if (magicWeak) HitType.Magic else HitType.Ranged, warden.hitpoints + 5)
            room.modifyHit(warden, right, run.alice)
            tickRoom(run, 10)
            assertTrue(room.isDown)
            val core = checkNotNull(room.core)
            val before = warden.hitpoints

            val big = TestHits.hit(HitType.Ranged, 101)
            room.modifyHit(core, big, run.alice)
            assertEquals(101, big.damage, "the hit is not trimmed to the core's health")
            assertEquals(before - 505, warden.hitpoints, "five fold, from $before")

            // The whole engine path: the core is topped up as the hit lands and never dies.
            core.queueHit(run.alice, 1, HitType.Ranged, 150, run.deps.npcHitModifier)
            tickRoom(run, 2)
            assertTrue(core.isSlotAssigned)
            assertEquals(core.baseHitpointsLvl, core.hitpoints)
            assertFalse(queues.death in core.queueList, "a dead core would end the attack on it")
        }
}
