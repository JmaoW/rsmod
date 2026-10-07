package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The "Toggle" option of the Echo tools, both in the inventory and while equipped (the cache gives
 * the tools a `wear_op1` of "Toggle"). It opens a chat menu with the tool's switches, each showing
 * whether it is on (green) or off (red), and flips the one that is picked.
 */
class LeagueRelicToggleScript @Inject constructor(private val toggles: RelicToggles) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld3(objs.league_trailblazer_pickaxe) { choose(PICKAXE) }
        onOpHeld3(objs.league_trailblazer_axe) { choose(AXE) }
        onOpHeld3(objs.league_trailblazer_harpoon) { choose(HARPOON) }
        onOpWorn2(objs.league_trailblazer_pickaxe) { choose(PICKAXE) }
        onOpWorn2(objs.league_trailblazer_axe) { choose(AXE) }
        onOpWorn2(objs.league_trailblazer_harpoon) { choose(HARPOON) }
    }

    private suspend fun ProtectedAccess.choose(options: List<RelicToggle>) {
        val labels = options.map { "Toggle ${it.label} (${toggles.state(player, it)})" }
        val picked =
            when (options.size) {
                2 -> choice2(labels[0], 0, labels[1], 1)
                else -> choice3(labels[0], 0, labels[1], 1, labels[2], 2)
            }
        flip(options[picked])
    }

    private fun ProtectedAccess.flip(toggle: RelicToggle) {
        val turnedOff = toggles.flip(player, toggle)
        player.mes("${toggle.sentence()} is now ${toggles.state(player, toggle)}.")
        for (other in turnedOff) {
            player.mes("${other.sentence()} is now ${toggles.state(player, other)}.")
        }
    }

    private fun RelicToggle.sentence(): String = label.replaceFirstChar(Char::uppercaseChar)

    private companion object {
        val PICKAXE = listOf(RelicToggle.AutoSmelt, RelicToggle.AutoGemCut, RelicToggle.MiningBank)
        val AXE = listOf(RelicToggle.AutoBurn, RelicToggle.AutoFletch, RelicToggle.WoodcuttingBank)
        val HARPOON = listOf(RelicToggle.AutoCook, RelicToggle.FishingBank)
    }
}
