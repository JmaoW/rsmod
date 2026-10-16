package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

internal object FollowerTestNpcs : NpcReferences() {
    val sara_ranger = find("godwars_spiritual_saradomin_ranger")
    val sara_mage = find("godwars_spiritual_saradomin_mage")
    val bloodveld = find("godwars_bloodveld")
    val wizard = find("godwars_ancient_saradomin_wizard")
    val aviansie = find("godwars_armadyl_male_armor01_blue")
}

class DungeonGodFollowerTest {
    class Deps @Inject constructor(val script: GodWarsFollowerScript, val registry: NpcRegistry)

    private val inside = CoordGrid(2882, 5310, 2)

    private object FollowerModule : AbstractModule() {
        override fun configure() {
            // The accuracy formulas read weapon styles that WeaponAttackStylesScript loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private lateinit var registry: NpcRegistry

    private fun GameTestState.runFollowerTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(
            Deps::class,
            FollowerModule,
            GodItemsScript::class,
            GodWarsFollowerScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            registry = deps.registry
            for (each in
                listOf(
                    varbits.protect_from_melee,
                    varbits.protect_from_missiles,
                    varbits.protect_from_magic,
                )) {
                player.setVarBit(each, 0)
            }
            body()
        }

    /** Spawns [type] with a viewer (an unseen npc does not hunt) at [coords]. */
    private fun GameTestScope.spawn(type: NpcType, coords: CoordGrid): Npc {
        allocZoneCollision(coords)
        return spawnNpc(coords, npcTypes[type]).also {
            it.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun isActive(): Boolean = true
                }
        }
    }

    private fun GameTestScope.hp(): Int = player.stats[stats.hitpoints]

    /**
     * Lets [type] fight a player at full health for [ticks] ticks, topping them back up each tick,
     * and says whether it hurt them at least once.
     */
    private fun GameTestScope.hurts(type: NpcType, at: CoordGrid, ticks: Int = 120): Boolean {
        allocZoneCollision(inside)
        player.placeAt(inside)
        val npc = spawn(type, at)
        player.stats[stats.hitpoints] = 99
        var hurt = false
        repeat(ticks) {
            advance()
            if (hp() < 99) hurt = true
            player.stats[stats.hitpoints] = 99
        }
        // Npcs outlive a test in the shared world and would block the next test's spawn tile.
        registry.del(npc)
        return hurt
    }

    /** Turns [varbit] on and the other prayers off: the player is shared by every test. */
    private fun GameTestScope.protect(varbit: org.rsmod.game.type.varbit.VarBitType) {
        for (each in
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )) {
            player.setVarBit(each, 0)
        }
        player.setVarBit(varbit, 1)
    }

    @Test
    fun GameTestState.`the wiki's rates and max hits are on the spiritual rangers and mages`() =
        runFollowerTest {
            val rangers =
                mapOf(
                    "zamorak" to (4 to 15),
                    "saradomin" to (4 to 16),
                    "bandos" to (4 to 13),
                    "armadyl" to (3 to 16),
                )
            for ((god, expected) in rangers) {
                val p = GodWarsFollowerScript.profileOf("godwars_spiritual_${god}_ranger", 0)
                assertEquals(expected.first, p.rate, "$god ranger rate")
                assertEquals(expected.second, p.main.maxHit, "$god ranger max hit")
                assertEquals(NpcAttackStyle.Ranged, p.main.style, "$god ranger style")
            }
            val mages =
                mapOf(
                    "zamorak" to (4 to 19),
                    "saradomin" to (4 to 20),
                    "bandos" to (5 to 17),
                    "armadyl" to (3 to 17),
                )
            for ((god, expected) in mages) {
                val p = GodWarsFollowerScript.profileOf("godwars_spiritual_${god}_mage", 0)
                assertEquals(expected.first, p.rate, "$god mage rate")
                assertEquals(expected.second, p.main.maxHit, "$god mage max hit")
                assertEquals(NpcAttackStyle.Magic, p.main.style, "$god mage style")
            }
            val warrior = GodWarsFollowerScript.profileOf("godwars_spiritual_armadyl_warrior", 0)
            assertEquals(3, warrior.rate)
            assertEquals(15, warrior.main.maxHit)
            assertEquals(NpcAttackStyle.Ranged, warrior.main.style)
        }

    @Test
    fun GameTestState.`aviansie max hits follow the wiki by combat level`() = runFollowerTest {
        assertEquals(8, GodWarsFollowerScript.aviansieMaxHit(69))
        assertEquals(12, GodWarsFollowerScript.aviansieMaxHit(89))
        assertEquals(16, GodWarsFollowerScript.aviansieMaxHit(148))
        assertEquals(10, GodWarsFollowerScript.aviansieMaxHit(80), "nearest level below")
        assertEquals(8, GodWarsFollowerScript.aviansieMaxHit(10), "below the lowest")
        assertEquals(16, GodWarsFollowerScript.aviansieMaxHit(200), "above the highest")
    }

    @Test
    fun GameTestState.`every ranged follower and the bloodveld has a profile`() = runFollowerTest {
        for (name in GodWarsNames.ranged + GodWarsNames.magicMelee) {
            val type = GodWarsRoster.byName.getValue(name)
            val profile = GodWarsFollowerScript.profileOf(name, npcTypes[type].vislevel)
            assertTrue(profile.rate in 3..5, "$name rate ${profile.rate}")
            assertTrue((profile.main.maxHit ?: 0) > 0, "$name max hit")
        }
        val bloodveld = GodWarsFollowerScript.profileOf("godwars_bloodveld", 81)
        assertEquals(NpcAttackStyle.Melee, bloodveld.main.style)
        assertTrue(bloodveld.magicalMelee)
        assertEquals(6, bloodveld.main.maxHit)
        val wizard = GodWarsFollowerScript.profileOf("godwars_ancient_saradomin_wizard", 108)
        assertEquals(NpcAttackStyle.Magic, wizard.main.style)
        assertEquals(NpcAttackStyle.Melee, wizard.close?.style)
        assertEquals(14, wizard.close?.maxHit)
    }

    // North of `inside` is walled; east, west and south have a line of sight.
    private val east = inside.translateX(5)

    @Test
    fun GameTestState.`a spiritual ranger shoots from a distance`() = runFollowerTest {
        assertTrue(hurts(FollowerTestNpcs.sara_ranger, east), "hurts without a prayer")
    }

    @Test
    fun GameTestState.`protect from missiles blocks the ranger`() = runFollowerTest {
        protect(varbits.protect_from_missiles)
        assertFalse(hurts(FollowerTestNpcs.sara_ranger, east), "missiles prayer on")
    }

    @Test
    fun GameTestState.`protect from magic does not block the ranger`() = runFollowerTest {
        protect(varbits.protect_from_magic)
        assertTrue(hurts(FollowerTestNpcs.sara_ranger, east), "wrong prayer")
    }

    @Test
    fun GameTestState.`protect from missiles does not block the spiritual mage`() =
        runFollowerTest {
            protect(varbits.protect_from_missiles)
            assertTrue(hurts(FollowerTestNpcs.sara_mage, east), "wrong prayer")
        }

    @Test
    fun GameTestState.`the right prayer stops the spiritual mage`() = runFollowerTest {
        protect(varbits.protect_from_magic)
        assertFalse(hurts(FollowerTestNpcs.sara_mage, east), "magic prayer on")
    }

    @Test
    fun GameTestState.`protect from melee blocks the bloodveld's magical melee`() =
        runFollowerTest {
            protect(varbits.protect_from_melee)
            assertFalse(hurts(FollowerTestNpcs.bloodveld, inside.translateX(2)), "melee prayer on")
        }

    @Test
    fun GameTestState.`protect from magic does not block the bloodveld`() = runFollowerTest {
        protect(varbits.protect_from_magic)
        assertTrue(hurts(FollowerTestNpcs.bloodveld, inside.translateX(2)), "magic prayer is wrong")
    }

    @Test
    fun GameTestState.`protect from missiles blocks an aviansie`() = runFollowerTest {
        protect(varbits.protect_from_missiles)
        assertFalse(hurts(FollowerTestNpcs.aviansie, east), "missiles prayer on")
    }

    @Test
    fun GameTestState.`an aviansie hurts a player with the wrong prayer`() = runFollowerTest {
        protect(varbits.protect_from_melee)
        assertTrue(hurts(FollowerTestNpcs.aviansie, east), "melee prayer is wrong")
    }

    @Test
    fun GameTestState.`a ranged follower stays at range instead of closing to melee`() =
        runFollowerTest {
            allocZoneCollision(inside)
            player.placeAt(inside)
            val ranger = spawn(FollowerTestNpcs.sara_ranger, east)
            repeat(30) {
                advance()
                player.stats[stats.hitpoints] = 99
            }
            val distance = ranger.coords.chebyshevDistance(player.coords)
            registry.del(ranger)
            assertTrue(distance in 2..GodWarsNames.ATTACK_RANGE, "stood at distance $distance")
        }
}
