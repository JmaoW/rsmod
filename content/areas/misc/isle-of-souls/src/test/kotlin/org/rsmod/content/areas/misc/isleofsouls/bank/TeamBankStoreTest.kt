package org.rsmod.content.areas.misc.isleofsouls.bank

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TeamBankStoreTest {
    private fun sample(): TeamBankStore {
        val store = TeamBankStore()
        store[1] =
            TeamBankData(
                layout = listOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 3),
                objs =
                    mapOf(
                        0 to StoredObj(995, 100_000, 0),
                        1 to StoredObj(1511, 12, 0),
                        4 to StoredObj(1265, 0, 7),
                    ),
            )
        store[2] = TeamBankData(layout = List(10) { 0 }, objs = emptyMap())
        return store
    }

    private fun parseError(json: String): String? =
        assertThrows(IllegalArgumentException::class.java) { TeamBankStore.parse(json) }.message

    @Test
    fun `a store survives a round trip through json`() {
        val parsed = TeamBankStore.parse(sample().toJson())

        assertEquals(setOf(1, 2), parsed.teamIds)
        val bank = parsed[1]!!
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 3), bank.layout)
        assertEquals(StoredObj(995, 100_000, 0), bank.objs[0])
        assertEquals(StoredObj(1265, 0, 7), bank.objs[4])
        assertEquals(3, bank.objs.size)
        assertEquals(emptyMap<Int, StoredObj>(), parsed[2]!!.objs)
        assertNull(parsed[3])
    }

    @Test
    fun `saving the same data twice gives identical text`() {
        assertEquals(sample().toJson(), sample().toJson())
        assertEquals(sample().toJson(), TeamBankStore.parse(sample().toJson()).toJson())
    }

    @Test
    fun `slots are written in order whatever order they were added`() {
        val a = TeamBankStore()
        a[1] =
            TeamBankData(listOf(0), linkedMapOf(5 to StoredObj(1, 1, 0), 2 to StoredObj(2, 1, 0)))
        val b = TeamBankStore()
        b[1] =
            TeamBankData(listOf(0), linkedMapOf(2 to StoredObj(2, 1, 0), 5 to StoredObj(1, 1, 0)))

        assertEquals(a.toJson(), b.toJson())
    }

    @Test
    fun `a file that is not a bank file is rejected with a reason`() {
        assertEquals("The file needs a top-level \"teams\" object.", parseError("{}"))
        assertEquals(
            "\"alpha\" is not a team id.",
            parseError("""{"teams":{"alpha":{"layout":[],"items":{}}}}"""),
        )
        assertEquals(
            "Team 1 needs a \"layout\" list.",
            parseError("""{"teams":{"1":{"items":{}}}}"""),
        )
        assertEquals(
            "Slot 0 (team 1) must be [obj, count, vars].",
            parseError("""{"teams":{"1":{"layout":[0],"items":{"0":[995,5]}}}}"""),
        )
        assertEquals(
            "Slot 0 (team 1) must be three whole numbers, with a count of 0 or more.",
            parseError("""{"teams":{"1":{"layout":[0],"items":{"0":[995,-1,0]}}}}"""),
        )
    }
}
