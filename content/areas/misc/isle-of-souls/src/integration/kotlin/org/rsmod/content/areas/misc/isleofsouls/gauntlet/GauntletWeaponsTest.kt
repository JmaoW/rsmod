package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatMeleeAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatRangedAttributeCollector
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
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
class GauntletWeaponsTest {
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
                .to(GauntletWeapons::class.java)
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

    private fun GameTestScope.wield(type: ObjType) {
        player.worn[Wearpos.RightHand.slot] = InvObj(type)
        advance(ticks = 3)
    }

    @Test
    fun GameTestState.`the bows and staves of both versions are in the registry`() =
        runWeaponTest { deps ->
            for (items in listOf(GauntletRegularItems, GauntletCorruptedItems)) {
                for (bow in items.bow) {
                    assertTrue(deps.registry.getRanged(InvObj(bow)) != null, "bow ${bow.id}")
                }
                for (staff in items.staff) {
                    assertTrue(deps.registry.getMagic(InvObj(staff)) != null, "staff ${staff.id}")
                }
                // The halberds are ordinary melee weapons.
                for (halberd in items.halberd) {
                    assertTrue(
                        deps.registry.getMelee(InvObj(halberd)) == null,
                        "halberd ${halberd.id}",
                    )
                }
            }
        }

    @Test
    fun GameTestState.`a bow fires without arrows and hurts`() = runWeaponTest {
        val man = target()
        player.stats[stats.ranged] = 99
        wield(GauntletRegularItems.bow[2])
        assertEquals(null, player.worn[Wearpos.Quiver.slot])
        player.opNpc2(man)

        advance(ticks = 30)

        assertTrue(man.hitpoints < man.baseHitpointsLvl, "the bow hit it")
    }

    /** The biggest single hit a staff did to a fresh target over [ticks] ticks. */
    private fun GameTestScope.biggestHit(staff: ObjType, magic: Int, ticks: Int = 100): Int {
        val man = target()
        player.stats[stats.magic] = magic
        wield(staff)
        var previous = man.hitpoints
        var biggest = 0
        player.opNpc2(man)
        repeat(ticks) {
            advance()
            biggest = maxOf(biggest, previous - man.hitpoints)
            previous = man.hitpoints
            if (man.hitpoints < 100) man.hitpoints = man.baseHitpointsLvl
        }
        return biggest
    }

    @Test
    fun GameTestState.`a staff hits for its own fixed max whatever the Magic level`() =
        runWeaponTest {
            for ((tier, expected) in GauntletWeapons.STAFF_MAX_HIT.withIndex()) {
                val staff = GauntletRegularItems.staff[tier]
                val skilled = biggestHit(staff, magic = 99)
                assertTrue(skilled > 0, "tier $tier hit something at Magic 99")
                assertTrue(skilled <= expected, "tier $tier hit $skilled, above $expected")
                val novice = biggestHit(staff, magic = 1)
                assertTrue(novice <= expected, "tier $tier hit $novice at Magic 1, above $expected")
            }
        }

    @Test
    fun GameTestState.`a halberd reaches two tiles`() = runWeaponTest {
        val man = target(distance = 2)
        player.stats[stats.attack] = 99
        player.stats[stats.strength] = 99
        wield(GauntletRegularItems.halberd[2])
        player.opNpc2(man)

        advance(ticks = 24)

        assertTrue(man.hitpoints < man.baseHitpointsLvl, "hit from two tiles away")
        assertEquals(here.translateX(2), player.coords)
    }
}
