package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo

class FightCaveRunTest {
    private object CaveModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(FightCaves::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val caves: FightCaves,
        val attacks: NpcAttacks,
        val targeting: NpcTargeting,
        val collision: org.rsmod.routefinder.collision.CollisionFlagMap,
        val routes: org.rsmod.api.route.RouteFactory,
        val script: FightCaveScript,
        val completions: FightCaveCompletions,
    )

    private class Cast(val alice: Player, val bob: Player, val carol: Player)

    /** alice and bob share team 1 and a cave; carol is a lone player. */
    private fun GameTestState.runCaveTest(body: GameTestScope.(Deps, Cast) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            CaveModule,
            FightCaveScript::class,
            FightCaveCombatScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            for (each in listOf(alice, bob, carol)) {
                each.stats[stats.hitpoints] = 99
            }
            deps.caves.settings = FightCaveSettings(FightCaveMode.Shortened)
            body(deps, Cast(alice, bob, carol))
        }

    private fun FightCaveRun.alive(): List<Npc> = monsters.filter { it.hitpoints > 0 }

    /** Kills everything in the wave, including the small Tz-Kek that the big ones leave behind. */
    private fun GameTestScope.killAll(run: FightCaveRun) {
        repeat(3) {
            for (npc in run.monsters.toList()) {
                npc.hitpoints = 0
                npc.queueDeath()
            }
            advance(ticks = 2)
        }
    }

    /** Waits for the next wave to spawn. */
    private fun GameTestScope.nextWave(run: FightCaveRun, number: Int) {
        advanceUntil(
            predicate = { run.waveNumber == number },
            timeoutTicks = 40,
            timeoutMessage = { "wave $number never spawned" },
        )
    }

    private fun kinds(run: FightCaveRun): List<FightCaveMonster> =
        run.monsters.map { FightCaveMonsterDefs.of(it.type)!!.kind }

    @Test
    fun GameTestState.`entering starts a run that spawns wave one after a short pause`() =
        runCaveTest { deps, cast ->
            assertEquals(EnterResult.Entered, deps.caves.enter(cast.alice))
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            assertEquals(FightCaveMode.Shortened, run.mode)
            assertEquals(9, run.waves.count)
            assertEquals(0, run.waveNumber)
            assertTrue(deps.instances.instanceOf(cast.alice)!!.contains(cast.alice.coords))

            nextWave(run, 1)

            assertEquals(listOf(FightCaveMonster.TzKih, FightCaveMonster.TzKih), kinds(run))
            assertTrue(run.monsters.all { run.instance.contains(it.coords) })
        }

    @Test
    fun GameTestState.`monsters hunt the nearest player`() = runCaveTest { deps, cast ->
        deps.caves.enter(cast.alice)
        val run = checkNotNull(deps.caves.runOf(cast.alice))
        nextWave(run, 1)

        assertTrue(run.monsters.all { it.mode == NpcMode.OpPlayer2 })
    }

    @Test
    fun GameTestState.`a cleared wave is followed by the next one after a pause`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(run, 1)

            killAll(run)
            assertEquals(0, run.monsters.size)
            assertEquals(1, run.waveNumber)

            nextWave(run, 2)
            assertEquals(setOf(FightCaveMonster.TzKek, FightCaveMonster.TzKih), kinds(run).toSet())
            assertMessageSent("<col=ef1020>Wave: 2")
        }

    @Test
    fun GameTestState.`a Tz-Kek leaves two small ones that must also be killed`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(run, 1)
            killAll(run)
            nextWave(run, 2)

            val kek =
                run.monsters.first {
                    FightCaveMonsterDefs.of(it.type)!!.kind == FightCaveMonster.TzKek
                }
            val kih = run.monsters.first { it !== kek }
            kih.hitpoints = 0
            kih.queueDeath()
            kek.hitpoints = 0
            kek.queueDeath()
            advance(ticks = 2)

            val small = run.monsters.filter { it.type.id == cave_npcs.tz_kek_spawn.id }
            assertEquals(2, small.size)
            assertEquals(2, run.monsters.size)

            // The wave is not over until the small ones are dead.
            advance(ticks = 20)
            assertEquals(2, run.waveNumber)
            killAll(run)
            nextWave(run, 3)
        }

    /** Lands a [type] hit of [damage] on [npc] from [source], then lets it resolve. */
    private fun GameTestScope.hitNpc(npc: Npc, source: Player, type: HitType, damage: Int) {
        npc.queueHit(source, delay = 1, type, damage, NpcHitModifier {})
        advance(ticks = 3)
    }

    @Test
    fun GameTestState.`both Tz-Keks recoil one damage on a melee hit only`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(run, 1)
            killAll(run)
            nextWave(run, 2)
            val big = run.monsters.first { it.type.id == cave_npcs.tz_kek.id }
            run.monsters.forEach { it.actionDelay = Int.MAX_VALUE }
            big.hitpoints = 30
            assertRecoils(cast.alice, big)

            // The two small ones it leaves behind recoil as well.
            big.hitpoints = 0
            big.queueDeath()
            advance(ticks = 2)
            val small = run.monsters.first { it.type.id == cave_npcs.tz_kek_spawn.id }
            small.actionDelay = Int.MAX_VALUE
            small.hitpoints = 30
            assertRecoils(cast.alice, small)
        }

    private fun GameTestScope.assertRecoils(player: Player, kek: Npc) {
        val before = player.hitpoints
        hitNpc(kek, player, HitType.Ranged, 1)
        hitNpc(kek, player, HitType.Magic, 1)
        assertEquals(before, player.hitpoints, "only melee recoils")
        hitNpc(kek, player, HitType.Melee, 0)
        assertEquals(before, player.hitpoints, "a melee miss does not recoil")
        hitNpc(kek, player, HitType.Melee, 2)
        assertEquals(before - 1, player.hitpoints, "a melee hit recoils 1")
    }

    @Test
    fun GameTestState.`a teammate joins the same run`() = runCaveTest { deps, cast ->
        deps.caves.enter(cast.alice)
        val run = checkNotNull(deps.caves.runOf(cast.alice))
        nextWave(run, 1)

        assertEquals(EnterResult.Entered, deps.caves.enter(cast.bob))

        assertTrue(run === deps.caves.runOf(cast.bob))
        assertEquals(1, run.waveNumber)
        assertEquals(1, deps.caves.liveRuns().size)
    }

    @Test
    fun GameTestState.`a lone player gets a run of their own`() = runCaveTest { deps, cast ->
        deps.caves.enter(cast.alice)
        deps.caves.enter(cast.carol)

        assertNotEquals(deps.caves.runOf(cast.alice), deps.caves.runOf(cast.carol))
        assertEquals(2, deps.caves.liveRuns().size)
    }

    @Test
    fun GameTestState.`when everyone leaves the run ends and its monsters are removed`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(run, 1)
            val monsters = run.monsters.toList()

            deps.caves.leave(cast.alice)

            assertTrue(deps.caves.liveRuns().isEmpty())
            assertTrue(monsters.none { it.isSlotAssigned })
            assertTrue(deps.caves.runOf(cast.alice) == null)
        }

    @Test
    fun GameTestState.`entering again after a failed run starts from wave one`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            val first = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(first, 1)
            killAll(first)
            nextWave(first, 2)
            deps.caves.leave(cast.alice)

            deps.caves.enter(cast.alice)
            val second = checkNotNull(deps.caves.runOf(cast.alice))

            assertFalse(first === second)
            assertEquals(0, second.waveNumber)
        }

    @Test
    fun GameTestState.`a player who dies can walk back in and rejoin the run`() =
        runCaveTest { deps, cast ->
            deps.caves.enter(cast.alice)
            deps.caves.enter(cast.bob)
            val run = checkNotNull(deps.caves.runOf(cast.alice))
            nextWave(run, 1)

            // Bob is sent out the way a death respawn does.
            deps.caves.leave(cast.bob)
            assertEquals(1, deps.caves.liveRuns().size)
            deps.caves.enter(cast.bob)

            assertTrue(run === deps.caves.runOf(cast.bob))
        }

    /** Plays the shortened cave up to Jad and returns the run, with Jad alive. */
    private fun GameTestScope.playToJad(deps: Deps, cast: Cast): FightCaveRun {
        deps.caves.enter(cast.alice)
        deps.caves.enter(cast.bob)
        val run = checkNotNull(deps.caves.runOf(cast.alice))
        for (number in 1..8) {
            nextWave(run, number)
            killAll(run)
        }
        nextWave(run, 9)
        return run
    }

    @Test
    fun GameTestState.`the whole shortened cave can be played to a win`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)

            assertEquals(listOf(FightCaveMonster.Jad), kinds(run))
            val jad = checkNotNull(run.jad)
            assertTrue(run.instance.contains(jad.coords))
            assertMessageSent("<col=ef1020>Final Challenge!")

            killAll(run)

            assertEquals(FightCaveState.Won, run.state)
            for (each in listOf(cast.alice, cast.bob)) {
                assertTrue(cave_objs.fire_cape in each.inv, "${each.username} has no fire cape")
                assertEquals(8032, each.inv.count(cave_objs.tokkul))
            }

            // Everyone is sent out a moment later and the run ends.
            advance(ticks = FightCaveRun.LEAVE_DELAY + 3)
            assertTrue(deps.instances.instanceOf(cast.alice) == null)
            assertTrue(deps.caves.liveRuns().isEmpty())
        }

    @Test
    fun GameTestState.`a player outside the cave gets nothing when Jad dies`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            deps.caves.leave(cast.bob)

            killAll(run)

            assertTrue(cave_objs.fire_cape in cast.alice.inv)
            assertFalse(cave_objs.fire_cape in cast.bob.inv)
        }

    @Test
    fun GameTestState.`four healers appear once Jad is hurt and heal him`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            val jad = checkNotNull(run.jad)
            assertEquals(0, run.healers.size)

            jad.hitpoints = FightCaves.JAD_HEALER_THRESHOLD - 50
            advance(ticks = 2)

            assertEquals(4, run.healers.size)
            val hurt = jad.hitpoints
            // Healers wait next to Jad and top him up, 5 at a time.
            advance(ticks = 12)
            assertTrue(jad.hitpoints > hurt, "Jad was not healed: $hurt -> ${jad.hitpoints}")

            // They only ever come once.
            run.healers.toList().forEach {
                it.hitpoints = 0
                it.queueDeath()
            }
            advance(ticks = 5)
            jad.hitpoints = 20
            advance(ticks = 3)
            assertEquals(0, run.healers.size)
        }

    @Test
    fun GameTestState.`Jad hits every player in the cave and the right prayer blocks it`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            val jad = checkNotNull(run.jad)
            jad.actionDelay = Int.MAX_VALUE // only the attack the test makes
            repeat(12) { random.next = 3 }
            cast.alice.setVarBit(varbits.protect_from_magic, 1)
            assertEquals(1, cast.alice.vars[varbits.protect_from_magic], "prayer varbit not set")
            val before = mapOf(cast.alice to cast.alice.hitpoints, cast.bob to cast.bob.hitpoints)

            val sure = FightCaveAttacks.jadMagic.copy(alwaysHits = true, maxHit = 5)
            val hit = deps.attacks.perform(jad, cast.alice, sure)
            advance(ticks = 20)

            assertEquals(setOf(cast.alice, cast.bob), hit.toSet())
            assertEquals(0, before.getValue(cast.alice) - cast.alice.hitpoints, "prayer failed")
            assertTrue(before.getValue(cast.bob) - cast.bob.hitpoints > 0, "bob was not hit")
        }

    /** Plays whatever waves the wave file has before Jad, so the Jad tasks work with any file. */
    private fun GameTestScope.reachJad(deps: Deps, cast: Cast): FightCaveRun {
        deps.caves.enter(cast.alice)
        deps.caves.enter(cast.bob)
        val run = checkNotNull(deps.caves.runOf(cast.alice))
        while (run.jad == null) {
            nextWave(run, run.waveNumber + 1)
            if (run.jad == null) {
                killAll(run)
            }
        }
        return run
    }

    private fun GameTestScope.jadHits(deps: Deps, cast: Cast, attack: NpcAttack) {
        val run = reachJad(deps, cast)
        val jad = checkNotNull(run.jad)
        jad.actionDelay = Int.MAX_VALUE // only the attack the test makes
        repeat(12) { random.next = 3 }
        cast.alice.coords = jad.coords.translate(5, 0)
        cast.bob.coords = jad.coords.translate(6, 0)
        val sure = attack.copy(alwaysHits = true, maxHit = 5)
        val listener = NpcImpactListener { target, hit -> deps.completions.jadHit(target, hit) }
        deps.attacks.perform(jad, cast.alice, sure, listener)
        advance(ticks = 20)
    }

    private fun GameTestScope.jadTaskDone(player: Player) =
        LeagueTaskTracker(eventBus, LeagueTeams(players))
            .isComplete(player, FightCaveCompletions.TASK_JAD_WITHOUT_PRAYER)

    @Test
    fun GameTestState.`surviving a Jad magic or ranged hit with no prayer completes the task`() =
        runCaveTest { deps, cast ->
            jadHits(deps, cast, FightCaveAttacks.jadMagic)
            assertTrue(jadTaskDone(cast.alice))
        }

    @Test
    fun GameTestState.`a Jad ranged hit with no prayer completes the task`() =
        runCaveTest { deps, cast ->
            jadHits(deps, cast, FightCaveAttacks.jadRanged)
            assertTrue(jadTaskDone(cast.alice))
        }

    @Test
    fun GameTestState.`the right protection prayer means the Jad task is not completed`() =
        runCaveTest { deps, cast ->
            // Alice and Bob share a team, and Jad hits both, so both need the prayer.
            cast.alice.setVarBit(varbits.protect_from_magic, 1)
            cast.bob.setVarBit(varbits.protect_from_magic, 1)
            jadHits(deps, cast, FightCaveAttacks.jadMagic)
            assertFalse(jadTaskDone(cast.alice))
        }

    @Test
    fun GameTestState.`the wrong protection prayer still completes the Jad task`() =
        runCaveTest { deps, cast ->
            cast.alice.setVarBit(varbits.protect_from_missiles, 1)
            jadHits(deps, cast, FightCaveAttacks.jadMagic)
            assertTrue(jadTaskDone(cast.alice))
        }

    @Test
    fun GameTestState.`a Jad hit that kills the player does not complete the task`() =
        runCaveTest { deps, cast ->
            val run = reachJad(deps, cast)
            val jad = checkNotNull(run.jad)
            jad.actionDelay = Int.MAX_VALUE
            repeat(12) { random.next = 3 }
            cast.alice.coords = jad.coords.translate(5, 0)
            cast.bob.coords = jad.coords.translate(6, 0)
            cast.alice.stats.setCurrentLevel(stats.hitpoints, 3)
            cast.bob.stats.setCurrentLevel(stats.hitpoints, 3)
            val lethal = FightCaveAttacks.jadMagic.copy(alwaysHits = true, maxHit = 50)
            val listener = NpcImpactListener { target, hit -> deps.completions.jadHit(target, hit) }
            deps.attacks.perform(jad, cast.alice, lethal, listener)
            advance(ticks = 20)
            assertFalse(jadTaskDone(cast.alice))
        }

    @Test
    fun GameTestState.`a player far from Jad is still hit by his magic attack`() =
        runCaveTest { deps, cast ->
            val run = reachJad(deps, cast)
            val jad = checkNotNull(run.jad)
            jad.actionDelay = Int.MAX_VALUE // only the attack the test makes
            repeat(12) { random.next = 3 }
            cast.alice.coords = jad.coords.translate(45, 0)
            cast.bob.coords = jad.coords.translate(46, 0)
            val before = cast.alice.hitpoints

            val sure = FightCaveAttacks.jadMagic.copy(alwaysHits = true, maxHit = 5)
            deps.attacks.perform(jad, cast.alice, sure)
            advance(ticks = 30)

            assertTrue(before - cast.alice.hitpoints > 0, "alice was not hit from afar")
        }

    @Test
    fun GameTestState.`the wrong prayer does not block Jad`() = runCaveTest { deps, cast ->
        val run = playToJad(deps, cast)
        val jad = checkNotNull(run.jad)
        jad.actionDelay = Int.MAX_VALUE // only the attack the test makes
        repeat(12) { random.next = 3 }
        cast.alice.setVarBit(varbits.protect_from_missiles, 1)
        val before = cast.alice.hitpoints

        deps.attacks.perform(
            jad,
            cast.alice,
            FightCaveAttacks.jadMagic.copy(alwaysHits = true, maxHit = 5),
        )
        advance(ticks = 20)

        assertTrue(before - cast.alice.hitpoints > 0)
    }

    @Test
    fun GameTestState.`jad's rock attack lands a few ticks after it starts`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            val jad = checkNotNull(run.jad)
            jad.actionDelay = Int.MAX_VALUE // only the attack the test makes
            repeat(12) { random.next = 3 }
            val before = cast.alice.hitpoints

            deps.attacks.perform(
                jad,
                cast.alice,
                FightCaveAttacks.jadRanged.copy(alwaysHits = true, maxHit = 5),
            )
            advance(ticks = FightCaveAttacks.JAD_HIT_DELAY - 2)
            assertEquals(before, cast.alice.hitpoints)

            advance(ticks = 4)
            assertTrue(cast.alice.hitpoints < before)
        }

    @Test
    fun GameTestState
        .`jad's magic and ranged hits land the same few ticks after at normal distances`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            val jad = checkNotNull(run.jad)
            jad.actionDelay = Int.MAX_VALUE // only the attacks the test makes
            repeat(60) { random.next = 3 }
            val attacks =
                mapOf("magic" to FightCaveAttacks.jadMagic, "ranged" to FightCaveAttacks.jadRanged)
            for ((name, attack) in attacks) {
                for (distance in listOf(2, 4, 7)) {
                    cast.alice.coords = jad.coords.translate(distance + 5, 0)
                    advance(ticks = 2)
                    val before = cast.alice.hitpoints
                    deps.attacks.attack(jad, cast.alice, attack.copy(alwaysHits = true, maxHit = 5))
                    var ticks = 0
                    while (cast.alice.hitpoints == before && ticks < 15) {
                        advance(ticks = 1)
                        ticks++
                    }
                    assertEquals(
                        FightCaveAttacks.JAD_HIT_DELAY,
                        ticks,
                        "$name from $distance tiles away",
                    )
                }
            }
        }

    @Test
    fun GameTestState.`jad fights players on its own once the wave starts`() =
        runCaveTest { deps, cast ->
            val run = playToJad(deps, cast)
            val jad = checkNotNull(run.jad)
            advanceUntil(
                predicate = { cast.alice.hitpoints < 99 || cast.bob.hitpoints < 99 },
                timeoutTicks = 150,
                timeoutMessage = { "Jad never attacked: ${jad.coords} ${jad.mode}" },
            )

            assertEquals(NpcMode.ApPlayer2, jad.mode)
        }

    @Test
    fun GameTestState.`the entrance and gatekeeper are placed on the plaza`() =
        runCaveTest { deps, _ ->
            deps.script.placeEntrance()

            assertNotNull(findLoc(FightCaveScript.ENTRANCE, cave_locs.entrance))
            val npcsThere = npcRepo.findAll(FightCaveScript.GATEKEEPER).toList()
            assertTrue(
                npcsThere.any { it.type.id == cave_npcs.gatekeeper.id },
                "the gatekeeper is not at ${FightCaveScript.GATEKEEPER}",
            )
        }

    @Test
    fun GameTestState.`using the entrance puts the player in a cave`() = runCaveTest { deps, cast ->
        deps.script.placeEntrance()
        val entrance = checkNotNull(findLoc(FightCaveScript.ENTRANCE, cave_locs.entrance))
        cast.alice.placeAt(FightCaveScript.ENTRANCE.translate(1, -1))

        cast.alice.opLoc1(BoundLocInfo(entrance, locTypes[cave_locs.entrance]))
        advance(ticks = 3)

        val run = checkNotNull(deps.caves.runOf(cast.alice))
        assertTrue(run.instance.contains(cast.alice.coords))
        assertEquals("Enter", locTypes[cave_locs.entrance].op[0])
    }

    @Test
    fun GameTestState.`the cave exit loc is usable and leaving ends an empty run`() =
        runCaveTest { deps, cast ->
            // The exit is a loc of the map that the cave copies; its first option is "Leave".
            assertEquals("Leave", locTypes[cave_locs.exit].op[0])
            deps.caves.enter(cast.alice)

            assertTrue(deps.caves.leave(cast.alice))

            assertEquals(FightCaveArena.exit, cast.alice.coords)
            assertTrue(deps.caves.liveRuns().isEmpty())
        }
}
