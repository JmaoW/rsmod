package org.rsmod.content.areas.misc.isleofsouls.cox

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Every room of a Challenge Mode raid (it has them all) run for a few hundred ticks with two
 * raiders inside, then its monsters killed, as the plugin scripts would drive it: nothing may
 * throw, and every encounter must end with its way on open.
 */
class CoxFightTest {
    private object CoxModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: CoxParties,
        val raids: CoxRaids,
        val rooms: CoxRoomRegistry,
        val collision: CollisionFlagMap,
    )

    @AfterEach
    fun resetSettings() {
        EventSettings.current = EventSettings()
    }

    @Test
    fun GameTestState.`every encounter runs and ends`() =
        runInjectedGameTest(
            Deps::class,
            CoxModule,
            WeaponAttackStylesScript::class,
            PlayerHitScript::class,
            CoxRoomDirector::class,
            CoxPuzzles::class,
            CoxCombatScript::class,
            CoxOlmScript::class,
            CoxResources::class,
        ) { deps ->
            for (zx in 400 until 420) {
                for (zz in 640 until 720) {
                    for (level in 0..2) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
            deps.collision.allocateIfAbsent(CoxMountain.ARRIVAL.x, CoxMountain.ARRIVAL.z, 0)
            deps.raids.fadeTicks = 0
            val alice = player
            alice.displayName = "Alice"
            alice.observerUUID = 1L
            alice.stats[stats.hitpoints] = 99
            val bob = registerPlayer(coords = CoxMountain.ARRIVAL)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            bob.stats[stats.hitpoints] = 99
            PathingEntityCommon.telejump(alice, deps.collision, CoxMountain.ARRIVAL)
            val party = deps.parties.create(alice)
            party.challenge = true
            deps.parties.join(party, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            advance()
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.start(alice)
            val hurtIn = LinkedHashSet<CoxRoomType>()
            for (room in raid.rooms) {
                val runtime = deps.rooms.runtime(raid, room) ?: continue
                val centre =
                    CoordGrid(room.type.sourceX(room.variant) + 16, room.type.sourceZ + 16, 0)
                val at = deps.raids.landing(raid, room, centre)
                deps.raids.move(alice, at)
                deps.raids.move(bob, deps.raids.nearOpen(at.translate(1, 0)))
                runtime.onEntered(alice)
                runtime.start()
                repeat(FIGHT_TICKS) {
                    advance()
                    deps.raids.tick()
                    deps.rooms.tick()
                    if (alice.stats[stats.hitpoints] < 99 || bob.stats[stats.hitpoints] < 99) {
                        hurtIn += room.type
                    }
                    alice.stats[stats.hitpoints] = 99
                    bob.stats[stats.hitpoints] = 99
                }
                // Kill what is left (bosses through the room, monsters through their deaths).
                var guard = 0
                while (!runtime.completed && guard++ < 400) {
                    for (npc in runtime.npcs.toList()) {
                        if (!npc.isSlotAssigned || npc.hitpoints <= 0) continue
                        npc.hitpoints = 0
                        runtime.hitpointsGone(npc)
                        if (runtime.owns(npc) && npc.hitpoints <= 0) runtime.died(npc)
                    }
                    advance()
                    deps.raids.tick()
                    deps.rooms.tick()
                    if (!runtime.completed && runtime.npcs.isEmpty()) runtime.complete()
                }
                assertTrue(runtime.completed, "${room.type} should end")
                assertTrue(deps.raids.isOpen(raid, room), "${room.type} should open")
            }
            // Every combat room fought back (player hits need PlayerHitScript loaded).
            val fighting = raid.rooms.map { it.type }.filter { it.kind == CoxRoomKind.Combat }
            for (type in fighting) assertTrue(type in hurtIn, "$type never hit anyone")
        }

    private companion object {
        const val FIGHT_TICKS = 200
    }
}
