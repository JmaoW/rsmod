package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.content.other.special.attacks.support.DotKind
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.skills.herblore.configs.PoisonCure
import org.rsmod.content.skills.herblore.configs.VenomCure
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.content.skills.herblore.scripts.SicknessTreatment
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

/**
 * A player poisoned by another player (a poisoned weapon, an emerald bolt, a Smoke spell) is
 * poisoned like one poisoned by an npc: the same poison, orb, antipoison and cure.
 */
class PvpPoisonTest {
    class Deps
    @Inject
    constructor(
        val dots: SpecialDots,
        val poison: PlayerPoison,
        val potions: PotionEffects,
        val sickness: SicknessTreatment,
        val venom: PlayerVenom,
    )

    private object PvpPoisonModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            // The game cycle and the damage over time must queue on the same world queue.
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            bind(PlayerPoison::class.java).`in`(Scopes.SINGLETON)
        }
    }

    /** alice poisons bob. */
    private fun GameTestState.runPvpPoisonTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            PvpPoisonModule,
            SicknessScript::class,
            PlayerHitScript::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            alice.stats[stats.hitpoints] = 99
            bob.stats[stats.hitpoints] = 99
            deps.poison.cure(bob)
            deps.venom.cure(bob)
            try {
                body(deps, alice, bob)
            } finally {
                deps.poison.cure(bob)
                deps.venom.cure(bob)
            }
        }

    @Test
    fun GameTestState.`a player's poison is the player's own poison, orb and all`() =
        runPvpPoisonTest { deps, alice, bob ->
            assertTrue(deps.dots.poison(bob, alice, startDamage = 4))
            assertTrue(deps.poison.isPoisoned(bob))
            assertEquals(4, deps.poison.damageOf(bob))
            assertEquals(4, bob.vars[PoisonVars.orb], "the poison orb shows")
            assertFalse(deps.dots.has(bob, DotKind.Poison), "not a special attack dot")
        }

    @Test
    fun GameTestState.`poison from a player hurts like poison from an npc`() =
        runPvpPoisonTest { deps, alice, bob ->
            assertTrue(deps.dots.poison(bob, alice, startDamage = 4))
            val before = bob.stat(stats.hitpoints)
            assertTrue(deps.poison.tick(bob))
            advance(ticks = 3)
            assertEquals(before - 4, bob.stat(stats.hitpoints))
            // After five hits at 4 the damage drops to 3, as an npc's poison does.
            repeat(4) { deps.poison.tick(bob) }
            assertEquals(3, deps.poison.damageOf(bob))
        }

    @Test
    fun GameTestState.`antipoison protects from a player's poison`() =
        runPvpPoisonTest { deps, alice, bob ->
            deps.sickness.treat(bob, PoisonCure.Cure, VenomCure.None, poisonTicks = 150)
            assertTrue(deps.potions.hasPoisonProtection(bob))
            assertFalse(deps.dots.poison(bob, alice, startDamage = 4))
            assertFalse(deps.poison.isPoisoned(bob))
        }

    @Test
    fun GameTestState.`antipoison cures a player's poison`() =
        runPvpPoisonTest { deps, alice, bob ->
            assertTrue(deps.dots.poison(bob, alice, startDamage = 5))
            deps.sickness.treat(bob, PoisonCure.Cure, VenomCure.None, poisonTicks = 150)
            assertFalse(deps.poison.isPoisoned(bob))
            assertEquals(0, bob.vars[PoisonVars.orb])
        }

    @Test
    fun GameTestState.`a second dose of poison changes nothing`() =
        runPvpPoisonTest { deps, alice, bob ->
            assertTrue(deps.dots.poison(bob, alice, startDamage = 2))
            assertFalse(deps.dots.poison(bob, alice, startDamage = 6))
            assertEquals(2, deps.poison.damageOf(bob))
        }

    @Test
    fun GameTestState.`venom replaces poison, and nothing poisons the envenomed`() =
        runPvpPoisonTest { deps, alice, bob ->
            assertTrue(deps.dots.poison(bob, alice, startDamage = 5))
            assertTrue(deps.venom.envenom(bob))
            assertFalse(deps.poison.isPoisoned(bob), "the poison is gone")
            assertTrue(deps.venom.isVenomed(bob))
            assertFalse(deps.poison.poison(bob, 6), "no poison on top of venom")
            assertFalse(deps.poison.isPoisoned(bob))
        }

    @Test
    fun GameTestState.`a full cure of both, as ambrosia gives, cures venom`() =
        runPvpPoisonTest { deps, _, bob ->
            assertTrue(deps.venom.envenom(bob))
            deps.sickness.treat(bob, PoisonCure.Cure, VenomCure.Cure, poisonTicks = 1200)
            assertFalse(deps.venom.isVenomed(bob))
            assertFalse(deps.poison.isPoisoned(bob), "and does not leave poison behind")
        }
}
