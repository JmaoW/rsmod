package org.rsmod.content.skills.hunter.scripts

import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class HunterFamiliesTest {
    private val coords = CoordGrid(0, 10, 10, 20, 20)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.loc(internal: String): UnpackedLocType =
        locTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        for (dx in -3..3) {
            for (dz in -3..3) {
                allocZoneCollision(coords.translate(dx * 8, dz * 8))
            }
        }
        player.observerUUID = player.uuid
        player.teleport(coords)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.hunter] = level
        player.statMap.setXP(stats.hunter, 0)
        advance(ticks = 12)
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

    private fun GameTestScope.bound(internal: String, at: CoordGrid) =
        BoundLocInfo(at, org.rsmod.game.loc.LocEntity(loc(internal).id, 10, 0), 2, 1, 1, 0)

    private fun GameTestScope.useItem(slot: Int, op: IfButtonOp) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, op))
    }

    // region box trap, magic box

    @Test
    fun GameTestState.`a chinchompa is caught in a box trap`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(53, "hunting_box_trap" to 1)
            val spot = player.coords
            useItem(0, IfButtonOp.Op2)
            advanceUntilMessage("You set up the box trap.", 8)
            assertExists(spot, loc("hunting_boxtrap_empty"))
            creature("hunting_chinchompa", spot.translate(2, 0))

            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")
            assertExists(spot, loc("hunting_boxtrap_full_chinchompa"))

            random.next = 0
            player.opLoc1(bound("hunting_boxtrap_full_chinchompa", spot))
            advance(ticks = 6)
            check(player.count(obj("chinchompa_captured")) == 1) { "Expected a chinchompa." }
            check(player.count(obj("hunting_box_trap")) == 1) { "Expected the trap back." }
            check(fineXp() == 198.4) { "Expected 198.4 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a catch can be collected and the trap set again`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(53, "hunting_box_trap" to 1)
            val spot = player.coords
            useItem(0, IfButtonOp.Op2)
            advanceUntilMessage("You set up the box trap.", 8)
            creature("hunting_chinchompa", spot.translate(2, 0))
            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")

            random.next = 0
            player.opLoc2(bound("hunting_boxtrap_full_chinchompa", spot))
            advance(ticks = 6)
            check(player.count(obj("chinchompa_captured")) == 1) { "Expected a chinchompa." }
            check(player.count(obj("hunting_box_trap")) == 0) {
                "Expected the trap kept in the ground."
            }
            assertExists(spot, loc("hunting_boxtrap_empty"))
        }

    @Test
    fun GameTestState.`a magic box catches an imp and gives it in a box`() =
        runGameTest(HunterTraps::class, HeldOpScript::class) {
            prepare(71, "magic_imp_box" to 1)
            val spot = player.coords
            useItem(0, IfButtonOp.Op2)
            advanceUntilMessage("You set up the magic box.", 8)
            assertExists(spot, loc("hunting_imptrap_empty"))
            creature("imp", spot.translate(1, 0))

            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")
            player.opLoc1(bound("hunting_imptrap_full", spot))
            advance(ticks = 5)
            check(player.count(obj("magic_imp_box_full")) == 1) { "Expected an imp in a box." }
            check(player.count(obj("magic_imp_box")) == 0) { "The box is the imp's now." }
            check(fineXp() == 450.0) { "Expected 450 experience, got ${fineXp()}." }
        }

    // endregion

    // region deadfall, net

    @Test
    fun GameTestState.`a deadfall is built on a boulder and catches a kebbit`() =
        runGameTest(HunterTraps::class) {
            prepare(23, "knife" to 1, "logs" to 1)
            val boulderAt = coords.translate(3, 0)
            val boulder = placeMapLoc(boulderAt, loc("hunting_deadfall_boulder"))
            player.teleport(boulderAt.translate(-1, 0))
            advance(ticks = 2)

            player.opLoc1(boulder)
            advanceUntilMessage("You set up the deadfall.", 12)
            assertExists(boulderAt, loc("hunting_deadfall_trap"))
            check(player.count(obj("logs")) == 0) { "Expected the logs used." }

            creature("huntingbeast_claws", boulderAt.translate(0, 1))
            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")
            assertExists(boulderAt, loc("hunting_deadfall_full_claw"))

            random.next = 0
            player.opLoc1(bound("hunting_deadfall_full_claw", boulderAt))
            advance(ticks = 6)
            check(player.count(obj("huntingbeast_claws")) == 1) { "Expected kebbit claws." }
            check(player.count(obj("bones")) == 1) { "Expected bones." }
            check(fineXp() == 102.4) { "Expected 102.4 experience, got ${fineXp()}." }
            // The boulder is back once the trap is taken down.
            assertExists(boulderAt, loc("hunting_deadfall_boulder"))
        }

    @Test
    fun GameTestState.`a deadfall needs a knife and logs`() =
        runGameTest(HunterTraps::class) {
            prepare(23)
            // Away from the other deadfall test's boulder, which the world keeps.
            val boulderAt = coords.translate(12, 12)
            val boulder = placeMapLoc(boulderAt, loc("hunting_deadfall_boulder"))
            player.teleport(boulderAt.translate(-1, 0))
            advance(ticks = 2)
            player.opLoc1(boulder)
            // The hunter walks round to the front of the boulder first.
            advanceUntilMessage("You need a knife and logs to build this trap.", 8)
        }

    @Test
    fun GameTestState.`a net trap is set on a young tree and catches an orange salamander`() =
        runGameTest(HunterTraps::class) {
            prepare(47, "rope" to 1, "net" to 1)
            val treeAt = coords.translate(3, 0)
            val tree = placeMapLoc(treeAt, loc("hunting_sapling_up_orange"))
            player.teleport(treeAt.translate(-1, 0))
            advance(ticks = 2)

            player.opLoc1(tree)
            advanceUntilMessage("You set up the net trap.", 12)
            assertExists(treeAt, loc("hunting_sapling_set_orange"))
            check(player.count(obj("rope")) == 0) { "Expected the rope used." }

            creature("salamander_orange", treeAt.translate(2, 2))
            random.next = 0
            advanceUntilMessage("Something has been caught in your trap!")

            random.next = 0
            val full = loc("hunting_sapling_full_orange")
            player.opLoc1(
                BoundLocInfo(treeAt, org.rsmod.game.loc.LocEntity(full.id, 10, 0), 2, 1, 2, 0)
            )
            advance(ticks = 6)
            check(player.count(obj("orange_salamander")) == 1) { "Expected an orange salamander." }
            check(player.count(obj("rope")) == 1) { "Expected the rope back." }
            check(fineXp() == 224.0) { "Expected 224 experience, got ${fineXp()}." }
            assertExists(treeAt, loc("hunting_sapling_up_orange"))
        }

    // endregion

    // region pits

    @Test
    fun GameTestState.`a teased cat is led across a pit and falls in`() =
        runGameTest(HunterTraps::class, Pitfalls::class) {
            prepare(31, "knife" to 1, "logs" to 1, "hunting_teasing_stick" to 1)
            val pitAt = coords.translate(4, 0)
            val pit = placeMapLoc(pitAt, loc("hunting_pitfall_7"))
            player.teleport(pitAt.translate(-1, 0))
            advance(ticks = 2)

            player.opLoc3(pit)
            advanceUntilMessage("You set up the pit.", 12)
            check(player.vars[Pitfalls.PIT_BIT.getValue(7)] == Pitfalls.PIT_SET) {
                "Expected a set pit."
            }

            val cat = creature("hunting_jaguar", pitAt.translate(-4, 0))
            player.opNpc1(cat)
            advanceUntilMessage("The creature follows you. Lead it to your pit and jump across.", 8)
            // The cat is brought close for the jump.
            cat.telejump(pitAt.translate(-2, 0))

            random.next = 0
            player.opLoc1(pit)
            advanceUntilMessage("The creature falls into your pit!", 12)
            advanceUntilMessage("Something has been caught in your pit!", 12)
            check(player.vars[Pitfalls.PIT_BIT.getValue(7)] == Pitfalls.PIT_FULL) {
                "Expected a full pit."
            }

            random.next = 0
            player.opLoc2(pit)
            advance(ticks = 6)
            check(player.count(obj("big_bones")) == 1) { "Expected big bones." }
            check(player.count(obj("hunting_larupia_meat")) == 1) { "Expected raw larupia." }
            check(fineXp() == 180.0) { "Expected 180 experience, got ${fineXp()}." }
            check(player.vars[Pitfalls.PIT_BIT.getValue(7)] == Pitfalls.PIT_EMPTY) {
                "Expected the pit emptied."
            }
        }

    // endregion
}
