package org.rsmod.content.skills.prayer.scripts

import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class AltarsTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    /** Places the altar and stands the player on the tile in front of it. */
    private fun GameTestScope.placeAltar(internal: String): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val altar = placeMapLoc(coords, type)
        player.teleport(coords.translateZ(-1))
        player.clearInv()
        player.xpRate = 1.0
        return altar
    }

    private fun GameTestScope.prayerXp() = player.statMap.getXP(stats.prayer)

    private fun GameTestScope.useBones(altar: BoundLocInfo, bones: String) {
        val slot = player.inv.indexOfFirst { it?.id == obj(bones).id }
        player.opLocT(altar, obj(bones), slot)
    }

    @Test
    fun GameTestState.`praying at an altar restores prayer points`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.stats[stats.prayer] = 50
            player.stats.setCurrentLevel(stats.prayer, 10)

            player.opLoc1(altar)
            advanceUntil({ player.prayerLvl == 50 }, timeoutTicks = 20)

            check(player.prayerLvl == 50) { "Expected full prayer, got ${player.prayerLvl}." }
            assertMessageSent("You recharge your Prayer points.")
        }

    @Test
    fun GameTestState.`praying with full prayer points does nothing`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.stats[stats.prayer] = 50

            player.opLoc1(altar)
            advance(ticks = 1)

            assertMessageSent("You already have full Prayer points.")
        }

    @Test
    fun GameTestState.`offering bones uses each bone and gives three and a half times the xp`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.inv[0] = InvObj(obj("dragon_bones"))
            player.inv[1] = InvObj(obj("dragon_bones"))
            player.inv[2] = InvObj(obj("dragon_bones"))

            useBones(altar, "dragon_bones")
            advanceUntil({ player.count(obj("dragon_bones")) == 0 }, timeoutTicks = 60)

            check(player.count(obj("dragon_bones")) == 0) { "Expected every bone to be offered." }
            // 72 * 3.5 = 252 for each of the three bones.
            check(prayerXp() == 756) { "Expected 756 xp, got ${prayerXp()}." }
        }

    @Test
    fun GameTestState.`bones are offered three ticks apart`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            repeat(4) { player.inv[it] = InvObj(obj("bones")) }

            useBones(altar, "bones")
            advanceUntil({ player.count(obj("bones")) < 4 }, timeoutTicks = 20)
            val first = player.count(obj("bones"))
            advance(ticks = 2)
            check(player.count(obj("bones")) == first) { "Expected no bone to be offered yet." }
            advance(ticks = 1)
            check(player.count(obj("bones")) == first - 1) { "Expected the next bone to go." }
        }

    @Test
    fun GameTestState.`the chaos altar can save a bone`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("chaosaltar")
            player.inv[0] = InvObj(obj("dragon_bones"))

            random.nextBoolean = true
            useBones(altar, "dragon_bones")
            advanceUntil({ prayerXp() > 0 }, timeoutTicks = 20)

            check(prayerXp() == 252) { "Expected 252 xp, got ${prayerXp()}." }
            check(player.count(obj("dragon_bones")) == 1) { "Expected the bone to be saved." }
            assertMessageSent(
                "The Dark Lord spares your sacrifice but still rewards you for your efforts."
            )
        }

    @Test
    fun GameTestState.`the chaos altar uses the bone when it does not save it`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("chaosaltar")
            player.inv[0] = InvObj(obj("dragon_bones"))

            random.nextBoolean = false
            useBones(altar, "dragon_bones")
            advanceUntil({ player.count(obj("dragon_bones")) == 0 }, timeoutTicks = 20)

            check(player.count(obj("dragon_bones")) == 0) { "Expected the bone to be used." }
            check(prayerXp() == 252) { "Expected 252 xp, got ${prayerXp()}." }
            assertMessageSent("The gods are pleased with your offering.")
        }

    @Test
    fun GameTestState.`a normal altar never saves a bone`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.inv[0] = InvObj(obj("dragon_bones"))

            random.nextBoolean = true
            useBones(altar, "dragon_bones")
            advanceUntil({ player.count(obj("dragon_bones")) == 0 }, timeoutTicks = 20)

            check(player.count(obj("dragon_bones")) == 0) { "Expected the bone to be used." }
        }

    @Test
    fun GameTestState.`using something that is not a bone does nothing`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.inv[0] = InvObj(obj("logs"))

            useBones(altar, "logs")
            advance(ticks = 1)

            assertMessageSent("Nothing interesting happens.")
            check(player.count(obj("logs")) == 1) { "Logs should be untouched." }
            check(prayerXp() == 0) { "Expected no xp." }
        }

    @Test
    fun GameTestState.`walking away from an altar stops praying and never holds the player`() =
        runGameTest(Altars::class, LocUOpScript::class) {
            val altar = placeAltar("altar")
            player.stats[stats.prayer] = 50
            player.stats.setCurrentLevel(stats.prayer, 10)

            player.opLoc1(altar)
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while praying." }

            player.ifClose(eventBus)
            advance(ticks = 10)

            check(player.prayerLvl == 10) { "Expected the prayer points not to come back." }
        }
}
