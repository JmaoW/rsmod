package org.rsmod.content.areas.misc.isleofsouls.zulrah

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Cloud
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Magic
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Melee
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Mixed
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Orb
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahActionKind.Ranged
import org.rsmod.map.CoordGrid

/** Checks the rotations against the wiki's strategy page (as written out in the plan). */
class ZulrahRotationsTest {
    /** "C g" style codes: spot letter and form letter. */
    private fun ZulrahRotation.shape(): List<String> =
        phases.map { it.spot.name.first().toString() + it.form.name.first().lowercase() }

    private fun ZulrahPhase.kinds(): List<ZulrahActionKind> = actions.map { it.kind }

    private fun tile(x: Int, z: Int) = CoordGrid(x, z, 0)

    @Test
    fun `there are four rotations`() {
        assertEquals(4, ZulrahRotations.all.size)
        assertEquals(4, ZulrahRotations.all.map { it.name }.toSet().size)
    }

    @Test
    fun `every rotation starts with green in the centre and four cloud barrages`() {
        for (rotation in ZulrahRotations.all) {
            val first = rotation.phases.first()
            assertEquals(ZulrahSpot.Centre, first.spot, rotation.name)
            assertEquals(ZulrahForm.Green, first.form, rotation.name)
            assertEquals(List(4) { Cloud }, first.kinds(), rotation.name)
        }
    }

    @Test
    fun `every rotation ends with five ranged shots and the same four barrages`() {
        for (rotation in ZulrahRotations.all) {
            val first = rotation.phases.first()
            val last = rotation.phases.last()
            assertEquals(ZulrahSpot.Centre, last.spot, rotation.name)
            assertEquals(ZulrahForm.Green, last.form, rotation.name)
            assertEquals(List(5) { Ranged } + List(4) { Cloud }, last.kinds(), rotation.name)
            // The closing barrages are the opening ones: they count as the next rotation's first.
            assertEquals(first.actions, last.actions.takeLast(4), rotation.name)
        }
    }

    @Test
    fun `the phases of each rotation are where and what the wiki says`() {
        assertEquals(
            listOf("Cg", "Cr", "Cb", "Sg", "Cr", "Wb", "Sg", "Sb", "Wg", "Cr", "Cg"),
            ZulrahRotations.one.shape(),
        )
        assertEquals(
            listOf("Cg", "Cr", "Cb", "Wg", "Sb", "Cr", "Eg", "Sb", "Wg", "Cr", "Cg"),
            ZulrahRotations.two.shape(),
        )
        assertEquals(
            listOf("Cg", "Eg", "Cr", "Wb", "Sg", "Eb", "Cg", "Wg", "Cb", "Eg", "Cb", "Cg"),
            ZulrahRotations.three.shape(),
        )
        assertEquals(
            listOf("Cg", "Eb", "Sg", "Wb", "Cr", "Eg", "Sg", "Wb", "Cg", "Cb", "Eg", "Cb", "Cg"),
            ZulrahRotations.four.shape(),
        )
    }

    @Test
    fun `rotation one's phases do what the wiki lists`() {
        val p = ZulrahRotations.one.phases
        assertEquals(List(2) { Melee }, p[1].kinds())
        assertEquals(List(4) { Mixed }, p[2].kinds())
        assertEquals(
            List(5) { Ranged } + List(2) { Orb } + List(2) { Cloud } + List(2) { Orb },
            p[3].kinds(),
        )
        assertEquals(List(3) { Cloud } + List(4) { Orb }, p[6].kinds())
        // "5 magic and/or ranged, then an alternating series of 5 snakeling orbs and clouds."
        assertEquals(List(5) { Mixed } + listOf(Orb, Cloud, Orb, Cloud, Orb), p[7].kinds())
        // The Jad phase: 10 alternating ranged and magic starting with ranged, then 4 clouds.
        assertEquals(
            listOf(Ranged, Magic, Ranged, Magic, Ranged, Magic, Ranged, Magic, Ranged, Magic) +
                List(4) { Cloud },
            p[8].kinds(),
        )
        assertEquals(List(5) { Ranged } + List(4) { Cloud }, p[10].kinds())
    }

    @Test
    fun `rotation three and four end the way the wiki says`() {
        val three = ZulrahRotations.three.phases
        // The Jad phase starts with magic and has no clouds after it.
        assertEquals(10, three[9].actions.size)
        assertEquals(Magic, three[9].kinds().first())
        assertTrue(three[9].kinds().none { it == Cloud })
        assertEquals(List(4) { Orb }, three[10].kinds())

        val four = ZulrahRotations.four.phases
        assertEquals(8, four[10].actions.size)
        assertEquals(Magic, four[10].kinds().first())
        assertEquals(List(4) { Orb }, four[11].kinds())
        assertEquals(List(5) { Ranged } + List(4) { Cloud }, four[12].kinds())
    }

    @Test
    fun `rotation three's red phase alternates clouds and orbs and then swings twice`() {
        val red = ZulrahRotations.three.phases[2]
        assertEquals(listOf(Cloud, Orb, Cloud, Orb, Cloud, Orb, Melee, Melee), red.kinds())
    }

    @Test
    fun `only the green phases that alternate are Jad phases`() {
        val jads = ZulrahRotations.all.map { r -> r.phases.count { it.jad } }
        assertEquals(listOf(1, 1, 1, 1), jads)
        for (rotation in ZulrahRotations.all) {
            for (phase in rotation.phases.filter { it.jad }) {
                assertEquals(ZulrahForm.Green, phase.form)
            }
        }
    }

    @Test
    fun `red phases swing and never shoot`() {
        for (rotation in ZulrahRotations.all) {
            for (phase in rotation.phases.filter { it.form == ZulrahForm.Red }) {
                assertTrue(phase.kinds().any { it == Melee }, "${rotation.name}: $phase")
                assertFalse(phase.kinds().any { it == Ranged || it == Magic || it == Mixed })
            }
        }
    }

    @Test
    fun `only blue phases use the mixed attack and only red phases melee`() {
        for (rotation in ZulrahRotations.all) {
            for (phase in rotation.phases) {
                if (phase.kinds().any { it == Mixed }) {
                    assertEquals(ZulrahForm.Blue, phase.form, rotation.name)
                }
                if (phase.kinds().any { it == Melee }) {
                    assertEquals(ZulrahForm.Red, phase.form, rotation.name)
                }
            }
        }
    }

    // The tiles -------------------------------------------------------------------------------

    private fun ZulrahRotation.barrages() =
        phases.flatMap { it.actions }.filterIsInstance<ZulrahAction.Cloud>()

    private fun ZulrahRotation.orbs() =
        phases.flatMap { it.actions }.filterIsInstance<ZulrahAction.Orb>()

    @Test
    fun `every cloud lands on one of the fixed sites, two to a barrage`() {
        for (rotation in ZulrahRotations.all) {
            for (barrage in rotation.barrages()) {
                assertEquals(2, barrage.centres.toSet().size, "${rotation.name}: $barrage")
                for (centre in barrage.centres) {
                    assertTrue(centre in ZulrahCloudSites.all, "${rotation.name}: $centre")
                }
            }
        }
    }

    @Test
    fun `the sites step three tiles down each arm and along the south`() {
        assertEquals(10, ZulrahCloudSites.all.size)
        assertEquals(listOf(3070, 3073, 3076), ZulrahCloudSites.west.map { it.z })
        assertEquals(listOf(3072, 3075, 3078), ZulrahCloudSites.east.map { it.z })
        assertEquals(setOf(2263), ZulrahCloudSites.west.map { it.x }.toSet())
        assertEquals(setOf(2273), ZulrahCloudSites.east.map { it.x }.toSet())
        assertEquals(listOf(2266, 2269, 2272, 2272), ZulrahCloudSites.south.map { it.x })
    }

    @Test
    fun `orbs land on or beside the platform`() {
        for (rotation in ZulrahRotations.all) {
            for (orb in rotation.orbs()) {
                assertTrue(orb.tile.x in 2262..2274 && orb.tile.z in 3068..3078, "$orb")
            }
        }
    }

    @Test
    fun `the opening fill is the four barrages the port has`() {
        val fill = ZulrahRotations.one.phases.first().actions
        assertEquals(
            listOf(
                ZulrahAction.Cloud(tile(2269, 3069), tile(2272, 3070)),
                ZulrahAction.Cloud(tile(2266, 3069), tile(2263, 3070)),
                ZulrahAction.Cloud(tile(2273, 3072), tile(2273, 3075)),
                ZulrahAction.Cloud(tile(2263, 3073), tile(2263, 3076)),
            ),
            fill,
        )
        // Every rotation opens the same way.
        for (rotation in ZulrahRotations.all) {
            assertEquals(fill, rotation.phases.first().actions)
        }
    }

    @Test
    fun `rotation one's south phase puts its orbs and barrages where the data says`() {
        val south = ZulrahRotations.one.phases[3]
        assertEquals(
            listOf(
                ZulrahAction.Orb(tile(2263, 3076)),
                ZulrahAction.Orb(tile(2263, 3073)),
                ZulrahAction.Cloud(tile(2263, 3070), tile(2266, 3069)),
                ZulrahAction.Cloud(tile(2272, 3069), tile(2273, 3072)),
                ZulrahAction.Orb(tile(2273, 3075)),
                ZulrahAction.Orb(tile(2273, 3077)),
            ),
            south.actions.filter { it.kind == Orb || it.kind == Cloud },
        )
    }

    @Test
    fun `after a wrap a rotation carries on from its second phase`() {
        assertEquals(1, ZulrahRotations.FIRST_PHASE_AFTER_WRAP)
        for (rotation in ZulrahRotations.all) {
            // The second phase is never the opening fill again.
            assertFalse(rotation.phases[1].kinds() == rotation.phases[0].kinds(), rotation.name)
        }
    }
}
