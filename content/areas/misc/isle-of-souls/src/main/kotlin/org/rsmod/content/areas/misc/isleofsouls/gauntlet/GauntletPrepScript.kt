package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.events.EventBus
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Preparing in the maze: gathering from the resource nodes, the start room's stations (the tool
 * storage and sceptre, the water pump, the range, the singing bowl and the two books), making
 * Egniol potions and using the escape crystal.
 *
 * The wiki gives no rates for gathering, cooking or the pump; those are Near-Reality's, as TSPS
 * found them: a harvest every two ticks with 1 experience, a burn chance of `48 - level` per cent
 * (at most 34), and one vial or fish a tick.
 */
class GauntletPrepScript
@Inject
constructor(
    private val gauntlet: Gauntlet,
    private val resources: GauntletResources,
    private val locRepo: LocRepository,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
    private val crafting: GauntletCrafting,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (kind in GauntletNode.entries) {
            for (mode in GauntletMode.entries) {
                onOpLoc1(kind.type(mode)) { gather(it.loc, kind) }
            }
        }

        onOpLoc1(gauntlet_locs.tools) { takeTools() }
        onOpLoc1(gauntlet_locs.tools_hm) { takeTools() }
        onOpLoc1(gauntlet_locs.sceptre_holder) { takeSceptre() }
        onOpLoc1(gauntlet_locs.sceptre_holder_hm) { takeSceptre() }

        onOpLoc1(gauntlet_locs.sink) { fillVials() }
        onOpLoc1(gauntlet_locs.sink_hm) { fillVials() }
        onOpLocU(gauntlet_locs.sink, GauntletRegularItems.vial) { fillVials() }
        onOpLocU(gauntlet_locs.sink_hm, GauntletCorruptedItems.vial) { fillVials() }
        onOpLocU(gauntlet_locs.pond, GauntletRegularItems.vial) { fillVials() }
        onOpLocU(gauntlet_locs.pond_hm, GauntletCorruptedItems.vial) { fillVials() }

        // The ranges are cooked on by the Cooking module (paddlefish is one of its foods); the
        // Gauntlet only counts the point for each one cooked.
        onEvent<Cooking.Cooked> {
            if (cooked.id == GauntletShared.food.id) {
                gauntlet.runOf(player)?.addPoints(player, GauntletRecipes.COOK_POINTS)
            }
        }

        onOpLoc1(gauntlet_locs.bowl) { sing() }
        onOpLoc1(gauntlet_locs.bowl_hm) { sing() }
        onOpLoc1(gauntlet_locs.book) {
            player.ifOpenMainModal(gauntlet_interfaces.recipes, eventBus)
        }
        onOpLoc1(gauntlet_locs.book_hm) {
            player.ifOpenMainModal(gauntlet_interfaces.recipes, eventBus)
        }
        onOpLoc1(gauntlet_locs.book_egniol) { readEgniol() }
        onOpLoc1(gauntlet_locs.book_egniol_hm) { readEgniol() }

        for (items in listOf(GauntletRegularItems, GauntletCorruptedItems)) {
            onOpHeldU(items.grymLeaf, GauntletShared.waterVial) {
                with(crafting) { addLeaf(items) }
            }
            onOpHeldU(items.pestle, items.shard) { with(crafting) { grind(items) } }
            onOpHeldU(items.dust, GauntletShared.grymPotion) {
                with(crafting) { finishPotion(items) }
            }
            onOpHeld1(items.escapeCrystal) { escape() }
        }
    }

    private fun ProtectedAccess.advance(stat: StatType, xp: Double) {
        statAdvance(stat, xp * xpMods.get(player, stat))
    }

    private fun ProtectedAccess.holds(obj: ObjType): Boolean = obj in inv || obj in worn

    // Gathering ---------------------------------------------------------------------------------

    /**
     * A harvest every [GauntletNode.YIELD_TICKS] ticks until the node is spent, the pack is full or
     * the player walks off. The first call starts the clock; the op then repeats itself each tick,
     * as the other gathering skills do.
     */
    private fun ProtectedAccess.gather(loc: BoundLocInfo, kind: GauntletNode) {
        val run = gauntlet.runOf(player) ?: return
        val items = run.mode.items
        val tool = kind.tool(items)
        if (tool != null && !holds(tool)) {
            mes(checkNotNull(kind.noTool))
            return
        }
        val product = kind.product(items)
        if (inv.isFull() && product !in inv) {
            mes(kind.full)
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + GauntletNode.YIELD_TICKS
            skillAnimDelay = mapClock + ANIM_TICKS
            kind.start?.let { mes(it) }
            anim(kind.anim(run.mode))
            opLoc1(loc)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(kind.anim(run.mode))
        }
        if (actionDelay == mapClock) {
            val spent = harvest(run, loc, kind, product)
            if (spent) {
                return
            }
            actionDelay = mapClock + GauntletNode.YIELD_TICKS
        }
        opLoc1(loc)
    }

    /** One harvest. Returns `true` if it spent the node. */
    private fun ProtectedAccess.harvest(
        run: GauntletRun,
        loc: BoundLocInfo,
        kind: GauntletNode,
        product: ObjType,
    ): Boolean {
        if (!invAdd(inv, product).success) {
            mes(kind.full)
            return true
        }
        mes(kind.gained)
        advance(kind.stat, HARVEST_XP)
        rollShards(run)
        if (!GauntletNode.NODES_DEPLETE) {
            return false
        }
        val left = (run.nodeLeft[loc.coords] ?: kind.harvests) - 1
        if (left > 0) {
            run.nodeLeft[loc.coords] = left
            return false
        }
        run.nodeLeft.remove(loc.coords)
        kind.spent?.let { mes(it) }
        locRepo.change(loc, kind.spentType(run.mode), Int.MAX_VALUE)
        return true
    }

    private fun ProtectedAccess.rollShards(run: GauntletRun) {
        if (gauntlet.pick(GauntletNode.SHARD_ODDS) != 0) {
            return
        }
        val range = GauntletNode.SHARDS_MAX - GauntletNode.SHARDS_MIN + 1
        val amount = GauntletNode.SHARDS_MIN + gauntlet.pick(range)
        invAdd(inv, run.mode.items.shard, amount)
        mes("You find $amount ${if (run.mode.corrupted) "corrupted" else "crystal"} shards.")
    }

    // The start room ----------------------------------------------------------------------------

    private class Tool(val obj: ObjType, val label: String)

    private suspend fun ProtectedAccess.takeTools() {
        val run = gauntlet.runOf(player) ?: return
        val items = run.mode.items
        val missing =
            listOf(
                    Tool(items.sceptre, "a sceptre"),
                    Tool(items.axe, "an axe"),
                    Tool(items.pickaxe, "a pickaxe"),
                    Tool(items.harpoon, "a harpoon"),
                    Tool(items.pestle, "a pestle and mortar"),
                )
                .filterNot { holds(it.obj) }
        if (missing.isEmpty()) {
            mesbox("You do not need anything available here.")
            return
        }
        if (missing.size == 1) {
            give(missing)
            return
        }
        // The chat menu holds five choices: everything, then the first four tools.
        val shown = missing.take(MENU_TOOLS)
        val labels = listOf("Take everything.") + shown.map { "Take ${it.label}." }
        val title = "What would you like to take?"
        val choice =
            when (labels.size) {
                3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
                4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
                else ->
                    choice5(
                        labels[0],
                        0,
                        labels[1],
                        1,
                        labels[2],
                        2,
                        labels[3],
                        3,
                        labels[4],
                        4,
                        title = title,
                    )
            }
        give(if (choice == 0) missing else listOf(shown[choice - 1]))
    }

    private fun ProtectedAccess.give(tools: List<Tool>) {
        for (tool in tools) {
            if (!invAdd(inv, tool.obj).success) {
                mes("You don't have enough inventory space.")
                return
            }
        }
    }

    private fun ProtectedAccess.takeSceptre() {
        val run = gauntlet.runOf(player) ?: return
        val sceptre = run.mode.items.sceptre
        if (holds(sceptre)) {
            mes("You already have a sceptre.")
        } else {
            give(listOf(Tool(sceptre, "a sceptre")))
        }
    }

    private suspend fun ProtectedAccess.fillVials() {
        val run = gauntlet.runOf(player) ?: return
        val vial = run.mode.items.vial
        if (vial !in inv) {
            mes("You have no vials to fill.")
            return
        }
        while (vial in inv && gauntlet.runOf(player) === run) {
            anim(gauntlet_seqs.fill)
            delay(1)
            if (!invDel(inv, vial, 1).success) {
                break
            }
            invAdd(inv, GauntletShared.waterVial)
            mes("You fill a crystal vial with water.")
        }
    }

    // The singing bowl --------------------------------------------------------------------------

    private suspend fun ProtectedAccess.sing() {
        // The menu comes back after each make; closing it (or moving away) ends the loop.
        while (true) {
            val run = gauntlet.runOf(player) ?: return
            val recipes = GauntletRecipes.available(run.mode) { holds(it) }
            if (recipes.isEmpty()) {
                return
            }
            val choice =
                skillMulti(
                    title = "What would you like to make?",
                    options = recipes.map { it.product },
                    maxQuantity = MAX_QUANTITY,
                    suggestedQuantity = 1,
                )
            with(crafting) { make(run, recipes[choice.index], choice.quantity) }
        }
    }

    private fun ProtectedAccess.readEgniol() {
        for (line in EGNIOL_TEXT) {
            mes(line)
        }
    }

    // Egniol potions ----------------------------------------------------------------------------

    // The escape crystal ------------------------------------------------------------------------

    private fun ProtectedAccess.escape() {
        gauntlet.useEscapeCrystal(player)
    }

    private companion object {
        const val HARVEST_XP = 1.0
        const val ANIM_TICKS = 4
        const val MENU_TOOLS = 4
        const val MAX_QUANTITY = 28

        val EGNIOL_TEXT =
            listOf(
                "Those hoping to survive the Gauntlet will need to take advantage of the Grym " +
                    "roots found within the dungeon.",
                "The leaves that grow on these roots can be used to create Egniol potions, which " +
                    "are able to restore both energy and divinity.",
                "Fill a vial with water. Add a Grym leaf to the vial. Crush ten crystal shards. " +
                    "Add the crystal dust to the vial.",
            )
    }
}
