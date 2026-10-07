package org.rsmod.content.skills.herblore.configs

import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/** How a recipe is made, which sets the speed and the wording of the chat message. */
enum class HerbloreMethod(val ticks: Int) {
    /** A herb in a vial of water. */
    Unfinished(2),

    /** A secondary ingredient in an unfinished potion. */
    Potion(2),

    /** A finished potion turned into another, such as a stamina potion or a divine potion. */
    Upgrade(2),

    /** A pestle and mortar, or a chisel, on something that is ground or cut into an ingredient. */
    Grind(2),
}

/** [count] of [obj], used up once for every potion that is made. */
class HerbloreInput(val obj: ObjType, val count: Int = 1)

/**
 * One thing herblore can make.
 *
 * @property inputs what the recipe uses up. The player uses two of them on each other to start it.
 * @property tools objs the player must carry that are not used up. A grinding recipe is started by
 *   using its first tool on its only input.
 * @property output how many of [product] an action makes, up to [outputMax].
 * @property potion the potion [product] is a dose of, for the recipes that make one.
 * @property secondary the input that content such as a relic may stop from being used up.
 */
class HerbloreRecipe(
    val method: HerbloreMethod,
    val tools: List<ObjType>,
    val inputs: List<HerbloreInput>,
    val product: ObjType,
    val level: Int,
    val xp: Double,
    val output: Int = 1,
    val outputMax: Int = output,
    val potion: Potion? = null,
    val secondary: ObjType? = null,
    internal val anim: SeqType? = null,
) {
    /**
     * The objs that, used on each other, offer this recipe: every pair of its inputs, or the tool
     * and the input for a grinding recipe.
     */
    fun triggers(): List<Pair<ObjType, ObjType>> {
        if (method == HerbloreMethod.Grind) {
            return listOf(tools.first() to inputs.first().obj)
        }
        val objs = inputs.map { it.obj }
        val pairs = ArrayList<Pair<ObjType, ObjType>>()
        for (i in objs.indices) {
            for (j in i + 1 until objs.size) {
                pairs += objs[i] to objs[j]
            }
        }
        return pairs
    }

    /** What a potion made by this recipe has when [bonus] extra doses are given, at most four. */
    fun productWith(bonus: Int): ObjType {
        val made = potion ?: return product
        if (method != HerbloreMethod.Potion || bonus <= 0) {
            return product
        }
        return made.dose(minOf(Potion.MAX_DOSES, Potion.MADE_DOSES + bonus))
    }
}

/** Every recipe herblore has, with the levels and experience from the wiki. */
object HerbloreRecipes {
    private val o = HerbloreObjs

    /** A finished potion made from [herb]'s unfinished potion and a [secondary] ingredient. */
    private class PotionRule(
        val potion: Potion,
        val herb: Herb,
        val secondary: ObjType,
        val level: Int,
        val xp: Double,
    )

    private val potionRules =
        listOf(
            PotionRule(Potions.attack, Herbs.guam, o.eye_of_newt, 3, 25.0),
            PotionRule(Potions.antipoison, Herbs.marrentill, o.unicorn_horn_dust, 5, 37.5),
            PotionRule(Potions.strength, Herbs.tarromin, o.limpwurt_root, 12, 50.0),
            PotionRule(Potions.restore, Herbs.harralander, o.red_spiders_eggs, 22, 62.5),
            PotionRule(Potions.energy, Herbs.harralander, o.chocolate_dust, 26, 67.5),
            PotionRule(Potions.defence, Herbs.ranarr, o.white_berries, 30, 75.0),
            PotionRule(Potions.agility, Herbs.toadflax, o.toads_legs, 34, 80.0),
            PotionRule(Potions.combat, Herbs.harralander, o.goat_horn_dust, 36, 84.0),
            PotionRule(Potions.prayer, Herbs.ranarr, o.snape_grass, 38, 87.5),
            PotionRule(Potions.super_attack, Herbs.irit, o.eye_of_newt, 45, 100.0),
            PotionRule(Potions.super_antipoison, Herbs.irit, o.unicorn_horn_dust, 48, 106.3),
            PotionRule(Potions.fishing, Herbs.avantoe, o.snape_grass, 50, 112.5),
            PotionRule(Potions.super_energy, Herbs.avantoe, o.mort_myre_fungus, 52, 117.5),
            PotionRule(Potions.hunter, Herbs.avantoe, o.kebbit_teeth_dust, 53, 120.0),
            PotionRule(Potions.goading, Herbs.harralander, o.aldarium, 54, 132.0),
            PotionRule(Potions.super_strength, Herbs.kwuarm, o.limpwurt_root, 55, 125.0),
            PotionRule(Potions.prayer_regeneration, Herbs.huasca, o.aldarium, 58, 132.0),
            PotionRule(Potions.super_restore, Herbs.snapdragon, o.red_spiders_eggs, 63, 142.5),
            PotionRule(Potions.super_defence, Herbs.cadantine, o.white_berries, 66, 150.0),
            PotionRule(Potions.antifire, Herbs.lantadyme, o.dragon_scale_dust, 69, 157.5),
            PotionRule(Potions.ranging, Herbs.dwarf_weed, o.wine_of_zamorak, 72, 162.5),
            PotionRule(Potions.magic, Herbs.lantadyme, o.potato_cactus, 76, 172.5),
            PotionRule(Potions.zamorak_brew, Herbs.torstol, o.jangerberries, 78, 175.0),
            PotionRule(Potions.saradomin_brew, Herbs.toadflax, o.crushed_nest, 81, 180.0),
        )

    /** Grinds [ground] into [product], [output] to [outputMax] of them. */
    private class GrindRule(
        val tool: ObjType,
        val ground: ObjType,
        val product: ObjType,
        val output: Int = 1,
        val outputMax: Int = output,
    )

    private val grindRules = buildList {
        val pestle = o.pestle_and_mortar
        add(GrindRule(pestle, o.unicorn_horn, o.unicorn_horn_dust))
        add(GrindRule(pestle, o.desert_goat_horn, o.goat_horn_dust))
        add(GrindRule(pestle, o.chocolate_bar, o.chocolate_dust))
        add(GrindRule(pestle, o.blue_dragon_scale, o.dragon_scale_dust))
        for (nest in
            listOf(
                o.bird_nest_egg_red,
                o.bird_nest_egg_green,
                o.bird_nest_egg_blue,
                o.bird_nest_seeds,
                o.bird_nest_ring,
                o.bird_nest_empty,
            )) {
            add(GrindRule(pestle, nest, o.crushed_nest))
        }
        add(GrindRule(o.chisel, o.lava_scale, o.lava_shard, output = 3, outputMax = 6))
    }

    private class DivineRule(val potion: Potion, val base: Potion, val level: Int)

    private val divineRules =
        listOf(
            DivineRule(Potions.divine_super_attack, Potions.super_attack, 70),
            DivineRule(Potions.divine_super_strength, Potions.super_strength, 70),
            DivineRule(Potions.divine_super_defence, Potions.super_defence, 70),
            DivineRule(Potions.divine_ranging, Potions.ranging, 74),
            DivineRule(Potions.divine_magic, Potions.magic, 78),
            DivineRule(Potions.divine_super_combat, Potions.super_combat, 97),
        )

    val all: List<HerbloreRecipe> = buildList {
        for (herb in Herbs.all) {
            add(
                HerbloreRecipe(
                    method = HerbloreMethod.Unfinished,
                    tools = emptyList(),
                    inputs = listOf(HerbloreInput(o.vial_water), HerbloreInput(herb.clean)),
                    product = herb.unfinished,
                    level = herb.unfinishedLevel,
                    xp = 0.0,
                    anim = HerbloreSeqs.mix,
                )
            )
        }
        for (rule in potionRules) {
            add(
                HerbloreRecipe(
                    method = HerbloreMethod.Potion,
                    tools = emptyList(),
                    inputs =
                        listOf(HerbloreInput(rule.herb.unfinished), HerbloreInput(rule.secondary)),
                    product = rule.potion.dose(Potion.MADE_DOSES),
                    level = rule.level,
                    xp = rule.xp,
                    potion = rule.potion,
                    secondary = rule.secondary,
                    anim = HerbloreSeqs.mix,
                )
            )
        }
        for (doses in 1..Potion.MAX_DOSES) {
            // The wiki gives the experience of these per dose.
            add(
                upgrade(
                    Potions.stamina,
                    doses,
                    base = Potions.super_energy,
                    ingredient = o.amylase,
                    level = 77,
                    xpPerDose = 25.5,
                )
            )
            add(
                upgrade(
                    Potions.extended_antifire,
                    doses,
                    base = Potions.antifire,
                    ingredient = o.lava_shard,
                    level = 84,
                    xpPerDose = 27.5,
                )
            )
            for (divine in divineRules) {
                add(
                    upgrade(
                        divine.potion,
                        doses,
                        base = divine.base,
                        ingredient = o.crystal_dust,
                        level = divine.level,
                        xpPerDose = 0.5,
                    )
                )
            }
            add(superCombat(doses))
        }
        for (rule in grindRules) {
            add(
                HerbloreRecipe(
                    method = HerbloreMethod.Grind,
                    tools = listOf(rule.tool),
                    inputs = listOf(HerbloreInput(rule.ground)),
                    product = rule.product,
                    level = 1,
                    xp = 0.0,
                    output = rule.output,
                    outputMax = rule.outputMax,
                    anim = HerbloreSeqs.grind,
                )
            )
        }
    }

    /** [potion] made from the same number of [doses] of [base], with one [ingredient] a dose. */
    private fun upgrade(
        potion: Potion,
        doses: Int,
        base: Potion,
        ingredient: ObjType,
        level: Int,
        xpPerDose: Double,
    ) =
        HerbloreRecipe(
            method = HerbloreMethod.Upgrade,
            tools = emptyList(),
            inputs = listOf(HerbloreInput(base.dose(doses)), HerbloreInput(ingredient, doses)),
            product = potion.dose(doses),
            level = level,
            xp = xpPerDose * doses,
            potion = potion,
            secondary = ingredient,
            anim = HerbloreSeqs.mix,
        )

    /** A super combat potion: a super attack, strength and defence of [doses] each, and torstol. */
    private fun superCombat(doses: Int) =
        HerbloreRecipe(
            method = HerbloreMethod.Upgrade,
            tools = emptyList(),
            inputs =
                listOf(
                    HerbloreInput(o.torstol),
                    HerbloreInput(Potions.super_attack.dose(doses)),
                    HerbloreInput(Potions.super_strength.dose(doses)),
                    HerbloreInput(Potions.super_defence.dose(doses)),
                ),
            product = Potions.super_combat.dose(doses),
            level = 90,
            xp = 150.0,
            potion = Potions.super_combat,
            secondary = o.torstol,
            anim = HerbloreSeqs.mix,
        )

    private fun key(first: ObjType, second: ObjType): String {
        val names = listOf(first.internalNameValue, second.internalNameValue).sorted()
        return "${names[0]}|${names[1]}"
    }

    /** Every pair of objs that offers at least one recipe, each pair once. */
    fun triggers(): List<Pair<ObjType, ObjType>> {
        val seen = HashSet<String>()
        val pairs = ArrayList<Pair<ObjType, ObjType>>()
        for (recipe in all) {
            for ((first, second) in recipe.triggers()) {
                if (seen.add(key(first, second))) {
                    pairs += first to second
                }
            }
        }
        return pairs
    }

    /** The recipes that using [first] and [second] on each other offers, in either order. */
    fun offered(first: ObjType, second: ObjType): List<HerbloreRecipe> {
        val wanted = key(first, second)
        return all.filter { recipe -> recipe.triggers().any { key(it.first, it.second) == wanted } }
    }
}
