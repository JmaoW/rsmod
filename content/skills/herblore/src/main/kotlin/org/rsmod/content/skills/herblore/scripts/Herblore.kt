package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.ProductionBatch
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.herblore.configs.Herb
import org.rsmod.content.skills.herblore.configs.HerbloreMethod
import org.rsmod.content.skills.herblore.configs.HerbloreQueues
import org.rsmod.content.skills.herblore.configs.HerbloreRecipe
import org.rsmod.content.skills.herblore.configs.HerbloreRecipes
import org.rsmod.content.skills.herblore.configs.Herbs
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Herblore skillcape emote
// - weapon poisons, antidotes, antivenoms and the barbarian mixes
// - the herb sack, the secondary sources (nothing on the isle gives eye of newt, snape grass and
//   the rest yet) and cleaning herbs with the Degrime spell
//
// Cleaning a grimy herb is instant, one herb to the click. Everything else is offered by using two
// inventory objs on each other, such as a vial of water on a herb or a secondary on an unfinished
// potion, in the chatbox menu that Smelting and Fletching use. A potion takes two ticks and is
// timed with a weak queue rather than a delay, so the player is never held in place: walking away
// or clicking something else clears the queue and ends the job.
class Herblore
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val mods: HerbloreModifiers,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (herb in Herbs.all) {
            onOpHeld1(herb.grimy) { clean(it, herb) }
        }
        for ((first, second) in HerbloreRecipes.triggers()) {
            onOpHeldU(first, second) { choose(first, second) }
        }
        onPlayerQueueWithArgs<MakeJob>(HerbloreQueues.make) { makeNext(it.args) }
    }

    private fun ProtectedAccess.clean(event: HeldObjEvents.Op1, herb: Herb) {
        if (player.herbloreLvl < herb.cleanLevel) {
            mes("You do not have the Herblore level required to clean this herb.")
            return
        }
        // A [ProductionBatch] rule cleans every herb of the kind in the inventory at once.
        val inventory = event.inventory
        val others =
            (0 until inventory.size).filter {
                it != event.slot && inventory[it]?.id == herb.grimy.id
            }
        val count = ProductionBatch.actions(player, others.size + 1)
        for (slot in listOf(event.slot) + others.take(count - 1)) {
            cleanSlot(event, slot, herb)
        }
    }

    private fun ProtectedAccess.cleanSlot(event: HeldObjEvents.Op1, slot: Int, herb: Herb) {
        if (invReplaceSlot(event.inventory, slot, 1, herb.clean).failure) {
            return
        }
        statAdvance(stats.herblore, herb.cleanXp * xpMods.get(player, stats.herblore))
        mes("You clean the dirt from the ${objTypes[herb.clean].name.lowercase()}.")
        publish(HerbCleaned(player, herb))
    }

    private suspend fun ProtectedAccess.choose(first: ObjType, second: ObjType) {
        val offered = HerbloreRecipes.offered(first, second)
        val choice =
            skillMulti(
                title = "What would you like to make?",
                options = offered.map { it.product },
                maxQuantity = MAX_QUANTITY,
            )
        start(offered[choice.index], choice.quantity)
    }

    private fun ProtectedAccess.start(recipe: HerbloreRecipe, actions: Int) {
        if (!canMake(recipe, notify = true)) {
            return
        }
        schedule(MakeJob(recipe, actions))
    }

    /** Starts the animation and queues the potion that comes out once it has finished. */
    private fun ProtectedAccess.schedule(job: MakeJob) {
        job.recipe.anim?.let { anim(it) }
        weakQueue(HerbloreQueues.make, job.recipe.method.ticks, job)
    }

    /**
     * Makes one of [job] (more with a [ProductionBatch] rule, unless the secondary stacks), then
     * queues the next unless the job is done or out of materials.
     */
    private fun ProtectedAccess.makeNext(job: MakeJob) {
        val recipe = job.recipe
        val stackingSecondary = recipe.secondary?.let { objTypes[it].stackable } == true
        val cap = if (stackingSecondary) 1 else Int.MAX_VALUE
        val made =
            ProductionBatch.make(player, job.remaining, cap) { first ->
                canMake(recipe, notify = first) && makeOnce(recipe)
            }
        if (made == 0) {
            resetAnim()
            return
        }
        job.remaining -= made
        // Running out of materials after the first action ends the job without a message.
        if (job.remaining > 0 && canMake(recipe, notify = false)) {
            schedule(job)
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.makeOnce(recipe: HerbloreRecipe): Boolean {
        val keepSecondary = recipe.secondary != null && mods.savesSecondary(player)
        val taken = ArrayList<Pair<ObjType, Int>>()
        for (input in recipe.inputs) {
            if (keepSecondary && input.obj == recipe.secondary) {
                continue
            }
            if (invDel(inv, input.obj, input.count).failure) {
                giveBack(taken)
                return false
            }
            taken += input.obj to input.count
        }

        val extraDoses = if (recipe.method == HerbloreMethod.Potion) mods.extraDoses(player) else 0
        val product = recipe.productWith(extraDoses)
        val quantity =
            if (recipe.outputMax > recipe.output) {
                random.of(recipe.output, recipe.outputMax)
            } else {
                recipe.output
            }
        if (invAdd(inv, product, quantity).failure) {
            giveBack(taken)
            mes("You do not have enough inventory space to make this.")
            return false
        }

        spam(describeMaking(recipe, product, quantity))
        if (recipe.xp > 0) {
            statAdvance(stats.herblore, recipe.xp * xpMods.get(player, stats.herblore))
        }
        publish(RecipeMade(player, recipe, product, quantity))
        recipe.potion?.let {
            val doses = Potion.MAX_DOSES - it.doses.indexOf(product)
            publish(PotionMade(player, it, doses, extraDoses > 0 && product != recipe.product))
        }
        return true
    }

    private fun ProtectedAccess.giveBack(taken: List<Pair<ObjType, Int>>) {
        for ((obj, count) in taken) {
            invAdd(inv, obj, count)
        }
    }

    /** Checks tools, level and materials. Sends a message for the first problem if [notify]. */
    private fun ProtectedAccess.canMake(recipe: HerbloreRecipe, notify: Boolean): Boolean {
        for (tool in recipe.tools) {
            if (invTotal(inv, tool) == 0) {
                if (notify) {
                    mes("You need ${withArticle(objTypes[tool].name.lowercase())} to do this.")
                }
                return false
            }
        }
        if (player.herbloreLvl < recipe.level) {
            if (notify) {
                val name = objTypes[recipe.product].name.lowercase()
                mes("You need a Herblore level of ${recipe.level} to make $name.")
            }
            return false
        }
        for (input in recipe.inputs) {
            if (invTotal(inv, input.obj) < input.count) {
                if (notify) {
                    mes("You do not have the materials to make this.")
                }
                return false
            }
        }
        return true
    }

    private fun describeMaking(recipe: HerbloreRecipe, product: ObjType, quantity: Int): String {
        val name = objTypes[product].name.lowercase()
        return when (recipe.method) {
            HerbloreMethod.Unfinished -> "You put the herb into the vial of water."
            HerbloreMethod.Potion -> "You mix the ingredient into your potion and make $name."
            HerbloreMethod.Upgrade -> "You mix the ingredients together and make $name."
            HerbloreMethod.Grind ->
                if (quantity > 1) "You make $quantity ${plural(name)}." else "You make $name."
        }
    }

    /** A herblore job in progress: [recipe] still has [remaining] actions left to make. */
    private class MakeJob(val recipe: HerbloreRecipe, var remaining: Int)

    /** Published when a grimy herb has been cleaned. */
    class HerbCleaned(val player: Player, val herb: Herb) : UnboundEvent

    /** Published once a recipe has been made, with the [product] and how many of it. */
    class RecipeMade(
        val player: Player,
        val recipe: HerbloreRecipe,
        val product: ObjType,
        val quantity: Int,
    ) : UnboundEvent

    /**
     * Published once a potion has been made, with the number of [doses] it has. [bonusDose] is
     * `true` when it has more than the usual three.
     */
    class PotionMade(
        val player: Player,
        val potion: Potion,
        val doses: Int,
        val bonusDose: Boolean,
    ) : UnboundEvent

    private companion object {
        /** The most actions one choice can ask for: the quantity buttons stop at an inventory. */
        const val MAX_QUANTITY = 28

        fun withArticle(name: String): String =
            if (name.first() in "aeiou") "an $name" else "a $name"

        fun plural(name: String): String = if (name.endsWith("s")) name else "${name}s"
    }
}
