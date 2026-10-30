package org.rsmod.content.areas.misc.isleofsouls.tasks

import com.google.inject.AbstractModule
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varbits
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varps
import org.rsmod.game.cheat.CheatCommandMap

class LeagueTaskWindowTest {
    private object NoBindings : AbstractModule()

    class Deps @Inject constructor(val commands: CheatCommandMap)

    private fun GameTestState.runWindowTest(body: GameTestScope.(CheatCommandMap) -> Unit) =
        runInjectedGameTest(Deps::class, NoBindings, LeagueTaskWindow::class) { deps ->
            body(deps.commands)
        }

    private fun GameTestScope.sent() = (player.client as CaptureClient)

    @Test
    fun GameTestState.`each dropdown pick is stored in its filter`() = runWindowTest { commands ->
        val picks =
            mapOf(
                LeagueTaskWindow.TIER_ENUM to (isle_varbits.league_task_filter_tier to 3),
                LeagueTaskWindow.COMPLETED_ENUM to (isle_varbits.league_task_filter_completed to 2),
                LeagueTaskWindow.TYPE_ENUM to (isle_varbits.league_task_filter_type to 9),
                LeagueTaskWindow.AREA_ENUM to (isle_varbits.league_task_filter_area to 17),
                LeagueTaskWindow.SKILL_ENUM to (isle_varbits.league_task_filter_skill to 5),
            )
        for ((enum, pick) in picks) {
            check(commands.execute(player, "leaguefilter", listOf("$enum", "${pick.second}")))
        }
        for ((_, pick) in picks) {
            assertEquals(pick.second, player.vars[pick.first], pick.first.internalName)
        }
    }

    @Test
    fun GameTestState.`picking all resets a filter`() = runWindowTest { commands ->
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TIER_ENUM}", "4"))
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TIER_ENUM}", "0"))
        assertEquals(0, player.vars[isle_varbits.league_task_filter_tier])
    }

    @Test
    fun GameTestState.`bad picks change nothing`() = runWindowTest { commands ->
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TIER_ENUM}", "2"))
        // Unknown dropdown, entry that does not fit the 3-bit tier filter, negative entry.
        commands.execute(player, "leaguefilter", listOf("7", "1"))
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TIER_ENUM}", "9"))
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TIER_ENUM}", "-1"))
        assertEquals(2, player.vars[isle_varbits.league_task_filter_tier])
        assertEquals(0, player.vars[isle_varbits.league_task_filter_type])
    }

    @Test
    fun GameTestState.`a pick sends the varp that redraws the list`() = runWindowTest { commands ->
        // Var changes are only sent once the player has been processed in the world.
        player.processedMapClock = 1
        commands.execute(player, "leaguefilter", listOf("${LeagueTaskWindow.TYPE_ENUM}", "3"))
        val varp = isle_varbits.league_task_filter_type.baseVar
        // The type filter sits in the high bits of its varp, so the value is sent as a large varp.
        val packets = sent().filterIsInstance<VarpLarge>().filter { it.id == varp.id }
        assertTrue(packets.any { it.value ushr 25 == 3 }) {
            "No varp sent for ${varp.internalName}."
        }
    }

    @Test
    fun GameTestState.`closing the window gives the chatbox its input back`() = runWindowTest { _ ->
        player.ifOpenOverlay(isle_interfaces.league_tasks, eventBus)
        val restore = { sent().filterIsInstance<RunClientScript>().count { it.id == 2158 } }
        assertEquals(0, restore())

        player.ifCloseSub(isle_interfaces.league_tasks, eventBus)

        assertEquals(1, restore())
        val flags = isle_varps.league_temp_noprotect_extra
        val packets = sent().filterIsInstance<VarpSmall>().filter { it.id == flags.id }
        assertTrue(packets.any { it.value == 0 }) { "Search flags were not cleared." }
    }

    @Test
    fun `patched client script reports the pick`() {
        val name = "[proc,script3211]"
        val bytes =
            LeagueTaskScripts::class.java.getResourceAsStream("cs2/$name")?.readBytes()
                ?: error("Missing cs2/$name")
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("leaguefilter "))
    }
}
