package org.rsmod.content.skills.runecraft.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.game.ui.Component

class EssencePouchesTest {
    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            if (type.isStackable) {
                player.inv[slot++] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[slot++] = InvObj(type) }
            }
        }
        player.stats[stats.runecrafting] = level
        player.stats[stats.crafting] = 99
        player.statMap.setXP(stats.runecrafting, 0)
        for (pouch in Pouches.all) {
            setVar(pouch.count, 0)
            setVar(pouch.kind, 0)
        }
    }

    private fun GameTestScope.setVar(varbit: VarBitType, value: Int) {
        player.withProtectedAccess { vars[varbit] = value }
        advance()
    }

    private fun GameTestScope.option(internal: String, op: IfButtonOp) {
        val slot = player.inv.indexOfFirst { it?.id == obj(internal).id }
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, op))
    }

    private fun GameTestScope.fill(internal: String) = option(internal, IfButtonOp.Op2)

    private fun GameTestScope.empty(internal: String) = option(internal, IfButtonOp.Op3)

    private fun GameTestScope.inspect(internal: String) = option(internal, IfButtonOp.Op4)

    private fun GameTestScope.useOn(first: String, second: String) {
        val comp = Component(components.inv_items.packed)
        val firstSlot = player.inv.indexOfFirst { it?.id == obj(first).id }
        val secondSlot = player.inv.indexOfFirst { it?.id == obj(second).id }
        eventBus.publish(
            IfOverlayButtonT(player, firstSlot, obj(first), secondSlot, obj(second), comp, comp)
        )
    }

    @Test
    fun GameTestState.`a small pouch takes three essence and gives them back`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(1, "rcu_pouch_small" to 1, "blankrune" to 5)

            fill("rcu_pouch_small")
            advance()

            check(player.count(obj("blankrune")) == 2) { "Expected 3 essence in the pouch." }
            check(player.vars[Pouches.small.count] == 3) { "Expected the pouch to hold 3." }

            empty("rcu_pouch_small")
            advance()

            check(player.count(obj("blankrune")) == 5) { "Expected the essence back." }
            check(player.vars[Pouches.small.count] == 0) { "Expected an empty pouch." }
        }

    @Test
    fun GameTestState.`a pouch keeps one kind of essence`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(1, "rcu_pouch_small" to 1, "blankrune" to 1, "blankrune_high" to 1)

            fill("rcu_pouch_small")
            advance()
            check(player.vars[Pouches.small.count] == 1) { "Expected one essence in the pouch." }
            // Pure essence is taken first, so the rune essence stays behind.
            check(player.count(obj("blankrune")) == 1) { "Expected the rune essence kept." }

            fill("rcu_pouch_small")
            assertMessageSent("You can only keep one kind of essence in a pouch.")
        }

    @Test
    fun GameTestState.`a pouch needs the runecraft level`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(24, "rcu_pouch_medium" to 1, "blankrune" to 5)

            fill("rcu_pouch_medium")
            assertMessageSent("You need a Runecraft level of 25 to use this pouch.")
            check(player.count(obj("blankrune")) == 5) { "Expected the essence kept." }
        }

    @Test
    fun GameTestState.`bigger pouches hold more`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(75, "rcu_pouch_giant" to 1, "blankrune_high" to 20)

            fill("rcu_pouch_giant")
            advance()

            check(player.vars[Pouches.giant.count] == 12) { "Expected the giant pouch to hold 12." }
            check(player.count(obj("blankrune_high")) == 8) { "Expected 8 essence left over." }
        }

    @Test
    fun GameTestState.`emptying stops when the inventory is full`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(50, "rcu_pouch_large" to 1)
            setVar(Pouches.large.count, 9)
            setVar(Pouches.large.kind, 2)
            // Leave exactly two free slots.
            var slot = 1
            while (slot < 26) {
                player.inv[slot++] = InvObj(obj("logs"))
            }

            empty("rcu_pouch_large")
            advance()

            check(player.count(obj("blankrune_high")) == 2) { "Expected only two essence out." }
            check(player.vars[Pouches.large.count] == 7) { "Expected 7 essence left inside." }
        }

    @Test
    fun GameTestState.`checking a pouch says what it holds`() =
        runGameTest(EssencePouches::class, HeldOpScript::class) {
            prepare(1, "rcu_pouch_small" to 1)
            setVar(Pouches.small.count, 2)
            setVar(Pouches.small.kind, 2)

            inspect("rcu_pouch_small")
            assertMessageSent("The small pouch holds 2 pure essence. It can hold 3.")
        }

    @Test
    fun GameTestState.`an abyssal needle stitches four pouches into a colossal one`() =
        runGameTest(EssencePouches::class, HeldUOpScript::class) {
            prepare(
                25,
                "rcu_pouch_small" to 1,
                "rcu_pouch_medium" to 1,
                "rcu_pouch_large" to 1,
                "rcu_pouch_giant" to 1,
                "abyssal_needle" to 1,
            )

            useOn("abyssal_needle", "rcu_pouch_giant")
            advance()

            check(player.count(obj("rcu_pouch_colossal")) == 1) { "Expected a colossal pouch." }
            check(player.count(obj("rcu_pouch_small")) == 0) { "Expected the small pouch used." }
            check(player.count(obj("abyssal_needle")) == 0) { "Expected the needle used." }
        }

    @Test
    fun GameTestState.`a colossal pouch needs all four pouches`() =
        runGameTest(EssencePouches::class, HeldUOpScript::class) {
            prepare(25, "rcu_pouch_small" to 1, "rcu_pouch_giant" to 1, "abyssal_needle" to 1)

            useOn("abyssal_needle", "rcu_pouch_giant")
            assertMessageSent(
                "You need a small, medium, large and giant pouch to make a colossal pouch."
            )
            check(player.count(obj("rcu_pouch_colossal")) == 0) { "No colossal pouch yet." }
        }
}
