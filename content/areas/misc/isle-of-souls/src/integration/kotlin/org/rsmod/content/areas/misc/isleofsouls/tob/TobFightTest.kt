package org.rsmod.content.areas.misc.isleofsouls.tob

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Each boss fight run for a few hundred ticks with two players in it, and through every phase
 * change to its end, as the plugin scripts would drive it.
 */
class TobFightTest {
    private object TobModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: TobParties,
        val raids: TobRaids,
        val rooms: TobRoomRegistry,
        val rewards: TobRewards,
        val collision: CollisionFlagMap,
        val routes: RouteFactory,
    )

    class Run(
        val deps: Deps,
        val alice: Player,
        val bob: Player,
        val raid: TobRaid,
        val room: TobRoom,
    ) {
        fun real(player: Player): CoordGrid = checkNotNull(raid.realOf(player.coords))

        fun <T : TobRoomRuntime> runtime(): T {
            @Suppress("UNCHECKED_CAST")
            return checkNotNull(deps.rooms.ensure(raid, room)) as T
        }
    }

    @AfterEach
    fun resetSettings() {
        EventSettings.current = EventSettings()
    }

    private fun GameTestState.runFight(room: TobRoom, body: GameTestScope.(Run) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            TobModule,
            WeaponAttackStylesScript::class,
            TobRoomDirector::class,
            TobMaidenScript::class,
            TobBloatScript::class,
            TobNylocasScript::class,
            TobSotetsegScript::class,
            TobXarpusScript::class,
            TobVerzikScript::class,
            TobVaultScript::class,
            TobSuppliesScript::class,
        ) { deps ->
            val zoneX = TobLayout.REAL_MIN_X / ZoneGrid.LENGTH
            val zoneZ = TobLayout.REAL_MIN_Z / ZoneGrid.LENGTH
            for (zx in zoneX until zoneX + TobLayout.ZONES) {
                for (zz in zoneZ until zoneZ + TobLayout.ZONES) {
                    for (level in 0 until CoordGrid.LEVEL_COUNT) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
            deps.collision.allocateIfAbsent(VerSinhaza.OUTSIDE.x, VerSinhaza.OUTSIDE.z, 0)
            deps.raids.fadeTicks = 0
            val alice = player
            alice.displayName = "Alice"
            alice.observerUUID = 1L
            alice.stats[stats.hitpoints] = 99
            val bob = registerPlayer(coords = VerSinhaza.OUTSIDE)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            bob.stats[stats.hitpoints] = 99
            PathingEntityCommon.telejump(alice, deps.collision, VerSinhaza.OUTSIDE)
            val party = deps.parties.create(alice)
            deps.parties.apply(party, bob)
            deps.parties.answer(party, bob, accept = true)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            // The test world's tiles without a map are blocked on the upper levels; open them all.
            open(deps, raid, room.bounds)
            room.arena?.let { open(deps, raid, it) }
            if (room == TobRoom.Sotetseg) {
                open(deps, raid, SotetsegRuntime.REALM)
                open(deps, raid, TobBox(3354, 4309, 3367, 4310, 3))
            }
            raid.room = room
            for (member in raid.active) {
                deps.raids.place(raid, member, room)
            }
            body(Run(deps, alice, bob, raid, room))
        }

    private fun open(deps: Deps, raid: TobRaid, box: TobBox) {
        for (x in box.minX..box.maxX) {
            for (z in box.minZ..box.maxZ) {
                val tile = raid.regionOf(CoordGrid(x, z, box.level))
                deps.collision[tile.x, tile.z, tile.level] = 0
            }
        }
    }

    /** Both players walk into the arena and the fight starts (through its barrier, or Verzik). */
    private fun GameTestScope.startFight(run: Run, at: CoordGrid) {
        val raid = run.raid
        PathingEntityCommon.telejump(run.alice, run.deps.collision, raid.regionOf(at))
        PathingEntityCommon.telejump(run.bob, run.deps.collision, raid.regionOf(at.translate(1, 0)))
        run.deps.raids.startFightHere(raid, run.room)
        assertTrue(run.deps.raids.isStarted(raid, run.room))
    }

    private fun GameTestScope.tick(run: Run, ticks: Int = 1) {
        repeat(ticks) {
            advance()
            run.deps.raids.tick()
            run.deps.rooms.tick()
            // Keep the party alive: these tests are about the boss, not the players' deaths.
            for (player in listOf(run.alice, run.bob)) {
                player.stats[stats.hitpoints] = 99
            }
        }
    }

    /** Kills [npc] the ordinary way: its death queue runs the room's handling. */
    private fun GameTestScope.kill(run: Run, npc: Npc) {
        npc.hitpoints = 0
        npc.queueDeath()
        tick(run, 2)
    }

    private fun hit(type: HitType, damage: Int): HitBuilder =
        HitBuilder(
            type = type,
            damage = damage,
            sourceUid = null,
            sourceSlot = null,
            isFromNpc = false,
            isFromPlayer = true,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = 0,
            sourceMaxDamageThreshold = 0,
        )

    @Test
    fun GameTestState.`the Maiden fights, calls Matomenos and falls`() =
        runFight(TobRoom.Maiden) { run ->
            startFight(run, CoordGrid(3175, 4446, 0))
            val maiden = run.runtime<MaidenRuntime>()
            val boss = checkNotNull(maiden.maiden)
            tick(run, 60)
            boss.hitpoints = boss.baseHitpointsLvl * 69 / 100
            tick(run, 2)
            assertEquals(4, maiden.matomenos.size)
            tick(run, 60)
            kill(run, checkNotNull(maiden.maiden))
            assertTrue(maiden.isCompleted)
            assertTrue(TobRoom.Maiden in run.raid.completed)
        }

    @Test
    fun GameTestState.`after the Maiden the party walks on to the Bloat through the fade`() =
        runFight(TobRoom.Maiden) { run ->
            startFight(run, CoordGrid(3175, 4446, 0))
            val maiden = run.runtime<MaidenRuntime>()
            tick(run, 30)
            kill(run, checkNotNull(maiden.maiden))
            tick(run, 20)
            open(run.deps, run.raid, TobRoom.Bloat.bounds)
            run.deps.raids.fadeTicks = TobRaids.FADE_TICKS
            assertEquals(TobRaids.PassResult.Moved, run.deps.raids.pass(run.alice))
            tick(run, 5)
            assertEquals(TobRoom.Bloat, run.raid.member(run.alice)?.room)
            assertEquals(TobRoom.Bloat, TobRoom.at(run.real(run.alice)))
            val bloat = run.deps.rooms.runtime(run.raid, TobRoom.Bloat) as BloatRuntime
            assertNotNull(bloat.bloat)
            tick(run, 30)
        }

    @Test
    fun GameTestState.`the Bloat walks, sleeps and falls, and hits on it walking are halved`() =
        runFight(TobRoom.Bloat) { run ->
            startFight(run, CoordGrid(3291, 4453, 0))
            val bloat = run.runtime<BloatRuntime>()
            val npc = checkNotNull(bloat.bloat)
            val walking = hit(HitType.Melee, 40)
            bloat.modifyHit(npc, walking, run.alice)
            assertEquals(20, walking.damage)
            var slept = false
            repeat(80) {
                tick(run)
                slept = slept || bloat.asleep
            }
            assertTrue(slept)
            kill(run, npc)
            assertTrue(bloat.isCompleted)
        }

    @Test
    fun GameTestState.`the Nylocas waves come and each takes damage only from its style`() =
        runFight(TobRoom.Nylocas) { run ->
            startFight(run, CoordGrid(3294, 4248, 0))
            val nylocas = run.runtime<NylocasRuntime>()
            assertEquals(4, nylocas.supports.size)
            var most = 0
            repeat(120) {
                tick(run)
                most = maxOf(most, nylocas.alive)
            }
            assertTrue(nylocas.wave > 5, "wave ${nylocas.wave}")
            assertTrue(most > 0)
            val any =
                nylocas.npcs.firstOrNull {
                    it.type.name.startsWith("Nylocas") && it !in nylocas.supports.values
                }
            if (any != null) {
                val melee = hit(HitType.Melee, 10)
                val ranged = hit(HitType.Ranged, 10)
                val magic = hit(HitType.Magic, 10)
                for (h in listOf(melee, ranged, magic)) {
                    nylocas.modifyHit(any, h, run.alice)
                }
                assertEquals(1, listOf(melee, ranged, magic).count { it.damage > 0 })
            }
        }

    @Test
    fun GameTestState.`Sotetseg takes one into the Shadow Realm and falls after the mazes`() =
        runFight(TobRoom.Sotetseg) { run ->
            startFight(run, CoordGrid(3276, 4315, 0))
            val sotetseg = run.runtime<SotetsegRuntime>()
            val npc = checkNotNull(sotetseg.sotetseg)
            tick(run, 30)
            npc.hitpoints = npc.baseHitpointsLvl * 60 / 100
            tick(run, 2)
            assertTrue(sotetseg.inMaze)
            val runner = checkNotNull(sotetseg.runner)
            assertEquals(3, run.real(runner).level)
            tick(run, 20)
            assertTrue(sotetseg.leaveRealm(runner))
            assertFalse(sotetseg.inMaze)
            assertEquals(0, run.real(runner).level)
            // It cannot die during a maze; out of it, it does.
            npc.hitpoints = npc.baseHitpointsLvl * 30 / 100
            tick(run, 2)
            assertTrue(sotetseg.inMaze)
            sotetseg.leaveRealm(checkNotNull(sotetseg.runner))
            kill(run, npc)
            assertTrue(sotetseg.isCompleted)
        }

    @Test
    fun GameTestState.`Sotetseg's tornado is centred on the maze path it follows`() =
        runFight(TobRoom.Sotetseg) { run ->
            startFight(run, CoordGrid(3276, 4315, 0))
            val sotetseg = run.runtime<SotetsegRuntime>()
            val npc = checkNotNull(sotetseg.sotetseg)
            tick(run, 5)
            npc.hitpoints = npc.baseHitpointsLvl * 60 / 100
            tick(run, 2)
            assertTrue(sotetseg.inMaze)
            val runner = checkNotNull(sotetseg.runner)
            val other = if (runner === run.alice) run.bob else run.alice
            // Someone on the maze's fourth row sets the tornado off.
            val row = SotetsegRuntime.MAZE_FLOOR
            PathingEntityCommon.telejump(
                other,
                run.deps.collision,
                run.raid.regionOf(CoordGrid(row.maxX, row.minZ + 3, 0)),
            )
            var tornado: Npc? = null
            repeat(5) {
                if (tornado == null) {
                    tick(run)
                    tornado = sotetseg.tornado
                }
            }
            val spawned = checkNotNull(tornado)
            val trail = sotetseg.tornadoTrail
            assertEquals(3, spawned.size)
            fun centre() = checkNotNull(run.raid.realOf(spawned.coords)).translate(1, 1)
            assertEquals(trail[0], centre())
            // It walks the path one tile at a time, never cutting a corner.
            var at = 0
            repeat(12) {
                tick(run)
                if (sotetseg.tornado === spawned) {
                    val next = trail.indexOf(centre())
                    assertTrue(next >= 0, "tornado centre ${centre()} is off the path")
                    assertTrue(next - at in 0..1, "tornado jumped from tile $at to $next")
                    at = next
                }
            }
            assertTrue(at >= 8, "tornado only got to tile $at")
        }

    @Test
    fun GameTestState.`Xarpus feeds, spits, turns on a quadrant and falls`() =
        runFight(TobRoom.Xarpus) { run ->
            startFight(run, CoordGrid(3165, 4382, 1))
            val xarpus = run.runtime<XarpusRuntime>()
            val npc = checkNotNull(xarpus.xarpus)
            assertEquals(XarpusRuntime.Phase.Recovery, xarpus.phase)
            assertEquals(
                npc.baseHitpointsLvl * 75 / 100,
                npc.hitpoints.coerceAtMost(npc.baseHitpointsLvl * 75 / 100),
            )
            var spat = false
            repeat(160) {
                tick(run)
                spat = spat || xarpus.phase == XarpusRuntime.Phase.Poison
            }
            assertTrue(spat, "phase ${xarpus.phase}")
            npc.hitpoints = npc.baseHitpointsLvl * 20 / 100
            tick(run, 2)
            assertEquals(XarpusRuntime.Phase.Counter, xarpus.phase)
            assertNotNull(xarpus.staring)
            tick(run, 20)
            kill(run, npc)
            assertTrue(xarpus.isCompleted)
        }

    @Test
    fun GameTestState.`Verzik goes through her three phases and the raid is won`() =
        runFight(TobRoom.Verzik) { run ->
            startFight(run, CoordGrid(3160, 4309, 0))
            val verzik = run.runtime<VerzikRuntime>()
            run.deps.rewards.pick = { n -> n - 1 }
            assertEquals(1, verzik.phase)
            // Only the Dawnbringer's special hurts her properly in the first phase.
            val melee = hit(HitType.Melee, 50)
            verzik.modifyHit(checkNotNull(verzik.verzik), melee, run.alice)
            assertEquals(VerzikRuntime.CAP_MELEE, melee.damage)
            tick(run, 40)
            kill(run, checkNotNull(verzik.verzik))
            tick(run, 20)
            assertEquals(2, verzik.phase)
            tick(run, 80)
            kill(run, checkNotNull(verzik.verzik))
            tick(run, 6)
            assertEquals(3, verzik.phase)
            tick(run, 100)
            kill(run, checkNotNull(verzik.verzik))
            tick(run, 12)
            assertTrue(verzik.isCompleted)
            assertTrue(run.raid.won)
            assertEquals(1, run.alice.vars[tob_varps.completions])
            assertTrue(run.deps.rewards.claim(run.raid, run.alice))
            assertTrue(run.deps.rewards.hasLoot(run.alice))
            // The treasure room opens: the way on is the vault.
            assertEquals(TobRaids.PassResult.Moved, run.deps.raids.pass(run.alice))
            assertEquals(TobRoom.Vault, run.raid.member(run.alice)?.room)
        }

    @Test
    fun GameTestState.`clicking past Verzik while she spins webs walks round her`() =
        runFight(TobRoom.Verzik) { run ->
            startFight(run, CoordGrid(3160, 4309, 0))
            val verzik = run.runtime<VerzikRuntime>()
            tick(run, 2)
            kill(run, checkNotNull(verzik.verzik))
            tick(run, 20)
            kill(run, checkNotNull(verzik.verzik))
            tick(run, 6)
            assertEquals(3, verzik.phase)
            verzik.spinWebsNow()
            var waited = 0
            while (!verzik.isSpinning && waited++ < 30) {
                tick(run)
            }
            assertTrue(verzik.isSpinning, "she never started spinning")
            val boss = checkNotNull(verzik.verzik)
            assertEquals(run.raid.regionOf(VerzikRuntime.WEB_CENTRE), boss.coords)

            // A click from her west side to her east side, straight through her tiles.
            val from = run.raid.regionOf(CoordGrid(3163, 4313, 0))
            val to = run.raid.regionOf(CoordGrid(3173, 4313, 0))
            assertTrue(verzik.isClosed(run.raid.regionOf(CoordGrid(3168, 4313, 0))))
            PathingEntityCommon.telejump(run.alice, run.deps.collision, from)
            tick(run)
            val route = run.deps.routes.create(run.alice.avatar, to)
            assertTrue(route.success && !route.alternative, "route $route")
            var at = from
            for (waypoint in route) {
                while (at.x != waypoint.x || at.z != waypoint.z) {
                    at =
                        at.translate(
                            Integer.signum(waypoint.x - at.x),
                            Integer.signum(waypoint.z - at.z),
                        )
                    assertFalse(verzik.isClosed(at), "the route goes under her at $at")
                }
            }
            assertEquals(to, at)

            // And walking it gets there.
            run.alice.routeRequest = RouteRequestCoord(to, clientRequest = true)
            var steps = 0
            while (run.alice.coords != to && steps++ < 30) {
                run.alice.frozenUntil = Int.MIN_VALUE
                tick(run)
                assertFalse(verzik.isClosed(run.alice.coords), "walked under her")
            }
            assertEquals(to, run.alice.coords)
        }
}
