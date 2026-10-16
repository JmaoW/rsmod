package org.rsmod.content.skills.slayer

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.front
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.scripts.CaveHorrorNpcs
import org.rsmod.content.skills.slayer.scripts.CaveHorrorScript
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

// The horror script is a singleton so the test and the loaded script share one instance.
@Execution(ExecutionMode.SAME_THREAD)
class CaveHorrorTest {
    private object HorrorModule : AbstractModule() {
        override fun configure() {
            // The accuracy formulas read weapon styles that WeaponAttackStylesScript loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(CaveHorrorScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(val horrors: CaveHorrorScript, val interactions: AiPlayerInteractions)

    private fun GameTestState.runHorrorTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            HorrorModule,
            CaveHorrorScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.hp(): Int = player.stats[stats.hitpoints]

    private fun GameTestScope.wearIcon() {
        player.front = InvObj(objTypes[SlayerObjs.witchwood_icon])
    }

    /** Puts a horror next to the player and has it fight them for [ticks]; returns each hit. */
    private fun GameTestScope.fightFor(deps: Deps, ticks: Int): List<Int> {
        val coords = CoordGrid(2750, 9400, 0)
        allocZoneCollision(coords)
        val stand = coords.translateX(npcTypes[CaveHorrorNpcs.alpha].size)
        allocZoneCollision(stand)
        player.placeAt(stand)
        advance(ticks = 3)
        val horror = spawnNpc(coords, npcTypes[CaveHorrorNpcs.alpha])
        player.stats[stats.hitpoints] = 99
        horror.opPlayer2(player, deps.interactions)

        val hits = ArrayList<Int>()
        repeat(ticks) {
            advance()
            val taken = 99 - hp()
            if (taken > 0) hits += taken
            player.stats[stats.hitpoints] = 99
        }
        return hits
    }

    @Test
    fun GameTestState.`the scream is a tenth of the player's base Hitpoints, rounded down`() =
        runHorrorTest { deps ->
            for ((level, expected) in listOf(99 to 9, 96 to 9, 50 to 5, 10 to 1, 9 to 0)) {
                player.stats[stats.hitpoints] = level
                val scream = deps.horrors.screamOf(player)
                assertEquals(expected, scream.minHit, "min at $level")
                assertEquals(expected, scream.maxHit, "max at $level")
                assertTrue(scream.alwaysHits, "never misses at $level")
            }
        }

    @Test
    fun GameTestState.`a horror always screams at a player without the icon`() =
        runHorrorTest { deps ->
            repeat(300) { assertTrue(deps.horrors.screams(player)) }
        }

    @Test
    fun GameTestState.`with the icon a horror both kicks and screams`() = runHorrorTest { deps ->
        wearIcon()
        val screams = (1..1000).count { deps.horrors.screams(player) }
        assertTrue(screams in 350..650, "about half scream, but $screams of 1000 did")
    }

    @Test
    fun GameTestState.`a kick never does more than 9`() = runHorrorTest { deps ->
        val horror = spawnNpc(CoordGrid(2750, 9400, 0), npcTypes[CaveHorrorNpcs.alpha])
        repeat(300) { assertTrue((deps.horrors.kickOf(horror, player).maxHit ?: 0) <= 9) }
    }

    @Test
    fun GameTestState.`an unprotected player takes the full scream from every hit`() =
        runHorrorTest { deps ->
            val hits = fightFor(deps, ticks = 60)
            assertTrue(hits.size >= 5, "the horror keeps attacking: $hits")
            assertTrue(hits.all { it == 9 }, "every scream does 9 at 99 Hitpoints: $hits")
        }

    @Test
    fun GameTestState.`protect from melee stops the horror's attacks`() = runHorrorTest { deps ->
        player.setVarBit(varbits.protect_from_melee, 1)
        val hits = fightFor(deps, ticks = 60)
        assertFalse(hits.any { it > 0 }, "protected from melee, but took $hits")
    }

    @Test
    fun GameTestState.`with the icon some hits are kicks that are lighter than the scream`() =
        runHorrorTest { deps ->
            wearIcon()
            val hits = fightFor(deps, ticks = 300)
            assertTrue(hits.any { it == 9 }, "still screams: $hits")
            assertTrue(hits.any { it < 9 }, "kicks as well: $hits")
        }
}
