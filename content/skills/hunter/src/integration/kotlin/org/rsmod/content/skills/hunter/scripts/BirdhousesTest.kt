package org.rsmod.content.skills.hunter.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.configs.HunterVarps
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class BirdhousesTest {
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
        VarPlayerIntMapSetter.set(player, HunterVarps.birdhouse_a, 0)
        advance(ticks = 12)
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 12) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun minutes(): Int =
        (System.currentTimeMillis() / 60_000L).toInt() - Birdhouses.EPOCH_MINUTES

    @Test
    fun GameTestState.`a birdhouse is built, filled with seeds and emptied of its birds`() =
        runGameTest(HunterTraps::class, Birdhouses::class) {
            prepare(5, "birdhouse_normal" to 1, "guam_seed" to 10)
            val spotAt = coords.translate(3, 0)
            val spot = placeMapLoc(spotAt, loc("birdhouse_1"))
            player.teleport(spotAt.translate(-1, 0))
            advance(ticks = 2)

            player.opLoc1(spot)
            advanceUntilMessage(
                "You place the birdhouse. Fill it with seeds to start catching birds."
            )
            check(player.vars[HunterVarps.birdhouse_a] == Birdhouses.value(0, Birdhouses.BUILT)) {
                "Expected a built house."
            }
            check(player.count(obj("birdhouse_normal")) == 0) { "Expected the house used." }

            player.opLoc2(spot)
            advanceUntilMessage(
                "The birdhouse is ready. Come back in ${Birdhouses.GROWTH_MINUTES} minutes."
            )
            check(player.vars[HunterVarps.birdhouse_a] == Birdhouses.value(0, Birdhouses.FILLING)) {
                "Expected a filling house."
            }
            check(player.count(obj("guam_seed")) == 0) { "Expected the seeds used." }

            // Time passes: the house was filled long enough ago.
            VarPlayerIntMapSetter.set(
                player,
                HunterVarps.birdhouse_a_filled,
                minutes() - Birdhouses.GROWTH_MINUTES - 1,
            )
            advanceUntilMessage("Birds have settled in your birdhouse.")
            check(player.vars[HunterVarps.birdhouse_a] == Birdhouses.value(0, Birdhouses.BIRDS)) {
                "Expected birds."
            }

            random.next = 99
            random.next = 99
            player.opLoc3(spot)
            advance(ticks = 6)
            check(player.count(obj("spit_raw_bird_meat")) == Birdhouses.MEAT) {
                "Expected bird meat."
            }
            check(player.count(obj("poh_clockwork_mechanism")) == 1) {
                "Expected the clockwork back."
            }
            val xp = player.statMap.getFineXP(stats.hunter) / 10.0
            check(xp == 112.0) { "Expected 112 experience, got $xp." }
            check(player.vars[HunterVarps.birdhouse_a] == 0) { "Expected an empty spot." }
        }

    @Test
    fun GameTestState.`a birdhouse needs the hunter level`() =
        runGameTest(HunterTraps::class, Birdhouses::class) {
            prepare(1, "birdhouse_normal" to 1)
            val spotAt = coords.translate(12, 0)
            val spot = placeMapLoc(spotAt, loc("birdhouse_2"))
            player.teleport(spotAt.translate(-1, 0))
            advance(ticks = 2)
            player.opLoc1(spot)
            advanceUntilMessage(
                "Bring a birdhouse that suits your Hunter level, which you can craft from logs and a clockwork."
            )
        }
}
