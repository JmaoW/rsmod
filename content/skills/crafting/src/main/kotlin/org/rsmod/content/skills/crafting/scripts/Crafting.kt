package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.crafting.configs.CraftingMethod
import org.rsmod.content.skills.crafting.configs.CraftingQueues
import org.rsmod.content.skills.crafting.configs.CraftingRecipes
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Crafting skillcape emote and the Crafting guild outfit
// - the Jeweller's chisel (not in the cache), the Costume needle (needs a quest)
// - crystal singing and golem crafting (their own minigames, see the crafting plan doc)
//
// A recipe is offered by using two inventory objs on each other, such as a needle on leather or a
// glassblowing pipe on molten glass. Every recipe a pair offers is listed in the chatbox menu that
// Smelting uses, and the player makes the one picked as many times as asked. A chisel on a single
// gem or shell has only one thing to make, so it starts at once and runs until the stack is gone.
// The recipe table is in [CraftingRecipes]; making things is in [CraftingMaker].
class Crafting @Inject constructor(private val maker: CraftingMaker) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((first, second) in CraftingRecipes.triggers()) {
            onOpHeldU(first, second) { choose(first, second) }
        }
        onPlayerQueueWithArgs<CraftJob>(CraftingQueues.make) { maker.next(this, it.args) }
    }

    private suspend fun ProtectedAccess.choose(first: ObjType, second: ObjType) {
        val offered = CraftingRecipes.offered(first, second)
        val only = offered.singleOrNull()
        if (only != null && only.method in START_AT_ONCE) {
            maker.start(this, only, MAX_QUANTITY)
            return
        }
        val choice =
            skillMulti(
                title = "What would you like to make?",
                options = offered.map { it.product },
                maxQuantity = MAX_QUANTITY,
            )
        maker.start(this, offered[choice.index], choice.quantity)
    }

    /**
     * Published once something has been crafted, after the materials were used. [quantity] is how
     * many of [product] the action gave, as tips and cups come in batches.
     */
    class ItemCrafted(val player: Player, val product: ObjType, val quantity: Int) : UnboundEvent

    private companion object {
        /** Recipes with nothing to choose between just run until the materials do. */
        val START_AT_ONCE =
            setOf(CraftingMethod.Chisel, CraftingMethod.String, CraftingMethod.Soften)

        /** The most actions one choice can ask for: the quantity buttons stop at an inventory. */
        const val MAX_QUANTITY = 28
    }
}
