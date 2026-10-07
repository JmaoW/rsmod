package org.rsmod.content.skills.crafting.configs

import org.rsmod.game.type.obj.ObjType

/**
 * One row of the tanner interface: a [hide] that becomes [leather] for [cost] coins each. [name] is
 * the label the interface shows. Tanning gives no experience and needs no level.
 */
class TanSlot(val hide: ObjType, val leather: ObjType, val cost: Int, val name: String) {
    /** The price text under the hide, such as `1 coin` or `20 coins`. */
    val costText: String = if (cost == 1) "1 coin" else "$cost coins"
}

/**
 * What a standard tanner charges, from the wiki's Tanner page: 1 coin for leather, 3 for hard
 * leather, 15 for snakeskin from a snake hide (20 from a swamp snake hide) and 20 for each
 * dragonhide. The slot order is the order of the interface's eight hide slots.
 */
object Tanning {
    private val o = CraftingObjs

    val slots: List<TanSlot> =
        listOf(
            TanSlot(o.cow_hide, o.leather, 1, "Leather"),
            TanSlot(o.cow_hide, o.hard_leather, 3, "Hard leather"),
            TanSlot(o.snake_hide, o.snakeskin, 15, "Snakeskin"),
            TanSlot(o.swamp_snake_hide, o.snakeskin, 20, "Snakeskin"),
            TanSlot(o.green_dragonhide, o.green_dragon_leather, 20, "Green d'hide"),
            TanSlot(o.blue_dragonhide, o.blue_dragon_leather, 20, "Blue d'hide"),
            TanSlot(o.red_dragonhide, o.red_dragon_leather, 20, "Red d'hide"),
            TanSlot(o.black_dragonhide, o.black_dragon_leather, 20, "Black d'hide"),
        )
}
