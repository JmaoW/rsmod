package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.skills.herblore.configs.HerbloreObjs
import org.rsmod.content.skills.herblore.configs.HerbloreSeqs
import org.rsmod.content.skills.herblore.configs.HerbloreTimers
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.Potions
import org.rsmod.content.skills.herblore.configs.StatChange
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the drinking sound (there is no named synth for it in the symbols yet)
// - barbarian mixes, which use the food timer, and the antipoison, antifire and goading effects
//   themselves (see [PotionEffects], nothing on the isle poisons or breathes fire yet)
//
// Drinking takes one dose and leaves the potion with one less, or an empty vial after the last. A
// player can drink one potion every [DRINK_INTERVAL] ticks. This is separate from the timers for
// food, so a potion can be drunk in the same tick as a piece of food, and a potion does not delay
// the next attack.
class Drinking
@Inject
constructor(
    private val statTypes: StatTypeList,
    private val effects: PotionEffects,
    private val masteries: CombatMasteryEffects,
) : PluginScript() {
    /** The cycle each player can next drink a potion. */
    private val ready = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        for (potion in Potions.all) {
            for (doses in 1..Potion.MAX_DOSES) {
                onOpHeld1(potion.dose(doses)) { drink(it, potion, doses) }
            }
        }
        onPlayerSoftTimer(HerbloreTimers.effects) { effects.tick(player) }
        onPlayerLogin { effects.reset(player) }
    }

    private fun ProtectedAccess.drink(event: HeldObjEvents.Op1, potion: Potion, doses: Int) {
        if (mapClock < (ready[player] ?: Int.MIN_VALUE)) {
            return
        }
        val effect = potion.effect
        if (effect is PotionEffect.Divine && player.hitpoints <= PotionEffect.DIVINE_DAMAGE) {
            mes("You do not have enough Hitpoints to drink this potion.")
            return
        }

        val remaining = if (doses > 1) potion.dose(doses - 1) else HerbloreObjs.vial_empty
        if (invReplaceSlot(event.inventory, event.slot, 1, remaining).failure) {
            return
        }
        ready[player] = mapClock + DRINK_INTERVAL

        anim(HerbloreSeqs.drink)
        mes("You drink some of your ${potion.name.lowercase()}.")
        apply(effect)
        mes(dosesLeft(doses - 1))
        publish(PotionDrunk(player, potion))
    }

    private fun dosesLeft(left: Int): String =
        when (left) {
            0 -> "You have finished your potion."
            1 -> "You have 1 dose of the potion left."
            else -> "You have $left doses of potion left."
        }

    private fun ProtectedAccess.apply(effect: PotionEffect) {
        when (effect) {
            is PotionEffect.Boost -> boost(effect.changes)
            is PotionEffect.Divine -> divine(effect)
            is PotionEffect.Restore -> restore(effect)
            is PotionEffect.PrayerPoints ->
                restoreStat(stats.prayer, effect.constant, effect.percent)
            is PotionEffect.RunEnergy -> restoreRunEnergy(effect.percent)
            is PotionEffect.Stamina -> stamina(effect)
            is PotionEffect.Timed -> effects.start(player, effect.effect, effect.ticks)
            PotionEffect.PrayerRegeneration -> effects.addRegeneration(player)
            PotionEffect.SaradominBrew -> saradominBrew()
            PotionEffect.ZamorakBrew -> zamorakBrew()
        }
    }

    private fun ProtectedAccess.boost(changes: List<StatChange>) {
        for (change in changes) {
            statBoost(change.stat, change.constant, change.percent)
        }
    }

    /** The boost, held for five minutes, at the cost of ten hitpoints. */
    private fun ProtectedAccess.divine(effect: PotionEffect.Divine) {
        takeInstantHit(HitType.Typeless, PotionEffect.DIVINE_DAMAGE, hitmark_groups.regular_damage)
        boost(effect.changes)
        effects.startDivine(player, effect.changes.map { it.stat })
    }

    private fun ProtectedAccess.restore(effect: PotionEffect.Restore) {
        for (stat in statTypes.values) {
            val skip = stat.isHitpoints() || (stat.isPrayer() && !effect.prayer)
            if (!skip) {
                restoreStat(stat, effect.constant, effect.percent)
            }
        }
    }

    /**
     * Restores [stat] by [constant] plus [percent] of its base level. Prayer points are raised by
     * the fifth combat mastery passive, which works on the number of points restored.
     */
    private fun ProtectedAccess.restoreStat(stat: StatType, constant: Int, percent: Int) {
        if (!stat.isPrayer()) {
            statHeal(stat, constant, percent)
            return
        }
        val points = constant + (player.statBase(stat) * percent) / 100
        statHeal(stat, masteries.prayerGain(player, points), 0)
    }

    private fun ProtectedAccess.restoreRunEnergy(percent: Int) {
        // Run energy is kept in hundredths of a percent.
        player.runEnergy = minOf(MAX_RUN_ENERGY, player.runEnergy + percent * 100)
        UpdateRun.energy(player, player.runEnergy)
    }

    private fun ProtectedAccess.stamina(effect: PotionEffect.Stamina) {
        restoreRunEnergy(effect.percent)
        VarPlayerIntMapSetter.set(player, varbits.stamina_active, 1)
        effects.start(player, TimedEffect.Stamina, PotionEffect.STAMINA_TICKS)
    }

    /** Raises hitpoints and Defence, and lowers Attack, Strength, Magic and Ranged. */
    private fun ProtectedAccess.saradominBrew() {
        statBoost(stats.hitpoints, 2, 15)
        statBoost(stats.defence, 2, 20)
        for (stat in listOf(stats.attack, stats.strength, stats.magic, stats.ranged)) {
            statDrain(stat, 2, 10)
        }
    }

    /** Raises Attack and Strength, and lowers Defence and the hitpoints the player has left. */
    private fun ProtectedAccess.zamorakBrew() {
        statBoost(stats.attack, 2, 20)
        statBoost(stats.strength, 2, 12)
        statDrain(stats.defence, 2, 10)
        val hitpoints = player.hitpoints
        val loss = (2 + hitpoints / 10).coerceAtMost(hitpoints - 1)
        if (loss > 0) {
            statSub(stats.hitpoints, loss, 0)
        }
    }

    private fun StatType.isHitpoints() = isType(stats.hitpoints)

    private fun StatType.isPrayer() = isType(stats.prayer)

    /** Published after a potion has been drunk. */
    class PotionDrunk(val player: Player, val potion: Potion) : UnboundEvent

    private companion object {
        /** A potion can be drunk once every three ticks. */
        const val DRINK_INTERVAL = 3

        const val MAX_RUN_ENERGY = 10_000
    }
}
