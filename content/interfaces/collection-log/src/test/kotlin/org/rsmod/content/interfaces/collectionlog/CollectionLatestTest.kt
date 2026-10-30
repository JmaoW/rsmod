package org.rsmod.content.interfaces.collectionlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionLatestTest {
    @Test
    fun `the newest item goes first and the rest move down`() {
        val items = IntArray(CollectionLatest.SIZE) { CollectionLatest.EMPTY }
        val dates = IntArray(CollectionLatest.SIZE)

        CollectionLatest.shift(items, dates, obj = 11, day = 9000)
        CollectionLatest.shift(items, dates, obj = 22, day = 9001)

        assertEquals(22, items[0])
        assertEquals(9001, dates[0])
        assertEquals(11, items[1])
        assertEquals(9000, dates[1])
        assertEquals(CollectionLatest.EMPTY, items[2])
    }

    @Test
    fun `the oldest item falls off after twelve`() {
        val items = IntArray(CollectionLatest.SIZE) { CollectionLatest.EMPTY }
        val dates = IntArray(CollectionLatest.SIZE)

        for (obj in 1..13) {
            CollectionLatest.shift(items, dates, obj = obj, day = 9000 + obj)
        }

        assertEquals(13, items[0])
        assertEquals(2, items[CollectionLatest.SIZE - 1])
        assertEquals(9002, dates[CollectionLatest.SIZE - 1])
    }
}
