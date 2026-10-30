package org.rsmod.content.interfaces.collectionlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionCatalogTest {
    /*
     * Five tabs with one category each, except tab 0, which has two: category 100 lists objs
     * 1, 2 and 3, category 101 lists obj 3 and 4 (so obj 3 is in two categories). Obj 2 is
     * listed under the alias key 20.
     */
    private fun catalog(): CollectionCatalog {
        val structs = HashMap<Int, Map<Int, Any>>()
        val enums = HashMap<Int, Map<Any, Any?>>()
        val categoryStructs =
            listOf(listOf(100, 101), listOf(110), listOf(120), listOf(130), listOf(140))
        for (tab in 0 until 5) {
            structs[471 + tab] =
                mapOf(
                    682 to "Tab $tab",
                    683 to 2000 + tab,
                    685 to 10 + tab,
                    686 to 20 + tab,
                    687 to 30 + tab,
                    688 to 40 + tab,
                )
            enums[2000 + tab] =
                categoryStructs[tab].withIndex().associate { (i, s) -> i as Any to s }
        }
        fun category(struct: Int, name: String, enumId: Int, objs: List<Int>) {
            structs[struct] = mapOf(689 to name, 690 to enumId)
            enums[enumId] = objs.withIndex().associate { (i, o) -> i as Any to o }
        }
        category(100, "Alpha", 3000, listOf(1, 2, 3))
        category(101, "Beta", 3001, listOf(3, 4))
        category(110, "Raid", 3002, listOf(5))
        category(120, "Clue", 3003, listOf(6))
        category(130, "Minigame", 3004, listOf(7))
        category(140, "Other", 3005, listOf(8))
        enums[3721] = mapOf(2 to 20)
        return CollectionCatalog.build({ structs[it] }, { enums[it] })
    }

    @Test
    fun `reads tabs, categories and totals`() {
        val catalog = catalog()
        assertEquals(5, catalog.tabs.size)
        assertEquals(listOf("Alpha", "Beta"), catalog.tabs[0].categories.map { it.name })
        assertEquals(listOf(0, 1), catalog.tabs[0].categories.map { it.row })
        assertEquals(9, catalog.slots)
        assertEquals(listOf(5, 1, 1, 1, 1), catalog.tabSlots.toList())
        assertEquals(10, catalog.tabs[0].container)
        assertEquals(41, catalog.tabs[1].scrollbar)
        assertEquals(471, catalog.tabs[0].struct)
    }

    @Test
    fun `an obj in two categories is one entry with both categories`() {
        val entry = assertNotNull(catalog().entryOf(3))
        assertEquals(listOf(100, 101), entry.categories.map { it.struct })
    }

    @Test
    fun `an aliased obj is stored under its alias but listed as itself`() {
        val entry = assertNotNull(catalog().entryOf(2))
        assertEquals(2, entry.listed)
        assertEquals(20, entry.key)
        assertEquals(1, assertNotNull(catalog().entryOf(1)).key)
    }

    @Test
    fun `an obj the log does not list has no entry`() {
        assertNull(catalog().entryOf(99))
        assertNull(catalog().category(12345))
        assertTrue(catalog().listedObjs.containsAll(listOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        org.junit.jupiter.api.Assertions.assertNotNull(value)
        return value!!
    }
}
