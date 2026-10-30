package org.rsmod.api.combat.formulas

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.accuracy.melee.PvNMeleeAccuracy
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.front
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.util.ParamMapBuilder

/**
 * The "type" params (undead, demon, ...) the combat formulas read were never set on any npc, so the
 * salve amulet, the demonbane weapons and the dragon hunter weapons never applied. They come from
 * `npc_attributes.toml` (see `tools/npc-attributes`) and from the cache itself (draconic, golem,
 * kalphite), which the cache packer merges into the npc types. The real-cache tests need `./gradlew
 * packCache` after that file changes.
 */
class NpcTypeAttributesTest {
    class Deps
    @Inject
    constructor(
        val collector: CombatNpcAttributeCollector,
        val accuracy: PvNMeleeAccuracy,
        val npcTypes: NpcTypeList,
    )

    private object Module : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, Module, WeaponAttackStylesScript::class) { deps ->
            body(deps)
        }

    private fun Deps.attributes(type: UnpackedNpcType) =
        collector.collect(type, currHp = 100, maxHp = 100, slayerTask = false)

    /** The attributes besides the ones every npc gets. */
    private fun Deps.typeAttributes(type: UnpackedNpcType) =
        attributes(type) - CombatNpcAttributes.Wilderness

    private fun Deps.real(internalName: String) =
        npcTypes.values.first { it.internalName == internalName }

    /** Whether any npc with this name has the param; the cache flags the main versions. */
    private fun Deps.anyNamed(name: String, param: ParamInt): Boolean =
        npcTypes.values.any { it.name == name && it.param(param) != 0 }

    @Test
    fun GameTestState.`an ordinary npc gets no type attributes`() = runTest { deps ->
        val goblin = deps.real("goblin_unarmed_melee_in_1")
        assertTrue(deps.typeAttributes(goblin).isEmpty(), "${deps.typeAttributes(goblin)}")
    }

    @Test
    fun GameTestState.`an undead npc gets the undead attribute`() = runTest { deps ->
        val zombie = deps.real("zombie_armed_rural1")
        val attributes = deps.attributes(zombie)
        assertTrue(CombatNpcAttributes.Undead in attributes)
        assertFalse(CombatNpcAttributes.Demon in attributes)
        assertFalse(CombatNpcAttributes.Draconic in attributes)
    }

    @Test
    fun GameTestState.`a demon gets the demon attribute`() = runTest { deps ->
        val demon = deps.real("black_demon")
        val attributes = deps.attributes(demon)
        assertTrue(CombatNpcAttributes.Demon in attributes)
        assertFalse(CombatNpcAttributes.DemonbaneResistance in attributes)
        assertFalse(CombatNpcAttributes.Undead in attributes)
        assertFalse(CombatNpcAttributes.Draconic in attributes)
    }

    @Test
    fun GameTestState.`duke sucellus is a demon that resists demonbane`() = runTest { deps ->
        val duke = deps.real("duke_sucellus_awake")
        val attributes = deps.attributes(duke)
        assertTrue(CombatNpcAttributes.Demon in attributes)
        assertTrue(CombatNpcAttributes.DemonbaneResistance in attributes)
    }

    @Test
    fun GameTestState.`a draconic npc gets the draconic attribute`() = runTest { deps ->
        // Vorkath is both, and `draconic` is carried by the cache itself.
        val attributes = deps.attributes(deps.real("vorkath"))
        assertTrue(CombatNpcAttributes.Draconic in attributes)
        assertTrue(CombatNpcAttributes.Undead in attributes)
        assertFalse(CombatNpcAttributes.Demon in attributes)
        assertTrue(deps.anyNamed("Blue dragon", params.draconic))
    }

    @Test
    fun GameTestState.`the other type params are set`() = runTest { deps ->
        fun check(internalName: String, expected: CombatNpcAttributes) {
            val attributes = deps.attributes(deps.real(internalName))
            assertTrue(expected in attributes, "$internalName: $expected not in $attributes")
        }
        check("shade_level1", CombatNpcAttributes.Shade)
        check("barrows_rat", CombatNpcAttributes.Rat)
        check("toa_amascut", CombatNpcAttributes.Amascut)
        assertTrue(deps.anyNamed("Kalphite Queen", params.kalphite))
        assertTrue(deps.anyNamed("Rock Golem", params.golem))
        // The shield is up by default, so the unshielded damage bonus stays off until it drops.
        assertTrue(deps.real("tormented_demon_1").param(params.tormented_demon) != 0)
    }

    @Test
    fun GameTestState.`the salve amulet raises accuracy against undead only`() = runTest { deps ->
        val undead = twin(params.undead)
        val ordinary = twin()

        val bare = deps.hitChance(this, ordinary, amulet = null)
        val vsOrdinary = deps.hitChance(this, ordinary, amulet = objs.salve_amulet_e)
        val vsUndead = deps.hitChance(this, undead, amulet = objs.salve_amulet_e)

        assertEquals(bare, vsOrdinary, "no bonus against an ordinary npc")
        assertTrue(vsUndead > vsOrdinary, "salve (e): $vsUndead > $vsOrdinary")
    }

    @Test
    fun GameTestState.`arclight raises accuracy against demons only`() = runTest { deps ->
        val demon = twin(params.demon)
        val ordinary = twin()

        val vsOrdinary = deps.hitChance(this, ordinary, weapon = objs.arclight)
        val vsDemon = deps.hitChance(this, demon, weapon = objs.arclight)

        assertTrue(vsDemon > vsOrdinary, "arclight: $vsDemon > $vsOrdinary")
    }

    @Test
    fun GameTestState.`demonbane is weaker against demonbane resistant demons`() = runTest { deps ->
        val demon = twin(params.demon)
        val resistant = twin(params.demon, params.demonbane_resistant)

        val vsDemon = deps.hitChance(this, demon, weapon = objs.arclight)
        val vsResistant = deps.hitChance(this, resistant, weapon = objs.arclight)

        assertTrue(vsResistant < vsDemon, "resistant: $vsResistant < $vsDemon")
    }

    @Test
    fun GameTestState.`the dragon hunter lance raises accuracy against draconic npcs only`() =
        runTest { deps ->
            val dragon = twin(params.draconic)
            val ordinary = twin()

            val vsOrdinary = deps.hitChance(this, ordinary, weapon = objs.dragon_hunter_lance)
            val vsDragon = deps.hitChance(this, dragon, weapon = objs.dragon_hunter_lance)

            assertTrue(vsDragon > vsOrdinary, "dragon hunter lance: $vsDragon > $vsOrdinary")
        }

    private fun Deps.hitChance(
        scope: GameTestScope,
        type: UnpackedNpcType,
        amulet: ObjType? = null,
        weapon: ObjType? = null,
    ): Int {
        val player = scope.player
        player.front = amulet?.let(::InvObj)
        player.righthand = weapon?.let(::InvObj)
        val npc = Npc(type)
        return accuracy.getHitChance(
            player = player,
            target = npc,
            attackType = MeleeAttackType.Slash,
            attackStyle = MeleeAttackStyle.Accurate,
            blockType = MeleeAttackType.Slash,
            specialMultiplier = 1.0,
        )
    }

    private companion object {
        val defences =
            listOf(
                params.defence_stab,
                params.defence_slash,
                params.defence_crush,
                params.defence_magic,
                params.defence_light,
                params.defence_standard,
                params.defence_heavy,
            )

        /** An npc with a defence that keeps hit chances away from 0% and 100%, and the flags. */
        fun twin(vararg flags: ParamInt): UnpackedNpcType =
            npcTypeFactory.create {
                name = "Twin"
                hitpoints = 100
                defence = 100
                paramMap =
                    ParamMapBuilder()
                        .apply {
                            for (defence in defences) {
                                this[defence] = 100
                            }
                            for (flag in flags) {
                                this[flag] = 1
                            }
                        }
                        .toParamMap()
            }
    }
}
