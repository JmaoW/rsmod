package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.content.skills.crafting.configs.CraftingRecipes
import org.rsmod.game.type.obj.ObjType

/** What cutting an uncut gem gives: the cut gem, and the Crafting experience for it. */
class CutGem(val gem: ObjType, val xp: Double)

/**
 * What the Power Miner's auto-gem cut makes of the gems that mining turns up. The gems and their
 * Crafting experience are the Crafting skill's own (`CraftingRecipes`), so cutting by hand and the
 * relic always agree. A crushed gem is never the result, and no level is needed.
 */
object LeagueGemCutting {
    /** The cut version of [uncut], or `null` if it is not a gem that can be cut. */
    fun cut(uncut: ObjType): CutGem? =
        CraftingRecipes.cutting(uncut)?.let { CutGem(it.product, it.xp) }
}
