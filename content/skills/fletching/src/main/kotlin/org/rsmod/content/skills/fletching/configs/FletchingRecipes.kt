package org.rsmod.content.skills.fletching.configs

import org.rsmod.content.skills.slayer.configs.SlayerUnlocks
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/** How a recipe is made, which sets the speed and the wording of the chat message. */
enum class FletchingMethod(val ticks: Int) {
    /** A knife on logs. */
    Cut(3),

    /** A bow string or crossbow string on an unstrung bow. */
    Stringing(3),

    /** A stock and limbs. */
    Assemble(3),

    /** Feathers, tips or heads put on a whole batch of ammo. */
    Attach(2),

    /** A chisel on a gem. */
    Chisel(2),
}

/**
 * [count] of [obj], used up once for every unit that is made. Any of the [alternatives] can stand
 * in for [obj], such as a coloured feather for a plain one.
 */
class FletchingInput(
    val obj: ObjType,
    val count: Int = 1,
    val alternatives: List<ObjType> = emptyList(),
) {
    /** Every obj that counts as this input, [obj] first. */
    val options: List<ObjType> = listOf(obj) + alternatives
}

/**
 * One thing fletching can make.
 *
 * Experience, [inputs] and [output] are for a single unit. An action makes up to [maxUnits] units
 * at once, as many as the player has the materials for, so 15 arrows are made in one go with
 * [maxUnits] of 15 and an [output] of 1, while one log cut into 15 arrow shafts has a [maxUnits] of
 * 1 and an [output] of 15.
 *
 * @property trigger the two inventory objs that, used on each other, offer this recipe. Objs that
 *   have alternatives (see [FletchingInput.alternatives]) offer it with any of them too, see
 *   [triggers].
 * @property tools objs the player must carry that are not used up.
 * @property slayerUnlock the Slayer unlock bit the player must have bought to make it, if any.
 */
class FletchingRecipe(
    val method: FletchingMethod,
    val trigger: Pair<ObjType, ObjType>,
    val tools: List<ObjType>,
    val inputs: List<FletchingInput>,
    val product: ObjType,
    val output: Int,
    val maxUnits: Int,
    val level: Int,
    val xp: Double,
    internal val anim: SeqType?,
    val slayerUnlock: Int? = null,
) {
    /** Every pair of objs that offers this recipe: [trigger] with each alternative swapped in. */
    val triggers: List<Pair<ObjType, ObjType>> by lazy {
        val (a, b) = trigger
        optionsOf(a).flatMap { first -> optionsOf(b).map { second -> first to second } }
    }

    private fun optionsOf(obj: ObjType): List<ObjType> =
        inputs.firstOrNull { it.obj.internalNameValue == obj.internalNameValue }?.options
            ?: listOf(obj)
}

/**
 * Levels and experience are from the wiki. Left out on purpose:
 * - amethyst bolt tips, arrowtips, javelin tips and dart tips: those are made with Crafting
 * - the ogre bow and ogre arrows (quest items that cannot be fletched), and arctic pine and achey
 *   logs (the wiki lists no Fletching use for them)
 *
 * Broad ammo is here too, behind the "Broader Fletching" Slayer unlock (see
 * [FletchingRecipe.slayerUnlock]).
 *
 * Many materials here (feathers, strings, dragon and amethyst tips, silver bolts, pearls) have no
 * source on the isle yet.
 */
object FletchingRecipes {
    private val o = FletchingObjs
    private val s = FletchingSeqs

    /** Coloured feathers that work as well as a plain feather in every recipe that needs one. */
    private val featherVariants =
        listOf(o.stripy_feather, o.red_feather, o.blue_feather, o.orange_feather, o.yellow_feather)

    val all: List<FletchingRecipe> = buildList {
        addShafts()
        addUnstrungBows()
        addStrungBows()
        addStocks()
        addShields()
        addCrossbows()
        addAmmo()
        addBoltTips()
    }

    /** The recipes that [first] and [second] offer when used on each other, in either order. */
    fun offered(first: ObjType, second: ObjType): List<FletchingRecipe> =
        all.filter { recipe ->
            recipe.triggers.any { (a, b) ->
                (same(a, first) && same(b, second)) || (same(a, second) && same(b, first))
            }
        }

    /** The recipe that cuts [logs] into arrow shafts, or `null` if those logs make none. */
    fun shaftsFrom(logs: ObjType): FletchingRecipe? =
        all.firstOrNull { same(it.product, o.arrow_shaft) && same(it.inputs[0].obj, logs) }

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

    /** Compared by name so the table can be checked without a cache. */
    private fun same(a: ObjType, b: ObjType): Boolean = a.internalNameValue == b.internalNameValue

    private fun MutableList<FletchingRecipe>.cut(
        logs: ObjType,
        product: ObjType,
        output: Int,
        level: Int,
        xp: Double,
        logCount: Int = 1,
    ) {
        add(
            FletchingRecipe(
                method = FletchingMethod.Cut,
                trigger = o.knife to logs,
                tools = listOf(o.knife),
                inputs = listOf(FletchingInput(logs, logCount)),
                product = product,
                output = output,
                maxUnits = 1,
                level = level,
                xp = xp,
                anim = s.cut,
            )
        )
    }

    private fun MutableList<FletchingRecipe>.addShafts() {
        // Level, experience and shafts all grow with the log, one log at a time.
        cut(o.logs, o.arrow_shaft, 15, 1, 5.0)
        cut(o.oak_logs, o.arrow_shaft, 30, 15, 10.0)
        cut(o.willow_logs, o.arrow_shaft, 45, 30, 15.0)
        cut(o.maple_logs, o.arrow_shaft, 60, 45, 20.0)
        cut(o.yew_logs, o.arrow_shaft, 75, 60, 25.0)
        cut(o.magic_logs, o.arrow_shaft, 90, 75, 30.0)
        cut(o.redwood_logs, o.arrow_shaft, 105, 90, 35.0)
        cut(o.logs, o.javelin_shaft, 15, 3, 5.0)
    }

    private fun MutableList<FletchingRecipe>.addUnstrungBows() {
        cut(o.logs, o.shortbow_u, 1, 5, 5.0)
        cut(o.logs, o.longbow_u, 1, 10, 10.0)
        cut(o.oak_logs, o.oak_shortbow_u, 1, 20, 16.5)
        cut(o.oak_logs, o.oak_longbow_u, 1, 25, 25.0)
        cut(o.willow_logs, o.willow_shortbow_u, 1, 35, 33.3)
        cut(o.willow_logs, o.willow_longbow_u, 1, 40, 41.5)
        cut(o.maple_logs, o.maple_shortbow_u, 1, 50, 50.0)
        cut(o.maple_logs, o.maple_longbow_u, 1, 55, 58.3)
        cut(o.yew_logs, o.yew_shortbow_u, 1, 65, 67.5)
        cut(o.yew_logs, o.yew_longbow_u, 1, 70, 75.0)
        cut(o.magic_logs, o.magic_shortbow_u, 1, 80, 83.3)
        cut(o.magic_logs, o.magic_longbow_u, 1, 85, 91.5)
    }

    private fun MutableList<FletchingRecipe>.string(
        unstrung: ObjType,
        strung: ObjType,
        level: Int,
        xp: Double,
        anim: SeqType,
        string: ObjType = o.bow_string,
    ) {
        add(
            FletchingRecipe(
                method = FletchingMethod.Stringing,
                trigger = string to unstrung,
                tools = emptyList(),
                inputs = listOf(FletchingInput(unstrung), FletchingInput(string)),
                product = strung,
                output = 1,
                maxUnits = 1,
                level = level,
                xp = xp,
                anim = anim,
            )
        )
    }

    private fun MutableList<FletchingRecipe>.addStrungBows() {
        string(o.shortbow_u, o.shortbow, 5, 5.0, s.string_shortbow)
        string(o.longbow_u, o.longbow, 10, 10.0, s.string_longbow)
        string(o.oak_shortbow_u, o.oak_shortbow, 20, 16.5, s.string_oak_shortbow)
        string(o.oak_longbow_u, o.oak_longbow, 25, 25.0, s.string_oak_longbow)
        string(o.willow_shortbow_u, o.willow_shortbow, 35, 33.2, s.string_willow_shortbow)
        string(o.willow_longbow_u, o.willow_longbow, 40, 41.5, s.string_willow_longbow)
        string(o.maple_shortbow_u, o.maple_shortbow, 50, 50.0, s.string_maple_shortbow)
        string(o.maple_longbow_u, o.maple_longbow, 55, 58.2, s.string_maple_longbow)
        string(o.yew_shortbow_u, o.yew_shortbow, 65, 67.5, s.string_yew_shortbow)
        string(o.yew_longbow_u, o.yew_longbow, 70, 75.0, s.string_yew_longbow)
        string(o.magic_shortbow_u, o.magic_shortbow, 80, 83.2, s.string_magic_shortbow)
        string(o.magic_longbow_u, o.magic_longbow, 85, 91.5, s.string_magic_longbow)
    }

    private fun MutableList<FletchingRecipe>.addStocks() {
        cut(o.logs, o.wooden_stock, 1, 9, 6.0)
        cut(o.oak_logs, o.oak_stock, 1, 24, 16.0)
        cut(o.willow_logs, o.willow_stock, 1, 39, 22.0)
        cut(o.teak_logs, o.teak_stock, 1, 46, 27.0)
        cut(o.maple_logs, o.maple_stock, 1, 54, 32.0)
        cut(o.mahogany_logs, o.mahogany_stock, 1, 61, 41.0)
        cut(o.yew_logs, o.yew_stock, 1, 69, 50.0)
        cut(o.magic_logs, o.magic_stock, 1, 78, 70.0)
    }

    private fun MutableList<FletchingRecipe>.addShields() {
        cut(o.oak_logs, o.oak_shield, 1, 27, 50.0, logCount = 2)
        cut(o.willow_logs, o.willow_shield, 1, 42, 83.0, logCount = 2)
        cut(o.maple_logs, o.maple_shield, 1, 57, 116.5, logCount = 2)
        cut(o.yew_logs, o.yew_shield, 1, 72, 150.0, logCount = 2)
        cut(o.magic_logs, o.magic_shield, 1, 87, 183.0, logCount = 2)
        cut(o.redwood_logs, o.redwood_shield, 1, 92, 216.0, logCount = 2)
    }

    private fun MutableList<FletchingRecipe>.crossbow(
        stock: ObjType,
        limbs: ObjType,
        unstrung: ObjType,
        strung: ObjType,
        level: Int,
        xp: Double,
        assemble: SeqType,
        stringing: SeqType,
        stringXp: Double = xp / 2,
    ) {
        add(
            FletchingRecipe(
                method = FletchingMethod.Assemble,
                trigger = stock to limbs,
                tools = listOf(o.hammer),
                inputs = listOf(FletchingInput(stock), FletchingInput(limbs)),
                product = unstrung,
                output = 1,
                maxUnits = 1,
                level = level,
                xp = xp,
                anim = assemble,
            )
        )
        // Stringing a crossbow gives half the experience of putting it together, bar the dragon
        // one.
        string(unstrung, strung, level, stringXp, stringing, o.crossbow_string)
    }

    private fun MutableList<FletchingRecipe>.addCrossbows() {
        crossbow(
            o.wooden_stock,
            o.bronze_limbs,
            o.bronze_crossbow_u,
            o.bronze_crossbow,
            9,
            12.0,
            s.assemble_bronze,
            s.string_bronze_crossbow,
        )
        crossbow(
            o.oak_stock,
            o.blurite_limbs,
            o.blurite_crossbow_u,
            o.blurite_crossbow,
            24,
            32.0,
            s.assemble_blurite,
            s.string_blurite_crossbow,
        )
        crossbow(
            o.willow_stock,
            o.iron_limbs,
            o.iron_crossbow_u,
            o.iron_crossbow,
            39,
            44.0,
            s.assemble_iron,
            s.string_iron_crossbow,
        )
        crossbow(
            o.teak_stock,
            o.steel_limbs,
            o.steel_crossbow_u,
            o.steel_crossbow,
            46,
            54.0,
            s.assemble_steel,
            s.string_steel_crossbow,
        )
        crossbow(
            o.maple_stock,
            o.mithril_limbs,
            o.mithril_crossbow_u,
            o.mithril_crossbow,
            54,
            64.0,
            s.assemble_mithril,
            s.string_mithril_crossbow,
        )
        crossbow(
            o.mahogany_stock,
            o.adamant_limbs,
            o.adamant_crossbow_u,
            o.adamant_crossbow,
            61,
            82.0,
            s.assemble_adamant,
            s.string_adamant_crossbow,
        )
        crossbow(
            o.yew_stock,
            o.rune_limbs,
            o.rune_crossbow_u,
            o.rune_crossbow,
            69,
            100.0,
            s.assemble_rune,
            s.string_rune_crossbow,
        )
        crossbow(
            o.magic_stock,
            o.dragon_limbs,
            o.dragon_crossbow_u,
            o.dragon_crossbow,
            78,
            135.0,
            s.assemble_dragon,
            s.string_dragon_crossbow,
            stringXp = 70.0,
        )
    }

    /** Puts [attachment] on [base]: a feather, an arrowhead, a tip or a javelin head. */
    private fun MutableList<FletchingRecipe>.attach(
        base: ObjType,
        attachment: ObjType,
        product: ObjType,
        maxUnits: Int,
        level: Int,
        xp: Double,
        slayerUnlock: Int? = null,
    ) {
        val attachmentInput =
            if (same(attachment, o.feather)) {
                FletchingInput(attachment, alternatives = featherVariants)
            } else {
                FletchingInput(attachment)
            }
        add(
            FletchingRecipe(
                method = FletchingMethod.Attach,
                trigger = base to attachment,
                tools = emptyList(),
                inputs = listOf(FletchingInput(base), attachmentInput),
                product = product,
                output = 1,
                maxUnits = maxUnits,
                level = level,
                xp = xp,
                anim = null,
                slayerUnlock = slayerUnlock,
            )
        )
    }

    private fun MutableList<FletchingRecipe>.addAmmo() {
        attach(o.arrow_shaft, o.feather, o.headless_arrow, 15, 1, 1.0)
        attach(o.headless_arrow, o.bronze_arrowheads, o.bronze_arrow, 15, 1, 1.3)
        attach(o.headless_arrow, o.iron_arrowheads, o.iron_arrow, 15, 15, 2.5)
        attach(o.headless_arrow, o.steel_arrowheads, o.steel_arrow, 15, 30, 5.0)
        attach(o.headless_arrow, o.mithril_arrowheads, o.mithril_arrow, 15, 45, 7.5)
        attach(o.headless_arrow, o.adamant_arrowheads, o.adamant_arrow, 15, 60, 10.0)
        attach(o.headless_arrow, o.rune_arrowheads, o.rune_arrow, 15, 75, 12.5)
        attach(
            o.headless_arrow,
            o.broad_arrowheads,
            o.broad_arrow,
            15,
            52,
            10.0,
            SlayerUnlocks.BROADER_FLETCHING,
        )
        attach(o.headless_arrow, o.amethyst_arrowheads, o.amethyst_arrow, 15, 82, 13.5)
        attach(o.headless_arrow, o.dragon_arrowheads, o.dragon_arrow, 15, 90, 15.0)

        attach(o.javelin_shaft, o.bronze_javelin_head, o.bronze_javelin, 15, 3, 1.0)
        attach(o.javelin_shaft, o.iron_javelin_head, o.iron_javelin, 15, 17, 2.0)
        attach(o.javelin_shaft, o.steel_javelin_head, o.steel_javelin, 15, 32, 5.0)
        attach(o.javelin_shaft, o.mithril_javelin_head, o.mithril_javelin, 15, 47, 8.0)
        attach(o.javelin_shaft, o.adamant_javelin_head, o.adamant_javelin, 15, 62, 10.0)
        attach(o.javelin_shaft, o.rune_javelin_head, o.rune_javelin, 15, 77, 12.4)
        attach(o.javelin_shaft, o.amethyst_javelin_head, o.amethyst_javelin, 15, 84, 13.5)
        attach(o.javelin_shaft, o.dragon_javelin_head, o.dragon_javelin, 15, 92, 15.0)

        attach(o.bronze_dart_tip, o.feather, o.bronze_dart, 10, 10, 1.8)
        attach(o.iron_dart_tip, o.feather, o.iron_dart, 10, 22, 3.8)
        attach(o.steel_dart_tip, o.feather, o.steel_dart, 10, 37, 7.5)
        attach(o.mithril_dart_tip, o.feather, o.mithril_dart, 10, 52, 11.2)
        attach(o.adamant_dart_tip, o.feather, o.adamant_dart, 10, 67, 15.0)
        attach(o.rune_dart_tip, o.feather, o.rune_dart, 10, 81, 18.8)
        attach(o.amethyst_dart_tip, o.feather, o.amethyst_dart, 10, 90, 21.0)
        attach(o.dragon_dart_tip, o.feather, o.dragon_dart, 10, 95, 25.0)

        attach(o.bronze_bolts_unf, o.feather, o.bronze_bolts, 10, 9, 0.5)
        attach(o.blurite_bolts_unf, o.feather, o.blurite_bolts, 10, 24, 1.0)
        attach(o.iron_bolts_unf, o.feather, o.iron_bolts, 10, 39, 1.5)
        attach(o.silver_bolts_unf, o.feather, o.silver_bolts, 10, 43, 2.5)
        attach(o.steel_bolts_unf, o.feather, o.steel_bolts, 10, 46, 3.5)
        attach(o.mithril_bolts_unf, o.feather, o.mithril_bolts, 10, 54, 5.0)
        attach(o.adamant_bolts_unf, o.feather, o.adamant_bolts, 10, 61, 7.0)
        attach(o.rune_bolts_unf, o.feather, o.rune_bolts, 10, 69, 10.0)
        attach(
            o.broad_bolts_unf,
            o.feather,
            o.broad_bolts,
            10,
            55,
            3.0,
            SlayerUnlocks.BROADER_FLETCHING,
        )
        attach(o.dragon_bolts_unf, o.feather, o.dragon_bolts, 10, 84, 12.0)
    }

    /** A chisel on [gem] makes [tipCount] bolt tips. */
    private fun MutableList<FletchingRecipe>.cutTips(
        gem: ObjType,
        tips: ObjType,
        tipCount: Int,
        level: Int,
        xp: Double,
    ) {
        add(
            FletchingRecipe(
                method = FletchingMethod.Chisel,
                trigger = o.chisel to gem,
                tools = listOf(o.chisel),
                inputs = listOf(FletchingInput(gem)),
                product = tips,
                output = tipCount,
                maxUnits = 1,
                level = level,
                xp = xp,
                anim = null,
            )
        )
    }

    private fun MutableList<FletchingRecipe>.addBoltTips() {
        cutTips(o.opal, o.opal_bolt_tips, 12, 11, 1.5)
        cutTips(o.jade, o.jade_bolt_tips, 12, 26, 2.0)
        cutTips(o.oyster_pearl, o.pearl_bolt_tips, 6, 41, 3.2)
        cutTips(o.oyster_pearls, o.pearl_bolt_tips, 24, 41, 3.2)
        cutTips(o.red_topaz, o.red_topaz_bolt_tips, 12, 48, 3.9)
        cutTips(o.sapphire, o.sapphire_bolt_tips, 12, 56, 4.0)
        cutTips(o.emerald, o.emerald_bolt_tips, 12, 58, 5.5)
        cutTips(o.ruby, o.ruby_bolt_tips, 12, 63, 6.3)
        cutTips(o.diamond, o.diamond_bolt_tips, 12, 65, 7.0)
        cutTips(o.dragonstone, o.dragonstone_bolt_tips, 12, 71, 8.2)
        cutTips(o.onyx, o.onyx_bolt_tips, 24, 73, 9.4)

        // Each tip goes on a bolt of a fixed metal.
        attach(o.opal_bolt_tips, o.bronze_bolts, o.opal_bolts, 10, 11, 1.6)
        attach(o.jade_bolt_tips, o.blurite_bolts, o.jade_bolts, 10, 26, 2.4)
        attach(o.pearl_bolt_tips, o.iron_bolts, o.pearl_bolts, 10, 41, 3.2)
        attach(o.red_topaz_bolt_tips, o.steel_bolts, o.red_topaz_bolts, 10, 48, 3.9)
        attach(o.barbed_bolt_tips, o.bronze_bolts, o.barbed_bolts, 10, 51, 9.5)
        attach(o.sapphire_bolt_tips, o.mithril_bolts, o.sapphire_bolts, 10, 56, 4.7)
        attach(o.emerald_bolt_tips, o.mithril_bolts, o.emerald_bolts, 10, 58, 5.5)
        attach(o.ruby_bolt_tips, o.adamant_bolts, o.ruby_bolts, 10, 63, 6.3)
        attach(o.diamond_bolt_tips, o.adamant_bolts, o.diamond_bolts, 10, 65, 7.0)
        attach(o.dragonstone_bolt_tips, o.rune_bolts, o.dragonstone_bolts, 10, 71, 8.2)
        attach(o.onyx_bolt_tips, o.rune_bolts, o.onyx_bolts, 10, 73, 9.4)
        attach(
            o.amethyst_bolt_tips,
            o.broad_bolts,
            o.amethyst_broad_bolts,
            10,
            76,
            10.6,
            SlayerUnlocks.BROADER_FLETCHING,
        )

        // Dragon bolts take any tip, all at the dragon bolts' own level.
        attach(o.opal_bolt_tips, o.dragon_bolts, o.opal_dragon_bolts, 10, 84, 1.6)
        attach(o.jade_bolt_tips, o.dragon_bolts, o.jade_dragon_bolts, 10, 84, 2.4)
        attach(o.pearl_bolt_tips, o.dragon_bolts, o.pearl_dragon_bolts, 10, 84, 3.2)
        attach(o.red_topaz_bolt_tips, o.dragon_bolts, o.topaz_dragon_bolts, 10, 84, 3.9)
        attach(o.sapphire_bolt_tips, o.dragon_bolts, o.sapphire_dragon_bolts, 10, 84, 4.7)
        attach(o.emerald_bolt_tips, o.dragon_bolts, o.emerald_dragon_bolts, 10, 84, 5.5)
        attach(o.ruby_bolt_tips, o.dragon_bolts, o.ruby_dragon_bolts, 10, 84, 6.3)
        attach(o.diamond_bolt_tips, o.dragon_bolts, o.diamond_dragon_bolts, 10, 84, 7.0)
        attach(o.dragonstone_bolt_tips, o.dragon_bolts, o.dragonstone_dragon_bolts, 10, 84, 8.2)
        attach(o.onyx_bolt_tips, o.dragon_bolts, o.onyx_dragon_bolts, 10, 84, 9.4)
    }
}
