package org.rsmod.content.skills.woodcutting.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.stats.xpmod.OutfitXpMod
import org.rsmod.api.stats.xpmod.OutfitXpMod.Outfit
import org.rsmod.api.stats.xpmod.OutfitXpMod.Piece
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.game.type.obj.ObjType

/**
 * The lumberjack outfit's Woodcutting experience bonus, and that of its forestry recolour: 0.4% for
 * the hat, 0.8% for the top, 0.6% for the legs and 0.2% for the boots, plus 0.5% more when all four
 * of one outfit are worn, which makes 2.5%.
 */
class WoodcuttingOutfitXpMod :
    XpMod by OutfitXpMod(
        stats.woodcutting,
        listOf(
            lumberjackOutfit(
                hat = objs.lumberjack_hat,
                top = objs.lumberjack_top,
                legs = objs.lumberjack_legs,
                boots = objs.lumberjack_boots,
            ),
            lumberjackOutfit(
                hat = objs.forestry_hat,
                top = objs.forestry_top,
                legs = objs.forestry_legs,
                boots = objs.forestry_boots,
            ),
        ),
    )

private fun lumberjackOutfit(hat: ObjType, top: ObjType, legs: ObjType, boots: ObjType) =
    Outfit(
        pieces = listOf(Piece(hat, 0.4), Piece(top, 0.8), Piece(legs, 0.6), Piece(boots, 0.2)),
        setBonus = 0.5,
    )
