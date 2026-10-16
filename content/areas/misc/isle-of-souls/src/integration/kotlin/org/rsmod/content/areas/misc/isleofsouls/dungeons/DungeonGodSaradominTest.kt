package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

internal object SaradominTestNpcs : NpcReferences() {
    val zilyana = find("godwars_saradomin_avatar")
    val starlight = find("godwars_saradomin_unicorn")
    val growler = find("godwars_saradomin_lion")
    val bree = find("godwars_saradomin_centaur")
}

class DungeonGodSaradominTest {
    class Deps @Inject constructor(val script: GodWarsSaradominScript, val registry: NpcRegistry)

    private object SaradominModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsSaradominScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private lateinit var registry: NpcRegistry
    private val spawned = mutableListOf<Npc>()

    /** The player and the world are shared by every test, so each test cleans up after itself. */
    private fun GameTestState.runSaradominTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(
            Deps::class,
            SaradominModule,
            GodItemsScript::class,
            GodWarsSaradominScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            registry = deps.registry
            pray()
            try {
                body()
            } finally {
                spawned.forEach { registry.del(it) }
                spawned.clear()
            }
        }

    // Zilyana's spawn: she fills 2897-2898 by 5269-5270.
    private val zilyanaAt = CoordGrid(2897, 5269, 0)
    private val besideZilyana = CoordGrid(2896, 5269, 0)
    private val farWest = CoordGrid(2893, 5264, 0)

    private fun GameTestScope.allocChamber() {
        for (x in listOf(2888, 2896, 2904)) {
            for (z in listOf(5256, 5264, 5272)) {
                allocZoneCollision(CoordGrid(x, z, 0))
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
    private fun GameTestScope.pray(vararg varbit: VarBitType) {
        for (each in
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )) {
            player.setVarBit(each, 0)
        }
        for (each in varbit) {
            player.setVarBit(each, 1)
        }
    }

    /** Every hit the player took, one entry per tick that cost hitpoints. */
    private fun GameTestScope.fight(
        type: NpcType,
        at: CoordGrid,
        stand: CoordGrid,
        ticks: Int = 400,
    ): List<Int> {
        allocChamber()
        player.placeAt(stand)
        val npc = spawn(type, at)
        player.stats[stats.hitpoints] = 99
        val hits = mutableListOf<Int>()
        repeat(ticks) {
            advance()
            val taken = 99 - player.stats[stats.hitpoints]
            if (taken > 0) hits += taken
            player.stats[stats.hitpoints] = 99
        }
        registry.del(npc)
        spawned -= npc
        return hits
    }

    @Test
    fun GameTestState.`zilyana and starlight hunt in melee and her ranged bodyguards from range`() =
        runSaradominTest {
            fun type(name: String) = npcTypes.values.first { it.internalName == name }
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.ZILYANA).huntMode)
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.STARLIGHT).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.GROWLER).huntMode)
            assertEquals(GodWarsHunt.leaderRanged.id, type(GodWarsNames.BREE).huntMode)
            assertEquals(8, type(GodWarsNames.BREE).attackRange)
        }

    @Test
    fun GameTestState.`melee and magic prayers together stop zilyana completely`() =
        runSaradominTest {
            pray(varbits.protect_from_melee, varbits.protect_from_magic)
            val hits = fight(SaradominTestNpcs.zilyana, zilyanaAt, besideZilyana)
            assertTrue(hits.isEmpty(), "took $hits")
        }

    @Test
    fun GameTestState.`protect from melee leaves only her magic, which hits for 10 to 20`() =
        runSaradominTest {
            pray(varbits.protect_from_melee)
            val hits = fight(SaradominTestNpcs.zilyana, zilyanaAt, besideZilyana)
            assertTrue(hits.isNotEmpty(), "her magic hurts through the melee prayer")
            assertTrue(hits.all { it in 10..20 }, "magic damage is 10 to 20: $hits")
        }

    @Test
    fun GameTestState.`protect from magic leaves only her melee which reaches 27`() =
        runSaradominTest {
            pray(varbits.protect_from_magic)
            val hits = fight(SaradominTestNpcs.zilyana, zilyanaAt, besideZilyana)
            assertTrue(hits.isNotEmpty(), "her melee hurts through the magic prayer")
            assertTrue(
                hits.all { it <= GodWarsSaradominScript.ZILYANA_MELEE_MAX },
                "up to 27: $hits",
            )
            assertTrue(hits.any { it > 20 }, "the punch goes past the magic's 20: $hits")
        }

    @Test
    fun GameTestState.`zilyana attacks every two ticks and the bodyguards every five`() =
        runSaradominTest {
            assertEquals(2, GodWarsSaradominScript.ZILYANA_RATE)
            assertEquals(5, GodWarsSaradominScript.BODYGUARD_RATE)
            // With no prayer she lands far more often than a bodyguard does in the same time.
            val zilyana = fight(SaradominTestNpcs.zilyana, zilyanaAt, besideZilyana, ticks = 100)
            val starlight =
                fight(SaradominTestNpcs.starlight, zilyanaAt, besideZilyana, ticks = 100)
            assertTrue(
                zilyana.size > starlight.size,
                "zilyana ${zilyana.size}, starlight ${starlight.size}",
            )
        }

    @Test
    fun GameTestState.`starlight fights in melee`() = runSaradominTest {
        pray(varbits.protect_from_melee)
        assertTrue(fight(SaradominTestNpcs.starlight, zilyanaAt, besideZilyana).isEmpty())
        pray(varbits.protect_from_missiles, varbits.protect_from_magic)
        assertTrue(fight(SaradominTestNpcs.starlight, zilyanaAt, besideZilyana).isNotEmpty())
    }

    @Test
    fun GameTestState.`growler casts and bree shoots`() = runSaradominTest {
        pray(varbits.protect_from_magic)
        assertTrue(fight(SaradominTestNpcs.growler, zilyanaAt, farWest).isEmpty(), "growler")
        pray(varbits.protect_from_melee, varbits.protect_from_missiles)
        assertTrue(fight(SaradominTestNpcs.growler, zilyanaAt, farWest).isNotEmpty(), "growler")
        pray(varbits.protect_from_missiles)
        assertTrue(fight(SaradominTestNpcs.bree, zilyanaAt, farWest).isEmpty(), "bree")
        pray(varbits.protect_from_melee, varbits.protect_from_magic)
        assertTrue(fight(SaradominTestNpcs.bree, zilyanaAt, farWest).isNotEmpty(), "bree")
    }

    @Test
    fun GameTestState.`zilyana and her bodyguards heal to full when the chamber empties`() =
        runSaradominTest {
            val outside = CoordGrid(2200, 2815, 0)
            allocZoneCollision(outside)
            player.placeAt(outside)
            val zilyana = spawn(SaradominTestNpcs.zilyana, zilyanaAt)
            val bree = spawn(SaradominTestNpcs.bree, zilyanaAt.translateX(-6))
            zilyana.hitpoints = 10
            bree.hitpoints = 5
            advance(ticks = GodWarsNames.RESET_TIMER * 3)
            assertEquals(zilyana.baseHitpointsLvl, zilyana.hitpoints, "zilyana")
            assertEquals(bree.baseHitpointsLvl, bree.hitpoints, "bree")
        }
}
