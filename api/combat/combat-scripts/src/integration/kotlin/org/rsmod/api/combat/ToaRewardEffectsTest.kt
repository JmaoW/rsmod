package org.rsmod.api.combat

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.magic.PvNMagicAccuracy
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatStaffAttributes
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.timers
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.righthand
import org.rsmod.api.player.ring
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.specials.scripts.SpecialEnergyScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

/** Osmumten's fang damage range, the Lightbearer's spec regen and Tumeken's shadow in the raid. */
class ToaRewardEffectsTest {
    class Deps
    @Inject
    constructor(
        val manager: PlayerAttackManager,
        val magic: PvNMagicAccuracy,
        val bonuses: WornBonuses,
    )

    private object StylesModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runEffectsTest(body: GameTestScope.(Deps, Npc) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            StylesModule,
            WeaponAttackStylesScript::class,
            SpecialEnergyScript::class,
        ) { deps ->
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), dummy)
            player.placeAt(npc.coords.translateX(1))
            player.stats[stats.strength] = 99
            player.stats[stats.magic] = 99
            body(deps, npc)
        }

    @Test
    fun GameTestState.`osmumtens fang rolls between 15 and 85 percent of its max hit`() =
        runEffectsTest { deps, npc ->
            player.righthand = InvObj(objs.osmumtens_fang)
            val maxHit = deps.manager.calculateMeleeMaxHit(player, npc, null, null, 1.0)
            assertTrue(maxHit >= 20, "test needs a max hit of at least 20, got $maxHit")

            val rolls = List(4_000) { deps.manager.rollMeleeMaxHit(player, npc, null, null, 1.0) }
            val shrink = maxHit * 3 / 20
            assertEquals(shrink, rolls.min())
            assertEquals(maxHit - shrink, rolls.max())
        }

    @Test
    fun GameTestState.`other weapons still roll from 1 to the max hit`() =
        runEffectsTest { deps, npc ->
            player.righthand = InvObj(objs.abyssal_whip)
            val maxHit = deps.manager.calculateMeleeMaxHit(player, npc, null, null, 1.0)
            val rolls = List(4_000) { deps.manager.rollMeleeMaxHit(player, npc, null, null, 1.0) }
            assertEquals(1, rolls.min())
            assertEquals(maxHit, rolls.max())
        }

    private fun GameTestScope.setRing(ring: InvObj?) {
        val changed = ring ?: player.ring ?: error("no ring to take off")
        player.ring = ring
        eventBus.publish(HeldEquipEvents.WearposChange(player, Wearpos.Ring, objTypes[changed]))
    }

    /** Waits for the next restore, then counts the ticks to the one after it. */
    private fun GameTestScope.ticksForTenPercent(): Int {
        val start = player.vars[varps.sa_energy]
        advanceUntil({ player.vars[varps.sa_energy] > start }) { "energy never rose" }
        val next = player.vars[varps.sa_energy]
        var ticks = 0
        while (player.vars[varps.sa_energy] == next) {
            check(ticks < 200) { "energy rose once only" }
            advance()
            ticks++
        }
        return ticks
    }

    @Test
    fun GameTestState.`lightbearer restores special energy twice as fast`() =
        runEffectsTest { _, _ ->
            VarPlayerIntMapSetter.set(player, varps.sa_energy, 0)
            player.softTimer(timers.spec_regen, 50)
            assertEquals(50, ticksForTenPercent())

            setRing(InvObj(objs.lightbearer))
            assertEquals(25, ticksForTenPercent())

            setRing(null)
            assertEquals(50, player.softTimerRemaining(timers.spec_regen))
            assertEquals(50, ticksForTenPercent())
        }

    @Test
    fun GameTestState.`equipping the lightbearer never delays the next restore`() =
        runEffectsTest { _, _ ->
            player.softTimer(timers.spec_regen, 50)
            advanceUntil({ (player.softTimerRemaining(timers.spec_regen) ?: 0) <= 10 })
            val before = player.softTimerRemaining(timers.spec_regen)
            setRing(InvObj(objs.lightbearer))
            assertEquals(before, player.softTimerRemaining(timers.spec_regen))

            // Early in the cycle it is brought forward to 25.
            setRing(null)
            setRing(InvObj(objs.lightbearer))
            assertEquals(25, player.softTimerRemaining(timers.spec_regen))
        }

    @Test
    fun GameTestState.`tumekens shadow quadruples its magic attack against amascut npcs`() =
        runEffectsTest { deps, _ ->
            player.righthand = InvObj(objs.tumekens_shadow)
            val wornBonus = deps.bonuses.offensiveMagicBonus(player)
            assertEquals(0, wornBonus % 3, "worn bonus should already be tripled: $wornBonus")

            val staff = EnumSet.of(CombatStaffAttributes.TumekensShadow)
            val outside =
                deps.magic.computeStaffAttackRoll(
                    player,
                    null,
                    staff,
                    EnumSet.noneOf(CombatNpcAttributes::class.java),
                )
            val inside =
                deps.magic.computeStaffAttackRoll(
                    player,
                    null,
                    staff,
                    EnumSet.of(CombatNpcAttributes.Amascut),
                )
            val effectiveMagic = MagicAccuracyOperations.calculateEffectiveMagic(player, null)
            assertEquals(effectiveMagic * (wornBonus + 64), outside)
            assertEquals(effectiveMagic * (wornBonus / 3 * 4 + 64), inside)
        }

    private companion object {
        val dummy =
            npcTypeFactory.create {
                name = "Toa effects dummy"
                hitpoints = 500
                defaultMode = NpcMode.None
            }
    }
}
