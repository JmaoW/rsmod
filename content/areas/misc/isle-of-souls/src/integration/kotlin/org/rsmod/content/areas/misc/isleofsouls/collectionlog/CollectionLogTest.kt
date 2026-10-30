package org.rsmod.content.areas.misc.isleofsouls.collectionlog

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueCollectionLogTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.collectionlog.CollectionKillCounts
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.CollectionLogScreens
import org.rsmod.content.interfaces.collectionlog.CollectionLogScript
import org.rsmod.content.interfaces.collectionlog.CollectionOverviewScript
import org.rsmod.content.interfaces.collectionlog.clog_components
import org.rsmod.content.interfaces.collectionlog.clog_interfaces
import org.rsmod.content.interfaces.collectionlog.clog_invs
import org.rsmod.content.interfaces.collectionlog.clog_varps
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

/** `./gradlew packCache` has to have run for the new `clog_kc_*` varps to be in the cache. */
class CollectionLogTest {
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val log: CollectionLog,
        val kills: CollectionKillCounts,
        val tracker: LeagueTaskTracker,
        val screens: CollectionLogScreens,
        val invTypes: InvTypeList,
    )

    private fun GameTestState.runLogTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            TeamsModule,
            CollectionLogScript::class,
            CollectionOverviewScript::class,
            LeagueCollectionLogTasks::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.type(obj: Int): ObjType = objTypes.values.first { it.id == obj }

    @Test
    fun GameTestState.`the catalog is read from the cache`() = runLogTest { deps ->
        val catalog = deps.log.catalog
        assertEquals(5, catalog.tabs.size)
        assertEquals(114, catalog.categories.size)
        assertEquals(1810, catalog.slots)
        assertEquals(
            listOf("Bosses", "Raids", "Clues", "Minigames", "Other"),
            catalog.tabs.map { it.name },
        )
        assertEquals("Zulrah", catalog.category(505)?.name)
    }

    @Test
    fun GameTestState.`receiving an item fills its slot once and counts it`() = runLogTest { deps ->
        val zulrah = checkNotNull(deps.log.catalog.category(505))
        // An item in two categories fills a slot in each, so pick one that is in only one.
        val obj = zulrah.objs.first { deps.log.catalog.entryOf(it)!!.categories.size == 1 }

        assertTrue(deps.log.receive(player, obj))
        assertFalse(deps.log.receive(player, obj, count = 2))

        assertEquals(3, deps.log.owned(player, obj))
        assertEquals(1, player.vars[clog_varps.count])
        assertEquals(1, player.vars[clog_varps.count_highscores])
        assertEquals(1, player.vars[clog_varps.tab_counts[0]])
        assertEquals(deps.log.catalog.slots, player.vars[clog_varps.count_max])
        assertEquals(1, deps.log.filled(player, zulrah))
        assertEquals(obj, player.vars[clog_varps.latest_items[0]])
    }

    @Test
    fun GameTestState.`an item that is not in the log does nothing`() = runLogTest { deps ->
        assertFalse(deps.log.receive(player, objs_logs()))
        assertEquals(0, player.vars[clog_varps.count])
    }

    private fun GameTestScope.objs_logs(): Int =
        objTypes.values.first { it.internalName == "logs" }.id

    @Test
    fun GameTestState.`a drop rolled for a hero fills the slot`() = runLogTest { deps ->
        val zulrah = checkNotNull(deps.log.catalog.category(505))
        val obj = zulrah.objs.first()
        val monster =
            npcTypeFactory.create {
                name = "Zulrah"
                hitpoints = 10
                defaultMode = NpcMode.None
            }
        val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), monster)

        eventBus.publish(NpcDropEvents.Rolled(npc, player, listOf(DropResult(type(obj), 1))))
        eventBus.publish(NpcDeathEvents.Killed(npc, player))

        assertEquals(1, deps.log.owned(player, obj))
    }

    @Test
    fun GameTestState.`killing a boss raises its kill count and showing a category copies it`() =
        runLogTest { deps ->
            val monster =
                npcTypeFactory.create {
                    name = "Zulrah"
                    hitpoints = 10
                    defaultMode = NpcMode.None
                }
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), monster)

            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            deps.kills.show(player, 505)

            assertEquals(2, player.vars[clog_varps.kill_lines[0]])
            assertEquals(0, player.vars[clog_varps.kill_lines[1]])

            deps.kills.show(player, 476)
            assertEquals(0, player.vars[clog_varps.kill_lines[0]])
        }

    @Test
    fun GameTestState.`filling five slots completes the five slot league task`() =
        runLogTest { deps ->
            val objs =
                deps.log.catalog.category(505)!!.objs.take(4) +
                    deps.log.catalog.category(476)!!.objs.take(1)
            for (obj in objs) {
                deps.log.receive(player, obj, quiet = true)
            }

            assertTrue(deps.tracker.isComplete(player, 1448))
            assertFalse(deps.tracker.isComplete(player, 1449))
        }

    private fun GameTestScope.scripts(id: Int): List<RunClientScript> =
        (player.client as CaptureClient).filterIsInstance<RunClientScript>().filter { it.id == id }

    @Test
    fun GameTestState.`the log is a saved inventory big enough for every item`() =
        runLogTest { deps ->
            val type = deps.invTypes[clog_invs.log]
            assertEquals(InvScope.Perm, type.scope)
            assertEquals(2048, type.size)
            assertTrue(deps.log.catalog.listedObjs.size <= type.size)
        }

    @Test
    fun GameTestState.`clicking a category row shows its kill count and redraws the list`() =
        runLogTest { deps ->
            val zulrah = checkNotNull(deps.log.catalog.category(505))
            deps.kills.addKill(player, "Zulrah")
            deps.kills.addKill(player, "Zulrah")
            deps.kills.addKill(player, "Zulrah")
            player.ifOpenOverlay(interfaces.collection, components.toplevel_target_floater)

            player.ifButton(clog_components.rows[zulrah.tab], zulrah.row)
            advance()

            assertEquals(3, player.vars[clog_varps.kill_lines[0]])
            val draw = scripts(2730).single()
            val tab = deps.log.catalog.tabs[zulrah.tab]
            assertEquals(
                listOf(
                    zulrah.tab,
                    tab.container,
                    tab.background,
                    tab.text,
                    tab.scrollbar,
                    471,
                    zulrah.row,
                ),
                draw.values.toList(),
            )
        }

    @Test
    fun GameTestState.`clicking a tab redraws the tabs and selects its first category`() =
        runLogTest { deps ->
            player.ifOpenOverlay(interfaces.collection, components.toplevel_target_floater)

            player.ifButton(clog_components.tabs[2])
            advance()

            assertEquals(listOf(2), scripts(2388).single().values.toList())
            val draw = scripts(2730).single().values.toList()
            assertEquals(listOf(2, 473, 0), listOf(draw[0], draw[5], draw[6]))
        }

    @Test
    fun GameTestState.`opening a tab shows the kill count of its first category`() =
        runLogTest { deps ->
            // The first boss in the list is the Abyssal Sire.
            repeat(7) { deps.kills.addKill(player, "Abyssal Sire") }
            player.ifOpenOverlay(interfaces.collection, components.toplevel_target_floater)

            player.ifButton(clog_components.tabs[0])
            advance()

            assertEquals(7, player.vars[clog_varps.kill_lines[0]])
        }

    @Test
    fun GameTestState.`the close buttons close both screens`() = runLogTest { deps ->
        deps.screens.openOverview(player)
        advance()
        player.ifButton(clog_components.overview_close)
        advance()
        assertFalse(player.ui.containsOverlay(clog_interfaces.overview))

        deps.screens.openLog(player)
        advance()
        assertTrue(player.ui.containsOverlay(interfaces.collection))
        player.ifButton(clog_components.log_close)
        advance()
        assertFalse(player.ui.containsOverlay(interfaces.collection))
    }

    @Test
    fun GameTestState.`closing the log ends its search so the chatbox gets input back`() =
        runLogTest { deps ->
            deps.screens.openLog(player)
            advance()
            assertTrue(scripts(4084).isEmpty())

            player.ifButton(clog_components.log_close)
            advance()

            assertEquals(listOf(1), scripts(4084).single().values.toList())
        }

    @Test
    fun GameTestState.`the summary opens the overview and its buttons lead into the log`() =
        runLogTest { deps ->
            deps.screens.openOverview(player)
            advance()
            assertTrue(player.ui.containsOverlay(clog_interfaces.overview))
            assertFalse(player.ui.containsOverlay(interfaces.collection))

            player.ifButton(clog_components.overview_sections_click, 3)
            advance()

            assertTrue(player.ui.containsOverlay(interfaces.collection))
            assertFalse(player.ui.containsOverlay(clog_interfaces.overview))
            assertEquals(listOf(3), scripts(2388).single().values.toList())

            player.ifButton(clog_components.log_burger_menu, 1)
            advance()

            assertTrue(player.ui.containsOverlay(clog_interfaces.overview))
            assertFalse(player.ui.containsOverlay(interfaces.collection))
        }
}
