package org.rsmod.content.skills.prayer.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

internal object PrayerBoneObjs : ObjReferences() {
    val bones_burnt = find("bones_burnt")
    val wolf_bones = find("wolf_bones")
    val bat_bones = find("bat_bones")
    val big_bones = find("big_bones")
    val zogre_bones = find("zogre_bones")
    val babydragon_bones = find("babydragon_bones")
    val wyrm_bones = find("wyrm_bones")
    val dragon_bones = find("dragon_bones")
    val wyvern_bones = find("wyvern_bones")
    val drake_bones = find("drake_bones")
    val fayrg_bones = find("zogre_ancestral_bones_fayg")
    val lava_dragon_bones = find("lava_dragon_bones")
    val raurg_bones = find("zogre_ancestral_bones_raurg")
    val hydra_bones = find("hydra_bones")
    val dagannoth_bones = find("dagannoth_king_bones")
    val ourg_bones = find("zogre_ancestral_bones_ourg")
    val superior_dragon_bones = find("dragon_bones_superior")
}

/** A bone that can be buried or offered, and the Prayer experience it gives when buried. */
class PrayerBone(val obj: ObjType, val xp: Double)

object PrayerBones {
    /** Every bone, with the burying experience from the wiki. */
    val all: List<PrayerBone> =
        listOf(
            PrayerBone(objs.bones, 4.5),
            PrayerBone(PrayerBoneObjs.bones_burnt, 4.5),
            PrayerBone(PrayerBoneObjs.wolf_bones, 4.5),
            PrayerBone(PrayerBoneObjs.bat_bones, 5.3),
            PrayerBone(PrayerBoneObjs.big_bones, 15.0),
            PrayerBone(PrayerBoneObjs.zogre_bones, 22.5),
            PrayerBone(PrayerBoneObjs.babydragon_bones, 30.0),
            PrayerBone(PrayerBoneObjs.wyrm_bones, 50.0),
            PrayerBone(PrayerBoneObjs.dragon_bones, 72.0),
            PrayerBone(PrayerBoneObjs.wyvern_bones, 72.0),
            PrayerBone(PrayerBoneObjs.drake_bones, 80.0),
            PrayerBone(PrayerBoneObjs.fayrg_bones, 84.0),
            PrayerBone(PrayerBoneObjs.lava_dragon_bones, 85.0),
            PrayerBone(PrayerBoneObjs.raurg_bones, 96.0),
            PrayerBone(PrayerBoneObjs.hydra_bones, 110.0),
            PrayerBone(PrayerBoneObjs.dagannoth_bones, 125.0),
            PrayerBone(PrayerBoneObjs.ourg_bones, 140.0),
            PrayerBone(PrayerBoneObjs.superior_dragon_bones, 150.0),
        )

    private val byObj: Map<Int, PrayerBone> by lazy { all.associateBy { it.obj.id } }

    /** The bone [obj] is, or `null` if it is not a bone. */
    fun forObj(obj: ObjType): PrayerBone? = byObj[obj.id]

    /** The bone [obj] is, or `null` if it is not a bone. */
    fun forObj(obj: Int): PrayerBone? = byObj[obj]
}
