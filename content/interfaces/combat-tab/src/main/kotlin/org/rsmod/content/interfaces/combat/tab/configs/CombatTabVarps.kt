package org.rsmod.content.interfaces.combat.tab.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences

typealias combat_varps = CombatTabVarps

object CombatTabVarps : VarpReferences() {
    val saved_attackstyle1 = find("weapons_saved_attackstyle1")
    val saved_attackstyle2 = find("weapons_saved_attackstyle2")

    /** Read by the client's spell picker to choose which spells to show, and where. */
    val autocast_layout = find("autocast_layout")
}

object CombatTabVarpBuilder : VarpBuilder() {
    init {
        build("weapons_saved_attackstyle1")
        build("weapons_saved_attackstyle2")
    }
}
