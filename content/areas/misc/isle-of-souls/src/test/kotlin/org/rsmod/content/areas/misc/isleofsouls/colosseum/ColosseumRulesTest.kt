package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Archer
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Berserker
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Jaguar
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Javelin
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Manticore
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Minotaur
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Seer
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Shaman
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Shockwave
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMonster.Sol
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.map.CoordGrid

class ColosseumRulesTest {
    // The waves ---------------------------------------------------------------------------------

    private fun wave(n: Int, modifiers: ColosseumModifierSet = ColosseumModifierSet()) =
        ColosseumWaves.starting(n, modifiers) { 0 }.groupingBy { it }.eachCount()

    @Test
    fun `each wave sends in the wiki's monsters`() {
        val trio = mapOf(Berserker to 1, Seer to 1, Archer to 1)
        assertEquals(trio + (Shaman to 1), wave(1))
        assertEquals(trio + mapOf(Shaman to 1, Javelin to 1), wave(2))
        assertEquals(trio + mapOf(Shaman to 1, Javelin to 2), wave(3))
        assertEquals(trio + mapOf(Shaman to 1, Manticore to 1), wave(4))
        assertEquals(trio + mapOf(Shaman to 1, Javelin to 1, Manticore to 1), wave(5))
        assertEquals(trio + mapOf(Shaman to 1, Javelin to 2, Manticore to 1), wave(6))
        assertEquals(trio + mapOf(Javelin to 1, Manticore to 1, Shockwave to 1), wave(7))
        assertEquals(trio + mapOf(Javelin to 2, Manticore to 1, Shockwave to 1), wave(8))
        assertEquals(trio + mapOf(Javelin to 1, Manticore to 2), wave(9))
        assertEquals(trio + mapOf(Javelin to 2, Manticore to 2), wave(10))
        assertEquals(trio + mapOf(Javelin to 1, Manticore to 2, Shockwave to 1), wave(11))
        assertEquals(mapOf(Sol to 1), wave(12))
    }

    @Test
    fun `quartet adds a warbander and dynamic duo doubles the shockwave colossus`() {
        val modifiers = ColosseumModifierSet()
        modifiers.add(ColosseumModifier.Quartet)
        modifiers.add(ColosseumModifier.DynamicDuo)
        val counts = wave(11, modifiers)
        assertEquals(2, counts[Berserker], "the roll of 0 picks a second berserker")
        assertEquals(2, counts[Shockwave])
    }

    @Test
    fun `reinforcements are the jaguar, the shaman and the minotaur on the wiki's waves`() {
        assertEquals(listOf(Jaguar), ColosseumWaves.reinforcements(1))
        assertEquals(listOf(Jaguar, Shaman), ColosseumWaves.reinforcements(5))
        assertEquals(listOf(Minotaur), ColosseumWaves.reinforcements(8))
        assertEquals(listOf(Shaman, Minotaur), ColosseumWaves.reinforcements(10))
        assertEquals(emptyList<ColosseumMonster>(), ColosseumWaves.reinforcements(12))
    }

    // The modifiers -----------------------------------------------------------------------------

    @Test
    fun `the first offer is always blasphemy, relentless and frailty`() {
        val offer = ColosseumModifierSet().offer(1) { 0 }
        assertEquals(
            listOf(
                ColosseumModifier.Blasphemy,
                ColosseumModifier.Relentless,
                ColosseumModifier.Frailty,
            ),
            offer,
        )
    }

    @Test
    fun `red flag and dynamic duo wait for wave 7, and the last wave has nothing for monsters`() {
        val set = ColosseumModifierSet()
        for (seed in 0 until 200) {
            var calls = 0
            val early = set.offer(4) { bound -> (seed * 31 + calls++ * 7) % bound }
            assertEquals(3, early.toSet().size)
            assertFalse(ColosseumModifier.RedFlag in early || ColosseumModifier.DynamicDuo in early)
            val last = set.offer(12) { bound -> (seed * 17 + calls++ * 13) % bound }
            for (modifier in
                listOf(
                    ColosseumModifier.DynamicDuo,
                    ColosseumModifier.RedFlag,
                    ColosseumModifier.Mantimayhem,
                    ColosseumModifier.Reentry,
                )) {
                assertFalse(modifier in last, "$modifier offered before Sol Heredit")
            }
        }
    }

    @Test
    fun `a maxed modifier is not offered again, and glory counts every tier`() {
        val set = ColosseumModifierSet()
        repeat(5) { set.add(ColosseumModifier.Doom) }
        set.add(ColosseumModifier.Quartet)
        assertEquals(3, set.tier(ColosseumModifier.Doom), "tiers stop at 3")
        assertTrue(set.maxed(ColosseumModifier.Quartet), "an untiered modifier is done once taken")
        for (seed in 0 until 100) {
            val offer = set.offer(8) { bound -> seed % bound }
            assertFalse(ColosseumModifier.Doom in offer)
            assertFalse(ColosseumModifier.Quartet in offer)
        }
        assertEquals(200 * 3 + 100, set.glory())
    }

    // Glory -------------------------------------------------------------------------------------

    @Test
    fun `a wave's glory is completion, no damage, modifiers and an even time bonus`() {
        // The wiki's example: wave 1 in 50 ticks is a 450 time bonus.
        assertEquals(100 + 100 + 100 + 450, Colosseum.glory(1, 50, false, 100))
        assertEquals(300 + 0 + 0 + 1496, Colosseum.glory(3, 1, true, 0), "rounded down to even")
        assertEquals(1200, Colosseum.glory(12, 1_000, true, 0), "no time left")
        assertEquals("Grand Champion", Colosseum.rankOf(20_000))
        assertEquals("Brawler", Colosseum.rankOf(4_999))
        assertEquals("Rookie", Colosseum.rankOf(0))
    }

    // The loot ----------------------------------------------------------------------------------

    @Test
    fun `each wave's table adds up to the wiki's denominator`() {
        val totals =
            mapOf(
                2 to 7,
                3 to 70,
                4 to 43_400,
                5 to 19_250,
                6 to 16_800,
                7 to 45_920,
                8 to 114_240,
                9 to 21_600,
                10 to 16_000,
                11 to 2_080,
                12 to 4_800,
            )
        for ((wave, total) in totals) {
            assertEquals(
                total,
                ColosseumLoot.tables.getValue(wave).sumOf { it.weight },
                "wave $wave",
            )
        }
    }

    @Test
    fun `wave 1 is 80 splinters and wave 12 always adds the quiver`() {
        val first = ColosseumLoot.roll(1, { 0 }) { 0 }
        assertEquals(1, first.size)
        assertSame(colosseum_objs.sunfire_splinters, first.single().obj)
        assertEquals(80, first.single().count)
        val last = ColosseumLoot.roll(12, { 0 }) { 0 }
        assertTrue(last.any { it.obj === colosseum_objs.quiver_uncharged })
    }

    @Test
    fun `a fanatic roll gives the piece the player has fewest of`() {
        val table = ColosseumLoot.tables.getValue(4)
        val before = table.takeWhile { !it.fanatic }.sumOf { it.weight }
        // Unresolved refs compare equal in a unit test, so the pieces are told apart by identity.
        fun owned(piece: org.rsmod.game.type.obj.ObjType): Int =
            when {
                piece === colosseum_objs.sunfire_helm -> 1
                piece === colosseum_objs.sunfire_legs -> 2
                else -> 0
            }
        val loot = ColosseumLoot.roll(4, { bound -> if (bound > 3) before else 0 }, ::owned)
        assertSame(colosseum_objs.sunfire_body, loot.single().obj)
    }

    // Settings ----------------------------------------------------------------------------------

    @Test
    fun `the colosseum settings default to the full run with both glory gates off`() {
        val settings = EventSettings.parse("{}").colosseum
        assertEquals(ColosseumMode.Full, settings.mode)
        assertEquals((1..12).toList(), settings.waves)
        assertEquals(0, settings.bankChestGlory)
        assertEquals(0, settings.ringGlory)
    }

    @Test
    fun `the shortened run plays the event's list, which must end on wave 12`() {
        val settings = EventSettings.parse("""{ "colosseum": { "mode": "shortened" } }""").colosseum
        assertEquals(listOf(1, 4, 7, 10, 12), settings.waves)
        val own =
            EventSettings.parse(
                    """{ "colosseum": { "mode": "shortened", "shortenedWaves": [2, 12],
                        "bankChestGlory": 2000 } }"""
                )
                .colosseum
        assertEquals(listOf(2, 12), own.waves)
        assertEquals(2000, own.bankChestGlory)
        assertThrows(IllegalArgumentException::class.java) {
            EventSettings.parse("""{ "colosseum": { "shortenedWaves": [1, 4] } }""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventSettings.parse("""{ "colosseum": { "mode": "short" } }""")
        }
        val typo = EventSettings.parse("""{ "colosseum": { "bankGlory": 1 } }""")
        assertEquals(listOf("colosseum.bankGlory"), typo.unknownKeys)
    }

    // Sol Heredit's patterns ----------------------------------------------------------------------

    @Test
    fun `shield 1 leaves the ring at 9x9 safe and shield 2 the ring at 11x11`() {
        val sol = ColosseumArena.solLanding
        val centre = CoordGrid(sol.x + 2, sol.z + 2, 0)
        val one = SolPatterns.shield(sol, second = false).map { it.tile }.toSet()
        val two = SolPatterns.shield(sol, second = true).map { it.tile }.toSet()
        assertFalse(centre.translate(4, 0) in one)
        assertTrue(centre.translate(5, 0) in one)
        assertFalse(centre.translate(5, 0) in two)
        assertTrue(centre.translate(4, 0) in two)
        assertTrue((one + two).all { ColosseumArena.inSolFloor(it) })
    }

    @Test
    fun `spear 1 covers the row in front and spares the corner tiles behind it`() {
        val sol = ColosseumArena.solLanding
        // A player south of his middle: the row south of him is hit, two lines run south.
        val player = CoordGrid(sol.x + 2, sol.z - 3, 0)
        assertEquals("S", SolPatterns.directionTo(sol, player))
        val tiles = SolPatterns.spear(sol, player, second = false).map { it.tile }.toSet()
        assertTrue(CoordGrid(sol.x, sol.z - 1, 0) in tiles, "the row in front")
        assertTrue(CoordGrid(sol.x + 1, sol.z - 2, 0) in tiles, "a line")
        assertFalse(CoordGrid(sol.x + 2, sol.z - 2, 0) in tiles, "between the lines is safe")
    }
}
