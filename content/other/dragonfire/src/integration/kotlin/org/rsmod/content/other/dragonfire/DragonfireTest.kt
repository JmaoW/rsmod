package org.rsmod.content.other.dragonfire

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.lefthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.scope.TestCombatMasteries
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.content.skills.herblore.scripts.AntifireLevel
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class DragonfireTest {
    private object DragonModule : AbstractModule() {
        override fun configure() {
            // The accuracy formulas read weapon styles that WeaponAttackStylesScript loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(val interactions: AiPlayerInteractions, val potions: PotionEffects)

    private fun GameTestState.runDragonTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            DragonModule,
            DragonScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            body(deps)
        }

    /** Wears the shield at [index] of [DragonObjs.shields]. */
    private fun GameTestScope.wear(index: Int = 0) {
        player.lefthand = InvObj(objTypes[DragonObjs.shields[index]])
    }

    private fun GameTestScope.drink(deps: Deps, effect: TimedEffect) {
        deps.potions.start(player, effect, 600)
    }

    /**
     * Puts [dragon] next to the player, or [far] tiles further away (then it can only breathe), and
     * has it fight them for [ticks]. The player's Hitpoints are reset every tick so they cannot
     * die. [each] runs before every tick. Returns the damage of each hit.
     */
    private fun GameTestScope.fightFor(
        deps: Deps,
        dragon: NpcType,
        ticks: Int,
        far: Int = 0,
        each: GameTestScope.(Int, CoordGrid) -> Unit = { _, _ -> },
    ): List<Int> {
        val coords = CoordGrid(2750, 9400, 0)
        val size = npcTypes[dragon].size
        for (dx in 0..(size + far + 16) step 8) {
            allocZoneCollision(coords.translateX(dx))
        }
        val stand = coords.translateX(size + far)
        player.placeAt(stand)
        advance(ticks = 3)
        val npc = spawnNpc(coords, npcTypes[dragon])
        player.stats[stats.hitpoints] = 99
        if (far > 0) {
            npc.apPlayer2(player, deps.interactions)
        } else {
            npc.opPlayer2(player, deps.interactions)
        }

        val hits = ArrayList<Int>()
        repeat(ticks) { tick ->
            each(tick, stand)
            advance()
            val taken = 99 - player.stats[stats.hitpoints]
            if (taken > 0) hits += taken
            player.stats[stats.hitpoints] = 99
        }
        return hits
    }

    private val steel = DragonNpcs.metallic.first { it.internalNameValue == "steel_dragon" }
    private val green = DragonNpcs.chromatic.first { it.internalNameValue == "green_dragon" }
    private val drake = DragonNpcs.drake.single()
    private val king = DragonNpcs.king_black_dragon.single()

    @Test
    fun GameTestState.`an unprotected player is hurt far beyond a bite by a green dragon`() =
        runDragonTest { deps ->
            // A green dragon bites for up to 8 and breathes for up to 50, one attack in six.
            val hits = fightFor(deps, green, ticks = 1200)
            assertTrue(hits.isNotEmpty(), "the dragon attacks")
            assertTrue(hits.max() > 12, "some breath is stronger than a bite: ${hits.sorted()}")
            assertTrue(hits.max() <= 50, "no breath tops 50: ${hits.max()}")
        }

    @Test
    fun GameTestState
        .`the damage reduction mastery cannot keep a player on 1 hitpoint alive from breath`() =
        runDragonTest { deps ->
            TestCombatMasteries.set(player, MasteryStyle.Melee, 4)
            // Hitpoints are put back to 1 before every tick; a tick that ends with the player at 0
            // reads as 99 taken. A steel dragon three tiles off can only breathe.
            val hits =
                fightFor(deps, steel, ticks = 600, far = 3) { _, _ ->
                    player.stats[stats.hitpoints] = 1
                }
            assertTrue(hits.any { it == 99 }, "the player dies: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`a shield and an antifire potion stop a green dragon's breath completely`() =
        runDragonTest { deps ->
            wear()
            drink(deps, TimedEffect.Antifire)
            val hits = fightFor(deps, green, ticks = 1200)
            assertTrue(hits.isNotEmpty(), "the dragon still bites")
            assertTrue(hits.all { it <= 12 }, "only bites get through: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`super antifire alone stops a green dragon's breath`() =
        runDragonTest { deps ->
            drink(deps, TimedEffect.SuperAntifire)
            val hits = fightFor(deps, green, ticks = 1200)
            assertTrue(hits.all { it <= 12 }, "only bites get through: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`protect from magic cuts a green dragon's breath to 10`() =
        runDragonTest { deps ->
            player.setVarBit(varbits.protect_from_magic, 1)
            val hits = fightFor(deps, green, ticks = 1200)
            assertTrue(hits.all { it <= 12 }, "a bite is 8 and a breath 10: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`a metal dragon breathes from a distance`() = runDragonTest { deps ->
        val hits = fightFor(deps, steel, ticks = 600, far = 3)
        assertTrue(hits.isNotEmpty(), "the dragon attacks from afar")
        assertTrue(hits.max() > 24, "breath tops a steel dragon's bite: ${hits.sorted()}")
        assertTrue(hits.max() <= 50, "no breath tops 50: ${hits.max()}")
    }

    @Test
    fun GameTestState.`protect from magic does nothing against a metal dragon`() =
        runDragonTest { deps ->
            player.setVarBit(varbits.protect_from_magic, 1)
            val hits = fightFor(deps, steel, ticks = 600, far = 3)
            assertTrue(hits.max() > 24, "breath still tops 24: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`a shield and super antifire make a metal dragon harmless at range`() =
        runDragonTest { deps ->
            wear()
            drink(deps, TimedEffect.ExtendedSuperAntifire)
            val hits = fightFor(deps, steel, ticks = 600, far = 3)
            assertTrue(hits.isEmpty(), "nothing hurts: $hits")
        }

    @Test
    fun GameTestState.`a shield alone drops a metal dragon's breath to 5`() =
        runDragonTest { deps ->
            wear()
            val hits = fightFor(deps, steel, ticks = 600, far = 3)
            assertTrue(hits.isNotEmpty(), "a breath of up to 5 still hurts")
            assertTrue(hits.max() <= 5, "no more than 5: ${hits.sorted()}")
        }

    private fun GameTestState.shieldBlocks(index: Int) = runDragonTest { deps ->
        wear(index)
        val hits = fightFor(deps, steel, ticks = 300, far = 3)
        assertTrue(hits.all { it <= 5 }, "${DragonObjs.shields[index].internalNameValue}: $hits")
    }

    @Test fun GameTestState.`an anti-dragon shield counts`() = shieldBlocks(0)

    @Test fun GameTestState.`a dragonfire shield counts`() = shieldBlocks(1)

    @Test fun GameTestState.`an uncharged dragonfire shield counts`() = shieldBlocks(2)

    @Test fun GameTestState.`a dragonfire ward counts`() = shieldBlocks(3)

    @Test fun GameTestState.`an ancient wyvern shield counts`() = shieldBlocks(5)

    @Test
    fun GameTestState.`a drake shoots arrows up to 15`() = runDragonTest { deps ->
        val hits = fightFor(deps, drake, ticks = 300, far = 3)
        assertTrue(hits.isNotEmpty(), "the drake attacks")
        assertTrue(hits.max() <= 15, "arrows top out at 15: ${hits.sorted()}")
    }

    @Test
    fun GameTestState.`protect from missiles stops a drake's arrows but not its breath`() =
        runDragonTest { deps ->
            player.setVarBit(varbits.protect_from_missiles, 1)
            val hits = fightFor(deps, drake, ticks = 600, far = 3)
            assertTrue(hits.isNotEmpty(), "the breath still burns")
            assertTrue(hits.all { it in 6..8 }, "only breath hits, for 6 to 8: ${hits.sorted()}")
        }

    @Test
    fun GameTestState.`a shield lowers a drake's breath to 4`() = runDragonTest { deps ->
        player.setVarBit(varbits.protect_from_missiles, 1)
        wear()
        val hits = fightFor(deps, drake, ticks = 600, far = 3)
        assertTrue(hits.all { it <= 4 }, "no more than 4: ${hits.sorted()}")
    }

    @Test
    fun GameTestState.`super antifire stops a drake's breath`() = runDragonTest { deps ->
        player.setVarBit(varbits.protect_from_missiles, 1)
        drink(deps, TimedEffect.SuperAntifire)
        val hits = fightFor(deps, drake, ticks = 600, far = 3)
        assertTrue(hits.isEmpty(), "nothing hurts: $hits")
    }

    @Test
    fun GameTestState.`the drake's breath misses a player who moves off the tile`() =
        runDragonTest { deps ->
            player.setVarBit(varbits.protect_from_missiles, 1)
            // The player steps to a new tile every tick, so none is where the breath was aimed.
            val hits =
                fightFor(deps, drake, ticks = 600, far = 3) { tick, stand ->
                    player.placeAt(stand.translateX(tick % 8))
                }
            assertTrue(hits.isEmpty(), "every breath is dodged: $hits")
        }

    @Test
    fun GameTestState.`the king black dragon's breath is capped at 65`() = runDragonTest { deps ->
        val hits = fightFor(deps, king, ticks = 600, far = 3)
        assertTrue(hits.isNotEmpty(), "the king breathes fire")
        assertTrue(hits.max() <= 65, "no breath tops 65: ${hits.sorted()}")
    }

    @Test
    fun GameTestState.`potions record how well they protect`() = runDragonTest { deps ->
        assertEquals(AntifireLevel.None, deps.potions.antifire(player))
        drink(deps, TimedEffect.ExtendedAntifire)
        assertEquals(AntifireLevel.Regular, deps.potions.antifire(player))
        drink(deps, TimedEffect.SuperAntifire)
        assertEquals(AntifireLevel.Super, deps.potions.antifire(player))
    }
}
