package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.api.type.refs.varbit.VarBitReferences

object RelicVarBits : VarBitReferences() {
    /** The relic card most recently clicked, as `card + 1` (`0` means none). Server state only. */
    val last_viewed = find("league_relic_last_viewed")

    /** `1` while the Golden God relic's "Noted items" shop button is switched off. */
    val shop_noted_disabled = find("league_shop_noted_items_disabled")

    /** The chosen option (1-based) of each tier, or `0` if none has been chosen. */
    val selection = List(8) { find("league_relic_selection_$it") }

    /**
     * The extra relic (1-based option) the Reloaded relic picked in each tier, or `0`. Only one
     * tier ever has one; the client counts it as active alongside that tier's own choice.
     */
    val other = List(8) { find("league_relic_selection_other_$it") }
}
