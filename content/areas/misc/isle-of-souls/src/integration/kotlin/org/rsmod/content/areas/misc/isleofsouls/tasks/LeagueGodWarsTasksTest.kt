package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class LeagueGodWarsTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.wear(pos: Wearpos, internal: String) {
        val type = objTypes.values.first { it.internalName == internal }
        player.worn[pos.slot] = InvObj(type)
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, objTypes[type]))
    }

    private fun GameTestScope.kill(internal: String) {
        // Somewhere no other test stands: spawned npcs outlive a test in the shared world.
        val at = CoordGrid(2500, 2500, 0)
        allocZoneCollision(at)
        val type = npcTypes.values.first { it.internalName == internal }
        val npc: Npc = spawnNpc(at, type)
        eventBus.publish(NpcDeathEvents.Killed(npc, player))
    }

    @Test
    fun GameTestState.`each general's own task completes when they die`() =
        runGameTest(LeagueGodWarsTasks::class) {
            assertFalse(done(LeagueGodWarsTasks.TASK_KRIL))
            kill("godwars_zamorak_avatar")
            assertTrue(done(LeagueGodWarsTasks.TASK_KRIL))
            assertFalse(done(LeagueGodWarsTasks.TASK_GRAARDOR), "only that general")
            kill("godwars_bandos_avatar")
            kill("godwars_armadyl_avatar")
            kill("godwars_saradomin_avatar")
            assertTrue(done(LeagueGodWarsTasks.TASK_GRAARDOR))
            assertTrue(done(LeagueGodWarsTasks.TASK_KREE))
            assertTrue(done(LeagueGodWarsTasks.TASK_ZILYANA))
        }

    @Test
    fun GameTestState.`a bodyguard or follower is not a general`() =
        runGameTest(LeagueGodWarsTasks::class) {
            kill("godwars_sergeant_goblin1")
            kill("godwars_ancient_imp")
            kill("godwars_armadyl_bodyguard_skree")
            assertFalse(done(LeagueGodWarsTasks.TASK_GRAARDOR))
            assertFalse(done(LeagueGodWarsTasks.TASK_100))
        }

    @Test
    fun GameTestState.`a hundred kills of one general completes the 100 task but not the 250`() =
        runGameTest(LeagueGodWarsTasks::class) {
            VarPlayerIntMapSetter.set(player, GwdProgress.graardor_100, 99)
            kill("godwars_bandos_avatar")
            assertTrue(done(LeagueGodWarsTasks.TASK_100))
            assertFalse(done(LeagueGodWarsTasks.TASK_250))
        }

    @Test
    fun GameTestState.`kills of different generals are not added together`() =
        runGameTest(LeagueGodWarsTasks::class) {
            VarPlayerIntMapSetter.set(player, GwdProgress.graardor_100, 98)
            VarPlayerIntMapSetter.set(player, GwdProgress.kree_100, 98)
            kill("godwars_bandos_avatar")
            kill("godwars_armadyl_avatar")
            assertFalse(done(LeagueGodWarsTasks.TASK_100), "99 of each is not 100 of one")
            kill("godwars_bandos_avatar")
            assertTrue(done(LeagueGodWarsTasks.TASK_100))
        }

    @Test
    fun GameTestState.`five hundred kills complete all three counted tasks`() =
        runGameTest(LeagueGodWarsTasks::class) {
            VarPlayerIntMapSetter.set(player, GwdProgress.zilyana_100, 99)
            VarPlayerIntMapSetter.set(player, GwdProgress.zilyana_250, 249)
            VarPlayerIntMapSetter.set(player, GwdProgress.zilyana_500, 499)
            kill("godwars_saradomin_avatar")
            assertTrue(done(LeagueGodWarsTasks.TASK_100))
            assertTrue(done(LeagueGodWarsTasks.TASK_250))
            assertTrue(done(LeagueGodWarsTasks.TASK_500))
        }

    @Test
    fun GameTestState.`armadyl armour completes the piece task and then the set task`() =
        runGameTest(LeagueGodWarsTasks::class) {
            wear(Wearpos.Hat, "armadyl_helmet")
            assertTrue(done(LeagueGodWarsTasks.TASK_ARMADYL_PIECE))
            assertFalse(done(LeagueGodWarsTasks.TASK_ARMADYL_SET))
            wear(Wearpos.Torso, "armadyl_chestplate")
            assertFalse(done(LeagueGodWarsTasks.TASK_ARMADYL_SET))
            wear(Wearpos.Legs, "armadyl_skirt")
            assertTrue(done(LeagueGodWarsTasks.TASK_ARMADYL_SET))
        }

    @Test
    fun GameTestState.`the ornament kit versions count as the plain armour`() =
        runGameTest(LeagueGodWarsTasks::class) {
            wear(Wearpos.Torso, "bandos_chestplate_ornament")
            wear(Wearpos.Legs, "bandos_skirt_ornament")
            wear(Wearpos.Feet, "bandos_boots_ornament")
            assertTrue(done(LeagueGodWarsTasks.TASK_BANDOS_PIECE))
            assertTrue(done(LeagueGodWarsTasks.TASK_BANDOS_SET))
        }

    @Test
    fun GameTestState.`bandos armour does not complete the armadyl tasks`() =
        runGameTest(LeagueGodWarsTasks::class) {
            wear(Wearpos.Torso, "bandos_chestplate")
            assertTrue(done(LeagueGodWarsTasks.TASK_BANDOS_PIECE))
            assertFalse(done(LeagueGodWarsTasks.TASK_ARMADYL_PIECE))
            assertFalse(done(LeagueGodWarsTasks.TASK_BANDOS_SET))
        }

    @Test
    fun GameTestState.`the dungeon's weapons complete their tasks`() =
        runGameTest(LeagueGodWarsTasks::class) {
            val weapons =
                listOf(
                    "saradomin_sword" to LeagueGodWarsTasks.TASK_SARADOMIN_SWORD,
                    "zamorak_spear" to LeagueGodWarsTasks.TASK_ZAMORAKIAN_SPEAR,
                    "acb" to LeagueGodWarsTasks.TASK_CROSSBOW,
                    "sotd" to LeagueGodWarsTasks.TASK_STAFF_OF_THE_DEAD,
                    "bgs" to LeagueGodWarsTasks.TASK_GODSWORD,
                )
            for ((name, task) in weapons) {
                assertFalse(done(task), "$name before")
                wear(Wearpos.RightHand, name)
                assertTrue(done(task), "$name after")
            }
        }
}
