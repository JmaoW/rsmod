package org.rsmod.content.other.dragonfire

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.lefthand
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.utils.bits.withBits

class DragonfireShieldTest {
    private object ShieldModule : AbstractModule() {
        override fun configure() {
            install(org.rsmod.api.specials.SpecialAttackModule())
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), SpecialAttackMap::class.java)
                .addBinding()
                .to(DragonfireShieldSpecials::class.java)
        }
    }

    class Deps
    @Inject
    constructor(
        val charges: ShieldCharges,
        val script: DragonfireShieldScript,
        val registry: SpecialAttackRegistry,
        val repo: SpecialAttackRepository,
        val manager: SpecialAttackManager,
        val weapons: SpecialAttackWeapons,
        val maps: Set<SpecialAttackMap>,
    )

    private val dummy =
        npcTypeFactory.create {
            name = "Shield dummy"
            vislevel = 50
            defence = 1
            hitpoints = 5_000
            defaultMode = NpcMode.None
        }

    private fun GameTestState.runShieldTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ShieldModule,
            NpcHitScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
        ) { deps ->
            deps.weapons.startup()
            for (map in deps.maps) {
                with(map) { deps.repo.register(deps.manager) }
            }
            player.observerUUID = player.uuid
            body(deps)
        }

    private fun GameTestScope.wear(shield: ChargedShield, charges: Int) {
        val type = if (charges > 0) shield.charged else shield.uncharged
        val vars = 0.withBits(org.rsmod.api.config.refs.varobjs.charges_16383.bits, charges)
        player.lefthand = InvObj(objTypes[type], 1, vars)
    }

    @Test
    fun GameTestState.`a shield takes a charge from dragonfire and fills up`() =
        runShieldTest { deps ->
            wear(ChargedShield.DragonfireShield, 0)
            assertEquals(ShieldObjs.dragonfire_shield_uncharged.id, player.lefthand?.id)

            assertTrue(deps.charges.charge(player))
            assertEquals(ShieldObjs.dragonfire_shield.id, player.lefthand?.id)
            assertEquals(1, deps.charges.charges(player.lefthand))

            assertTrue(deps.charges.charge(player, ShieldCharges.MAX_CHARGES))
            assertEquals(ShieldCharges.MAX_CHARGES, deps.charges.charges(player.lefthand))
            assertFalse(deps.charges.charge(player), "A full shield takes no more.")
        }

    @Test
    fun GameTestState.`spending the last charge empties the shield`() = runShieldTest { deps ->
        wear(ChargedShield.DragonfireWard, 1)
        assertTrue(deps.charges.spend(player))
        assertEquals(ShieldObjs.dragonfire_ward_uncharged.id, player.lefthand?.id)
        assertFalse(deps.charges.spend(player))
    }

    @Test
    fun GameTestState.`activating readies the special and again puts it away`() =
        runShieldTest { deps ->
            wear(ChargedShield.DragonfireShield, 5)
            player.withProtectedAccess {
                with(deps.script) { activate(ChargedShield.DragonfireShield) }
            }
            assertEquals(SpecialAttackType.Shield.varValue, player.vars[varps.sa_attack])
            player.withProtectedAccess {
                with(deps.script) { activate(ChargedShield.DragonfireShield) }
            }
            assertEquals(SpecialAttackType.None.varValue, player.vars[varps.sa_attack])
        }

    @Test
    fun GameTestState.`an empty shield cannot be activated`() = runShieldTest { deps ->
        wear(ChargedShield.DragonfireShield, 0)
        player.withProtectedAccess {
            with(deps.script) { activate(ChargedShield.DragonfireShield) }
        }
        assertEquals(SpecialAttackType.None.varValue, player.vars[varps.sa_attack])
    }

    @Test
    fun GameTestState.`the special breathes dragonfire and takes a charge`() =
        runShieldTest { deps ->
            val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), dummy)
            player.placeAt(npc.coords.translateX(-2))
            wear(ChargedShield.DragonfireShield, 5)
            val special = deps.registry[checkNotNull(player.lefthand)] as SpecialAttack.Shield
            var breathed = false
            player.withProtectedAccess { breathed = special.attack(this, npc) }
            assertTrue(breathed)
            assertEquals(4, deps.charges.charges(player.lefthand))
            advance(4)
            // It needs two minutes to breathe again.
            var again = true
            player.withProtectedAccess { again = special.attack(this, npc) }
            assertFalse(again)
            assertEquals(4, deps.charges.charges(player.lefthand))
        }

    @Test
    fun GameTestState.`all three shields have the special`() = runShieldTest { deps ->
        for (shield in ChargedShield.entries) {
            player.lefthand = InvObj(objTypes[shield.charged], 1)
            check(deps.registry[checkNotNull(player.lefthand)] is SpecialAttack.Shield) {
                "No shield special for ${shield.title}."
            }
        }
    }

    @Test
    fun GameTestState.`the shield slot is the left hand`() = runShieldTest { _ ->
        assertEquals(5, Wearpos.LeftHand.slot)
    }
}
