package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToaPointsTest {
    private var team = 2
    private val points = ToaPoints { team }

    @Test
    fun `a team starts on five thousand points and loot is worked out without them`() {
        assertEquals(5_000, points.total)
        assertEquals(0, points.lootPoints)
    }

    @Test
    fun `damage becomes room points that join the total when the room is won`() {
        points.damage(1, 1_000)
        points.damage(2, 400)
        assertEquals(5_000, points.total)
        assertEquals(1_000, points.roomPoints(1))
        val mvp = points.completeRoom()
        assertEquals(1, mvp)
        // 1,000 + 400 plus the bonus of 300 x 2 players.
        assertEquals(5_000 + 1_400 + 600, points.total)
        assertEquals(1_600, points.personal(1))
        assertEquals(400, points.personal(2))
        assertEquals(0, points.roomPoints(1))
    }

    @Test
    fun `monster multipliers scale the points and a room is capped at twenty thousand`() {
        points.damage(1, 100, ToaPoints.Multiplier.BABA)
        assertEquals(200, points.roomPoints(1))
        points.damage(1, 100, ToaPoints.Multiplier.WARDEN_CORE)
        assertEquals(200, points.roomPoints(1))
        points.damage(1, 50_000)
        assertEquals(20_000, points.roomPoints(1))
    }

    @Test
    fun `the total never passes sixty-four thousand`() {
        repeat(5) {
            points.damage(1, 20_000)
            points.completeRoom()
        }
        assertEquals(64_000, points.total)
    }

    @Test
    fun `a death costs a fifth of the total, at least a thousand`() {
        points.death()
        assertEquals(4_000, points.total)
        // A fifth of 4,000 is 800, so the thousand minimum applies.
        points.death()
        assertEquals(3_000, points.total)
        repeat(20) { points.death() }
        assertEquals(0, points.total)
    }

    @Test
    fun `awards go straight to the total and the player share`() {
        points.award(2, 300)
        assertEquals(5_300, points.total)
        assertEquals(300, points.personal(2))
    }
}
