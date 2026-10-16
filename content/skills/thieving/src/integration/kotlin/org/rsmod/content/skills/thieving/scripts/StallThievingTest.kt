package org.rsmod.content.skills.thieving.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Stands in for the Dodgy Deals relic: stalls never run out and are robbed again and again. */
class StallRelicStub : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<StallThieving.Modifiers> {
            keepStall = true
            repeat = true
        }
    }
}

class StallThievingTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.thieving] = level
        player.statMap.setXP(stats.thieving, 0)
    }

    private fun GameTestScope.stall(internal: String): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val loc = placeMapLoc(coords, type)
        player.teleport(loc.coords.translateX(-1))
        return loc
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.thieving) / 10.0

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 12) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.locType(internal: String) =
        locTypes.values.first { it.internalName == internal }

    @Test
    fun GameTestState.`a silk stall gives silk and experience, then sits empty until it refills`() =
        runGameTest(StallThieving::class) {
            val loc = stall("silkthiefstall")
            prepare(20)

            player.opLoc2(loc)
            advanceUntilMessage("You steal a silk from the silk stall.", timeoutTicks = 6)

            check(player.count(obj("silk")) == 1) { "Expected silk." }
            check(fineXp() == 24.0) { "Expected 24 experience, was ${fineXp()}." }
            assertExists(coords, locType("market"))

            advance(ticks = 10)
            assertExists(coords, locType("silkthiefstall"))
        }

    @Test
    fun GameTestState.`a stall needs its thieving level`() =
        runGameTest(StallThieving::class) {
            val loc = stall("silkthiefstall")
            prepare(19)

            player.opLoc2(loc)
            advanceUntilMessage("You need a Thieving level of 20 to steal from the silk stall.", 3)
            check(player.count(obj("silk")) == 0) { "Expected nothing taken." }
        }

    @Test
    fun GameTestState.`a full inventory stops a theft`() =
        runGameTest(StallThieving::class) {
            val loc = stall("silkthiefstall")
            prepare(20)
            player.fillInv()

            player.opLoc2(loc)
            advanceUntilMessage("You don't have enough inventory space to do that.", 3)
        }

    @Test
    fun GameTestState.`stalls that never run out are robbed again and again`() =
        runGameTest(StallThieving::class, StallRelicStub::class) {
            val loc = stall("silkthiefstall")
            prepare(20)

            player.opLoc2(loc)
            advance(ticks = 14)

            val silk = player.count(obj("silk"))
            check(silk >= 3) { "Expected several silks, got $silk." }
            assertExists(coords, locType("silkthiefstall"))
        }

    @Test
    fun GameTestState.`the stall loot follows its table`() =
        runGameTest(StallThieving::class) {
            val loc = stall("cakethiefstall")
            prepare(5)
            // The weights are bread 5, cake 13 and chocolate slice 2, so 0.99 is a chocolate slice.
            random.next = 99
            random.next = 1

            player.opLoc2(loc)
            advance(ticks = 6)

            check(player.count(obj("chocolate_slice")) == 1) { "Expected a chocolate slice." }
        }
}
