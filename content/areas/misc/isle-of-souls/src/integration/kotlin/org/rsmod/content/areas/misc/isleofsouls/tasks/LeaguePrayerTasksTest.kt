package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.prayer.tab.Prayer
import org.rsmod.content.interfaces.prayer.tab.PrayerEvents
import org.rsmod.content.skills.prayer.configs.AltarKind
import org.rsmod.content.skills.prayer.scripts.Altars
import org.rsmod.content.skills.prayer.scripts.Burying
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.varbit.VarBitType

class LeaguePrayerTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prayer(enabled: VarBitType): Prayer =
        Prayer(
            id = 0,
            name = "Test",
            level = 1,
            sound = synths.human_hit_1,
            enabled = enabled,
            drainEffect = 1,
            overhead = null,
            unlocked = null,
            unlockState = 1,
            defenceReq = null,
            lockedMessage = null,
        )

    @Test
    fun GameTestState.`burying any bones completes the first task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(done(97))

            eventBus.publish(Burying.BoneBuried(player, obj("bones")))

            assertTrue(done(97))
            assertFalse(done(188))
        }

    @Test
    fun GameTestState.`burying dragon or wyvern bones completes the dragon bones task`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Burying.BoneBuried(player, obj("big_bones")))
            assertFalse(done(188))

            eventBus.publish(Burying.BoneBuried(player, obj("wyvern_bones")))
            assertTrue(done(188))
        }

    @Test
    fun GameTestState.`burying dragon bones completes the dragon bones task`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Burying.BoneBuried(player, obj("dragon_bones")))
            assertTrue(done(188))
        }

    @Test
    fun GameTestState.`offering bones at the chaos altar completes the sacrifice tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Altars.BoneOffered(player, obj("bones"), AltarKind.Chaos, false))
            assertTrue(done(969))
            assertFalse(done(970))

            // A saved bone is still an offering.
            eventBus.publish(
                Altars.BoneOffered(player, obj("dragon_bones"), AltarKind.Chaos, saved = true)
            )
            assertTrue(done(970))
        }

    @Test
    fun GameTestState.`offering bones at a normal altar completes no sacrifice task`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(
                Altars.BoneOffered(player, obj("dragon_bones"), AltarKind.Standard, false)
            )
            assertFalse(done(969))
            assertFalse(done(970))
        }

    @Test
    fun GameTestState.`restoring prayer points completes the altar tasks by amount`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Altars.PrayerRestored(player, 4))
            assertFalse(done(94))

            eventBus.publish(Altars.PrayerRestored(player, 5))
            assertTrue(done(94))
            assertFalse(done(65))

            eventBus.publish(Altars.PrayerRestored(player, 75))
            assertTrue(done(65))
        }

    @Test
    fun GameTestState.`using protection and the high level prayers completes their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(PrayerEvents.Enabled(player, prayer(varbits.protect_from_melee)))
            assertTrue(done(387))
            assertFalse(done(726))
            assertFalse(done(727))

            eventBus.publish(PrayerEvents.Enabled(player, prayer(varbits.chivalry)))
            assertTrue(done(726))
            assertFalse(done(727))

            eventBus.publish(PrayerEvents.Enabled(player, prayer(varbits.piety)))
            assertTrue(done(727))
        }

    @Test
    fun GameTestState.`strength and reflexes together complete their task`() =
        runGameTest(LeagueSkillTasks::class) {
            player.setVarBit(varbits.superhuman_strength, 1)
            eventBus.publish(PrayerEvents.Enabled(player, prayer(varbits.superhuman_strength)))
            assertFalse(done(95))

            player.setVarBit(varbits.improved_reflexes, 1)
            eventBus.publish(PrayerEvents.Enabled(player, prayer(varbits.improved_reflexes)))
            assertTrue(done(95))
        }

    @Test
    fun GameTestState.`prayer level 99 completes its milestone when bones are buried`() =
        runGameTest(LeagueSkillTasks::class) {
            player.stats[stats.prayer] = 98
            eventBus.publish(Burying.BoneBuried(player, obj("bones")))
            assertFalse(done(234))

            player.stats[stats.prayer] = 99
            eventBus.publish(Burying.BoneBuried(player, obj("bones")))
            assertTrue(done(234))
        }
}
