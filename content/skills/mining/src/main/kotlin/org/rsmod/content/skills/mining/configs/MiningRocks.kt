package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.loc.LocType

internal object MiningRocks : LocEditor() {
    init {
        orePair(RockLocs.copper_rock_1, RockLocs.copper_rock_2, objs.copper_ore, levelReq = 1, xp = 17.5)
        orePair(RockLocs.tin_rock_1, RockLocs.tin_rock_2, objs.tin_ore, levelReq = 1, xp = 17.5)
        orePair(RockLocs.clay_rock_1, RockLocs.clay_rock_2, objs.clay, levelReq = 1, xp = 5.0)
        orePair(RockLocs.iron_rock_1, RockLocs.iron_rock_2, objs.iron_ore, levelReq = 15, xp = 35.0)
        orePair(RockLocs.silver_rock_1, RockLocs.silver_rock_2, objs.silver_ore, levelReq = 20, xp = 40.0)
        orePair(RockLocs.coal_rock_1, RockLocs.coal_rock_2, objs.coal, levelReq = 30, xp = 50.0)
        orePair(RockLocs.gold_rock_1, RockLocs.gold_rock_2, objs.gold_ore, levelReq = 40, xp = 65.0)
        orePair(RockLocs.mithril_rock_1, RockLocs.mithril_rock_2, objs.mithril_ore, levelReq = 55, xp = 80.0)
        orePair(RockLocs.adamantite_rock_1, RockLocs.adamantite_rock_2, objs.adamantite_ore, levelReq = 70, xp = 95.0)
        orePair(RockLocs.runite_rock_1, RockLocs.runite_rock_2, objs.runite_ore, levelReq = 85, xp = 125.0)
    }

    private fun orePair(
        rock1: LocType,
        rock2: LocType,
        product: org.rsmod.game.type.obj.ObjType,
        levelReq: Int,
        xp: Double,
    ) {
        rock(rock1, RockLocs.depleted_rock_1, product, levelReq, xp)
        rock(rock2, RockLocs.depleted_rock_2, product, levelReq, xp)
    }

    private fun rock(
        type: LocType,
        depleted: LocType,
        product: org.rsmod.game.type.obj.ObjType,
        levelReq: Int,
        xp: Double,
    ) {
        edit(type) {
            contentGroup = content.ore
            param[params.levelrequire] = levelReq
            param[params.skill_productitem] = product
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
            param[params.deplete_chance] = 0
            param[params.next_loc_stage] = depleted
            param[params.respawn_time] = 30
        }
    }

    internal object RockLocs : org.rsmod.api.type.refs.loc.LocReferences() {
        val depleted_rock_1 = find("rocks1")
        val depleted_rock_2 = find("rocks2")

        val copper_rock_1 = find("copperrock1")
        val copper_rock_2 = find("copperrock2")
        val tin_rock_1 = find("tinrock1")
        val tin_rock_2 = find("tinrock2")
        val clay_rock_1 = find("clayrock1")
        val clay_rock_2 = find("clayrock2")
        val iron_rock_1 = find("ironrock1")
        val iron_rock_2 = find("ironrock2")
        val silver_rock_1 = find("silverrock1")
        val silver_rock_2 = find("silverrock2")
        val coal_rock_1 = find("coalrock1")
        val coal_rock_2 = find("coalrock2")
        val gold_rock_1 = find("goldrock1")
        val gold_rock_2 = find("goldrock2")
        val mithril_rock_1 = find("mithrilrock1")
        val mithril_rock_2 = find("mithrilrock2")
        val adamantite_rock_1 = find("adamantiterock1")
        val adamantite_rock_2 = find("adamantiterock2")
        val runite_rock_1 = find("runiterock1")
        val runite_rock_2 = find("runiterock2")
    }
}
