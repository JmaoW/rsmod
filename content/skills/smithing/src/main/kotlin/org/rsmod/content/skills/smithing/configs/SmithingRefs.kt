package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences

internal object SmithingObjs : ObjReferences() {
    val hammer = find("hammer")

    val copper_ore = find("copper_ore")
    val tin_ore = find("tin_ore")
    val iron_ore = find("iron_ore")
    val silver_ore = find("silver_ore")
    val gold_ore = find("gold_ore")
    val mithril_ore = find("mithril_ore")
    val adamantite_ore = find("adamantite_ore")
    val runite_ore = find("runite_ore")
    val coal = find("coal")

    val bronze_bar = find("bronze_bar")
    val iron_bar = find("iron_bar")
    val silver_bar = find("silver_bar")
    val steel_bar = find("steel_bar")
    val gold_bar = find("gold_bar")
    val mithril_bar = find("mithril_bar")
    val adamantite_bar = find("adamantite_bar")
    val runite_bar = find("runite_bar")
}

internal object SmithingSeqs : SeqReferences() {
    val smelt = find("human_furnace")
    val smith = find("human_smithing")
}

internal object SmithingInterfaces : InterfaceReferences() {
    val anvil = find("smithing")
}

object SmithingComponents : ComponentReferences() {
    /**
     * The item buttons of the anvil interface, in the order the client lays them out. A product's
     * `slot` is its index in this list.
     */
    val slots =
        listOf(
            find("smithing:dagger"),
            find("smithing:sword"),
            find("smithing:scimitar"),
            find("smithing:longsword"),
            find("smithing:2h"),
            find("smithing:axe"),
            find("smithing:mace"),
            find("smithing:warhammer"),
            find("smithing:battleaxe"),
            find("smithing:claws"),
            find("smithing:chainbody"),
            find("smithing:platelegs"),
            find("smithing:plateskirt"),
            find("smithing:platebody"),
            find("smithing:nails"),
            find("smithing:medhelm"),
            find("smithing:fullhelm"),
            find("smithing:squareshield"),
            find("smithing:kiteshield"),
            find("smithing:other_2"),
            find("smithing:darttips"),
            find("smithing:arrowheads"),
            find("smithing:knives"),
            find("smithing:other_1"),
            find("smithing:other_3"),
            find("smithing:bolts"),
            find("smithing:limbs"),
        )
}

internal object SmithingVarBits : VarBitReferences() {
    /** Which metal the anvil interface shows items for, see [SmithingMetal.barType]. */
    val bar_type = find("smithing_bar_type")
}
