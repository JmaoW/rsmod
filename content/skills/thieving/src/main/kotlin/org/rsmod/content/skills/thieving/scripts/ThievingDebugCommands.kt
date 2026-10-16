package org.rsmod.content.skills.thieving.scripts

import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.thieving.configs.ThievingObjs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helper: `::thievekit` puts the equipment that changes thieving into the inventory:
 * the gloves of silence, a dodgy necklace, the five pieces of rogue equipment and a Thieving cape.
 * Nothing on the isle gives any of them yet.
 */
internal class ThievingDebugCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("thievekit") {
            modLevel = modlevels.admin
            desc = "Give the gear that helps Thieving"
            cheat {
                val o = ThievingObjs
                for (obj in
                    listOf(
                        o.hunting_silent_gloves,
                        o.dodgy_necklace,
                        o.roguesden_helm,
                        o.roguesden_body,
                        o.roguesden_legs,
                        o.roguesden_gloves,
                        o.roguesden_boots,
                        o.skillcape_thieving,
                    )) {
                    player.invAdd(player.inv, obj, 1, strict = false)
                }
                player.mes("Gave the thieving kit.")
            }
        }
    }
}
