package org.rsmod.content.areas.misc.isleofsouls.event

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.game.process.player.AutosaveSettings
import org.rsmod.api.game.process.player.PlayerAutosaveProcess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.gauntlet.GauntletNode
import org.rsmod.content.areas.misc.isleofsouls.masteries.MasteryData
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.relics.RelicData
import org.rsmod.content.areas.misc.isleofsouls.relics.RelicVarBits
import org.rsmod.content.areas.misc.isleofsouls.revenant.ether_objs
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahNpcs
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahScaleMod
import org.rsmod.content.areas.misc.isleofsouls.zulrah.zulrah_objs
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid

class EventSettingsScriptTest {
    class Deps @Inject constructor(val autosave: AutosaveSettings)

    private val relics = LeagueRelicTracker()

    /** Runs [body] with [settings] as the event's, then puts the previous settings back. */
    private fun GameTestState.withSettings(
        settings: EventSettings,
        body: GameTestScope.(Deps) -> Unit,
    ) =
        runInjectedGameTest(Deps::class, null, EventSettingsScript::class) { deps ->
            val before = EventSettings.current
            val nodes = GauntletNode.NODES_DEPLETE
            try {
                EventSettings.current = settings
                body(this, deps)
            } finally {
                EventSettings.current = before
                GauntletNode.NODES_DEPLETE = nodes
            }
        }

    @Test
    fun GameTestState
        .`the xp and drop multipliers, thresholds and mastery points follow the settings`() =
        withSettings(
            EventSettings.parse(
                """
                {
                  "xpMultiplier": { "base": 3, "byRelicTier": { "1": 4, "3": 6 },
                                    "combatBonus": 3, "combatBonusTier": 2 },
                  "dropMultiplier": { "byRelicTier": { "3": 9 } },
                  "relicPointThresholds": [0, 10, 20, 30, 40, 50, 60, 70],
                  "taskPoints": { "easy": 1, "master": 99 },
                  "masteryPointsPerTask": 5
                }
                """
            )
        ) {
            fun choose(tier: Int) {
                VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], 1)
            }
            assertEquals(1.0, relics.dropMultiplier(player))
            assertEquals(3.0, relics.xpMultiplier(player, stats.mining))
            choose(0)
            assertEquals(4.0, relics.xpMultiplier(player, stats.mining))
            choose(1)
            assertEquals(4.0, relics.xpMultiplier(player, stats.mining))
            assertEquals(12.0, relics.xpMultiplier(player, stats.attack))
            choose(2)
            assertEquals(6.0, relics.xpMultiplier(player, stats.mining))

            assertEquals(9.0, relics.dropMultiplier(player))

            assertEquals(30, RelicData.thresholds[3])
            assertEquals(5, MasteryData.pointsPerTask)
        }

    @Test
    fun GameTestState.`applying the settings reaches the gauntlet flag and the autosave timing`() =
        withSettings(EventSettings()) { deps ->
            val interval = deps.autosave.interval
            val delay = deps.autosave.requestDelay
            try {
                val script = EventSettingsScript(deps.autosave)
                script.apply(
                    EventSettings.parse(
                        """
                        { "gauntletNodesDeplete": true,
                          "autosave": { "intervalTicks": 40, "requestDelayTicks": 8 } }
                        """
                    )
                )
                assertTrue(GauntletNode.NODES_DEPLETE)
                assertEquals(40, deps.autosave.interval)
                assertEquals(8, deps.autosave.requestDelay)

                script.apply(EventSettings())
                assertFalse(GauntletNode.NODES_DEPLETE)
                assertEquals(PlayerAutosaveProcess.AUTOSAVE_INTERVAL, deps.autosave.interval)
                assertEquals(
                    PlayerAutosaveProcess.AUTOSAVE_REQUEST_DELAY,
                    deps.autosave.requestDelay,
                )
            } finally {
                deps.autosave.interval = interval
                deps.autosave.requestDelay = delay
            }
        }

    @Test
    fun GameTestState.`revenant ether is multiplied by the setting`() =
        withSettings(EventSettings.parse("""{ "revenantEtherMultiplier": 3 }""")) {
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), goblin)
            val modify = NpcDropEvents.Modify(npc, player)
            eventBus.publish(modify)
            assertEquals(3.0, modify.countMultipliers[ether_objs.ether])
            assertEquals(30, modify.countOf(DropResult(ether_objs.ether, 10)))
        }

    @Test
    fun GameTestState.`zulrah scales are multiplied by the setting`() =
        runInjectedGameTest(Deps::class, null, ZulrahScaleMod::class) {
            val before = EventSettings.current
            try {
                EventSettings.current = EventSettings.parse("""{ "zulrahScaleMultiplier": 4 }""")
                val zulrah = spawnNpc(CoordGrid(0, 50, 50, 0, 0), npcTypes[ZulrahNpcs.green])
                val modify = NpcDropEvents.Modify(zulrah, player)
                eventBus.publish(modify)
                assertEquals(4.0, modify.countMultipliers[zulrah_objs.scales])
                assertEquals(400, modify.countOf(DropResult(zulrah_objs.scales, 100)))

                val other = spawnNpc(CoordGrid(0, 52, 50, 0, 0), goblin)
                val unchanged = NpcDropEvents.Modify(other, player)
                eventBus.publish(unchanged)
                assertEquals(null, unchanged.countMultipliers[zulrah_objs.scales])
            } finally {
                EventSettings.current = before
            }
        }

    @Test
    fun GameTestState.`the xp rate is set at login only when the file has one`() =
        withSettings(EventSettings.parse("""{ "baseXpRate": 12.5 }""")) {
            player.xpRate = 1.0
            eventBus.publish(SessionStateEvent.Initialize(player))
            assertEquals(12.5, player.xpRate)

            EventSettings.current = EventSettings()
            player.xpRate = 7.0
            eventBus.publish(SessionStateEvent.Initialize(player))
            assertEquals(7.0, player.xpRate)
        }

    private companion object {
        val goblin: UnpackedNpcType =
            npcTypeFactory.create {
                name = "Test goblin"
                size = 1
                hitpoints = 7
            }
    }
}
