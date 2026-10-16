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
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
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
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {
    /** The cycle each player can next drink a potion. */
    private val ready = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        val registered = HashSet<Int>()
        for (potion in Potions.drinkable) {
            for (doses in 1..potion.doses.size) {
                val obj = potion.dose(doses)
                if (registered.add(obj.id)) {
                    onOpHeld1(obj) { drink(it, potion, doses) }
                }
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

        if (effect is PotionEffect.Overload && player.hitpoints <= OVERLOAD_MIN_HITPOINTS) {
            mes("You need more than $OVERLOAD_MIN_HITPOINTS Hitpoints to drink this potion.")
            return
        }

        val remaining = if (doses > 1) potion.dose(doses - 1) else potion.empty
        val taken =
            if (remaining != null) {
                invReplaceSlot(event.inventory, event.slot, 1, remaining)
            } else {
                invDel(event.inventory, potion.dose(doses), 1, slot = event.slot)
            }
        if (taken.failure) {
            return
        }
        ready[player] = mapClock + DRINK_INTERVAL

        anim(HerbloreSeqs.drink)
        mes("You drink some of your ${potion.name.lowercase()}.")
        apply(effect)
        if (potion.heal > 0) {
            healHitpoints(potion.heal, 0, overheal = false)
        }
        mes(dosesLeft(doses - 1))
        publish(PotionDrunk(player, potion))
    }

    /**
     * Gives [target] the effect of a dose of [potion] without a dose being drunk, for the potion
     * sharing spells. A player who is busy misses out.
     *
     * @return `false` if the player was busy.
     */
    fun share(target: Player, potion: Potion): Boolean =
        launcher.launch(target) { applyShared(potion) }

    /** The effect of a dose of [potion], without the dose being drunk. */
    fun ProtectedAccess.applyShared(potion: Potion) {
        anim(HerbloreSeqs.drink)
        apply(potion.effect)
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
            is PotionEffect.Composite -> effect.effects.forEach { apply(it) }
            is PotionEffect.Drain ->
                effect.changes.forEach { statDrain(it.stat, it.constant, it.percent) }
            is PotionEffect.Heal -> healHitpoints(effect.constant, effect.percent, effect.overheal)
            is PotionEffect.Overload ->
                effects.startOverload(player, effect.constant, effect.percent)
            is PotionEffect.PrayerEnhance -> effects.startPrayerEnhance(player, effect.ticks)
            PotionEffect.MenaphiteRemedy -> effects.startMenaphite(player)
            PotionEffect.Ambrosia -> ambrosia()
            PotionEffect.NoEffect -> {}
        }
    }

    /**
     * Heals [constant] plus [percent] percent of the base Hitpoints level, going [overheal] above
     * the base level by that much. Healing does not lower hitpoints that are already above it.
     */
    private fun ProtectedAccess.healHitpoints(constant: Int, percent: Int, overheal: Boolean) {
        val amount = constant + player.baseHitpointsLvl * percent / 100
        if (overheal) {
            statBoost(stats.hitpoints, amount, 0)
        } else if (player.hitpoints < player.baseHitpointsLvl) {
            statHeal(stats.hitpoints, amount, 0)
        }
    }

    /** Hitpoints and prayer back to full and a quarter (a fifth) over, full run energy. */
    private fun ProtectedAccess.ambrosia() {
        restoreStat(stats.hitpoints, 255, 0)
        statBoost(stats.hitpoints, 2, 25)
        restoreStat(stats.prayer, 255, 0)
        statBoost(stats.prayer, 5, 20)
        restoreRunEnergy(100)
        effects.start(player, TimedEffect.Antipoison, AMBROSIA_ANTIPOISON_TICKS)
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
        if (player.stat(stat) >= player.statBase(stat)) {
            return
        }
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

        /** Overload takes 50 hitpoints in hits of 10, so the player needs more than that. */
        const val OVERLOAD_MIN_HITPOINTS = 50

        const val AMBROSIA_ANTIPOISON_TICKS = 1200
    }
}
