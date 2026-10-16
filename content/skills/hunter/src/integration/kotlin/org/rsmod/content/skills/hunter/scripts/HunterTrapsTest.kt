package org.rsmod.content.skills.hunter.scripts

import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Stands in for the Animal Wrangler relic: every trap catches, and a catch counts twice. */
class TrapRelicStub : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<HunterTraps.Roll> { success = true }
        onEvent<HunterTraps.Collecting> {
            catches = 2
            xpMultiplier = 1.0
        }
        onEvent<HunterTraps.Modifiers> {
            scanTicks = 1
            settleTicks = 1
        }
    }
}

@OptIn(InternalApi::class)
class HunterTrapsTest {
    /** Ground with no map on it, so that nothing is in the way of a trap. */
    private val coords = CoordGrid(0, 10, 10, 20, 20)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.loc(internal: String): UnpackedLocType =
        locTypes.values.first { it.internalName == internal }

    private fun GameTestScope.clearGround() {
        for (dx in -2..2) {
            for (dz in -2..2) {
                allocZoneCollision(coords.translate(dx * 8, dz * 8))
            }
        }
    }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        clearGround()
        player.observerUUID = player.uuid
        player.teleport(coords)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.hunter] = level
        player.statMap.setXP(stats.hunter, 0)
        // A fresh test player counts as being in combat for a few ticks.
        advance(ticks = 12)
    }

    /** "Lay" is the first inventory option, which is the second button option. */
    private fun GameTestScope.useItem(slot: Int) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2))
    }

    private fun GameTestScope.creature(internal: String, at: CoordGrid): Npc {
        val type = npcTypes.values.first { it.internalName == internal }
        return spawnNpc(at, type) { noneMode() }
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.hunter) / 10.0

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 40) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    /** Lays a bird snare where the player stands and returns where that was. */
    private fun GameTestScope.layBirdSnare(): CoordGrid {
        val spot = player.coords
        useItem(0)
        advanceUntilMessage("You set up the bird snare.", 8)
        return spot
    }

    @Test
    fun GameTestState.`a bird snare is laid where the trapper stands`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()

            assertExists(spot, loc("hunting_ojibway_trap"))
            check(player.count(obj("hunting_ojibway_bird_snare")) == 0) {
                "Expected the snare gone."
            }
        }

    @Test
    fun GameTestState.`a trapper needs the level for the trap`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(10, "hunting_box_trap" to 1)
            useItem(0)
            assertMessageSent("You need a Hunter level of 27 to set a box trap.")
            check(player.count(obj("hunting_box_trap")) == 1) { "Expected the trap kept." }
        }

    @Test
    fun GameTestState.`a bird walks into a snare, and checking it gives the catch and the xp`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()
            creature("hunting_bird_jungle", spot.translate(2, 0))

            // 0 is under every chance, so the swift is caught.
            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")
            assertExists(spot, loc("hunting_ojibway_trap_full_jungle"))

            val trap =
                BoundLocInfo(
                    spot,
                    org.rsmod.game.loc.LocEntity(loc("hunting_ojibway_trap_full_jungle").id, 10, 0),
                    2,
                    1,
                    1,
                    0,
                )
            random.next = 0
            random.next = 0
            player.opLoc1(trap)
            advance(ticks = 6)

            check(player.count(obj("hunting_ojibway_bird_snare")) == 1) {
                "Expected the snare back."
            }
            check(player.count(obj("bones")) == 1) { "Expected bones." }
            check(player.count(obj("spit_raw_bird_meat")) == 1) { "Expected bird meat." }
            check(fineXp() == 34.0) { "Expected 34 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a snare that fails breaks, and dismantling gives the snare back`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()
            creature("hunting_bird_jungle", spot.translate(1, 0))

            // 99 is over every chance, so the swift gets away.
            random.next = 99
            advance(ticks = 20)
            assertExists(spot, loc("hunting_ojibway_trap_broken"))

            val broken =
                BoundLocInfo(
                    spot,
                    org.rsmod.game.loc.LocEntity(loc("hunting_ojibway_trap_broken").id, 10, 0),
                    2,
                    1,
                    1,
                    0,
                )
            player.opLoc1(broken)
            advance(ticks = 5)
            check(player.count(obj("hunting_ojibway_bird_snare")) == 1) {
                "Expected the snare back."
            }
        }

    @Test
    fun GameTestState.`a trap nobody checks collapses`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()

            advanceUntilMessage("Your trap has collapsed.", HunterTraps.LIFETIME + 10)
            assertDoesNotExist(spot, loc("hunting_ojibway_trap"))
        }

    @Test
    fun GameTestState.`a creature that is too high a level for the trapper is left alone`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()
            // A golden warbler needs level 5.
            creature("hunting_bird_desert", spot.translate(1, 0))
            random.next = 0
            advance(ticks = 25)
            assertExists(spot, loc("hunting_ojibway_trap"))
        }

    @Test
    fun GameTestState.`a relic can make every catch certain and double it`() =
        runGameTest(HunterTraps::class, HeldOpScript::class, TrapRelicStub::class) {
            prepare(1, "hunting_ojibway_bird_snare" to 1)
            val spot = layBirdSnare()
            creature("hunting_bird_jungle", spot.translate(1, 0))
            // A roll of 99 would fail, but the relic makes it a catch.
            random.next = 99
            advanceUntilMessage("Something has been caught in your trap!")

            val trap =
                BoundLocInfo(
                    spot,
                    org.rsmod.game.loc.LocEntity(loc("hunting_ojibway_trap_full_jungle").id, 10, 0),
                    2,
                    1,
                    1,
                    0,
                )
            random.next = 0
            random.next = 0
            random.next = 0
            random.next = 0
            player.opLoc1(trap)
            advance(ticks = 6)

            check(player.count(obj("bones")) == 2) {
                "Expected two bones, got ${player.count(obj("bones"))}."
            }
            check(fineXp() == 68.0) { "Expected 68 experience, got ${fineXp()}." }
        }
}
