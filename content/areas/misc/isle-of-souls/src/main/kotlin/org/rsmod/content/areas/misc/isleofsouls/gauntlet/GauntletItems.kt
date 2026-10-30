package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

/**
 * Every item of one version of the Gauntlet. The two versions have the same items under different
 * ids (the Corrupted ones end in `_hm` in the cache) except for those they share, which are
 * [GauntletShared].
 */
interface GauntletItemSet {
    val sceptre: ObjType
    val axe: ObjType
    val pickaxe: ObjType
    val harpoon: ObjType
    val pestle: ObjType
    val teleportCrystal: ObjType
    val escapeCrystal: ObjType
    val shard: ObjType
    val dust: ObjType
    val spike: ObjType
    val bowstring: ObjType
    val orb: ObjType
    val frame: ObjType
    val grymLeaf: ObjType
    val linum: ObjType
    val ore: ObjType
    val bark: ObjType
    val vial: ObjType
    val comboFood: ObjType

    /** The three tiers (basic, attuned, perfected) of each piece of armour and weapon. */
    val helm: List<ObjType>
    val body: List<ObjType>
    val legs: List<ObjType>
    val halberd: List<ObjType>
    val staff: List<ObjType>
    val bow: List<ObjType>
}

/** What both versions use: water-filled vials, the potions and the paddlefish. */
object GauntletShared : ObjReferences() {
    val waterVial = find("gauntlet_vial_water")
    val grymPotion = find("gauntlet_potion_unfinished")

    /** Egniol potion (3), the potion a vial of grym potion and ten dust make. */
    val egniol = find("gauntlet_potion_3")
    val rawFood = find("gauntlet_raw_food")
    val burntFood = find("gauntlet_burnt_food")
    val food = find("gauntlet_food")
}

/** The Gauntlet. */
object GauntletRegularItems : ObjReferences(), GauntletItemSet {
    override val sceptre = find("gauntlet_sceptre")
    override val axe = find("gauntlet_axe")
    override val pickaxe = find("gauntlet_pickaxe")
    override val harpoon = find("gauntlet_harpoon")
    override val pestle = find("gauntlet_pestle")
    override val teleportCrystal = find("gauntlet_teleport_crystal")
    override val escapeCrystal = find("gauntlet_escape_crystal")
    override val shard = find("gauntlet_crystal_shard")
    override val dust = find("gauntlet_crystal_shard_crushed")
    override val spike = find("gauntlet_melee_component")
    override val bowstring = find("gauntlet_ranged_component")
    override val orb = find("gauntlet_magic_component")
    override val frame = find("gauntlet_generic_component")
    override val grymLeaf = find("gauntlet_herb")
    override val linum = find("gauntlet_fibre")
    override val ore = find("gauntlet_ore")
    override val bark = find("gauntlet_bark")
    override val vial = find("gauntlet_vial_empty")
    override val comboFood = find("gauntlet_combo_food")
    override val helm =
        listOf(find("gauntlet_helmet_t1"), find("gauntlet_helmet_t2"), find("gauntlet_helmet_t3"))
    override val body =
        listOf(
            find("gauntlet_chestplate_t1"),
            find("gauntlet_chestplate_t2"),
            find("gauntlet_chestplate_t3"),
        )
    override val legs =
        listOf(
            find("gauntlet_platelegs_t1"),
            find("gauntlet_platelegs_t2"),
            find("gauntlet_platelegs_t3"),
        )
    override val halberd =
        listOf(find("gauntlet_melee_t1"), find("gauntlet_melee_t2"), find("gauntlet_melee_t3"))
    override val staff =
        listOf(find("gauntlet_magic_t1"), find("gauntlet_magic_t2"), find("gauntlet_magic_t3"))
    override val bow =
        listOf(find("gauntlet_ranged_t1"), find("gauntlet_ranged_t2"), find("gauntlet_ranged_t3"))
}

/** The Corrupted Gauntlet. */
object GauntletCorruptedItems : ObjReferences(), GauntletItemSet {
    override val sceptre = find("gauntlet_sceptre_hm")
    override val axe = find("gauntlet_axe_hm")
    override val pickaxe = find("gauntlet_pickaxe_hm")
    override val harpoon = find("gauntlet_harpoon_hm")
    override val pestle = find("gauntlet_pestle")
    override val teleportCrystal = find("gauntlet_teleport_crystal_hm")
    override val escapeCrystal = find("gauntlet_escape_crystal_hm")
    override val shard = find("gauntlet_crystal_shard_hm")
    override val dust = find("gauntlet_crystal_shard_crushed_hm")
    override val spike = find("gauntlet_melee_component_hm")
    override val bowstring = find("gauntlet_ranged_component_hm")
    override val orb = find("gauntlet_magic_component_hm")
    override val frame = find("gauntlet_generic_component_hm")
    override val grymLeaf = find("gauntlet_herb_hm")
    override val linum = find("gauntlet_fibre_hm")
    override val ore = find("gauntlet_ore_hm")
    override val bark = find("gauntlet_bark_hm")
    override val vial = find("gauntlet_vial_empty_hm")
    override val comboFood = find("gauntlet_combo_food_hm")
    override val helm =
        listOf(
            find("gauntlet_helmet_t1_hm"),
            find("gauntlet_helmet_t2_hm"),
            find("gauntlet_helmet_t3_hm"),
        )
    override val body =
        listOf(
            find("gauntlet_chestplate_t1_hm"),
            find("gauntlet_chestplate_t2_hm"),
            find("gauntlet_chestplate_t3_hm"),
        )
    override val legs =
        listOf(
            find("gauntlet_platelegs_t1_hm"),
            find("gauntlet_platelegs_t2_hm"),
            find("gauntlet_platelegs_t3_hm"),
        )
    override val halberd =
        listOf(
            find("gauntlet_melee_t1_hm"),
            find("gauntlet_melee_t2_hm"),
            find("gauntlet_melee_t3_hm"),
        )
    override val staff =
        listOf(
            find("gauntlet_magic_t1_hm"),
            find("gauntlet_magic_t2_hm"),
            find("gauntlet_magic_t3_hm"),
        )
    override val bow =
        listOf(
            find("gauntlet_ranged_t1_hm"),
            find("gauntlet_ranged_t2_hm"),
            find("gauntlet_ranged_t3_hm"),
        )
}
