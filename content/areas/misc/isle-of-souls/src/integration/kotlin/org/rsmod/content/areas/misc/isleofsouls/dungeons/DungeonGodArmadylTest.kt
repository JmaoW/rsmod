package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcMeleeBlocks
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
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

internal object ArmadylTestNpcs : NpcReferences() {
    val kree = find("godwars_armadyl_avatar")
    val skree = find("godwars_armadyl_bodyguard_skree")
    val geerin = find("godwars_armadyl_bodyguard_geerin")
    val kilisa = find("godwars_armadyl_bodyguard_kilisa")
    val aviansie = find("godwars_armadyl_male_armor01_blue")
    val armadyl_mage = find("godwars_spiritual_armadyl_mage")
    val sara_knight = find("godwars_saradomin_knight_1")
    val zamorak_mage = find("godwars_spiritual_zamorak_mage")
}

class DungeonGodArmadylTest {
    class Deps
    @Inject
    constructor(
        val script: GodWarsArmadylScript,
        val blocks: NpcMeleeBlocks,
        val registry: NpcRegistry,
    )

    private object ArmadylModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsArmadylScript::class.java).`in`(Scopes.SINGLETON)
            bind(NpcMeleeBlocks::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private lateinit var registry: NpcRegistry

    private fun GameTestState.runArmadylTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ArmadylModule,
            GodItemsScript::class,
            GodWarsArmadylScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            registry = deps.registry
            pray()
            body(deps)
        }

    // Kree'arra's spawn: she fills 2832-2836 by 5302-5306.
    private val kreeAt = CoordGrid(2832, 5302, 2)
    private val eastOfKree = CoordGrid(2837, 5304, 2)
    private val farWest = CoordGrid(2826, 5300, 2)

    private fun GameTestScope.allocChamber() {
        for (x in listOf(2824, 2832, 2840)) {
            for (z in listOf(5296, 5304)) {
                allocZoneCollision(CoordGrid(x, z, 2))
            }
        }
    }

    private fun GameTestScope.spawn(type: NpcType, coords: CoordGrid): Npc {
        allocChamber()
        return spawnNpc(coords, npcTypes[type]).also {
            it.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun isActive(): Boolean = true
                }
        }
    }

    /**
     * Turns exactly [varbit] on: the player is shared by every test, so earlier prayers are
     * cleared.
     */
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

    private fun GameTestScope.hp(): Int = player.stats[stats.hitpoints]

    /**
     * Lets [type] fight a player standing at [stand] for [ticks] ticks (topping them up each tick)
     * and says whether it hurt them. With [targeting], the player is attacking the npc.
     */
    private fun GameTestScope.hurts(
        type: NpcType,
        at: CoordGrid,
        stand: CoordGrid,
        targeting: Boolean = false,
        ticks: Int = 150,
    ): Boolean {
        allocChamber()
        player.placeAt(stand)
        val npc = spawn(type, at)
        player.stats[stats.hitpoints] = 99
        var hurt = false
        repeat(ticks) {
            if (targeting) {
                player.interaction =
                    InteractionNpcOp(
                        InteractionOp.Op2,
                        npc,
                        hasOpTrigger = false,
                        hasApTrigger = false,
                    )
            }
            advance()
            if (hp() < 99) hurt = true
            player.stats[stats.hitpoints] = 99
        }
        registry.del(npc)
        return hurt
    }

    @Test
    fun GameTestState.`the armadylean monsters cannot be meleed but others can`() =
        runArmadylTest { deps ->
            val flying = GodWarsNames.armadyl + GodWarsNames.armadylLeaders
            for (name in flying) {
                val npc = spawn(GodWarsRoster.byName.getValue(name), farWest)
                assertTrue(deps.blocks.isBlocked(npc), "$name is blocked")
                assertEquals(GodWarsArmadylScript.FLYING_TOO_HIGH, deps.blocks.messageFor(npc))
                registry.del(npc)
            }
            for (type in listOf(ArmadylTestNpcs.sara_knight, ArmadylTestNpcs.zamorak_mage)) {
                val npc = spawn(type, farWest)
                assertFalse(deps.blocks.isBlocked(npc), "${type.internalName} is not blocked")
                registry.del(npc)
            }
        }

    @Test
    fun GameTestState
        .`halberds and salamanders get round the melee block but other weapons do not`() =
        runArmadylTest { deps ->
            val npc = spawn(ArmadylTestNpcs.aviansie, farWest)
            assertTrue(deps.blocks.allows(npc, "dragon_halberd"))
            assertTrue(deps.blocks.allows(npc, "red_salamander"))
            assertFalse(deps.blocks.allows(npc, "abyssal_whip"))
            assertFalse(deps.blocks.allows(npc, null))
            val knight = spawn(ArmadylTestNpcs.sara_knight, farWest.translateX(1))
            assertFalse(deps.blocks.allows(knight, "dragon_halberd"), "only blocked types")
        }

    @Test
    fun GameTestState.`kree and her ranged bodyguards hunt from a distance after packing`() =
        runArmadylTest {
            fun type(name: String) = npcTypes.values.first { it.internalName == name }
            val ranged = GodWarsHunt.leaderRanged.id
            assertEquals(ranged, type(GodWarsNames.KREE).huntMode)
            assertEquals(16, type(GodWarsNames.KREE).attackRange)
            assertEquals(ranged, type(GodWarsNames.SKREE).huntMode)
            assertEquals(ranged, type(GodWarsNames.GEERIN).huntMode)
            assertEquals(GodWarsHunt.leader.id, type(GodWarsNames.KILISA).huntMode)
        }

    @Test
    fun GameTestState.`kree knocks players back from her but not out of a corner`() =
        runArmadylTest { deps ->
            val kree = spawn(ArmadylTestNpcs.kree, kreeAt)
            // East of her centre (2834, 5304): pushed one tile east.
            player.placeAt(eastOfKree)
            deps.script.knockBack(kree, player)
            assertEquals(eastOfKree.translateX(1), player.coords)
            // The south-east corner of the chamber: nowhere to go.
            val corner = CoordGrid(2842, 5296, 2)
            player.placeAt(corner)
            deps.script.knockBack(kree, player)
            assertEquals(corner, player.coords)
            // Against the east wall they slide along it instead.
            val wall = CoordGrid(2842, 5300, 2)
            player.placeAt(wall)
            deps.script.knockBack(kree, player)
            assertEquals(wall.translateZ(-1), player.coords)
        }

    @Test
    fun GameTestState.`a knockback keeps a player attacking kree but not another target`() =
        runArmadylTest { deps ->
            val kree = spawn(ArmadylTestNpcs.kree, kreeAt)
            val other = spawn(ArmadylTestNpcs.aviansie, farWest)
            fun aim(target: Npc) =
                InteractionNpcOp(
                    InteractionOp.Op2,
                    target,
                    hasOpTrigger = false,
                    hasApTrigger = false,
                )

            player.placeAt(eastOfKree)
            player.interaction = aim(kree)
            deps.script.knockBack(kree, player)
            assertEquals(eastOfKree.translateX(1), player.coords)
            assertEquals(kree.uid, (player.interaction as InteractionNpc).uid)

            player.placeAt(eastOfKree)
            player.interaction = aim(other)
            deps.script.knockBack(kree, player)
            assertNull(player.interaction, "only a fight with her is kept")
            registry.del(other)
        }

    @Test
    fun GameTestState.`kree swipes with magical melee when nobody is attacking her`() =
        runArmadylTest {
            pray(varbits.protect_from_melee)
            assertFalse(hurts(ArmadylTestNpcs.kree, kreeAt, eastOfKree), "melee prayer stops it")
        }

    @Test
    fun GameTestState.`protect from magic and missiles do not stop her swipe`() = runArmadylTest {
        pray(varbits.protect_from_magic, varbits.protect_from_missiles)
        assertTrue(hurts(ArmadylTestNpcs.kree, kreeAt, eastOfKree), "melee is not prayed against")
    }

    @Test
    fun GameTestState.`both ranged prayers stop her attacks on a player who is fighting her`() =
        runArmadylTest {
            pray(varbits.protect_from_magic, varbits.protect_from_missiles)
            assertFalse(
                hurts(ArmadylTestNpcs.kree, kreeAt, farWest, targeting = true),
                "both prayers on",
            )
        }

    @Test
    fun GameTestState.`protect from missiles alone does not stop her magic`() = runArmadylTest {
        pray(varbits.protect_from_missiles)
        assertTrue(hurts(ArmadylTestNpcs.kree, kreeAt, farWest, targeting = true), "magic lands")
    }

    @Test
    fun GameTestState.`protect from magic alone does not stop her ranged attack`() =
        runArmadylTest {
            pray(varbits.protect_from_magic)
            assertTrue(hurts(ArmadylTestNpcs.kree, kreeAt, farWest, targeting = true), "ranged")
        }

    @Test
    fun GameTestState.`kree hits every player in the chamber at once`() = runArmadylTest {
        allocChamber()
        val other = registerPlayer(player = Player().apply { username = "other" })
        player.placeAt(farWest)
        other.placeAt(farWest.translateZ(4))
        val kree = spawn(ArmadylTestNpcs.kree, kreeAt)
        var hurtFirst = false
        var hurtOther = false
        repeat(150) {
            player.interaction =
                InteractionNpcOp(
                    InteractionOp.Op2,
                    kree,
                    hasOpTrigger = false,
                    hasApTrigger = false,
                )
            player.stats[stats.hitpoints] = 99
            other.stats[stats.hitpoints] = 99
            advance()
            if (player.stats[stats.hitpoints] < 99) hurtFirst = true
            if (other.stats[stats.hitpoints] < 99) hurtOther = true
        }
        registry.del(kree)
        unregisterPlayer(other)
        assertTrue(hurtFirst, "the player fighting her")
        assertTrue(hurtOther, "the bystander is hit too")
    }

    @Test
    fun GameTestState.`flight kilisa fights in melee`() = runArmadylTest {
        pray(varbits.protect_from_melee)
        assertFalse(hurts(ArmadylTestNpcs.kilisa, kreeAt, kreeAt.translateX(3)), "melee prayer")
    }

    @Test
    fun GameTestState.`flight kilisa hurts a player with the wrong prayers`() = runArmadylTest {
        pray(varbits.protect_from_magic, varbits.protect_from_missiles)
        assertTrue(hurts(ArmadylTestNpcs.kilisa, kreeAt, kreeAt.translateX(3)), "wrong prayers")
    }

    @Test
    fun GameTestState.`wingman skree casts and flockleader geerin shoots`() = runArmadylTest {
        pray(varbits.protect_from_magic)
        assertFalse(hurts(ArmadylTestNpcs.skree, kreeAt, farWest), "skree, magic prayer")
        pray(varbits.protect_from_missiles)
        assertFalse(hurts(ArmadylTestNpcs.geerin, kreeAt, farWest), "geerin, missiles prayer")
    }

    @Test
    fun GameTestState.`skree hurts a player who only prays against missiles`() = runArmadylTest {
        pray(varbits.protect_from_missiles)
        assertTrue(hurts(ArmadylTestNpcs.skree, kreeAt, farWest), "wrong prayer")
    }

    @Test
    fun GameTestState.`geerin hurts a player who only prays against magic`() = runArmadylTest {
        pray(varbits.protect_from_magic)
        assertTrue(hurts(ArmadylTestNpcs.geerin, kreeAt, farWest), "wrong prayer")
    }

    @Test
    fun GameTestState.`the chamber's monsters heal to full when it empties`() = runArmadylTest {
        val outside = CoordGrid(2200, 2815, 0)
        allocZoneCollision(outside)
        player.placeAt(outside)
        val kree = spawn(ArmadylTestNpcs.kree, kreeAt)
        val kilisa = spawn(ArmadylTestNpcs.kilisa, kreeAt.translateX(8))
        kree.hitpoints = 10
        kilisa.hitpoints = 5
        kree.defenceLvl = 1
        kilisa.attackLvl = 1
        advance(ticks = GodWarsNames.RESET_TIMER * 3)
        assertEquals(kree.baseHitpointsLvl, kree.hitpoints, "kree")
        assertEquals(kilisa.baseHitpointsLvl, kilisa.hitpoints, "kilisa")
        assertEquals(kree.baseDefenceLvl, kree.defenceLvl, "a lowered Defence is restored")
        assertEquals(kilisa.baseAttackLvl, kilisa.attackLvl, "and so is Attack")
        registry.del(kree)
        registry.del(kilisa)
    }

    @Test
    fun GameTestState.`they stay hurt while a player is in the chamber`() = runArmadylTest {
        allocChamber()
        player.placeAt(farWest)
        val kilisa = spawn(ArmadylTestNpcs.kilisa, kreeAt.translateX(8))
        kilisa.hitpoints = 5
        pray(varbits.protect_from_melee)
        repeat(GodWarsNames.RESET_TIMER * 2) {
            player.stats[stats.hitpoints] = 99
            advance()
        }
        assertTrue(kilisa.hitpoints < kilisa.baseHitpointsLvl, "not healed with a player present")
        registry.del(kilisa)
    }
}
