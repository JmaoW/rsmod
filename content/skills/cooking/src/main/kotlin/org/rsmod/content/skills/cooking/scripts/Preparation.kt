package org.rsmod.content.skills.cooking.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.cooking.configs.CookingQueues
import org.rsmod.content.skills.cooking.configs.CookingTimers
import org.rsmod.content.skills.cooking.configs.PrepRecipe
import org.rsmod.content.skills.cooking.configs.PrepRecipes
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - butter, cheese, cream and flour, which need a dairy churn or a windmill
// - the gnome restaurant dishes and the rest of the recipes that need a special place
//
// Everything in [PrepRecipes] is made by using two items on each other (or a tool and an item):
// the same menu Herblore and Fletching use offers each recipe that fits, and a weak queue makes one
// item every two ticks, so walking away or clicking something else ends the job.
class Preparation
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {
    /** A recipe with its items found in the cache. */
    private class Resolved(
        val recipe: PrepRecipe,
        val inputs: List<Pair<ObjType, Int>>,
        val outputs: List<Pair<ObjType, Int>>,
        val tools: List<ObjType>,
    ) {
        val product: ObjType
            get() = outputs.first().first

        /** Every pair of objs that, used on each other, offers this recipe. */
        fun triggers(): List<Pair<ObjType, ObjType>> {
            val objs = (tools + inputs.map { it.first }).distinctBy { it.id }
            val pairs = ArrayList<Pair<ObjType, ObjType>>()
            for (i in objs.indices) {
                for (j in i + 1 until objs.size) {
                    pairs += objs[i] to objs[j]
                }
            }
            return pairs
        }
    }

    /** A job in progress: [recipe] still has [remaining] items left to make. */
    private class Job(val recipe: Resolved, var remaining: Int)

    /** The cycle each player's unfermented wine finishes fermenting. */
    private val fermentAt = WeakHashMap<Player, Int>()

    private var recipes: List<Resolved> = emptyList()
    private var wine: ObjType? = null
    private var badWine: ObjType? = null
    private val unfermentedWine: ObjType?
        get() = recipes.firstOrNull { it.recipe.ferments }?.product

    override fun ScriptContext.startup() {
        val index = NameIndex(objTypes.values)
        recipes = PrepRecipes.all.mapNotNull { resolve(it, index) }
        wine = index.find("Jug of wine")
        badWine = index.find("Jug of bad wine")
        val seen = HashSet<Long>()
        for (recipe in recipes) {
            for ((first, second) in recipe.triggers()) {
                if (seen.add(key(first, second))) {
                    onOpHeldU(first, second) { choose(first, second) }
                }
            }
        }
        onPlayerQueueWithArgs<Job>(CookingQueues.prepare) { makeNext(it.args) }
        onPlayerSoftTimer(CookingTimers.wine) { ferment(player) }
    }

    private fun resolve(recipe: PrepRecipe, index: NameIndex): Resolved? {
        val inputs = recipe.inputs.map { (index.find(it.name) ?: return null) to it.count }
        val outputs = recipe.outputs.map { (index.find(it.name) ?: return null) to it.count }
        val tools = recipe.tools.map { index.find(it) ?: return null }
        return Resolved(recipe, inputs, outputs, tools)
    }

    private fun key(first: ObjType, second: ObjType): Long {
        val low = minOf(first.id, second.id).toLong()
        val high = maxOf(first.id, second.id).toLong()
        return (low shl 32) or high
    }

    private suspend fun ProtectedAccess.choose(first: ObjType, second: ObjType) {
        val offered =
            recipes.filter { r ->
                r.triggers().any { key(it.first, it.second) == key(first, second) }
            }
        if (offered.isEmpty()) {
            return
        }
        val choice =
            skillMulti(
                title = "What would you like to make?",
                options = offered.map { it.product },
                maxQuantity = MAX_QUANTITY,
            )
        val recipe = offered[choice.index]
        if (!canMake(recipe, notify = true)) {
            return
        }
        schedule(Job(recipe, choice.quantity))
    }

    private fun ProtectedAccess.schedule(job: Job) {
        weakQueue(CookingQueues.prepare, TICKS, job)
    }

    /** Makes one of [job], then queues the next unless the job is done or out of materials. */
    private fun ProtectedAccess.makeNext(job: Job) {
        if (!canMake(job.recipe, notify = true) || !makeOnce(job.recipe)) {
            return
        }
        job.remaining--
        if (job.remaining > 0 && canMake(job.recipe, notify = false)) {
            schedule(job)
        }
    }

    private fun ProtectedAccess.makeOnce(resolved: Resolved): Boolean {
        val taken = ArrayList<Pair<ObjType, Int>>()
        for ((obj, count) in resolved.inputs) {
            if (invDel(inv, obj, count).failure) {
                giveBack(taken)
                return false
            }
            taken += obj to count
        }
        val added = ArrayList<Pair<ObjType, Int>>()
        for ((obj, count) in resolved.outputs) {
            if (invAdd(inv, obj, count).failure) {
                for ((done, n) in added) {
                    invDel(inv, done, n)
                }
                giveBack(taken)
                mes("You do not have enough inventory space to make this.")
                return false
            }
            added += obj to count
        }

        val name = objTypes[resolved.product].name.lowercase()
        spam("You make ${withArticle(name)}.")
        val recipe = resolved.recipe
        if (recipe.xp > 0) {
            statAdvance(stats.cooking, recipe.xp * xpMods.get(player, stats.cooking))
        }
        if (recipe.ferments) {
            fermentAt[player] = mapClock + FERMENT_TICKS
            softTimer(CookingTimers.wine, 1)
        }
        publish(Prepared(player, resolved.product))
        return true
    }

    private fun ProtectedAccess.giveBack(taken: List<Pair<ObjType, Int>>) {
        for ((obj, count) in taken) {
            invAdd(inv, obj, count)
        }
    }

    /** Checks tools, level and materials. Sends a message for the first problem if [notify]. */
    private fun ProtectedAccess.canMake(recipe: Resolved, notify: Boolean): Boolean {
        for (tool in recipe.tools) {
            if (invTotal(inv, tool) == 0) {
                if (notify) {
                    mes("You need ${withArticle(objTypes[tool].name.lowercase())} to do this.")
                }
                return false
            }
        }
        if (player.cookingLvl < recipe.recipe.level) {
            if (notify) {
                val name = objTypes[recipe.product].name.lowercase()
                mes("You need a Cooking level of ${recipe.recipe.level} to make $name.")
            }
            return false
        }
        for ((obj, count) in recipe.inputs) {
            if (invTotal(inv, obj) < count) {
                if (notify) {
                    mes("You do not have the materials to make this.")
                }
                return false
            }
        }
        return true
    }

    /**
     * The timer of unfermented wine. Once the wait is over, all the unfermented wine the player has
     * ferments at once: wine, or bad wine if the roll fails. As the wiki says, the wine is divided
     * into fifteen batches that are rolled for together, and the few that are left over one by one.
     */
    private fun ferment(player: Player) {
        val ready = fermentAt[player]
        if (ready == null) {
            player.clearSoftTimer(CookingTimers.wine)
            return
        }
        launcher.launch(player) {
            if (mapClock < ready) {
                return@launch
            }
            fermentAt.remove(player)
            clearSoftTimer(CookingTimers.wine)
            fermentWine()
        }
    }

    private fun ProtectedAccess.fermentWine() {
        val unfermented = unfermentedWine ?: return
        val wine = wine ?: return
        val bad = badWine ?: return
        val total = invTotal(inv, unfermented)
        if (total == 0) {
            return
        }
        val chance = ((19.0 + 119.0 * (player.cookingLvl - 1) / 98.0) / 100.0).coerceAtMost(1.0)
        val batch = total / BATCHES
        var good = 0
        repeat(BATCHES) {
            if (batch > 0 && random.randomDouble() < chance) {
                good += batch
            }
        }
        repeat(total - batch * BATCHES) {
            if (random.randomDouble() < chance) {
                good++
            }
        }
        if (invDel(inv, unfermented, total).failure) {
            return
        }
        invAdd(inv, wine, good)
        invAdd(inv, bad, total - good)
        if (good > 0) {
            statAdvance(stats.cooking, WINE_XP * good * xpMods.get(player, stats.cooking))
        }
        spam("The wine has finished fermenting.")
        publish(Prepared(player, wine, good))
    }

    /** The objs by name, without noted copies and placeholders, lowest id first. */
    private class NameIndex(types: Collection<org.rsmod.game.type.obj.UnpackedObjType>) {
        private val byName =
            types
                .filter { it.certtemplate <= 0 && it.placeholdertemplate <= 0 }
                .sortedBy { it.id }
                .groupBy { it.name }

        /** The obj called [text], or for `Name#2` the second obj with that name. */
        fun find(text: String): ObjType? {
            val name = text.substringBefore('#')
            val nth = text.substringAfter('#', "1").toInt()
            return byName[name]?.getOrNull(nth - 1)
        }
    }

    /** Published once an item has been made, with how many ([count] is more than 1 for wine). */
    class Prepared(val player: Player, val product: ObjType, val count: Int = 1) : UnboundEvent

    private companion object {
        /** The most items one choice can ask for: the quantity buttons stop at an inventory. */
        const val MAX_QUANTITY = 28

        /** Ticks between each item of a job. */
        const val TICKS = 2

        /** Ticks the wine ferments for, starting again with every unfermented wine made. */
        const val FERMENT_TICKS = 20

        const val BATCHES = 15
        const val WINE_XP = 200.0

        fun withArticle(name: String): String =
            if (name.first() in "aeiou") "an $name" else "a $name"
    }
}
