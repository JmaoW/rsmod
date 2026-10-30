package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.scope.TestCombatMasteries
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Hunllef: its form, the attack cycle, the protection, the max hits by armour and prayer, the
 * tornadoes, the floor and winning. The fight is driven a tick at a time through [Gauntlet.tick].
 */
class GauntletHunllefTest {
    private object HunllefModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(Gauntlet::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val gauntlet: Gauntlet,
        val teams: LeagueTeams,
        val collision: CollisionFlagMap,
        val hunllef: GauntletHunllef,
        val locRepo: LocRepository,
        val locTypes: LocTypeList,
    )

    private fun GameTestScope.templates(deps: Deps) {
        val collision = deps.collision
        val origins = ArrayList<Pair<Int, Int>>()
        for (offset in listOf(0, 8)) {
            for (chunkX in listOf(232, 234, 236)) {
                for (chunkZ in listOf(704, 706, 708, 710)) {
                    origins += (chunkX + offset) to chunkZ
                }
            }
            origins += (238 + offset) to 708
            origins += (238 + offset) to 710
        }
        for ((chunkX, chunkZ) in origins) {
            for (level in 0..3) {
                for (x in 0 until 2) {
                    for (z in 0 until 2) {
                        collision.allocateIfAbsent((chunkX + x) * 8, (chunkZ + z) * 8, level)
                    }
                }
            }
        }
        // The floor of the Hunllef's arena: one plain floor tile on each of its 144 tiles.
        for (offset in listOf(0, 8)) {
            for (x in 2..13) {
                for (z in 2..13) {
                    val at = org.rsmod.map.CoordGrid((238 + offset) * 8 + x, 710 * 8 + z, 1)
                    placeMapLoc(
                        at,
                        if (offset == 0) gauntlet_locs.floor else gauntlet_locs.floor_hm,
                    )
                }
            }
        }
        collision.allocateIfAbsent(Gauntlet.LOBBY.x, Gauntlet.LOBBY.z, Gauntlet.LOBBY.level)
    }

    /** alice and bob are teammates and are briefed. */
    private fun GameTestState.runHunllefTest(
        pick: (Int) -> Int = { 0 },
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            HunllefModule,
            GauntletScript::class,
            GauntletCombatScript::class,
            GauntletHunllefScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
        ) { deps ->
            templates(deps)
            deps.gauntlet.pick = pick
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            for (p in listOf(alice, bob)) {
                VarPlayerIntMapSetter.set(p, gauntlet_varps.bryn, 1)
                p.stats[stats.hitpoints] = 99
            }
            body(deps, alice, bob)
        }

    /** Both players in the maze, the fight begun and the players apart from the Hunllef. */
    private fun GameTestScope.startFight(deps: Deps, alice: Player, bob: Player): GauntletRun {
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        deps.gauntlet.enter(bob, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        deps.gauntlet.startBossPhase(run, forced = false)
        // Stand them in the far corner, out from under it.
        alice.coords = run.roomTile(run.layout.boss, 3, 3)
        bob.coords = run.roomTile(run.layout.boss, 4, 3)
        return run
    }

    private fun GameTestScope.fight(run: GauntletRun) = checkNotNull(run.hunllef)

    private fun GameTestScope.wearFullSet(player: Player, items: GauntletItemSet, tier: Int) {
        player.worn[Wearpos.Hat.slot] = InvObj(items.helm[tier])
        player.worn[Wearpos.Torso.slot] = InvObj(items.body[tier])
        player.worn[Wearpos.Legs.slot] = InvObj(items.legs[tier])
    }

    // The set-up --------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the Hunllef waits in its room from the start of the run`() =
        runHunllefTest { deps, alice, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val fight = fight(run)

            assertEquals(run.roomTile(run.layout.boss, 6, 7), fight.npc.coords)
            assertFalse(fight.started)
            assertEquals(
                deps.hunllef.form(GauntletMode.Regular, fight.protecting).id,
                fight.npc.type.id,
            )
            assertEquals(fight.maxHitpoints, fight.npc.hitpoints)

            deps.gauntlet.startBossPhase(run, forced = false)
            assertTrue(fight.started)
        }

    @Test
    fun GameTestState.`the corrupted Hunllef is the corrupted form`() =
        runHunllefTest { deps, alice, _ ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.done, 1)
            deps.gauntlet.enter(alice, GauntletMode.Corrupted)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            assertEquals(
                deps.hunllef.form(GauntletMode.Corrupted, fight(run).protecting).id,
                fight(run).npc.type.id,
            )
            assertNotEquals(
                deps.hunllef.form(GauntletMode.Regular, fight(run).protecting).id,
                fight(run).npc.type.id,
            )
        }

    // The attack cycle --------------------------------------------------------------------------

    @Test
    fun GameTestState.`it attacks every five ticks and changes style every four attacks`() =
        runHunllefTest { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)
            // Keep the players alive and apart from it, so only ordinary attacks happen.
            val styles = ArrayList<HunllefStyle>()
            var seen = 0
            repeat(120) {
                advance()
                alice.stats[stats.hitpoints] = 99
                bob.stats[stats.hitpoints] = 99
                fight.nextTornado = Int.MAX_VALUE
                fight.nextPattern = Int.MAX_VALUE
                if (fight.attacks > seen) {
                    seen = fight.attacks
                    styles += fight.style
                }
            }
            assertTrue(fight.attacks in 20..25, "${fight.attacks} attacks in 120 ticks")
            // The style a fight is in after its Nth attack: ranged x4, magic x4, ranged x4...
            assertEquals(HunllefStyle.Ranged, styles[0])
            assertEquals(HunllefStyle.Ranged, styles[2])
            assertEquals(HunllefStyle.Magic, styles[3])
            assertEquals(HunllefStyle.Magic, styles[6])
            assertEquals(HunllefStyle.Ranged, styles[7])
        }

    @Test
    fun GameTestState.`a duo take it in turns to be attacked`() =
        runHunllefTest { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)
            fight.nextTornado = Int.MAX_VALUE
            fight.nextPattern = Int.MAX_VALUE
            val order = ArrayList<Int>()
            var seen = 0
            repeat(60) {
                advance()
                alice.stats[stats.hitpoints] = 99
                bob.stats[stats.hitpoints] = 99
                if (fight.attacks > seen) {
                    seen = fight.attacks
                    order += fight.lastTarget
                }
            }
            assertTrue(order.size >= 8)
            assertEquals(setOf(alice.accountId, bob.accountId), order.toSet())
            for (i in 1 until order.size) {
                assertNotEquals(order[i - 1], order[i], "the same player twice in a row")
            }
        }

    @Test
    fun GameTestState.`a player under it is trampled instead`() =
        runHunllefTest(pick = { n -> n - 1 }) { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)
            fight.nextTornado = Int.MAX_VALUE
            fight.nextPattern = Int.MAX_VALUE
            // Alone with it, so nobody else is there to be attacked.
            deps.gauntlet.leave(bob, GauntletExit.Escape)
            alice.coords = fight.npc.coords
            var damaged = false
            repeat(40) {
                advance()
                if (alice.hitpoints < 99) damaged = true
                alice.stats[stats.hitpoints] = 99
                alice.coords = fight.npc.coords
            }
            assertTrue(damaged)
            assertEquals(0, fight.attacks, "a stomp is not one of the four attacks before a switch")
        }

    // Damage ------------------------------------------------------------------------------------

    private fun GameTestScope.biggestHit(
        deps: Deps,
        alice: Player,
        bob: Player,
        ticks: Int,
        prayer: Boolean,
        tier: Int,
    ): Int {
        val run = startFight(deps, alice, bob)
        val fight = fight(run)
        fight.nextTornado = Int.MAX_VALUE
        fight.nextPattern = Int.MAX_VALUE
        // Both players take the same stand.
        for (p in listOf(alice, bob)) {
            if (tier > 0) wearFullSet(p, GauntletRegularItems, tier - 1)
            VarPlayerIntMapSetter.set(p, varbits.protect_from_missiles, if (prayer) 1 else 0)
            VarPlayerIntMapSetter.set(p, varbits.protect_from_magic, if (prayer) 1 else 0)
        }
        var biggest = 0
        repeat(ticks) {
            advance()
            for (p in listOf(alice, bob)) {
                biggest = maxOf(biggest, 99 - p.hitpoints)
                p.stats[stats.hitpoints] = 99
            }
        }
        return biggest
    }

    @Test
    fun GameTestState.`unprotected hits go up to 51 with no armour`() =
        runHunllefTest(pick = { n -> if (n > 3) n - 1 else 0 }) { deps, alice, bob ->
            val unprayed = biggestHit(deps, alice, bob, ticks = 80, prayer = false, tier = 0)
            assertTrue(unprayed in 13..51, "$unprayed")
        }

    @Test
    fun GameTestState.`the right prayer caps a hit at twelve with no armour`() =
        runHunllefTest(pick = { n -> if (n > 3) n - 1 else 0 }) { deps, alice, bob ->
            val prayed = biggestHit(deps, alice, bob, ticks = 80, prayer = true, tier = 0)
            assertTrue(prayed in 1..12, "$prayed")
        }

    @Test
    fun GameTestState.`perfected armour caps the hits at 26`() =
        runHunllefTest(pick = { n -> if (n > 3) n - 1 else 0 }) { deps, alice, bob ->
            val unprayed = biggestHit(deps, alice, bob, ticks = 80, prayer = false, tier = 3)
            assertTrue(unprayed in 7..26, "$unprayed")
        }

    @Test
    fun GameTestState.`the armour tier is the lowest of a full set`() =
        runHunllefTest { deps, alice, _ ->
            val items = GauntletRegularItems
            assertEquals(0, deps.hunllef.armourTier(alice, GauntletMode.Regular))
            wearFullSet(alice, items, 2)
            assertEquals(3, deps.hunllef.armourTier(alice, GauntletMode.Regular))
            alice.worn[Wearpos.Legs.slot] = InvObj(items.legs[0])
            assertEquals(1, deps.hunllef.armourTier(alice, GauntletMode.Regular))
            alice.worn[Wearpos.Hat.slot] = null
            assertEquals(0, deps.hunllef.armourTier(alice, GauntletMode.Regular))
        }

    // The protection ----------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`hits of the protected style do nothing and every sixth other hit switches it`() =
        runHunllefTest { deps, alice, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val fight = fight(run)
            val protecting = fight.protecting
            val others = HunllefStyle.entries.filter { it != protecting }

            assertTrue(deps.hunllef.onHit(run, protecting))
            assertEquals(protecting, fight.protecting)

            repeat(5) { assertFalse(deps.hunllef.onHit(run, others[0])) }
            assertEquals(protecting, fight.protecting)
            assertFalse(deps.hunllef.onHit(run, others[1]))

            // The sixth hit that was not protected against switches to that hit's style.
            assertEquals(others[1], fight.protecting)
            assertEquals(
                deps.hunllef.form(GauntletMode.Regular, others[1]).id,
                fight.npc.visType.id,
            )
            // Hits that are not of any combat style are never stopped.
            assertFalse(deps.hunllef.onHit(run, HunllefStyle.of(HitType.Typeless)))
        }

    @Test
    fun GameTestState.`the protection mastery lets part of a protected hit through`() =
        runHunllefTest { deps, alice, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val fight = fight(run)
            val style = fight.protecting

            fun damageOf(type: HitType): Int {
                val builder = hit(type, 40)
                eventBus.publish(NpcHitEvents.ModifyAny(fight.npc, builder, alice))
                return builder.damage
            }

            assertEquals(0, damageOf(style.hitType), "protected, no mastery")
            TestCombatMasteries.setAll(alice, 5)
            assertEquals(0, damageOf(style.hitType), "protected, mastery tier 5")
            TestCombatMasteries.setAll(alice, 6)
            assertEquals(24, damageOf(style.hitType), "protected, 60% penetration")
            val other = HunllefStyle.entries.first { it != style }
            assertEquals(40, damageOf(other.hitType), "an unprotected style is not reduced")
        }

    @Test
    fun GameTestState.`it shows the overhead icon of the style it protects against`() =
        runHunllefTest { deps, alice, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val fight = fight(run)
            val icons = ArrayList<Triple<Int, Int, Int>>()
            fight.npc.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun setHeadIcon(slot: Int, graphic: Int, index: Int) {
                        icons += Triple(slot, graphic, index)
                    }
                }
            val next = HunllefStyle.entries.first { it != fight.protecting }
            repeat(GauntletHunllef.OFF_PRAYER_HITS_PER_SWITCH) { deps.hunllef.onHit(run, next) }
            assertEquals(listOf(Triple(0, GauntletHunllef.HEAD_ICONS_PRAYER, next.ordinal)), icons)
        }

    private fun GameTestScope.hit(type: HitType, damage: Int): HitBuilder =
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

    // Tornadoes and the floor -------------------------------------------------------------------

    @Test
    fun GameTestState.`tornadoes come in the right number, chase and catch a player`() =
        runHunllefTest { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)
            fight.nextPattern = Int.MAX_VALUE
            fight.nextTornado = 0
            fight.nextAttack = 0

            advance(2)
            assertEquals(1, fight.tornadoes.size, "one above two thirds of its hitpoints")

            repeat(30) {
                advance()
                alice.stats[stats.hitpoints] = 99
                bob.stats[stats.hitpoints] = 99
                fight.nextAttack = Int.MAX_VALUE
            }
            assertTrue(fight.tornadoes.isEmpty(), "a tornado lasts 20 ticks")

            fight.npc.hitpoints = fight.maxHitpoints / 2
            assertEquals(1, fight.phase())
            fight.npc.hitpoints = fight.maxHitpoints / 5
            assertEquals(2, fight.phase())
        }

    @Test
    fun GameTestState.`a tornado on a player hurts them`() = runHunllefTest { deps, alice, bob ->
        val run = startFight(deps, alice, bob)
        val fight = fight(run)
        fight.nextPattern = Int.MAX_VALUE
        fight.nextTornado = 0
        fight.nextAttack = 0
        // Alone in the arena, so the tornado has one player to chase.
        deps.gauntlet.leave(bob, GauntletExit.Escape)
        advance(2)
        fight.nextAttack = Int.MAX_VALUE
        var hurt = false
        repeat(20) {
            advance()
            if (alice.hitpoints < 99) hurt = true
        }
        assertTrue(hurt, "a tornado caught the player")
    }

    @Test
    fun GameTestState.`the floor goes blue then orange and hurts whoever stands on it`() =
        runHunllefTest { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)
            fight.nextTornado = Int.MAX_VALUE
            fight.nextAttack = Int.MAX_VALUE
            fight.nextPattern = 1

            advance(2)
            assertTrue(fight.floor.isNotEmpty())
            val tile = fight.floor.values.first()
            val at = deps.hunllef.arenaTile(run, tile.x, tile.z)
            val warning = deps.locRepo.findAll(at).map { it.id }.toList()
            assertEquals(listOf(gauntlet_locs.floor_warning.id), warning)

            alice.coords = at
            repeat(12) {
                advance()
                fight.nextAttack = Int.MAX_VALUE
                fight.nextTornado = Int.MAX_VALUE
            }
            assertTrue(alice.hitpoints < 99, "the orange tile hurt")
            // Each pattern clears again.
            repeat(20) {
                advance()
                fight.nextAttack = Int.MAX_VALUE
                fight.nextPattern = Int.MAX_VALUE
            }
            assertTrue(fight.floor.isEmpty())
            assertEquals(
                listOf(gauntlet_locs.floor.id),
                deps.locRepo.findAll(at).map { it.id }.toList(),
            )
        }

    // Winning -----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`killing the Hunllef wins the run and takes the team to the lobby`() =
        runHunllefTest { deps, alice, bob ->
            val run = startFight(deps, alice, bob)
            val fight = fight(run)

            deps.gauntlet.hunllefDied(run)

            assertTrue(run.won)
            assertEquals(1, alice.vars[gauntlet_varps.done])
            assertEquals(1, bob.vars[gauntlet_varps.done])
            assertTrue(fight.tornadoes.isEmpty() && fight.floor.isEmpty())

            advance(Gauntlet.COMPLETE_DELAY + 2)
            assertEquals(Gauntlet.LOBBY, alice.coords)
            assertEquals(Gauntlet.LOBBY, bob.coords)
            assertTrue(deps.gauntlet.liveRuns().isEmpty())
            // Winning twice counts once.
            deps.gauntlet.hunllefDied(run)
            assertEquals(1, alice.vars[gauntlet_varps.done])
        }

    @Test
    fun GameTestState.`the Hunllef goes with the maze`() = runHunllefTest { deps, alice, bob ->
        val run = startFight(deps, alice, bob)
        val npc = fight(run).npc
        deps.gauntlet.leave(alice, GauntletExit.Exit)
        deps.gauntlet.leave(bob, GauntletExit.Exit)
        assertFalse(npc.isSlotAssigned)
        assertTrue(run.hunllef == null)
    }
}
