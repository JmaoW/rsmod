package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.crafting.configs.CraftingInterfaces
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.configs.CraftingRecipe
import org.rsmod.content.skills.crafting.configs.CraftingVarps
import org.rsmod.content.skills.crafting.configs.GoldComponents
import org.rsmod.content.skills.crafting.configs.JewelleryItem
import org.rsmod.content.skills.crafting.configs.JewelleryRecipes
import org.rsmod.content.skills.crafting.configs.SilverComponents
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.InterfaceType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the gold tiara, which the wiki lists but the client's gold interface has no button for
// - the silver interface's quest items (lightning rod, Ivandis, Silvthrill rod, Demonic sigil)
//
// Using a gold or silver bar on a furnace opens the real client interface for it (the main modal
// that Smithing's anvil also uses). The client draws everything itself: it greys out what the
// player has no mould, gem, bar or level for. A click on a product makes it, and the quantity
// buttons ("1", "5", "10", "X", "All") at the bottom are stored in the varp `makexcrafting`, which
// the client reads back to highlight the chosen button. Nothing is made until a product is clicked.
class Jewellery
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val maker: CraftingMaker,
    private val slayer: SlayerEngine,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            if (isFurnace(type)) {
                onOpLocU(type, CraftingObjs.gold_bar) { open(CraftingInterfaces.gold) }
                onOpLocU(type, CraftingObjs.silver_bar) { open(CraftingInterfaces.silver) }
            }
        }

        for (group in JewelleryRecipes.all.groupBy { it.component.packed }.values) {
            onIfModalButton(group.first().component) { make(group) }
        }

        quantityButtons(
            GoldComponents.make_1,
            GoldComponents.make_5,
            GoldComponents.make_10,
            GoldComponents.make_x,
            GoldComponents.make_all,
        )
        quantityButtons(
            SilverComponents.make_1,
            SilverComponents.make_5,
            SilverComponents.make_10,
            SilverComponents.make_x,
            SilverComponents.make_all,
        )
    }

    private fun ScriptContext.quantityButtons(
        one: ComponentType,
        five: ComponentType,
        ten: ComponentType,
        custom: ComponentType,
        all: ComponentType,
    ) {
        onIfModalButton(one) { vars[CraftingVarps.make_x] = 1 }
        onIfModalButton(five) { vars[CraftingVarps.make_x] = 5 }
        onIfModalButton(ten) { vars[CraftingVarps.make_x] = 10 }
        onIfModalButton(all) { vars[CraftingVarps.make_x] = MAX_QUANTITY }
        onIfModalButton(custom) {
            val amount = countDialog().coerceIn(1, MAX_QUANTITY)
            vars[CraftingVarps.make_x] = amount
        }
    }

    private fun ProtectedAccess.open(interf: InterfaceType) {
        ifOpenMainModal(interf)
    }

    /**
     * Makes the product of the button clicked. A button can stand for more than one recipe (the
     * Slayer ring needs either kind of gem), in which case the first the player can make is used.
     * Recipes that need a Slayer unlock the player has not bought are left out.
     */
    private fun ProtectedAccess.make(group: List<JewelleryItem>) {
        ifClose()
        val available =
            group.filter { it.slayerUnlock == null || slayer.isUnlocked(player, it.slayerUnlock) }
        if (available.isEmpty()) {
            // Only the Slayer rings need an unlock, and that is Ring Bling.
            mes("You need to buy the Ring Bling unlock from the Slayer rewards to make this.")
            return
        }
        val recipe =
            available.map { it.recipe }.firstOrNull { hasMaterials(it) } ?: available.first().recipe
        val quantity = vars[CraftingVarps.make_x].let { if (it <= 0) MAX_QUANTITY else it }
        if (!hasMaterials(recipe)) {
            mes("You do not have the materials to make this.")
            return
        }
        maker.start(this, recipe, quantity.coerceAtMost(MAX_QUANTITY))
    }

    private fun ProtectedAccess.hasMaterials(recipe: CraftingRecipe): Boolean =
        recipe.tools.all { invTotal(inv, it) > 0 } &&
            recipe.inputs.all { input -> input.options.sumOf { invTotal(inv, it) } >= input.count }

    private fun isFurnace(type: UnpackedLocType): Boolean =
        type.name.equals("Furnace", ignoreCase = true) && type.op.take(3).any { it == SMELT_OP }

    private companion object {
        const val SMELT_OP = "Smelt"

        /** The most actions one choice can ask for: the quantity buttons stop at an inventory. */
        const val MAX_QUANTITY = 28
    }
}
