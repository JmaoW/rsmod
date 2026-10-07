package org.rsmod.content.areas.misc.isleofsouls.teams

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamProgressStoreTest {
    @Test
    fun `a task completes once per team`() {
        val store = TeamProgressStore()

        assertTrue(store.complete(1, 303, at = 100))
        assertFalse(store.complete(1, 303, at = 200))
        assertTrue(store.complete(2, 303, at = 300))

        assertEquals(mapOf(303 to 100L), store.completed(1))
        assertTrue(store.isComplete(2, 303))
        assertFalse(store.isComplete(1, 304))
    }

    @Test
    fun `counters add up to their cap`() {
        val store = TeamProgressStore()

        assertEquals(1, store.increment(1, "tin", cap = 2))
        assertEquals(2, store.increment(1, "tin", cap = 2))
        assertEquals(2, store.increment(1, "tin", cap = 2))

        assertEquals(2, store.counter(1, "tin"))
        assertEquals(0, store.counter(2, "tin"))
    }

    @Test
    fun `clear forgets one team and reports the tasks it cleared`() {
        val store = TeamProgressStore()
        store.complete(1, 10, at = 1)
        store.complete(1, 11, at = 2)
        store.complete(2, 10, at = 3)
        store.increment(1, "tin", cap = 5)

        assertEquals(2, store.clear(1))

        assertTrue(store.completed(1).isEmpty())
        assertEquals(0, store.counter(1, "tin"))
        assertTrue(store.isComplete(2, 10))
        assertEquals(0, store.clear(1))
    }

    @Test
    fun `json round trips keeping completion order`() {
        val store = TeamProgressStore()
        store.complete(1, 500, at = 1_759_650_000_000)
        store.complete(1, 20, at = 1_759_650_001_000)
        store.increment(1, "isle_count_tin_ore", cap = 5)
        store.complete(7, 303, at = 5)

        val again = TeamProgressStore.parse(store.toJson())

        assertEquals(listOf(500, 20), again.completed(1).keys.toList())
        assertEquals(1_759_650_001_000, again.completed(1)[20])
        assertEquals(1, again.counter(1, "isle_count_tin_ore"))
        assertTrue(again.isComplete(7, 303))
    }

    @Test
    fun `bad files are rejected with an argument error`() {
        val bad =
            listOf(
                """{}""",
                """{"teams": []}""",
                """{"teams": {"x": {}}}""",
                """{"teams": {"1": {"completed": {"abc": 5}}}}""",
                """{"teams": {"1": {"completed": {"5": "soon"}}}}""",
                """{"teams": {"1": {"counters": {"tin": "many"}}}}""",
            )
        for (json in bad) {
            assertThrows(
                IllegalArgumentException::class.java,
                { TeamProgressStore.parse(json) },
                json,
            )
        }
    }
}
