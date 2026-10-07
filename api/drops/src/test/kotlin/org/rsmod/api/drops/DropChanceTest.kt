package org.rsmod.api.drops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DropChanceTest {
    @Test
    fun `parse fractions`() {
        assertEquals(3.0 / 128.0, DropChance.parse("3/128"))
        assertEquals(1.0 / 5000.0, DropChance.parse("~1/5,000"))
        assertEquals(1.0 / 2.5, DropChance.parse("1 / 2.5"))
    }

    @Test
    fun `parse percentages and plain numbers`() {
        assertEquals(0.125, DropChance.parse("12.5%"))
        assertEquals(0.25, DropChance.parse("0.25"))
    }

    @Test
    fun `reject chances that are not between zero and one`() {
        assertThrows<IllegalArgumentException> { DropChance.parse("0/128") }
        assertThrows<IllegalArgumentException> { DropChance.parse("5/4") }
        assertThrows<IllegalArgumentException> { DropChance.parse("1/0") }
        assertThrows<IllegalArgumentException> { DropChance.parse("Rare") }
    }

    @Test
    fun `always is recognised`() {
        assertTrue(DropChance.isAlways("Always"))
        assertTrue(DropChance.isAlways(" always "))
        assertTrue(DropChance.isAlways("1/1"))
        assertFalse(DropChance.isAlways("3/128"))
    }

    @Test
    fun `parse quantities`() {
        assertEquals(5..5, DropChance.parseQuantity("5"))
        assertEquals(1..5, DropChance.parseQuantity("1-5"))
        assertEquals(1..5, DropChance.parseQuantity("1–5"))
        assertEquals(1000..2000, DropChance.parseQuantity("1,000-2,000"))
    }

    @Test
    fun `reject bad quantities`() {
        assertThrows<IllegalArgumentException> { DropChance.parseQuantity("0") }
        assertThrows<IllegalArgumentException> { DropChance.parseQuantity("5-1") }
        assertThrows<IllegalArgumentException> { DropChance.parseQuantity("a lot") }
        assertThrows<IllegalArgumentException> { DropChance.parseQuantity("1-2-3") }
    }
}
