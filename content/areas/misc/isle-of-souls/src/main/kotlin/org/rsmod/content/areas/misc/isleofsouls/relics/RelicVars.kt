package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.api.type.refs.varbit.VarBitReferences

object RelicVarBits : VarBitReferences() {
    /** The relic card most recently clicked, as `card + 1` (`0` means none). Server state only. */
    val last_viewed = find("league_relic_last_viewed")

    /** The chosen option (1-based) of each tier, or `0` if none has been chosen. */
    val selection = List(8) { find("league_relic_selection_$it") }
}
