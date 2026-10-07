package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.configs.CraftingRecipe
import org.rsmod.content.skills.crafting.configs.CraftingRecipes
import org.rsmod.content.skills.crafting.configs.CraftingStation
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Spinning wheels, looms, potter's wheels, pottery ovens, and the molten glass made in a furnace.
//
// Clicking a station (or using something it can work on with it) lists what it makes in the
// chatbox menu, and the player makes the one picked as many times as asked. The recipes are in
// [org.rsmod.content.skills.crafting.configs.StationRecipes]. Furnaces are shared with Smelting:
// the furnace's use-with handler there covers ores, and the specific one here (sand, soda ash)
// wins for those objs.
class Stations
@Inject
constructor(private val locTypes: LocTypeList, private val maker: CraftingMaker) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            val station = stationOf(type)
            if (station != null) {
                registerStation(type, station)
            } else if (isFurnace(type)) {
                registerFurnace(type)
            }
        }
    }

    private fun ScriptContext.registerStation(type: UnpackedLocType, station: StationInfo) {
        if (!onOpLocNamed(type, station.op) { choose(station.station, null) }) {
            return
        }
        onOpLocU(type) { choose(station.station, it.objType) }
    }

    private fun ScriptContext.registerFurnace(type: UnpackedLocType) {
        if (type.op.take(3).none { it == SMELT_OP }) {
            return
        }
        for (obj in listOf(CraftingObjs.bucket_of_sand, CraftingObjs.soda_ash)) {
            onOpLocU(type, obj) { melt() }
        }
    }

    /** Lists what [station] makes (from [with] if the player used something on it). */
    private suspend fun ProtectedAccess.choose(station: CraftingStation, with: ObjType?) {
        var recipes = CraftingRecipes.at(station)
        if (with != null) {
            recipes = recipes.filter { uses(it, with) }
            if (recipes.isEmpty()) {
                mes("Nothing interesting happens.")
                return
            }
        }
        val choice =
            skillMulti(
                title = TITLES.getValue(station),
                options = recipes.map { it.product },
                maxQuantity = MAX_QUANTITY,
            )
        maker.start(this, recipes[choice.index], choice.quantity)
    }

    /** Sand or soda ash used on a furnace makes molten glass until the materials run out. */
    private fun ProtectedAccess.melt() {
        maker.start(this, CraftingRecipes.at(CraftingStation.Furnace).first(), MAX_QUANTITY)
    }

    private fun uses(recipe: CraftingRecipe, obj: ObjType): Boolean =
        recipe.inputs.any { input ->
            input.options.any { it.internalNameValue == obj.internalNameValue }
        }

    private fun stationOf(type: UnpackedLocType): StationInfo? {
        val name = type.name
        val internal = type.internalName.orEmpty()
        return when {
            name.equals("Spinning wheel", ignoreCase = true) ->
                StationInfo(CraftingStation.SpinningWheel, "Spin")
            name.equals("Loom", ignoreCase = true) -> StationInfo(CraftingStation.Loom, "Weave")
            // The apostrophe in the cache's name is not a plain one.
            name.filter { it.isLetter() }.equals("potterswheel", ignoreCase = true) ->
                StationInfo(CraftingStation.PotterWheel, "Use")
            internal.contains("potteryoven") || internal.contains("pottery_oven") ->
                StationInfo(CraftingStation.PotteryOven, "Fire")
            else -> null
        }
    }

    private fun isFurnace(type: UnpackedLocType): Boolean =
        type.name.equals("Furnace", ignoreCase = true)

    private class StationInfo(val station: CraftingStation, val op: String)

    private companion object {
        const val SMELT_OP = "Smelt"

        /** The most actions one choice can ask for: the quantity buttons stop at an inventory. */
        const val MAX_QUANTITY = 28

        val TITLES =
            mapOf(
                CraftingStation.SpinningWheel to "What would you like to spin?",
                CraftingStation.Loom to "What would you like to weave?",
                CraftingStation.PotterWheel to "What would you like to make?",
                CraftingStation.PotteryOven to "What would you like to fire?",
                CraftingStation.Furnace to "What would you like to make?",
            )
    }
}

/**
 * Registers [action] for whichever of the first three ops of [type] is called [opName]. Stations
 * keep their op in different slots (a spinning wheel's "Spin" is the second), so it cannot be a
 * fixed op.
 *
 * @return `false` if [type] has no such op in its first three slots.
 */
internal fun ScriptContext.onOpLocNamed(
    type: UnpackedLocType,
    opName: String,
    action: suspend ProtectedAccess.(org.rsmod.game.loc.BoundLocInfo) -> Unit,
): Boolean {
    when (type.op.indexOf(opName)) {
        0 -> onOpLoc1(type) { action(it.loc) }
        1 -> onOpLoc2(type) { action(it.loc) }
        2 -> onOpLoc3(type) { action(it.loc) }
        else -> return false
    }
    return true
}
