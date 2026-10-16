package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

internal object ZamorakTestNpcs : NpcReferences() {
    val kril = find("godwars_zamorak_avatar")
    val tstanon = find("godwars_ancient_greater_demon")
    val zakln = find("godwars_ancient_lesser_demon")
    val balfrug = find("godwars_ancient_black_demon")
}

class DungeonGodZamorakTest {
    class Deps
    @Inject
    constructor(
        val script: GodWarsZamorakScript,
        val poison: PlayerPoison,
        val registry: NpcRegistry,
    )

    private object ZamorakModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsZamorakScript::class.java).`in`(Scopes.SINGLETON)
            bind(PlayerPoison::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private lateinit var registry: NpcRegistry
    private lateinit var script: GodWarsZamorakScript
    private lateinit var poison: PlayerPoison
    private val spawned = mutableListOf<Npc>()
    private val extraPlayers = mutableListOf<Player>()

    /** The player and the world are shared by every test, so each test cleans up after itself. */
    private fun GameTestState.runZamorakTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ZamorakModule,
            GodItemsScript::class,
            GodWarsZamorakScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            registry = deps.registry
            script = deps.script
            poison = deps.poison
            pray()
            poison.cure(player)
            player.worn[Wearpos.LeftHand.slot] = null
            try {
                body()
            } finally {
                spawned.forEach { registry.del(it) }
                spawned.clear()
                extraPlayers.forEach { unregisterPlayer(it) }
                extraPlayers.clear()
                poison.cure(player)
                player.worn[Wearpos.LeftHand.slot] = null
            }
        }

    // K'ril's spawn: he fills 2925-2929 by 5322-5326.
    private val krilAt = CoordGrid(2925, 5322, 2)
    private val besideKril = CoordGrid(2924, 5324, 2)
    private val nearWest = CoordGrid(2920, 5320, 2)
    private val northEast = CoordGrid(2936, 5331, 2)
    private val southWest = CoordGrid(2918, 5318, 2)

    private fun GameTestScope.allocChamber() {
        for (x in listOf(2912, 2920, 2928)) {
            for (z in listOf(5312, 5320, 5328)) {
                allocZoneCollision(CoordGrid(x, z, 2))
            }
        }
    }

    private fun GameTestScope.spawn(type: NpcType, coords: CoordGrid): Npc {
        allocChamber()
        return spawnNpc(coords, npcTypes[type]).also {
            spawned += it
            it.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun isActive(): Boolean = true
                }
        }
    }

    /** Turns exactly [varbit] on for [who]; every other protection prayer is off. */
    private fun GameTestScope.pray(vararg varbit: VarBitType, who: Player = player) {
        for (each in
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )) {
            who.setVarBit(each, 0)
        }
        for (each in varbit) {
            who.setVarBit(each, 1)
        }
    }

    /** Every hit the player took, one entry per tick that cost hitpoints. */
    private fun GameTestScope.fight(
        type: NpcType,
        at: CoordGrid,
        stand: CoordGrid,
        ticks: Int = 800,
        prayer: Int = 99,
        onTick: () -> Unit = {},
    ): List<Int> {
        allocChamber()
        player.placeAt(stand)
        val npc = spawn(type, at)
        player.stats[stats.hitpoints] = 99
        player.stats[stats.prayer] = prayer
        val hits = mutableListOf<Int>()
        repeat(ticks) {
            advance()
            val taken = 99 - player.stats[stats.hitpoints]
            if (taken > 0) hits += taken
            player.stats[stats.hitpoints] = 99
            onTick()
        }
        registry.del(npc)
        spawned -= npc
        return hits
    }

    @Test
    fun GameTestState.`k'ril and tstanon hunt in melee and the other demons from range`() =
        runZamorakTest {
            fun type(name: String) = npcTypes.values.first { it.internalName == name }
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.KRIL).huntMode)
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.TSTANON).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.ZAKLN).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.BALFRUG).huntMode)
            assertEquals(8, type(GodWarsNames.BALFRUG).attackRange)
        }

    @Test
    fun GameTestState.`with no prayer k'ril's hits never pass 46`() = runZamorakTest {
        val hits = fight(ZamorakTestNpcs.kril, krilAt, besideKril)
        assertTrue(hits.isNotEmpty())
        assertTrue(
            hits.all { it <= GodWarsZamorakScript.KRIL_MELEE_MAX },
            "no smash without the prayer: $hits",
        )
    }

    @Test
    fun GameTestState.`protect from melee leaves his magic and the prayer smash`() =
        runZamorakTest {
            pray(varbits.protect_from_melee)
            // Poison from the blocked swipes is not what this test counts.
            val hits =
                fight(ZamorakTestNpcs.kril, krilAt, besideKril, onTick = { poison.cure(player) })
            assertTrue(hits.all { it in 10..30 || it in 35..49 }, "magic or smash only: $hits")
            assertTrue(hits.any { it in 10..30 }, "his magic hurts: $hits")
            assertTrue(hits.any { it in 35..49 }, "the smash comes through the prayer: $hits")
        }

    @Test
    fun GameTestState
        .`melee and magic prayers together leave only the smash, which drains prayer`() =
        runZamorakTest {
            pray(varbits.protect_from_melee, varbits.protect_from_magic)
            val lowest = intArrayOf(99)
            val hits =
                fight(ZamorakTestNpcs.kril, krilAt, besideKril) {
                    lowest[0] = minOf(lowest[0], player.stats[stats.prayer])
                    // Poison from the blocked swipes is not what this test counts.
                    poison.cure(player)
                }
            assertTrue(hits.isNotEmpty(), "the smash gets through")
            assertTrue(hits.all { it in 35..49 }, "only the smash: $hits")
            assertTrue(lowest[0] < 99, "the smash drains prayer points")
        }

    @Test
    fun GameTestState.`protect from magic leaves his melee which passes 30`() = runZamorakTest {
        pray(varbits.protect_from_magic)
        val hits = fight(ZamorakTestNpcs.kril, krilAt, besideKril)
        assertTrue(hits.all { it <= GodWarsZamorakScript.KRIL_MELEE_MAX }, "no smash: $hits")
        assertTrue(hits.any { it > 30 }, "the swipe goes past the magic's 30: $hits")
    }

    @Test
    fun GameTestState
        .`the smash takes half the prayer points and a quarter with a spectral shield`() =
        runZamorakTest {
            player.stats[stats.prayer] = 51
            assertEquals(25, script.prayerDrain(player))
            val shield =
                objTypes.values.first { it.internalName == GodWarsZamorakScript.SPECTRAL_SHIELD }
            player.worn[Wearpos.LeftHand.slot] = InvObj(shield)
            assertEquals(12, script.prayerDrain(player))
            player.stats[stats.prayer] = 0
            assertEquals(0, script.prayerDrain(player))
        }

    @Test
    fun GameTestState.`the smash only replaces a swipe against a player praying melee`() =
        runZamorakTest {
            pray(varbits.protect_from_missiles, varbits.protect_from_magic)
            assertTrue((1..2000).none { script.smashes(player) }, "never without the melee prayer")
            pray(varbits.protect_from_melee)
            val smashes = (1..9000).count { script.smashes(player) }
            assertTrue(smashes in 700..1300, "about 1 in 9: $smashes of 9000")
        }

    @Test
    fun GameTestState.`a landed swipe can poison even through protect from melee`() =
        runZamorakTest {
            pray(varbits.protect_from_melee, varbits.protect_from_magic)
            var poisoned = false
            // The smash is the only damage; the poison comes from the swipes the prayer blocks.
            fight(ZamorakTestNpcs.kril, krilAt, besideKril, ticks = 800) {
                if (poison.isPoisoned(player)) poisoned = true
            }
            assertTrue(poisoned, "blocked swipes still poison")
            assertEquals(16, GodWarsZamorakScript.KRIL_POISON)
        }

    @Test
    fun GameTestState.`poison hurts every thirty ticks and weakens by one every five hits`() =
        runZamorakTest {
            assertTrue(poison.poison(player, 16))
            assertFalse(poison.poison(player, 20), "a second dose changes nothing")
            assertEquals(16, poison.damageOf(player))
            repeat(4) { assertTrue(poison.tick(player)) }
            assertEquals(16, poison.damageOf(player), "four hits in")
            assertTrue(poison.tick(player))
            assertEquals(15, poison.damageOf(player), "the fifth hit weakens it")
            var ticks = 5
            while (poison.tick(player)) ticks++
            assertEquals(16 * 5, ticks + 1, "sixteen steps of five hits")
            assertFalse(poison.isPoisoned(player))
        }

    @Test
    fun GameTestState.`tstanon fights in melee`() = runZamorakTest {
        pray(varbits.protect_from_melee)
        assertTrue(fight(ZamorakTestNpcs.tstanon, krilAt, nearWest, ticks = 400).isEmpty())
        pray(varbits.protect_from_missiles, varbits.protect_from_magic)
        assertTrue(fight(ZamorakTestNpcs.tstanon, krilAt, nearWest, ticks = 400).isNotEmpty())
    }

    @Test
    fun GameTestState.`zakl'n throws snowballs and balfrug casts fireballs`() = runZamorakTest {
        pray(varbits.protect_from_missiles)
        assertTrue(fight(ZamorakTestNpcs.zakln, krilAt, nearWest, ticks = 400).isEmpty(), "zakl'n")
        pray(varbits.protect_from_melee, varbits.protect_from_magic)
        assertTrue(
            fight(ZamorakTestNpcs.zakln, krilAt, nearWest, ticks = 400).isNotEmpty(),
            "zakl'n",
        )
        pray(varbits.protect_from_magic)
        assertTrue(
            fight(ZamorakTestNpcs.balfrug, krilAt, nearWest, ticks = 400).isEmpty(),
            "balfrug",
        )
        pray(varbits.protect_from_melee, varbits.protect_from_missiles)
        assertTrue(
            fight(ZamorakTestNpcs.balfrug, krilAt, nearWest, ticks = 400).isNotEmpty(),
            "balfrug",
        )
    }

    @Test
    fun GameTestState.`when k'ril dies each bodyguard goes for a random player in the chamber`() =
        runZamorakTest {
            allocChamber()
            val other = registerPlayer(player = Player().apply { username = "other" })
            extraPlayers += other
            pray(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )
            pray(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
                who = other,
            )
            // Both players are far from every demon, outside their hunt range.
            player.placeAt(northEast)
            other.placeAt(northEast.translateX(-1))
            val kril = spawn(ZamorakTestNpcs.kril, krilAt)
            val guards =
                listOf(ZamorakTestNpcs.tstanon, ZamorakTestNpcs.zakln, ZamorakTestNpcs.balfrug)
                    .map { spawn(it, southWest) }
            advance(ticks = 6)
            guards.forEach { assertNull(it.interaction, "idle before K'ril dies") }

            eventBus.publish(NpcDeathEvents.Killed(kril, player))
            repeat(8) {
                player.stats[stats.hitpoints] = 99
                other.stats[stats.hitpoints] = 99
                advance()
            }
            for (guard in guards) {
                val target = (guard.interaction as? InteractionPlayer)?.target
                assertNotEquals(null, target, "${guard.visType.internalName} has a target")
                assertTrue(target == player || target == other)
            }
        }

    @Test
    fun GameTestState.`k'ril and the demons heal to full when the chamber empties`() =
        runZamorakTest {
            val outside = CoordGrid(2200, 2815, 0)
            allocZoneCollision(outside)
            player.placeAt(outside)
            val kril = spawn(ZamorakTestNpcs.kril, krilAt)
            val balfrug = spawn(ZamorakTestNpcs.balfrug, southWest)
            kril.hitpoints = 10
            balfrug.hitpoints = 5
            advance(ticks = GodWarsNames.RESET_TIMER * 3)
            assertEquals(kril.baseHitpointsLvl, kril.hitpoints, "k'ril")
            assertEquals(balfrug.baseHitpointsLvl, balfrug.hitpoints, "balfrug")
        }
}
