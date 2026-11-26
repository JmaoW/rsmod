package org.rsmod.content.areas.misc.isleofsouls.cox

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class CoxRulesTest {
    private val sw = CoordGrid(6400, 6400, 0)

    private fun layouts(): List<CoxLayout> {
        val out = ArrayList<CoxLayout>()
        val random = Random(42)
        repeat(400) { out += CoxLayout.normal { random.nextInt(it) } }
        out += CoxLayout.challenge()
        return out
    }

    @Test
    fun `every pattern lays out a path without two rooms on one square`() {
        for (layout in layouts()) {
            for (floor in 0 until layout.floors) {
                val squares = layout.floor(floor).map { it.gridX to it.gridZ }
                assertEquals(squares.size, squares.toSet().size, "overlap in $layout")
                for ((a, b) in squares.zipWithNext()) {
                    val step =
                        kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)
                    assertEquals(1, step, "rooms not neighbours in $layout")
                }
                // The floors fit the region, leaving the Olm's chamber its own zones.
                for (room in layout.floor(floor)) {
                    assertTrue(CoxMap.BASE_ZONE + (room.gridZ + 1) * 4 <= CoxMap.OLM_ZONE_Z)
                    assertTrue(CoxMap.BASE_ZONE + (room.gridX + 1) * 4 <= 40)
                }
            }
            assertEquals(CoxRoomType.RaidStart, layout.rooms.first().type)
            assertEquals(0, layout.rooms.first().rotation)
            assertEquals(CoxRoomType.FloorEnd, layout.rooms.last().type)
            assertEquals(1, layout.rooms.last().variant, "the last floor's end has the Olm's hole")
        }
    }

    @Test
    fun `a normal raid repeats no combat room and draws two puzzles plus at most one extra`() {
        for (layout in layouts().filter { !it.challenge }) {
            val combat = layout.rooms.filter { it.type.kind == CoxRoomKind.Combat }.map { it.type }
            assertEquals(combat.size, combat.toSet().size, "combat repeated in $layout")
            val puzzles =
                layout.rooms.filter { it.type.kind == CoxRoomKind.Puzzle }.map { it.type }.toSet()
            val slots = layout.rooms.count { it.type.kind == CoxRoomKind.Puzzle }
            assertTrue(puzzles.size <= minOf(slots, 3), "too many puzzle kinds in $layout")
        }
    }

    /** The template tiles on a room's way out, by version, and on its way in. */
    private fun exits(room: CoxPlacement): List<CoordGrid> {
        val local =
            when (room.variant) {
                0 -> listOf(0 to 15, 0 to 16)
                1 -> listOf(15 to 31, 16 to 31)
                else -> listOf(31 to 15, 31 to 16)
            }
        return local.map {
            CoordGrid(room.type.sourceX(room.variant) + it.first, room.type.sourceZ + it.second, 0)
        }
    }

    private fun entries(room: CoxPlacement): List<CoordGrid> =
        listOf(15, 16).map { CoordGrid(room.type.sourceX(room.variant) + it, room.type.sourceZ, 0) }

    @Test
    fun `each room's way out meets the next room's way in`() {
        for (layout in layouts()) {
            for (floor in 0 until layout.floors) {
                for ((a, b) in layout.floor(floor).zipWithNext()) {
                    val out = exits(a).map { CoxMap.toRegion(a, it, sw) }
                    val into = entries(b).map { CoxMap.toRegion(b, it, sw) }
                    val nearest =
                        out.minOf { o ->
                            into.minOf { i ->
                                maxOf(kotlin.math.abs(o.x - i.x), kotlin.math.abs(o.z - i.z))
                            }
                        }
                    assertEquals(
                        1,
                        nearest,
                        "${a.type} (${a.variant}, ${a.rotation}) to ${b.type} in $layout",
                    )
                }
            }
        }
    }

    @Test
    fun `room tiles survive the trip into the raid and back for every turn`() {
        for (rotation in 0..3) {
            val room = CoxPlacement(0, CoxRoomType.Tekton, 0, 1, 1, rotation, 2)
            for (x in 0 until 32) for (z in 0 until 32) {
                val template = CoordGrid(room.type.sourceX(2) + x, room.type.sourceZ + z, 0)
                val inside = CoxMap.toRegion(room, template, sw)
                assertEquals(room, CoxMap.roomAt(CoxLayout(null, false, listOf(room)), inside, sw))
                assertEquals(template, CoxMap.toTemplate(room, inside, sw))
            }
        }
    }

    @Test
    fun `scaling follows the dps calculator`() {
        // Solo at 126 combat and 99 Hitpoints: everything at its base.
        val solo = CoxScaling.scale(CoxScaling.Bases.tekton, CoxScalingInputs(1, 126, 99))
        assertEquals(300, solo.hitpoints)
        assertEquals(390, solo.attack)
        assertEquals(205, solo.defence)
        // Five raiders: hitpoints x (1 + 5 / 2) = x3, offence x (100 + 7 x 2 + 4)%.
        val five = CoxScaling.scale(CoxScaling.Bases.tekton, CoxScalingInputs(5, 126, 99))
        assertEquals(900, five.hitpoints)
        assertEquals(390 * 118 / 100, five.attack)
        assertEquals(205 * (100 + 2 + 2) / 100, five.defence)
        // Challenge Mode adds half; Tekton's defence a fifth under four raiders.
        val cm =
            CoxScaling.scale(
                CoxScaling.Bases.tekton,
                CoxScalingInputs(1, 126, 99, challenge = true),
                tekton = true,
            )
        assertEquals(450, cm.hitpoints)
        assertEquals(205 + 205 * 20 / 100, cm.defence)
        // Scavengers scale alone.
        val scavenger = CoxScaling.scale(CoxScaling.Bases.scavenger, CoxScalingInputs(20, 126, 99))
        assertEquals(30, scavenger.hitpoints)
        // The Olm: k = min(n - 1, 50) - 3 x (min(n, 50) / 8).
        assertEquals(800, CoxScaling.olmHitpoints(1, head = true))
        assertEquals(600 + 300 * (7 - 3), CoxScaling.olmHitpoints(8, head = false))
    }

    @Test
    fun `loot math`() {
        assertEquals(0.01, CoxLootMath.uniqueChance(8676), 1e-9)
        assertEquals(CoxLootMath.UNIQUE_CAP, CoxLootMath.uniqueChance(10_000_000), 1e-9)
        assertEquals(60, CoxLootMath.UNIQUES.sumOf { it.second })
        assertEquals(56, CoxLootMath.UNIQUES.sumOf { it.third })
        assertEquals(33, CoxLootMath.COMMONS.size)
        val death = CoxLootMath.COMMONS.first { it.name == "deathrune" }
        assertEquals(3640, CoxLootMath.amount(death, 131071))
        assertEquals(3640, CoxLootMath.amount(death, 900_000))
        assertEquals(7000, CoxLootMath.challengeTarget(1))
    }
}
