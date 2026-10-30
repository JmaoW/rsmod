package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Admin commands for checking the collection log without playing for it. */
class CollectionLogCommands
@Inject
constructor(
    private val log: CollectionLog,
    private val kills: CollectionKillCounts,
    private val screens: CollectionLogScreens,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("clog") {
            modLevel = modlevels.admin
            desc = "Give, fill, clear or open the collection log, or set a kill count"
            invalidArgs =
                "Use as ::clog give objId [count] | fill [tab 0-4] | clear | open | kc name amount"
            cheat {
                when (args.getOrNull(0)) {
                    "give" -> {
                        val obj = args[1].toInt()
                        val count = args.getOrNull(2)?.toInt() ?: 1
                        val filled = log.receive(player, obj, count)
                        player.mes("Gave $count x $obj to the collection log (new slot: $filled).")
                    }
                    "fill" -> {
                        val tab = args.getOrNull(1)?.toInt()
                        val tabs = log.catalog.tabs.filter { tab == null || it.index == tab }
                        var added = 0
                        for (category in tabs.flatMap { it.categories }) {
                            for (obj in category.objs) {
                                if (log.receive(player, obj, quiet = true)) {
                                    added++
                                }
                            }
                        }
                        player.mes("Filled $added slots of the collection log.")
                    }
                    "clear" -> {
                        log.clear(player)
                        player.mes("Cleared the collection log.")
                    }
                    "open" -> screens.openOverview(player)
                    "kc" -> {
                        val name = args[1]
                        val amount = args[2].toInt()
                        val counter =
                            CollectionKillCountData.all
                                .map { it.first }
                                .firstOrNull { it.internalName == "clog_kc_$name" }
                        if (counter == null) {
                            player.mes("No kill count called '$name'.")
                            return@cheat
                        }
                        kills.add(player, counter, amount)
                        player.mes("Added $amount to ${counter.internalName}.")
                    }
                    else -> player.mes("Use as ::clog give | fill | clear | open | kc")
                }
            }
        }
    }
}
