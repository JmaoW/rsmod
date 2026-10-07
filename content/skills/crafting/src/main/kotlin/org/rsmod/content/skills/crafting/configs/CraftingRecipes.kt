package org.rsmod.content.skills.crafting.configs

import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/** How a recipe is made, which sets the speed and the wording of the chat message. */
enum class CraftingMethod(val ticks: Int) {
    /** Needle and thread on leather, hides, cloth and the like. */
    Sew(3),

    /** A chisel on a gem, shell or other raw material. */
    Chisel(2),

    /** Parts put together by hand or with a hammer (shields, staves, studs, birdhouses). */
    Assemble(3),

    /** A glassblowing pipe on molten glass. */
    Blow(3),

    /** Stringing an amulet. */
    String(2),

    /** Mixing clay with water. */
    Soften(2),

    /** At a spinning wheel. */
    Spin(3),

    /** At a loom. */
    Weave(3),

    /** Shaping soft clay on a potter's wheel. */
    Shape(3),

    /** Firing shaped clay in a pottery oven. */
    Fire(7),

    /** Heating sand and soda ash into molten glass in a furnace. */
    Melt(2),

    /** Pouring gold or silver into a mould in a furnace. */
    Mould(3),
}

/** Where a recipe is made when it is not made by using two items on each other. */
enum class CraftingStation {
    SpinningWheel,
    Loom,
    PotterWheel,
    PotteryOven,
    Furnace,
}

/** [count] of [obj], used up once for every action of a recipe. Any of [alternatives] will do. */
class CraftingInput(
    val obj: ObjType,
    val count: Int = 1,
    val alternatives: List<ObjType> = emptyList(),
) {
    /** Every obj that counts as this input, [obj] first. */
    val options: List<ObjType> = listOf(obj) + alternatives
}

/**
 * What can go wrong when cutting a gem (opal, jade and red topaz can be crushed). The chance to
 * succeed is `(base + (level - 1) * slope / 98) / 256`, capped at 1.
 */
class GemCrush(val base: Int, val slope: Int, val crushXp: Double) {
    /** The chance, out of 256, to cut the gem rather than crush it at [level]. */
    fun successOutOf256(level: Int): Int = minOf(256, base + ((level - 1) * slope) / SUCCESS_LEVELS)

    private companion object {
        const val SUCCESS_LEVELS = 98
    }
}

/**
 * One thing that is made by using two inventory objs on each other.
 *
 * Experience, [inputs] and [output] are for a single action.
 *
 * @property triggers the pairs of objs that, used on each other, offer this recipe. Empty for
 *   recipes made at a [station] or on the jewellery interfaces.
 * @property tools objs the player must carry that are not used up.
 * @property returns objs the player gets back, such as the empty bucket after mixing soft clay.
 * @property thread whether the action uses thread. A spool lasts [THREAD_USES] actions.
 * @property crack the chance, out of 256 at level 1 rising to certain at [CRACK_SAFE_LEVEL], that
 *   fired clay comes out whole. `false` for everything but pottery.
 */
class CraftingRecipe(
    val method: CraftingMethod,
    val triggers: List<Pair<ObjType, ObjType>>,
    val station: CraftingStation?,
    val tools: List<ObjType>,
    val inputs: List<CraftingInput>,
    val returns: List<ObjType>,
    val product: ObjType,
    val output: Int,
    val level: Int,
    val xp: Double,
    val thread: Boolean,
    val crush: GemCrush?,
    val crack: Boolean,
    internal val anim: SeqType?,
) {
    /** The chance, out of 256, that this recipe's clay is fired whole at [level]. */
    fun fireSuccessOutOf256(level: Int): Int =
        if (!crack) 256 else FIRE_SUCCESS[(level - 1).coerceIn(0, FIRE_SUCCESS.size - 1)]

    companion object {
        /** How many actions one spool of thread lasts. */
        const val THREAD_USES = 5

        /** From this Crafting level on, clay never cracks in the oven. */
        const val CRACK_SAFE_LEVEL = 13

        /** The wiki's chance, out of 256, that clay survives the oven at levels 1 to 13. */
        private val FIRE_SUCCESS =
            intArrayOf(181, 187, 194, 200, 206, 213, 219, 225, 232, 238, 244, 251, 256)
    }
}

/**
 * Levels, experience and materials are from the Old School RuneScape wiki. Left out on purpose:
 * - hemp, cotton and linen yarn and their cloth (the cache has none of those items), drift nets
 * - the gold tiara (the gold jewellery interface has no tiara entry), the pre-nature amulet
 * - quest-only silver items (lightning rod, Ivandis, Silvthrill, Demonic sigil)
 * - crystal singing and golem crafting (minigames with their own objects and areas)
 *
 * Jewellery at a furnace is in [Jewellery], spinning, weaving and pottery are in [Stations] and
 * tanning is in [Tanning].
 */
object CraftingRecipes {
    private val o = CraftingObjs
    private val s = CraftingSeqs

    val all: List<CraftingRecipe> = buildList {
        addGems()
        addLeather()
        addDragonhide()
        addShields()
        addSnakeskin()
        addOtherArmour()
        addSnelmsAndCrab()
        addStaves()
        addAmethyst()
        addGlass()
        addPottery()
        addStringing()
        addBirdhouses()
        addAll(StationRecipes.all)
        addAll(JewelleryRecipes.all.map { it.recipe })
    }

    /** The recipes made at [station], in the order the menu shows them. */
    fun at(station: CraftingStation): List<CraftingRecipe> = all.filter { it.station == station }

    /** The recipes that [first] and [second] offer when used on each other, in either order. */
    fun offered(first: ObjType, second: ObjType): List<CraftingRecipe> =
        all.filter { recipe ->
            recipe.triggers.any { (a, b) ->
                (same(a, first) && same(b, second)) || (same(a, second) && same(b, first))
            }
        }

    /** The recipe that cuts [uncut] into a gem, or `null` if [uncut] is not an uncut gem. */
    fun cutting(uncut: ObjType): CraftingRecipe? =
        all.firstOrNull { it.crushOrGem && same(it.inputs[0].obj, uncut) }

    /** Every distinct pair of objs that offers a recipe. */
    internal fun triggers(): List<Pair<ObjType, ObjType>> {
        val seen = LinkedHashMap<Set<String>, Pair<ObjType, ObjType>>()
        for (recipe in all) {
            for ((a, b) in recipe.triggers) {
                seen.putIfAbsent(setOf(a.internalNameValue, b.internalNameValue), a to b)
            }
        }
        return seen.values.toList()
    }

    private val CraftingRecipe.crushOrGem: Boolean
        get() =
            method == CraftingMethod.Chisel &&
                tools.size == 1 &&
                inputs.size == 1 &&
                gemNames.any { same(inputs[0].obj, it) }

    private val gemNames =
        listOf(
            o.uncut_opal,
            o.uncut_jade,
            o.uncut_red_topaz,
            o.uncut_sapphire,
            o.uncut_emerald,
            o.uncut_ruby,
            o.uncut_diamond,
            o.uncut_dragonstone,
            o.uncut_onyx,
            o.uncut_zenyte,
        )

    /** Compared by name so the table can be checked without a cache. */
    private fun same(a: ObjType, b: ObjType): Boolean = a.internalNameValue == b.internalNameValue

    private fun MutableList<CraftingRecipe>.recipe(
        method: CraftingMethod,
        triggers: List<Pair<ObjType, ObjType>>,
        tools: List<ObjType>,
        inputs: List<CraftingInput>,
        product: ObjType,
        level: Int,
        xp: Double,
        output: Int = 1,
        returns: List<ObjType> = emptyList(),
        thread: Boolean = false,
        crush: GemCrush? = null,
        anim: SeqType? = null,
    ) {
        add(
            CraftingRecipe(
                method = method,
                triggers = triggers,
                station = null,
                tools = tools,
                inputs = inputs,
                returns = returns,
                product = product,
                output = output,
                level = level,
                xp = xp,
                thread = thread,
                crush = crush,
                crack = false,
                anim = anim,
            )
        )
    }

    /** Needle and thread on [material]: [count] of it make [product]. */
    private fun MutableList<CraftingRecipe>.sew(
        material: ObjType,
        product: ObjType,
        level: Int,
        xp: Double,
        count: Int = 1,
        extra: List<CraftingInput> = emptyList(),
        trigger: ObjType = material,
        anim: SeqType? = s.leatherwork,
    ) {
        recipe(
            method = CraftingMethod.Sew,
            triggers = listOf(o.needle to trigger, o.thread to trigger),
            tools = listOf(o.needle),
            inputs = listOf(CraftingInput(material, count)) + extra,
            product = product,
            level = level,
            xp = xp,
            thread = true,
            anim = anim,
        )
    }

    // ---- Gems -----------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addGems() {
        fun gem(
            uncut: ObjType,
            cut: ObjType,
            level: Int,
            xp: Double,
            anim: SeqType,
            crush: GemCrush? = null,
        ) =
            recipe(
                method = CraftingMethod.Chisel,
                triggers = listOf(o.chisel to uncut),
                tools = listOf(o.chisel),
                inputs = listOf(CraftingInput(uncut)),
                product = cut,
                level = level,
                xp = xp,
                crush = crush,
                anim = anim,
            )
        // Success chances are from the wiki: (level - 1) * slope / 98 + base, out of 256.
        gem(o.uncut_opal, o.opal, 1, 15.0, s.cut_opal, GemCrush(129, 122, 3.8))
        gem(o.uncut_jade, o.jade, 13, 20.0, s.cut_jade, GemCrush(101, 145, 5.0))
        gem(o.uncut_red_topaz, o.red_topaz, 16, 25.0, s.cut_red_topaz, GemCrush(91, 150, 6.3))
        gem(o.uncut_sapphire, o.sapphire, 20, 50.0, s.cut_sapphire)
        gem(o.uncut_emerald, o.emerald, 27, 67.5, s.cut_emerald)
        gem(o.uncut_ruby, o.ruby, 34, 85.0, s.cut_ruby)
        gem(o.uncut_diamond, o.diamond, 43, 107.5, s.cut_diamond)
        gem(o.uncut_dragonstone, o.dragonstone, 55, 137.5, s.cut_dragonstone)
        gem(o.uncut_onyx, o.onyx, 67, 167.5, s.cut_dragonstone)
        gem(o.uncut_zenyte, o.zenyte, 89, 50.0, s.cut_dragonstone)
    }

    // ---- Leather --------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addLeather() {
        sew(o.leather, o.leather_gloves, 1, 13.8)
        sew(o.leather, o.leather_boots, 7, 16.3)
        sew(o.leather, o.leather_cowl, 9, 18.5)
        sew(o.leather, o.leather_vambraces, 11, 22.0)
        sew(o.leather, o.leather_body, 14, 25.0)
        sew(o.leather, o.leather_chaps, 18, 27.0)
        sew(o.hard_leather, o.hardleather_body, 28, 35.0)
        sew(o.leather, o.coif, 38, 37.0)

        // Studs go on a finished piece, with no needle or thread.
        recipe(
            method = CraftingMethod.Assemble,
            triggers = listOf(o.leather_body to o.steel_studs),
            tools = emptyList(),
            inputs = listOf(CraftingInput(o.leather_body), CraftingInput(o.steel_studs)),
            product = o.studded_body,
            level = 41,
            xp = 40.0,
        )
        recipe(
            method = CraftingMethod.Assemble,
            triggers = listOf(o.leather_chaps to o.steel_studs),
            tools = emptyList(),
            inputs = listOf(CraftingInput(o.leather_chaps), CraftingInput(o.steel_studs)),
            product = o.studded_chaps,
            level = 44,
            xp = 42.0,
        )

        // Kebbit claws on any vambraces: level 32 and 6 experience for all of them.
        val spiky =
            listOf(
                o.leather_vambraces to o.spiky_vambraces,
                o.green_dhide_vambraces to o.green_spiky_vambraces,
                o.blue_dhide_vambraces to o.blue_spiky_vambraces,
                o.red_dhide_vambraces to o.red_spiky_vambraces,
                o.black_dhide_vambraces to o.black_spiky_vambraces,
            )
        for ((plain, spiked) in spiky) {
            recipe(
                method = CraftingMethod.Assemble,
                triggers = listOf(o.kebbit_claws to plain),
                tools = emptyList(),
                inputs = listOf(CraftingInput(plain), CraftingInput(o.kebbit_claws)),
                product = spiked,
                level = 32,
                xp = 6.0,
            )
        }
    }

    // ---- Dragonhide -----------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addDragonhide() {
        class Set(
            val leather: ObjType,
            val vambraces: ObjType,
            val chaps: ObjType,
            val body: ObjType,
            val levels: Triple<Int, Int, Int>,
            val xp: Triple<Double, Double, Double>,
        )
        val sets =
            listOf(
                Set(
                    o.green_dragon_leather,
                    o.green_dhide_vambraces,
                    o.green_dhide_chaps,
                    o.green_dhide_body,
                    Triple(57, 60, 63),
                    Triple(62.0, 124.0, 186.0),
                ),
                Set(
                    o.blue_dragon_leather,
                    o.blue_dhide_vambraces,
                    o.blue_dhide_chaps,
                    o.blue_dhide_body,
                    Triple(66, 68, 71),
                    Triple(70.0, 140.0, 210.0),
                ),
                Set(
                    o.red_dragon_leather,
                    o.red_dhide_vambraces,
                    o.red_dhide_chaps,
                    o.red_dhide_body,
                    Triple(73, 75, 77),
                    Triple(78.0, 156.0, 234.0),
                ),
                Set(
                    o.black_dragon_leather,
                    o.black_dhide_vambraces,
                    o.black_dhide_chaps,
                    o.black_dhide_body,
                    Triple(79, 82, 84),
                    Triple(86.0, 172.0, 258.0),
                ),
            )
        for (set in sets) {
            sew(set.leather, set.vambraces, set.levels.first, set.xp.first, count = 1)
            sew(set.leather, set.chaps, set.levels.second, set.xp.second, count = 2)
            sew(set.leather, set.body, set.levels.third, set.xp.third, count = 3)
        }
    }

    // ---- Shields: a hammer, a wooden shield, hide and nails --------------------------------

    private fun MutableList<CraftingRecipe>.addShields() {
        fun shield(
            base: ObjType,
            material: ObjType,
            materialCount: Int,
            nails: List<ObjType>,
            nailCount: Int,
            product: ObjType,
            level: Int,
            xp: Double,
            extra: List<CraftingInput> = emptyList(),
        ) =
            recipe(
                method = CraftingMethod.Assemble,
                triggers = listOf(o.hammer to base, material to base),
                tools = listOf(o.hammer),
                inputs =
                    listOf(
                        CraftingInput(material, materialCount),
                        CraftingInput(base),
                        CraftingInput(nails.first(), nailCount, nails.drop(1)),
                    ) + extra,
                product = product,
                level = level,
                xp = xp,
            )
        shield(
            o.oak_shield,
            o.hard_leather,
            2,
            listOf(o.bronze_nails),
            15,
            o.hard_leather_shield,
            41,
            70.0,
        )
        shield(
            o.maple_shield,
            o.green_dragon_leather,
            2,
            listOf(o.steel_nails),
            15,
            o.green_dhide_shield,
            62,
            124.0,
        )
        shield(
            o.yew_shield,
            o.blue_dragon_leather,
            2,
            listOf(o.mithril_nails),
            15,
            o.blue_dhide_shield,
            69,
            140.0,
        )
        shield(
            o.magic_shield,
            o.red_dragon_leather,
            2,
            listOf(o.adamant_nails),
            15,
            o.red_dhide_shield,
            76,
            156.0,
        )
        shield(
            o.redwood_shield,
            o.black_dragon_leather,
            2,
            listOf(o.rune_nails),
            15,
            o.black_dhide_shield,
            83,
            172.0,
        )
    }

    // ---- Snakeskin and Broodoo shields ------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addSnakeskin() {
        sew(o.snakeskin, o.snakeskin_boots, 45, 30.0, count = 6)
        sew(o.snakeskin, o.snakeskin_vambraces, 47, 35.0, count = 8)
        sew(o.snakeskin, o.snakeskin_bandana, 48, 45.0, count = 5)
        sew(o.snakeskin, o.snakeskin_chaps, 51, 50.0, count = 12)
        sew(o.snakeskin, o.snakeskin_body, 53, 55.0, count = 15)

        recipe(
            method = CraftingMethod.Assemble,
            triggers = listOf(o.hammer to o.willow_shield, o.snakeskin to o.willow_shield),
            tools = listOf(o.hammer),
            inputs =
                listOf(
                    CraftingInput(o.snakeskin, 2),
                    CraftingInput(o.willow_shield),
                    CraftingInput(o.iron_nails, 15),
                ),
            product = o.snakeskin_shield,
            level = 56,
            xp = 100.0,
        )

        // A Broodoo shield takes a tribal mask, two snakeskins and eight nails of any type.
        val anyNails =
            listOf(
                o.bronze_nails,
                o.iron_nails,
                o.black_nails,
                o.steel_nails,
                o.mithril_nails,
                o.adamant_nails,
                o.rune_nails,
            )
        val masks =
            listOf(
                o.tribal_mask_poison to o.broodoo_shield_poison,
                o.tribal_mask_disease to o.broodoo_shield_disease,
                o.tribal_mask_combat to o.broodoo_shield_combat,
            )
        for ((mask, shield) in masks) {
            recipe(
                method = CraftingMethod.Assemble,
                triggers = listOf(o.hammer to mask),
                tools = listOf(o.hammer),
                inputs =
                    listOf(
                        CraftingInput(o.snakeskin, 2),
                        CraftingInput(mask),
                        CraftingInput(anyNails.first(), 8, anyNails.drop(1)),
                    ),
                product = shield,
                level = 35,
                xp = 100.0,
            )
        }
    }

    // ---- Yak-hide, Xerician, splitbark, mixed hide and Hueycoatl hide ------------------------

    private fun MutableList<CraftingRecipe>.addOtherArmour() {
        sew(o.cured_yak_hide, o.yak_hide_legs, 43, 32.0)
        sew(o.cured_yak_hide, o.yak_hide_top, 46, 32.0, count = 2)

        sew(o.xerician_fabric, o.xerician_hat, 14, 66.0, count = 3)
        sew(o.xerician_fabric, o.xerician_robe, 17, 88.0, count = 4)
        sew(o.xerician_fabric, o.xerician_top, 22, 110.0, count = 5)

        fun splitbark(product: ObjType, level: Int, xp: Double, count: Int) =
            sew(
                material = o.bark,
                product = product,
                level = level,
                xp = xp,
                count = count,
                extra = listOf(CraftingInput(o.fine_cloth, count)),
            )
        splitbark(o.splitbark_gauntlets, 60, 62.0, 1)
        splitbark(o.splitbark_boots, 60, 62.0, 1)
        splitbark(o.splitbark_helm, 61, 124.0, 2)
        splitbark(o.splitbark_legs, 62, 186.0, 3)
        splitbark(o.splitbark_body, 62, 248.0, 4)

        fun mixed(product: ObjType, fur: ObjType, furCount: Int, level: Int, xp: Double) =
            sew(
                material = o.mixed_hide_base,
                product = product,
                level = level,
                xp = xp,
                extra = listOf(CraftingInput(fur, furCount)),
            )
        mixed(o.mixed_hide_cape, o.jaguar_fur, 1, 68, 62.0)
        mixed(o.mixed_hide_boots, o.sunlight_antelope_fur, 1, 69, 75.0)
        mixed(o.mixed_hide_legs, o.fox_fur, 3, 71, 210.0)
        mixed(o.mixed_hide_top, o.sunlight_antelope_fur, 2, 72, 150.0)

        sew(o.hueycoatl_hide, o.hueycoatl_vambraces, 76, 95.0)
        sew(o.hueycoatl_hide, o.hueycoatl_coif, 76, 190.0, count = 2)
        sew(o.hueycoatl_hide, o.hueycoatl_chaps, 77, 190.0, count = 2)
        sew(o.hueycoatl_hide, o.hueycoatl_body, 78, 285.0, count = 3)
    }

    // ---- Snelms and crab armour ---------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addSnelmsAndCrab() {
        val snelms =
            listOf(
                o.blamish_red_shell_pointed to o.red_snelm_pointed,
                o.blamish_red_shell_round to o.red_snelm_round,
                o.blamish_bark_shell to o.bark_snelm,
                o.blamish_blue_shell_pointed to o.blue_snelm_pointed,
                o.blamish_blue_shell_round to o.blue_snelm_round,
                o.blamish_myre_shell_pointed to o.myre_snelm_pointed,
                o.blamish_myre_shell_round to o.myre_snelm_round,
                o.blamish_ochre_shell_pointed to o.ochre_snelm_pointed,
                o.blamish_ochre_shell_round to o.ochre_snelm_round,
            )
        for ((shell, snelm) in snelms) {
            recipe(
                method = CraftingMethod.Chisel,
                triggers = listOf(o.chisel to shell),
                tools = listOf(o.chisel),
                inputs = listOf(CraftingInput(shell)),
                product = snelm,
                level = 15,
                xp = 32.5,
                anim = s.cut_shell,
            )
        }
        recipe(
            method = CraftingMethod.Chisel,
            triggers = listOf(o.chisel to o.fresh_crab_shell),
            tools = listOf(o.chisel),
            inputs = listOf(CraftingInput(o.fresh_crab_shell)),
            product = o.crab_helmet,
            level = 15,
            xp = 32.5,
        )
        recipe(
            method = CraftingMethod.Chisel,
            triggers = listOf(o.chisel to o.fresh_crab_claw),
            tools = listOf(o.chisel),
            inputs = listOf(CraftingInput(o.fresh_crab_claw)),
            product = o.crab_claw,
            level = 15,
            xp = 32.5,
        )
    }

    // ---- Battlestaves ---------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addStaves() {
        fun staff(orb: ObjType, product: ObjType, level: Int, xp: Double) =
            recipe(
                method = CraftingMethod.Assemble,
                triggers = listOf(o.battlestaff to orb),
                tools = emptyList(),
                inputs = listOf(CraftingInput(o.battlestaff), CraftingInput(orb)),
                product = product,
                level = level,
                xp = xp,
            )
        staff(o.water_orb, o.water_battlestaff, 54, 100.0)
        staff(o.earth_orb, o.earth_battlestaff, 58, 112.5)
        staff(o.fire_orb, o.fire_battlestaff, 62, 125.0)
        staff(o.air_orb, o.air_battlestaff, 66, 137.5)
    }

    // ---- Amethyst ------------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addAmethyst() {
        fun tips(product: ObjType, output: Int, level: Int) =
            recipe(
                method = CraftingMethod.Chisel,
                triggers = listOf(o.chisel to o.amethyst),
                tools = listOf(o.chisel),
                inputs = listOf(CraftingInput(o.amethyst)),
                product = product,
                output = output,
                level = level,
                xp = 60.0,
            )
        tips(o.amethyst_bolt_tips, 15, 83)
        tips(o.amethyst_arrowtips, 15, 85)
        tips(o.amethyst_javelin_tips, 5, 87)
        tips(o.amethyst_dart_tips, 8, 89)
    }

    // ---- Glass
    // -----------------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addGlass() {
        fun blow(product: ObjType, level: Int, xp: Double) =
            recipe(
                method = CraftingMethod.Blow,
                triggers = listOf(o.glassblowing_pipe to o.molten_glass),
                tools = listOf(o.glassblowing_pipe),
                inputs = listOf(CraftingInput(o.molten_glass)),
                product = product,
                level = level,
                xp = xp,
                anim = s.glassblowing,
            )
        blow(o.beer_glass, 1, 17.5)
        blow(o.candle_lantern, 4, 19.0)
        blow(o.oil_lamp, 12, 25.0)
        blow(o.vial, 33, 35.0)
        blow(o.fishbowl, 42, 42.5)
        blow(o.unpowered_orb, 46, 52.5)
        blow(o.lantern_lens, 49, 55.0)
        blow(o.empty_light_orb, 87, 70.0)

        recipe(
            method = CraftingMethod.Assemble,
            triggers = listOf(o.oil_lamp to o.oil_lantern_frame),
            tools = emptyList(),
            inputs = listOf(CraftingInput(o.oil_lamp), CraftingInput(o.oil_lantern_frame)),
            product = o.oil_lantern,
            level = 26,
            xp = 50.0,
        )
        recipe(
            method = CraftingMethod.Assemble,
            triggers = listOf(o.empty_light_orb to o.cave_goblin_wire),
            tools = emptyList(),
            inputs = listOf(CraftingInput(o.empty_light_orb), CraftingInput(o.cave_goblin_wire)),
            product = o.light_orb,
            level = 87,
            xp = 104.0,
        )
    }

    // ---- Soft clay
    // -----------------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addPottery() {
        val containers =
            listOf(
                o.bucket_of_water to o.empty_bucket,
                o.jug_of_water to o.empty_jug,
                o.bowl_of_water to o.bowl,
                o.vial_of_water to o.vial,
            )
        for ((full, empty) in containers) {
            recipe(
                method = CraftingMethod.Soften,
                triggers = listOf(o.clay to full),
                tools = emptyList(),
                inputs = listOf(CraftingInput(o.clay), CraftingInput(full)),
                returns = listOf(empty),
                product = o.soft_clay,
                level = 1,
                xp = 0.0,
            )
        }
    }

    // ---- Stringing amulets
    // ------------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addStringing() {
        val amulets =
            listOf(
                o.gold_amulet_u to o.gold_amulet,
                o.sapphire_amulet_u to o.sapphire_amulet,
                o.emerald_amulet_u to o.emerald_amulet,
                o.ruby_amulet_u to o.ruby_amulet,
                o.diamond_amulet_u to o.diamond_amulet,
                o.dragonstone_amulet_u to o.dragonstone_amulet,
                o.onyx_amulet_u to o.onyx_amulet,
                o.zenyte_amulet_u to o.zenyte_amulet,
                o.opal_amulet_u to o.opal_amulet,
                o.jade_amulet_u to o.jade_amulet,
                o.topaz_amulet_u to o.topaz_amulet,
            )
        for ((unstrung, strung) in amulets) {
            recipe(
                method = CraftingMethod.String,
                triggers = listOf(o.ball_of_wool to unstrung),
                tools = emptyList(),
                inputs = listOf(CraftingInput(unstrung), CraftingInput(o.ball_of_wool)),
                product = strung,
                level = 1,
                xp = STRING_XP,
            )
        }
    }

    // ---- Birdhouses
    // -------------------------------------------------------------------------------------------

    private fun MutableList<CraftingRecipe>.addBirdhouses() {
        fun house(logs: ObjType, product: ObjType, level: Int, xp: Double) =
            recipe(
                method = CraftingMethod.Assemble,
                triggers = listOf(o.clockwork to logs),
                tools = listOf(o.hammer, o.chisel),
                inputs = listOf(CraftingInput(logs), CraftingInput(o.clockwork)),
                product = product,
                level = level,
                xp = xp,
            )
        house(o.logs, o.bird_house, 5, 15.0)
        house(o.oak_logs, o.oak_bird_house, 15, 20.0)
        house(o.willow_logs, o.willow_bird_house, 25, 25.0)
        house(o.teak_logs, o.teak_bird_house, 35, 30.0)
        house(o.maple_logs, o.maple_bird_house, 45, 35.0)
        house(o.mahogany_logs, o.mahogany_bird_house, 50, 40.0)
        house(o.yew_logs, o.yew_bird_house, 60, 45.0)
        house(o.magic_logs, o.magic_bird_house, 75, 50.0)
        house(o.redwood_logs, o.redwood_bird_house, 90, 55.0)
    }

    /** Experience for putting a string on any amulet: its total minus the unstrung amulet. */
    const val STRING_XP = 4.0
}
