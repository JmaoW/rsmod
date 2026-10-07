package org.rsmod.api.stats.xpmod

import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

/**
 * The experience bonus of skilling outfits for [stat]: every worn piece gives its own bonus, and
 * wearing every piece of an [Outfit] gives its [Outfit.setBonus] on top. Bonuses are in percent and
 * can have decimals (0.4 is 0.4%), which the whole-percent `xpmod_percent` obj param of
 * [WornXpModifiers] cannot express.
 */
class OutfitXpMod(private val stat: StatType, private val outfits: List<Outfit>) : XpMod {
    class Piece(val obj: ObjType, val percent: Double)

    class Outfit(val pieces: List<Piece>, val setBonus: Double)

    override fun Player.modifier(stat: StatType): Double {
        if (stat != this@OutfitXpMod.stat) {
            return 0.0
        }
        var percent = 0.0
        for (outfit in outfits) {
            var worn = 0
            for (piece in outfit.pieces) {
                if (piece.obj in this.worn) {
                    percent += piece.percent
                    worn++
                }
            }
            if (worn == outfit.pieces.size) {
                percent += outfit.setBonus
            }
        }
        return percent / 100.0
    }
}
