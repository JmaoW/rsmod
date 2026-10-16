package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

internal object BandosTestNpcs : NpcReferences() {
    val graardor = find("godwars_bandos_avatar")
    val strongstack = find("godwars_sergeant_goblin1")
    val steelwill = find("godwars_sergeant_goblin2")
    val grimspike = find("godwars_sergeant_goblin3")
}

class DungeonGodBandosTest {
    class Deps @Inject constructor(val script: GodWarsBandosScript, val registry: NpcRegistry)

    private object BandosModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsBandosScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private lateinit var registry: NpcRegistry
    private lateinit var script: GodWarsBandosScript
    private val spawned = mutableListOf<Npc>()
    private val extraPlayers = mutableListOf<Player>()

    /** The player and the world are shared by every test, so each test cleans up after itself. */
    private fun GameTestState.runBandosTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(
            Deps::class,
            BandosModule,
            GodItemsScript::class,
            GodWarsBandosScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            registry = deps.registry
            script = deps.script
            pray()
            try {
                body()
            } finally {
                spawned.forEach { registry.del(it) }
                spawned.clear()
                extraPlayers.forEach { unregisterPlayer(it) }
                extraPlayers.clear()
                script.forgetHitter()
            }
        }

    // Graardor's spawn: he fills 2872-2875 by 5358-5361.
    private val graardorAt = CoordGrid(2872, 5358, 2)
    private val besideGraardor = CoordGrid(2871, 5359, 2)
    private val farWest = CoordGrid(2866, 5353, 2)

    private fun GameTestScope.allocChamber() {
        for (x in listOf(2864, 2872)) {
            for (z in listOf(5344, 5352, 5360, 5368)) {
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

    /** Turns exactly [varbit] on; every other protection prayer is off. */
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

    private fun GameTestScope.hp(who: Player = player): Int = who.stats[stats.hitpoints]

    /** Every hit [who] took, one entry per tick that cost hitpoints. */
    private fun GameTestScope.fight(
        type: NpcType,
        at: CoordGrid,
        stand: CoordGrid,
        ticks: Int = 400,
        who: Player = player,
    ): List<Int> {
        allocChamber()
        who.placeAt(stand)
        val npc = spawn(type, at)
        who.stats[stats.hitpoints] = 99
        val hits = mutableListOf<Int>()
        repeat(ticks) {
            advance()
            val taken = 99 - hp(who)
            if (taken > 0) hits += taken
            who.stats[stats.hitpoints] = 99
        }
        registry.del(npc)
        spawned -= npc
        return hits
    }

    @Test
    fun GameTestState
        .`graardor and strongstack hunt in melee and the other sergeants from range`() =
        runBandosTest {
            fun type(name: String) = npcTypes.values.first { it.internalName == name }
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.GRAARDOR).huntMode)
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.STRONGSTACK).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.STEELWILL).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.GRIMSPIKE).huntMode)
            assertEquals(8, type(GodWarsNames.STEELWILL).attackRange)
        }

    @Test
    fun GameTestState.`both melee and missiles prayers stop graardor completely`() = runBandosTest {
        pray(varbits.protect_from_melee, varbits.protect_from_missiles)
        val hits = fight(BandosTestNpcs.graardor, graardorAt, besideGraardor)
        assertTrue(hits.isEmpty(), "took $hits")
    }

    @Test
    fun GameTestState.`protect from melee leaves only the slam, which hits for 15 to 35`() =
        runBandosTest {
            pray(varbits.protect_from_melee)
            val hits = fight(BandosTestNpcs.graardor, graardorAt, besideGraardor)
            assertTrue(hits.isNotEmpty(), "the slam hurts through the melee prayer")
            assertTrue(hits.all { it in 15..35 }, "slam damage is 15 to 35: $hits")
        }

    @Test
    fun GameTestState.`protect from missiles leaves only the punch which can top 35`() =
        runBandosTest {
            pray(varbits.protect_from_missiles)
            val hits = fight(BandosTestNpcs.graardor, graardorAt, besideGraardor)
            assertTrue(hits.isNotEmpty(), "the punch hurts through the missiles prayer")
            assertTrue(hits.all { it <= GodWarsBandosScript.GRAARDOR_MELEE_MAX }, "up to 60: $hits")
            assertTrue(hits.any { it > 35 }, "the punch goes past the slam's 35: $hits")
        }

    @Test
    fun GameTestState.`the slam hits a player who is nowhere near him`() = runBandosTest {
        allocChamber()
        val other = registerPlayer(player = Player().apply { username = "other" })
        extraPlayers += other
        pray(varbits.protect_from_melee)
        pray(varbits.protect_from_melee, who = other)
        player.placeAt(besideGraardor)
        other.placeAt(farWest)
        spawn(BandosTestNpcs.graardor, graardorAt)
        val taken = mutableMapOf(player to mutableListOf<Int>(), other to mutableListOf())
        repeat(500) {
            player.stats[stats.hitpoints] = 99
            other.stats[stats.hitpoints] = 99
            advance()
            for ((each, list) in taken) {
                if (99 - hp(each) > 0) list += 99 - hp(each)
            }
        }
        assertTrue(taken.getValue(player).isNotEmpty(), "the player beside him")
        assertTrue(taken.getValue(other).isNotEmpty(), "the player across the chamber")
        for ((_, list) in taken) {
            assertTrue(list.all { it in 15..35 }, "only the slam gets through: $list")
        }
    }

    @Test
    fun GameTestState.`sergeant strongstack fights in melee`() = runBandosTest {
        pray(varbits.protect_from_melee)
        assertTrue(fight(BandosTestNpcs.strongstack, graardorAt, besideGraardor).isEmpty())
        pray(varbits.protect_from_missiles, varbits.protect_from_magic)
        assertTrue(fight(BandosTestNpcs.strongstack, graardorAt, besideGraardor).isNotEmpty())
    }

    @Test
    fun GameTestState.`sergeant steelwill casts and grimspike throws axes`() = runBandosTest {
        pray(varbits.protect_from_magic)
        assertTrue(fight(BandosTestNpcs.steelwill, graardorAt, farWest).isEmpty(), "steelwill")
        pray(varbits.protect_from_melee, varbits.protect_from_missiles)
        assertTrue(fight(BandosTestNpcs.steelwill, graardorAt, farWest).isNotEmpty(), "steelwill")
        pray(varbits.protect_from_missiles)
        assertTrue(fight(BandosTestNpcs.grimspike, graardorAt, farWest).isEmpty(), "grimspike")
        pray(varbits.protect_from_melee, varbits.protect_from_magic)
        assertTrue(fight(BandosTestNpcs.grimspike, graardorAt, farWest).isNotEmpty(), "grimspike")
    }

    @Test
    fun GameTestState.`sergeants switch to the player who last hurt graardor`() = runBandosTest {
        allocChamber()
        val other = registerPlayer(player = Player().apply { username = "other" })
        extraPlayers += other
        pray(varbits.protect_from_melee, varbits.protect_from_missiles, varbits.protect_from_magic)
        pray(
            varbits.protect_from_melee,
            varbits.protect_from_missiles,
            varbits.protect_from_magic,
            who = other,
        )
        player.placeAt(besideGraardor)
        other.placeAt(farWest)
        val grimspike = spawn(BandosTestNpcs.grimspike, graardorAt.translateX(-4))
        val strongstack = spawn(BandosTestNpcs.strongstack, graardorAt.translateX(-4).translateZ(1))

        script.recordHit(other)
        repeat(60) {
            player.stats[stats.hitpoints] = 99
            other.stats[stats.hitpoints] = 99
            advance()
        }
        assertEquals(other, script.targetOf(grimspike), "grimspike")
        assertEquals(other, script.targetOf(strongstack), "strongstack")

        script.recordHit(player)
        repeat(60) {
            player.stats[stats.hitpoints] = 99
            other.stats[stats.hitpoints] = 99
            advance()
        }
        assertEquals(player, script.targetOf(grimspike), "grimspike back")
        assertEquals(player, script.targetOf(strongstack), "strongstack back")
    }

    @Test
    fun GameTestState.`the favourite is only followed while they are in the chamber`() =
        runBandosTest {
            val inside = player
            inside.placeAt(besideGraardor)
            val outside = registerPlayer(player = Player().apply { username = "gone" })
            extraPlayers += outside
            outside.placeAt(CoordGrid(2200, 2815, 0))
            script.recordHit(outside)
            assertEquals(inside, script.focusOf(inside), "a player outside is ignored")
            script.recordHit(inside)
            assertEquals(inside, script.focusOf(inside))
            assertNotEquals(null, script.lastHitter)
        }

    @Test
    fun GameTestState.`graardor and the sergeants heal to full when the chamber empties`() =
        runBandosTest {
            val outside = CoordGrid(2200, 2815, 0)
            allocZoneCollision(outside)
            player.placeAt(outside)
            val graardor = spawn(BandosTestNpcs.graardor, graardorAt)
            val steelwill = spawn(BandosTestNpcs.steelwill, graardorAt.translateX(-5))
            graardor.hitpoints = 10
            steelwill.hitpoints = 5
            script.recordHit(player)
            advance(ticks = GodWarsNames.RESET_TIMER * 3)
            assertEquals(graardor.baseHitpointsLvl, graardor.hitpoints, "graardor")
            assertEquals(steelwill.baseHitpointsLvl, steelwill.hitpoints, "steelwill")
            assertFalse(script.lastHitter != null, "the favourite is forgotten")
        }
}
