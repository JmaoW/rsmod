package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

internal object ExtrasTestNpcs : NpcReferences() {
    val imp = find("godwars_ancient_imp")
}

/** The God Wars altars, the ecumenical key, and the chamber doors' use of the key. */
class DungeonGodExtrasTest {
    class Deps
    @Inject
    constructor(
        val altars: GodWarsAltarScript,
        val keys: GodWarsKeyScript,
        val tasks: LeagueTaskTracker,
        val registry: NpcRegistry,
    )

    private fun GameTestState.runExtrasTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            GodWarsAltarScript::class,
            GodWarsKeyScript::class,
            DungeonGateScript::class,
        ) { deps ->
            player.observerUUID = player.uuid
            deps.altars.reset(player)
            for (slot in listOf(Wearpos.Hat, Wearpos.Legs, Wearpos.Torso)) {
                player.worn[slot.slot] = null
            }
            player.inv.objs.fill(null)
            // A player counts as in combat for a while after the map clock's start (their
            // last-combat tick is 0), so let that pass.
            advance(ticks = 40)
            body(deps)
        }

    private fun GameTestScope.loc(
        coords: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): BoundLocInfo {
        allocZoneCollision(coords)
        return placeMapLoc(coords, type, shape, angle)
    }

    /** The altars' rotations on the real map (an altar can only be reached from some sides). */
    private fun angleOf(type: LocType): LocAngle =
        if (type == GodWarsAltarLocs.armadyl) LocAngle.South else LocAngle.West

    private fun GameTestScope.prayer(base: Int, current: Int) {
        player.setBaseLevel(stats.prayer, base)
        player.setCurrentLevel(stats.prayer, current)
    }

    private fun GameTestScope.currentPrayer(): Int = player.stats[stats.prayer]

    private fun GameTestScope.wear(pos: Wearpos, internal: String) {
        val type = objTypes.values.first { it.internalName == internal }
        player.worn[pos.slot] = InvObj(type)
    }

    // The Armadyl altar stands at 2821,5301 and the Bandos one at 2869,5370.
    private val armadylAltar = CoordGrid(2821, 5301, 2)
    private val bandosAltar = CoordGrid(2869, 5370, 2)

    private fun GameTestScope.pray(altar: CoordGrid, type: LocType, stand: CoordGrid) {
        val loc = loc(altar, type, angle = angleOf(type))
        allocZoneCollision(stand)
        player.placeAt(stand)
        player.opLoc1(loc)
        advance(ticks = 10)
    }

    @Test
    fun GameTestState.`praying restores the prayer points`() = runExtrasTest { _ ->
        prayer(base = 50, current = 5)
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        assertEquals(50, currentPrayer())
    }

    @Test
    fun GameTestState.`each item of the altar's god lifts the points one above the maximum`() =
        runExtrasTest { _ ->
            prayer(base = 50, current = 5)
            wear(Wearpos.Hat, "armadyl_helmet")
            wear(Wearpos.Legs, "armadyl_skirt")
            pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
            assertEquals(52, currentPrayer())
        }

    @Test
    fun GameTestState.`another god's items do not boost the altar`() = runExtrasTest { _ ->
        prayer(base = 50, current = 5)
        wear(Wearpos.Hat, "armadyl_helmet")
        wear(Wearpos.Legs, "armadyl_skirt")
        pray(bandosAltar, GodWarsAltarLocs.bandos, bandosAltar.translate(0, -3))
        assertEquals(50, currentPrayer())
    }

    @Test
    fun GameTestState.`an altar works once every ten minutes`() = runExtrasTest { _ ->
        prayer(base = 50, current = 5)
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        assertEquals(50, currentPrayer())

        prayer(base = 50, current = 7)
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        assertEquals(7, currentPrayer(), "still waiting")

        advance(ticks = GodWarsAltarScript.COOLDOWN_TICKS)
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        assertEquals(50, currentPrayer(), "ten minutes later")
    }

    @Test
    fun GameTestState.`the altars share one wait`() = runExtrasTest { _ ->
        prayer(base = 50, current = 5)
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        prayer(base = 50, current = 9)
        pray(bandosAltar, GodWarsAltarLocs.bandos, bandosAltar.translate(0, -3))
        assertEquals(9, currentPrayer())
    }

    @Test
    fun GameTestState.`an altar will not pray for a player in combat`() = runExtrasTest { _ ->
        prayer(base = 50, current = 5)
        VarPlayerIntMapSetter.set(player, varps.lastcombat, currentMapClockNow())
        pray(armadylAltar, GodWarsAltarLocs.armadyl, armadylAltar.translate(3, 1))
        assertEquals(5, currentPrayer())
    }

    private fun GameTestScope.currentMapClockNow(): Int = player.currentMapClock

    @Test
    fun GameTestState.`the teleport option takes a player outside the door even in combat`() =
        runExtrasTest { _ ->
            val loc = loc(bandosAltar, GodWarsAltarLocs.bandos, angle = LocAngle.West)
            val outside = DungeonGates.bandosChamber.a
            allocZoneCollision(outside)
            val stand = bandosAltar.translate(0, -3)
            allocZoneCollision(stand)
            player.placeAt(stand)
            VarPlayerIntMapSetter.set(player, varps.lastcombat, currentMapClockNow())
            player.opLoc2(loc)
            advance(ticks = 10)
            assertEquals(outside, player.coords)
        }

    @Test
    fun GameTestState.`the wait is told in minutes and seconds`() = runExtrasTest { deps ->
        assertEquals("10 minutes", deps.altars.waitText(1000))
        assertEquals("1 minute", deps.altars.waitText(100))
        assertEquals("30 seconds", deps.altars.waitText(50))
        assertEquals("1 second", deps.altars.waitText(1))
    }

    // The ecumenical key.

    private val inside = CoordGrid(2882, 5310, 2)

    private fun GameTestScope.key() = objTypes.values.first { it.internalName == "ecumenical_key" }

    @Test
    fun GameTestState.`a follower drops a key about one time in sixty`() = runExtrasTest { deps ->
        allocZoneCollision(inside)
        player.placeAt(inside)
        val imp = spawnNpc(inside.translateX(3), npcTypes[ExtrasTestNpcs.imp])
        val drops =
            (1..6000).count { deps.keys.roll(player, imp).also { player.inv.objs.fill(null) } }
        deps.registry.del(imp)
        assertTrue(drops in 60..150, "about 100 of 6000: $drops")
    }

    @Test
    fun GameTestState.`a key comes with the obtain an ecumenical key task`() =
        runExtrasTest { deps ->
            allocZoneCollision(inside)
            player.placeAt(inside)
            val imp = spawnNpc(inside.translateX(3), npcTypes[ExtrasTestNpcs.imp])
            assertFalse(deps.tasks.isComplete(player, GodWarsKeyScript.TASK_KEY))
            repeat(3000) {
                if (!deps.tasks.isComplete(player, GodWarsKeyScript.TASK_KEY)) {
                    deps.keys.roll(player, imp)
                }
            }
            deps.registry.del(imp)
            assertTrue(deps.tasks.isComplete(player, GodWarsKeyScript.TASK_KEY))
        }

    @Test
    fun GameTestState.`nobody drops a key to a player who carries three`() = runExtrasTest { deps ->
        allocZoneCollision(inside)
        player.placeAt(inside)
        player.inv.objs[0] = InvObj(key(), 3)
        assertEquals(3, deps.keys.carried(player))
        val imp = spawnNpc(inside.translateX(3), npcTypes[ExtrasTestNpcs.imp])
        assertEquals(0, (1..3000).count { deps.keys.roll(player, imp) })
        deps.registry.del(imp)
    }

    @Test
    fun GameTestState.`a monster outside the dungeon never drops one`() = runExtrasTest { deps ->
        val outside = CoordGrid(2200, 2815, 0)
        allocZoneCollision(outside)
        player.placeAt(outside)
        val imp = spawnNpc(outside.translateX(2), npcTypes[ExtrasTestNpcs.imp])
        assertEquals(0, (1..3000).count { deps.keys.roll(player, imp) })
        deps.registry.del(imp)
    }

    // The chamber doors.

    private fun GameTestScope.door(gate: DungeonGate): BoundLocInfo {
        val click = gate.clicks.first()
        val shape = LocShape.CentrepieceStraight
        val angle =
            when (gate) {
                DungeonGates.armadylChamber -> LocAngle.South
                DungeonGates.bandosChamber -> LocAngle.West
                DungeonGates.saradominChamber -> LocAngle.East
                else -> LocAngle.North
            }
        allocZoneCollision(gate.b)
        return loc(checkNotNull(click.coords), click.type, shape, angle)
    }

    private fun GameTestScope.go(door: BoundLocInfo, stand: CoordGrid): CoordGrid {
        allocZoneCollision(stand)
        player.placeAt(stand)
        player.opLoc1(door)
        advance(ticks = 8)
        return player.coords
    }

    @Test
    fun GameTestState
        .`an ecumenical key opens a chamber instead of the kill count and is used up`() =
        runExtrasTest { _ ->
            val gate = DungeonGates.bandosChamber
            val door = door(gate)
            player.inv.objs[0] = InvObj(key())
            assertEquals(gate.b, go(door, gate.a))
            assertEquals(null, player.inv.objs[0], "the key is gone")

            // And with no key and no kills the door stays shut.
            val shut = go(door, gate.a)
            assertTrue(shut.chebyshevDistance(gate.a) <= 1, "stays outside: $shut")
        }

    @Test
    fun GameTestState.`the kill count is checked before the key and nothing is spent`() =
        runExtrasTest { _ ->
            val gate = DungeonGates.zamorakChamber
            val door = door(gate)
            player.inv.objs[0] = InvObj(key())
            VarPlayerIntMapSetter.set(player, GodWarsVarbits.zamorak_kills, 45)
            assertEquals(gate.b, go(door, gate.a))
            assertEquals(45, player.vars[GodWarsVarbits.zamorak_kills], "kept")
            assertEquals(1, player.inv.objs[0]?.count, "the key is kept")
        }

    @Test
    fun GameTestState.`one key opens one door only`() = runExtrasTest { _ ->
        val gate = DungeonGates.saradominChamber
        val door = door(gate)
        player.inv.objs[0] = InvObj(key(), 2)
        // Both keys are in one stack here; the door takes just one of them.
        assertEquals(gate.b, go(door, gate.a))
        assertEquals(1, player.inv.objs[0]?.count)
    }
}
