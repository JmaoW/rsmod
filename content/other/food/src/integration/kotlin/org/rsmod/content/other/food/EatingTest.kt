package org.rsmod.content.other.food

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.timers
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.stats.plugin.StatRegenScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.scope.TestCombatMasteries
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class EatingTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(hitpointsLevel: Int, hitpoints: Int, vararg food: String) {
        player.teleport(coords)
        player.clearInv()
        food.forEachIndexed { slot, name -> player.inv[slot] = InvObj(obj(name)) }
        player.stats[stats.hitpoints] = hitpointsLevel
        player.stats.setCurrentLevel(stats.hitpoints, hitpoints)
    }

    private fun GameTestScope.eat(slot: Int) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2))
    }

    @Test
    fun GameTestState.`eating a shark heals 20`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 50, "shark")

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 70) { "Expected 70 hitpoints, got ${player.hitpoints}." }
            check(player.count(obj("shark")) == 0) { "Expected the shark to be eaten." }
        }

    @Test
    fun GameTestState.`the second mastery passive raises the healing by a fifth`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 50, "shark")
            TestCombatMasteries.set(player, MasteryStyle.Magic, 2)

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 74) { "Expected 74 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`eating does not heal above the base level`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 90, "shark")

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 99) { "Expected 99 hitpoints, got ${player.hitpoints}." }
            check(player.count(obj("shark")) == 0) { "The shark should still be eaten." }
        }

    @Test
    fun GameTestState.`eating at full health sends no heal message`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 99, "trout")

            eat(0)
            assertMessageSent("You eat the trout.")
            assertMessageNotSent("It heals some health.")
        }

    @Test
    fun GameTestState.`eating sends the eat and heal messages`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 50, "trout")

            eat(0)
            assertMessagesSent("You eat the trout.", "It heals some health.")
        }

    @Test
    fun GameTestState.`standard food cannot be eaten again for three ticks`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "shrimp", "shrimp")

            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 13) { "Expected 13 hitpoints, got ${player.hitpoints}." }

            eat(1)
            advance(ticks = 1)
            check(player.hitpoints == 13) { "The second shrimp should be blocked." }
            check(player.count(obj("shrimp")) == 1) { "A blocked food must not be consumed." }

            advance(ticks = 2)
            eat(1)
            advance(ticks = 1)
            check(player.hitpoints == 16) { "Expected 16 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`a karambwan can be eaten on the same tick as standard food`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "shark", "tbwt_cooked_karambwan")

            eat(0)
            eat(1)
            advance(ticks = 1)

            check(player.hitpoints == 48) { "Expected 48 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`eating delays the next attack`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "shark")
            advance(ticks = 1)
            player.actionDelay = -1

            eat(0)
            advance(ticks = 1)

            // The test clock runs a tick ahead of the player's processed clock, and one more tick
            // has passed since eating, so a three tick delay has one or two ticks left.
            val remaining = player.actionDelay - mapClock.cycle
            check(remaining in 1..2) { "Expected a three tick delay, got $remaining left." }
        }

    @Test
    fun GameTestState.`anglerfish heals above the base level`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 99, "anglerfish")

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 121) { "Expected 121 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`standard food does not heal while overhealed`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 110, "shark")

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 110) { "Hitpoints should be unchanged." }
            check(player.count(obj("shark")) == 0) { "The shark should still be eaten." }
        }

    @Test
    fun GameTestState.`overheal wears off like a stat boost`() =
        runGameTest(StatRegenScript::class) {
            prepare(99, 110)

            player.softTimer(timers.stat_boost_restore, 1)
            advance(ticks = 3)

            check(player.hitpoints < 110) { "Expected overheal to decay, got ${player.hitpoints}." }
            check(player.hitpoints >= 99) { "Hitpoints must not drop below the base level." }
        }
}
