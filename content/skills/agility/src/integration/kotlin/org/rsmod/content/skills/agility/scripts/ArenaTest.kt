package org.rsmod.content.skills.agility.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.agility.configs.ArenaClick
import org.rsmod.content.skills.agility.configs.ArenaEntrance
import org.rsmod.content.skills.agility.configs.ArenaKind
import org.rsmod.content.skills.agility.configs.ArenaLayout
import org.rsmod.content.skills.agility.configs.ArenaNpcs
import org.rsmod.content.skills.agility.configs.ArenaObjs
import org.rsmod.content.skills.agility.configs.ArenaStats
import org.rsmod.content.skills.agility.configs.ArenaTimers
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Remembers the streak of each tag. */
class TagListener : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<Arena.Tagged> { streaks += streak }
    }

    companion object {
        val streaks = ArrayList<Int>()
    }
}

class ArenaTest {
    private fun CoordGrid.free(collision: org.rsmod.routefinder.collision.CollisionFlagMap) =
        collision[x, z, level] and (0x200000 or 0x100 or 0x40000) == 0

    private fun Tile.coords() = CoordGrid(x, z, level)

    private fun GameTestScope.at(tile: Tile) {
        player.teleport(tile.coords())
        advance(3)
    }

    /** The client's messages are kept for one tick only, so look for [text] as the ticks pass. */
    private fun GameTestScope.expectMessage(text: String, ticks: Int = 8) {
        repeat(ticks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.startTimer() {
        player.softTimer(ArenaTimers.tick, 1)
    }

    private fun GameTestScope.standsOn(tile: Tile) = player.coords == tile.coords()

    private fun GameTestScope.agilityXp(): Double = player.statMap.getFineXP(stats.agility) / 10.0

    private fun shape(loc: String): Int =
        when {
            loc.contains("ledge") ||
                loc.contains("logbalance") ||
                loc.contains("ropebalance") ||
                loc.contains("plank") -> 22
            loc.contains("handholds") -> 5
            else -> 10
        }

    /** The loc of [locs] that is nearest the player, which is what a player clicks. */
    private fun GameTestScope.nearest(locs: List<BoundLocInfo>): BoundLocInfo =
        locs.minByOrNull { player.coords.chebyshevDistance(it.coords) }!!

    private fun GameTestScope.locAt(
        coords: CoordGrid,
        type: org.rsmod.game.type.loc.UnpackedLocType,
    ) = findLocs(coords).firstOrNull { it.coords == coords && it.id == type.id }

    private fun GameTestScope.place(click: ArenaClick): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == click.loc }
        return placeMapLoc(
            CoordGrid(click.x, click.z, 3),
            type,
            LocShape[shape(click.loc)],
            LocAngle[0],
        )
    }

    @Test
    fun GameTestState.`the layout stands on walkable tiles`() = runGameTest {
        for (edge in ArenaLayout.edges) {
            for (lane in edge.lanes) {
                for (tile in listOf(lane.from, lane.to)) {
                    check(tile.coords().free(collision)) {
                        "${edge.kind} ${edge.a}-${edge.b}: $tile"
                    }
                }
            }
            edge.trap?.forEach {
                check(it.coords().free(collision)) { "${edge.kind} trap tile $it" }
            }
            edge.blade?.forEach { check(it.coords().free(collision)) { "blade tile $it" } }
        }
        for (pit in ArenaLayout.pits) {
            val up = Tile(pit.rope.x, pit.rope.z, 3)
            check(up.coords().free(collision)) { "Nowhere to arrive at the top of rope $up." }
            check(pit.landing.isNotEmpty()) { "No landing in pit ${pit.pillar}." }
            for (tile in pit.landing) {
                check(tile.coords().free(collision)) { "Pit ${pit.pillar} landing $tile." }
            }
        }
        check(ArenaLayout.entry.coords().free(collision)) { "The entry tile is blocked." }
        check(ArenaEntrance.exit.free(collision)) { "The exit tile is blocked." }
        check(ArenaEntrance.clerk.free(collision)) { "The clerk's tile is blocked." }
        check(ArenaEntrance.jackie.free(collision)) { "Jackie's tile is blocked." }
    }

    @Test
    fun GameTestState.`every obstacle crosses both ways for its experience`() =
        runGameTest(ArenaObstacles::class) {
            player.stats[stats.agility] = 99
            val obstacles =
                setOf(
                    ArenaKind.Ledge,
                    ArenaKind.Log,
                    ArenaKind.Rope,
                    ArenaKind.Bars,
                    ArenaKind.Hands,
                    ArenaKind.Plank,
                    ArenaKind.Swing,
                    ArenaKind.Pillar,
                    ArenaKind.LowWall,
                )
            for (edge in ArenaLayout.edges.filter { it.kind in obstacles }) {
                val stat = ArenaStats.of(edge.kind)
                val locs = edge.clicks.map { place(it) }
                for ((index, lane) in edge.lanes.withIndex()) {
                    val candidates =
                        if (edge.kind == ArenaKind.Plank) {
                            locs.filter { it.coords.z == lane.from.z }
                        } else {
                            locs
                        }
                    at(lane.from)
                    player.statMap.setXP(stats.agility, 0)
                    player.opLoc1(nearest(candidates))
                    advance(70)
                    check(standsOn(lane.to)) {
                        "${edge.kind} ${edge.a}-${edge.b} lane $index forwards ended on " +
                            "${player.coords}, not ${lane.to}."
                    }
                    check(Math.abs(agilityXp() - stat.xp) < 0.11) {
                        "${edge.kind} ${edge.a}-${edge.b} gave ${agilityXp()}, not ${stat.xp}."
                    }

                    player.opLoc1(nearest(candidates))
                    advance(70)
                    check(standsOn(lane.from)) {
                        "${edge.kind} ${edge.a}-${edge.b} lane $index back ended on " +
                            "${player.coords}, not ${lane.from}."
                    }
                }
            }
        }

    @Test
    fun GameTestState.`hand holds need level 20`() =
        runGameTest(ArenaObstacles::class) {
            val edge = ArenaLayout.edges.first { it.kind == ArenaKind.Hands }
            val loc = place(edge.clicks.first())
            val lane = edge.lanes.first()
            player.stats[stats.agility] = 19
            at(lane.from)

            player.opLoc1(loc)
            expectMessage("You need an Agility level of at least 20 to attempt this.")
            check(standsOn(lane.from)) { "The player should not have moved." }
        }

    @Test
    fun GameTestState.`a fall drops into the pit, hurts, and a rope leads back up`() =
        runGameTest(ArenaObstacles::class, Arena::class, PlayerHitScript::class) {
            val edge = ArenaLayout.edges.first { it.kind == ArenaKind.Ledge }
            val loc = place(edge.clicks.first())
            val lane = edge.lanes.first()
            player.stats[stats.agility] = 1
            player.stats[stats.hitpoints] = 99
            at(lane.from)
            random.next = 255 // Fails the roll.
            random.next = 0 // The first landing tile.

            player.opLoc1(loc)
            var lowest = 99
            repeat(30) {
                advance()
                lowest = minOf(lowest, player.stats[stats.hitpoints])
            }
            val pit = ArenaLayout.pits.first { it.pillar == edge.a }
            check(player.coords.level == 0) {
                "Expected to be on the ground, was ${player.coords}."
            }
            check(
                Math.abs(player.coords.x - pit.rope.x) <= 3 &&
                    Math.abs(player.coords.z - pit.rope.z) <= 3
            ) {
                "Expected to be in the pit by ${pit.rope}, was ${player.coords}."
            }
            check(lowest == 99 - (99 * 5 / 100 + 2)) { "Expected 6 damage, lowest was $lowest." }

            val rope = ArenaLayout.pits.first { it.pillar == edge.a }.rope
            val ropeType = locTypes.values.first { it.internalName == "agilityarena_climbingrope" }
            val ropeLoc = placeMapLoc(CoordGrid(rope.x, rope.z, 0), ropeType, LocShape[10])
            player.opLoc1(ropeLoc)
            advance(10)
            check(player.coords == CoordGrid(rope.x, rope.z, 3)) {
                "Expected to climb to the platform, was ${player.coords}."
            }
        }

    @Test
    fun GameTestState.`only the sound plank can be walked`() =
        runGameTest(ArenaObstacles::class, Arena::class) {
            val edge = ArenaLayout.edges.first { it.kind == ArenaKind.Plank }
            val locs = edge.clicks.map { place(it) }
            val broken = locTypes.values.first { it.internalName == "agilityarena_plank_broke1" }
            val sound = locTypes.values.first { it.internalName == "agilityarena_plank_middle" }
            player.stats[stats.agility] = 50
            player.stats[stats.hitpoints] = 99
            startTimer()
            advance(3)

            // One lane has sound middles, the other two broken ones.
            val planks = checkNotNull(edge.planks)
            val soundLanes =
                planks.indices.filter { lane ->
                    planks[lane].all { locAt(CoordGrid(it.x, it.z, 3), sound) != null }
                }
            check(soundLanes.size == 1) { "Expected one sound lane, got $soundLanes." }
            for (lane in planks.indices - soundLanes) {
                check(planks[lane].all { locAt(CoordGrid(it.x, it.z, 3), broken) != null }) {
                    "Lane $lane should be drawn broken."
                }
            }

            val good = edge.lanes[soundLanes.single()]
            at(good.from)
            player.opLoc1(locs.first { it.coords.z == good.from.z })
            advance(70)
            check(standsOn(good.to)) { "The sound plank ended on ${player.coords}." }

            val bad = edge.lanes[(soundLanes.single() + 1) % 3]
            at(bad.from)
            random.next = 0
            player.opLoc1(locs.first { it.coords.z == bad.from.z })
            advance(70)
            check(player.coords.level == 0) { "A broken plank should drop the player." }
        }

    private fun GameTestScope.placeDispensers() {
        for (dispenser in ArenaLayout.dispensers) {
            val type = locTypes.values.first { it.internalName == dispenser.loc }
            placeMapLoc(CoordGrid(dispenser.x, dispenser.z, 3), type, LocShape[10], LocAngle[0])
        }
    }

    /** Stands beside dispenser [index] and tags it. */
    private fun GameTestScope.tag(index: Int, expect: String? = null) {
        val dispenser = ArenaLayout.dispensers[index]
        val type = locTypes.values.first { it.internalName == dispenser.loc }
        val coords = CoordGrid(dispenser.x, dispenser.z, 3)
        val bound = BoundLocInfo(locAt(coords, type)!!, type)
        player.teleport(CoordGrid(dispenser.x, dispenser.z - 1, 3))
        advance(2)
        player.opLoc1(bound)
        if (expect != null) {
            expectMessage(expect)
        } else {
            advance(3)
        }
    }

    @Test
    fun GameTestState
        .`tagging the marked dispenser gives a ticket, a voucher and experience, and keeps a streak`() =
        runGameTest(Arena::class, TagListener::class) {
            TagListener.streaks.clear()
            placeDispensers()
            player.stats[stats.agility] = 60
            player.statMap.setXP(stats.agility, 0)
            player.clearInv()
            random.next = 7 // The first dispenser marked.
            startTimer()
            advance(3)

            tag(3, "You can only get a ticket when the flashing arrow is above the pillar.")
            check(player.count(objTypes[ArenaObjs.ticket]) == 0) { "Expected no ticket yet." }

            tag(7)
            check(player.count(objTypes[ArenaObjs.ticket]) == 1) { "Expected one ticket." }
            check(player.count(objTypes[ArenaObjs.voucher]) == 1) { "Expected one voucher." }
            check(Math.abs(agilityXp() - 180.0) < 0.11) { "Expected 180 experience." }
            check(TagListener.streaks == listOf(1)) {
                "Expected a first tag: ${TagListener.streaks}."
            }

            // The same dispenser cannot be tagged twice.
            tag(7, "You can only get one ticket at a time, wait till the arrow moves again.")
            check(player.count(objTypes[ArenaObjs.ticket]) == 1) { "Tagged twice." }

            // The next minute marks another one, and tagging it keeps the streak.
            random.next = 9
            advance(Arena.LIT_TICKS)
            tag(9)
            check(TagListener.streaks == listOf(1, 2)) {
                "Expected a streak: ${TagListener.streaks}."
            }

            // Missing a whole minute breaks it.
            random.next = 11
            random.next = 13
            advance(Arena.LIT_TICKS * 2)
            tag(13)
            check(TagListener.streaks == listOf(1, 2, 1)) {
                "Expected a reset: ${TagListener.streaks}."
            }
            check(player.count(objTypes[ArenaObjs.ticket]) == 3) { "Expected three tickets." }
        }

    @Test
    fun GameTestState.`the ladder asks for the fee, the exit ladder and ropes lead out and up`() =
        runGameTest(Arena::class) {
            val down = locTypes.values.first { it.internalName == "agilityarena_ladderdown" }
            val up = locTypes.values.first { it.internalName == "agilityarena_ladderup" }
            val ladder = placeMapLoc(ArenaEntrance.ladder, down, LocShape[10], LocAngle[1])
            val exit = placeMapLoc(ArenaLayout.ladder.coords(), up, LocShape[10], LocAngle[0])
            player.clearInv()
            player.teleport(ArenaEntrance.exit)
            advance(3)

            player.opLoc1(ladder)
            expectMessage("You need to pay Cap'n Izzy No-Beard before you go down there.")
            check(player.coords == ArenaEntrance.exit) { "Expected to stay put: ${player.coords}." }

            player.teleport(ArenaLayout.entry.coords())
            advance(3)
            player.opLoc1(exit)
            advance(8)
            check(player.coords == ArenaEntrance.exit) { "Expected to be out: ${player.coords}." }
        }

    @Test
    fun GameTestState.`paying Cap'n Izzy opens the way down the ladder`() =
        runGameTest(Arena::class) {
            val down = locTypes.values.first { it.internalName == "agilityarena_ladderdown" }
            val ladder = placeMapLoc(ArenaEntrance.ladder, down, LocShape[10], LocAngle[1])
            val clerk = spawnNpc(ArenaEntrance.clerk, npcTypes[ArenaNpcs.clerk])
            player.clearInv()
            player.invAdd(player.inv, objs.coins, 250)
            player.teleport(ArenaEntrance.exit)
            advance(3)

            player.opNpc1(clerk)
            advance(3)
            player.resumePauseButton(components.chat_left_pbutton) // "Welcome to ..."
            advance(2)
            player.resumePauseButton(components.chatmenu_pbutton, 1) // Pay.
            advance(2)
            player.resumePauseButton(components.chat_right_pbutton) // "I'd like to go in."
            advance(2)
            check(player.count(objTypes[objs.coins]) == 250 - ArenaEntrance.FEE) {
                "Expected the fee to be taken, have ${player.count(objTypes[objs.coins])}."
            }

            player.opLoc1(ladder)
            advance(10)
            check(player.coords == ArenaLayout.entry.coords()) {
                "Expected to be in the arena, was ${player.coords}."
            }

            // The fee buys one way in.
            player.teleport(ArenaEntrance.exit)
            advance(3)
            player.opLoc1(ladder)
            expectMessage("You need to pay Cap'n Izzy No-Beard before you go down there.")
        }

    private fun GameTestScope.trapOf(kind: ArenaKind) =
        ArenaLayout.edges.first { it.kind == kind && it.trap != null }

    @Test
    fun GameTestState.`the pads, spikes, blades and darts are crossed for their experience`() =
        runGameTest(Arena::class, ArenaTraps::class) {
            player.stats[stats.agility] = 99
            startTimer()
            advance(3)
            for (kind in listOf(ArenaKind.Pads, ArenaKind.Spikes, ArenaKind.Saw, ArenaKind.Darts)) {
                val edge = trapOf(kind)
                val (first, second) = checkNotNull(edge.trap)
                val stat = ArenaStats.of(kind)
                // Away first, so that stepping up is a move.
                at(edge.lanes.first().from)
                player.statMap.setXP(stats.agility, 0)
                player.teleport(first.coords())
                advance(12)
                check(standsOn(second)) { "$kind ended on ${player.coords}, not $second." }
                check(Math.abs(agilityXp() - stat.xp) < 0.11) {
                    "$kind gave ${agilityXp()}, not ${stat.xp}."
                }
            }
        }

    @Test
    fun GameTestState.`the pads give experience twice and then wait`() =
        runGameTest(Arena::class, ArenaTraps::class) {
            player.stats[stats.agility] = 99
            val edge = trapOf(ArenaKind.Pads)
            val (first, second) = checkNotNull(edge.trap)
            startTimer()
            advance(3)
            player.statMap.setXP(stats.agility, 0)
            val gained = ArrayList<Double>()
            repeat(3) {
                val before = agilityXp()
                player.teleport(first.coords())
                advance(5)
                gained += agilityXp() - before
                player.teleport(edge.lanes.first().from.coords())
                advance(1)
            }
            check(gained[0] > 25.9 && gained[1] > 25.9) { "Expected two payments: $gained." }
            check(gained[2] < 0.1) { "Expected the third to pay nothing: $gained." }
            check(second.coords() != first.coords())
        }

    @Test
    fun GameTestState.`a failed trap hurts and throws the player back, the darts drain Agility`() =
        runGameTest(Arena::class, ArenaTraps::class, PlayerHitScript::class) {
            player.stats[stats.agility] = 45
            player.stats[stats.hitpoints] = 99
            val edge = trapOf(ArenaKind.Darts)
            val (first, second) = checkNotNull(edge.trap)
            startTimer()
            advance(3)
            at(edge.lanes.first().from)
            random.next = 255 // Fails the roll.

            player.teleport(first.coords())
            var lowest = 99
            repeat(12) {
                advance()
                lowest = minOf(lowest, player.stats[stats.hitpoints])
            }
            val back = Tile(first.x + (first.x - second.x), first.z + (first.z - second.z), 3)
            check(standsOn(back)) { "Expected to be thrown back to $back, was ${player.coords}." }
            check(lowest == 99 - (99 * 5 / 100 + 2)) { "Expected 6 damage, lowest was $lowest." }
            check(player.stats[stats.agility] == 43 || player.stats[stats.agility] < 45) {
                "Expected the Agility level to drop."
            }
        }

    @Test
    fun GameTestState.`a blade that rises under the player hurts and throws them clear`() =
        runGameTest(Arena::class, ArenaTraps::class, PlayerHitScript::class) {
            player.stats[stats.hitpoints] = 99
            val edge = ArenaLayout.edges.first { it.kind == ArenaKind.Blade }
            val tiles = checkNotNull(edge.blade)
            startTimer()
            advance(3)
            player.teleport(tiles.first().coords())
            var lowest = 99
            repeat(20) {
                advance()
                lowest = minOf(lowest, player.stats[stats.hitpoints])
            }
            check(lowest < 99) { "Expected the blade to hurt." }
            check(tiles.none { standsOn(it) }) { "Expected to be thrown clear: ${player.coords}." }
        }
}
