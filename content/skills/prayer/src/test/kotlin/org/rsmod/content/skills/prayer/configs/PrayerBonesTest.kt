package org.rsmod.content.skills.prayer.configs

import java.util.Collections
import java.util.IdentityHashMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs

class PrayerBonesTest {
    @Test
    fun `experience matches the wiki`() {
        val expected =
            listOf(
                objs.bones to 4.5,
                PrayerBoneObjs.bones_burnt to 4.5,
                PrayerBoneObjs.wolf_bones to 4.5,
                PrayerBoneObjs.bat_bones to 5.3,
                PrayerBoneObjs.big_bones to 15.0,
                PrayerBoneObjs.zogre_bones to 22.5,
                PrayerBoneObjs.babydragon_bones to 30.0,
                PrayerBoneObjs.wyrm_bones to 50.0,
                PrayerBoneObjs.dragon_bones to 72.0,
                PrayerBoneObjs.wyvern_bones to 72.0,
                PrayerBoneObjs.drake_bones to 80.0,
                PrayerBoneObjs.fayrg_bones to 84.0,
                PrayerBoneObjs.lava_dragon_bones to 85.0,
                PrayerBoneObjs.raurg_bones to 96.0,
                PrayerBoneObjs.hydra_bones to 110.0,
                PrayerBoneObjs.dagannoth_bones to 125.0,
                PrayerBoneObjs.ourg_bones to 140.0,
                PrayerBoneObjs.superior_dragon_bones to 150.0,
            )
        assertEquals(expected.size, PrayerBones.all.size)
        for ((obj, xp) in expected) {
            assertEquals(xp, PrayerBones.all.firstOrNull { it.obj === obj }?.xp, "xp for $obj")
        }
    }

    @Test
    fun `every bone is listed once`() {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        for (bone in PrayerBones.all) {
            assertTrue(seen.add(bone.obj), "duplicate bone ${bone.obj}")
        }
    }
}
