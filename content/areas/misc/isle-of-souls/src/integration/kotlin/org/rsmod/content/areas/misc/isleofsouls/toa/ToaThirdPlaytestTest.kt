package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/** The third round of playtest fixes (2026-10-29). */
class ToaThirdPlaytestTest {
    @Test
    fun GameTestState.`a jug is pushed diagonally from a corner tile`() =
        runRoomTest(ToaRoom.CrondisBoss, ZebakScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3934, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as ZebakRuntime
            room.fightEnabled = false
            room.start()
            val tile = CoordGrid(3936, 5410, 0)
            room.spawnJugs(listOf(tile))
            val jug = room.jugNpcs().single()
            run.stand(run.alice, tile.translate(-1, -1))
            tickRoom(run)
            run.alice.opNpc1(jug)
            tickRoom(run, 3)
            val at = run.real(jug)
            assertTrue(at.x > tile.x && at.z > tile.z, "it rolled away north-east, at $at")
        }

    @Test
    fun GameTestState.`hitting a jug from afar is an attack with the player's weapon`() =
        runRoomTest(ToaRoom.CrondisBoss, ZebakScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3934, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as ZebakRuntime
            room.fightEnabled = false
            room.start()
            room.spawnJugs(listOf(CoordGrid(3940, 5408, 0)))
            val jug = room.jugNpcs().single()
            assertTrue(run.deps.pvnRules.isAttackable(jug), "a jug may be attacked")
            run.alice.opNpc4(jug)
            tickRoom(run)
            val interaction = run.alice.interaction as? InteractionNpcOp
            assertNotNull(interaction, "hit turned into an attack")
            assertEquals(InteractionOp.Op2, interaction?.op)
            assertTrue(interaction?.target === jug)
            assertEquals(CoordGrid(3934, 5408, 0), run.real(run.alice), "without walking up")
            // The blow shatters it the tick after it lands.
            val hit = TestHits.hit(HitType.Ranged, 5)
            room.modifyHit(jug, hit, run.alice)
            assertEquals(0, hit.damage)
            tickRoom(run, 2)
            assertEquals(0, room.jugCount)
        }

    @Test
    fun GameTestState.`a matching tile turns from a diagonal tile`() =
        runRoomTest(ToaRoom.ScabarasPuzzle, ScabarasScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3545, 5280, 0))
            tickRoom(run)
            val room = run.runtime() as ScabarasPuzzleRuntime
            room.start()
            tickRoom(run)
            // Alone, some pairs start face up: take a tile still face down.
            val tiles =
                ScabarasPuzzleRuntime.MATCH_OFFSETS.map { (dx, dz) ->
                    ScabarasPuzzleRuntime.MATCH_BASES[1].translate(dx, dz)
                }
            val index =
                tiles.indexOfFirst {
                    room.placedLoc(it, LocShape.GroundDecor)?.id == scabaras_locs.match_tile.id
                }
            val tile = tiles[index]
            val key = room.matchKeys(north = true)[index]
            val loc = checkNotNull(room.placedLoc(tile, LocShape.GroundDecor))
            run.stand(run.alice, tile.translate(-1, -1))
            tickRoom(run)
            run.alice.opLoc1(BoundLocInfo(loc, run.deps.locTypes.getValue(loc.id)))
            tickRoom(run, 2)
            assertEquals(
                scabaras_locs.match_symbols[key].id,
                room.placedLoc(tile, LocShape.GroundDecor)?.id,
            )
        }

    @Test
    fun GameTestState.`touching an obelisk costs no attack delay`() =
        runRoomTest(ToaRoom.ScabarasPuzzle, ScabarasScript::class, PlayerHitScript::class) { run ->
            val room = run.runtime() as ScabarasPuzzleRuntime
            val obelisk = room.npcs.first { it.type.id == scabaras_npcs.obelisk.id }
            assertEquals(0, run.deps.attackDelays.adjust(run.alice, obelisk, 5))
        }

    @Test
    fun GameTestState.`a warden skull costs no attack delay`() =
        runRoomTest(ToaRoom.WardensFinal, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3936, 5162, 1))
            tickRoom(run, 2)
            val skull = run.runtime().spawn(wardens_npcs.siphon, CoordGrid(3936, 5160, 1))
            assertEquals(0, run.deps.attackDelays.adjust(run.alice, skull, 4))
            val warden = checkNotNull((run.runtime() as WardensFinalRuntime).warden)
            assertEquals(4, run.deps.attackDelays.adjust(run.alice, warden, 4))
        }

    @Test
    fun GameTestState.`kephri's second down adds a spitter and dead she does nothing`() =
        runRoomTest(ToaRoom.ScabarasBoss, KephriScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3545, 5402, 0))
            tickRoom(run)
            val room = run.runtime() as KephriRuntime
            room.fightEnabled = false
            room.start()
            val boss = checkNotNull(room.kephri)
            fun spitters() = room.npcs.count { it.type.id == kephri_npcs.spitter.id }
            fun waitOutSwarms() =
                repeat(12) {
                    tickRoom(run, 10)
                    run.alice.stats[stats.hitpoints] = 99
                }
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run, 4)
            assertEquals(1, spitters(), "a spitter the first time")
            waitOutSwarms()
            assertFalse(room.isDazed)
            val overlords = room.overlordCount
            val spittersBefore = spitters()
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run, 4)
            assertEquals(2, room.phase)
            assertEquals(overlords + 3, room.overlordCount, "arcane, spitter and soldier")
            assertEquals(spittersBefore + 1, spitters(), "another spitter the second time")
            waitOutSwarms()
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run)
            assertEquals(3, room.phase)
            boss.playerFace(run.alice)
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run, 4)
            assertTrue(room.isCompleted)
            assertEquals(kephri_npcs.dead.id, boss.visType.id)
            assertEquals(NpcMode.None, boss.mode)
            assertEquals(0, room.landingDamage(boss, 40), "a hit still in flight does nothing")
            val hit = TestHits.hit(HitType.Melee, 40)
            room.modifyHit(boss, hit, run.alice)
            assertEquals(0, hit.damage)
        }

    @Test
    fun GameTestState.`ba-ba's baboons stand still while they appear`() =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as BaBaRuntime
            room.fightEnabled = false
            room.start()
            val boss = checkNotNull(room.baba)
            boss.hitpoints = boss.baseHitpointsLvl * 70 / 100
            fun baboons() = room.monsters.values.filter { it.npc.type.id == baba_npcs.baboon.id }
            var waited = 0
            while (baboons().isEmpty() && waited++ < 5) {
                tickRoom(run)
            }
            val baboon = baboons().first()
            val start = baboon.npc.coords
            assertTrue(room.isHeld(baboon.npc), "held while its spawn animation plays")
            while (room.isHeld(baboon.npc)) {
                assertEquals(start, baboon.npc.coords)
                tickRoom(run)
            }
        }

    @Test
    fun GameTestState.`the final warden falls, stays down and the crystal rises in front`() =
        runRoomTest(ToaRoom.WardensFinal, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3936, 5162, 1))
            tickRoom(run, 2)
            val room = run.runtime() as WardensFinalRuntime
            room.fightEnabled = false
            val boss = checkNotNull(room.warden)
            room.skipToEnd()
            tickRoom(run)
            assertTrue(boss.isSlotAssigned, "it collapses where it stood first")
            tickRoom(run, room.ticksOf(wardens_seqs.death) + 1)
            assertFalse(boss.isSlotAssigned, "then it is gone, never standing up again")
            val trail =
                room.npcs.filter {
                    it.type.id == wardens_npcs.tumeken_death.id ||
                        it.type.id == wardens_npcs.elidinis_death.id
                }
            assertEquals(5, trail.size)
            for (npc in trail) {
                assertEquals(Direction.North, npc.respawnDir, "each faces the way the Warden did")
            }
            tickRoom(run, WardensFinalRuntime.CRYSTAL_TICKS)
            assertNull(room.placedLoc(CoordGrid(3936, 5154, 1)), "not under where it stood")
            assertEquals(
                wardens_locs.final_crystal.id,
                room.placedLoc(WardensFinalRuntime.CRYSTAL_SPOT)?.id,
            )
            assertEquals(5157, WardensFinalRuntime.CRYSTAL_SPOT.z, "the arena's first row")
        }
}
