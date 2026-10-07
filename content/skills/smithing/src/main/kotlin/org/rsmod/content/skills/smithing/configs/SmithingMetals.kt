package org.rsmod.content.skills.smithing.configs

import org.rsmod.game.type.obj.ObjType

/**
 * The metals an anvil works with.
 *
 * @property barType the value of `smithing_bar_type` that makes the anvil interface show this
 *   metal's items. These are the keys of the client's `smithing_bar_type` enum, which maps each one
 *   to its bar.
 * @property xpPerBar every anvil item gives its bar cost times this much experience.
 */
internal enum class SmithingMetal(
    val barType: Int,
    val bar: ObjType,
    val xpPerBar: Double,
    val noun: String,
) {
    Bronze(1, SmithingObjs.bronze_bar, 12.5, "bronze"),
    Iron(2, SmithingObjs.iron_bar, 25.0, "iron"),
    Steel(3, SmithingObjs.steel_bar, 37.5, "steel"),
    Mithril(4, SmithingObjs.mithril_bar, 50.0, "mithril"),
    Adamant(5, SmithingObjs.adamantite_bar, 62.5, "adamant"),
    Rune(6, SmithingObjs.runite_bar, 75.0, "rune");

    companion object {
        /** The metal whose bar is [bar], or `null` if the anvil cannot work it. */
        fun forBar(bar: ObjType): SmithingMetal? = entries.firstOrNull { it.bar.id == bar.id }
    }
}
