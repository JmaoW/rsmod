package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

/** Every item the reward chest can give. */
object GauntletRewardObjs : ObjReferences() {
    val acne_potion = find("acne_potion")
    val eye_of_newt = find("eye_of_newt")
    val trout = find("trout")
    val cod = find("cod")
    val firerune = find("firerune")
    val waterrune = find("waterrune")
    val airrune = find("airrune")
    val earthrune = find("earthrune")
    val mindrune = find("mindrune")
    val bodyrune = find("bodyrune")
    val deathrune = find("deathrune")
    val naturerune = find("naturerune")
    val chaosrune = find("chaosrune")
    val lawrune = find("lawrune")
    val cosmicrune = find("cosmicrune")
    val bloodrune = find("bloodrune")
    val maple_longbow = find("maple_longbow")
    val maple_shortbow = find("maple_shortbow")
    val mithril_arrow = find("mithril_arrow")
    val adamant_arrow = find("adamant_arrow")
    val rune_arrow = find("rune_arrow")
    val coins = find("coins")
    val mithril_platelegs = find("mithril_platelegs")
    val adamant_platelegs = find("adamant_platelegs")
    val rune_platelegs = find("rune_platelegs")
    val mithril_plateskirt = find("mithril_plateskirt")
    val adamant_plateskirt = find("adamant_plateskirt")
    val rune_plateskirt = find("rune_plateskirt")
    val rune_chainbody = find("rune_chainbody")
    val mithril_platebody = find("mithril_platebody")
    val adamant_platebody = find("adamant_platebody")
    val rune_platebody = find("rune_platebody")
    val mithril_full_helm = find("mithril_full_helm")
    val adamant_full_helm = find("adamant_full_helm")
    val rune_full_helm = find("rune_full_helm")
    val adamant_dagger = find("adamant_dagger")
    val adamant_pickaxe = find("adamant_pickaxe")
    val rune_pickaxe = find("rune_pickaxe")
    val adamant_scimitar = find("adamant_scimitar")
    val battlestaff = find("battlestaff")
    val mithril_mace = find("mithril_mace")
    val adamant_mace = find("adamant_mace")
    val uncut_diamond = find("uncut_diamond")
    val uncut_ruby = find("uncut_ruby")
    val uncut_emerald = find("uncut_emerald")
    val uncut_sapphire = find("uncut_sapphire")
    val cake = find("cake")
    val silver_bar = find("silver_bar")
    val rotten_tomato = find("rotten_tomato")
    val rune_halberd = find("rune_halberd")
    val dragon_halberd = find("dragon_halberd")
    val crystal_seed_old = find("crystal_seed_old")
    val dragon_arrow = find("dragon_arrow")
    val trail_elite_emote_exp1 = find("trail_elite_emote_exp1")
    val flier_prif = find("flier_prif")
    val gauntletpet = find("gauntletpet")
    val gauntletpet_corrupt = find("gauntletpet_corrupt")
    val gauntlet_crystalline_cape = find("gauntlet_crystalline_cape")
    val prif_armour_seed = find("prif_armour_seed")
    val prif_crystal_shard = find("prif_crystal_shard")
    val prif_weapon_seed_enhanced = find("prif_weapon_seed_enhanced")
}

/**
 * A line of a reward table. [weight] is its share of the table's total, [noted] gives it as a note
 * when it has a noted form.
 */
class Row(val obj: ObjType, val min: Int, val max: Int, val weight: Int, val noted: Boolean)

/**
 * The reward chest's tables (wiki, Reward Chest (The Gauntlet)). The weights are halves of the
 * wiki's x/24 so that the 0.5/24 lines are whole numbers. Generated from TSPS's copy of the tables;
 * the item names are the cache's.
 */
object GauntletRewardTables {
    val crystalline =
        listOf(
            Row(GauntletRewardObjs.battlestaff, 4, 8, 2, true),
            Row(GauntletRewardObjs.rune_full_helm, 2, 4, 2, true),
            Row(GauntletRewardObjs.rune_chainbody, 1, 2, 2, true),
            Row(GauntletRewardObjs.rune_platebody, 1, 2, 2, true),
            Row(GauntletRewardObjs.rune_platelegs, 1, 2, 2, true),
            Row(GauntletRewardObjs.rune_plateskirt, 1, 2, 2, true),
            Row(GauntletRewardObjs.rune_halberd, 1, 2, 2, true),
            Row(GauntletRewardObjs.rune_pickaxe, 1, 2, 2, true),
            Row(GauntletRewardObjs.dragon_halberd, 1, 1, 2, true),
            Row(GauntletRewardObjs.cosmicrune, 160, 240, 2, false),
            Row(GauntletRewardObjs.naturerune, 100, 140, 2, false),
            Row(GauntletRewardObjs.lawrune, 80, 140, 2, false),
            Row(GauntletRewardObjs.chaosrune, 180, 300, 2, false),
            Row(GauntletRewardObjs.deathrune, 100, 160, 2, false),
            Row(GauntletRewardObjs.bloodrune, 80, 140, 2, false),
            Row(GauntletRewardObjs.mithril_arrow, 800, 1200, 2, false),
            Row(GauntletRewardObjs.adamant_arrow, 400, 600, 2, false),
            Row(GauntletRewardObjs.rune_arrow, 200, 300, 2, false),
            Row(GauntletRewardObjs.dragon_arrow, 30, 85, 2, false),
            Row(GauntletRewardObjs.uncut_sapphire, 20, 60, 2, true),
            Row(GauntletRewardObjs.uncut_emerald, 10, 50, 2, true),
            Row(GauntletRewardObjs.uncut_ruby, 5, 30, 2, true),
            Row(GauntletRewardObjs.uncut_diamond, 3, 7, 2, true),
            Row(GauntletRewardObjs.coins, 20000, 80000, 2, false),
        )

    val corrupted =
        listOf(
            Row(GauntletRewardObjs.battlestaff, 8, 12, 2, true),
            Row(GauntletRewardObjs.rune_full_helm, 3, 5, 2, true),
            Row(GauntletRewardObjs.rune_chainbody, 2, 3, 2, true),
            Row(GauntletRewardObjs.rune_platebody, 2, 2, 2, true),
            Row(GauntletRewardObjs.rune_platelegs, 2, 3, 2, true),
            Row(GauntletRewardObjs.rune_plateskirt, 2, 3, 2, true),
            Row(GauntletRewardObjs.rune_halberd, 2, 3, 2, true),
            Row(GauntletRewardObjs.rune_pickaxe, 2, 3, 2, true),
            Row(GauntletRewardObjs.dragon_halberd, 1, 2, 1, true),
            Row(GauntletRewardObjs.cosmicrune, 175, 250, 2, false),
            Row(GauntletRewardObjs.naturerune, 125, 150, 2, false),
            Row(GauntletRewardObjs.lawrune, 100, 150, 2, false),
            Row(GauntletRewardObjs.chaosrune, 200, 350, 2, false),
            Row(GauntletRewardObjs.deathrune, 125, 175, 2, false),
            Row(GauntletRewardObjs.bloodrune, 100, 150, 2, false),
            Row(GauntletRewardObjs.mithril_arrow, 1000, 1500, 2, false),
            Row(GauntletRewardObjs.adamant_arrow, 500, 750, 2, false),
            Row(GauntletRewardObjs.rune_arrow, 250, 450, 2, false),
            Row(GauntletRewardObjs.dragon_arrow, 50, 100, 2, false),
            Row(GauntletRewardObjs.uncut_sapphire, 25, 65, 2, true),
            Row(GauntletRewardObjs.uncut_emerald, 15, 60, 2, true),
            Row(GauntletRewardObjs.uncut_ruby, 10, 40, 2, true),
            Row(GauntletRewardObjs.uncut_diamond, 5, 15, 2, true),
            Row(GauntletRewardObjs.coins, 75000, 150000, 3, false),
        )

    /** The incomplete-run table: each of its 27 lines is 1/27. */
    val incomplete =
        listOf(
            Row(GauntletRewardObjs.adamant_dagger, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_full_helm, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_mace, 2, 3, 1, true),
            Row(GauntletRewardObjs.adamant_pickaxe, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_platebody, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_platelegs, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_plateskirt, 1, 1, 1, false),
            Row(GauntletRewardObjs.adamant_scimitar, 1, 1, 1, false),
            Row(GauntletRewardObjs.maple_longbow, 7, 13, 1, true),
            Row(GauntletRewardObjs.maple_shortbow, 8, 11, 1, true),
            Row(GauntletRewardObjs.mithril_full_helm, 1, 1, 1, false),
            Row(GauntletRewardObjs.mithril_mace, 2, 5, 1, true),
            Row(GauntletRewardObjs.mithril_platebody, 1, 1, 1, false),
            Row(GauntletRewardObjs.mithril_platelegs, 1, 1, 1, false),
            Row(GauntletRewardObjs.mithril_plateskirt, 1, 1, 1, false),
            Row(GauntletRewardObjs.airrune, 200, 300, 1, false),
            Row(GauntletRewardObjs.bodyrune, 250, 350, 1, false),
            Row(GauntletRewardObjs.earthrune, 200, 300, 1, false),
            Row(GauntletRewardObjs.firerune, 200, 300, 1, false),
            Row(GauntletRewardObjs.mindrune, 300, 400, 1, false),
            Row(GauntletRewardObjs.waterrune, 200, 300, 1, false),
            Row(GauntletRewardObjs.cake, 10, 20, 1, true),
            Row(GauntletRewardObjs.cod, 75, 125, 1, true),
            Row(GauntletRewardObjs.trout, 50, 100, 1, true),
            Row(GauntletRewardObjs.eye_of_newt, 300, 500, 1, true),
            Row(GauntletRewardObjs.silver_bar, 15, 30, 1, true),
            Row(GauntletRewardObjs.uncut_sapphire, 1, 3, 1, true),
        )

    /** Iwan's flyer, potion, rotten tomato: each 1/3. */
    val junk =
        listOf(
            Row(GauntletRewardObjs.flier_prif, 1, 1, 1, false),
            Row(GauntletRewardObjs.acne_potion, 1, 1, 1, false),
            Row(GauntletRewardObjs.rotten_tomato, 1, 1, 1, false),
        )
}
