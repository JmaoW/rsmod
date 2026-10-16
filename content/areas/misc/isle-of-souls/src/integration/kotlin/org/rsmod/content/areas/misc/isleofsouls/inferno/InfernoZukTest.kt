package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

class InfernoZukTest {
    private object InfernoModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Inferno::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val inferno: Inferno,
        val zuk: InfernoZuk,
        val locRepo: LocRepository,
        val locZones: LocZoneStorage,
        val attacks: NpcAttacks,
        val collision: CollisionFlagMap,
    )

    private class Fight(
        val deps: Deps,
        val alice: Player,
        val bob: Player,
        val run: InfernoRun,
        val fight: ZukFight,
    )

    /** Enters alice and bob and waits for the Zuk fight (the only wave) to begin. */
    private fun GameTestState.runZukTest(body: GameTestScope.(Fight) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            InfernoModule,
            InfernoScript::class,
            InfernoCombatScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            alice.stats[stats.hitpoints] = 99
            bob.stats[stats.hitpoints] = 99
            deps.inferno.waveLoader = { InfernoWaves.parse(ZUK_ONLY, "test waves") }
            seedPrison(deps)
            deps.inferno.enter(alice)
            deps.inferno.enter(bob)
            val run = checkNotNull(deps.inferno.runOf(alice))
            advanceUntil(
                predicate = { run.zukFight != null },
                timeoutTicks = 40,
                timeoutMessage = { "the Zuk fight never began" },
            )
            body(Fight(deps, alice, bob, run, checkNotNull(run.zukFight)))
        }

    /** The test world has no map, so the prison pieces the real map holds are put in by hand. */
    private fun GameTestScope.seedPrison(deps: Deps) {
        val pieces =
            mapOf(
                InfernoArena.prisonGlyph to InfernoPrisonLocs.glyph_closed,
                InfernoArena.prisonSideLeft to InfernoPrisonLocs.side_left_closed,
                InfernoArena.prisonSideRight to InfernoPrisonLocs.side_right_closed,
                InfernoArena.prisonCornerLeft to InfernoPrisonLocs.corner_left_closed,
                InfernoArena.prisonCornerRight to InfernoPrisonLocs.corner_right_closed,
                InfernoArena.prisonJoinRight to InfernoPrisonLocs.join_right_closed,
                InfernoArena.prisonJoinLeft to InfernoPrisonLocs.join_left_static,
            )
        val layer = LocLayerConstants.of(10)
        for ((at, type) in pieces) {
            val key = LocZoneKey(ZoneGrid.from(at), layer)
            deps.locZones.mapLocs[ZoneKey.from(at), key] = LocEntity(locTypes[type].id, 10, 3)
        }
    }

    /** Stops Zuk firing and the sets arriving, so a test can look at one thing. */
    private fun GameTestScope.hush(f: Fight) {
        f.fight.nextAttackAt = Int.MAX_VALUE
        f.fight.nextSetAt = mapClock.cycle + 100_000
    }

    private fun GameTestScope.minions(f: Fight): List<Npc> = f.run.monsters.toList()

    private fun GameTestScope.stand(f: Fight, player: Player, dx: Int, rows: Int = 0) {
        val glyph = f.fight.glyph
        player.telejump(CoordGrid(glyph.coords.x + 1 + dx, f.fight.coverTopZ - rows, 0))
    }

    @Test
    fun GameTestState.`Zuk breaks out with a full glyph and the players are put in front of him`() =
        runZukTest { f ->
            assertEquals(inferno_npcs.zuk.id, f.fight.zuk.type.id)
            assertEquals(1200, f.fight.zuk.hitpoints)
            assertEquals(7, f.fight.zuk.size)
            assertEquals(600, f.fight.glyph.hitpoints)
            assertTrue(f.run.instance.contains(f.fight.zuk.coords))
            for (player in listOf(f.alice, f.bob)) {
                assertTrue(player.coords.z in f.fight.coverTopZ - 2..f.fight.coverTopZ + 1)
            }
            assertEquals(WaveState.InWave, f.run.state)
        }

    @Test
    fun GameTestState.`the glyph walks a tile a tick and rests at the ends`() = runZukTest { f ->
        hush(f)
        val glyph = f.fight.glyph
        glyph.telejump(f.deps.collision, CoordGrid(f.fight.shieldMaxX - 6, glyph.coords.z, 0))
        f.fight.shieldDirection = 1
        f.fight.shieldMoveAt = mapClock.cycle

        val xs = ArrayList<Int>()
        repeat(40) {
            advance(ticks = 1)
            xs += glyph.coords.x
        }

        assertEquals(f.fight.shieldMaxX, xs.max(), "it walks to the end and no further")
        val steps = xs.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it in -1..1 }, "never more than a tile a tick: $steps")
        val atEnd = xs.count { it == f.fight.shieldMaxX }
        assertTrue(atEnd >= InfernoZuk.SHIELD_PAUSE, "it rests at the end: $atEnd ticks")
        assertTrue(xs.last() < f.fight.shieldMaxX, "and then walks back: $xs")
    }

    @Test
    fun GameTestState
        .`a player is covered within two tiles of the glyph in the north three rows`() =
        runZukTest { f ->
            val zuk = f.deps.zuk
            stand(f, f.alice, dx = 0)
            assertTrue(zuk.isCovered(f.fight, f.alice))
            stand(f, f.alice, dx = -2, rows = 2)
            assertTrue(zuk.isCovered(f.fight, f.alice), "the far corner")
            stand(f, f.alice, dx = 3)
            assertFalse(zuk.isCovered(f.fight, f.alice), "too far to the side")
            stand(f, f.alice, dx = 0, rows = 3)
            assertFalse(zuk.isCovered(f.fight, f.alice), "too far from the wall")
        }

    @Test
    fun GameTestState.`Zuk hits a player in the open and not one behind the glyph`() =
        runZukTest { f ->
            hush(f)
            repeat(12) { random.next = 3 }
            stand(f, f.alice, dx = 0)
            stand(f, f.bob, dx = 6)
            f.fight.glyph.telejump(
                f.deps.collision,
                CoordGrid(f.fight.glyph.coords.x, f.fight.glyph.coords.z, 0),
            )
            f.fight.shieldMoveAt = Int.MAX_VALUE
            f.fight.nextAttackAt = mapClock.cycle

            advance(ticks = 8)

            assertEquals(99, f.alice.hitpoints, "alice was behind the glyph")
            assertTrue(f.bob.hitpoints < 99, "bob was in the open")
        }

    @Test
    fun GameTestState.`Zuk fires every 10 ticks and every 7 once enraged`() = runZukTest { f ->
        f.fight.nextSetAt = Int.MAX_VALUE
        f.fight.shieldMoveAt = Int.MAX_VALUE
        f.fight.nextAttackAt = mapClock.cycle
        advance(ticks = 1)
        assertEquals(mapClock.cycle - 1 + 10, f.fight.nextAttackAt)

        f.fight.zuk.hitpoints = 200
        advance(ticks = 1)
        f.fight.nextAttackAt = mapClock.cycle
        advance(ticks = 1)
        assertTrue(f.fight.enraged)
        assertEquals(mapClock.cycle - 1 + 7, f.fight.nextAttackAt)
    }

    @Test
    fun GameTestState.`a monster hits the glyph with its normal accuracy and damage`() =
        runZukTest { f ->
            val ranger =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.rangerFinal,
                    InfernoArena.zukRanger,
                    true,
                )
            val mager =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.magerFinal,
                    InfernoArena.zukMager,
                    true,
                )
            val jad =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.jadFinal,
                    InfernoArena.zukJad,
                    true,
                )
            val rolls = mutableMapOf<String, MutableList<Int>>()
            repeat(300) {
                rolls.getOrPut("ranger") { mutableListOf() } +=
                    f.deps.attacks.rollDamage(ranger, f.fight.glyph, InfernoAttacks.rangerRanged)
                rolls.getOrPut("mager") { mutableListOf() } +=
                    f.deps.attacks.rollDamage(mager, f.fight.glyph, InfernoAttacks.magerMagic)
                rolls.getOrPut("jad") { mutableListOf() } +=
                    f.deps.attacks.rollDamage(jad, f.fight.glyph, InfernoAttacks.jadMagic)
            }
            assertTrue(rolls.getValue("ranger").all { it in 0..46 })
            assertTrue(rolls.getValue("mager").all { it in 0..70 })
            assertTrue(rolls.getValue("jad").all { it in 0..113 })
            for ((name, list) in rolls) {
                assertTrue(list.any { it > 0 }, "$name never hurt the glyph")
            }
            assertTrue(rolls.getValue("jad").max() > rolls.getValue("ranger").max())
        }

    @Test
    fun GameTestState.`a set that is left alone wears the glyph down and breaks it`() =
        runZukTest { f ->
            hush(f)
            f.fight.shieldMoveAt = Int.MAX_VALUE
            val ranger =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.rangerFinal,
                    InfernoArena.zukRanger,
                    true,
                )
            f.deps.inferno.spawnMinion(
                f.run,
                InfernoMonsterDefs.magerFinal,
                InfernoArena.zukMager,
                true,
            )
            assertEquals(NpcMode.None, ranger.mode)

            advance(ticks = 8)
            assertTrue(f.fight.glyph.hitpoints < 600, "the glyph was hit")
            assertFalse(f.fight.shieldGone)
            assertTrue(ranger.isSlotAssigned)

            val began = mapClock.cycle
            advanceUntil(
                predicate = { f.fight.shieldGone },
                timeoutTicks = 400,
                timeoutMessage = { "the glyph never broke: ${f.fight.glyph.hitpoints}" },
            )
            // With their normal damage a Jal-Xil and a Jal-Zek break it in well under a minute.
            assertTrue(mapClock.cycle - began < 100, "took ${mapClock.cycle - began} ticks")
            advance(ticks = 3)
            assertFalse(f.deps.zuk.isCovered(f.fight, f.alice), "nobody is covered any more")
            assertFalse(f.fight.glyph.isSlotAssigned, "the glyph is gone")
            assertTrue(minions(f).none { f.run.states.getValue(it).shieldAttacker })
            assertTrue(minions(f).all { it.mode == NpcMode.ApPlayer2 }, "they turn on the players")
        }

    @Test
    fun GameTestState.`a monster that is hit leaves the glyph alone and goes for the players`() =
        runZukTest { f ->
            hush(f)
            val ranger =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.rangerFinal,
                    InfernoArena.zukRanger,
                    true,
                )
            val mager =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.magerFinal,
                    InfernoArena.zukMager,
                    true,
                )
            ranger.hitpoints -= 20

            advance(ticks = 3)

            assertFalse(f.run.states.getValue(ranger).shieldAttacker)
            assertEquals(NpcMode.ApPlayer2, ranger.mode)
            assertTrue(
                f.run.states.getValue(mager).shieldAttacker,
                "the other one still attacks the glyph",
            )
            assertEquals(NpcMode.None, mager.mode)
        }

    @Test
    fun GameTestState.`the ranger arrives on the east side and the mager on the west`() =
        runZukTest { f ->
            f.fight.nextAttackAt = Int.MAX_VALUE
            f.fight.shieldMoveAt = Int.MAX_VALUE
            f.fight.nextSetAt = mapClock.cycle + 2

            advance(ticks = 3)

            val ranger = minions(f).single { it.type.id == inferno_npcs.ranger_final.id }
            val mager = minions(f).single { it.type.id == inferno_npcs.mager_final.id }
            assertTrue(
                ranger.coords.x > mager.coords.x,
                "ranger ${ranger.coords} mager ${mager.coords}",
            )
        }

    @Test
    fun GameTestState.`Zuk's set can shoot from anywhere in the arena and so never walks`() =
        runZukTest { f ->
            hush(f)
            f.fight.shieldMoveAt = Int.MAX_VALUE
            val ranger =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.rangerFinal,
                    InfernoArena.zukRanger,
                    true,
                )
            val mager =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.magerFinal,
                    InfernoArena.zukMager,
                    true,
                )
            val jad =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.jadFinal,
                    InfernoArena.zukJad,
                    true,
                )
            for (npc in listOf(ranger, mager, jad)) {
                assertTrue(npc.visType.attackRange >= 30, "range ${npc.visType.attackRange}")
            }
            val before = listOf(ranger, mager, jad).map { it.coords }
            // Hit, so that they turn on the players instead of the glyph.
            listOf(ranger, mager, jad).forEach { it.hitpoints -= 10 }
            // The players are as far from them as the arena allows.
            f.alice.telejump(f.run.instance.region.normal[InfernoArena.start])

            advance(ticks = 25)

            assertEquals(before, listOf(ranger, mager, jad).map { it.coords }, "none of them moved")
        }

    @Test
    fun GameTestState.`the Jal-Zek of Zuk's set never revives anything`() = runZukTest { f ->
        hush(f)
        f.fight.shieldMoveAt = Int.MAX_VALUE
        val mager =
            f.deps.inferno.spawnMinion(
                f.run,
                InfernoMonsterDefs.magerFinal,
                InfernoArena.zukMager,
                true,
            )
        f.run.slain += SlainMonster(InfernoMonsterDefs.rangerFinal)
        mager.hitpoints -= 10
        mager.queueCombatRetaliate(f.alice)
        mager.actionDelay = -1
        // Every roll comes up 0: an ordinary Jal-Zek would revive on its first.
        repeat(40) { random.next = 0 }

        advance(ticks = 12)

        assertEquals(listOf(mager), f.run.monsters.toList(), "nothing was brought back")
        assertFalse(f.run.slain.single().revived)
    }

    @Test
    fun GameTestState.`Zuk does not turn or walk to a player who hits him`() = runZukTest { f ->
        hush(f)
        val zuk = f.fight.zuk
        val before = zuk.coords
        zuk.hitpoints -= 40
        zuk.queueCombatRetaliate(f.alice)

        advance(ticks = 6)

        assertEquals(NpcMode.None, zuk.mode, "no chase and no facing the player")
        assertEquals(before, zuk.coords)
        for (healer in f.fight.healers) {
            assertEquals(NpcMode.None, healer.mode)
        }
    }

    @Test
    fun GameTestState.`Zuk's prison opens when he breaks out`() = runZukTest { f ->
        val locRepo = f.deps.locRepo
        val region = f.run.instance.region.normal
        fun has(at: CoordGrid, type: LocType) = locRepo.findExact(region[at], type) != null

        assertFalse(
            has(InfernoArena.prisonGlyph, locTypes[InfernoPrisonLocs.glyph_closed]),
            "no static glyph",
        )
        assertFalse(
            has(InfernoArena.prisonSideLeft, locTypes[InfernoPrisonLocs.side_left_closed]),
            "check 1",
        )
        assertFalse(
            has(InfernoArena.prisonSideRight, locTypes[InfernoPrisonLocs.side_right_closed])
        )
        assertTrue(
            has(InfernoArena.prisonSideLeft, locTypes[InfernoPrisonLocs.side_left_cracking]),
            "check 2",
        )
        assertTrue(
            has(InfernoArena.prisonSideRight, locTypes[InfernoPrisonLocs.side_right_cracking])
        )
        assertTrue(
            has(InfernoArena.prisonCornerLeft, locTypes[InfernoPrisonLocs.corner_left_open]),
            "check 3",
        )
        assertTrue(
            has(InfernoArena.prisonCornerRight, locTypes[InfernoPrisonLocs.corner_right_open])
        )
        assertTrue(
            has(InfernoArena.prisonJoinRight, locTypes[InfernoPrisonLocs.join_right_open]),
            "check 4",
        )
        assertTrue(
            has(InfernoArena.prisonJoinLeft, locTypes[InfernoPrisonLocs.join_left_open]),
            "the west wall is filled in like the east one",
        )
        val join =
            locRepo.findExact(
                f.run.instance.region.normal[InfernoArena.prisonJoinLeft],
                locTypes[InfernoPrisonLocs.join_left_open],
            )
        assertEquals(
            (3 + InfernoPrison.LEFT_JOIN_TURN) and 3,
            checkNotNull(join).angleId,
            "turned a half turn from the map's piece",
        )
        assertEquals(Direction.South, f.fight.zuk.respawnDir)
        assertFalse(
            has(InfernoArena.prisonJoinLeft, locTypes[InfernoPrisonLocs.join_left_static]),
            "and the plain wall piece that stood there is gone",
        )
        assertEquals(1, f.alice.vars[InfernoVarBits.prison_roof_hidden], "the roof is off")
        assertEquals(1, f.bob.vars[InfernoVarBits.prison_roof_hidden])

        advance(ticks = 40)

        assertTrue(
            has(InfernoArena.prisonSideLeft, locTypes[InfernoPrisonLocs.side_left_rubble]),
            "check 5",
        )
        assertTrue(
            has(InfernoArena.prisonSideRight, locTypes[InfernoPrisonLocs.side_right_rubble]),
            "check 6",
        )
        assertFalse(
            has(InfernoArena.prisonSideLeft, locTypes[InfernoPrisonLocs.side_left_cracking])
        )
        assertTrue(f.fight.crumbles.isEmpty())
    }

    @Test
    fun GameTestState.`the first set waits for the glyph to touch its first end a second time`() =
        runZukTest { f ->
            f.fight.nextAttackAt = Int.MAX_VALUE
            val glyph = f.fight.glyph
            val firstEnd = f.fight.shieldMaxX
            assertEquals(1, f.fight.shieldDirection, "it sets off east")

            val ends = ArrayList<Int>()
            var last = Int.MIN_VALUE
            advanceUntil(
                predicate = {
                    if (f.fight.shieldEnds != last) {
                        last = f.fight.shieldEnds
                        ends += glyph.coords.x
                    }
                    f.run.monsters.isNotEmpty()
                },
                timeoutTicks = 300,
                timeoutMessage = { "the first set never came, glyph ends so far $ends" },
            )

            assertEquals(3, f.fight.shieldEnds)
            assertEquals(
                listOf(firstEnd, f.fight.shieldMinX, firstEnd),
                ends.drop(1).takeLast(3).ifEmpty { ends },
            )
            assertFalse(f.fight.firstSetPending)
            assertEquals(
                setOf(inferno_npcs.ranger_final.id, inferno_npcs.mager_final.id),
                minions(f).map { it.type.id }.toSet(),
            )
        }

    @Test
    fun GameTestState.`a set that is hit shoots back from where it stands instead of walking`() =
        runZukTest { f ->
            hush(f)
            f.fight.shieldMoveAt = Int.MAX_VALUE
            val ranger =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.rangerFinal,
                    InfernoArena.zukRanger,
                    true,
                )
            val jad =
                f.deps.inferno.spawnMinion(
                    f.run,
                    InfernoMonsterDefs.jadFinal,
                    InfernoArena.zukJad,
                    true,
                )
            f.alice.telejump(f.run.instance.region.normal[InfernoArena.start])
            val before = listOf(ranger, jad).map { it.coords }

            for (npc in listOf(ranger, jad)) {
                npc.hitpoints -= 10
                npc.queueCombatRetaliate(f.alice)
            }
            advance(ticks = 20)

            for (npc in listOf(ranger, jad)) {
                assertEquals(
                    NpcMode.ApPlayer2,
                    npc.mode,
                    "${npc.type.internalName} shoots, not melees",
                )
            }
            assertEquals(before, listOf(ranger, jad).map { it.coords }, "neither walked")
        }

    @Test
    fun GameTestState.`Zuk's healers stand on the lava in front of him, two to each side`() =
        runZukTest { f ->
            hush(f)
            f.fight.zuk.hitpoints = 230
            advance(ticks = 1)

            val middle = f.fight.zuk.coords.x + 3
            val offsets = f.fight.healers.map { it.coords.x - middle }.toSet()
            assertEquals(setOf(-8, -5, 5, 8), offsets)
            val row = f.run.instance.region.normal[InfernoArena.mejjakSpots.first()].z
            assertTrue(f.fight.healers.all { it.coords.z == row })
        }

    @Test
    fun GameTestState.`Zuk's Jad still gets his healers when there is no room on his north side`() =
        runZukTest { f ->
            hush(f)
            f.fight.zuk.hitpoints = 470
            advance(ticks = 1)
            val jad = minions(f).single()
            jad.actionDelay = Int.MAX_VALUE
            for (x in jad.coords.x - 1..jad.coords.x + jad.size) {
                f.deps.collision.add(x, jad.coords.z + jad.size, 0, CollisionFlag.BLOCK_WALK)
            }

            jad.hitpoints = 100
            advance(ticks = 2)

            assertEquals(3, f.run.healers.size)
        }

    @Test
    fun GameTestState.`the first set arrives, then one every 3 minutes 30`() = runZukTest { f ->
        f.fight.nextAttackAt = Int.MAX_VALUE
        f.fight.shieldMoveAt = Int.MAX_VALUE
        f.fight.nextSetAt = mapClock.cycle + 2
        assertEquals(0, f.run.monsters.size)

        advance(ticks = 3)

        assertEquals(
            setOf(inferno_npcs.ranger_final.id, inferno_npcs.mager_final.id),
            minions(f).map { it.type.id }.toSet(),
        )
        assertTrue(minions(f).all { f.run.states.getValue(it).shieldAttacker })
        assertEquals(mapClock.cycle - 1 + 350, f.fight.nextSetAt)
    }

    @Test
    fun GameTestState
        .`at 600 the timer stops with an extra 1 minute 45 and at 480 Jad restarts it`() =
        runZukTest { f ->
            f.fight.nextAttackAt = Int.MAX_VALUE
            f.fight.shieldMoveAt = Int.MAX_VALUE
            f.fight.firstSetPending = false
            f.fight.nextSetAt = mapClock.cycle + 100

            f.fight.zuk.hitpoints = 590
            advance(ticks = 1)
            assertTrue(f.fight.timerPaused)
            assertTrue(f.fight.pausedRemaining in 274..275, "${f.fight.pausedRemaining}")

            // While it is stopped no set arrives, however long.
            advance(ticks = 150)
            assertEquals(0, f.run.monsters.size)
            assertFalse(f.fight.jadSpawned)

            f.fight.zuk.hitpoints = 470
            advance(ticks = 1)
            assertTrue(f.fight.jadSpawned)
            assertFalse(f.fight.timerPaused)
            assertTrue(f.fight.nextSetAt - mapClock.cycle in 273..275, "${f.fight.nextSetAt}")
            val jad = minions(f).single()
            assertEquals(inferno_npcs.jad_final.id, jad.type.id)
            assertTrue(f.run.states.getValue(jad).shieldAttacker, "his Jad starts on the glyph")
        }

    @Test
    fun GameTestState.`Zuk's Jad gets his three healers on his north side`() = runZukTest { f ->
        hush(f)
        f.fight.zuk.hitpoints = 470
        advance(ticks = 1)
        val jad = minions(f).single()
        jad.actionDelay = Int.MAX_VALUE

        jad.hitpoints = 100
        advance(ticks = 2)

        assertEquals(3, f.run.healers.size)
        assertTrue(f.run.healers.all { it.type.id == inferno_npcs.jad_healer_final.id })
        assertTrue(f.run.healers.all { it.coords.z >= jad.coords.z + jad.size }, "north of him")
    }

    @Test
    fun GameTestState.`below 240 four healers heal Zuk until they are hit`() = runZukTest { f ->
        hush(f)
        f.fight.zuk.hitpoints = 230
        advance(ticks = 1)
        assertTrue(f.fight.enraged)
        assertEquals(4, f.fight.healers.size)
        assertTrue(f.fight.healers.all { it.type.id == inferno_npcs.zuk_healer.id })

        val before = f.fight.zuk.hitpoints
        advance(ticks = 4)
        val gained = f.fight.zuk.hitpoints - before
        assertTrue(gained >= 4 * 15, "four healers heal at least 60: $gained")

        // Hit three of them: only the fourth still heals.
        f.fight.healers.drop(1).forEach { it.hitpoints -= 30 }
        advance(ticks = 1)
        f.fight.zuk.hitpoints = 100
        f.fight.healers.forEach { it.actionDelay = -1 }
        f.fight.healers.drop(1).forEach { it.actionDelay = Int.MAX_VALUE }
        advance(ticks = 3)
        val gainedNow = f.fight.zuk.hitpoints - 100
        assertTrue(gainedNow in 15..24, "one healer heals 15 to 24: $gainedNow")
    }

    @Test
    fun GameTestState
        .`a healer that is hit turns round and throws lava on the ground south of it`() =
        runZukTest { f ->
            hush(f)
            f.fight.zuk.hitpoints = 230
            advance(ticks = 1)
            // Only one healer is hit, so the lava comes from one place.
            val hit = f.fight.healers.first()
            hit.hitpoints -= 30
            f.fight.healers.drop(1).forEach { it.actionDelay = Int.MAX_VALUE }
            hit.actionDelay = -1
            // The player is nowhere near it: the lava is not aimed at them.
            f.alice.telejump(f.run.instance.region.normal[InfernoArena.start])
            val before = f.alice.hitpoints

            advance(ticks = 1)

            val strike = f.fight.lava.single()
            for (tile in strike.tiles) {
                assertTrue(
                    tile.x in
                        hit.coords.x - InfernoZuk.LAVA_SPREAD..hit.coords.x +
                                InfernoZuk.LAVA_SPREAD,
                    "$tile beside the healer",
                )
                assertTrue(
                    tile.z in
                        hit.coords.z - InfernoZuk.LAVA_FAR..hit.coords.z - InfernoZuk.LAVA_NEAR,
                    "$tile south of the healer",
                )
            }

            // Someone standing where a ball lands is hurt; the player elsewhere is not.
            val landing = strike.tiles.first()
            f.bob.telejump(landing)
            val bobBefore = f.bob.hitpoints
            advance(ticks = 6)

            assertEquals(before, f.alice.hitpoints, "the player was not targeted")
            val lost = bobBefore - f.bob.hitpoints
            assertTrue(
                lost in InfernoZuk.LAVA_MIN..InfernoZuk.LAVA_MIN + 5,
                "lava does 5 to 10: $lost",
            )
        }

    @Test
    fun GameTestState.`killing Zuk wins the run and takes his minions with him`() =
        runZukTest { f ->
            hush(f)
            f.deps.inferno.spawnMinion(
                f.run,
                InfernoMonsterDefs.rangerFinal,
                InfernoArena.zukRanger,
                true,
            )
            f.fight.zuk.hitpoints = 230
            advance(ticks = 1)
            assertEquals(4, f.fight.healers.size)

            f.fight.zuk.hitpoints = 0
            f.fight.zuk.queueDeath()
            advance(ticks = 3)

            assertEquals(WaveState.Won, f.run.state)
            assertEquals(0, f.run.monsters.size)
            assertTrue(f.fight.healers.none { it.isSlotAssigned })
            assertFalse(f.fight.glyph.isSlotAssigned)
            advance(ticks = WaveRunLeave + 3)
            assertTrue(f.deps.inferno.liveRuns().isEmpty())
        }

    private companion object {
        const val WaveRunLeave = org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun.LEAVE_DELAY

        val ZUK_ONLY =
            """
            [[wave]]
            number = 1
            monsters = ["zuk"]
            boss = true
            """
                .trimIndent()
    }
}
