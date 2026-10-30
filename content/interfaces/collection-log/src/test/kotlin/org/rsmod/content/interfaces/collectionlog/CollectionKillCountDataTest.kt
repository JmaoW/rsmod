package org.rsmod.content.interfaces.collectionlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionKillCountDataTest {
    @Test
    fun `every category lists at most the three client lines`() {
        for ((struct, counters) in CollectionKillCountData.byCategory) {
            assertEquals(3, counters.size, "category $struct")
            assertTrue(counters.any { it != null }, "category $struct has no counter")
        }
    }

    @Test
    fun `every counter used by a category exists once`() {
        val all = CollectionKillCountData.all.map { it.first.internalNameValue }
        assertEquals(all.size, all.toSet().size)
        for (counters in CollectionKillCountData.byCategory.values) {
            for (counter in counters.filterNotNull()) {
                assertTrue(
                    counter.internalNameValue in all,
                    "${counter.internalName} is not in the counter list",
                )
            }
        }
    }

    @Test
    fun `counters are named clog_kc`() {
        for ((counter, _) in CollectionKillCountData.all) {
            assertTrue(counter.internalNameValue.startsWith("clog_kc_"))
        }
    }

    @Test
    fun `the normal and rare clue categories show one hard clue count`() {
        val hard = CollectionKillCountData.byCategory.getValue(510)
        val hardRare = CollectionKillCountData.byCategory.getValue(2869)
        assertEquals(hard[0]?.internalName, hardRare[0]?.internalName)
    }

    @Test
    fun `a category with several lines uses each client varp once`() {
        val kings = CollectionKillCountData.byCategory.getValue(486)
        assertEquals(3, kings.filterNotNull().size)
        assertEquals(3, kings.filterNotNull().map { it.internalName }.toSet().size)
        val raids = CollectionKillCountData.byCategory.getValue(506)
        assertEquals(3, raids.filterNotNull().map { it.internalName }.toSet().size)
    }

    @Test
    fun `kill sources match monster names in any letter case`() {
        assertTrue(CollectionKillSources.countersFor("zulrah").isNotEmpty())
        assertTrue(CollectionKillSources.countersFor("  ZULRAH ").isNotEmpty())
        assertEquals(
            CollectionKillSources.countersFor("Zulrah").map { it.internalName },
            CollectionKillSources.countersFor("zulrah").map { it.internalName },
        )
        assertTrue(CollectionKillSources.countersFor("Dusk").isNotEmpty())
    }

    @Test
    fun `any revenant counts as a revenant kill`() {
        val imp = CollectionKillSources.countersFor("Revenant imp")
        val dragon = CollectionKillSources.countersFor("Revenant dragon")
        assertEquals(1, imp.size)
        assertEquals(imp.map { it.internalName }, dragon.map { it.internalName })
    }

    @Test
    fun `an unrelated monster has no counter`() {
        assertFalse(CollectionKillSources.countersFor("Goblin").isNotEmpty())
        assertFalse(CollectionKillSources.countersFor("Revenant").isNotEmpty())
    }

    @Test
    fun `every counter that kills can raise is in the counter list`() {
        val all = CollectionKillCountData.all.map { it.first.internalNameValue }.toSet()
        for (name in CollectionKillSources.names) {
            for (counter in CollectionKillSources.countersFor(name)) {
                assertTrue(counter.internalNameValue in all, "$name -> ${counter.internalName}")
            }
        }
    }
}
