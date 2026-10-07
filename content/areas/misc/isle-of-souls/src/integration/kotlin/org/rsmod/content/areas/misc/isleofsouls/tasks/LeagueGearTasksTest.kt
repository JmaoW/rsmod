package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos

class LeagueGearTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.wear(pos: Wearpos, obj: ObjType) {
        player.worn[pos.slot] = InvObj(obj)
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, objTypes[obj]))
    }

    @Test
    fun GameTestState.`wearing a fire cape completes its task`() =
        runGameTest(LeagueGearTasks::class) {
            assertFalse(done(653))
            wear(Wearpos.Back, cave_objs.fire_cape)
            assertTrue(done(653))
            assertEquals(200, player.vars[LeagueTaskVars.points_completed])
        }

    @Test
    fun GameTestState.`each metal weapon completes only its own task`() =
        runGameTest(LeagueGearTasks::class) {
            wear(Wearpos.RightHand, obj("mithril_scimitar"))
            assertTrue(done(412))
            assertFalse(done(413))
            assertFalse(done(414))

            wear(Wearpos.RightHand, obj("adamant_2h_sword"))
            assertTrue(done(413))
            assertFalse(done(414))

            wear(Wearpos.RightHand, obj("rune_claws"))
            assertTrue(done(414))
        }

    @Test
    fun GameTestState.`tools and weaker weapons do not count as metal weapons`() =
        runGameTest(LeagueGearTasks::class) {
            wear(Wearpos.RightHand, obj("rune_axe"))
            wear(Wearpos.RightHand, obj("steel_scimitar"))
            assertFalse(done(412))
            assertFalse(done(413))
            assertFalse(done(414))
        }

    @Test
    fun GameTestState.`a full set needs a body, a helm and legs or a skirt`() =
        runGameTest(LeagueGearTasks::class) {
            wear(Wearpos.Torso, obj("mithril_platebody"))
            wear(Wearpos.Hat, obj("mithril_full_helm"))
            assertFalse(done(417))

            wear(Wearpos.Legs, obj("mithril_plateskirt"))
            assertTrue(done(417))
            assertFalse(done(418))

            wear(Wearpos.Torso, obj("rune_platebody"))
            wear(Wearpos.Hat, obj("rune_full_helm"))
            wear(Wearpos.Legs, obj("rune_platelegs"))
            assertTrue(done(419))
            assertFalse(done(418))
        }

    @Test
    fun GameTestState.`a mixed set does not complete a full set task`() =
        runGameTest(LeagueGearTasks::class) {
            wear(Wearpos.Torso, obj("adamant_platebody"))
            wear(Wearpos.Hat, obj("adamant_full_helm"))
            wear(Wearpos.Legs, obj("rune_platelegs"))
            assertFalse(done(418))
        }

    @Test
    fun GameTestState.`any one piece of steel armour completes its task`() =
        runGameTest(LeagueGearTasks::class) {
            wear(Wearpos.Hat, obj("steel_med_helm"))
            assertFalse(done(1072))

            wear(Wearpos.Hat, obj("steel_full_helm"))
            assertTrue(done(1072))
        }
}
