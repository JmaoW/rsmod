package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.configs.SmeltingBar
import org.rsmod.content.skills.smithing.configs.SmeltingBars
import org.rsmod.content.skills.smithing.configs.SmithingSeqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - Blurite and Lovakite bars, perfect gold bars, and cannonballs
// - the Ring of forging, the Goldsmith gauntlets, and the Superheat Item spell
// - the Blast Furnace and the Giants' Foundry
//
// A furnace is any loc named "Furnace" that has a "Smelt" op. Smelting continues through the
// otherwise unused op4 of the furnace once the player has picked a bar, the same way cooking
// continues on a fire or range.
class Smelting
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
) : PluginScript() {
    /** What each player is currently smelting, so the loop knows what comes next. */
    private val jobs = WeakHashMap<Player, SmeltJob>()

    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            if (!isFurnace(type)) {
                continue
            }
            if (!onOpLocNamed(type, SMELT_OP) { chooseBar(it) }) {
                continue
            }
            onOpLocU(type) { useOn(it.loc, it.objType) }
            onOpLoc4(type) { smeltNext(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.useOn(loc: BoundLocInfo, obj: ObjType) {
        if (!SmeltingBars.isIngredient(obj)) {
            mes("Nothing interesting happens.")
            return
        }
        chooseBar(loc)
    }

    private suspend fun ProtectedAccess.chooseBar(loc: BoundLocInfo) {
        val choice =
            skillMulti(
                title = "What would you like to smelt?",
                options = SmeltingBars.all.map { it.bar },
                maxQuantity = MAX_QUANTITY,
            )
        start(loc, SmeltingBars.all[choice.index], choice.quantity)
    }

    private fun ProtectedAccess.start(loc: BoundLocInfo, recipe: SmeltingBar, quantity: Int) {
        if (!canSmelt(recipe)) {
            return
        }
        jobs[player] = SmeltJob(recipe, quantity)
        actionDelay = mapClock + SMELT_DELAY
        skillAnimDelay = mapClock + ANIM_INTERVAL
        anim(SmithingSeqs.smelt)
        opLoc4(loc)
    }

    private fun ProtectedAccess.smeltNext(loc: BoundLocInfo) {
        val job = jobs[player] ?: return
        if (!canSmelt(job.recipe)) {
            stop()
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_INTERVAL
            anim(SmithingSeqs.smelt)
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + SMELT_DELAY
        } else if (actionDelay == mapClock) {
            smeltOne(job)
            if (job.remaining <= 0 || !hasIngredients(job.recipe)) {
                stop()
                return
            }
        }

        opLoc4(loc)
    }

    private fun ProtectedAccess.smeltOne(job: SmeltJob) {
        val recipe = job.recipe
        spam("You smelt the ${recipe.oreNames} in the furnace.")

        val (first, second) = recipe.ingredients.let { it[0] to it.getOrNull(1) }
        val removed =
            if (second == null) {
                invDel(inv, first.ore, first.count)
            } else {
                invDel(inv, first.ore, first.count, second.ore, second.count)
            }
        if (!removed.success) {
            job.remaining = 0
            return
        }
        job.remaining--

        val roll = SmeltRoll(player, recipe.bar, random.randomDouble() < recipe.successChance)
        publish(roll)
        if (!roll.success) {
            spam("The ore is too impure and you fail to refine it.")
            return
        }

        if (!invAdd(inv, recipe.bar).success) {
            job.remaining = 0
            return
        }
        spam("You retrieve a bar of ${recipe.noun}.")
        statAdvance(stats.smithing, recipe.xp * xpMods.get(player, stats.smithing))
        publish(BarSmelted(player, recipe.bar))
    }

    /** Checks level and ores. Sends a message for the first problem. */
    private fun ProtectedAccess.canSmelt(recipe: SmeltingBar): Boolean {
        if (player.smithingLvl < recipe.level) {
            val name = objTypes[recipe.bar].name.lowercase()
            val article = if (name.first() in "aeiou") "an" else "a"
            mes("You need a Smithing level of ${recipe.level} to make $article $name.")
            return false
        }
        if (!hasIngredients(recipe)) {
            mes("You do not have the ores needed to smelt this bar.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.hasIngredients(recipe: SmeltingBar): Boolean =
        recipe.ingredients.all { invTotal(inv, it.ore) >= it.count }

    private fun ProtectedAccess.stop() {
        jobs.remove(player)
        resetAnim()
    }

    private class SmeltJob(val recipe: SmeltingBar, var remaining: Int)

    /** What smelting an ore without a furnace gives: a bar, and the Smithing experience for it. */
    class AutoSmelt(val bar: ObjType, val xp: Double)

    /**
     * Published on every smelting attempt, after the normal roll. Listeners can change whether the
     * attempt is a [success], for example to make iron bars never fail.
     */
    class SmeltRoll(val player: Player, val bar: ObjType, var success: Boolean) : UnboundEvent

    /** Published once a bar has been smelted successfully. */
    class BarSmelted(val player: Player, val bar: ObjType) : UnboundEvent

    companion object {
        private const val SMELT_OP = "Smelt"

        /** The most bars one choice can ask for: the quantity buttons stop at an inventory. */
        private const val MAX_QUANTITY = 28

        /**
         * Ticks to wait before each bar is smelted, minus one. The loop spends one extra tick
         * between bars, so this gives a four tick interval.
         */
        private const val SMELT_DELAY = 3
        private const val ANIM_INTERVAL = 4

        /**
         * What a single [ore] turns into when content smelts it without a furnace (the Echo
         * pickaxe), or `null` if [ore] is not an ore. No ore or level is needed.
         */
        fun autoSmelt(ore: ObjType): AutoSmelt? =
            SmeltingBars.autoSmelt(ore)?.let { AutoSmelt(it.bar, it.xp) }

        fun isFurnace(type: UnpackedLocType): Boolean =
            type.name.equals("Furnace", ignoreCase = true) && SMELT_OP in type.op
    }
}
