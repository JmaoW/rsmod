package org.rsmod.content.other.bolt.effects

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.special.attacks.SpecialAttackModule
import org.rsmod.content.other.special.attacks.support.DotKind
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.WeaponPoisonScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

/** Armadyl Eye and Evoke, which boost the effect of the enchanted bolt they shoot. */
class CrossbowSpecialBoltsTest {
    class Deps
    @Inject
    constructor(
        val registry: SpecialAttackRegistry,
        val repo: SpecialAttackRepository,
        val manager: SpecialAttackManager,
        val weapons: SpecialAttackWeapons,
        val energy: SpecialAttackEnergy,
        val dots: SpecialDots,
        val maps: Set<SpecialAttackMap>,
    ) {
        fun registerAll() {
            weapons.startup()
            for (map in maps) {
                with(map) { repo.register(manager) }
            }
        }
    }

    private object SpecialsModule : AbstractModule() {
        override fun configure() {
            install(org.rsmod.api.specials.SpecialAttackModule())
            install(SpecialAttackModule())
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runSpecialBoltTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            SpecialsModule,
            WeaponAttackStylesScript::class,
            NpcHitScript::class,
            PlayerHitScript::class,
            WeaponPoisonScript::class,
            EnchantedBoltsScript::class,
        ) { deps ->
            deps.registerAll()
            body(deps)
        }

    private val dummy =
        npcTypeFactory.create {
            name = "Special bolt dummy"
            vislevel = 50
            attack = 50
            strength = 50
            defence = 1
            hitpoints = 10_000
            defaultMode = NpcMode.None
        }

    private fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.spawnDummy(): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), dummy)
        player.placeAt(npc.coords.translateX(-3))
        return npc
    }

    private fun GameTestScope.equip(crossbow: String, ammo: String) {
        for (stat in listOf(stats.attack, stats.strength, stats.defence, stats.ranged)) {
            player.stats[stat] = 99
        }
        player.stats[stats.hitpoints] = 99
        player.worn[Wearpos.RightHand.slot] = InvObj(obj(crossbow))
        player.worn[Wearpos.Quiver.slot] = InvObj(obj(ammo), 1_000)
        player.observerUUID = player.uuid
    }

    /** The special attack of the equipped crossbow, with a full special bar. */
    private fun GameTestScope.fire(deps: Deps, target: Npc): Boolean {
        player.setVarp(varps.sa_energy, 1000)
        val weapon = checkNotNull(player.worn[Wearpos.RightHand.slot])
        val special = deps.registry[weapon] as SpecialAttack.Ranged
        var fired = false
        player.withProtectedAccess {
            val attack =
                CombatAttack.Ranged(weapon, RangedAttackType.Standard, RangedAttackStyle.Accurate)
            fired = special.attack(this, target, attack)
        }
        return fired
    }

    @Test
    fun GameTestState.`evoke makes the effect of an emerald bolt work on every hit`() =
        runSpecialBoltTest { deps ->
            val npc = spawnDummy()
            equip("zaryte_xbow", "xbows_crossbow_bolts_mithril_tipped_emerald_enchanted")
            var poisoned = false
            repeat(60) {
                if (!poisoned && fire(deps, npc)) {
                    advance(ticks = 6)
                    poisoned = deps.dots.has(npc, DotKind.Poison)
                }
            }
            assertTrue(poisoned, "an Evoke that hit never poisoned")
            // Poison from the Zaryte crossbow starts a point higher.
            assertEquals(6, deps.dots.nextHit(npc, DotKind.Poison))
        }

    @Test
    fun GameTestState.`armadyl eye shoots enchanted bolts like any other shot`() =
        runSpecialBoltTest { deps ->
            val npc = spawnDummy()
            equip("acb", "xbows_crossbow_bolts_mithril_tipped_emerald_enchanted")
            var fired = false
            repeat(40) { fired = fire(deps, npc) || fired }
            assertTrue(fired, "Armadyl Eye never fired")
        }
}
