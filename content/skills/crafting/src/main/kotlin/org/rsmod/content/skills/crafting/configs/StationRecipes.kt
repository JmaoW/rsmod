package org.rsmod.content.skills.crafting.configs

import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

/**
 * Recipes made at a spinning wheel, a loom, a potter's wheel, a pottery oven, or a furnace (molten
 * glass). Levels, experience and quantities are from the wiki's Crafting page and item pages.
 *
 * Hemp, cotton and linen yarn and their cloth are not here because the cache has none of them.
 */
object StationRecipes {
    private val o = CraftingObjs
    private val s = CraftingSeqs

    val all: List<CraftingRecipe> = buildList {
        addSpinning()
        addWeaving()
        addPotteryWheel()
        addPotteryOven()
        addGlass()
    }

    private fun MutableList<CraftingRecipe>.station(
        station: CraftingStation,
        method: CraftingMethod,
        inputs: List<CraftingInput>,
        product: ObjType,
        level: Int,
        xp: Double,
        output: Int = 1,
        returns: List<ObjType> = emptyList(),
        crack: Boolean = false,
        anim: SeqType? = null,
    ) {
        add(
            CraftingRecipe(
                method = method,
                triggers = emptyList(),
                station = station,
                tools = emptyList(),
                inputs = inputs,
                returns = returns,
                product = product,
                output = output,
                level = level,
                xp = xp,
                thread = false,
                crush = null,
                crack = crack,
                anim = anim,
            )
        )
    }

    private fun MutableList<CraftingRecipe>.addSpinning() {
        fun spin(input: CraftingInput, product: ObjType, level: Int, xp: Double) =
            station(
                CraftingStation.SpinningWheel,
                CraftingMethod.Spin,
                listOf(input),
                product,
                level,
                xp,
                anim = s.spinning_wheel,
            )
        spin(CraftingInput(o.wool), o.ball_of_wool, 1, 2.5)
        spin(CraftingInput(o.flax), o.bow_string, 10, 15.0)
        spin(
            CraftingInput(
                o.sinew,
                alternatives = listOf(o.oak_roots, o.willow_roots, o.maple_roots, o.yew_roots),
            ),
            o.crossbow_string,
            10,
            15.0,
        )
        spin(CraftingInput(o.magic_roots), o.magic_string, 19, 30.0)
        spin(CraftingInput(o.yak_hair), o.rope, 30, 25.0)
    }

    private fun MutableList<CraftingRecipe>.addWeaving() {
        fun weave(input: ObjType, count: Int, product: ObjType, level: Int, xp: Double) =
            station(
                CraftingStation.Loom,
                CraftingMethod.Weave,
                listOf(CraftingInput(input, count)),
                product,
                level,
                xp,
                anim = s.spinning_wheel,
            )
        weave(o.ball_of_wool, 4, o.strip_of_cloth, 10, 12.0)
        weave(o.jute_fibre, 4, o.empty_sack, 21, 38.0)
        weave(o.willow_branch, 6, o.basket, 36, 56.0)
    }

    private fun MutableList<CraftingRecipe>.addPotteryWheel() {
        fun shape(product: ObjType, level: Int, xp: Double, output: Int = 1) =
            station(
                CraftingStation.PotterWheel,
                CraftingMethod.Shape,
                listOf(CraftingInput(o.soft_clay)),
                product,
                level,
                xp,
                output = output,
                anim = s.potter_wheel,
            )
        shape(o.unfired_pot, 1, 6.3)
        // One soft clay is turned into four cups.
        shape(o.unfired_cup, 3, 8.5, output = 4)
        shape(o.unfired_pie_dish, 7, 15.0)
        shape(o.unfired_bowl, 8, 18.0)
        shape(o.unfired_plant_pot, 19, 20.0)
        shape(o.unfired_pot_lid, 25, 20.0)
    }

    private fun MutableList<CraftingRecipe>.addPotteryOven() {
        fun fire(unfired: ObjType, product: ObjType, level: Int, xp: Double) =
            station(
                CraftingStation.PotteryOven,
                CraftingMethod.Fire,
                listOf(CraftingInput(unfired)),
                product,
                level,
                xp,
                crack = true,
                anim = s.oven,
            )
        fire(o.unfired_pot, o.pot, 1, 6.3)
        fire(o.unfired_cup, o.cup, 3, 8.5)
        fire(o.unfired_pie_dish, o.pie_dish, 7, 10.0)
        fire(o.unfired_bowl, o.bowl, 8, 15.0)
        fire(o.unfired_plant_pot, o.plant_pot, 19, 17.5)
        fire(o.unfired_pot_lid, o.pot_lid, 25, 20.0)
    }

    private fun MutableList<CraftingRecipe>.addGlass() {
        station(
            CraftingStation.Furnace,
            CraftingMethod.Melt,
            listOf(CraftingInput(o.bucket_of_sand), CraftingInput(o.soda_ash)),
            o.molten_glass,
            1,
            20.0,
            returns = listOf(o.empty_bucket),
            anim = s.furnace,
        )
    }
}
