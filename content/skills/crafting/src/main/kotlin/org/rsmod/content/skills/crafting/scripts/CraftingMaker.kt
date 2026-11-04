package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.ProductionBatch
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.crafting.configs.CraftingMethod
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.configs.CraftingQueues
import org.rsmod.content.skills.crafting.configs.CraftingRecipe
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/** A crafting job in progress: [recipe] still has [remaining] actions left to make. */
class CraftJob(val recipe: CraftingRecipe, var remaining: Int)

/**
 * Makes things for every crafting script: checks the tools, level and materials, uses them up,
 * hands over the product and the experience, and repeats the action on a weak queue until the job
 * is done. Using a weak queue (not a delay) means the player is never held in place: walking away
 * or clicking something else clears the queue and ends the job.
 */
@Singleton
class CraftingMaker
@Inject
constructor(private val objTypes: ObjTypeList, private val xpMods: XpModifiers) {
    /** How many actions each player has done on their current spool of thread. */
    private val threadUses = WeakHashMap<Player, Int>()

    /** Checks [recipe] and, if it can be made, starts [actions] actions of it. */
    fun start(access: ProtectedAccess, recipe: CraftingRecipe, actions: Int) {
        if (!access.canMake(recipe, notify = true)) {
            return
        }
        schedule(access, CraftJob(recipe, actions))
    }

    /**
     * Makes one action of [job] (more with a [ProductionBatch] rule), then queues the next unless
     * the job is done or out of materials.
     */
    fun next(access: ProtectedAccess, job: CraftJob) {
        val recipe = job.recipe
        val made =
            ProductionBatch.make(access.player, job.remaining) { first ->
                access.canMake(recipe, notify = first) && access.makeOnce(recipe)
            }
        if (made == 0) {
            access.resetAnim()
            return
        }
        job.remaining -= made
        // Running out of materials after the first action ends the job without a message.
        if (job.remaining > 0 && access.canMake(recipe, notify = false)) {
            schedule(access, job)
        } else {
            access.resetAnim()
        }
    }

    private fun schedule(access: ProtectedAccess, job: CraftJob) {
        job.recipe.anim?.let { access.anim(it) }
        access.weakQueue(CraftingQueues.make, job.recipe.method.ticks, job)
    }

    /** Checks tools, level, and materials. Sends a message for the first problem if [notify]. */
    fun ProtectedAccess.canMake(recipe: CraftingRecipe, notify: Boolean): Boolean {
        for (tool in recipe.tools) {
            if (invTotal(inv, tool) == 0) {
                if (notify) {
                    mes("You need ${withArticle(nameOf(tool))} to make this.")
                }
                return false
            }
        }
        if (player.craftingLvl < recipe.level) {
            if (notify) {
                mes(
                    "You need a Crafting level of ${recipe.level} to make ${nameOf(recipe.product)}."
                )
            }
            return false
        }
        if (recipe.thread && !hasThread()) {
            if (notify) {
                mes("You need thread to make this.")
            }
            return false
        }
        for (input in recipe.inputs) {
            if (input.options.sumOf { invTotal(inv, it) } < input.count) {
                if (notify) {
                    mes("You do not have the materials to make this.")
                }
                return false
            }
        }
        return true
    }

    private fun ProtectedAccess.hasThread(): Boolean =
        (threadUses[player] ?: 0) > 0 || invTotal(inv, CraftingObjs.thread) > 0

    /**
     * Uses up one action's materials of [recipe] and gives its product. Returns `false` if it could
     * not be made, in which case nothing was changed.
     */
    private fun ProtectedAccess.makeOnce(recipe: CraftingRecipe): Boolean {
        val taken = ArrayList<Pair<ObjType, Int>>()
        if (!takeInputs(recipe, taken)) {
            giveBack(taken)
            return false
        }

        // Gems can be crushed and clay can crack, which gives a different (or no) result.
        val failure = failureOf(recipe)
        val gives = ArrayList<Pair<ObjType, Int>>()
        if (failure == null) {
            gives += recipe.product to recipe.output
            recipe.returns.forEach { gives += it to 1 }
        } else if (failure.gives != null) {
            gives += failure.gives to 1
        }
        for ((obj, count) in gives) {
            if (!invAdd(inv, obj, count).success) {
                giveBack(taken)
                mes("You do not have enough inventory space to make this.")
                return false
            }
        }

        if (recipe.thread) {
            useThread()
        }
        if (failure != null) {
            spam(failure.message)
            failure.xp?.let { statAdvance(stats.crafting, it * xpMods.get(player, stats.crafting)) }
            return true
        }

        spam(describeMaking(recipe, recipe.output))
        if (recipe.xp > 0.0) {
            statAdvance(stats.crafting, recipe.xp * xpMods.get(player, stats.crafting))
        }
        publish(Crafting.ItemCrafted(player, recipe.product, recipe.output))
        return true
    }

    /** What a gem that is crushed or clay that cracks turns into, or `null` if it went well. */
    private fun ProtectedAccess.failureOf(recipe: CraftingRecipe): Failure? {
        val level = player.craftingLvl
        recipe.crush?.let { crush ->
            if (random.of(256) >= crush.successOutOf256(level)) {
                val gem = nameOf(recipe.product)
                return Failure(
                    gives = CraftingObjs.crushed_gem,
                    message = "You mis-cut the $gem and it crumbles into crushed gemstone.",
                    xp = crush.crushXp,
                )
            }
        }
        if (recipe.crack && random.of(256) >= recipe.fireSuccessOutOf256(level)) {
            val unfired = nameOf(recipe.inputs[0].obj)
            return Failure(gives = null, message = "The $unfired cracks in the oven.", xp = null)
        }
        return null
    }

    /** Counts a use of thread, and uses up the spool on its last use. */
    private fun ProtectedAccess.useThread() {
        val uses = (threadUses[player] ?: 0) + 1
        if (uses < CraftingRecipe.THREAD_USES) {
            threadUses[player] = uses
            return
        }
        threadUses.remove(player)
        invDel(inv, CraftingObjs.thread, 1)
        mes("You use up a reel of thread.")
    }

    /**
     * Removes the inputs of one action of [recipe], adding each removal to [taken]. An input that
     * has alternatives is taken from whichever of them the player has, the usual one first.
     */
    private fun ProtectedAccess.takeInputs(
        recipe: CraftingRecipe,
        taken: MutableList<Pair<ObjType, Int>>,
    ): Boolean {
        for (input in recipe.inputs) {
            var needed = input.count
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

    private fun describeMaking(recipe: CraftingRecipe, quantity: Int): String {
        val product = describe(recipe.product, quantity)
        val input = nameOf(recipe.inputs[0].obj)
        return when (recipe.method) {
            CraftingMethod.Chisel ->
                if (input.startsWith("uncut ")) "You cut the ${nameOf(recipe.product)}."
                else "You carefully cut it into $product."
            CraftingMethod.Blow -> "You blow the molten glass into $product."
            CraftingMethod.Spin -> "You spin the $input into $product."
            CraftingMethod.Weave -> "You weave the $input into $product."
            CraftingMethod.Shape -> "You shape the soft clay into $product."
            CraftingMethod.Fire -> "You remove $product from the oven."
            CraftingMethod.Melt ->
                "You heat the sand and soda ash in the furnace to make molten glass."
            CraftingMethod.String -> "You put some string on your amulet."
            CraftingMethod.Soften -> "You mix the clay and the water to make soft clay."
            CraftingMethod.Sew,
            CraftingMethod.Assemble,
            CraftingMethod.Mould -> "You make $product."
        }
    }

    private fun describe(product: ObjType, quantity: Int): String {
        val name = nameOf(product)
        return if (quantity > 1) "$quantity ${plural(name)}" else withArticle(name)
    }

    fun nameOf(obj: ObjType): String = objTypes[obj].name.lowercase()

    /** A failed action: what the player is left with ([gives], if anything), and what to say. */
    private class Failure(val gives: ObjType?, val message: String, val xp: Double?)

    companion object {
        fun withArticle(name: String): String =
            if (name.first() in "aeiou") "an $name" else "a $name"

        fun plural(name: String): String = if (name.endsWith("s")) name else "${name}s"
    }
}
