package org.rsmod.content.other.food

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the eating sound (there is no named synth for it in the symbols yet)
// - meat, bread, pies and the other food that is not a cooked fish
// - potions are drunk by content/skills/herblore (Drinking), which keeps its own cooldown
class Eating
@Inject
constructor(private val objTypes: ObjTypeList, private val masteries: CombatMasteryEffects) :
    PluginScript() {
    /** The cycle each player can next eat each kind of food. */
    private val cooldowns = WeakHashMap<Player, IntArray>()

    override fun ScriptContext.startup() {
        for (food in Foods.all) {
            onOpHeld1(food.obj) { eat(it, food) }
        }
    }

    private fun ProtectedAccess.eat(event: HeldObjEvents.Op1, food: Food) {
        val ready = cooldowns.getOrPut(player) { IntArray(FoodKind.entries.size) { Int.MIN_VALUE } }
        val kind = food.kind.ordinal
        if (mapClock < ready[kind]) {
            return
        }
        if (invDel(event.inventory, food.obj, 1, slot = event.slot).failure) {
            return
        }

        ready[kind] = mapClock + food.kind.eatDelay
        // The next attack is pushed back from now, or from the end of the current attack delay.
        actionDelay = maxOf(actionDelay, mapClock) + food.kind.attackDelay

        anim(FoodSeqs.eat)
        mes("You eat the ${objTypes[food.obj].name.lowercase()}.")
        if (heal(food)) {
            mes("It heals some health.")
        }
    }

    /** @return `true` if any hitpoints were restored. */
    private fun ProtectedAccess.heal(food: Food): Boolean {
        val base = player.baseHitpointsLvl
        val before = player.hitpoints
        // The second combat mastery passive raises all healing.
        val amount = masteries.healAmount(player, food.heal(base))
        if (food.overheals) {
            statBoost(stats.hitpoints, amount, 0)
        } else if (before < base) {
            statHeal(stats.hitpoints, amount, 0)
        }
        return player.hitpoints > before
    }
}
