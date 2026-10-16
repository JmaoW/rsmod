package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.agility.configs.AgilityObjs
import org.rsmod.content.skills.agility.configs.ArenaLayout
import org.rsmod.content.skills.agility.configs.Courses
import org.rsmod.content.skills.agility.configs.GraceObjs
import org.rsmod.content.skills.agility.configs.GraceSpot
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helpers. `::agilitykit` gives 300 marks of grace, the six graceful pieces and a pair
 * of amylase packs. `::agility grace` or `::agility arena` takes you to Grace or into the arena.
 * `::agility <course>` still works for any course added to [Courses].
 */
internal class AgilityDebugCommands
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("agilitykit") {
            modLevel = modlevels.admin
            desc = "Give marks of grace, the graceful outfit and amylase packs"
            cheat {
                player.invAdd(player.inv, AgilityObjs.mark_of_grace, 300, strict = false)
                for (obj in
                    listOf(
                        AgilityObjs.graceful_hood,
                        AgilityObjs.graceful_top,
                        AgilityObjs.graceful_legs,
                        AgilityObjs.graceful_gloves,
                        AgilityObjs.graceful_boots,
                        AgilityObjs.graceful_cape,
                    )) {
                    player.invAdd(player.inv, obj, 1, strict = false)
                }
                player.invAdd(player.inv, GraceObjs.amylase_pack, 2, strict = false)
                player.mes("Gave the agility kit.")
            }
        }

        onCommand("agility") {
            modLevel = modlevels.admin
            desc = "Go to Grace, the arena, or a course start"
            invalidArgs = "Use as ::agility grace or ::agility arena"
            cheat {
                val name = args[0].lowercase()
                val dest: CoordGrid? =
                    if (name == "grace") {
                        GraceSpot.tile.translateX(1)
                    } else if (name == "arena") {
                        CoordGrid(ArenaLayout.entry.x, ArenaLayout.entry.z, ArenaLayout.entry.level)
                    } else {
                        Courses.byKey(name)?.let { course ->
                            val first = course.obstacles.first()
                            val route = first.route
                            val click = first.clicks.first()
                            if (route != null) CoordGrid(route.x, route.z, 0)
                            else CoordGrid(click.x, click.z - 1, 0)
                        }
                    }
                if (dest == null) {
                    player.mes("No course called $name.")
                } else {
                    protectedAccess.launch(player) {
                        player.mes("Teleported to $dest.")
                        telejump(dest)
                    }
                }
            }
        }
    }
}
