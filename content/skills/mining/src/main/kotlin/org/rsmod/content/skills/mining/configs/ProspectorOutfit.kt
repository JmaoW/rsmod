package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.stats
import org.rsmod.api.stats.xpmod.OutfitXpMod
import org.rsmod.api.stats.xpmod.OutfitXpMod.Outfit
import org.rsmod.api.stats.xpmod.OutfitXpMod.Piece
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.api.type.refs.obj.ObjReferences

object ProspectorObjs : ObjReferences() {
    val helmet = find("motherlode_reward_hat")
    val jacket = find("motherlode_reward_top")
    val legs = find("motherlode_reward_legs")
    val boots = find("motherlode_reward_boots")
}

/**
 * The prospector kit's Mining experience bonus: 0.4% for the helmet, 0.8% for the jacket, 0.6% for
 * the legs and 0.2% for the boots, plus 0.5% more when all four are worn, which makes 2.5%.
 */
class ProspectorXpMod :
    XpMod by OutfitXpMod(
        stats.mining,
        listOf(
            Outfit(
                pieces =
                    listOf(
                        Piece(ProspectorObjs.helmet, 0.4),
                        Piece(ProspectorObjs.jacket, 0.8),
                        Piece(ProspectorObjs.legs, 0.6),
                        Piece(ProspectorObjs.boots, 0.2),
                    ),
                setBonus = 0.5,
            )
        ),
    )
