package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.type.editors.struct.StructEditor
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.api.type.refs.struct.StructReferences
import org.rsmod.game.type.struct.StructType

/** The league task struct params the client reads (see [IsleOfSoulsTaskStructs]). */
internal object IsleOfSoulsTaskParams : ParamReferences() {
    val name = find<String>("league_task_name")
    val description = find<String>("league_task_desc")
    val area = find<Int>("league_task_area")
}

/**
 * Vanilla league task structs that [IsleOfSoulsTaskEdits] rewrites. Each needs a line in
 * `.data/symbols/struct.sym` named `league_task_<task id>` with the struct's id.
 */
internal object IsleOfSoulsTaskEditStructs : StructReferences() {
    fun task(taskId: Int): StructType = find("league_task_$taskId")
}

/**
 * The new text for league tasks that named a place the isle does not have, or that asked for one:
 * "Mine Some Runite Ore in the Wilderness" is now "Mine Some Runite Ore" and completes anywhere.
 * The server names in `LeagueTaskList` must be kept in step with [Edit.name].
 */
object IsleOfSoulsTaskTexts {
    /** A rewrite of one task. A `null` [name] or [description] keeps what the cache has. */
    class Edit(val taskId: Int, val name: String?, val description: String?)

    val edits =
        listOf(
            // Tasks that already complete anywhere, whose text still named a place.
            Edit(700, "Fletch 100 Maple Longbow (u)", "Fletch 100 Maple Longbow (u)"),
            Edit(981, "Craft an Air Battlestaff", "Craft an Air Battlestaff"),
            Edit(1572, "Craft some Pottery", "Craft some Pottery"),
            Edit(644, null, "Catch a Karambwanji"),
            Edit(646, null, "Catch 50 Karambwan"),
            Edit(696, null, "Catch a Monkfish"),
            Edit(1548, null, "Catch a Salmon"),
            Edit(1362, null, "Fletch a Redwood Shield"),
            // Tasks that were locked behind a place.
            Edit(985, "Mine Some Runite Ore", "Mine some Runite Ore"),
            Edit(1379, "Mine 200 Runite Ore", "Mine 200 Runite Ore"),
            Edit(1378, "Mine 200 Soft Clay", "Mine 200 Soft Clay"),
            Edit(1578, "Mine 25 Gold Rocks", "Mine 25 gold rocks"),
            Edit(1571, "Mine 15 Granite", "Mine 15 Granite"),
            Edit(860, null, "Mine 30 chunks of Granite"),
            Edit(1694, "Mine 20 Mithril Ore", "Mine 20 Mithril Ore"),
            Edit(1385, "Chop 100 Teak Logs", "Chop 100 Teak Logs"),
            Edit(1579, "Chop 100 Maple Logs", "Chop 100 Maple Logs"),
            Edit(1765, "Chop 50 Magic Logs", "Chop 50 Magic Logs"),
            Edit(1719, "Chop 20 Magic Logs", "Chop 20 Magic Logs"),
            Edit(1607, "Chop 15 Yew Logs", "Chop 15 Yew Logs"),
            Edit(1587, "Cook 20 Sharks", "Cook 20 Sharks"),
            Edit(514, "Cook a Rabbit", "Cook a Rabbit"),
            Edit(450, "Cook Some Food on a Range", "Cook some food on a Range"),
            Edit(1592, "Make 50 Oak Planks", "Make 50 Oak Planks"),
            Edit(972, null, "Catch 100 Dark Crabs"),
        )
}

/**
 * Rewrites the vanilla league task structs listed in [IsleOfSoulsTaskTexts]: the name and
 * description, and the area (param `1017`), which is set to `0` as it is for tasks that are not
 * tied to an area.
 *
 * The client reads these from the cache, so this needs `./gradlew packCache` to reach it.
 */
internal object IsleOfSoulsTaskEdits : StructEditor() {
    private const val AREA_GLOBAL = 0

    init {
        for (task in IsleOfSoulsTaskTexts.edits) {
            edit(IsleOfSoulsTaskEditStructs.task(task.taskId)) {
                task.name?.let { param[IsleOfSoulsTaskParams.name] = it }
                task.description?.let { param[IsleOfSoulsTaskParams.description] = it }
                param[IsleOfSoulsTaskParams.area] = AREA_GLOBAL
            }
        }
    }
}
