package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.content.skills.thieving.scripts.Pickpocketing
import org.rsmod.content.skills.thieving.scripts.StallThieving
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class LeagueDodgyDealsTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.choose() {
        VarPlayerIntMapSetter.set(
            player,
            RelicVarBits.selection[LeagueDodgyDeals.TIER],
            LeagueDodgyDeals.OPTION,
        )
    }

    private fun GameTestScope.prepare(level: Int) {
        player.clearInv()
        player.stats[stats.thieving] = level
        player.statMap.setXP(stats.thieving, 0)
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

    @Test
    fun GameTestState.`without the relic a pickpocket is one npc with no help`() =
        runGameTest(Pickpocketing::class, LeagueDodgyDeals::class) {
            val target = PickpocketTargets.byKey("man")
            val type = npcTypes.values.first { target.matches(it) }
            val man = spawnNpc(coords, type)
            spawnNpc(coords.translateZ(2), type)
            player.teleport(coords.translateX(-1))
            advance(ticks = 12)
            prepare(1)
            repeat(12) { random.next = 0 }

            player.opNpc3(man)
            advanceUntilMessage("You pick the man's pocket.")

            assertEquals(1, player.count(obj("pickpocket_coin_pouch_citizen")))
        }

    @Test
    fun GameTestState.`Dodgy Deals robs the neighbours, never fails and goes on by itself`() =
        runGameTest(Pickpocketing::class, LeagueDodgyDeals::class) {
            val target = PickpocketTargets.byKey("man")
            val type = npcTypes.values.first { target.matches(it) }
            val man = spawnNpc(coords, type)
            spawnNpc(coords.translateZ(2), type)
            spawnNpc(coords.translateZ(-3), type)
            spawnNpc(coords.translateX(20), type)
            player.teleport(coords.translateX(-1))
            advance(ticks = 12)
            prepare(1)
            choose()
            // Every roll is a failing one for anyone else, but this thief cannot fail.
            repeat(60) { random.next = 99 }

            player.opNpc3(man)
            advanceUntilMessage("You pick the man's pocket.")

            // The man and the two near him; the one 20 tiles away is left alone.
            val first = player.count(obj("pickpocket_coin_pouch_citizen"))
            assertEquals(3, first)

            // It carries on without another click.
            advance(ticks = 8)
            assertTrue(player.count(obj("pickpocket_coin_pouch_citizen")) > first)
        }

    @Test
    fun GameTestState.`a Dodgy Deals pickpocket never holds the thief and walking away ends it`() =
        runGameTest(Pickpocketing::class, LeagueDodgyDeals::class) {
            val target = PickpocketTargets.byKey("man")
            val type = npcTypes.values.first { target.matches(it) }
            val man = spawnNpc(coords, type)
            player.teleport(coords.translateX(-1))
            advance(ticks = 12)
            prepare(1)
            choose()

            player.opNpc3(man)
            repeat(10) {
                advance()
                check(!player.isDelayed) { "Expected the thief to be free to walk away." }
            }
            val robbed = player.count(obj("pickpocket_coin_pouch_citizen"))
            assertTrue(robbed > 0)

            val away = player.coords.translateZ(-3)
            player.moveGameClick(away)
            advance(ticks = 8)
            assertEquals(away, player.coords)

            val after = player.count(obj("pickpocket_coin_pouch_citizen"))
            advance(ticks = 8)
            assertEquals(after, player.count(obj("pickpocket_coin_pouch_citizen")))
        }

    @Test
    fun GameTestState.`a Dodgy Deals stall theft never holds the thief and walking away ends it`() =
        runGameTest(StallThieving::class, LeagueDodgyDeals::class) {
            val type = locTypes.values.first { it.internalName == "silkthiefstall" }
            val stall = placeMapLoc(coords, type)
            player.teleport(stall.coords.translateX(-1))
            advance(ticks = 12)
            prepare(20)
            choose()

            player.opLoc2(stall)
            repeat(10) {
                advance()
                check(!player.isDelayed) { "Expected the thief to be free to walk away." }
            }
            assertTrue(player.count(obj("silk")) > 0)

            val away = player.coords.translateZ(-3)
            player.moveGameClick(away)
            advance(ticks = 8)
            assertEquals(away, player.coords)

            val after = player.count(obj("silk"))
            advance(ticks = 8)
            assertEquals(after, player.count(obj("silk")))
        }

    @Test
    fun GameTestState.`Dodgy Deals lets stalls refill themselves while they are robbed again`() =
        runGameTest(StallThieving::class, LeagueDodgyDeals::class) {
            val type = locTypes.values.first { it.internalName == "silkthiefstall" }
            val stall = placeMapLoc(coords, type)
            player.teleport(stall.coords.translateX(-1))
            prepare(20)
            choose()

            player.opLoc2(stall)
            advance(ticks = 12)

            val silk = player.count(obj("silk"))
            assertTrue(silk >= 3, "Expected several silks, got $silk.")
            assertExists(coords, type)
        }
}
