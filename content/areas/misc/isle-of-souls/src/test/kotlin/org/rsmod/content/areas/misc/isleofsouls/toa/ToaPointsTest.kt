package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToaPointsTest {
    private var team = 2
    private val points = ToaPoints { team }

    @Test
    fun `every player starts on five thousand points and loot is worked out without them`() {
        assertEquals(5_000, points.total(1))
        assertEquals(5_000, points.total(2))
        assertEquals(0, points.lootPoints(1))
        assertEquals(0, points.teamLootPoints)
    }

    @Test
    fun `damage becomes room points that join the player's own total when the room is won`() {
        points.damage(1, 1_000)
        points.damage(2, 400)
        assertEquals(5_000, points.total(1))
        assertEquals(1_000, points.roomPoints(1))
        val mvp = points.completeRoom()
        assertEquals(1, mvp)
        // 1,000 plus the bonus of 300 x 2 players.
        assertEquals(5_000 + 1_000 + 600, points.total(1))
        assertEquals(5_000 + 400, points.total(2))
        assertEquals(1_600, points.lootPoints(1))
        assertEquals(400, points.lootPoints(2))
        assertEquals(2_000, points.teamLootPoints)
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
    fun `each player's total is capped at sixty-four thousand, not the team's`() {
        repeat(5) {
            points.damage(1, 20_000)
            points.damage(2, 20_000)
            points.completeRoom()
        }
        assertEquals(64_000, points.total(1))
        assertEquals(64_000, points.total(2))
        assertEquals(2 * 59_000, points.teamLootPoints)
    }

    @Test
    fun `a death costs the player who died a fifth of their own total, at least a thousand`() {
        repeat(2) {
            points.damage(1, 20_000)
            points.damage(2, 20_000)
            points.completeRoom()
        }
        val dying = points.total(1)
        val other = points.total(2)
        points.death(1)
        assertEquals(dying - dying / 5, points.total(1))
        assertEquals(other, points.total(2))

        points.death(3)
        assertEquals(4_000, points.total(3))
        // A fifth of 4,000 is 800, so the thousand minimum applies.
        points.death(3)
        assertEquals(3_000, points.total(3))
        repeat(20) { points.death(3) }
        assertEquals(0, points.total(3))
        assertEquals(0, points.lootPoints(3))
    }

    @Test
    fun `awards go straight to the player's total`() {
        points.award(2, 300)
        assertEquals(5_300, points.total(2))
        assertEquals(5_000, points.total(1))
        assertEquals(300, points.teamLootPoints)
    }
}
