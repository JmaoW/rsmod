package org.rsmod.content.other.dragonfire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.herblore.scripts.AntifireLevel

/** The numbers are the OSRS Wiki's "Dragonfire" tables (burnt / resisted for the chromatic). */
class DragonfireTableTest {
    private val none = DragonfireProtection.NONE
    private val shield = DragonfireProtection(shield = true)
    private val prayer = DragonfireProtection(prayer = true)
    private val shieldPrayer = DragonfireProtection(shield = true, prayer = true)
    private val antifire = DragonfireProtection(potion = AntifireLevel.Regular)
    private val superAntifire = DragonfireProtection(potion = AntifireLevel.Super)
    private val shieldAntifire = DragonfireProtection(shield = true, potion = AntifireLevel.Regular)
    private val shieldSuper = DragonfireProtection(shield = true, potion = AntifireLevel.Super)
    private val prayerAntifire = DragonfireProtection(prayer = true, potion = AntifireLevel.Regular)
    private val prayerSuper = DragonfireProtection(prayer = true, potion = AntifireLevel.Super)
    private val allAntifire =
        DragonfireProtection(shield = true, prayer = true, potion = AntifireLevel.Regular)
    private val allSuper =
        DragonfireProtection(shield = true, prayer = true, potion = AntifireLevel.Super)

    private val everything =
        listOf(
            none,
            shield,
            prayer,
            shieldPrayer,
            antifire,
            superAntifire,
            shieldAntifire,
            shieldSuper,
            prayerAntifire,
            prayerSuper,
            allAntifire,
            allSuper,
        )

    private fun Breath.maxHits(burnt: Boolean) = everything.map { maxHit(it, burnt) }

    @Test
    fun `every protection has its own row`() {
        assertEquals((0 until BREATH_ROWS).toList(), everything.map { it.row })
    }

    @Test
    fun `chromatic dragons`() {
        assertEquals(listOf(50, 5, 10, 5, 35, 0, 0, 0, 0, 0, 0, 0), Breath.Chromatic.maxHits(true))
        assertEquals(listOf(30, 5, 10, 5, 15, 0, 0, 0, 0, 0, 0, 0), Breath.Chromatic.maxHits(false))
    }

    @Test
    fun `chromatic dragons breathe at most 50 and nothing gets through super antifire`() {
        assertEquals(50, Breath.Chromatic.maxHit(none, burntRoll = true))
        assertEquals(0, Breath.Chromatic.maxHit(superAntifire, burntRoll = true))
        assertEquals(0, Breath.Chromatic.maxHit(shieldAntifire, burntRoll = true))
        assertEquals(35, Breath.Chromatic.maxHit(antifire, burntRoll = true))
    }

    @Test
    fun `metal dragons ignore protect from magic`() {
        for (potion in AntifireLevel.entries) {
            for (shield in listOf(false, true)) {
                val without = DragonfireProtection(shield, prayer = false, potion = potion)
                val with = DragonfireProtection(shield, prayer = true, potion = potion)
                for (burnt in listOf(true, false)) {
                    assertEquals(
                        Breath.Metallic.maxHit(without, burnt),
                        Breath.Metallic.maxHit(with, burnt),
                        "$potion shield=$shield burnt=$burnt",
                    )
                }
            }
        }
        assertEquals(50, Breath.Metallic.maxHit(none, true))
        assertEquals(30, Breath.Metallic.maxHit(none, false))
        assertEquals(35, Breath.Metallic.maxHit(prayerAntifire, true))
        assertEquals(0, Breath.Metallic.maxHit(allAntifire, true))
    }

    @Test
    fun `king black dragon`() {
        assertEquals(
            listOf(65, 15, 20, 15, 50, 0, 0, 0, 5, 0, 0, 0),
            Breath.KingBlackDragon.maxHits(true),
        )
        assertEquals(
            listOf(50, 10, 15, 10, 50, 50, 10, 10, 15, 15, 10, 10),
            Breath.KingBlackDragonSpecial.maxHits(true),
        )
        // Not even super antifire stops the special breaths: the shield is needed.
        assertEquals(50, Breath.KingBlackDragonSpecial.maxHit(superAntifire, true))
        assertEquals(10, Breath.KingBlackDragonSpecial.maxHit(shieldSuper, true))
    }

    @Test
    fun elvarg() {
        assertEquals(listOf(70, 10, 55, 7, 55, 0, 7, 0, 40, 0, 4, 0), Breath.Elvarg.maxHits(true))
    }

    @Test
    fun `a drake's breath burns four times for 6 to 8`() {
        assertEquals(4, DrakeBreath.HITS)
        assertEquals(6..8, DrakeBreath.damage(none))
        // Protect from Magic does nothing, it is not a magic attack.
        assertEquals(6..8, DrakeBreath.damage(prayer))
    }

    @Test
    fun `either a shield or an antifire potion lowers a drake's breath to 4`() {
        assertEquals(0..4, DrakeBreath.damage(shield))
        assertEquals(0..4, DrakeBreath.damage(antifire))
        assertEquals(0..4, DrakeBreath.damage(shieldPrayer))
    }

    @Test
    fun `a shield with antifire, or super antifire alone, stops a drake's breath`() {
        assertEquals(0..0, DrakeBreath.damage(shieldAntifire))
        assertEquals(0..0, DrakeBreath.damage(superAntifire))
        assertEquals(0..0, DrakeBreath.damage(shieldSuper))
        assertEquals(0..0, DrakeBreath.damage(allAntifire))
    }

    @Test
    fun `a resisted breath is never stronger than a burning one`() {
        for (breath in Breath.entries) {
            for (protection in everything) {
                val burnt = breath.maxHit(protection, true)
                val resisted = breath.maxHit(protection, false)
                assertEquals(true, resisted <= burnt, "$breath $protection")
            }
        }
    }
}
