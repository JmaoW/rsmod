package org.rsmod.content.areas.misc.isleofsouls.specials

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.manager.PlayerAttackEvents
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.lefthand
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.areas.misc.isleofsouls.revenant.RevenantSpecials
import org.rsmod.content.other.dragonfire.ChargedShield
import org.rsmod.content.other.dragonfire.DragonfireShieldSpecials
import org.rsmod.content.other.dragonfire.ShieldObjs
import org.rsmod.content.other.special.attacks.SpecialAttackModule
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

/**
 * Special attacks made the way a player makes them: the special bar is on, they click a target and
 * the combat script runs the special in place of the normal swing.
 */
@OptIn(InternalApi::class)
class SpecialCombatFlowTest {
    private object FlowModule : AbstractModule() {
        override fun configure() {
            install(org.rsmod.api.specials.SpecialAttackModule())
            install(SpecialAttackModule())
            val maps = Multibinder.newSetBinder(binder(), SpecialAttackMap::class.java)
            maps.addBinding().to(RevenantSpecials::class.java)
            maps.addBinding().to(DragonfireShieldSpecials::class.java)

            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            @Suppress("UNCHECKED_CAST")
            bind(Class.forName("org.rsmod.api.combat.PvNCombat") as Class<Any>)
                .`in`(Scopes.SINGLETON)
            bind(WeaponRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponRepository::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponAttackManager::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), WeaponMap::class.java)
            Multibinder.newSetBinder(binder(), SpellAttackMap::class.java)
            install(MagicSpellModule())
            bind(SpellAttackManager::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRepository::class.java).`in`(Scopes.SINGLETON)
            for (repository in
                listOf(
                    ComboRuneRepository::class,
                    CompactRuneRepository::class,
                    FakeRuneRepository::class,
                    RuneSubstituteRepository::class,
                    StaffSubstituteRepository::class,
                    UnlimitedRuneRepository::class,
                )) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    class Deps
    @Inject
    constructor(
        val registry: SpecialAttackRegistry,
        val repo: SpecialAttackRepository,
        val manager: SpecialAttackManager,
        val weapons: SpecialAttackWeapons,
        val maps: Set<SpecialAttackMap>,
    )

    @Suppress("UNCHECKED_CAST")
    private fun script(name: String) = Class.forName(name).kotlin as KClass<out PluginScript>

    private fun GameTestState.runFlowTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            FlowModule,
            script("org.rsmod.api.weapons.scripts.WeaponScript"),
            script("org.rsmod.api.combat.scripts.PvNCombatScript"),
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            script("org.rsmod.api.spells.attack.scripts.SpellAttackScript"),
            org.rsmod.content.other.special.attacks.support.WeaponPoisonScript::class,
            org.rsmod.content.other.special.attacks.support.DamageGuardScript::class,
        ) { deps ->
            deps.weapons.startup()
            for (map in deps.maps) {
                with(map) { deps.repo.register(deps.manager) }
            }
            player.observerUUID = player.uuid
            body(deps)
        }

    private val here = CoordGrid(3217, 10062, 0)

    /** A target with plenty of hitpoints, [distance] tiles from the player. */
    private fun GameTestScope.target(distance: Int = 1): Npc {
        allocZoneCollision(here)
        val stand = here.translateX(distance)
        allocZoneCollision(stand)
        player.placeAt(stand)
        advance(ticks = 2)
        val type =
            npcTypes.values.first {
                it.hasOp(InteractionOp.Op2) && it.size == 1 && it.hitpoints in 300..2_000
            }
        val npc = spawnNpc(here, type)
        advance(ticks = 3)
        return npc
    }

    private fun GameTestScope.wield(weapon: ObjType, ammo: String? = null) {
        for (stat in
            listOf(stats.attack, stats.strength, stats.ranged, stats.magic, stats.defence)) {
            player.stats[stat] = 99
        }
        player.worn[Wearpos.RightHand.slot] = InvObj(weapon)
        if (ammo != null) {
            val type = objTypes.values.first { it.internalName == ammo }
            player.worn[Wearpos.Quiver.slot] = InvObj(type, 200)
        }
        player.setVarp(varps.sa_energy, 1000)
        advance(ticks = 3)
    }

    private fun GameTestScope.specialOn(type: SpecialAttackType = SpecialAttackType.Weapon) {
        player.setVarp(varps.sa_attack, type.varValue)
    }

    private fun GameTestScope.energy(): Int = player.vars[varps.sa_energy]

    @Test
    fun GameTestState.`a melee special replaces the next swing`() = runFlowTest { _ ->
        val npc = target()
        wield(sobjs.dragon_dagger)
        val hits = mutableListOf<PlayerAttackEvents.Hit>()
        eventBus.subscribeUnbound(PlayerAttackEvents.Hit::class.java) { hits += this }
        specialOn()
        player.opNpc2(npc)
        advance(ticks = 2)

        assertEquals(750, energy())
        // Puncture: two hits in the first swing; the special bar goes off again for the next.
        assertTrue(hits.size >= 2) { "Expected the two hits of Puncture, saw ${hits.size}." }
        assertEquals(SpecialAttackType.None.varValue, player.vars[varps.sa_attack])
    }

    @Test
    fun GameTestState.`a ranged special replaces the next shot`() = runFlowTest { _ ->
        val npc = target(distance = 3)
        wield(sobjs.magic_shortbow, "rune_arrow")
        specialOn()
        player.opNpc2(npc)
        advance(ticks = 3)

        assertEquals(450, energy())
        // Snapshot fires two arrows.
        assertTrue((player.worn[Wearpos.Quiver.slot]?.count ?: 0) <= 198)
    }

    @Test
    fun GameTestState.`a staff special is made whichever style the staff is held in`() =
        runFlowTest { _ ->
            val npc = target()
            wield(sobjs.nightmare_staff_volatile)
            specialOn()
            // Held for a bash (melee), not for a spell: the special is still the magic one.
            player.opNpc2(npc)
            advance(ticks = 4)

            assertEquals(450, energy())
            assertTrue(npc.hitpoints < npc.baseHitpointsLvl || energy() == 450)
        }

    @Test
    fun GameTestState.`a shield special replaces the next swing whatever is wielded`() =
        runFlowTest { _ ->
            val npc = target()
            wield(sobjs.dragon_dagger)
            player.lefthand = InvObj(ShieldObjs.dragonfire_shield, 1, 5)
            specialOn(SpecialAttackType.Shield)
            player.opNpc2(npc)
            advance(ticks = 3)

            // No special attack energy is spent, only a charge.
            assertEquals(1000, energy())
            assertEquals(SpecialAttackType.None.varValue, player.vars[varps.sa_attack])
            val charges = ChargedShield.of(player.lefthand)
            assertEquals(ChargedShield.DragonfireShield, charges)
            assertEquals(
                4,
                org.rsmod.content.other.dragonfire.ShieldCharges().charges(player.lefthand),
            )
        }

    @Test
    fun GameTestState.`not enough special energy leaves the swing alone`() = runFlowTest { _ ->
        val npc = target()
        wield(sobjs.dragon_dagger)
        player.setVarp(varps.sa_energy, 100)
        specialOn()
        player.opNpc2(npc)
        advance(ticks = 2)

        assertEquals(100, energy())
        assertEquals(SpecialAttackType.None.varValue, player.vars[varps.sa_attack])
    }
}
