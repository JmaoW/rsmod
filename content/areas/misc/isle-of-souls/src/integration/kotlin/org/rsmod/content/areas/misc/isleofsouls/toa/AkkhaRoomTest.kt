package org.rsmod.content.areas.misc.isleofsouls.toa

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Sands of Time: the forms, the shadows, the specials and the final stand. */
class AkkhaRoomTest {
    private object ToaModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: ToaParties,
        val raids: ToaRaids,
        val rooms: ToaRoomRegistry,
        val collision: CollisionFlagMap,
    )

    private fun GameTestScope.allocate(deps: Deps) {
        for (column in ToaPacking.columns) {
            for (zx in column.realZoneX until column.realZoneX + column.zoneWidth) {
                for (zz in
                    ToaPacking.REAL_ZONE_Z until ToaPacking.REAL_ZONE_Z + ToaPacking.ZONE_ROWS) {
                    for (level in 0 until CoordGrid.LEVEL_COUNT) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
        }
    }

    private class Run(
        val deps: Deps,
        val alice: Player,
        val bob: Player,
        val raid: ToaRaid,
        val room: AkkhaRuntime,
    ) {
        fun at(x: Int, z: Int) = CoordGrid(x, z, AkkhaRuntime.Z)

        fun stand(player: Player, real: CoordGrid) {
            PathingEntityCommon.telejump(player, deps.collision, raid.regionOf(real))
        }

        val boss: Npc
            get() = checkNotNull(room.akkha)
    }

    private fun GameTestState.runAkkhaTest(
        twoPlayers: Boolean = false,
        body: GameTestScope.(Run) -> Unit,
    ) =
        runInjectedGameTest(Deps::class, ToaModule, AkkhaScript::class, PlayerHitScript::class) {
            deps ->
            allocate(deps)
            val alice = player
            alice.displayName = "Alice"
            // The boss drops are owned by a player, which needs an observer id.
            alice.observerUUID = 1L
            alice.stats[stats.hitpoints] = 99
            PathingEntityCommon.telejump(alice, deps.collision, ToaLobby.RETURN)
            deps.collision.allocateIfAbsent(ToaLobby.RETURN.x, ToaLobby.RETURN.z, 0)
            val bob = registerPlayer(coords = ToaLobby.RETURN)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            bob.stats[stats.hitpoints] = 99
            val party = deps.parties.create(alice)
            if (twoPlayers) {
                deps.parties.apply(party, bob)
                deps.parties.answer(party, bob, accept = true)
            }
            // The moves between rooms fade the screen first; these tests move at once.
            deps.raids.fadeTicks = 0
            deps.raids.enterFromLobby(alice)
            if (twoPlayers) {
                deps.raids.enterFromLobby(bob)
            }
            val raid = checkNotNull(deps.raids.raidOf(alice))
            for (member in raid.active) {
                deps.raids.place(raid, member, ToaRoom.HetBoss)
            }
            // The test world has no map: the arena floor is open ground.
            for (x in 3670..3691) {
                for (z in 5395..5419) {
                    val tile = raid.regionOf(CoordGrid(x, z, AkkhaRuntime.Z))
                    deps.collision[tile.x, tile.z, tile.level] = 0
                }
            }
            val room = deps.rooms.ensure(raid, ToaRoom.HetBoss) as AkkhaRuntime
            body(Run(deps, alice, bob, raid, room))
        }

    private fun GameTestScope.tick(run: Run, ticks: Int = 1) {
        repeat(ticks) {
            advance()
            run.deps.raids.tick()
            run.deps.rooms.tick()
        }
    }

    /** Starts the fight with the player on the arena floor, out of the boss's reach. */
    private fun GameTestScope.begin(run: Run) {
        run.room.fightEnabled = false
        run.stand(run.alice, run.at(3688, 5398))
        tick(run)
        run.room.start()
    }

    @Test
    fun GameTestState.`akkha stands in the arena with the health of the party`() =
        runAkkhaTest { run ->
            val expected = ToaScaling.hitpoints(400, run.raid.raidLevel, 1, 0)
            assertEquals(expected, run.boss.hitpoints)
            assertEquals(akkha_npcs.melee.id, run.boss.id)
            assertEquals("melee", run.room.style)
            assertFalse(run.room.standing)
        }

    @Test
    fun GameTestState.`each form prays against one style only`() = runAkkhaTest { run ->
        begin(run)
        val boss = run.boss
        assertEquals(0, run.room.damageAfterRules(boss, HitType.Melee, 20, run.alice))
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Ranged, 20, run.alice))
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Magic, 20, run.alice))
        run.room.changeStyle()
        assertEquals("ranged", run.room.style)
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Melee, 20, run.alice))
        assertEquals(0, run.room.damageAfterRules(boss, HitType.Ranged, 20, run.alice))
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Magic, 20, run.alice))
        run.room.changeStyle()
        assertEquals("magic", run.room.style)
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Melee, 20, run.alice))
        assertEquals(20, run.room.damageAfterRules(boss, HitType.Ranged, 20, run.alice))
        assertEquals(0, run.room.damageAfterRules(boss, HitType.Magic, 20, run.alice))
        run.room.changeStyle()
        assertEquals("melee", run.room.style)
    }

    @Test
    fun GameTestState.`the form shows its own overhead prayer`() = runAkkhaTest { run ->
        begin(run)
        tick(run)
        assertEquals(akkha_npcs.melee.id, run.boss.visType.id)
        run.room.changeStyle()
        tick(run)
        assertEquals(akkha_npcs.range.id, run.boss.visType.id)
        run.room.changeStyle()
        tick(run)
        assertEquals(akkha_npcs.mage.id, run.boss.visType.id)
    }

    @Test
    fun GameTestState.`akkha wanders his room before anyone has started the fight`() =
        runAkkhaTest { run ->
            val boss = run.boss
            val start = boss.coords
            assertFalse(run.room.isStarted)
            tick(run, 60)
            assertTrue(boss.coords != start, "he should have wandered")
            assertTrue(run.room.room.inChallenge(checkNotNull(run.raid.realOf(boss.coords))))
        }

    @Test
    fun GameTestState.`a hit that would kill him leaves one hitpoint`() = runAkkhaTest { run ->
        begin(run)
        val boss = run.boss
        boss.hitpoints = 5
        assertEquals(4, run.room.damageAfterRules(boss, HitType.Magic, 99, run.alice))
        boss.hitpoints = 1
        assertEquals(0, run.room.damageAfterRules(boss, HitType.Magic, 3, run.alice))
    }

    @Test
    fun GameTestState.`the shadows appear at eighty percent and shield his quadrant`() =
        runAkkhaTest { run ->
            begin(run)
            val boss = run.boss
            // Back to his spawn in the north-west quadrant, whatever his wander before the start.
            boss.resetMovement()
            boss.telejump(run.deps.collision, run.raid.regionOf(AkkhaRuntime.SPAWN))
            val max = boss.baseHitpointsLvl
            boss.hitpoints = max * 80 / 100
            tick(run)
            assertEquals(4, run.room.shadowCount)
            // He stands in the quadrant of the north-west shadow, which is alive: he is immune.
            assertTrue(run.room.onShadedQuadrant())
            assertEquals(0, run.room.damageAfterRules(boss, HitType.Magic, 20, run.alice))
            // While nothing can hurt him he is shown shaded: his own form, tinted.
            tick(run)
            assertTrue(run.room.isShaded)
            assertEquals(akkha_npcs.melee.id, boss.visType.id)
            assertEquals(AkkhaTint.akkha, boss.activeTint)

            // The first wave is not immune: dispel the one under him.
            val shadow = checkNotNull(run.room.shadowNpc(3))
            assertEquals(false, run.room.shadowImmune(3))
            assertNull(shadow.activeTint)
            assertEquals(10, run.room.damageAfterRules(shadow, HitType.Magic, 10, run.alice))
            run.room.damageAfterRules(shadow, HitType.Magic, 9999, run.alice)
            tick(run)
            assertFalse(run.room.onShadedQuadrant())
            assertEquals(20, run.room.damageAfterRules(boss, HitType.Magic, 20, run.alice))
            tick(run)
            assertFalse(run.room.isShaded)
            assertEquals(akkha_npcs.melee.id, boss.visType.id)
            assertNull(boss.activeTint)
            // The other three are shielded (and shown shaded) until the next threshold.
            for (index in 0..2) {
                assertTrue(run.room.shadowImmune(index))
                val other = checkNotNull(run.room.shadowNpc(index))
                assertEquals(0, run.room.damageAfterRules(other, HitType.Magic, 10, run.alice))
                assertEquals(akkha_npcs.shadow.id, other.visType.id)
                assertEquals(AkkhaTint.shadow, other.activeTint)
            }
            assertEquals(3, run.room.shadowCount)
        }

    @Test
    fun GameTestState.`the next threshold restores the shadows`() = runAkkhaTest { run ->
        begin(run)
        val boss = run.boss
        val max = boss.baseHitpointsLvl
        boss.hitpoints = max * 80 / 100
        tick(run)
        run.room.damageAfterRules(
            checkNotNull(run.room.shadowNpc(3)),
            HitType.Magic,
            9999,
            run.alice,
        )
        tick(run)
        boss.hitpoints = max * 60 / 100
        tick(run)
        assertEquals(4, run.room.shadowCount)
        // The ones that stood are open to attack again; the one killed comes back shielded.
        assertFalse(run.room.shadowImmune(0))
        assertTrue(run.room.shadowImmune(3))
    }

    @Test
    fun GameTestState.`a killed shadow stays immune and shaded in every later phase`() =
        runAkkhaTest { run ->
            begin(run)
            val boss = run.boss
            val max = boss.baseHitpointsLvl
            fun kill(index: Int) {
                val shadow = checkNotNull(run.room.shadowNpc(index))
                assertFalse(run.room.shadowImmune(index), "shadow $index should be killable")
                run.room.damageAfterRules(shadow, HitType.Magic, 9999, run.alice)
                tick(run)
            }

            boss.hitpoints = max * 80 / 100
            tick(run)
            kill(3)

            boss.hitpoints = max * 60 / 100
            tick(run)
            kill(0)

            // Health he regains does not count the 60 percent threshold a second time.
            boss.hitpoints = max * 60 / 100 + 5
            tick(run)
            boss.hitpoints = max * 60 / 100 - 5
            tick(run)
            assertEquals(3, run.room.shadowCount)

            boss.hitpoints = max * 40 / 100
            tick(run, 2)
            assertEquals(4, run.room.shadowCount)
            for (index in listOf(0, 3)) {
                val shadow = checkNotNull(run.room.shadowNpc(index))
                assertTrue(run.room.shadowImmune(index), "killed shadow $index should be immune")
                assertEquals(0, run.room.damageAfterRules(shadow, HitType.Magic, 10, run.alice))
                assertEquals(AkkhaTint.shadow, shadow.activeTint)
            }
            for (index in listOf(1, 2)) {
                val shadow = checkNotNull(run.room.shadowNpc(index))
                assertFalse(run.room.shadowImmune(index))
                assertNull(shadow.activeTint)
            }
            kill(1)

            // The last phase leaves only the one never killed open to attack.
            boss.hitpoints = max * 20 / 100
            tick(run, 2)
            for (index in 0..3) {
                assertEquals(index != 2, run.room.shadowImmune(index), "shadow $index")
            }
        }

    @Test
    fun GameTestState.`a shadow blasts its quadrant when its timer runs out`() =
        runAkkhaTest { run ->
            begin(run)
            val boss = run.boss
            boss.hitpoints = boss.baseHitpointsLvl * 80 / 100
            tick(run)
            // Alice stands in the south-east quadrant, whose shadow is the second.
            run.stand(run.alice, run.at(3684, 5403))
            val before = run.alice.hitpoints
            tick(run, AkkhaRuntime.SHADOW_TIMER + 12)
            assertTrue(run.alice.hitpoints < before, "the blast should have hurt")
        }

    @Test
    fun GameTestState.`the memory blast hides him and brings him back`() = runAkkhaTest { run ->
        begin(run)
        run.room.special(AkkhaRuntime.SPECIAL_MEMORY)
        tick(run, 4)
        assertTrue(run.room.isVanished)
        assertTrue(run.room.isMemoryRunning)
        tick(run, 80)
        assertFalse(run.room.isVanished)
        assertFalse(run.room.isMemoryRunning)
    }

    @Test
    fun GameTestState.`the trail leaves an orb on each tile a moving player leaves`() =
        runAkkhaTest { run ->
            begin(run)
            run.room.special(AkkhaRuntime.SPECIAL_TRAIL)
            tick(run, 3)
            assertTrue(run.room.isTrailing)
            run.stand(run.alice, run.at(3688, 5398))
            tick(run)
            run.stand(run.alice, run.at(3688, 5399))
            tick(run)
            assertTrue(run.room.trailOrbCount > 0)

            // Stepping back onto it sets it off.
            val before = run.alice.hitpoints
            run.stand(run.alice, run.at(3688, 5398))
            tick(run, 4)
            assertTrue(run.alice.hitpoints < before)
        }

    @Test
    fun GameTestState.`the detonation strikes the arms of a plus around the other player`() =
        runAkkhaTest(twoPlayers = true) { run ->
            begin(run)
            run.stand(run.alice, run.at(3688, 5398))
            run.stand(run.bob, run.at(3688, 5400))
            tick(run)
            val before = run.bob.hitpoints
            run.room.special(AkkhaRuntime.SPECIAL_DETONATION)
            tick(run, AkkhaRuntime.DETONATION_TICKS + 3)
            assertTrue(run.bob.hitpoints < before, "bob stood on alice's column")
        }

    @Test
    fun GameTestState.`zero health starts the final stand with four shadows and rolling orbs`() =
        runAkkhaTest { run ->
            begin(run)
            val boss = run.boss
            run.room.damageAfterRules(boss, HitType.Magic, 99999, run.alice)
            tick(run)
            assertTrue(run.room.standing)
            assertEquals(0, run.room.damageAfterRules(boss, HitType.Magic, 10, run.alice))
            tick(run, 4)
            val finals = run.room.finalNpcs
            assertEquals(4, finals.size)
            assertNotNull(run.room.finalReal)
            val max = ToaScaling.hitpoints(400, run.raid.raidLevel, 1, 0)
            assertEquals(max * AkkhaRuntime.FINAL_SHARE_PERCENT / 100, finals[0].hitpoints)
            // Only the real one can be hurt, and only in melee.
            val real = checkNotNull(run.room.finalReal)
            val fake = finals.first { it !== real }
            assertEquals(0, run.room.damageAfterRules(fake, HitType.Melee, 10, run.alice))
            assertEquals(0, run.room.damageAfterRules(real, HitType.Magic, 10, run.alice))
            assertEquals(10, run.room.damageAfterRules(real, HitType.Melee, 10, run.alice))

            tick(run, AkkhaRuntime.UNSTABLE_INTERVAL + 1)
            assertTrue(run.room.unstableOrbCount > 0)
        }

    @Test
    fun GameTestState.`enough hits on the real shadow swap it with a fake`() = runAkkhaTest { run ->
        begin(run)
        run.room.damageAfterRules(run.boss, HitType.Magic, 99999, run.alice)
        tick(run, 5)
        val real = checkNotNull(run.room.finalReal)
        // The real one shows the full-colour Akkha with both protections; the others are shaded.
        assertEquals(akkha_npcs.enrage.id, real.visType.id)
        assertNull(real.activeTint)
        for (other in run.room.finalNpcs.filter { it !== real }) {
            assertEquals(akkha_npcs.shadow_enrage.id, other.visType.id)
            assertEquals(AkkhaTint.shadow, other.activeTint)
        }
        repeat(2 + 1) { run.room.damageAfterRules(real, HitType.Melee, 1, run.alice) }
        tick(run, 3)
        val now = checkNotNull(run.room.finalReal)
        assertTrue(now !== real, "the real one should have moved to another shadow")
        assertEquals(akkha_npcs.enrage.id, now.visType.id)
        assertEquals(akkha_npcs.shadow_enrage.id, real.visType.id)
        // The old one is now shaded and immune.
        assertEquals(AkkhaTint.shadow, real.activeTint)
        assertNull(now.activeTint)
        assertEquals(0, run.room.damageAfterRules(real, HitType.Melee, 10, run.alice))
        assertEquals(10, run.room.damageAfterRules(now, HitType.Melee, 10, run.alice))
        assertEquals(real.hitpoints.coerceAtLeast(now.hitpoints), now.hitpoints)
    }

    @Test
    fun GameTestState.`unstable orbs roll away and burst at the end of their line`() =
        runAkkhaTest { run ->
            begin(run)
            run.room.damageAfterRules(run.boss, HitType.Magic, 99999, run.alice)
            tick(run, 5 + AkkhaRuntime.UNSTABLE_INTERVAL + 1)
            assertTrue(run.room.unstableOrbCount > 0)
            // Never more than a few waves are alive: the old ones have gone at the arena's edge.
            var most = 0
            repeat(120) {
                tick(run)
                most = maxOf(most, run.room.unstableOrbCount)
            }
            assertTrue(most <= AkkhaRuntime.UNSTABLE_ORBS * 14, "orbs pile up: $most")
        }

    @Test
    fun GameTestState.`an unstable orb rolls into the player in its path and hits them`() =
        runAkkhaTest { run ->
            begin(run)
            // Players block npcs, so an orb that walked normally would stop in front of her.
            run.stand(run.alice, run.at(3684, 5400))
            tick(run)
            run.room.rollUnstableOrb(run.at(3679, 5400), AkkhaDirection.East)
            var hit = false
            repeat(10) {
                tick(run)
                if (run.alice.hitpoints < 99) {
                    hit = true
                }
            }
            assertTrue(hit, "the orb should have reached her and hit")
            assertEquals(0, run.room.unstableOrbCount)
        }

    @Test
    fun GameTestState.`killing the real shadow wins the room`() = runAkkhaTest { run ->
        begin(run)
        run.room.damageAfterRules(run.boss, HitType.Magic, 99999, run.alice)
        tick(run, 5)
        val real = checkNotNull(run.room.finalReal)
        run.room.damageAfterRules(real, HitType.Melee, 99999, run.alice)
        tick(run)
        assertTrue(run.room.isCompleted)
        assertTrue(run.deps.raids.isWon(run.raid, ToaRoom.HetBoss))
        assertTrue(run.room.finalNpcs.count { it.isSlotAssigned } <= 1)
    }

    @Test
    fun GameTestState.`a wipe puts akkha back as he was`() = runAkkhaTest { run ->
        begin(run)
        run.boss.hitpoints = run.boss.baseHitpointsLvl * 80 / 100
        tick(run)
        assertEquals(4, run.room.shadowCount)
        run.room.reset()
        assertEquals(0, run.room.shadowCount)
        assertEquals(ToaScaling.hitpoints(400, run.raid.raidLevel, 1, 0), run.boss.hitpoints)
        assertFalse(run.room.standing)
    }
}
