package org.rsmod.content.other.food

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.skills.herblore.configs.PoisonCure
import org.rsmod.content.skills.herblore.configs.VenomCure
import org.rsmod.content.skills.herblore.scripts.SicknessTreatment
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the eating sound (there is no named synth for it in the symbols yet)
// - blighted food is meant for the Wilderness only. It can be eaten anywhere here.
// - potions are drunk by content/skills/herblore (Drinking), which keeps its own cooldown
//
// Every food in [FoodTable] is looked up by name in the cache, see [FoodIndex]. Food with several
// bites turns into the next bite, and then into whatever it leaves (a pie dish, a bowl).
class Eating
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val statTypes: StatTypeList,
    private val masteries: CombatMasteryEffects,
    private val random: GameRandom,
    private val sickness: SicknessTreatment,
    private val rules: EatRules,
) : PluginScript() {
    /** The cycle each player can next eat each kind of food. */
    private val cooldowns = WeakHashMap<Player, IntArray>()

    /** The second heal each player is waiting for, from a hunter meat. */
    private val pending = WeakHashMap<Player, Pending>()

    private class Pending(val then: DelayedHeal, var remaining: Int)

    private class Bite(val obj: ObjType, val index: Int, val next: ObjType?)

    override fun ScriptContext.startup() {
        val index = FoodIndex(objTypes.values)
        val registered = HashSet<Int>()
        for (def in FoodTable.all) {
            for (bite in bitesOf(def, index)) {
                if (registered.add(bite.obj.id)) {
                    onOpHeld1(bite.obj) { eat(it, def, bite) }
                }
            }
        }
        onPlayerSoftTimer(FoodTimers.delayed_heal) { tickDelayed(player) }
    }

    private fun bitesOf(def: FoodDef, index: FoodIndex): List<Bite> {
        val leaves = def.leaves?.let { index.item(it) }
        if (def.sameNameBites > 1) {
            val objs = index.edible(def.name).take(def.sameNameBites)
            return objs.mapIndexed { i, obj -> Bite(obj, i, objs.getOrNull(i + 1) ?: leaves) }
        }
        val result = ArrayList<Bite>()
        for ((i, name) in def.bites.withIndex()) {
            val next =
                if (i + 1 < def.bites.size) index.edible(def.bites[i + 1]).firstOrNull() else leaves
            for (obj in index.edible(name)) {
                result += Bite(obj, i, next)
            }
        }
        return result
    }

    private fun ProtectedAccess.eat(event: HeldObjEvents.Op1, def: FoodDef, bite: Bite) {
        if (def.refuse != null) {
            mes(def.refuse)
            return
        }
        val denial = rules.denial(player, bite.obj)
        if (denial != null) {
            mes(denial)
            return
        }
        val ready = cooldowns.getOrPut(player) { IntArray(FoodKind.entries.size) { Int.MIN_VALUE } }
        val kind = def.kind.ordinal
        if (mapClock < ready[kind]) {
            return
        }
        val taken =
            if (bite.next != null) {
                invReplaceSlot(event.inventory, event.slot, 1, bite.next)
            } else {
                invDel(event.inventory, bite.obj, 1, slot = event.slot)
            }
        if (taken.failure) {
            return
        }

        ready[kind] = mapClock + def.eatDelay(bite.index)
        // The next attack is pushed back from now, or from the end of the current attack delay.
        actionDelay = maxOf(actionDelay, mapClock) + def.kind.attackDelay

        anim(FoodSeqs.eat)
        mes(eatMessage(def, bite))

        val before = player.hitpoints
        val after = ArrayList<String>()
        if (def.outcomes.isEmpty()) {
            heal(player, def.heal)
            applyEffects(player, def.effects)
            if (player.hitpoints > before && def.healMessage != null) {
                after += def.healMessage
            }
            def.afterMessage?.let { after += it }
        } else {
            val outcome = roll(def.outcomes)
            heal(player, outcome.heal)
            val drained = applyEffects(player, outcome.effects)
            outcome.message?.let { after += it }
            if (
                outcome.drainMessage != null &&
                    (drained != null || outcome.effects.drains.isNotEmpty())
            ) {
                after += outcome.drainMessage.replace("{skill}", drained.orEmpty())
            }
        }
        for (message in after) {
            mes(message)
        }

        def.then?.let {
            pending[player] = Pending(it, it.ticks)
            softTimer(FoodTimers.delayed_heal, 1)
        }
        publish(Eaten(player, bite.obj, player.hitpoints - before))
    }

    private fun eatMessage(def: FoodDef, bite: Bite): String =
        def.messages.getOrNull(bite.index)
            ?: def.message
            ?: "You ${def.verb} the ${objTypes[bite.obj].name.lowercase()}."

    private fun roll(outcomes: List<Outcome>): Outcome {
        var left = random.of(0, outcomes.sumOf { it.weight } - 1)
        for (outcome in outcomes) {
            left -= outcome.weight
            if (left < 0) {
                return outcome
            }
        }
        return outcomes.last()
    }

    /** Heals [player] by [heal], the second combat mastery passive raising all healing. */
    private fun heal(player: Player, heal: Heal?) {
        if (heal == null) {
            return
        }
        val base = player.baseHitpointsLvl
        val amount =
            masteries.healAmount(
                player,
                when (heal) {
                    is Heal.Fixed -> heal.amount
                    is Heal.Roll -> random.of(heal.min, heal.max)
                    is Heal.OfLevel -> heal.flat + base * heal.percent / 100
                    Heal.Anglerfish -> Foods.anglerfishHeal(base)
                },
            )
        when {
            heal == Heal.Anglerfish -> player.statBoost(stats.hitpoints, amount, 0)
            // statHeal throws when hitpoints are above the base level, so overhealed players
            // are left alone.
            player.hitpoints < base -> player.statHeal(stats.hitpoints, amount, 0)
        }
    }

    /**
     * Applies the stat changes and run energy of [effects].
     *
     * @return the name of the skill a random drain hit, or `null` if there was none.
     */
    private fun applyEffects(player: Player, effects: FoodEffects): String? {
        if (effects.isEmpty()) {
            return null
        }
        for (boost in effects.boosts) {
            player.statBoost(boost.stat, boost.constant, boost.percent)
        }
        for (drain in effects.drains) {
            player.statDrain(drain.stat, drain.constant, drain.percent)
        }
        for (restore in effects.restores) {
            restoreStat(player, restore)
        }
        if (effects.runEnergy > 0) {
            restoreRunEnergy(player, effects.runEnergy)
        }
        if (effects.poison != PoisonCure.None || effects.venom != VenomCure.None) {
            sickness.treat(
                player,
                effects.poison,
                effects.venom,
                venomTicks = effects.venomImmunity,
            )
        }
        if (effects.randomDrain > 0) {
            return drainRandom(player, effects.randomDrain)?.displayName
        }
        return null
    }

    private fun restoreStat(player: Player, restore: StatAmount) {
        val base = player.statBase(restore.stat)
        if (player.stat(restore.stat) >= base) {
            return
        }
        val points = restore.constant + base * restore.percent / 100
        val gain =
            if (restore.stat.isType(stats.prayer)) masteries.prayerGain(player, points) else points
        player.statHeal(restore.stat, gain, 0)
    }

    private fun restoreRunEnergy(player: Player, percent: Int) {
        // Run energy is kept in hundredths of a percent.
        player.runEnergy = minOf(MAX_RUN_ENERGY, player.runEnergy + percent * 100)
        UpdateRun.energy(player, player.runEnergy)
    }

    /** Drains [amount] from a random skill that is not Hitpoints and is above level 2. */
    private fun drainRandom(player: Player, amount: Int): StatType? {
        val candidates =
            statTypes.values.filter { !it.isType(stats.hitpoints) && player.stat(it) > 2 }
        if (candidates.isEmpty()) {
            return null
        }
        val stat = candidates[random.of(0, candidates.size - 1)]
        player.statDrain(stat, amount, 0)
        return stat
    }

    private fun tickDelayed(player: Player) {
        val waiting = pending[player]
        if (waiting == null) {
            player.clearSoftTimer(FoodTimers.delayed_heal)
            return
        }
        if (--waiting.remaining > 0) {
            return
        }
        pending.remove(player)
        player.clearSoftTimer(FoodTimers.delayed_heal)
        if (player.hitpoints <= 0) {
            return
        }
        heal(player, Heal.Fixed(waiting.then.heal))
        applyEffects(player, waiting.then.effects)
    }

    /** Published after every bite of food or sip of drink, with the hitpoints it restored. */
    class Eaten(val player: Player, val obj: ObjType, val healed: Int) : UnboundEvent

    private companion object {
        const val MAX_RUN_ENERGY = 10_000
    }
}
