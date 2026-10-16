package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class InfernoMonstersTest {
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
        val pillars: InfernoPillars,
        val combat: InfernoCombatScript,
        val collision: CollisionFlagMap,
    )

    /** Enters alice into an Inferno that plays [waves], and waits for its first wave to spawn. */
    private fun GameTestState.runMonsterTest(
        waves: String,
        body: GameTestScope.(Deps, Player, InfernoRun) -> Unit,
    ) =
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
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            alice.stats[stats.hitpoints] = 99
            deps.inferno.waveLoader = { InfernoWaves.parse(waves, "test waves") }
            deps.inferno.enter(alice)
            val run = checkNotNull(deps.inferno.runOf(alice))
            nextWave(run, 1)
            body(deps, alice, run)
        }

    private fun GameTestScope.nextWave(run: InfernoRun, number: Int) {
        advanceUntil(
            predicate = { run.waveNumber == number },
            timeoutTicks = 40,
            timeoutMessage = { "wave $number never spawned" },
        )
    }

    private fun kinds(run: InfernoRun): List<InfernoMonster> =
        run.monsters.map { InfernoMonsterDefs.of(it.type)!!.kind }

    private fun GameTestScope.kill(npc: Npc) {
        npc.hitpoints = 0
        npc.queueDeath()
        advance(ticks = 2)
    }

    private fun GameTestScope.killAll(run: InfernoRun) {
        repeat(3) {
            for (npc in run.monsters.toList()) {
                npc.hitpoints = 0
                npc.queueDeath()
            }
            advance(ticks = 2)
        }
    }

    /** Puts [npc] [dz] tiles north of [player] and keeps every other monster quiet. */
    private fun place(deps: Deps, run: InfernoRun, npc: Npc, player: Player, dz: Int, dx: Int = 0) {
        npc.telejump(
            deps.collision,
            CoordGrid(player.coords.x + dx, player.coords.z + dz, player.coords.level),
        )
        for (other in run.monsters) {
            if (other !== npc) {
                other.actionDelay = Int.MAX_VALUE
            }
        }
    }

    private fun waves(vararg first: String): String {
        val text = StringBuilder()
        first.forEachIndexed { i, line ->
            text.append("[[wave]]\nnumber = ${i + 1}\nmonsters = [$line]\n\n")
        }
        text.append("[[wave]]\nnumber = ${first.size + 1}\nmonsters = [\"zuk\"]\nboss = true\n")
        return text.toString()
    }

    @Test
    fun GameTestState.`the nibblers spawn together and the others in their own zones`() =
        runMonsterTest(waves("\"nibbler\", \"nibbler\", \"nibbler\", \"bat\"")) { deps, alice, run
            ->
            assertEquals(
                listOf(
                    InfernoMonster.Nibbler,
                    InfernoMonster.Nibbler,
                    InfernoMonster.Nibbler,
                    InfernoMonster.Bat,
                ),
                kinds(run).sortedBy { it.ordinal },
            )
            assertTrue(run.monsters.all { run.instance.contains(it.coords) })
            val centre = run.instance.region.normal[InfernoArena.nibblerSpawn]
            val nibblers = run.monsters.filter { it.type.id == inferno_npcs.nibbler.id }
            assertTrue(nibblers.all { it.distanceTo(centre) <= 3 }, "nibblers near the middle")
        }

    @Test
    fun GameTestState.`monsters are aimed at the player, melee ones to fight next to them`() =
        runMonsterTest(waves("\"nibbler\", \"bat\", \"mager\"")) { _, _, run ->
            val modes = run.monsters.associate { it.type.id to it.mode }
            // Nibblers ignore the player while a pillar stands (see InfernoPillarsTest).
            assertEquals(NpcMode.None, modes[inferno_npcs.nibbler.id])
            assertEquals(NpcMode.ApPlayer2, modes[inferno_npcs.bat.id])
            assertEquals(NpcMode.ApPlayer2, modes[inferno_npcs.mager.id])
        }

    @Test
    fun GameTestState.`the spawn planner gives the biggest monsters the first zones`() =
        runMonsterTest(waves("\"bat\"")) { _, _, _ ->
            val wave =
                InfernoWave(
                    1,
                    listOf(
                        InfernoMonster.Nibbler,
                        InfernoMonster.Bat,
                        InfernoMonster.Mager,
                        InfernoMonster.Ranger,
                    ),
                )
            val plan = InfernoSpawnPlanner { 0 }.plan(wave)

            assertEquals(
                listOf(InfernoMonster.Mager, InfernoMonster.Ranger, InfernoMonster.Bat),
                plan.map { it.first }.filter { it != InfernoMonster.Nibbler },
            )
            val zones = plan.filter { it.first != InfernoMonster.Nibbler }.map { it.second }
            assertEquals(3, zones.toSet().size)
            assertTrue(zones.all { it in InfernoArena.spawnZones })
            assertEquals(
                InfernoArena.nibblerSpawn,
                plan.first { it.first == InfernoMonster.Nibbler }.second,
            )
        }

    @Test
    fun GameTestState.`a cleared wave is followed by the next one`() =
        runMonsterTest(waves("\"nibbler\", \"bat\"", "\"ranger\"")) { _, _, run ->
            killAll(run)
            assertEquals(0, run.monsters.size)
            assertEquals(1, run.waveNumber)

            nextWave(run, 2)
            assertEquals(listOf(InfernoMonster.Ranger), kinds(run))
        }

    @Test
    fun GameTestState.`a blob leaves three splinters that must also be killed`() =
        runMonsterTest(waves("\"blob\"", "\"bat\"")) { _, _, run ->
            val blob = run.monsters.single()
            kill(blob)

            val types = run.monsters.map { it.type.id }.toSet()
            assertEquals(
                setOf(
                    inferno_npcs.blob_range.id,
                    inferno_npcs.blob_mage.id,
                    inferno_npcs.blob_melee.id,
                ),
                types,
            )
            assertEquals(3, run.monsters.size)

            // The wave is not over until the splinters are dead.
            advance(ticks = 20)
            assertEquals(1, run.waveNumber)
            killAll(run)
            nextWave(run, 2)
        }

    @Test
    fun GameTestState.`splinters are not brought back by a Jal-Zek and a nibbler is not either`() =
        runMonsterTest(waves("\"blob\", \"nibbler\", \"mager\"")) { deps, _, run ->
            val mager = run.monsters.first { it.type.id == inferno_npcs.mager.id }
            mager.actionDelay = Int.MAX_VALUE
            kill(run.monsters.first { it.type.id == inferno_npcs.nibbler.id })
            kill(run.monsters.first { it.type.id == inferno_npcs.blob.id })
            for (splinter in run.monsters.filter { it !== mager }) {
                kill(splinter)
            }

            // Only the blob itself could be brought back.
            assertEquals(1, run.slain.size)
            assertEquals(InfernoMonsterDefs.blob, run.slain.single().def)
        }

    @Test
    fun GameTestState.`a Jal-Zek brings a monster back once at half health`() =
        runMonsterTest(waves("\"melee\", \"mager\"")) { deps, _, run ->
            val mager = run.monsters.first { it.type.id == inferno_npcs.mager.id }
            val melee = run.monsters.first { it.type.id == inferno_npcs.melee.id }
            mager.actionDelay = Int.MAX_VALUE
            melee.actionDelay = Int.MAX_VALUE
            assertFalse(deps.inferno.canRevive(run))
            assertFalse(deps.inferno.revive(run, mager))

            kill(melee)
            assertTrue(deps.inferno.canRevive(run))
            assertTrue(deps.inferno.revive(run, mager))

            val back = run.monsters.first { it.type.id == inferno_npcs.melee.id }
            assertEquals(back.baseHitpointsLvl / 2, back.hitpoints)
            assertTrue(run.states.getValue(back).revived)
            assertTrue(back.actionDelay > 0, "a short pause before it attacks")
            assertFalse(deps.inferno.canRevive(run))

            // It can be killed for good: it is not brought back a second time.
            back.actionDelay = Int.MAX_VALUE
            kill(back)
            assertFalse(deps.inferno.canRevive(run))
            assertFalse(deps.inferno.revive(run, mager))
        }

    @Test
    fun GameTestState.`slain monsters are forgotten when the next wave starts`() =
        runMonsterTest(waves("\"melee\"", "\"ranger\"")) { deps, _, run ->
            kill(run.monsters.single())
            assertEquals(1, run.slain.size)

            nextWave(run, 2)

            assertTrue(run.slain.isEmpty())
        }

    @Test
    fun GameTestState.`the blob picks a style the prayer does not cover three ticks ahead`() =
        runMonsterTest(waves("\"blob\"")) { deps, alice, run ->
            val blob = run.monsters.single()
            place(deps, run, blob, alice, dz = 5)
            alice.setVarBit(varbits.protect_from_magic, 1)
            val state = checkNotNull(deps.inferno.stateOf(blob))
            state.blobStyle = null
            blob.actionDelay = mapClock.cycle + 5

            advanceUntil(
                predicate = { state.blobStyle != null },
                timeoutTicks = 4,
                timeoutMessage = { "the blob never read the prayer" },
            )

            assertEquals(org.rsmod.api.combat.NpcAttackStyle.Ranged, state.blobStyle)
        }

    @Test
    fun GameTestState.`ranged and magic monsters shoot from a distance`() =
        runMonsterTest(waves("\"ranger\", \"mager\", \"blob\", \"bat\"")) { deps, alice, run ->
            val shooters = run.monsters.toList()
            for ((i, npc) in shooters.withIndex()) {
                place(deps, run, npc, alice, dz = 6, dx = (i - 1) * 5)
                npc.actionDelay = -1
            }
            for (npc in shooters) {
                val others = shooters.filter { it !== npc }
                others.forEach { it.actionDelay = Int.MAX_VALUE }
                npc.actionDelay = -1
                advance(ticks = 3)
                assertTrue(npc.actionDelay > 0, "${npc.type.id} never attacked")
                assertTrue(npc.distanceTo(alice) > 1, "${npc.type.id} walked up to melee")
            }
        }

    @Test
    fun GameTestState.`melee monsters and nibblers walk up to the player`() =
        runMonsterTest(waves("\"nibbler\", \"melee\"")) { deps, alice, run ->
            deps.pillars.collapseAll(run)
            for ((i, npc) in run.monsters.withIndex()) {
                place(deps, run, npc, alice, dz = 6, dx = if (i == 0) -6 else 4)
                npc.actionDelay = Int.MAX_VALUE
            }
            advance(ticks = 12)
            assertTrue(run.monsters.all { it.distanceTo(alice) <= 1 }, "they walk up to fight")
        }

    @Test
    fun GameTestState.`a bat that hits drains run energy and a level, unless Missiles is on`() =
        runMonsterTest(waves("\"bat\"")) { deps, alice, _ ->
            val combat =
                listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
            combat.forEach { alice.stats[it] = 50 }
            val energy = alice.runEnergy
            val before = combat.sumOf { alice.stats[it] }

            deps.combat.batDrain(alice, HitType.Ranged, 0)
            deps.combat.batDrain(alice, HitType.Magic, 7)
            assertEquals(energy, alice.runEnergy, "a miss or another style drains nothing")

            alice.setVarBit(varbits.protect_from_missiles, 1)
            deps.combat.batDrain(alice, HitType.Ranged, 7)
            assertEquals(energy, alice.runEnergy, "Protect from Missiles stops the drain")

            alice.setVarBit(varbits.protect_from_missiles, 0)
            deps.combat.batDrain(alice, HitType.Ranged, 7)
            assertEquals(energy - 30, alice.runEnergy)
            val after = combat.sumOf { alice.stats[it] }
            assertEquals(before - 1, after)
        }

    @Test
    fun GameTestState
        .`a melee monster burrows when its timer runs out and comes up beside the player`() =
        runMonsterTest(waves("\"melee\"")) { deps, alice, run ->
            val melee = run.monsters.single()
            place(deps, run, melee, alice, dz = 14)
            melee.actionDelay = Int.MAX_VALUE
            val state = checkNotNull(deps.inferno.stateOf(melee))
            state.burrowAt = mapClock.cycle

            advance(ticks = 2)
            assertNotEquals(0, state.digUntil, "it digs down")
            assertEquals(NpcMode.None, melee.mode)
            assertEquals(Int.MAX_VALUE, melee.actionDelay, "it cannot attack underground")

            advance(ticks = Inferno.DIG_TICKS + 2)
            assertEquals(0, state.digUntil, "it comes up")
            assertTrue(melee.distanceTo(alice) <= 1, "next to the player")
            assertTrue(melee.actionDelay > mapClock.cycle, "and waits before it attacks")
            assertTrue(state.burrowAt > mapClock.cycle + 30)
        }

    @Test
    fun GameTestState.`fighting resets the burrow timer, but not during the first 30 ticks`() =
        runMonsterTest(waves("\"melee\"")) { deps, _, run ->
            val melee = run.monsters.single()
            val state = checkNotNull(deps.inferno.stateOf(melee))
            val first = state.burrowAt
            assertEquals(state.spawnedAt + Inferno.BURROW_FIRST, first)

            deps.inferno.noteAttack(melee)
            assertEquals(first, state.burrowAt, "no reset in the first 30 ticks")

            advance(ticks = Inferno.BURROW_LOCK + 1)
            deps.inferno.noteAttack(melee)
            assertNotNull(state)
            assertTrue(state.burrowAt >= mapClock.cycle + Inferno.BURROW_MIN)
            assertTrue(state.burrowAt < mapClock.cycle + Inferno.BURROW_MIN + Inferno.BURROW_SPREAD)
        }

    @Test
    fun GameTestState.`a monster that fights does not burrow`() =
        runMonsterTest(waves("\"melee\"")) { deps, alice, run ->
            val melee = run.monsters.single()
            val state = checkNotNull(deps.inferno.stateOf(melee))
            state.burrowAt = mapClock.cycle + 60
            place(deps, run, melee, alice, dz = 2)

            advance(ticks = 10)

            assertEquals(0, state.digUntil)
            assertNotEquals(NpcMode.None, melee.mode)
        }

    @Test
    fun GameTestState.`the cache gives every ordinary monster its stats and a chase`() =
        runMonsterTest(waves("\"nibbler\"")) { _, _, _ ->
            for (def in InfernoMonsterDefs.all) {
                val type = npcTypes[def.type]
                assertEquals(def.size, type.size, "size of ${type.name}")
                assertTrue(type.hitpoints > 0, type.name)
            }
        }
}
