package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueSkillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.herblore.scripts.Herblore
import org.rsmod.content.skills.herblore.scripts.HerbloreModifiers
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class LeagueHerbloreTest {
    class Mods @Inject constructor(val modifiers: HerbloreModifiers)

    private fun Player.choose(relic: RelicItem) {
        VarPlayerIntMapSetter.set(this, RelicVarBits.selection[relic.tier], relic.option)
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.useOn(first: String, second: String) {
        val comp = Component(components.inv_items.packed)
        val firstSlot = player.inv.indexOfFirst { it?.id == obj(first).id }
        val secondSlot = player.inv.indexOfFirst { it?.id == obj(second).id }
        eventBus.publish(
            IfOverlayButtonT(player, firstSlot, obj(first), secondSlot, obj(second), comp, comp)
        )
    }

    @Test
    fun GameTestState.`friendly forager adds a dose and saves most secondaries`() =
        runInjectedGameTest(Mods::class, null, LeagueHerblore::class) { deps ->
            val modifiers = deps.modifiers
            assertEquals(0, modifiers.extraDoses(player))

            player.choose(RelicItem.FriendlyForager)
            assertEquals(0, modifiers.extraDoses(player), "The pouch is needed too.")

            player.inv[0] = InvObj(RelicItem.FriendlyForager.item)
            assertEquals(1, modifiers.extraDoses(player))

            val saved = (1..2000).count { modifiers.savesSecondary(player) }
            assertTrue(saved in 1700..1900, "Expected about 90% saved, got $saved of 2000.")
        }

    @Test
    fun GameTestState.`without the relic a potion has three doses and uses its secondary`() =
        runInjectedGameTest(
            Mods::class,
            null,
            LeagueHerblore::class,
            Herblore::class,
            HeldUOpScript::class,
        ) {
            player.teleport(CoordGrid(0, 50, 50, 34, 31))
            player.clearInv()
            player.inv[0] = InvObj(obj("guamvial"))
            player.inv[1] = InvObj(obj("eye_of_newt"))
            player.stats[stats.herblore] = 3

            useOn("eye_of_newt", "guamvial")
            advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
            player.resumePauseButton(components.skillmulti_buttons[0], 1)
            advanceUntil({ player.count(obj("3dose1attack")) == 1 }, timeoutTicks = 10)

            assertEquals(0, player.count(obj("eye_of_newt")))
        }

    @Test
    fun GameTestState.`friendly forager makes four dose potions and completes the task`() =
        runInjectedGameTest(
            Mods::class,
            null,
            LeagueHerblore::class,
            LeagueSkillTasks::class,
            Herblore::class,
            HeldUOpScript::class,
        ) {
            player.choose(RelicItem.FriendlyForager)
            player.teleport(CoordGrid(0, 50, 50, 34, 31))
            player.clearInv()
            player.inv[0] = InvObj(RelicItem.FriendlyForager.item)
            player.inv[1] = InvObj(obj("guamvial"))
            player.inv[2] = InvObj(obj("eye_of_newt"))
            player.stats[stats.herblore] = 3

            useOn("eye_of_newt", "guamvial")
            advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
            player.resumePauseButton(components.skillmulti_buttons[0], 1)
            advanceUntil({ player.count(obj("4dose1attack")) == 1 }, timeoutTicks = 10)

            assertTrue(LeagueTaskTracker(eventBus, LeagueTeams(players)).isComplete(player, 396))
        }
}
