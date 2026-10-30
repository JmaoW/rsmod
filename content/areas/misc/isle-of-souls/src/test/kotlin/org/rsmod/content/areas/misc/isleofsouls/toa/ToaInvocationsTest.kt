package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToaInvocationsTest {
    private val catalog = InvocationFixture.catalog

    private fun set() = InvocationSet(catalog)

    private fun InvocationSet.on(name: String): InvocationToggle =
        toggle(checkNotNull(catalog.byName(name)) { name })

    @Test
    fun `the board has every invocation with a distinct bit`() {
        assertEquals(46, catalog.all.size)
        assertEquals(46, catalog.all.map { it.bit }.toSet().size)
        assertEquals(catalog.all.map { it.name }, catalog.all.map { it.name }.distinct())
    }

    @Test
    fun `the raid level is the sum of the levels switched on`() {
        val invocations = set()
        assertEquals(0, invocations.raidLevel)
        invocations.on(Inv.TRY_AGAIN)
        invocations.on(Inv.PATHSEEKER)
        invocations.on(Inv.DEADLY_PRAYERS)
        assertEquals(5 + 15 + 20, invocations.raidLevel)
        assertEquals(3, invocations.activeCount)
    }

    @Test
    fun `modes follow the wiki ranges`() {
        assertEquals(ToaMode.Entry, ToaMode.of(0))
        assertEquals(ToaMode.Entry, ToaMode.of(149))
        assertEquals(ToaMode.Normal, ToaMode.of(150))
        assertEquals(ToaMode.Normal, ToaMode.of(299))
        assertEquals(ToaMode.Expert, ToaMode.of(300))
        assertFalse(ToaMode.Entry.countsForTasks)
        assertTrue(ToaMode.Expert.countsForTasks)
    }

    @Test
    fun `attempt invocations replace one another`() {
        val invocations = set()
        invocations.on(Inv.TRY_AGAIN)
        assertEquals(10, invocations.attempts)
        invocations.on(Inv.SOFTCORE_RUN)
        assertEquals(3, invocations.attempts)
        assertFalse(invocations.isActive(Inv.TRY_AGAIN))
        invocations.on(Inv.SOFTCORE_RUN)
        assertNull(invocations.attempts)
    }

    @Test
    fun `time limits are in ticks and cost twice their level when missed`() {
        val invocations = set()
        assertNull(invocations.timeLimitTicks)
        assertEquals(0, invocations.timePenalty)
        invocations.on(Inv.WALK_FOR_IT)
        assertEquals(4_000, invocations.timeLimitTicks) // 40 minutes
        assertEquals(20, invocations.timePenalty)
        invocations.on(Inv.SPRINT_FOR_IT)
        assertEquals(2_500, invocations.timeLimitTicks) // 25 minutes
        assertEquals(50, invocations.timePenalty)
    }

    @Test
    fun `the helpful spirit and path levels read their magnitudes`() {
        val invocations = set()
        assertEquals(100, invocations.supplyPercent)
        assertEquals(0, invocations.startingPathLevel)
        invocations.on(Inv.NEED_LESS_HELP)
        invocations.on(Inv.PATHFINDER)
        assertEquals(33, invocations.supplyPercent)
        assertEquals(2, invocations.startingPathLevel)
        invocations.on(Inv.PATHMASTER)
        assertEquals(3, invocations.startingPathLevel)
        assertFalse(invocations.isActive(Inv.PATHFINDER))
    }

    @Test
    fun `an invocation cannot be on before the one it needs`() {
        val invocations = set()
        val refused = invocations.on(Inv.OVERCLOCKED_2)
        assertTrue(refused is InvocationToggle.NeedsOther)
        assertEquals(Inv.OVERCLOCKED, (refused as InvocationToggle.NeedsOther).needs.name)
        invocations.on(Inv.OVERCLOCKED)
        invocations.on(Inv.OVERCLOCKED_2)
        invocations.on(Inv.INSANITY)
        assertTrue(invocations.isActive(Inv.INSANITY))
    }

    @Test
    fun `switching off an invocation takes its dependants with it`() {
        val invocations = set()
        invocations.on(Inv.NOT_JUST_A_HEAD)
        invocations.on(Inv.ARTERIAL_SPRAY)
        invocations.on(Inv.BLOOD_THINNERS)
        invocations.on(Inv.OVERCLOCKED)
        invocations.on(Inv.OVERCLOCKED_2)
        invocations.on(Inv.INSANITY)
        invocations.on(Inv.OVERCLOCKED)
        assertFalse(invocations.isActive(Inv.OVERCLOCKED_2))
        assertFalse(invocations.isActive(Inv.INSANITY))
        assertTrue(invocations.isActive(Inv.ARTERIAL_SPRAY))
        invocations.on(Inv.NOT_JUST_A_HEAD)
        assertFalse(invocations.isActive(Inv.ARTERIAL_SPRAY))
        assertFalse(invocations.isActive(Inv.BLOOD_THINNERS))
    }

    @Test
    fun `the blazing tombs pair is not offered`() {
        val invocations = set()
        assertEquals(InvocationToggle.NotOffered, invocations.on("Blazing Tombs I"))
        assertEquals(0, invocations.raidLevel)
    }

    @Test
    fun `the bitmaps put each invocation in the word the panel reads`() {
        val invocations = set()
        invocations.on(Inv.TRY_AGAIN) // bit 1
        invocations.on(Inv.KEEP_BACK) // bit 31 starts the second word
        invocations.on(Inv.ANCIENT_HASTE) // bit 39
        assertEquals(1 shl 1, invocations.words[0])
        assertEquals((1 shl 0) or (1 shl 8), invocations.words[1])
        assertEquals(0, invocations.words[2])
        val copy = invocations.copy()
        assertEquals(invocations.words.toList(), copy.words.toList())
        invocations.clear()
        assertTrue(invocations.isEmpty())
        assertFalse(copy.isEmpty())
    }
}
