package org.rsmod.content.other.food

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

/** Eating food that has several bites, effects or outcomes, and drinks. Foods are found by name. */
class FoodVarietyTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    /** The first obj named [name] that can be eaten or drunk. */
    private fun GameTestScope.named(name: String): UnpackedObjType =
        objTypes.values
            .filter { it.certtemplate <= 0 && it.placeholdertemplate <= 0 }
            .sortedBy { it.id }
            .first { it.name == name && it.iop.getOrNull(0) in listOf("Eat", "Drink") }

    /** The first obj named [name], whatever it does. */
    private fun GameTestScope.item(name: String): UnpackedObjType =
        objTypes.values
            .filter { it.certtemplate <= 0 && it.placeholdertemplate <= 0 }
            .sortedBy { it.id }
            .first { it.name == name }

    private fun GameTestScope.prepare(hitpointsLevel: Int, hitpoints: Int, vararg food: String) {
        player.teleport(coords)
        player.clearInv()
        food.forEachIndexed { slot, name -> player.inv[slot] = InvObj(named(name)) }
        player.stats[stats.hitpoints] = hitpointsLevel
        player.stats.setCurrentLevel(stats.hitpoints, hitpoints)
    }

    private fun GameTestScope.eat(slot: Int) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2))
    }

    @Test
    fun GameTestState.`meat and bread can be eaten`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Cooked meat", "Cooked chicken", "Cooked rabbit", "Bread")

            eat(0)
            advance(ticks = 3)
            eat(1)
            advance(ticks = 3)
            eat(2)
            advance(ticks = 3)
            eat(3)
            advance(ticks = 1)

            // 3 + 3 + 5 + 5.
            check(player.hitpoints == 26) { "Expected 26 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`a cake is eaten in three bites`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Cake")

            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 14) { "Expected 14 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == item("2/3 cake").id) { "Expected two thirds of a cake." }

            advance(ticks = 2)
            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 18) { "Expected 18 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == item("Slice of cake").id) { "Expected a slice of cake." }

            advance(ticks = 3)
            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 22) { "Expected 22 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0] == null) { "The last slice should be gone." }
        }

    @Test
    fun GameTestState.`a pie leaves its dish and can be bitten again the next tick`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Wild pie")
            player.stats[stats.slayer] = 50
            player.stats[stats.ranged] = 60

            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 21) { "Expected 21 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == item("Half a wild pie").id) { "Expected half a wild pie." }
            check(player.stat(stats.slayer) == 55) { "Expected a Slayer boost of 5." }
            check(player.stat(stats.ranged) == 64) { "Expected a Ranged boost of 4." }

            // Members' pies have a one tick eat delay.
            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 32) { "Expected 32 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == item("Pie dish").id) { "Expected an empty pie dish." }
        }

    @Test
    fun GameTestState.`stew leaves a bowl`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Stew")

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 21) { "Expected 21 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == item("Bowl").id) { "Expected an empty bowl." }
        }

    @Test
    fun GameTestState.`hunter meat heals again seven ticks later`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Cooked kyatt")

            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 19) { "Expected 19 hitpoints, got ${player.hitpoints}." }

            advance(ticks = 4)
            check(player.hitpoints == 19) { "The second heal should not have come yet." }

            advance(ticks = 5)
            check(player.hitpoints == 27) { "Expected 27 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`a jug of wine heals and lowers attack`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 50, "Jug of wine")
            player.stats[stats.attack] = 50

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 61) { "Expected 61 hitpoints, got ${player.hitpoints}." }
            check(player.stat(stats.attack) == 48) { "Expected Attack 48." }
            check(player.inv[0]?.id == item("Jug").id) { "Expected an empty jug." }
        }

    @Test
    fun GameTestState.`beer boosts strength and lowers attack`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 50, "Beer")
            player.stats[stats.attack] = 50
            player.stats[stats.strength] = 50

            eat(0)
            advance(ticks = 1)

            check(player.hitpoints == 51) { "Expected 51 hitpoints, got ${player.hitpoints}." }
            check(player.stat(stats.strength) == 52) { "Expected Strength 52." }
            check(player.stat(stats.attack) == 46) { "Expected Attack 46." }
        }

    @Test
    fun GameTestState.`a kebab rolls its outcome`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Kebab")
            player.stats[stats.attack] = 50

            random.next = 0 // The amazing kebab.
            eat(0)
            advance(ticks = 1)

            // 7 + 24% of 99 hitpoints.
            check(player.hitpoints == 40) { "Expected 40 hitpoints, got ${player.hitpoints}." }
            check(player.stat(stats.attack) == 52) { "Expected Attack 52." }
        }

    @Test
    fun GameTestState.`cooked crab meat is eaten in five bites`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Cooked crab meat")

            repeat(5) {
                val before = player.hitpoints
                eat(0)
                advance(ticks = 2)
                check(player.hitpoints == before + 2) { "Bite ${it + 1} should heal 2." }
            }

            check(player.inv[0] == null) { "Five bites should finish the crab." }
        }

    @Test
    fun GameTestState.`blighted and minigame food heal like the normal food`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 10, "Blighted manta ray")
            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 32) { "Expected 32 hitpoints, got ${player.hitpoints}." }

            player.clearInv()
            player.inv[0] = InvObj(objTypes.values.first { it.internalName == "br_shark" })
            advance(ticks = 3)
            eat(0)
            advance(ticks = 1)
            check(player.hitpoints == 52) { "Expected 52 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`strange fruit restores run energy`() =
        runGameTest(Eating::class, HeldOpScript::class) {
            prepare(99, 99, "Strange fruit")
            player.runEnergy = 0

            eat(0)
            advance(ticks = 1)

            // Run energy also regenerates a little each tick.
            check(player.runEnergy in 3000..3100) {
                "Expected 30% run energy, got ${player.runEnergy}."
            }
        }
}
