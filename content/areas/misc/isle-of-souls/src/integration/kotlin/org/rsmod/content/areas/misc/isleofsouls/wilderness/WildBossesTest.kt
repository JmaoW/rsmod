package org.rsmod.content.areas.misc.isleofsouls.wilderness

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.spells.attack.SpellAccuracyOverrides
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

class WildBossesTest {
    private object WildModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(WildBosses::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val bosses: WildBosses,
        val script: WildBossScript,
        val tracker: LeagueTaskTracker,
        val rewards: WildRewards,
        val spellOverrides: SpellAccuracyOverrides,
    )

    /** alice and bob, one team, with every lair's boss spawned. */
    private fun GameTestState.runWildTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            WildModule,
            WildBossScript::class,
            LeagueKillTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 101L
            alice.observerUUID = 101L
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.uuid = 102L
            bob.observerUUID = 102L
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            topUp(alice, bob)
            for (lair in WildLair.entries) {
                allocZoneCollision(lair.arrival)
                allocZoneCollision(lair.bossSpawn)
            }
            deps.bosses.spawnAll()
            body(deps, alice, bob)
        }

    private fun GameTestScope.topUp(vararg players: Player) {
        for (each in players) {
            each.stats[stats.hitpoints] = 99
        }
    }

    private fun GameTestScope.until(
        what: String,
        vararg players: Player,
        timeout: Int = 400,
        predicate: () -> Boolean,
    ) {
        var ticks = 0
        while (!predicate()) {
            check(ticks++ < timeout) { "never happened: $what" }
            topUp(*players)
            advance()
        }
    }

    private fun Deps.boss(lair: WildLair): Npc = checkNotNull(bosses.bossOf(lair))

    /** Puts [player] in [lair] a few tiles north of the boss, as if they had walked in. */
    private fun GameTestScope.enterLair(deps: Deps, lair: WildLair, player: Player) {
        player.placeAt(CoordGrid(lair.arrival.x, lair.arrival.z, lair.arrival.level))
    }

    // The data --------------------------------------------------------------------------------

    @Test
    fun GameTestState.`every lair is distinct and well formed`() = runWildTest { deps, _, _ ->
        assertEquals(6, WildLair.entries.size)
        assertEquals(6, WildLair.entries.map { it.key }.toSet().size)
        assertEquals(3, WildLair.entries.count { it.multi })
        for (lair in WildLair.entries) {
            assertTrue(lair.arrival in lair.arena, "${lair.key} arrival is on its arena")
            assertTrue(lair.bossSpawn in lair.arena, "${lair.key} boss spawn is on its arena")
            assertTrue(lair.exitCoords in lair.area, "${lair.key} exit is in its area")
            val boss = deps.boss(lair)
            assertEquals(lair.bossSize, boss.size, "${lair.key} boss size")
            assertEquals(lair.bossSpawn, boss.coords, "${lair.key} boss is at its spawn")
            assertTrue(
                deps.bosses.standable(lair.bossSpawn, boss.size),
                "${lair.key} boss spawn is open floor",
            )
            assertTrue(deps.bosses.walkable(lair.arrival), "${lair.key} arrival is open floor")
        }
        for ((a, b) in WildLair.entries.flatMap { x -> WildLair.entries.map { y -> x to y } }) {
            if (a != b) {
                assertFalse(
                    a.area.level == b.area.level &&
                        a.area.minX <= b.area.maxX &&
                        b.area.minX <= a.area.maxX &&
                        a.area.minZ <= b.area.maxZ &&
                        b.area.minZ <= a.area.maxZ,
                    "${a.key} overlaps ${b.key}",
                )
            }
        }
    }

    @Test
    fun GameTestState.`the bosses have the wiki's stats from the cache`() =
        runWildTest { deps, _, _ ->
            assertEquals(1000, deps.boss(WildLair.CALLISTO).baseHitpointsLvl)
            assertEquals(450, deps.boss(WildLair.HUNTERS_END).baseHitpointsLvl)
            assertEquals(850, deps.boss(WildLair.SILK_CHASM).baseHitpointsLvl)
            assertEquals(515, deps.boss(WildLair.WEB_CHASM).baseHitpointsLvl)
            assertEquals(255, deps.boss(WildLair.VETIONS_REST).baseHitpointsLvl)
            assertEquals(150, deps.boss(WildLair.SKELETAL_TOMB).baseHitpointsLvl)
        }

    // The doors -------------------------------------------------------------------------------

    private fun GameTestScope.locAt(
        coords: CoordGrid,
        type: org.rsmod.game.type.loc.LocType,
    ): LocInfo? = findLocs(coords).firstOrNull { it.coords == coords && it.id == type.id }

    @Test
    fun GameTestState.`each entrance in the Revenant Caves leads into its lair`() =
        runWildTest { deps, alice, _ ->
            for (lair in WildLair.entries) {
                allocZoneCollision(lair.entrance.sw)
            }
            allocZoneCollision(EscapeCaves.entrance.sw)
            deps.script.placeEntrances()

            for (lair in WildLair.entries) {
                val info = checkNotNull(locAt(lair.entrance.sw, lair.entrance.type)) { lair.key }
                alice.placeAt(lair.entrance.front)
                alice.opLoc1(BoundLocInfo(info, locTypes[lair.entrance.type]))
                advance(ticks = 6)
                assertEquals(lair.arrival, alice.coords, lair.key)
            }
        }

    @Test
    fun GameTestState.`the exits lead to the Escape Caves or back to the Revenant Caves`() =
        runWildTest { deps, alice, _ ->
            for (lair in WildLair.entries) {
                allocZoneCollision(lair.exitCoords)
                allocZoneCollision(lair.entrance.front)
                val exit =
                    placeMapLoc(
                        lair.exitCoords,
                        lair.exitType,
                        org.rsmod.game.loc.LocShape.CentrepieceStraight,
                        org.rsmod.game.loc.LocAngle.North,
                    )
                alice.placeAt(
                    CoordGrid(lair.exitCoords.x, lair.exitCoords.z + 2, lair.exitCoords.level)
                )
                alice.opLoc1(exit)
                advance(ticks = 6)
                val expected = EscapeCaves.arrivalOf(lair) ?: lair.entrance.front
                assertEquals(expected, alice.coords, lair.key)
            }
            // The three big lairs open at three different places in the caves, all inside them.
            val mouths = WildLair.entries.mapNotNull { EscapeCaves.arrivalOf(it) }
            assertEquals(3, mouths.toSet().size)
            for (mouth in mouths) {
                assertTrue(mouth in EscapeCaves.area)
                assertTrue(deps.bosses.walkable(mouth))
            }
        }

    @Test
    fun GameTestState.`the Escape Caves are entered and left by their own objects`() =
        runWildTest { deps, alice, _ ->
            allocZoneCollision(EscapeCaves.entrance.sw)
            deps.script.placeEntrances()
            val info = checkNotNull(locAt(EscapeCaves.entrance.sw, EscapeCaves.entrance.type))
            alice.placeAt(EscapeCaves.entrance.front)
            alice.opLoc1(BoundLocInfo(info, locTypes[EscapeCaves.entrance.type]))
            advance(ticks = 6)
            assertTrue(alice.coords in EscapeCaves.fronts, "entered at ${alice.coords}")

            for ((type, spot) in EscapeCaves.exits) {
                allocZoneCollision(spot)
                val exit =
                    placeMapLoc(
                        spot,
                        type,
                        org.rsmod.game.loc.LocShape.CentrepieceStraight,
                        org.rsmod.game.loc.LocAngle.North,
                    )
                alice.placeAt(CoordGrid(spot.x, spot.z + 2, spot.level))
                alice.opLoc1(exit)
                advance(ticks = 6)
                assertEquals(EscapeCaves.entrance.front, alice.coords)
            }
            for (front in EscapeCaves.fronts) {
                assertTrue(front in EscapeCaves.area)
                assertTrue(deps.bosses.walkable(front))
            }
        }

    // Callisto and Artio ----------------------------------------------------------------------

    @Test
    fun GameTestState.`Callisto walks up and hits, then roars and sets traps at 66 percent`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.CALLISTO
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            enterLair(deps, lair, alice)
            until("Callisto hurts her", alice, timeout = 200) { alice.hitpoints < 99 }
            assertTrue(fight.engaged)

            boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
            until("the first roar", alice, timeout = 40) { fight.stage >= 1 }
            until("traps appear", alice, timeout = 40) { fight.traps.isNotEmpty() }
            assertEquals(1, fight.stage)
            boss.hitpoints = boss.baseHitpointsLvl * 30 / 100
            until("the second roar", alice, timeout = 60) { fight.stage >= 2 }
        }

    @Test
    fun GameTestState.`a player on a trap is bound`() = runWildTest { deps, alice, _ ->
        val lair = WildLair.HUNTERS_END
        val fight = deps.bosses.fightIn(lair) as CallistoFight
        enterLair(deps, lair, alice)
        fight.engaged = true
        fight.attackAt = Int.MAX_VALUE
        val tile = alice.coords
        fight.traps[tile] = BearTrap(expires = deps.bosses.now + 100, armed = true)
        advance(ticks = 3)
        assertTrue(alice.isFrozen)
        assertFalse(tile in fight.traps)
    }

    @Test
    fun GameTestState.`a trap that has not appeared yet does not bind`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.HUNTERS_END
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            enterLair(deps, lair, alice)
            fight.engaged = true
            fight.attackAt = Int.MAX_VALUE
            fight.traps[alice.coords] = BearTrap(expires = deps.bosses.now + 100)
            advance(ticks = 3)
            assertFalse(alice.isFrozen)
        }

    @Test
    fun GameTestState.`the first roar starts waves of five traps every six ticks`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.CALLISTO
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            enterLair(deps, lair, alice)
            fight.engaged = true
            fight.attackAt = Int.MAX_VALUE
            fight.stage = 1
            fight.nextWaveAt = deps.bosses.now
            advance(ticks = 1)
            assertEquals(CallistoLogic.WAVE_SIZE, fight.traps.size)
            assertTrue(fight.nextWaveAt - deps.bosses.now in 4..CallistoLogic.WAVE_EVERY)
            val middle = deps.bosses.centre(deps.boss(lair))
            for (tile in fight.traps.keys) {
                assertTrue(deps.bosses.chebyshev(tile, middle) <= CallistoLogic.TRAP_RADIUS)
                assertFalse(deps.bosses.onBody(deps.boss(lair), tile))
            }
            advance(ticks = CallistoLogic.WAVE_EVERY + 1)
            assertTrue(fight.traps.size > CallistoLogic.WAVE_SIZE, "a second wave went down")
        }

    @Test
    fun GameTestState.`the orb throws a player straight away from Callisto`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.CALLISTO
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            // One tile off the line north of Callisto's middle: the old rule threw her diagonally.
            val start = CoordGrid(3361, 10331, lair.arrival.level)
            allocZoneCollision(start)
            alice.placeAt(start)
            fight.engaged = true
            fight.attackAt = 0
            fight.orbAttack = 1
            until("the orb throws her", alice, timeout = 60) { alice.coords != start }
            advance(ticks = 8)
            assertEquals(start.x, alice.coords.x)
            assertEquals(start.z + CallistoLogic.KNOCKBACK_TILES, alice.coords.z)
            assertFalse(alice.isFrozen, "the orb does not stun")
        }

    @Test
    fun GameTestState.`attacks follow the captured cadence and the orb comes every 8 to 12`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.CALLISTO
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            val start = CoordGrid(3361, 10331, lair.arrival.level)
            allocZoneCollision(start)
            alice.placeAt(start)
            fight.engaged = true
            fight.attackAt = 0
            until("the first attack", alice, timeout = 20) { fight.attacks == 1 }
            assertTrue(fight.orbAttack in CallistoLogic.ORB_MIN..CallistoLogic.ORB_MAX)
            // She is not next to it, so the first attack is the ranged one: 5 ticks to the next.
            val wait = fight.attackAt - deps.bosses.now
            assertTrue(wait in CallistoLogic.RANGED_RATE - 1..CallistoLogic.RANGED_RATE, "$wait")
        }

    @Test
    fun GameTestState.`a roar breaks the freeze and the immunity after it`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.CALLISTO
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as CallistoFight
            enterLair(deps, lair, alice)
            fight.attackAt = Int.MAX_VALUE
            boss.freeze(20)
            deps.bosses.freezeImmunity.immuneUntil(boss, deps.bosses.now + 25)
            assertTrue(deps.bosses.freezeImmunity.isImmune(boss, deps.bosses.now))
            boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
            until("the first roar", alice, timeout = 40) { fight.stage >= 1 }
            assertFalse(boss.isFrozen)
            assertFalse(deps.bosses.freezeImmunity.isImmune(boss, deps.bosses.now))
        }

    @Test
    fun GameTestState.`the first freezing spell on Callisto or Artio is certain to hit`() =
        runWildTest { deps, alice, _ ->
            for (lair in listOf(WildLair.CALLISTO, WildLair.HUNTERS_END)) {
                val boss = deps.boss(lair)
                for (spell in
                    listOf(objs.spell_ice_barrage, objs.spell_ice_rush, objs.spell_entangle)) {
                    assertTrue(deps.spellOverrides.guaranteed(alice, boss, spell), lair.key)
                }
                // Only freezing spells, and only while he is not already held.
                assertFalse(deps.spellOverrides.guaranteed(alice, boss, objs.spell_fire_blast))
                boss.freeze(10)
                assertFalse(deps.spellOverrides.guaranteed(alice, boss, objs.spell_ice_barrage))
            }
            // Nobody else gets the guarantee.
            val other = deps.boss(WildLair.SILK_CHASM)
            assertFalse(deps.spellOverrides.guaranteed(alice, other, objs.spell_ice_barrage))
        }

    // Venenatis and Spindel -------------------------------------------------------------------

    @Test
    fun GameTestState
        .`Venenatis calls spiderlings on her first ranged attack and webs on the tenth`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.SILK_CHASM
            val fight = deps.bosses.fightIn(lair) as VenenatisFight
            enterLair(deps, lair, alice)
            until("spiderlings arrive", alice, timeout = 200) { fight.spiderlings.isNotEmpty() }
            assertEquals(2, fight.spiderlings.size)

            fight.attacks = 10
            fight.attackAt = 0
            until("a web is spat", alice, timeout = 60) { fight.webs.isNotEmpty() }
            assertTrue(fight.webs.first().tiles.isNotEmpty())
            assertTrue(fight.webs.first().tiles.size <= 35)

            // The scenery is the real web: a lattice block with corner pieces and an edge fringe.
            val web = fight.webs.first()
            assertTrue(web.locs.size >= web.tiles.size)
            val types = web.locs.map { it.entity.id }.toSet()
            assertTrue(wild_locs.web_centre.id in types)
            assertTrue(wild_locs.web_edge.id in types)
            assertTrue(wild_locs.web_corner.id in types)

            // It is gone again once it wears off.
            until("the web wears off", alice, timeout = 60) { fight.webs.isEmpty() }
        }

    @Test
    fun GameTestState.`Spindel is the same fight in the Web Chasm`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.WEB_CHASM
            val fight = deps.bosses.fightIn(lair) as VenenatisFight
            enterLair(deps, lair, alice)
            until("Spindel's spiderlings arrive", alice, timeout = 200) {
                fight.spiderlings.isNotEmpty()
            }
            assertTrue(fight.spiderlings.all { it.visType.id == wild_npcs.spindel_spiderling.id })
        }

    // Vet'ion and Calvar'ion ------------------------------------------------------------------

    @Test
    fun GameTestState.`Vet'ion is immune while his hellhounds live and rises enraged`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.VETIONS_REST
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as VetionFight
            enterLair(deps, lair, alice)
            until("he engages", alice, timeout = 100) { fight.engaged }

            boss.hitpoints = boss.baseHitpointsLvl / 2
            until("the hounds come", alice, timeout = 60) { fight.hounds.size == 2 }
            assertTrue(fight.hounds.all { it.visType.id == wild_npcs.vetion_hound.id })

            // Nothing hurts him while they live.
            val before = boss.hitpoints
            boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, 30)
            advance(ticks = 3)
            assertEquals(before, boss.hitpoints)

            // Once they are dead he can be hurt again.
            for (hound in fight.hounds.toList()) {
                hound.hitpoints = 0
            }
            advance(ticks = 2)
            boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, 30)
            advance(ticks = 3)
            assertTrue(boss.hitpoints < before)

            // The first form falls and he rises with full health, enraged.
            boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, boss.hitpoints)
            until("he rises", alice, timeout = 40) { fight.form == 1 }
            assertEquals(boss.baseHitpointsLvl, boss.hitpoints)
            assertEquals(wild_npcs.vetion_enraged.id, boss.visType.id)
        }

    @Test
    fun GameTestState.`Vet'ion stands still for an attack and only walks between them`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.VETIONS_REST
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as VetionFight
            enterLair(deps, lair, alice)
            until("he engages", alice, timeout = 100) { fight.engaged }

            // The test world does not move npcs, so what shows it is whether he has a walk planned.
            var chased = false
            var held = 0
            repeat(120) {
                advance()
                topUp(alice)
                if (deps.bosses.now < fight.busyUntil) {
                    held++
                    assertTrue(boss.routeDestination.isEmpty(), "he walked during an attack")
                } else if (boss.routeDestination.isNotEmpty()) {
                    chased = true
                }
            }
            assertTrue(held > 0, "he held still for at least one attack")
            assertTrue(chased, "he chases between attacks")
        }

    @Test
    fun GameTestState.`lightning damage lands only as the strike ends`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.VETIONS_REST
            val fight = deps.bosses.fightIn(lair) as VetionFight
            enterLair(deps, lair, alice)
            until("he engages", alice, timeout = 100) { fight.engaged }

            var start = -1
            var lastBusy = fight.busyUntil
            var hurt = 0
            repeat(150) {
                advance()
                if (fight.busyUntil != lastBusy) {
                    lastBusy = fight.busyUntil
                    start = deps.bosses.now
                }
                if (alice.hitpoints < 99) {
                    hurt++
                    assertTrue(start >= 0)
                    assertTrue(
                        deps.bosses.now - start >= 3,
                        "hit ${deps.bosses.now - start} ticks after the shout",
                    )
                }
                topUp(alice)
            }
            assertTrue(hurt > 0, "the lightning hurt her at least once")
        }

    @Test
    fun GameTestState
        .`the bash only stuns whoever is on the shadowed tiles or under him as it lands`() =
        runWildTest { deps, alice, bob ->
            val lair = WildLair.VETIONS_REST
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as VetionFight
            val logic = deps.bosses.logicOf(WildFamily.Vetion) as VetionLogic
            val x = boss.coords.x
            val z = boss.coords.z
            val level = boss.coords.level
            enterLair(deps, lair, alice)
            fight.engaged = true
            fight.attackAt = Int.MAX_VALUE

            // alice stands on a shadowed tile and moves to another one; bob steps off them.
            alice.placeAt(CoordGrid(x - 1, z + 1, level))
            bob.placeAt(CoordGrid(x - 2, z + 1, level))
            val start = deps.bosses.now
            logic.bash(fight, alice, false)
            assertEquals(logic.bashFacing(boss, alice), fight.faceLock)
            advance(ticks = 2)
            assertFalse(alice.isFrozen)
            alice.placeAt(CoordGrid(x - 2, z + 2, level))
            bob.placeAt(CoordGrid(x - 6, z + 1, level))
            until("the bash lands", alice, bob, timeout = 8) { alice.isFrozen }
            assertTrue(deps.bosses.now - start >= 4, "landed on tick ${deps.bosses.now - start}")
            assertFalse(bob.isFrozen, "bob had stepped off the shadows")

            // Standing under him is caught too.
            fight.faceLock = null
            val second = deps.bosses.now
            logic.bash(fight, alice, false)
            bob.placeAt(CoordGrid(x + 1, z + 1, level))
            until("the second bash lands", alice, bob, timeout = 8) { bob.isFrozen }
            assertTrue(deps.bosses.now - second >= 4)
        }

    @Test
    fun GameTestState.`Vet'ion stays put through the enrage ring`() =
        runWildTest { deps, alice, _ ->
            val lair = WildLair.VETIONS_REST
            val boss = deps.boss(lair)
            val fight = deps.bosses.fightIn(lair) as VetionFight
            enterLair(deps, lair, alice)
            until("he engages", alice, timeout = 100) { fight.engaged }

            // He fell while walking towards her.
            boss.walk(alice.coords)
            boss.hitpoints = 0
            boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, 1)
            until("he rises", alice, timeout = 40) { fight.form == 1 }
            assertTrue(boss.routeDestination.isEmpty(), "his walk was dropped")
            while (deps.bosses.now < fight.busyUntil - 1) {
                advance()
                topUp(alice)
                assertTrue(boss.routeDestination.isEmpty(), "he walked before the ring struck")
            }
            assertTrue(fight.busyUntil - fight.enragedAt >= 5)
        }

    @Test
    fun GameTestState.`the enrage ring is twelve strike points round his body`() =
        runWildTest { deps, _, _ ->
            val boss = deps.boss(WildLair.VETIONS_REST)
            val logic = deps.bosses.logicOf(WildFamily.Vetion) as VetionLogic
            val x = boss.coords.x + 1
            val z = boss.coords.z + 1
            val level = boss.coords.level
            val centres = logic.enrageCentres(boss)
            assertEquals(12, centres.toSet().size)
            for ((dx, dz) in listOf(1 to 3, -1 to -3, 3 to 1, -3 to 1, 2 to 2, -2 to -2, 2 to -2)) {
                assertTrue(CoordGrid(x + dx, z + dz, level) in centres, "($dx, $dz)")
            }
            assertFalse(CoordGrid(x, z + 3, level) in centres)
        }

    @Test
    fun GameTestState.`the side bash is two straight lines in front of him`() =
        runWildTest { deps, alice, _ ->
            val boss = deps.boss(WildLair.VETIONS_REST)
            val logic = deps.bosses.logicOf(WildFamily.Vetion) as VetionLogic
            val x = boss.coords.x
            val z = boss.coords.z
            val level = boss.coords.level

            // Standing at the middle of his west side.
            alice.placeAt(CoordGrid(x - 1, z + 1, level))
            val west = logic.bashLines(boss, alice)
            assertEquals(12, west.size)
            for (k in -1..3) {
                assertTrue(CoordGrid(x - 1, z + k, level) in west, "first line, row $k")
            }
            for (k in -2..4) {
                assertTrue(CoordGrid(x - 2, z + k, level) in west, "second line, row $k")
            }

            // The middle of his north side: the same, turned.
            alice.placeAt(CoordGrid(x + 1, z + 3, level))
            val north = logic.bashLines(boss, alice)
            assertEquals(12, north.size)
            assertTrue(CoordGrid(x - 1, z + 3, level) in north)
            assertTrue(CoordGrid(x + 3, z + 3, level) in north)
            assertTrue(CoordGrid(x - 2, z + 4, level) in north)
            assertTrue(CoordGrid(x + 4, z + 4, level) in north)
        }

    @Test
    fun GameTestState.`the corner bash wraps two lines round the nearest corner`() =
        runWildTest { deps, alice, _ ->
            val boss = deps.boss(WildLair.VETIONS_REST)
            val logic = deps.bosses.logicOf(WildFamily.Vetion) as VetionLogic
            val x = boss.coords.x
            val z = boss.coords.z
            val level = boss.coords.level

            // The north-west corner tile and the two tiles beside it get the same bash.
            val expected =
                setOf(
                    // First line (5): from the middle of the west side, round, to the north middle.
                    CoordGrid(x - 1, z + 1, level),
                    CoordGrid(x - 1, z + 2, level),
                    CoordGrid(x - 1, z + 3, level),
                    CoordGrid(x, z + 3, level),
                    CoordGrid(x + 1, z + 3, level),
                    // Second line (7).
                    CoordGrid(x - 2, z + 1, level),
                    CoordGrid(x - 2, z + 2, level),
                    CoordGrid(x - 2, z + 3, level),
                    CoordGrid(x - 2, z + 4, level),
                    CoordGrid(x - 1, z + 4, level),
                    CoordGrid(x, z + 4, level),
                    CoordGrid(x + 1, z + 4, level),
                )
            for (spot in
                listOf(
                    CoordGrid(x - 1, z + 3, level),
                    CoordGrid(x, z + 3, level),
                    CoordGrid(x - 1, z + 2, level),
                )) {
                alice.placeAt(spot)
                assertEquals(expected, logic.bashLines(boss, alice), "standing on $spot")
            }

            // The south-east corner is the mirror image.
            alice.placeAt(CoordGrid(x + 3, z - 1, level))
            val southEast = logic.bashLines(boss, alice)
            assertEquals(12, southEast.size)
            assertTrue(CoordGrid(x + 3, z + 1, level) in southEast)
            assertTrue(CoordGrid(x + 1, z - 1, level) in southEast)
            assertTrue(CoordGrid(x + 4, z + 1, level) in southEast)
            assertTrue(CoordGrid(x + 4, z - 2, level) in southEast)
        }

    @Test
    fun GameTestState.`Calvar'ion calls the singles hellhounds`() = runWildTest { deps, alice, _ ->
        val lair = WildLair.SKELETAL_TOMB
        val boss = deps.boss(lair)
        val fight = deps.bosses.fightIn(lair) as VetionFight
        enterLair(deps, lair, alice)
        until("he engages", alice, timeout = 100) { fight.engaged }
        boss.hitpoints = boss.baseHitpointsLvl / 2
        until("the hounds come", alice, timeout = 60) { fight.hounds.size == 2 }
        assertTrue(fight.hounds.all { it.visType.id == wild_npcs.calvarion_hound.id })
    }

    // Rewards ---------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a kill is shared by damage and counts once for the team`() =
        runWildTest { deps, alice, bob ->
            val lair = WildLair.HUNTERS_END
            val boss = deps.boss(lair)
            enterLair(deps, lair, alice)
            bob.placeAt(lair.arrival.translate(1, 0))
            boss.heroPoints(alice, 300)
            boss.heroPoints(bob, 100)

            val winners = deps.rewards.recipients(boss)
            assertEquals(listOf(alice, bob), winners.map { it.first })
            assertEquals(0.75, winners[0].second, 0.001)

            boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, boss.hitpoints)
            until("the boss dies", alice, bob, timeout = 80) { deps.bosses.fightIn(lair)!!.dead }
            advance(ticks = 12)
            // Artio counts as a Callisto for the kill tasks, once for the team.
            assertEquals(1, alice.vars[LeagueProgressVarps.kill_callisto_50])
            assertEquals(1, bob.vars[LeagueProgressVarps.kill_callisto_50])
        }

    @Test
    fun GameTestState.`the boss comes back after it dies`() = runWildTest { deps, alice, _ ->
        val lair = WildLair.WEB_CHASM
        val boss = deps.boss(lair)
        val fight = deps.bosses.fightIn(lair)!!
        enterLair(deps, lair, alice)
        boss.heroPoints(alice, 100)
        boss.queueHit(alice, 1, org.rsmod.game.hit.HitType.Melee, boss.hitpoints)
        until("the boss dies", alice, timeout = 80) { fight.dead }
        until("the boss is back", alice, timeout = 300) { !fight.dead && boss.hitpoints > 0 }
        assertEquals(boss.baseHitpointsLvl, boss.hitpoints)
        assertEquals(lair.bossSpawn, boss.coords)
    }

    // The Escape Caves' creatures -------------------------------------------------------------

    @Test
    fun GameTestState.`a player in the Escape Caves is attacked by the creatures`() =
        runWildTest { deps, alice, _ ->
            deps.script.spawnCaveCreatures()
            val spot = EscapeCaves.fronts.first()
            alice.placeAt(spot)
            until("something bites", alice, timeout = 400) { alice.hitpoints < 99 }
        }
}
