package org.rsmod.content.skills.hunter.scripts

import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helper: `::huntkit` puts what Hunter needs into the inventory: a few of every trap
 * and what it is set with, bait, both nets, jars, a teasing stick and a falconer's glove. Nothing
 * on the isle hands any of them out yet.
 */
internal class HunterDebugCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("huntkit") {
            modLevel = modlevels.admin
            desc = "Give the gear that Hunter needs"
            cheat {
                val o = hunter_objs
                val kit =
                    listOf(
                        o.bird_snare to 5,
                        o.box_trap to 5,
                        o.magic_box to 1,
                        o.rope to 5,
                        o.small_net to 5,
                        o.knife to 1,
                        o.logs to 20,
                        o.torch_lit to 1,
                        o.butterfly_net to 1,
                        o.magic_butterfly_net to 1,
                        o.butterfly_jar to 10,
                        o.impling_jar to 10,
                        o.teasing_stick to 1,
                        o.falconers_glove to 1,
                        o.spicy_tomato to 5,
                        o.raw_beef to 5,
                        o.red_bead to 5,
                        o.tar_marrentill to 10,
                    )
                for ((obj, count) in kit) {
                    player.invAdd(player.inv, obj, count, strict = false)
                }
                player.mes("Gave the hunter kit.")
            }
        }
    }
}
