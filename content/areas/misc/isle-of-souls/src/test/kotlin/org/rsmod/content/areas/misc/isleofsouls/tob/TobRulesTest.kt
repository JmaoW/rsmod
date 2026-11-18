package org.rsmod.content.areas.misc.isleofsouls.tob

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.map.CoordGrid

class TobRulesTest {
    // The layout
    // ------------------------------------------------------------------------------------------

    @Test
    fun `the whole theatre is copied to the region unchanged`() {
        val sw = CoordGrid(6400, 6400, 0)
        val real = CoordGrid(3162, 4444, 0)
        val inside = TobLayout.toRegion(real, sw)
        assertEquals(CoordGrid(6400 + 26, 6400 + 220, 0), inside)
        assertEquals(real, TobLayout.toReal(checkNotNull(inside), sw))
        // The Shadow Realm keeps its level.
        val realm = CoordGrid(3360, 4309, 3)
        assertEquals(realm, TobLayout.toReal(checkNotNull(TobLayout.toRegion(realm, sw)), sw))
        assertNull(TobLayout.toRegion(VerSinhaza.OUTSIDE, sw))
        assertEquals(TobLayout.ZONES * 8, TobLayout.SIZE)
    }

    @Test
    fun `every room's arrival, arena, exits and jails lie in that room`() {
        for (room in TobRoom.entries) {
            assertEquals(room, TobRoom.at(room.arrival), "$room arrival")
            room.arena?.let { arena ->
                assertTrue(
                    arena.minX >= room.bounds.minX && arena.maxX <= room.bounds.maxX,
                    "$room arena x",
                )
                assertTrue(
                    arena.minZ >= room.bounds.minZ && arena.maxZ <= room.bounds.maxZ,
                    "$room arena z",
                )
                assertEquals(room.bounds.level, arena.level, "$room arena level")
            }
            for (exit in room.exits) {
                assertEquals(room, TobRoom.at(exit), "$room exit $exit")
            }
            for (barrier in room.barriers) {
                for (tile in barrier.tiles) {
                    assertEquals(room, TobRoom.at(tile), "$room barrier $tile")
                }
            }
        }
        assertEquals(TobRoom.Bloat, TobRoom.Maiden.next)
        assertNull(TobRoom.Vault.next)
    }

    @Test
    fun `the rooms do not overlap`() {
        for (a in TobRoom.entries) {
            for (b in TobRoom.entries) {
                if (a == b || a.bounds.level != b.bounds.level) continue
                val overlap =
                    a.bounds.minX <= b.bounds.maxX &&
                        b.bounds.minX <= a.bounds.maxX &&
                        a.bounds.minZ <= b.bounds.maxZ &&
                        b.bounds.minZ <= a.bounds.maxZ
                assertFalse(overlap, "$a overlaps $b")
            }
        }
    }

    @Test
    fun `a barrier knows which side is the fight and what lies across`() {
        val maiden = TobRoom.Maiden.barriers.single()
        // The Maiden's fight is west of x 3185.
        assertFalse(maiden.onFightSide(CoordGrid(3186, 4446, 0)))
        assertTrue(maiden.onFightSide(CoordGrid(3184, 4446, 0)))
        assertEquals(CoordGrid(3184, 4446, 0), maiden.across(CoordGrid(3186, 4446, 0)))
        assertEquals(CoordGrid(3186, 4446, 0), maiden.across(CoordGrid(3184, 4446, 0)))
        // Nylocas: the fight is south of z 4255.
        val nylo = TobRoom.Nylocas.barriers.single()
        assertTrue(nylo.alongX)
        assertTrue(nylo.onFightSide(CoordGrid(3295, 4254, 0)))
        assertEquals(CoordGrid(3295, 4254, 0), nylo.across(CoordGrid(3295, 4256, 0)))
        // Every room's barrier tiles sit at its arena's edge: across them is the arena.
        for (room in TobRoom.entries) {
            for (barrier in room.barriers) {
                val tile = barrier.tiles.first()
                val outside =
                    if (barrier.alongX)
                        CoordGrid(
                            tile.x,
                            if (barrier.fightIsGreater) tile.z - 1 else tile.z + 1,
                            tile.level,
                        )
                    else
                        CoordGrid(
                            if (barrier.fightIsGreater) tile.x - 1 else tile.x + 1,
                            tile.z,
                            tile.level,
                        )
                assertTrue(room.inArena(barrier.across(outside)), "$room: across ${barrier.tiles}")
            }
        }
    }

    // Scaling and the fights' tables
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the bosses' hitpoints scale with the party as the wiki gives, and on past five`() {
        assertEquals(2625, TobScaling.hitpoints(3500, 1))
        assertEquals(2625, TobScaling.hitpoints(3500, 3))
        assertEquals(3062, TobScaling.hitpoints(3500, 4))
        assertEquals(3500, TobScaling.hitpoints(3500, 5))
        assertEquals(4375, TobScaling.hitpoints(3500, 7))
    }

    @Test
    fun `the nylocas table has thirty-one waves that parse`() {
        assertEquals(31, NylocasWaves.waves.size)
        val all = NylocasWaves.waves.flatMap { it.east + it.south + it.west }.map(NyloSpawn::parse)
        assertTrue(all.isNotEmpty())
        val first = NyloSpawn.parse("ra!")
        assertEquals(NyloStyle.Ranged, first.style)
        assertTrue(first.aggressive)
        assertFalse(first.big)
        val flicker = NyloSpawn.parse("MA!~mame")
        assertEquals(NyloStyle.Magic, flicker.style)
        assertTrue(flicker.big)
        assertEquals(listOf(NyloStyle.Magic, NyloStyle.Melee), flicker.flickers)
        // Every flickering nylocas ends on its own style (the table's colours).
        for (spawn in all.filter { it.flickers.isNotEmpty() }) {
            assertTrue(spawn.flickers.size == 2, "$spawn")
        }
    }

    @Test
    fun `the maze path climbs from the bottom row to the top, one step at a time`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val path = SotetsegRuntime.generatePath { random.nextInt(it) }
            val realm = SotetsegRuntime.REALM
            assertEquals(realm.minZ, path.first().z)
            assertEquals(realm.maxZ, path.last().z)
            for (tile in path) {
                assertTrue(tile in realm, "$tile outside the realm")
            }
            for ((a, b) in path.zipWithNext()) {
                val step = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z)
                assertEquals(1, step, "a jump from $a to $b")
            }
            assertEquals(path.size, path.toSet().size)
        }
        // A realm tile's twin is on the arena's maze.
        val twin = SotetsegRuntime.toMaze(CoordGrid(3354, 4311, 3))
        assertEquals(CoordGrid(3273, 4310, 0), twin)
    }

    @Test
    fun `the big ball's damage goes from the wiki's two to five`() {
        assertEquals(70, SotetsegRuntime.bigBallDamage(1))
        assertEquals(121, SotetsegRuntime.bigBallDamage(2))
        assertEquals(188, SotetsegRuntime.bigBallDamage(5))
    }

    // The loot
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the unique weights and the common table follow the wiki`() {
        assertEquals(19, TobUnique.entries.sumOf { it.weight })
        assertEquals(8, TobUnique.Hilt.weight)
        assertEquals(1, TobUnique.Scythe.weight)
        assertEquals(TobLootMath.COMMON_ODDS, TobLootMath.commons.sumOf { it.weight })
        assertEquals(1.0 / 9.1, TobLootMath.UNIQUE_CHANCE, 1e-12)
        // Every point of the dice lands on something.
        val seen = (0 until 19).map { point -> TobLootMath.pickUnique { point } }.toSet()
        assertEquals(TobUnique.entries.toSet(), seen)
        val commons = (0 until 30).map { point -> TobLootMath.pickCommon { point } }.toSet()
        assertEquals(TobLootMath.commons.toSet(), commons)
    }

    @Test
    fun `each death costs a fifth of a player's standing`() {
        assertEquals(1.0, TobLootMath.standing(0), 1e-9)
        assertEquals(0.6, TobLootMath.standing(2), 1e-9)
        assertEquals(0.0, TobLootMath.standing(5), 1e-9)
        assertEquals(0.0, TobLootMath.standing(9), 1e-9)
    }

    // The event file
    // ------------------------------------------------------------------------------------------

    @Test
    fun `the event file sets the party cap and the unique chance`() {
        val settings =
            EventSettings.parse(
                """{"tob": {"partyCap": 5, "uniqueMultiplier": 2.5, "teamUniques": false}}"""
            )
        assertEquals(5, settings.tob.partyCap)
        assertEquals(2.5, settings.tob.uniqueMultiplier, 1e-9)
        assertFalse(settings.tob.teamUniques)
        assertTrue(settings.unknownKeys.isEmpty())
        val defaults = EventSettings.parse("{}")
        assertEquals(8, defaults.tob.partyCap)
        assertTrue(defaults.tob.teamUniques)
        assertThrows(IllegalArgumentException::class.java) {
            EventSettings.parse("""{"tob": {"partyCap": 9}}""")
        }
        assertEquals(
            listOf("tob.partySize"),
            EventSettings.parse("""{"tob": {"partySize": 4}}""").unknownKeys,
        )
    }
}
