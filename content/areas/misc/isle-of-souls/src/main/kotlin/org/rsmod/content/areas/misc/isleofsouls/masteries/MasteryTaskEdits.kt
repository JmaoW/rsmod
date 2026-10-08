package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.api.type.editors.struct.StructEditor
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.api.type.refs.struct.StructReferences

internal object MasteryTaskParams : ParamReferences() {
    /** The text of a combat mastery or mastery task, which the masteries screen shows. */
    val description = find<String>("combat_mastery_desc")
}

/**
 * Mastery task structs that [MasteryTaskEdits] rewrites. Each needs a line in
 * `.data/symbols/struct.sym` with the struct's id.
 */
internal object MasteryTaskStructs : StructReferences() {
    /** Mastery task 3. */
    val scurrius = find("combat_mastery_task_scurrius")
}

/**
 * Rewrites the text of the combat mastery tasks that do not suit the isle. "Defeat Scurrius by
 * yourself." becomes "Defeat Scurrius.", since a kill by either player on a team counts for both.
 *
 * The client reads these from the cache, so this needs `./gradlew packCache` to reach it.
 */
internal object MasteryTaskEdits : StructEditor() {
    init {
        edit(MasteryTaskStructs.scurrius) {
            param[MasteryTaskParams.description] =
                MasteryData.taskNames[MasteryKills.SLOT_SCURRIUS - 1] + "."
        }
    }
}
