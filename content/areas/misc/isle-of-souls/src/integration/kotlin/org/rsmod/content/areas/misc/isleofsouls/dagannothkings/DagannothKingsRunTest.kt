package org.rsmod.content.areas.misc.isleofsouls.dagannothkings

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueDagannothKingTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class DagannothKingsRunTest {
    private object DkModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(DagannothKings::class.java).`in`(Scopes.SINGLETON)
            bind(DkScript::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueDagannothKingTasks::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that these scripts load.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val dks: DagannothKings,
        val script: DkScript,
        val tasks: LeagueDagannothKingTasks,
        val attacks: DkAttacks,
        val tracker: LeagueTaskTracker,
        val dropTables: DropTables,
        val collision: CollisionFlagMap,
        val eventBus: EventBus,
        val poison: PlayerPoison,
        val npcs: NpcRepository,
    )

    /** alice and bob, one team, in a lair whose kings have not yet appeared. */
    private fun GameTestState.runDkTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            DkModule,
            DkScript::class,
            LeagueDagannothKingTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 301L
            alice.observerUUID = 301L
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.uuid = 302L
            bob.observerUUID = 302L
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            topUp(alice, bob)
            if (enter) {
                deps.dks.enter(alice)
                deps.dks.enter(bob)
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

    /** Waits for the kings and spinolyps to appear, and returns the run. */
    private fun GameTestScope.runWithKings(deps: Deps, alice: Player, bob: Player): DkRun {
        val run = checkNotNull(deps.dks.runOf(alice))
        until("the kings appear", alice, bob, timeout = 30) {
            run.kings.values.all { it.npc != null }
        }
        // A npc added this tick is not processed until the next.
        advance(ticks = 2)
        return run
    }

    /**
     * Removes every npc from the lair but [keep]'s (all of them for `null`), and keeps them from
     * coming back during the test.
     */
    private fun DkRun.keepOnly(deps: Deps, keep: DkKing?, spinolyps: Boolean = false) {
        val never = Int.MAX_VALUE / 2
        for (slot in kings.values) {
            if (slot.king == keep) {
                continue
            }
            slot.npc?.let { deps.npcs.del(it, Int.MAX_VALUE) }
            slot.npc = null
            slot.respawnAt = never
        }
        if (!spinolyps) {
            for (slot in this.spinolyps) {
                slot.npc?.let { deps.npcs.del(it, Int.MAX_VALUE) }
                slot.npc = null
                slot.respawnAt = never
            }
        }
    }

    private fun DkRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    private fun DkRun.king(king: DkKing): Npc = checkNotNull(kings.getValue(king).npc)

    // The lair --------------------------------------------------------------------------------

    @Test
    fun GameTestState.`entering puts the team in the lair with three kings and 25 spinolyps`() =
        runDkTest { deps, alice, bob ->
            val run = checkNotNull(deps.dks.runOf(alice))
            assertEquals(DkInstance.type, run.instance.type)
            assertEquals(run.real(DkArena.start), alice.coords)
            assertEquals(run, deps.dks.runOf(bob))

            runWithKings(deps, alice, bob)
            for (king in DkKing.entries) {
                val npc = run.king(king)
                assertEquals(king.type.id, npc.type.id)
                assertTrue(run.instance.contains(npc.coords))
                val home = run.real(king.home)
                assertTrue(npc.coords.chebyshevDistance(home) <= 8, "$king is far from $home")
                assertEquals(255, npc.baseHitpointsLvl, "$king has the wiki's 255 hitpoints")
            }
            assertEquals(25, run.spinolyps.count { it.npc != null })
            for (slot in run.spinolyps) {
                assertEquals(run.real(slot.spot.coords), checkNotNull(slot.npc).coords)
            }
        }

    @Test
    fun GameTestState.`the kings carry the dagannoth Slayer task`() = runDkTest { _, _, _ ->
        for (king in DkKing.entries) {
            assertEquals(35, npcTypes[king.type].param(SlayerParams.task_id), "$king")
            assertEquals(DkKing.SIZE, npcTypes[king.type].size, "$king")
        }
    }

    @Test
    fun GameTestState.`the start and each king's home are open floor`() =
        runDkTest { deps, alice, _ ->
            val run = checkNotNull(deps.dks.runOf(alice))
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            fun open(spot: CoordGrid, size: Int, what: String) {
                val real = run.real(spot)
                for (dz in 0 until size) {
                    for (dx in 0 until size) {
                        val flags = deps.collision[real.x + dx, real.z + dz, real.level]
                        assertEquals(0, flags and blocked, "$what is blocked at +$dx,+$dz")
                    }
                }
            }
            open(DkArena.start, 1, "the start")
            for (king in DkKing.entries) {
                open(king.home, DkKing.SIZE, "$king's home")
            }
        }

    @Test
    fun GameTestState.`two teams get lairs of their own`() = runDkTest { deps, alice, bob ->
        val carol = registerPlayer(player = Player().apply { username = "carol" })
        carol.uuid = 303L
        carol.observerUUID = 303L
        deps.teams.createTeam(2, "Beta")
        deps.teams.assign("carol", 2)
        deps.dks.enter(carol)
        val first = checkNotNull(deps.dks.runOf(alice))
        val second = checkNotNull(deps.dks.runOf(carol))
        assertTrue(first !== second)
        assertTrue(first.instance !== second.instance)
        assertEquals(first, deps.dks.runOf(bob))
        assertEquals(2, deps.dks.teammatesInside(alice))
        assertEquals(1, deps.dks.teammatesInside(carol))
    }

    @Test
    fun GameTestState.`climbing the ladder up leaves for the ladder on the isle`() =
        runDkTest { deps, alice, _ ->
            assertTrue(deps.dks.leave(alice))
            assertEquals(DkArena.exit, alice.coords)
            assertEquals(1, deps.dks.teammatesInside(alice), "bob is still down there")
        }

    @Test
    fun GameTestState.`the ladder on the isle takes players down and peeks`() =
        runDkTest(enter = false) { deps, alice, _ ->
            allocZoneCollision(DkLadder.coords)
            deps.script.placeLadder()
            val info =
                checkNotNull(
                    findLocs(DkLadder.coords).firstOrNull {
                        it.coords == DkLadder.coords && it.id == dk_locs.entrance.id
                    }
                ) {
                    "the ladder is placed"
                }
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            val front = DkLadder.front
            assertEquals(0, deps.collision[front.x, front.z, front.level] and blocked)
            assertEquals(0, deps.dks.teammatesInside(alice))
            assertEquals(
                "You peek down the ladder. Nobody from your team is in the lair.",
                DkScript.peekMessage(0),
            )

            alice.placeAt(DkLadder.front)
            alice.opLoc1(BoundLocInfo(info, locTypes[dk_locs.entrance]))
            advance(ticks = 8)
            val run = checkNotNull(deps.dks.runOf(alice)) { "alice is in a lair" }
            assertEquals(run.real(DkArena.start), alice.coords)
        }

    // The kings -------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a king leaves a player off its side alone and attacks one near it`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            run.keepOnly(deps, DkKing.Rex)
            deps.dks.leave(bob)
            val rex = run.king(DkKing.Rex)
            // The start is 13 tiles from Rex's home: off his side of the lair.
            repeat(40) {
                topUp(alice)
                advance()
            }
            assertEquals(99, alice.hitpoints, "Rex never came for her")
            assertNull((rex.interaction as? InteractionPlayerOp)?.target)

            alice.telejump(rex.coords.translate(DkKing.SIZE, 1))
            val taken = ArrayList<Int>()
            repeat(120) {
                topUp(alice)
                advance()
                val lost = 99 - alice.hitpoints
                if (lost > 0) taken += lost
            }
            assertTrue(taken.isNotEmpty(), "Rex attacks her once she is near")
            assertTrue(taken.all { it <= DkAttacks.REX_MAX }, "nothing tops 26: $taken")
        }

    /** Waits until [player] has lost hitpoints, keeping them topped up first. */
    private fun GameTestScope.untilHit(what: String, player: Player, timeout: Int) {
        topUp(player)
        var ticks = 0
        while (player.hitpoints == 99) {
            check(ticks++ < timeout) { "never happened: $what" }
            advance()
        }
    }

    @Test
    fun GameTestState.`a king that is on a player follows them to the other side of the lair`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            run.keepOnly(deps, DkKing.Rex)
            deps.dks.leave(bob)
            val rex = run.king(DkKing.Rex)
            alice.telejump(rex.coords.translate(DkKing.SIZE, 1))
            untilHit("Rex attacks her", alice, timeout = 60)

            // The start is 13 tiles from his home, off his side: he would never pick her there.
            alice.telejump(run.real(DkArena.start))
            advance()
            untilHit("Rex comes after her", alice, timeout = 80)
            assertEquals(alice, (rex.interaction as? InteractionPlayerOp)?.target)
            assertTrue(rex.coords.chebyshevDistance(run.real(DkKing.Rex.home)) > DkKing.WANDER)
        }

    @Test
    fun GameTestState.`Prime and Supreme shoot their target across the lair`() =
        runDkTest { deps, alice, bob ->
            for (king in listOf(DkKing.Prime, DkKing.Supreme)) {
                val run = runWithKings(deps, alice, bob)
                run.keepOnly(deps, king)
                deps.dks.leave(bob)
                val npc = run.king(king)
                alice.telejump(npc.coords.translate(DkKing.SIZE + 3, 1))
                untilHit("$king attacks her", alice, timeout = 60)

                // The south-east corner of the floor, 14 tiles or more from both their homes.
                alice.telejump(run.real(CoordGrid(2924, 4437, 0)))
                advance()
                untilHit("$king shoots her across the lair", alice, timeout = 40)
                untilHit("$king keeps shooting", alice, timeout = 40)
                assertTrue(
                    npc.coords.chebyshevDistance(alice.coords) > 10,
                    "$king shoots from where it stands: ${npc.coords} to ${alice.coords}",
                )
                deps.dks.leave(alice)
                deps.dks.leave(bob)
                deps.dks.enter(alice)
                deps.dks.enter(bob)
            }
        }

    @Test
    fun GameTestState.`Prime and Supreme hit from a distance up to their max`() =
        runDkTest { deps, alice, bob ->
            for (king in listOf(DkKing.Prime, DkKing.Supreme)) {
                val run = runWithKings(deps, alice, bob)
                run.keepOnly(deps, king)
                val npc = run.king(king)
                alice.telejump(npc.coords.translate(DkKing.SIZE + 4, 1))
                bob.telejump(run.real(DkArena.start))
                val taken = ArrayList<Int>()
                repeat(150) {
                    topUp(alice, bob)
                    advance()
                    val lost = 99 - alice.hitpoints
                    if (lost > 0) taken += lost
                }
                val max = if (king == DkKing.Prime) DkAttacks.PRIME_MAX else DkAttacks.SUPREME_MAX
                assertTrue(taken.isNotEmpty(), "$king attacks")
                assertTrue(taken.all { it <= max }, "$king tops $max: $taken")
                assertTrue(
                    npc.coords.chebyshevDistance(alice.coords) > 1,
                    "$king fights from a distance",
                )
                // Start the next king in a fresh lair.
                deps.dks.leave(alice)
                deps.dks.leave(bob)
                deps.dks.enter(alice)
                deps.dks.enter(bob)
            }
        }

    @Test
    fun GameTestState
        .`Prime splashes the players next to its target and Supreme reaches further`() =
        runDkTest { deps, alice, bob ->
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            carol.uuid = 303L
            carol.observerUUID = 303L
            deps.teams.assign("carol", 1)
            deps.dks.enter(carol)
            val run = runWithKings(deps, alice, bob)
            run.keepOnly(deps, null)
            val spot = run.real(CoordGrid(2914, 4440, 0))
            alice.telejump(spot)
            bob.telejump(spot.translate(1, 1))
            carol.telejump(spot.translate(3, 0))
            advance()
            val prime = spawnNpc(spot.translate(-6, 0), npcTypes[dk_npcs.prime])

            assertEquals(
                listOf(bob),
                deps.attacks.splashTargets(prime, alice, DkAttacks.PRIME_SPLASH),
            )
            assertEquals(
                setOf(bob, carol),
                deps.attacks.splashTargets(prime, alice, DkAttacks.SUPREME_SPLASH).toSet(),
            )
            assertEquals(listOf(alice, bob), deps.attacks.king(prime, DkKing.Prime, alice))
        }

    // The spinolyps ---------------------------------------------------------------------------

    @Test
    fun GameTestState.`spinolyps shoot players in range, drain prayer and poison`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            run.keepOnly(deps, null, spinolyps = true)
            deps.dks.leave(bob)
            alice.stats[stats.prayer] = 99
            val taken = ArrayList<Int>()
            var poisoned = false
            repeat(600) {
                topUp(alice)
                advance()
                val lost = 99 - alice.hitpoints
                if (lost > 0) taken += lost
                poisoned = poisoned || deps.poison.isPoisoned(alice)
                if (poisoned && alice.stats[stats.prayer] < 99 && taken.size > 5) {
                    return@repeat
                }
            }
            assertTrue(taken.size > 5, "they shoot: $taken")
            assertTrue(alice.stats[stats.prayer] < 99, "their magic drains prayer")
            assertTrue(poisoned, "their spines poison")
        }

    @Test
    fun GameTestState
        .`a spinolyp dives into suspicious water that cannot be hurt and comes back`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            run.keepOnly(deps, null, spinolyps = true)
            val slot = run.spinolyps.first()
            val npc = checkNotNull(slot.npc)
            deps.dks.dive(npc, slot)
            assertTrue(DkSpinolyps.isSubmerged(npc))
            val before = npc.hitpoints
            npc.queueHit(alice, 1, HitType.Ranged, 30)
            advance(ticks = 3)
            assertEquals(before, npc.hitpoints, "suspicious water takes no damage")

            until("it comes back up", alice, bob, timeout = 40) { !DkSpinolyps.isSubmerged(npc) }
            assertEquals(dk_npcs.spinolyp_a.id, npc.visType.id)
            npc.queueHit(alice, 1, HitType.Ranged, 30)
            advance(ticks = 3)
            assertEquals(before - 30, npc.hitpoints, "the spinolyp can be hurt again")
        }

    @Test
    fun GameTestState.`a killed spinolyp is back after a short while`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            val slot = run.spinolyps.first()
            val npc = checkNotNull(slot.npc)
            npc.hitpoints = 0
            npc.queueDeath()
            until("it is back", alice, bob, timeout = 80) { slot.npc != null && slot.npc !== npc }
        }

    // Deaths and tasks ------------------------------------------------------------------------

    @Test
    fun GameTestState.`a dead king drops loot at its middle and comes back after its respawn`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            val rex = run.king(DkKing.Rex)
            val table = DropTable("test", always = listOf(DropStack(objTypes[objs.bones], 1, 1)))
            deps.dropTables.replaceAll(mapOf(dk_npcs.rex.id to table))
            rex.heroPoints(alice, 5)
            val centre = rex.coords.translate(1, 1)
            rex.hitpoints = 0
            rex.queueDeath()
            until("the loot lands", alice, bob, timeout = 30) {
                objRepo.findAll(centre).toList().isNotEmpty()
            }
            val slot = run.kings.getValue(DkKing.Rex)
            assertNull(slot.npc)
            val respawn = deps.npcs.respawnSpeed.scale(DkKing.RESPAWN_TICKS)
            until("Rex is back", alice, bob, timeout = respawn + 20) { slot.npc != null }
            assertTrue(run.instance.contains(run.king(DkKing.Rex).coords))
            assertNotNull(run.kings.getValue(DkKing.Prime).npc, "the others were never gone")
        }

    @Test
    fun GameTestState.`each-king tasks complete once the lowest king reaches the goal`() =
        runDkTest { deps, alice, bob ->
            repeat(50) { deps.tasks.kill(alice, DkKing.Rex) }
            repeat(50) { deps.tasks.kill(bob, DkKing.Prime) }
            repeat(49) { deps.tasks.kill(alice, DkKing.Supreme) }
            assertFalse(deps.tracker.isComplete(alice, EACH_50))
            deps.tasks.kill(bob, DkKing.Supreme)
            assertTrue(deps.tracker.isComplete(alice, EACH_50), "the team's kills add up")
            assertTrue(deps.tracker.isComplete(bob, EACH_50))
            assertFalse(deps.tracker.isComplete(alice, EACH_150))
        }

    @Test
    fun GameTestState.`all three kings in one visit completes its task, and leaving starts over`() =
        runDkTest { deps, alice, bob ->
            val run = runWithKings(deps, alice, bob)
            fun kill(king: DkKing, by: Player) {
                val npc = run.king(king)
                npc.heroPoints(by, 5)
                deps.dks.kingDied(npc)
                deps.npcs.del(npc, Int.MAX_VALUE)
                deps.tasks.kill(by, king)
            }
            kill(DkKing.Rex, alice)
            kill(DkKing.Prime, alice)
            deps.dks.leave(alice)
            deps.dks.enter(alice)
            kill(DkKing.Supreme, alice)
            assertEquals(setOf(DkKing.Supreme), deps.dks.killsThisVisit(alice))
            assertFalse(deps.tracker.isComplete(alice, WITHOUT_LEAVING), "she left in between")

            run.kings.values.forEach { it.respawnAt = 0 }
            until("the kings are back", alice, bob, timeout = 5) {
                run.kings.values.all { it.npc != null }
            }
            kill(DkKing.Rex, bob)
            kill(DkKing.Prime, bob)
            kill(DkKing.Supreme, bob)
            assertTrue(deps.tracker.isComplete(bob, WITHOUT_LEAVING))
            assertTrue(deps.tracker.isComplete(alice, WITHOUT_LEAVING), "it counts for the team")
        }

    @Test
    fun GameTestState.`wearing each ring completes its task and all four complete every ring`() =
        runDkTest(enter = false) { deps, alice, _ ->
            fun wear(internal: String) {
                val type = checkNotNull(findObjType { it.internalName == internal }.firstOrNull())
                alice.worn[Wearpos.Ring.slot] = InvObj(type)
                eventBus.publish(HeldEquipEvents.WearposChange(alice, Wearpos.Ring, type))
            }
            wear("berzerker_ring")
            assertTrue(deps.tracker.isComplete(alice, 599))
            wear("nzone_seer_ring")
            assertTrue(deps.tracker.isComplete(alice, 604), "an imbued ring counts")
            wear("warrior_ring")
            assertFalse(deps.tracker.isComplete(alice, EVERY_RING))
            wear("sw_ranger_ring")
            assertTrue(deps.tracker.isComplete(alice, 601))
            assertTrue(deps.tracker.isComplete(alice, 607))
            assertTrue(deps.tracker.isComplete(alice, EVERY_RING))
        }

    private companion object {
        const val WITHOUT_LEAVING = 596
        const val EVERY_RING = 597
        const val EACH_50 = 613
        const val EACH_150 = 614
    }
}
