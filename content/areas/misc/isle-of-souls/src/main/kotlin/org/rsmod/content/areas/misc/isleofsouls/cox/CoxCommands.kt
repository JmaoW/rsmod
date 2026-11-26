package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Developer commands for the Chambers of Xeric. */
class CoxCommands
@Inject
constructor(private val raids: CoxRaids, private val rooms: CoxRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("cox") {
            modLevel = modlevels.admin
            desc = "Go to Mount Quidamortem"
            cheat {
                if (raids.raidOf(player) != null) {
                    player.mes("Leave your raid first.")
                    return@cheat
                }
                raids.move(player, CoxMountain.ARRIVAL)
            }
        }
        onCommand("coxlayout") {
            modLevel = modlevels.admin
            desc = "Show your raid's layout and the room you are in"
            cheat {
                val raid = raids.raidOf(player) ?: return@cheat player.mes("You are not in a raid.")
                player.mes("Layout: ${raid.layout.pattern ?: "Challenge Mode"}")
                player.mes(raid.layout.toString())
                val room = raid.roomAt(player.coords)
                player.mes(
                    if (room != null) {
                        "Room ${room.index}: ${room.type.label} (version ${room.variant}, turned ${room.rotation}), " +
                            "open ${raids.isOpen(raid, room)}, at ${raid.templateOf(room, player.coords)}"
                    } else if (raid.inOlmChamber(player.coords)) {
                        "The Great Olm's chamber, at ${raid.olmReal(player.coords)}"
                    } else {
                        "Between rooms."
                    }
                )
            }
        }
        onCommand("coxpoints") {
            modLevel = modlevels.admin
            desc = "Give yourself raid points"
            invalidArgs = "Use as ::coxpoints amount"
            cheat {
                val raid = raids.raidOf(player) ?: return@cheat player.mes("You are not in a raid.")
                raids.addPoints(raid, player, args[0].toInt())
            }
        }
        onCommand("coxroom") {
            modLevel = modlevels.admin
            desc = "Go to a room of your raid by its number (olm for the Great Olm)"
            invalidArgs = "Use as ::coxroom number"
            cheat {
                val raid = raids.raidOf(player) ?: return@cheat player.mes("You are not in a raid.")
                if (args[0].equals("olm", ignoreCase = true)) {
                    raids.move(player, raids.nearOpen(raid.olmTile(CoxTiles.OLM_ENTRANCE)))
                    return@cheat
                }
                val room =
                    raid.rooms.getOrNull(args[0].toInt())
                        ?: return@cheat player.mes("No such room.")
                val centre =
                    org.rsmod.map.CoordGrid(
                        room.type.sourceX(room.variant) + 16,
                        room.type.sourceZ + 16,
                        0,
                    )
                raids.move(player, raids.landing(raid, room, centre))
            }
        }
        onCommand("coxopen") {
            modLevel = modlevels.admin
            desc = "Complete the room you are in"
            cheat {
                val raid = raids.raidOf(player) ?: return@cheat player.mes("You are not in a raid.")
                val room = raid.roomAt(player.coords) ?: return@cheat player.mes("Stand in a room.")
                val runtime = rooms.runtime(raid, room)
                if (runtime != null) runtime.complete() else raids.openRoom(raid, room)
                player.mes("The ${room.type.label} room is done.")
            }
        }
    }
}
