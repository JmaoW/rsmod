package org.rsmod.content.areas.misc.isleofsouls.colosseum

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.manager.AmmoPouches
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

/** The Tonalztics of Ralos's throws and charges, and Dizana's quiver's extra ammunition slot. */
@OptIn(InternalApi::class)
class ColosseumItemsTest {
    private object ItemsModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            // The weapon registry the combat script reads is the one the weapons were put in.
            @Suppress("UNCHECKED_CAST")
            bind(Class.forName("org.rsmod.api.combat.PvNCombat") as Class<Any>)
                .`in`(Scopes.SINGLETON)
            bind(WeaponRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponRepository::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponAttackManager::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), WeaponMap::class.java)
                .addBinding()
                .to(ColosseumWeapons::class.java)
            // The combat script reads the spell registry.
            install(MagicSpellModule())
            bind(SpellAttackManager::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRepository::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), SpellAttackMap::class.java)
        }
    }

    class Deps
    @Inject
    constructor(
        val registry: WeaponRegistry,
        val pouches: AmmoPouches,
        val pouch: DizanasQuiverPouch,
    )

    @Suppress("UNCHECKED_CAST")
    private fun script(name: String) = Class.forName(name).kotlin as KClass<out PluginScript>

    private fun GameTestState.runItemsTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ItemsModule,
            script("org.rsmod.api.weapons.scripts.WeaponScript"),
            script("org.rsmod.api.combat.scripts.PvNCombatScript"),
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            script("org.rsmod.api.spells.attack.scripts.SpellAttackScript"),
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

    private fun GameTestScope.setTonalzticsCharges(charges: Int) {
        VarPlayerIntMapSetter.set(player, colosseum_varbits.tonalztics_charges, charges)
    }

    // The Tonalztics of Ralos ---------------------------------------------------------------------

    @Test
    fun GameTestState.`both tonalztics have their own attack`() = runItemsTest { deps ->
        val o = colosseum_objs
        assertNotNull(deps.registry.getRanged(InvObj(o.tonalztics_uncharged)))
        assertNotNull(deps.registry.getRanged(InvObj(o.tonalztics_charged)))
    }

    @Test
    fun GameTestState.`the uncharged tonalztics comes back to the thrower`() = runItemsTest {
        val man = target()
        player.stats[stats.ranged] = 99
        wield(colosseum_objs.tonalztics_uncharged)
        player.opNpc2(man)

        advance(ticks = 30)

        assertTrue(man.hitpoints < man.baseHitpointsLvl, "it hit")
        assertEquals(
            colosseum_objs.tonalztics_uncharged.id,
            player.worn[Wearpos.RightHand.slot]?.id,
        )
        assertEquals(1, player.worn[Wearpos.RightHand.slot]?.count)
    }

    @Test
    fun GameTestState.`a charged throw uses one charge whatever lands`() = runItemsTest {
        val man = target()
        player.stats[stats.ranged] = 99
        setTonalzticsCharges(100)
        wield(colosseum_objs.tonalztics_charged)
        player.opNpc2(man)

        // Speed 7 (rapid 6): a few throws in 20 ticks.
        advance(ticks = 20)

        val used = 100 - player.vars[colosseum_varbits.tonalztics_charges]
        assertTrue(used in 1..4, "a charge a throw: $used")
        assertTrue(man.hitpoints < man.baseHitpointsLvl, "it hit")
        assertEquals(colosseum_objs.tonalztics_charged.id, player.worn[Wearpos.RightHand.slot]?.id)
    }

    @Test
    fun GameTestState.`the last charge turns it back into the uncharged weapon`() = runItemsTest {
        val man = target()
        player.stats[stats.ranged] = 99
        setTonalzticsCharges(1)
        wield(colosseum_objs.tonalztics_charged)
        player.opNpc2(man)

        advance(ticks = 12)

        assertEquals(0, player.vars[colosseum_varbits.tonalztics_charges])
        assertEquals(
            colosseum_objs.tonalztics_uncharged.id,
            player.worn[Wearpos.RightHand.slot]?.id,
        )
    }

    // Dizana's quiver's extra slot
    // ------------------------------------------------------------------

    @Test
    fun GameTestState.`swap trades the ammo slot and the pouch`() = runItemsTest { deps ->
        player.worn[Wearpos.Quiver.slot] = InvObj(colosseum_objs.onyx_bolts, 20)
        deps.pouch.set(player, objs.bronze_arrow.id, 50)

        deps.pouch.swap(player)

        assertEquals(objs.bronze_arrow.id, player.worn[Wearpos.Quiver.slot]?.id)
        assertEquals(50, player.worn[Wearpos.Quiver.slot]?.count)
        assertEquals(colosseum_objs.onyx_bolts.id, deps.pouch.obj(player))
        assertEquals(20, deps.pouch.count(player))
    }

    @Test
    fun GameTestState.`a bow fires the pouch's arrows when the ammo slot holds bolts`() =
        runItemsTest { deps ->
            deps.pouches.register(deps.pouch)
            // Arrows that land are dropped as the player's own objs.
            player.uuid = 501L
            player.observerUUID = 501L
            val man = target()
            player.stats[stats.ranged] = 99
            player.worn[Wearpos.Back.slot] = InvObj(colosseum_objs.quiver_uncharged)
            player.worn[Wearpos.Quiver.slot] = InvObj(colosseum_objs.onyx_bolts, 20)
            deps.pouch.set(player, objs.bronze_arrow.id, 50)
            wield(objs.shortbow)
            player.opNpc2(man)

            advance(ticks = 12)

            val worn = player.worn[Wearpos.Quiver.slot]
            assertEquals(objs.bronze_arrow.id, worn?.id, "the arrows were swapped in")
            assertTrue(checkNotNull(worn).count < 50, "and fired")
            assertEquals(colosseum_objs.onyx_bolts.id, deps.pouch.obj(player))
            assertEquals(20, deps.pouch.count(player), "the bolts wait in the pouch")
        }

    @Test
    fun GameTestState.`without a quiver the pouch stays shut`() = runItemsTest { deps ->
        deps.pouches.register(deps.pouch)
        val man = target()
        player.stats[stats.ranged] = 99
        player.worn[Wearpos.Quiver.slot] = InvObj(colosseum_objs.onyx_bolts, 20)
        deps.pouch.set(player, objs.bronze_arrow.id, 50)
        wield(objs.shortbow)
        player.opNpc2(man)

        advance(ticks = 6)

        assertEquals(colosseum_objs.onyx_bolts.id, player.worn[Wearpos.Quiver.slot]?.id)
        assertEquals(50, deps.pouch.count(player))
    }
}
