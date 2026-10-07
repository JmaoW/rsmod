package org.rsmod.content.areas.misc.isleofsouls.event

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LeagueEventTest {
    @TempDir lateinit var dir: Path

    private var now = 1_000L

    private fun event() = LeagueEvent { now }

    private val standings =
        TeamRanking.rank(
            listOf(
                TeamScore(1, "Alpha", 340, 12, reachedAt = 5_000),
                TeamScore(2, "Beta", 0, 0, reachedAt = null),
            )
        )

    @Test
    fun `a new event is not frozen`() {
        val event = event()

        assertFalse(event.frozen)
        assertNull(event.frozenAt)
        assertNull(event.frozenStandings())
    }

    @Test
    fun `freezing keeps the standings and the time`() {
        val event = event()
        now = 7_000

        assertTrue(event.freeze(standings))

        assertTrue(event.frozen)
        assertEquals(7_000L, event.frozenAt)
        assertEquals(standings, event.frozenStandings())
    }

    @Test
    fun `freezing twice changes nothing`() {
        val event = event()
        event.freeze(standings)
        now = 9_000

        assertFalse(event.freeze(emptyList()))

        assertEquals(standings, event.frozenStandings())
        assertEquals(1_000L, event.frozenAt)
    }

    @Test
    fun `unfreezing makes the standings live again`() {
        val event = event()
        event.freeze(standings)

        assertTrue(event.unfreeze())

        assertFalse(event.frozen)
        assertNull(event.frozenStandings())
        assertFalse(event.unfreeze())
    }

    @Test
    fun `listeners hear about every freeze and unfreeze but not no-ops`() {
        val event = event()
        var heard = 0
        event.onChange { heard++ }

        event.freeze(standings)
        event.freeze(standings)
        event.unfreeze()
        event.unfreeze()

        assertEquals(2, heard)
    }

    @Test
    fun `a freeze survives a restart`() {
        val first = event().also { it.load(dir) }
        now = 4_000
        first.freeze(standings)

        val second = event().also { it.load(dir) }

        assertTrue(second.frozen)
        assertEquals(4_000L, second.frozenAt)
        assertEquals(standings, second.frozenStandings())
    }

    @Test
    fun `an unfreeze survives a restart too`() {
        val first = event().also { it.load(dir) }
        first.freeze(standings)
        first.unfreeze()

        assertFalse(event().also { it.load(dir) }.frozen)
    }

    @Test
    fun `a file that cannot be read stops loading and is left alone`() {
        val file = dir.resolve("event.json")
        file.writeText("{ nope")

        assertThrows(IllegalStateException::class.java) { event().load(dir) }

        assertEquals("{ nope", file.readText())
    }

    @Test
    fun `a frozen file without standings is rejected`() {
        dir.resolve("event.json").writeText("""{"frozen": true, "frozenAt": 5}""")

        assertThrows(IllegalStateException::class.java) { event().load(dir) }
    }

    @Test
    fun `an in memory event never touches the disk`() {
        val event = event()
        event.freeze(standings)

        assertTrue(dir.toFile().list().isNullOrEmpty())
    }
}
