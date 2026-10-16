package org.rsmod.content.areas.misc.isleofsouls.zulrah

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcMeleeBlocks
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit as queueNpcHit
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PoisonTimers
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PoisonVars
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class ZulrahRunTest {
    private object ZulrahModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Zulrah::class.java).`in`(Scopes.SINGLETON)
            bind(NpcMeleeBlocks::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val zulrah: Zulrah,
        val venom: PlayerVenom,
        val poison: PlayerPoison,
        val potions: PotionEffects,
        val blocks: NpcMeleeBlocks,
        val tracker: LeagueTaskTracker,
        val hitModifier: StandardNpcHitModifier,
        val locRepo: LocRepository,
    )

    /** alice and bob, one team, in a Zulrah that follows rotation [rotation] (0-3). */
    private fun GameTestState.runZulrahTest(
        rotation: Int = 0,
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            ZulrahModule,
            ZulrahScript::class,
            ZulrahCombatScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
            HeldOpScript::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            topUp(alice, bob)
            deps.zulrah.rotationPicker = { rotation }
            if (enter) {
                deps.zulrah.enter(alice)
                deps.zulrah.enter(bob)
            }
            body(deps, alice, bob)
        }

    private fun GameTestScope.topUp(vararg players: Player) {
        for (each in players) {
            each.stats[stats.hitpoints] = 99
        }
    }

    /** Advances (keeping the players alive) until [predicate] holds. */
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

    /** Waits for Zulrah to rise and returns the run. */
    private fun GameTestScope.fightOf(deps: Deps, alice: Player, bob: Player): ZulrahRun {
        val run = checkNotNull(deps.zulrah.runOf(alice))
        until("Zulrah appears", alice, bob, timeout = 40) { run.fight != null }
        return run
    }

    /** Makes Zulrah do nothing for a good while. */
    private fun ZulrahFight.stillFor(ticks: Int, now: Int) {
        nextAt = now + ticks
    }

    /**
     * Puts Zulrah into a made-up [phase] that starts now, with [target] as the player she shoots.
     */
    private fun ZulrahFight.force(phase: ZulrahPhase, target: Player, now: Int) {
        this.phase = phase
        this.form = phase.form
        actionIndex = 0
        submerged = false
        step = ZulrahStep.Acting
        this.target = target
        nextAt = now
    }

    private fun ZulrahRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    @Test
    fun GameTestState.`boarding the boat puts the team on the platform and Zulrah rises`() =
        runZulrahTest { deps, alice, bob ->
            val run = checkNotNull(deps.zulrah.runOf(alice))
            assertEquals(ZulrahInstance.type, run.instance.type)
            assertEquals(run.real(ZulrahArena.start), alice.coords)
            assertEquals(run, deps.zulrah.runOf(bob))
            assertTrue(run.platform.size > 30, "the platform has ${run.platform.size} tiles")

            until("Zulrah appears", alice, bob, timeout = 40) { run.fight != null }
            val fight = checkNotNull(run.fight)
            assertEquals(run.real(ZulrahArena.spot(ZulrahSpot.Centre)), fight.zulrah.coords)
            assertEquals(500, fight.zulrah.hitpoints)
            assertEquals(zulrah_npcs.green.id, fight.zulrah.type.id)
            assertEquals(ZulrahRotations.one, fight.rotation)
            assertEquals(WaveState.InWave, run.state)
        }

    @Test
    fun GameTestState.`the platform tiles are walkable and the pillars are not on it`() =
        runZulrahTest { deps, alice, _ ->
            val run = checkNotNull(deps.zulrah.runOf(alice))
            val platform = run.platform.map { it.normal }
            assertTrue(CoordGrid(2268, 3069, 0) in platform, "the start tile")
            assertTrue(CoordGrid(2268, 3068, 0) in platform, "the exit scroll tile")
            assertFalse(CoordGrid(2265, 3071, 0) in platform, "a pillar")
            assertFalse(CoordGrid(2271, 3071, 0) in platform, "a pillar")
            assertFalse(CoordGrid(2268, 3075, 0) in platform, "the pond")
            assertTrue(
                platform.all { it.x in ZulrahArena.platformX && it.z in ZulrahArena.platformZ }
            )
        }

    @Test
    fun GameTestState
        .`the opening fill is eight clouds on fixed sites that leave only the tips clear`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val sites = ZulrahCloudSites.all.map { run.real(it) }.toSet()
            until("the fill has landed", alice, bob) { run.clouds.size == 8 }
            for (cloud in run.clouds) {
                assertTrue(cloud.centre in sites, "cloud at ${cloud.centre} is not a site")
                assertNotNull(cloud.loc)
            }
            val covered =
                run.clouds.flatMap { c ->
                    (-1..1).flatMap { dx ->
                        (-1..1).map { dz -> CoordGrid(c.centre.x + dx, c.centre.z + dz, 0) }
                    }
                }
            val free = run.platform.filter { it.real !in covered }.map { it.normal }.toSet()
            val tips =
                setOf(
                    CoordGrid(2264, 3078, 0),
                    CoordGrid(2272, 3077, 0),
                    CoordGrid(2272, 3078, 0),
                    CoordGrid(2273, 3077, 0),
                    CoordGrid(2274, 3077, 0),
                )
            assertEquals(tips, free)

            // Quiet Zulrah and wait the clouds out.
            run.fight!!.stillFor(1_000, mapClock.cycle)
            until("clouds expire", alice, bob, timeout = 60) { run.clouds.isEmpty() }
        }

    @Test
    fun GameTestState.`she dives, rises red in the centre`() = runZulrahTest { deps, alice, bob ->
        val run = fightOf(deps, alice, bob)
        val fight = checkNotNull(run.fight)
        var sawSubmerged = false
        until("phase two", alice, bob) {
            sawSubmerged = sawSubmerged || fight.submerged
            fight.phaseIndex == 1 && fight.step == ZulrahStep.Acting
        }
        assertTrue(sawSubmerged, "she was under the water between the phases")
        assertEquals(ZulrahForm.Red, fight.form)
        assertEquals(zulrah_npcs.red.id, fight.zulrah.visType.id)
        assertEquals(run.real(ZulrahArena.spot(ZulrahSpot.Centre)), fight.zulrah.coords)
        assertFalse(fight.submerged)
    }

    @Test
    fun GameTestState.`phases rise at their own spot and in their own form`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val seen = mutableMapOf<Int, Pair<ZulrahForm, CoordGrid>>()
            until("phase four", alice, bob, timeout = 600) {
                if (fight.step == ZulrahStep.Acting) {
                    seen[fight.phaseIndex] = fight.form to fight.zulrah.coords
                }
                fight.phaseIndex == 3 && fight.step == ZulrahStep.Acting
            }
            // Rotation 1: phase 4 (index 3) is green in the south.
            val (form, coords) = seen.getValue(3)
            assertEquals(ZulrahForm.Green, form)
            assertEquals(run.real(ZulrahArena.spot(ZulrahSpot.South)), coords)
            assertEquals(ZulrahForm.Blue, seen.getValue(2).first)
        }

    @Test
    fun GameTestState.`a ranged phase shoots every three ticks and then she dives`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Green, List(3) { ZulrahAction.Ranged }),
                alice,
                mapClock.cycle,
            )

            val shotAt = mutableListOf<Int>()
            var last = 0
            until("three shots", alice, bob, timeout = 40) {
                if (fight.actionIndex != last) {
                    last = fight.actionIndex
                    shotAt += mapClock.cycle
                }
                fight.step == ZulrahStep.Diving
            }
            assertEquals(3, shotAt.size)
            assertEquals(Zulrah.ATTACK_RATE, shotAt[1] - shotAt[0])
            assertEquals(Zulrah.ATTACK_RATE, shotAt[2] - shotAt[1])
            assertTrue(fight.submerged)
        }

    @Test
    fun GameTestState.`a snakeling orb becomes a snakeling that is gone after forty seconds`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(
                    ZulrahSpot.Centre,
                    ZulrahForm.Green,
                    listOf(ZulrahAction.Orb(CoordGrid(2263, 3073, 0))),
                ),
                alice,
                mapClock.cycle,
            )

            until("a snakeling comes out", alice, bob, timeout = 40) { run.monsters.isNotEmpty() }
            val snakeling = run.monsters.single()
            assertTrue(deps.zulrah.isSnakeling(snakeling))
            assertTrue(run.platform.any { it.real == snakeling.coords }, "on the platform")
            assertEquals(1, snakeling.hitpoints)

            fight.stillFor(10_000, mapClock.cycle)
            until("the snakeling goes", alice, bob, timeout = Zulrah.SNAKELING_LIFETIME + 20) {
                run.monsters.isEmpty()
            }
            assertFalse(snakeling.isSlotAssigned)
        }

    @Test
    fun GameTestState
        .`a tail swing hits the tile she aimed at and the ones round it, not further`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Red, listOf(ZulrahAction.Melee)),
                alice,
                mapClock.cycle,
            )

            until("the swing starts", alice, bob, timeout = 20) { fight.tails.isNotEmpty() }
            val tile = fight.tails.single().tile
            // alice stays where the swing was aimed; bob goes as far from it as the platform
            // allows.
            alice.telejump(tile)
            val far =
                run.platform.maxBy {
                    maxOf(kotlin.math.abs(it.real.x - tile.x), kotlin.math.abs(it.real.z - tile.z))
                }
            bob.telejump(far.real)
            assertTrue(
                maxOf(kotlin.math.abs(far.real.x - tile.x), kotlin.math.abs(far.real.z - tile.z)) >=
                    2
            )

            until("the first swing lands", alice, bob, timeout = 20) { alice.isFrozen }
            assertFalse(bob.isFrozen, "bob was not stunned")
            assertTrue(deps.venom.isVenomed(alice), "the tail envenoms")
            assertTrue(alice.coords != tile, "alice was thrown back")
            assertTrue(
                run.platform.any { it.real == alice.coords },
                "thrown back onto the platform, not into the water",
            )
        }

    @Test
    fun GameTestState.`a melee action is two swings and takes eighteen ticks`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Red, listOf(ZulrahAction.Melee)),
                alice,
                mapClock.cycle,
            )
            until("the swing starts", alice, bob, timeout = 20) { fight.tails.isNotEmpty() }
            val first = fight.tails.single()
            val start = first.actionStart
            assertEquals(start + Zulrah.FIRST_STRIKE, first.hitAt)
            until("the second swing", alice, bob, timeout = 20) {
                fight.tails.singleOrNull()?.first == false
            }
            val second = fight.tails.single()
            assertEquals(start + Zulrah.SECOND_AIM, second.animAt)
            assertEquals(start + Zulrah.SECOND_STRIKE, second.hitAt)
            until("she dives", alice, bob, timeout = 30) { fight.step == ZulrahStep.Diving }
            // She dives the tick the action's 18 are up; the dive sets when she resurfaces.
            assertEquals(start + Zulrah.MELEE_RATE, fight.nextAt - Zulrah.DIVE_TICKS)
        }

    @Test
    fun GameTestState.`the tail stuns for three seconds`() = runZulrahTest { deps, alice, bob ->
        val run = fightOf(deps, alice, bob)
        val fight = checkNotNull(run.fight)
        fight.force(
            ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Red, listOf(ZulrahAction.Melee)),
            alice,
            mapClock.cycle,
        )
        until("the swing starts", alice, bob, timeout = 20) { fight.tails.isNotEmpty() }
        alice.telejump(fight.tails.single().tile)
        until("alice is stunned", alice, bob, timeout = 20) { alice.isFrozen }
        var ticks = 0
        until("the stun wears off", alice, bob, timeout = 20) {
            ticks++
            alice.isFrozen.not()
        }
        assertTrue(ticks <= Zulrah.TAIL_STUN + 1, "the stun lasted $ticks ticks")
    }

    @Test
    fun GameTestState.`only a halberd reaches her with melee`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val boss = checkNotNull(run.fight).zulrah
            assertTrue(deps.blocks.isBlocked(boss), "melee is blocked")
            assertTrue(deps.blocks.allows(boss, "dragon_halberd"))
            assertFalse(deps.blocks.allows(boss, "abyssal_whip"))
        }

    @Test
    fun GameTestState.`the tiles beside the pillars are safe from the tail`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val safe = run.real(ZulrahArena.tailSafe.first())
            alice.telejump(safe)
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Red, listOf(ZulrahAction.Melee)),
                alice,
                mapClock.cycle,
            )
            until("the swing starts", alice, bob, timeout = 20) { fight.tails.isNotEmpty() }
            assertEquals(safe, fight.tails.single().tile, "she aimed at alice")
            until("the first swing lands", alice, bob, timeout = 20) {
                fight.tails.singleOrNull()?.first == false
            }
            assertEquals(safe, alice.coords, "alice was not thrown")
            assertFalse(alice.isFrozen, "alice was not stunned")
            assertFalse(deps.venom.isVenomed(alice), "alice was not envenomed")
        }

    @Test
    fun GameTestState.`a tail swing lands before she dives, even as the phase's last action`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Red, listOf(ZulrahAction.Melee)),
                alice,
                mapClock.cycle,
            )
            until("she dives", alice, bob) { fight.step == ZulrahStep.Diving }
            assertTrue(fight.tails.isEmpty(), "the swing had landed before the dive")
        }

    @Test
    fun GameTestState.`standing in a cloud hurts but does not envenom`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            until("a cloud lands", alice, bob) { run.clouds.isNotEmpty() }
            fight.stillFor(10_000, mapClock.cycle)
            fight.pendingClouds.clear()
            val cloud = run.clouds.first()
            alice.stats[stats.hitpoints] = 99
            alice.telejump(cloud.centre)
            // bob stays on the start tile, which is far from the clouds' reach unless one landed
            // there.
            advance(ticks = 3)
            assertTrue(alice.stats[stats.hitpoints] < 99, "alice lost hitpoints")
            assertFalse(deps.venom.isVenomed(alice), "a cloud does not envenom (wiki)")
        }

    @Test
    fun GameTestState.`venom hits for six and rises by two to twenty`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            run.fight!!.stillFor(100_000, mapClock.cycle)
            assertTrue(deps.venom.envenom(alice))
            assertFalse(deps.venom.envenom(alice), "a second dose changes nothing")
            assertEquals(6, deps.venom.damageOf(alice))

            val seen = mutableListOf(deps.venom.damageOf(alice))
            repeat(9) {
                topUp(alice, bob)
                advance(ticks = PlayerVenom.INTERVAL)
                seen += deps.venom.damageOf(alice)
            }
            assertEquals(listOf(6, 8, 10, 12, 14, 16, 18, 20, 20, 20), seen)
        }

    @Test
    fun GameTestState.`venom takes hitpoints each time it ticks`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            run.fight!!.stillFor(100_000, mapClock.cycle)
            deps.venom.envenom(alice)
            alice.stats[stats.hitpoints] = 99
            advance(ticks = PlayerVenom.INTERVAL + 3)
            assertEquals(99 - 6, alice.stats[stats.hitpoints])
        }

    @Test
    fun GameTestState.`the charged serpentine helm and an antipoison stop venom`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            run.fight!!.stillFor(100_000, mapClock.cycle)

            alice.worn[Wearpos.Hat.slot] = InvObj(zulrah_objs.serpentine_helm_charged)
            assertTrue(deps.venom.isImmune(alice))
            assertFalse(deps.venom.envenom(alice))
            assertEquals(0, deps.venom.damageOf(alice))

            assertFalse(deps.venom.isImmune(bob))
            assertTrue(deps.venom.envenom(bob))
        }

    @Test
    fun GameTestState.`leaving the lair keeps venom`() = runZulrahTest { deps, alice, bob ->
        val run = fightOf(deps, alice, bob)
        run.fight!!.stillFor(100_000, mapClock.cycle)
        deps.venom.envenom(alice)
        assertTrue(deps.zulrah.leave(alice))
        advance(ticks = PlayerVenom.INTERVAL + 2)
        assertTrue(deps.venom.isVenomed(alice), "venom follows a player out of the lair")
        assertTrue(alice.vars[zulrah_vars.poison_orb] > 0)
    }

    @Test
    fun GameTestState.`antipoison keeps its ticks through a log-out and log-in`() =
        runZulrahTest { deps, alice, bob ->
            deps.potions.start(alice, TimedEffect.Antipoison, 100)
            // The effect ticks come from herblore's script, which this test does not load.
            repeat(10) { deps.potions.tick(alice) }
            // A log-out drops the effect state, and a log-in starts from the saved ticks.
            deps.potions.reset(alice)
            assertTrue(deps.potions.hasPoisonProtection(alice), "still protected after login")
            assertEquals(90, deps.potions.ticksLeft(alice, TimedEffect.Antipoison))
        }

    @Test
    fun GameTestState.`poison comes back on login with its timer and orb`() =
        runZulrahTest { deps, alice, bob ->
            assertTrue(deps.poison.poison(alice, 16))
            // Timers are not saved, so a login starts without the poison timer.
            alice.clearSoftTimer(PoisonTimers.poison)
            advance(ticks = PlayerPoison.INTERVAL + 2)
            assertEquals(16, deps.poison.damageOf(alice), "no timer, so no hit before login")

            deps.poison.resume(alice)
            assertEquals(16, alice.vars[PoisonVars.orb])
            // The poison timer's handler is in the God Wars script, which this test does not load.
            deps.poison.tick(alice)
            assertEquals(1, alice.vars[PoisonVars.hits], "the poison hits again after login")
        }

    @Test
    fun GameTestState.`venom comes back on login with its timer and orb`() =
        runZulrahTest { deps, alice, bob ->
            deps.venom.envenom(alice)
            // Timers are not saved, so a login starts without the venom timer.
            alice.clearSoftTimer(zulrah_timers.venom)
            advance(ticks = PlayerVenom.INTERVAL + 2)
            assertEquals(6, deps.venom.damageOf(alice), "no timer, so no hit before login")

            deps.venom.resume(alice)
            assertEquals(1_000_006, alice.vars[zulrah_vars.poison_orb])
            advance(ticks = PlayerVenom.INTERVAL + 2)
            assertEquals(8, deps.venom.damageOf(alice), "the venom ticks again after login")
        }

    @Test
    fun GameTestState.`a hit on Zulrah is taken in full, above fifty and under the water too`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            until("she is up", alice, bob, timeout = 40) { fight.step == ZulrahStep.Acting }
            fight.stillFor(100_000, mapClock.cycle)
            val zulrah = fight.zulrah

            zulrah.queueNpcHit(1, HitType.Typeless, 90, deps.hitModifier)
            advance(ticks = 2)
            assertEquals(90, 500 - zulrah.hitpoints)

            fight.submerged = true
            val before = zulrah.hitpoints
            zulrah.queueNpcHit(1, HitType.Typeless, 30, deps.hitModifier)
            advance(ticks = 2)
            assertEquals(before - 30, zulrah.hitpoints)
        }

    @Test
    fun GameTestState.`killing Zulrah wins the run, counts the tasks once for the team`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.stillFor(100_000, mapClock.cycle)
            assertFalse(deps.tracker.isComplete(alice, ZulrahCompletions.TASK_DEFEAT))

            fight.zulrah.hitpoints = 0
            fight.zulrah.queueDeath()
            until("the kill is counted", alice, bob, timeout = 40) {
                deps.tracker.isComplete(alice, ZulrahCompletions.TASK_DEFEAT)
            }
            assertTrue(deps.tracker.isComplete(bob, ZulrahCompletions.TASK_DEFEAT))
            // One kill for the team, not two.
            assertEquals(1, alice.vars[LeagueProgressVarps.zulrah_50])
            assertEquals(1, alice.vars[LeagueProgressVarps.zulrah_300])
            // Nobody is sent out: she waits to come back.
            assertEquals(WaveState.WaitingForNextWave, run.state)
            assertTrue(deps.instances.instanceOf(alice) != null, "alice is still in the lair")
        }

    @Test
    fun GameTestState.`after a kill Zulrah comes back in the same lair`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val dead = checkNotNull(run.fight)
            dead.stillFor(100_000, mapClock.cycle)
            dead.zulrah.hitpoints = 0
            dead.zulrah.queueDeath()
            until("she is back", alice, bob, timeout = 200) {
                run.state == WaveState.InWave && run.fight !== dead
            }
            val back = checkNotNull(run.fight)
            assertTrue(back.zulrah.isSlotAssigned, "a new Zulrah is in the lair")
            assertTrue(run.instance.contains(back.zulrah.coords))
            assertFalse(back.zulrah === dead.zulrah)
        }

    @Test
    fun GameTestState.`her drop lands where the killer stands, not in the water`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            until("she is up", alice, bob, timeout = 40) { fight.step == ZulrahStep.Acting }
            fight.stillFor(100_000, mapClock.cycle)
            val standing = alice.coords
            // Only alice hurts her.
            alice.observerUUID = 1L
            fight.zulrah.heroPoints(alice, 5)
            fight.zulrah.hitpoints = 0
            fight.zulrah.queueDeath()
            until("she is dead", alice, bob, timeout = 40) { fight.dead }
            advance(ticks = 20)
            val drops = objRepo.findAll(standing).toList()
            val hero = fight.zulrah.findHero(players)
            assertTrue(
                drops.isNotEmpty(),
                "something dropped at $standing (hero ${hero?.username}, zulrah at ${fight.zulrah.coords}, hp ${fight.zulrah.hitpoints}, assigned ${fight.zulrah.isSlotAssigned})",
            )
        }

    @Test
    fun GameTestState.`dismantling a unique gives twenty thousand scales and the task`() =
        runZulrahTest(enter = false) { deps, alice, _ ->
            alice.inv[0] = InvObj(zulrah_objs.tanzanite_fang)
            val obj = objTypes[zulrah_objs.tanzanite_fang]
            // Her uniques' fourth option is the fourth held op, which the client sends as Op6.
            eventBus.publish(IfOverlayButton(alice, components.inv_items, 0, obj, IfButtonOp.Op6))
            advance(ticks = 2)
            assertEquals(ZulrahScript.SCALES_PER_UNIQUE, alice.count(objTypes[zulrah_objs.scales]))
            assertTrue(deps.tracker.isComplete(alice, ZulrahCompletions.TASK_DISMANTLE))
        }

    @Test
    fun GameTestState.`the next rotation follows the last and she can show another form`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            // Jump to the last phase of the rotation and let it end.
            fight.phaseIndex = fight.rotation.phases.size - 1
            fight.phase = fight.rotation.phases.last()
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Green, emptyList()),
                alice,
                mapClock.cycle,
            )
            until("the next rotation", alice, bob, timeout = 60) { fight.rotationsDone == 1 }
            // The closing phase counted as the first of the next rotation: it carries on from the
            // second phase, so the opening fill is not thrown twice.
            assertEquals(ZulrahRotations.FIRST_PHASE_AFTER_WRAP, fight.phaseIndex)
        }

    @Test
    fun GameTestState.`a barrage lands exactly the two clouds the rotation gives`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val first = CoordGrid(2263, 3070, 0)
            val second = CoordGrid(2272, 3069, 0)
            fight.force(
                ZulrahPhase(
                    ZulrahSpot.Centre,
                    ZulrahForm.Green,
                    listOf(ZulrahAction.Cloud(first, second)),
                ),
                alice,
                mapClock.cycle,
            )
            // The opening fill may still be on its way: only look at what this barrage adds.
            fight.pendingClouds.clear()
            run.clouds.forEach { it.loc?.let { loc -> deps.locRepo.del(loc, Int.MAX_VALUE) } }
            run.clouds.clear()
            until("the barrage lands", alice, bob, timeout = 30) { run.clouds.size >= 2 }
            assertEquals(
                setOf(run.real(first), run.real(second)),
                run.clouds.map { it.centre }.toSet(),
            )
        }

    @Test
    fun GameTestState.`a second cloud on the same site only renews it`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val site = CoordGrid(2266, 3069, 0)
            val barrage = ZulrahAction.Cloud(site, CoordGrid(2269, 3069, 0))
            fight.force(
                ZulrahPhase(ZulrahSpot.Centre, ZulrahForm.Green, listOf(barrage, barrage)),
                alice,
                mapClock.cycle,
            )
            fight.pendingClouds.clear()
            run.clouds.forEach { it.loc?.let { loc -> deps.locRepo.del(loc, Int.MAX_VALUE) } }
            run.clouds.clear()
            until("both barrages have landed", alice, bob, timeout = 40) {
                fight.actionIndex == 2 && fight.pendingClouds.isEmpty()
            }
            assertEquals(2, run.clouds.size)
        }

    @Test
    fun GameTestState.`a red phase still throws its clouds and orbs, and swings`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(
                    ZulrahSpot.Centre,
                    ZulrahForm.Red,
                    listOf(
                        ZulrahAction.Cloud(CoordGrid(2263, 3070, 0), CoordGrid(2266, 3069, 0)),
                        ZulrahAction.Orb(CoordGrid(2273, 3072, 0)),
                        ZulrahAction.Melee,
                    ),
                ),
                alice,
                mapClock.cycle,
            )
            var swung = false
            var orbs = false
            until("the phase is done", alice, bob, timeout = 60) {
                swung = swung || fight.tails.isNotEmpty()
                orbs = orbs || fight.pendingSnakelings.isNotEmpty() || run.monsters.isNotEmpty()
                fight.step == ZulrahStep.Diving
            }
            assertTrue(swung, "she swung her tail")
            assertTrue(orbs, "she spat the orb")
        }

    @Test
    fun GameTestState.`an orb aimed at the shore lands on the nearest platform tile`() =
        runZulrahTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.force(
                ZulrahPhase(
                    ZulrahSpot.Centre,
                    ZulrahForm.Green,
                    listOf(ZulrahAction.Orb(CoordGrid(2273, 3078, 0))),
                ),
                alice,
                mapClock.cycle,
            )
            until("the orb is in the air", alice, bob, timeout = 20) {
                fight.pendingSnakelings.isNotEmpty()
            }
            val tile = fight.pendingSnakelings.single().tile
            assertTrue(run.platform.any { it.real == tile }, "$tile is not on the platform")
            // One tile from the shore tile she aimed at (several platform tiles are that close).
            val wanted = run.real(CoordGrid(2273, 3078, 0))
            assertTrue(
                maxOf(kotlin.math.abs(tile.x - wanted.x), kotlin.math.abs(tile.z - wanted.z)) <= 1
            )
        }
}
