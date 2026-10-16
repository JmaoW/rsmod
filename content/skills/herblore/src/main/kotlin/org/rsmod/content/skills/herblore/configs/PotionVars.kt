package org.rsmod.content.skills.herblore.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences

object PotionVars : VarpReferences() {
    /** Antipoison protection left, in ticks: `0` for none. Saved on log-out. */
    val antipoison_ticks = find("antipoison_ticks")
}

/** Server-only potion variables. */
internal object PotionVarpBuilder : VarpBuilder() {
    init {
        build("antipoison_ticks") { permanent = true }
    }
}
