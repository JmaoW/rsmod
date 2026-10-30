package org.rsmod.content.areas.misc.isleofsouls.revenant

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.CombatStaffAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatMeleeAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatRangedAttributeCollector
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.api.spells.autocast.scripts.AutocastScript
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.combo.ComboRuneScript
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneScript
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneScript
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteScript
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteScript
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

private val RUNE_REPOSITORIES =
    listOf(
        ComboRuneRepository::class,
        CompactRuneRepository::class,
        FakeRuneRepository::class,
        RuneSubstituteRepository::class,
        StaffSubstituteRepository::class,
        UnlimitedRuneRepository::class,
    )

@OptIn(InternalApi::class)
class RevenantWeaponsTest {
    private object WeaponsModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            // The weapon registry the combat script reads is the one the weapons were put in:
            // the combat class must be made here, not in the parent, to be given it.
            @Suppress("UNCHECKED_CAST")
            bind(Class.forName("org.rsmod.api.combat.PvNCombat") as Class<Any>)
                .`in`(Scopes.SINGLETON)
            bind(WeaponRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponRepository::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponAttackManager::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), WeaponMap::class.java)
                .addBinding()
                .to(RevenantWeapons::class.java)
            // The combat script reads the spell registry and the rune repositories.
            install(MagicSpellModule())
            bind(SpellAttackManager::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRepository::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), SpellAttackMap::class.java)
                .addBinding()
                .to(ElementalSpells::class.java)
            for (repository in RUNE_REPOSITORIES) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    class Deps
    @Inject
    constructor(
        val melee: CombatMeleeAttributeCollector,
        val ranged: CombatRangedAttributeCollector,
        val magic: CombatMagicAttributeCollector,
        val npcs: CombatNpcAttributeCollector,
        val random: GameRandom,
        val registry: WeaponRegistry,
    )

    @Suppress("UNCHECKED_CAST")
    private fun script(name: String) = Class.forName(name).kotlin as KClass<out PluginScript>

    private fun GameTestState.runWeaponTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            WeaponsModule,
            script("org.rsmod.api.weapons.scripts.WeaponScript"),
            script("org.rsmod.api.combat.scripts.PvNCombatScript"),
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            RevenantSpellEther::class,
            script("org.rsmod.api.spells.attack.scripts.SpellAttackScript"),
            AutocastScript::class,
            ComboRuneScript::class,
            CompactRuneScript::class,
            FakeRuneScript::class,
            RuneSubstituteScript::class,
            StaffSubstituteScript::class,
            UnlimitedRuneScript::class,
        ) { deps ->
            body(deps)
        }

    private val here = CoordGrid(3217, 10062, 0)

    /** A target with plenty of hitpoints, [distance] tiles from the player. */
    private fun GameTestScope.target(distance: Int = 3): Npc {
        allocZoneCollision(here)
        val stand = here.translateX(distance)
        allocZoneCollision(stand)
        player.placeAt(stand)
        advance(ticks = 2)
        val type =
            npcTypes.values.first {
                it.hasOp(InteractionOp.Op2) && it.size == 1 && it.hitpoints in 300..2_000
            }
        val man = spawnNpc(here, type)
        advance(ticks = 3)
        return man
    }

    /** Puts a weapon in hand and waits for the combat styles to follow it. */
    private fun GameTestScope.wield(type: ObjType, stored: Int) {
        player.worn[Wearpos.RightHand.slot] = EtherCharges.withStored(InvObj(type), type, stored)
        advance(ticks = 3)
    }

    private fun GameTestScope.wieldPlain(type: ObjType) {
        player.worn[Wearpos.RightHand.slot] = InvObj(type)
        advance(ticks = 3)
    }

    private fun GameTestScope.worn() = player.worn[Wearpos.RightHand.slot]

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 40) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    @Test
    fun GameTestState.`the weapons are in the registry`() = runWeaponTest { deps ->
        assertTrue(deps.registry.getMelee(InvObj(ether_objs.viggora)) != null, "viggora")
        assertTrue(deps.registry.getMelee(InvObj(ether_objs.ursine)) != null, "ursine")
        assertTrue(deps.registry.getRanged(InvObj(ether_objs.craw)) != null, "craw")
        assertTrue(deps.registry.getRanged(InvObj(ether_objs.webweaver_uncharged)) != null, "web")
        assertTrue(deps.registry.getMagic(InvObj(ether_objs.thammaron)) != null, "thammaron")
        assertTrue(deps.registry.getMagic(InvObj(ether_objs.accursed_uncharged)) != null, "acc")
    }

    // The boost: only while there is ether to spend, and everywhere.

    @Test
    fun GameTestState.`the melee boost needs ether to spend`() = runWeaponTest { deps ->
        wield(ether_objs.viggora, RevenantEther.ACTIVATION + 1)
        assertTrue(
            CombatMeleeAttributes.RevenantWeapon in deps.melee.collect(player, null),
            "ether to spend",
        )

        wield(ether_objs.viggora, RevenantEther.ACTIVATION)
        assertFalse(
            CombatMeleeAttributes.RevenantWeapon in deps.melee.collect(player, null),
            "only the activation is left",
        )

        wield(ether_objs.ursine, RevenantEther.ACTIVATION + 5)
        assertTrue(CombatMeleeAttributes.RevenantWeapon in deps.melee.collect(player, null))

        player.worn[Wearpos.RightHand.slot] = InvObj(ether_objs.viggora_uncharged)
        assertFalse(CombatMeleeAttributes.RevenantWeapon in deps.melee.collect(player, null))
    }

    @Test
    fun GameTestState.`the ranged boost needs ether to spend`() = runWeaponTest { deps ->
        wield(ether_objs.craw, RevenantEther.ACTIVATION + 1)
        assertTrue(CombatRangedAttributes.RevenantWeapon in deps.ranged.collect(player, null, null))
        wield(ether_objs.webweaver, RevenantEther.ACTIVATION)
        assertFalse(
            CombatRangedAttributes.RevenantWeapon in deps.ranged.collect(player, null, null)
        )
    }

    @Test
    fun GameTestState.`the sceptres' boost needs ether to spend`() = runWeaponTest { deps ->
        for (sceptre in
            listOf(
                ether_objs.thammaron,
                ether_objs.thammaron_a,
                ether_objs.accursed,
                ether_objs.accursed_a,
            )) {
            wield(sceptre, RevenantEther.ACTIVATION + 1)
            assertTrue(
                CombatSpellAttributes.RevenantWeapon in
                    deps.magic.spellCollect(
                        player,
                        objs.spell_flames_of_zamorak,
                        Spellbook.Standard,
                        false,
                        deps.random,
                    ),
                "$sceptre spell",
            )
            assertTrue(
                CombatStaffAttributes.RevenantWeapon in
                    deps.magic.staffCollect(player, deps.random),
                "$sceptre staff",
            )
            wield(sceptre, RevenantEther.ACTIVATION)
            assertFalse(
                CombatSpellAttributes.RevenantWeapon in
                    deps.magic.spellCollect(
                        player,
                        objs.spell_flames_of_zamorak,
                        Spellbook.Standard,
                        false,
                        deps.random,
                    )
            )
            assertFalse(
                CombatStaffAttributes.RevenantWeapon in deps.magic.staffCollect(player, deps.random)
            )
        }
    }

    @Test
    fun GameTestState.`every npc counts as in the Wilderness for the boost`() =
        runWeaponTest { deps ->
            val man = npcTypes.values.first { it.name == "Man" }
            assertTrue(
                CombatNpcAttributes.Wilderness in
                    deps.npcs.collect(man, currHp = 10, maxHp = 10, slayerTask = false)
            )
        }

    // Spending ether.

    @Test
    fun GameTestState.`a chainmace spends an ether on each attack`() = runWeaponTest {
        val man = target(distance = 1)
        player.stats[stats.attack] = 99
        player.stats[stats.strength] = 99
        wield(ether_objs.viggora, RevenantEther.ACTIVATION + 100)
        player.opNpc2(man)

        advance(ticks = 24)

        val left = RevenantEther.usable(worn())
        assertTrue(left in 80..96, "about one ether per 4-tick attack in 24 ticks, left $left")
        assertEquals(ether_objs.viggora.id, worn()!!.id)
    }

    @Test
    fun GameTestState.`a chainmace with no ether still fights`() = runWeaponTest {
        val man = target(distance = 1)
        player.stats[stats.attack] = 99
        player.stats[stats.strength] = 99
        wield(ether_objs.viggora, RevenantEther.ACTIVATION)
        player.opNpc2(man)

        advance(ticks = 24)

        assertEquals(RevenantEther.ACTIVATION, RevenantEther.stored(worn()))
        assertTrue(man.hitpoints < man.baseHitpointsLvl, "it hits with no boost")
    }

    @Test
    fun GameTestState.`a bow needs no ammunition, only ether, and stops when it has none`() =
        runWeaponTest {
            val man = target()
            player.stats[stats.ranged] = 99
            wield(ether_objs.craw, RevenantEther.ACTIVATION + 3)
            assertEquals(null, player.worn[Wearpos.Quiver.slot])
            player.opNpc2(man)

            advanceUntilMessage("Your bow has no ether to fire with.", timeoutTicks = 60)

            assertEquals(0, RevenantEther.usable(worn()))
            assertEquals(RevenantEther.ACTIVATION, RevenantEther.stored(worn()))
            assertTrue(man.hitpoints < man.baseHitpointsLvl, "the three shots hit it at least once")
        }

    @Test
    fun GameTestState.`an uncharged bow cannot fire`() = runWeaponTest {
        val man = target()
        player.stats[stats.ranged] = 99
        wieldPlain(ether_objs.webweaver_uncharged)
        player.opNpc2(man)

        advanceUntilMessage("Your bow has no ether to fire with.")

        assertEquals(man.baseHitpointsLvl, man.hitpoints)
    }

    @Test
    fun GameTestState.`a sceptre casts its own spell for an ether each`() = runWeaponTest {
        val man = target()
        player.stats[stats.magic] = 99
        wield(ether_objs.accursed, RevenantEther.ACTIVATION + 8)
        player.opNpc2(man)

        advanceUntilMessage("Your sceptre has no ether to cast with.", timeoutTicks = 80)
        advance(ticks = 6)

        assertEquals(0, RevenantEther.usable(worn()))
        assertEquals(RevenantEther.ACTIVATION, RevenantEther.stored(worn()))
        assertTrue(man.hitpoints < man.baseHitpointsLvl, "eight casts hit at least once")
    }

    @Test
    fun GameTestState.`an uncharged sceptre has no spell`() = runWeaponTest {
        val man = target()
        player.stats[stats.magic] = 99
        wieldPlain(ether_objs.thammaron_uncharged)
        player.opNpc2(man)

        advanceUntilMessage("Your sceptre has no ether to cast with.")

        assertEquals(man.baseHitpointsLvl, man.hitpoints)
    }

    @Test
    fun GameTestState.`the sceptre's max hit is a third of the level less 8 or 6`() =
        runWeaponTest {
            assertEquals(25, 99 / 3 - EtherItems.thammaron.maxHitOffset)
            assertEquals(27, 99 / 3 - EtherItems.accursed.maxHitOffset)
        }
}
