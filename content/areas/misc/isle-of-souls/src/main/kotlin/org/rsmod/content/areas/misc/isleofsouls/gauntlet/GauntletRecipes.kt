package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.game.type.obj.ObjType

/** What a recipe makes, for the points it earns. */
enum class GearKind {
    Armour,
    Weapon,
}

/**
 * One thing the singing bowl can make. Crafting and Smithing each get [xp] (wiki: both skills get
 * the same experience).
 *
 * @param needs what is used up besides the shards; each is taken from the pack, or from what the
 *   player wears if that is where it is.
 * @param tier for gear: 0 basic, 1 attuned, 2 perfected.
 */
class GauntletRecipe(
    val product: ObjType,
    val shards: Int,
    val xp: Double,
    val needs: List<Pair<ObjType, Int>>,
    val gear: GearKind? = null,
    val tier: Int = 0,
)

/**
 * The singing bowl's recipes (wiki, Singing Bowl). Each piece of armour and each weapon is offered
 * at the tier after the one the player has, which makes ten entries at most: the chat menu's limit.
 * The armour totals come to 150 / 350 / 650 shards and 3 / 7 / 13 of each resource for a full basic
 * / attuned / perfected set, as the wiki's Gauntlet page says.
 */
object GauntletRecipes {
    /** Resources (ore, bark and linum each) and shards for each tier of a piece. */
    private class ArmourCost(val each: Int, val shards: Int, val xp: Double)

    private val helm =
        listOf(ArmourCost(1, 50, 20.0), ArmourCost(1, 50, 30.0), ArmourCost(2, 100, 40.0))
    private val body =
        listOf(ArmourCost(1, 50, 20.0), ArmourCost(2, 100, 30.0), ArmourCost(2, 100, 40.0))
    private val legs = helm

    const val VIAL_SHARDS = 10
    const val TELEPORT_CRYSTAL_SHARDS = 50
    const val ESCAPE_CRYSTAL_SHARDS = 200
    const val COMBO_FOOD_SHARDS = 10
    const val ATTUNE_SHARDS = 50

    /** The tier to make next of a piece whose tiers are [tiers]: -1 when it is complete. */
    fun nextTier(has: (ObjType) -> Boolean, tiers: List<ObjType>): Int =
        when {
            has(tiers[2]) -> -1
            has(tiers[1]) -> 2
            has(tiers[0]) -> 1
            else -> 0
        }

    /** What the bowl offers a player now. [has] says whether they hold or wear an obj. */
    fun available(mode: GauntletMode, has: (ObjType) -> Boolean): List<GauntletRecipe> {
        val items = mode.items
        val recipes = ArrayList<GauntletRecipe>()
        recipes += GauntletRecipe(items.vial, VIAL_SHARDS, 5.0, emptyList())
        recipes += GauntletRecipe(items.teleportCrystal, TELEPORT_CRYSTAL_SHARDS, 20.0, emptyList())
        recipes += GauntletRecipe(items.escapeCrystal, ESCAPE_CRYSTAL_SHARDS, 100.0, emptyList())
        recipes +=
            GauntletRecipe(
                items.comboFood,
                COMBO_FOOD_SHARDS,
                5.0,
                listOf(GauntletShared.food to 1),
            )
        for ((tiers, costs) in listOf(items.helm to helm, items.body to body, items.legs to legs)) {
            val tier = nextTier(has, tiers)
            if (tier < 0) continue
            val cost = costs[tier]
            val needs = ArrayList<Pair<ObjType, Int>>()
            if (tier > 0) needs += tiers[tier - 1] to 1
            needs += items.ore to cost.each
            needs += items.bark to cost.each
            needs += items.linum to cost.each
            recipes +=
                GauntletRecipe(tiers[tier], cost.shards, cost.xp, needs, GearKind.Armour, tier)
        }
        val components =
            listOf(
                items.halberd to items.spike,
                items.staff to items.orb,
                items.bow to items.bowstring,
            )
        for ((tiers, component) in components) {
            val tier = nextTier(has, tiers)
            if (tier < 0) continue
            val (shards, xp, needs) =
                when (tier) {
                    0 -> Triple(0, 10.0, listOf(items.frame to 1))
                    1 -> Triple(ATTUNE_SHARDS, 30.0, listOf(tiers[0] to 1))
                    else -> Triple(0, 0.0, listOf(tiers[1] to 1, component to 1))
                }
            recipes += GauntletRecipe(tiers[tier], shards, xp, needs, GearKind.Weapon, tier)
        }
        return recipes
    }

    /** Points for a run that does not kill the Hunllef (wiki): basic 2, attuned 5, perfected 10. */
    val GEAR_POINTS = listOf(2, 5, 10)
    const val COMBO_FOOD_POINTS = 1
    const val COOK_POINTS = 1
}
