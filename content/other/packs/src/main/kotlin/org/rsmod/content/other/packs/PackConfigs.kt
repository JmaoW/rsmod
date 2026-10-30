package org.rsmod.content.other.packs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

/** A pack that opens into [count] of [contents]. [found] is the text the player sees on opening. */
class ItemPack(val pack: ObjType, val contents: ObjType, val count: Int, val found: String)

/**
 * The 32 core packs (`pack_*`). Each examine text says "A pack containing 100 ..." and the cache
 * stores no amount, so every pack gives 100.
 */
object PackObjs : ObjReferences() {
    val all: List<ItemPack> =
        listOf(
            ItemPack(find("pack_feather"), find("feather"), 100, "100 feathers"),
            ItemPack(
                find("pack_fishing_bait"),
                find("fishing_bait"),
                100,
                "100 sets of fishing bait",
            ),
            ItemPack(find("pack_vial_empty"), find("vial_empty"), 100, "100 empty vials"),
            ItemPack(find("pack_vial_water"), find("vial_water"), 100, "100 vials of water"),
            ItemPack(
                find("pack_slayer_broad_arrowhead"),
                find("slayer_broad_arrowhead"),
                100,
                "100 broad arrowheads",
            ),
            ItemPack(
                find("pack_slayer_broad_bolt_unfinished"),
                find("slayer_broad_bolt_unfinished"),
                100,
                "100 unfinished broad bolts",
            ),
            ItemPack(find("pack_softclay"), find("softclay"), 100, "100 pieces of soft clay"),
            ItemPack(find("pack_softclay_guild"), find("softclay"), 100, "100 pieces of soft clay"),
            ItemPack(find("pack_amylase"), find("amylase"), 100, "100 amylase crystals"),
            ItemPack(find("pack_airrune"), find("airrune"), 100, "100 air runes"),
            ItemPack(find("pack_waterrune"), find("waterrune"), 100, "100 water runes"),
            ItemPack(find("pack_earthrune"), find("earthrune"), 100, "100 earth runes"),
            ItemPack(find("pack_firerune"), find("firerune"), 100, "100 fire runes"),
            ItemPack(find("pack_mindrune"), find("mindrune"), 100, "100 mind runes"),
            ItemPack(find("pack_chaosrune"), find("chaosrune"), 100, "100 chaos runes"),
            ItemPack(find("pack_essencerune"), find("blankrune_high"), 100, "100 pure essence"),
            ItemPack(find("pack_airrune_tzhaar"), find("airrune"), 100, "100 air runes"),
            ItemPack(find("pack_waterrune_tzhaar"), find("waterrune"), 100, "100 water runes"),
            ItemPack(find("pack_earthrune_tzhaar"), find("earthrune"), 100, "100 earth runes"),
            ItemPack(find("pack_firerune_tzhaar"), find("firerune"), 100, "100 fire runes"),
            ItemPack(
                find("pack_ojibway_bird_snare"),
                find("hunting_ojibway_bird_snare"),
                100,
                "100 bird snares",
            ),
            ItemPack(find("pack_box_trap"), find("hunting_box_trap"), 100, "100 box traps"),
            ItemPack(find("pack_magic_imp_box"), find("magic_imp_box"), 100, "100 magic boxes"),
            ItemPack(find("pack_olive_oil"), find("oliveoil4"), 100, "100 vials of olive oil"),
            ItemPack(find("pack_eye_newt"), find("eye_of_newt"), 100, "100 eyes of newt"),
            ItemPack(
                find("pack_bone_bolt"),
                find("dttd_bone_crossbow_bolt"),
                100,
                "100 bone bolts",
            ),
            ItemPack(
                find("pack_plant_pot_compost"),
                find("plantpot_compost"),
                100,
                "100 filled plant pots",
            ),
            ItemPack(find("pack_sack"), find("sack_empty"), 100, "100 empty sacks"),
            ItemPack(find("pack_basket"), find("basket_empty"), 100, "100 baskets"),
            ItemPack(find("pack_compost"), find("bucket_compost"), 100, "100 buckets of compost"),
            ItemPack(find("pack_jug_empty"), find("jug_empty"), 100, "100 empty jugs"),
            ItemPack(find("pack_bucket"), find("bucket_empty"), 100, "100 empty buckets"),
        )
}
