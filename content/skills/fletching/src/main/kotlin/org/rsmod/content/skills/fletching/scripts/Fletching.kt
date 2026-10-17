package org.rsmod.content.skills.fletching.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.fletching.configs.FletchingMethod
import org.rsmod.content.skills.fletching.configs.FletchingQueues
import org.rsmod.content.skills.fletching.configs.FletchingRecipe
import org.rsmod.content.skills.fletching.configs.FletchingRecipes
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Fletching skillcape emote and the Fletcher's outfit
// - amethyst tips (Crafting), the ogre bow and ogre arrows (quest items)
//
// A recipe is offered by using two inventory objs on each other, such as a knife on logs or a bow
// string on an unstrung bow. Every recipe a pair offers is listed in the chatbox menu that Smelting
// uses, and the player makes the one picked as many times as asked, a batch at a time. Batches are
// timed with a weak queue rather than a delay, so the player is never held in place: walking away
// or clicking something else clears the queue and ends the job.
class Fletching
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val slayer: SlayerEngine,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((first, second) in FletchingRecipes.triggers()) {
            onOpHeldU(first, second) { choose(first, second) }
        }
        onPlayerQueueWithArgs<FletchJob>(FletchingQueues.make) { makeNext(it.args) }
    }

    private suspend fun ProtectedAccess.choose(first: ObjType, second: ObjType) {
        val offered = FletchingRecipes.offered(first, second).filter { isUnlocked(it) }
        if (offered.isEmpty()) {
            mes("You need to buy the Broader Fletching Slayer unlock to make this.")
            return
        }
        val choice =
            skillMulti(
                title = "What would you like to make?",
                options = offered.map { it.product },
                maxQuantity = MAX_QUANTITY,
            )
        start(offered[choice.index], choice.quantity)
    }

    /** Whether the player has the Slayer unlock a recipe needs, if it needs one. */
    private fun ProtectedAccess.isUnlocked(recipe: FletchingRecipe): Boolean =
        recipe.slayerUnlock == null || slayer.isUnlocked(player, recipe.slayerUnlock)

    private fun ProtectedAccess.start(recipe: FletchingRecipe, actions: Int) {
        if (!canMake(recipe, notify = true)) {
            return
        }
        schedule(FletchJob(recipe, actions))
    }

    /** Starts the animation and queues the batch that comes out once it has finished. */
    private fun ProtectedAccess.schedule(job: FletchJob) {
        job.recipe.anim?.let { anim(it) }
        weakQueue(FletchingQueues.make, job.recipe.method.ticks, job)
    }

    /**
     * Makes one batch of [job], then queues the next unless the job is done or out of materials.
     */
    private fun ProtectedAccess.makeNext(job: FletchJob) {
        val recipe = job.recipe
        if (!canMake(recipe, notify = true) || !makeOnce(recipe)) {
            resetAnim()
            return
        }
        job.remaining--
        // Running out of materials after the first action ends the job without a message.
        if (job.remaining > 0 && canMake(recipe, notify = false)) {
            schedule(job)
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.makeOnce(recipe: FletchingRecipe): Boolean {
        val units = unitsFor(recipe)
        val taken = ArrayList<Pair<ObjType, Int>>()
        if (!takeInputs(recipe, units, taken)) {
            giveBack(taken)
            return false
        }

        val quantity = recipe.output * units
        if (!invAdd(inv, recipe.product, quantity).success) {
            giveBack(taken)
            mes("You do not have enough inventory space to make this.")
            return false
        }

        spam(describeMaking(recipe, quantity))
        statAdvance(stats.fletching, recipe.xp * units * xpMods.get(player, stats.fletching))
        publish(ItemFletched(player, recipe.product, quantity))
        return true
    }

    /**
     * Removes what [units] of [recipe] use up, adding each removal to [taken]. An input that has
     * alternatives is taken from whichever of them the player has, the usual one first.
     */
    private fun ProtectedAccess.takeInputs(
        recipe: FletchingRecipe,
        units: Int,
        taken: MutableList<Pair<ObjType, Int>>,
    ): Boolean {
        for (input in recipe.inputs) {
            var needed = input.count * units
            for (option in input.options) {
                val take = minOf(needed, invTotal(inv, option))
                if (take == 0) {
                    continue
                }
                if (!invDel(inv, option, take).success) {
                    return false
                }
                taken += option to take
                needed -= take
            }
            if (needed > 0) {
                return false
            }
        }
        return true
    }

    private fun ProtectedAccess.giveBack(taken: List<Pair<ObjType, Int>>) {
        for ((obj, count) in taken) {
            invAdd(inv, obj, count)
        }
    }

    /** Checks tools, level, and materials. Sends a message for the first problem if [notify]. */
    private fun ProtectedAccess.canMake(recipe: FletchingRecipe, notify: Boolean): Boolean {
        for (tool in recipe.tools) {
            if (invTotal(inv, tool) == 0) {
                if (notify) {
                    val name = objTypes[tool].name.lowercase()
                    mes("You need ${withArticle(name)} to make this.")
                }
                return false
            }
        }
        if (player.fletchingLvl < recipe.level) {
            if (notify) {
                val name = objTypes[recipe.product].name.lowercase()
                mes("You need a Fletching level of ${recipe.level} to make $name.")
            }
            return false
        }
        if (unitsFor(recipe) < 1) {
            if (notify) {
                mes("You do not have the materials to make this.")
            }
            return false
        }
        return true
    }

    /** How many units the player can make in one action: as many as the materials allow. */
    private fun ProtectedAccess.unitsFor(recipe: FletchingRecipe): Int {
        var units = recipe.maxUnits
        for (input in recipe.inputs) {
            val owned = input.options.sumOf { invTotal(inv, it) }
            units = minOf(units, owned / input.count)
        }
        return units
    }

    private fun describeMaking(recipe: FletchingRecipe, quantity: Int): String {
        val product = describe(recipe.product, quantity)
        return when (recipe.method) {
            FletchingMethod.Cut -> "You carefully cut the wood into $product."
            FletchingMethod.Stringing -> "You add a string to the bow and make $product."
            FletchingMethod.Assemble -> "You fit the limbs to the stock and make $product."
            FletchingMethod.Attach -> "You make $product."
            FletchingMethod.Chisel -> "You carefully cut the gem into $product."
        }
    }

    private fun describe(product: ObjType, quantity: Int): String {
        val name = objTypes[product].name.lowercase()
        return if (quantity > 1) "$quantity ${plural(name)}" else withArticle(name)
    }

    /** A fletching job in progress: [recipe] still has [remaining] batches left to make. */
    private class FletchJob(val recipe: FletchingRecipe, var remaining: Int)

    /**
     * Published once something has been fletched. [quantity] is how many of [product] the action
     * gave, as arrows and shafts come in batches.
     */
    class ItemFletched(val player: Player, val product: ObjType, val quantity: Int) : UnboundEvent

    /** What cutting a single log into arrow shafts gives: [count] shafts and [xp] experience. */
    class AutoShafts(val count: Int, val xp: Double)

    companion object {
        /**
         * What one of [logs] gives when content cuts it into arrow shafts without a knife (the Echo
         * axe), or `null` if those logs make none. No level is needed.
         */
        fun autoShafts(logs: ObjType): AutoShafts? =
            FletchingRecipes.shaftsFrom(logs)?.let { AutoShafts(it.output, it.xp) }

        /** The most actions one choice can ask for: the quantity buttons stop at an inventory. */
        private const val MAX_QUANTITY = 28

        private fun withArticle(name: String): String =
            if (name.first() in "aeiou") "an $name" else "a $name"

        private fun plural(name: String): String = if (name.endsWith("s")) name else "${name}s"
    }
}
