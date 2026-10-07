package org.rsmod.content.skills.firemaking.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class FiremakingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    /** Using the tinderbox starts the script at once, so its first messages are sent right away. */
    private fun GameTestScope.useTinderbox(logs: String) {
        val comp = Component(components.inv_items.packed)
        val tinderbox = obj("tinderbox")
        val slot = player.inv.indexOfFirst { it?.id == obj(logs).id }
        val tinderboxSlot = player.inv.indexOfFirst { it?.id == tinderbox.id }
        eventBus.publish(
            IfOverlayButtonT(player, tinderboxSlot, tinderbox, slot, obj(logs), comp, comp)
        )
    }

    private fun GameTestScope.prepare(logs: String, level: Int) {
        player.teleport(coords)
        player.clearInv()
        player.inv[0] = InvObj(obj("tinderbox"))
        player.inv[1] = InvObj(obj(logs))
        player.stats[stats.firemaking] = level
    }

    private fun GameTestScope.fireAt(tile: CoordGrid) =
        findLocs(tile).firstOrNull { locTypes[it.id]?.name == "Fire" }

    @Test
    fun GameTestState.`light logs and step west`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)

            random.next = 0 // Guarantee the fire lights.
            useTinderbox("logs")
            advanceUntil({ fireAt(coords) != null }, timeoutTicks = 20)

            val fire = fireAt(coords)
            check(fire != null) { "Expected a fire where the player stood." }
            // Cooking treats a loc named "Fire" with no first option as a fire to cook on.
            val type = locTypes[fire.id]
            check(type != null && type.op.getOrNull(0).isNullOrBlank()) {
                "Expected the fire to have no first option so it can be cooked on."
            }
            check(player.count(obj("logs")) == 0) { "Expected the logs to be used." }
            advance(ticks = 3)
            check(player.coords == coords.translateX(-1)) { "Expected the player to step west." }
        }

    @Test
    fun GameTestState.`a failed attempt tries again`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)

            random.next = 100 // First attempt fails.
            random.next = 0
            useTinderbox("logs")
            advanceUntil({ fireAt(coords) != null }, timeoutTicks = 30)

            check(fireAt(coords) != null) { "Expected the second attempt to light the logs." }
        }

    @Test
    fun GameTestState.`oak logs need level 15`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("oak_logs", 14)

            useTinderbox("oak_logs")
            assertMessageSent("You need a Firemaking level of 15 to burn oak logs.")
            check(player.count(obj("oak_logs")) == 1) { "Oak logs should be untouched." }
            check(fireAt(coords) == null) { "No fire should be lit." }
        }

    @Test
    fun GameTestState.`cannot light a fire on top of another fire`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)

            random.next = 0
            useTinderbox("logs")
            advanceUntil({ fireAt(coords) != null }, timeoutTicks = 20)
            advance(ticks = 3)

            player.teleport(coords)
            player.inv[1] = InvObj(obj("logs"))
            useTinderbox("logs")
            assertMessageSent("You can't light a fire here.")
            check(player.count(obj("logs")) == 1) { "Logs should be untouched." }
        }

    @Test
    fun GameTestState.`fire burns out and leaves ashes`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)

            random.next = 0
            useTinderbox("logs")
            advanceUntil({ fireAt(coords) != null }, timeoutTicks = 20)
            val fireTile = coords

            advanceUntil({ fireAt(fireTile) == null }, timeoutTicks = 210)

            check(fireAt(fireTile) == null) { "Expected the fire to burn out." }
            advance(ticks = 2)
            val ashes = objRepo.findAll(fireTile).any { it.type == obj("ashes").id }
            check(ashes) { "Expected ashes where the fire was." }
        }

    @Test
    fun GameTestState.`walking away stops lighting and never holds the player`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)

            random.next = 0
            useTinderbox("logs")
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while lighting." }

            // Walking and clicking something else both close everything with `ifClose`.
            player.ifClose(eventBus)
            advance(ticks = 10)

            check(fireAt(coords) == null) { "Expected no fire after walking away." }
            check(player.count(obj("logs")) == 1) { "Expected the logs to be kept." }
        }

    @Test
    fun GameTestState.`using the tinderbox again while lighting starts over without an error`() =
        runGameTest(Firemaking::class, HeldUOpScript::class) {
            prepare("logs", 1)
            player.inv[2] = InvObj(obj("logs"))

            random.next = 0
            useTinderbox("logs")
            advance(ticks = 1)

            // This used to throw while the player was delayed, which logged them out.
            useTinderbox("logs")
            advanceUntil({ fireAt(coords) != null }, timeoutTicks = 20)

            check(player.count(obj("logs")) == 1) { "Expected only one log to be lit." }
        }
}
