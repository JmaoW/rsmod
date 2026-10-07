package org.rsmod.content.skills.crafting.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.varp.VarpReferences

/** Everything crafting uses or makes. The names are the internal obj names from the cache. */
object CraftingObjs : ObjReferences() {
    // Tools and small parts.
    val needle = find("needle")
    val thread = find("thread")
    val chisel = find("chisel")
    val hammer = find("hammer")
    val glassblowing_pipe = find("glassblowingpipe")
    val steel_studs = find("studs")
    val bronze_nails = find("nails_bronze")
    val iron_nails = find("nails_iron")
    val black_nails = find("nails_black")
    val steel_nails = find("nails")
    val mithril_nails = find("nails_mithril")
    val adamant_nails = find("nails_adamant")
    val rune_nails = find("nails_rune")
    val kebbit_claws = find("huntingbeast_claws")

    // Raw hides and the leathers they are tanned into.
    val cow_hide = find("cow_hide")
    val snake_hide = find("village_snake_hide")
    val swamp_snake_hide = find("templetrek_swamp_snake_hide")
    val yak_hide = find("yak_hide")
    val green_dragonhide = find("dragonhide_green")
    val blue_dragonhide = find("dragonhide_blue")
    val red_dragonhide = find("dragonhide_red")
    val black_dragonhide = find("dragonhide_black")

    val leather = find("leather")
    val hard_leather = find("hard_leather")
    val snakeskin = find("village_snake_skin")
    val cured_yak_hide = find("yak_hide_cured")
    val green_dragon_leather = find("dragon_leather")
    val blue_dragon_leather = find("dragon_leather_blue")
    val red_dragon_leather = find("dragon_leather_red")
    val black_dragon_leather = find("dragon_leather_black")

    // Leather armour.
    val leather_gloves = find("leather_gloves")
    val leather_boots = find("leather_boots")
    val leather_cowl = find("leather_cowl")
    val leather_vambraces = find("leather_vambraces")
    val leather_body = find("leather_armour")
    val leather_chaps = find("leather_chaps")
    val hardleather_body = find("hardleather_body")
    val coif = find("coif")
    val studded_body = find("studded_body")
    val studded_chaps = find("studded_chaps")
    val spiky_vambraces = find("spiked_vambraces")
    val green_spiky_vambraces = find("hunting_green_spiked_vambraces")
    val blue_spiky_vambraces = find("hunting_blue_spiked_vambraces")
    val red_spiky_vambraces = find("hunting_red_spiked_vambraces")
    val black_spiky_vambraces = find("hunting_black_spiked_vambraces")
    val hard_leather_shield = find("leather_shield")

    // Dragonhide armour.
    val green_dhide_vambraces = find("dragon_vambraces")
    val green_dhide_chaps = find("dragonhide_chaps")
    val green_dhide_body = find("dragonhide_body")
    val blue_dhide_vambraces = find("blue_dragon_vambraces")
    val blue_dhide_chaps = find("blue_dragonhide_chaps")
    val blue_dhide_body = find("blue_dragonhide_body")
    val red_dhide_vambraces = find("red_dragon_vambraces")
    val red_dhide_chaps = find("red_dragonhide_chaps")
    val red_dhide_body = find("red_dragonhide_body")
    val black_dhide_vambraces = find("black_dragon_vambraces")
    val black_dhide_chaps = find("black_dragonhide_chaps")
    val black_dhide_body = find("black_dragonhide_body")
    val green_dhide_shield = find("green_dhide_shield")
    val blue_dhide_shield = find("blue_dhide_shield")
    val red_dhide_shield = find("red_dhide_shield")
    val black_dhide_shield = find("black_dhide_shield")

    // Wooden shields the shields are built on.
    val oak_shield = find("oak_shield")
    val willow_shield = find("willow_shield")
    val maple_shield = find("maple_shield")
    val yew_shield = find("yew_shield")
    val magic_shield = find("magic_shield")
    val redwood_shield = find("redwood_shield")

    // Snakeskin and the other armour sets.
    val snakeskin_body = find("snakeskin_body")
    val snakeskin_chaps = find("snakeskin_chaps")
    val snakeskin_bandana = find("snakeskin_bandana")
    val snakeskin_boots = find("snakeskin_boots")
    val snakeskin_vambraces = find("snakeskin_vambraces")
    val snakeskin_shield = find("snakeskin_shield")
    val tribal_mask_poison = find("broodoomask_poison")
    val tribal_mask_disease = find("broodoomask_disease")
    val tribal_mask_combat = find("broodoomask_combat")
    val broodoo_shield_poison = find("broodoo_poisonshield_10")
    val broodoo_shield_disease = find("broodoo_diseaseshield_10")
    val broodoo_shield_combat = find("broodoo_combatshield_10")

    val yak_hide_legs = find("yak_hide_armour_greaves")
    val yak_hide_top = find("yak_hide_armour_body")

    val fresh_crab_claw = find("hundred_pirate_crab_shell_claw")
    val crab_claw = find("hundred_pirate_crab_shell_gauntlet")
    val fresh_crab_shell = find("hundred_pirate_crab_shell_head")
    val crab_helmet = find("hundred_pirate_crab_shell_helm")

    val xerician_fabric = find("xeric_fabric")
    val xerician_hat = find("xeric_hat")
    val xerician_top = find("xeric_top")
    val xerician_robe = find("xeric_robe")

    val bark = find("hollow_bark")
    val fine_cloth = find("fine_cloth")
    val splitbark_helm = find("splitbark_helm")
    val splitbark_body = find("splitbark_body")
    val splitbark_legs = find("splitbark_legs")
    val splitbark_gauntlets = find("splitbark_gauntlets")
    val splitbark_boots = find("splitbark_greaves")

    val mixed_hide_base = find("hg_mixedhide_base")
    val jaguar_fur = find("varlamore_jaguar_fur")
    val sunlight_antelope_fur = find("hunting_antelopesun_fur")
    val fox_fur = find("hunting_fennecfox_fur")
    val mixed_hide_cape = find("hide_cape")
    val mixed_hide_boots = find("hide_boots")
    val mixed_hide_legs = find("hide_legs")
    val mixed_hide_top = find("hide_top")

    val hueycoatl_hide = find("huey_hide")
    val hueycoatl_vambraces = find("huey_vambraces")
    val hueycoatl_coif = find("huey_coif")
    val hueycoatl_chaps = find("huey_chaps")
    val hueycoatl_body = find("huey_body")

    // Snelms.
    val blamish_red_shell_round = find("shellround_red+black")
    val blamish_red_shell_pointed = find("shellpoint_red+black")
    val blamish_bark_shell = find("shellround_orange")
    val blamish_blue_shell_round = find("shellround_blue")
    val blamish_blue_shell_pointed = find("shellpoint_blue")
    val blamish_myre_shell_round = find("shellround_swamp")
    val blamish_myre_shell_pointed = find("shellpoint_swamp")
    val blamish_ochre_shell_round = find("shellround_yellow")
    val blamish_ochre_shell_pointed = find("shellpoint_yellow")
    val red_snelm_round = find("snelm_round_red+black")
    val red_snelm_pointed = find("snelm_point_red+black")
    val bark_snelm = find("snelm_round_orange")
    val blue_snelm_round = find("snelm_round_blue")
    val blue_snelm_pointed = find("snelm_point_blue")
    val myre_snelm_round = find("snelm_round_swamp")
    val myre_snelm_pointed = find("snelm_point_swamp")
    val ochre_snelm_round = find("snelm_round_yellow")
    val ochre_snelm_pointed = find("snelm_point_yellow")

    // Battlestaves.
    val battlestaff = find("battlestaff")
    val water_orb = find("water_orb")
    val earth_orb = find("earth_orb")
    val fire_orb = find("fire_orb")
    val air_orb = find("air_orb")
    val water_battlestaff = find("water_battlestaff")
    val earth_battlestaff = find("earth_battlestaff")
    val fire_battlestaff = find("fire_battlestaff")
    val air_battlestaff = find("air_battlestaff")

    // Amethyst.
    val amethyst = find("amethyst")
    val amethyst_bolt_tips = find("xbows_bolt_tips_amethyst")
    val amethyst_arrowtips = find("amethyst_arrowheads")
    val amethyst_javelin_tips = find("amethyst_javelin_head")
    val amethyst_dart_tips = find("amethyst_dart_tip")

    // Spinning and weaving.
    val wool = find("wool")
    val ball_of_wool = find("ball_of_wool")
    val flax = find("flax")
    val bow_string = find("bow_string")
    val sinew = find("xbows_sinew")
    val crossbow_string = find("xbows_crossbow_string")
    val oak_roots = find("oak_roots")
    val willow_roots = find("willow_roots")
    val maple_roots = find("maple_roots")
    val yew_roots = find("yew_roots")
    val magic_roots = find("magic_roots")
    val magic_string = find("magic_string")
    val yak_hair = find("yak_hair")
    val rope = find("rope")
    val strip_of_cloth = find("regicide_cloth")
    val jute_fibre = find("jute_fibre")
    val empty_sack = find("sack_empty")
    val willow_branch = find("willow_branch")
    val basket = find("basket_empty")

    // Pottery.
    val clay = find("clay")
    val soft_clay = find("softclay")
    val bucket_of_water = find("bucket_water")
    val empty_bucket = find("bucket_empty")
    val jug_of_water = find("jug_water")
    val empty_jug = find("jug_empty")
    val bowl_of_water = find("bowl_water")
    val vial_of_water = find("vial_water")
    val unfired_pot = find("pot_unfired")
    val unfired_pie_dish = find("piedish_unfired")
    val unfired_bowl = find("bowl_unfired")
    val unfired_cup = find("cup_unfired")
    val unfired_plant_pot = find("plantpot_unfired")
    val unfired_pot_lid = find("potlid_unfired")
    val pot = find("pot_empty")
    val pie_dish = find("piedish")
    val bowl = find("bowl_empty")
    val cup = find("cup_empty")
    val plant_pot = find("plantpot_empty")
    val pot_lid = find("potlid")

    // Glass.
    val bucket_of_sand = find("bucket_sand")
    val soda_ash = find("soda_ash")
    val molten_glass = find("molten_glass")
    val beer_glass = find("beer_glass")
    val candle_lantern = find("candle_lantern_empty")
    val oil_lamp = find("oil_lamp_empty")
    val oil_lantern_frame = find("oil_lantern_frame")
    val oil_lantern = find("oil_lantern_empty")
    val vial = find("vial_empty")
    val fishbowl = find("fishbowl_empty")
    val unpowered_orb = find("stafforb")
    val lantern_lens = find("bullseye_lantern_lens")
    val empty_light_orb = find("dorgesh_lightbulb_nofilament")
    val cave_goblin_wire = find("dorgesh_wire")
    val light_orb = find("dorgesh_light_bulb")

    // Birdhouses and the clockwork they need.
    val clockwork = find("poh_clockwork_mechanism")
    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val willow_logs = find("willow_logs")
    val teak_logs = find("teak_logs")
    val maple_logs = find("maple_logs")
    val mahogany_logs = find("mahogany_logs")
    val yew_logs = find("yew_logs")
    val magic_logs = find("magic_logs")
    val redwood_logs = find("redwood_logs")
    val bird_house = find("birdhouse_normal")
    val oak_bird_house = find("birdhouse_oak")
    val willow_bird_house = find("birdhouse_willow")
    val teak_bird_house = find("birdhouse_teak")
    val maple_bird_house = find("birdhouse_maple")
    val mahogany_bird_house = find("birdhouse_mahogany")
    val yew_bird_house = find("birdhouse_yew")
    val magic_bird_house = find("birdhouse_magic")
    val redwood_bird_house = find("birdhouse_redwood")

    // Gems.
    val uncut_opal = find("uncut_opal")
    val uncut_jade = find("uncut_jade")
    val uncut_red_topaz = find("uncut_red_topaz")
    val uncut_sapphire = find("uncut_sapphire")
    val uncut_emerald = find("uncut_emerald")
    val uncut_ruby = find("uncut_ruby")
    val uncut_diamond = find("uncut_diamond")
    val uncut_dragonstone = find("uncut_dragonstone")
    val uncut_onyx = find("uncut_onyx")
    val uncut_zenyte = find("uncut_zenyte")
    val opal = find("opal")
    val jade = find("jade")
    val red_topaz = find("red_topaz")
    val sapphire = find("sapphire")
    val emerald = find("emerald")
    val ruby = find("ruby")
    val diamond = find("diamond")
    val dragonstone = find("dragonstone")
    val onyx = find("onyx")
    val zenyte = find("zenyte")
    val crushed_gem = find("crushed_gemstone")

    // Jewellery.
    val gold_bar = find("gold_bar")
    val silver_bar = find("silver_bar")
    val ring_mould = find("ring_mould")
    val necklace_mould = find("necklace_mould")
    val amulet_mould = find("amulet_mould")
    val bracelet_mould = find("jewl_bracelet_mould")
    val holy_mould = find("holy_symbol_mould")
    val unholy_mould = find("unholy_symbol_mould")
    val sickle_mould = find("sickle_mould")
    val bolt_mould = find("xbows_silver_bolt_mould")
    val tiara_mould = find("tiara_mould")
    val slayer_gem = find("slayer_gem")
    val eternal_gem = find("slayer_eternal_gem")
    val slayer_ring = find("slayer_ring_8")
    val eternal_slayer_ring = find("slayer_ring_eternal")

    val gold_ring = find("gold_ring")
    val sapphire_ring = find("sapphire_ring")
    val emerald_ring = find("emerald_ring")
    val ruby_ring = find("ruby_ring")
    val diamond_ring = find("diamond_ring")
    val dragonstone_ring = find("dragonstone_ring")
    val onyx_ring = find("onyx_ring")
    val zenyte_ring = find("zenyte_ring")
    val opal_ring = find("opal_ring")
    val jade_ring = find("jade_ring")
    val topaz_ring = find("topaz_ring")

    val gold_necklace = find("gold_necklace")
    val sapphire_necklace = find("sapphire_necklace")
    val emerald_necklace = find("emerald_necklace")
    val ruby_necklace = find("ruby_necklace")
    val diamond_necklace = find("diamond_necklace")
    val dragonstone_necklace = find("dragonstone_necklace")
    val onyx_necklace = find("onyx_necklace")
    val zenyte_necklace = find("zenyte_necklace")
    val opal_necklace = find("opal_necklace")
    val jade_necklace = find("jade_necklace")
    val topaz_necklace = find("topaz_necklace")

    val gold_amulet_u = find("unstrung_gold_amulet")
    val sapphire_amulet_u = find("unstrung_sapphire_amulet")
    val emerald_amulet_u = find("unstrung_emerald_amulet")
    val ruby_amulet_u = find("unstrung_ruby_amulet")
    val diamond_amulet_u = find("unstrung_diamond_amulet")
    val dragonstone_amulet_u = find("unstrung_dragonstone_amulet")
    val onyx_amulet_u = find("unstrung_onyx_amulet")
    val zenyte_amulet_u = find("unstrung_zenyte_amulet")
    val opal_amulet_u = find("unstrung_opal_amulet")
    val jade_amulet_u = find("unstrung_jade_amulet")
    val topaz_amulet_u = find("unstrung_topaz_amulet")

    val gold_amulet = find("strung_gold_amulet")
    val sapphire_amulet = find("strung_sapphire_amulet")
    val emerald_amulet = find("strung_emerald_amulet")
    val ruby_amulet = find("strung_ruby_amulet")
    val diamond_amulet = find("strung_diamond_amulet")
    val dragonstone_amulet = find("strung_dragonstone_amulet")
    val onyx_amulet = find("strung_onyx_amulet")
    val zenyte_amulet = find("zenyte_amulet")
    val opal_amulet = find("strung_opal_amulet")
    val jade_amulet = find("strung_jade_amulet")
    val topaz_amulet = find("strung_topaz_amulet")

    val gold_bracelet = find("jewl_gold_bracelet")
    val sapphire_bracelet = find("jewl_sapphire_bracelet")
    val emerald_bracelet = find("jewl_emerald_bracelet")
    val ruby_bracelet = find("jewl_ruby_bracelet")
    val diamond_bracelet = find("jewl_diamond_bracelet")
    val dragonstone_bracelet = find("jewl_dragonstone_bracelet")
    val onyx_bracelet = find("jewl_onyx_bracelet")
    val zenyte_bracelet = find("zenyte_bracelet")
    val opal_bracelet = find("opal_bracelet")
    val jade_bracelet = find("jade_bracelet")
    val topaz_bracelet = find("topaz_bracelet")

    val unstrung_symbol = find("nostringstar")
    val unstrung_emblem = find("nostringsnake")
    val silver_sickle = find("silver_sickle")
    val silver_bolts_unfeathered = find("xbows_crossbow_bolts_silver_unfeathered")
    val tiara = find("tiara")

    val coins = find("coins")
}

internal object CraftingSeqs : SeqReferences() {
    val leatherwork = find("human_leather_crafting")
    val cut_dragonstone = find("human_dragonstonecutting")
    val cut_diamond = find("human_diamondcutting")
    val cut_ruby = find("human_rubycutting")
    val cut_sapphire = find("human_sapphirecutting")
    val cut_emerald = find("human_emeraldcutting")
    val cut_opal = find("human_opalcutting")
    val cut_jade = find("human_jadecutting")
    val cut_red_topaz = find("human_redtopazcutting")
    val cut_shell = find("human_snailshellcutting")
    val potter_wheel = find("human_potterywheel")
    val glassblowing = find("human_glassblowing")
    val spinning_wheel = find("human_spinningwheel")
    val furnace = find("human_furnace")
    val oven = find("human_cooking")
}

internal object CraftingQueues : QueueReferences() {
    /** Fires when the next action of a crafting job is ready to be made. */
    val make = find("crafting_make")
}

/** Server-kept copy of the quantity the player picked on the jewellery interfaces. */
internal object CraftingVarps : VarpReferences() {
    val make_x = find("makexcrafting")
}

internal object CraftingInterfaces : InterfaceReferences() {
    val gold = find("crafting_gold")
    val silver = find("silver_crafting")
    val tanner = find("tanner")
}

/** The product buttons of the gold jewellery interface. */
object GoldComponents : ComponentReferences() {
    val gold_ring = find("crafting_gold:gold_ring")
    val sapphire_ring = find("crafting_gold:sapphire_ring")
    val emerald_ring = find("crafting_gold:emerald_ring")
    val ruby_ring = find("crafting_gold:ruby_ring")
    val diamond_ring = find("crafting_gold:diamond_ring")
    val dragon_ring = find("crafting_gold:dragon_ring")
    val onyx_ring = find("crafting_gold:onyx_ring")
    val zenyte_ring = find("crafting_gold:zenyte_ring")
    val slayer_ring = find("crafting_gold:slayer_ring")

    val gold_necklace = find("crafting_gold:gold_necklace")
    val sapphire_necklace = find("crafting_gold:sapphire_necklace")
    val emerald_necklace = find("crafting_gold:emerald_necklace")
    val ruby_necklace = find("crafting_gold:ruby_necklace")
    val diamond_necklace = find("crafting_gold:diamond_necklace")
    val dragon_necklace = find("crafting_gold:dragon_necklace")
    val onyx_necklace = find("crafting_gold:onyx_necklace")
    val zenyte_necklace = find("crafting_gold:zenyte_necklace")

    val gold_amulet = find("crafting_gold:gold_amulet")
    val sapphire_amulet = find("crafting_gold:sapphire_amulet")
    val emerald_amulet = find("crafting_gold:emerald_amulet")
    val ruby_amulet = find("crafting_gold:ruby_amulet")
    val diamond_amulet = find("crafting_gold:diamond_amulet")
    val dragon_amulet = find("crafting_gold:dragon_amulet")
    val onyx_amulet = find("crafting_gold:onyx_amulet")
    val zenyte_amulet = find("crafting_gold:zenyte_amulet")

    val gold_bracelet = find("crafting_gold:gold_bracelet")
    val sapphire_bracelet = find("crafting_gold:sapphire_bracelet")
    val emerald_bracelet = find("crafting_gold:emerald_bracelet")
    val ruby_bracelet = find("crafting_gold:ruby_bracelet")
    val diamond_bracelet = find("crafting_gold:diamond_bracelet")
    val dragon_bracelet = find("crafting_gold:dragon_bracelet")
    val onyx_bracelet = find("crafting_gold:onyx_bracelet")
    val zenyte_bracelet = find("crafting_gold:zenyte_bracelet")

    val make_1 = find("crafting_gold:make_1")
    val make_5 = find("crafting_gold:make_5")
    val make_10 = find("crafting_gold:make_10")
    val make_x = find("crafting_gold:make_x")
    val make_all = find("crafting_gold:make_all")
}

/** The product buttons of the silver jewellery interface. */
object SilverComponents : ComponentReferences() {
    val opal_ring = find("silver_crafting:opal_ring")
    val jade_ring = find("silver_crafting:jade_ring")
    val topaz_ring = find("silver_crafting:topaz_ring")
    val opal_necklace = find("silver_crafting:opal_necklace")
    val jade_necklace = find("silver_crafting:jade_necklace")
    val topaz_necklace = find("silver_crafting:topaz_necklace")
    val opal_amulet = find("silver_crafting:opal_amulet")
    val jade_amulet = find("silver_crafting:jade_amulet")
    val topaz_amulet = find("silver_crafting:topaz_amulet")
    val opal_bracelet = find("silver_crafting:opal_bracelet")
    val jade_bracelet = find("silver_crafting:jade_bracelet")
    val topaz_bracelet = find("silver_crafting:topaz_bracelet")
    val holy_symbol = find("silver_crafting:holy_symbol")
    val unholy_symbol = find("silver_crafting:unholy_symbol")
    val sickle = find("silver_crafting:sickle")
    val crossbow_bolt = find("silver_crafting:crossbow_bolt")
    val tiara = find("silver_crafting:tiara")

    val make_1 = find("silver_crafting:make_1")
    val make_5 = find("silver_crafting:make_5")
    val make_10 = find("silver_crafting:make_10")
    val make_x = find("silver_crafting:make_x")
    val make_all = find("silver_crafting:make_all")
}

/**
 * The tanner interface (the old-style one with a fixed layout). Slot `a` to `h` has a hide picture
 * (`model`), a name (`text`), a price, and four buttons: tan `1`, `5`, `x` and `all`.
 */
object TannerComponents : ComponentReferences() {
    private val letters = "abcdefgh"

    private fun slots(suffix: String) = letters.map { find("tanner:tanning_${it}_$suffix") }

    /** The item pictures, one per slot. */
    val models = slots("model")

    /** The hide's name label above each picture. */
    val names = slots("text")

    /** The price label below each picture. */
    val costs = slots("price")

    val tan_all = slots("all")
    val tan_x = slots("x")
    val tan_5 = slots("5")
    val tan_1 = slots("1")
}
