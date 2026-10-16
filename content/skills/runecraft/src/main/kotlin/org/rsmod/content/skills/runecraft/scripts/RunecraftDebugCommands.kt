package org.rsmod.content.skills.runecraft.scripts

import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.runecraft.configs.RunecraftObjs
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helper: `::runekit <kit>` puts what is needed to try one part of Runecraft into the
 * player's inventory, since talismans, tiaras and a binding necklace have no source on the isle
 * yet. Run it with no kit to list them.
 */
internal class RunecraftDebugCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("runekit") {
            modLevel = modlevels.admin
            desc = "Give the items to test one part of Runecraft"
            invalidArgs = "Use as ::runekit <kit> (kits: ${KITS.keys.joinToString(", ")})"
            cheat {
                val name = args.getOrNull(0)?.lowercase()
                val kit = name?.let { KITS[it] }
                if (kit == null) {
                    player.mes("Kits: ${KITS.keys.joinToString(", ")}")
                    return@cheat
                }
                give(player, kit)
                player.mes("Gave the $name kit.")
            }
        }
    }

    private fun give(player: Player, kit: List<Pair<ObjType, Int>>) {
        for ((obj, count) in kit) {
            player.invAdd(player.inv, obj, count, strict = false)
        }
    }

    private companion object {
        private val o = RunecraftObjs

        /** Each kit is a list of what to give and how many of it. */
        val KITS: Map<String, List<Pair<ObjType, Int>>> =
            linkedMapOf(
                "pure" to listOf(o.pure_essence to 28),
                "rune" to listOf(o.rune_essence to 28),
                "tiara" to
                    listOf(
                        o.tiara to 4,
                        o.air_talisman to 1,
                        o.mind_talisman to 1,
                        o.water_talisman to 1,
                        o.earth_talisman to 1,
                    ),
                "combo" to
                    listOf(
                        o.pure_essence to 12,
                        o.air_rune to 12,
                        o.water_rune to 12,
                        o.earth_rune to 12,
                        o.fire_rune to 12,
                        o.air_talisman to 1,
                        o.water_talisman to 1,
                        o.earth_talisman to 1,
                        o.fire_talisman to 1,
                        o.binding_necklace to 1,
                    ),
                "pouches" to
                    listOf(
                        o.small_pouch to 1,
                        o.medium_pouch to 1,
                        o.large_pouch to 1,
                        o.giant_pouch to 1,
                        o.abyssal_needle to 1,
                        o.pure_essence to 20,
                    ),
            )
    }
}
