package org.rsmod.content.skills.runecraft.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class RunecraftingTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

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
        player.statMap.setXP(stats.runecrafting, 0)
    }

    private fun GameTestScope.altar(internal: String): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val loc = placeMapLoc(coords, type)
        player.teleport(loc.coords.translateX(-1))
        return loc
    }

    private fun GameTestScope.useOnLoc(loc: BoundLocInfo, item: String) {
        val slot = player.inv.indexOfFirst { it?.id == obj(item).id }
        player.opLocT(loc, obj(item), slot)
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 10) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.runecrafting) / 10.0

    @Test
    fun GameTestState.`rune essence at the air altar makes air runes`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("air_altar")
            prepare(1, "blankrune" to 5)

            player.opLoc1(altar)
            advance()

            check(player.count(obj("airrune")) == 5) { "Expected 5 air runes." }
            check(player.count(obj("blankrune")) == 0) { "Expected the essence to be used." }
            check(fineXp() == 25.0) { "Expected 25 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a high level gives several runes for each essence`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("air_altar")
            prepare(22, "blankrune_high" to 4)

            player.opLoc1(altar)
            advance()

            check(player.count(obj("airrune")) == 12) { "Expected 3 runes per essence." }
            check(fineXp() == 20.0) { "Experience counts essence, not runes: ${fineXp()}." }
        }

    @Test
    fun GameTestState.`the altar needs the runecraft level`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("water_altar")
            prepare(4, "blankrune" to 3)

            player.opLoc1(altar)
            advanceUntilMessage("You need a Runecraft level of 5 to craft water runes.")

            check(player.count(obj("blankrune")) == 3) { "The essence should be kept." }
            check(player.count(obj("waterrune")) == 0) { "No runes should be made." }
        }

    @Test
    fun GameTestState.`rune essence is no good at the cosmic altar`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("cosmic_altar")
            prepare(30, "blankrune" to 3)

            player.opLoc1(altar)
            advanceUntilMessage("You can only bind cosmic runes from pure essence.")

            check(player.count(obj("blankrune")) == 3) { "The essence should be kept." }
        }

    @Test
    fun GameTestState.`pure and rune essence are bound together`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("mind_altar")
            prepare(2, "blankrune" to 2, "blankrune_high" to 3)

            player.opLoc1(altar)
            advance()

            check(player.count(obj("mindrune")) == 5) { "Expected 5 mind runes." }
            check(fineXp() == 27.5) { "Expected 27.5 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`wrath runes come from pure essence at level 95`() =
        runGameTest(Runecrafting::class) {
            val altar = altar("wrath_altar")
            prepare(95, "blankrune_high" to 6)

            player.opLoc1(altar)
            advance()

            check(player.count(obj("wrathrune")) == 6) { "Expected 6 wrath runes." }
            check(fineXp() == 48.0) { "Expected 48 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a binding necklace makes every combination rune`() =
        runGameTest(Runecrafting::class, LocUOpScript::class) {
            val altar = altar("air_altar")
            prepare(6, "blankrune_high" to 8, "waterrune" to 8, "water_talisman" to 1)
            player.worn[Wearpos.Front.slot] = InvObj(obj("magic_emerald_necklace"))

            useOnLoc(altar, "waterrune")
            advance()

            check(player.count(obj("mistrune")) == 8) { "Expected 8 mist runes." }
            check(player.count(obj("water_talisman")) == 0) { "The talisman is used up." }
            check(player.count(obj("blankrune_high")) == 0) { "The essence is used up." }
            check(fineXp() == 64.0) { "Expected 64 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`combination runes fail about half the time without a necklace`() =
        runGameTest(Runecrafting::class, LocUOpScript::class) {
            val altar = altar("water_altar")
            prepare(6, "blankrune_high" to 20, "airrune" to 20, "air_talisman" to 1)

            useOnLoc(altar, "air_talisman")
            advance()

            val made = player.count(obj("mistrune"))
            check(made in 1..19) { "Expected some but not all of 20 to succeed, got $made." }
            check(player.count(obj("air_talisman")) == 0) { "The talisman is used up." }
            check(player.count(obj("airrune")) == 0) { "The runes are used up either way." }
            check(fineXp() == made * 8.5) { "Expected ${made * 8.5} experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`combining needs the other talisman`() =
        runGameTest(Runecrafting::class, LocUOpScript::class) {
            val altar = altar("air_altar")
            prepare(6, "blankrune_high" to 4, "waterrune" to 4)

            useOnLoc(altar, "waterrune")
            advanceUntilMessage(
                "You need the water talisman to combine water runes with this altar."
            )

            check(player.count(obj("waterrune")) == 4) { "The runes should be kept." }
        }

    @Test
    fun GameTestState.`a talisman is bound into a tiara at its altar`() =
        runGameTest(Runecrafting::class, LocUOpScript::class) {
            val altar = altar("air_altar")
            prepare(1, "tiara" to 1, "air_talisman" to 1)

            useOnLoc(altar, "air_talisman")
            advance()

            check(player.count(obj("tiara_air")) == 1) { "Expected an air tiara." }
            check(player.count(obj("tiara")) == 0) { "The plain tiara is used up." }
            check(fineXp() == 25.0) { "Expected 25 experience, was ${fineXp()}." }
        }
}
