package org.rsmod.content.other.drops

import jakarta.inject.Inject
import org.rsmod.api.drops.DropVariant
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Picks the drop table variant for a kill: a skulled killer rolls the wiki's skulled rates, and a
 * killer on a Slayer task for the monster rolls the on-task rates (which replace the unique table).
 * A killer the league's hooks count as always on task (the Slayer Master relic) does too, for any
 * monster they could be assigned. A monster with no variant table keeps its usual table whatever
 * the killer's state.
 */
internal class DropVariantScript @Inject constructor(private val slayer: SlayerEngine) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Modify> {
            val skulled = hero.skullIcon != null
            val onTask = slayer.isOnTask(hero, npc) || slayer.countsAsOnTask(hero, npc.visType)
            variant =
                when {
                    onTask && skulled -> DropVariant.ON_TASK_SKULLED
                    onTask -> DropVariant.ON_TASK
                    skulled -> DropVariant.SKULLED
                    else -> null
                }
        }
    }
}
