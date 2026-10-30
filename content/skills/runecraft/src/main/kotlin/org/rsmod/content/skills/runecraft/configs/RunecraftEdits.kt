package org.rsmod.content.skills.runecraft.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.params
import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType

/**
 * Makes the large rock in the essence mine an ore, so Mining runs it. It has no depleted form, so
 * it never runs out; `RuneEssenceMining` makes every swing a success and gives pure essence from
 * Mining level 30. Needs `packCache`.
 */
internal object EssenceRockEdits : LocEditor() {
    init {
        edit(RunecraftLocs.essence_rock) {
            contentGroup = content.ore
            param[params.levelrequire] = 1
            param[params.skill_productitem] = RunecraftObjs.rune_essence
            param[params.skill_xp] = PlayerStatMap.toFineXP(ESSENCE_XP).toInt()
        }
    }

    /** Mining experience for each essence. */
    const val ESSENCE_XP = 5.0
}

/**
 * The cache has a rift in the Abyss for every altar but blood, astral and wrath, so three of the
 * mine's spare portal types are renamed to serve as theirs. Needs `packCache`.
 */
internal object RiftEdits : LocEditor() {
    init {
        edit(RunecraftLocs.blood_rift) {
            name = "Blood rift"
            op1 = RIFT_OP
        }
        edit(RunecraftLocs.astral_rift) {
            name = "Astral rift"
            op1 = RIFT_OP
        }
        edit(RunecraftLocs.wrath_rift) {
            name = "Wrath rift"
            op1 = RIFT_OP
        }
    }

    private const val RIFT_OP = "Exit-through"
}

/**
 * The Mage of Zamorak's shop (the wiki's Battle Runes, the stock after the Enter the Abyss
 * miniquest). He sells at the rune's value and buys at 55%, his prices moving 0.2% a purchase;
 * percentages are in tenths. Needs `packCache`.
 */
internal object BattleRuneShopEdits : InvEditor() {
    init {
        edit(RunecraftInvs.battle_rune_shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(RunecraftObjs.fire_rune, count = 1000, restockCycles = 10)
            stock += stock(RunecraftObjs.water_rune, count = 1000, restockCycles = 10)
            stock += stock(RunecraftObjs.air_rune, count = 1000, restockCycles = 10)
            stock += stock(RunecraftObjs.earth_rune, count = 1000, restockCycles = 10)
            stock += stock(RunecraftObjs.mind_rune, count = 1000, restockCycles = 10)
            stock += stock(RunecraftObjs.chaos_rune, count = 500, restockCycles = 10)
            stock += stock(RunecraftObjs.death_rune, count = 500, restockCycles = 10)
            stock += stock(RunecraftObjs.blood_rune, count = 500, restockCycles = 15)
            stock += stock(RunecraftObjs.fire_rune_pack, count = 50, restockCycles = 2)
            stock += stock(RunecraftObjs.water_rune_pack, count = 50, restockCycles = 2)
            stock += stock(RunecraftObjs.air_rune_pack, count = 50, restockCycles = 2)
            stock += stock(RunecraftObjs.earth_rune_pack, count = 50, restockCycles = 2)
            stock += stock(RunecraftObjs.mind_rune_pack, count = 35, restockCycles = 5)
            stock += stock(RunecraftObjs.chaos_rune_pack, count = 35, restockCycles = 5)
        }
    }
}

/**
 * Aubury's Rune Shop, with the stock the wiki lists. He sells at the rune's value and buys at 60%,
 * his prices moving 0.1% a purchase; percentages are in tenths. The Runecraft cape shop the wiki
 * lists beside it is not here, as the isle sells no skillcapes. Needs `packCache`.
 */
internal object RuneShopEdits : InvEditor() {
    init {
        edit(RunecraftInvs.rune_shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(RunecraftObjs.air_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.fire_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.water_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.earth_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.mind_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.body_rune, count = 5000, restockCycles = 10)
            stock += stock(RunecraftObjs.chaos_rune, count = 250, restockCycles = 10)
            stock += stock(RunecraftObjs.death_rune, count = 250, restockCycles = 15)
            stock += stock(RunecraftObjs.fire_rune_pack, count = 80, restockCycles = 10)
            stock += stock(RunecraftObjs.water_rune_pack, count = 80, restockCycles = 10)
            stock += stock(RunecraftObjs.air_rune_pack, count = 80, restockCycles = 10)
            stock += stock(RunecraftObjs.earth_rune_pack, count = 80, restockCycles = 10)
            stock += stock(RunecraftObjs.mind_rune_pack, count = 40, restockCycles = 10)
            stock += stock(RunecraftObjs.chaos_rune_pack, count = 35, restockCycles = 10)
        }
    }
}

internal object RuneShopNpcEdits : NpcEditor() {
    init {
        edit(RunecraftNpcs.mage_of_zamorak) {
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 550
            param[ShopParams.shop_change_percentage] = 2
        }

        edit(RunecraftNpcs.aubury) {
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 600
            param[ShopParams.shop_change_percentage] = 1
        }
    }
}
