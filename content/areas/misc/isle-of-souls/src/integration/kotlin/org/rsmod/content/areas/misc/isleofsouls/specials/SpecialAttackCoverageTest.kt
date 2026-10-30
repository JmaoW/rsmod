package org.rsmod.content.areas.misc.isleofsouls.specials

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.revenant.RevenantSpecials
import org.rsmod.content.other.dragonfire.DragonfireShieldSpecials
import org.rsmod.content.other.special.attacks.SpecialAttackModule
import org.rsmod.game.inv.InvObj

class SpecialAttackCoverageTest {
    private object SpecialsModule : AbstractModule() {
        override fun configure() {
            install(org.rsmod.api.specials.SpecialAttackModule())
            install(SpecialAttackModule())
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            val maps = Multibinder.newSetBinder(binder(), SpecialAttackMap::class.java)
            maps.addBinding().to(RevenantSpecials::class.java)
            maps.addBinding().to(DragonfireShieldSpecials::class.java)
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

    /**
     * Every weapon the cache gives a special attack energy requirement has a special attack, with
     * every module that registers them in place.
     */
    @Test
    fun GameTestState.`every weapon with a special attack energy has a special attack`() =
        runInjectedGameTest(Deps::class, SpecialsModule, WeaponAttackStylesScript::class) { deps ->
            deps.weapons.startup()
            for (map in deps.maps) {
                with(map) { deps.repo.register(deps.manager) }
            }
            val weapons = objTypes.values.filter { deps.weapons.getSpecialEnergy(it) != null }
            val missing =
                weapons.filter { deps.registry[InvObj(it)] == null }.mapNotNull { it.internalName }
            check(weapons.size > 200) {
                "Expected the special attack weapons, found ${weapons.size}."
            }
            check(missing.isEmpty()) {
                "No special attack for ${missing.size} of ${weapons.size}: $missing"
            }
        }
}
