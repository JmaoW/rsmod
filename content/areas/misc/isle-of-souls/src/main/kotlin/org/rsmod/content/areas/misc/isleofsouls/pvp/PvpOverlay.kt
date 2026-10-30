package org.rsmod.content.areas.misc.isleofsouls.pvp

import org.rsmod.api.type.builders.clientscript.ClientScriptBuilder
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences

typealias pvp_varps = PvpVarps

object PvpVarps : VarpReferences() {
    /**
     * The level the client's Wilderness overlay shows while the player is in a PvP zone, or 0 when
     * they are not. See [PvpOverlayScripts].
     */
    val overlay = find("isle_pvp_overlay")
}

internal object PvpVarpBuilder : VarpBuilder() {
    init {
        build("isle_pvp_overlay") {
            temporary = true
            transmitAlways = true
        }
    }
}

/**
 * Packs the clientscript that lets the server turn the Wilderness overlay on.
 *
 * The overlay (the `pvp_icons` interface: the skull, "Level: N" and the combat level range) works
 * out the Wilderness level on the client, from the player's coordinates and the Wilderness's
 * regions written into `[proc,wilderness_level]`, so the server cannot make it show anywhere else.
 * The file is that proc from revision 233 with six instructions added at the start, which return
 * the value of the varp [PvpVarps.overlay] when it is above 0
 * (`tools/cs2/patch_wilderness_level.py` writes it). The proc is only called by the overlay's own
 * scripts, so nothing else is affected.
 *
 * The overlay's skull has a red "no" circle over it, which `[proc,pvp_icons_layout]` makes opaque
 * every time it draws in an ordinary world, so the skull always reads as disabled. That proc is
 * patched too (`tools/cs2/patch_pvp_icons_layout.py`): a block before its final return hides the
 * circle while [PvpVarps.overlay] is above 0, so inside a PvP zone the skull is shown plain.
 *
 * The server sets the varp and runs `[clientscript,combat_wilderness_transmit]` (390) when a player
 * enters or leaves a PvP zone, which is what the real game does for the Wilderness: it sets the
 * client flag the overlay checks and redraws it.
 *
 * The client reads scripts and varp definitions from the cache, so this needs `./gradlew packCache`
 * to reach it.
 */
object PvpOverlayScripts : ClientScriptBuilder() {
    override fun onPackCs2Task() {
        resourceFile<PvpOverlayScripts>("cs2/[proc,wilderness_level]")
        resourceFile<PvpOverlayScripts>("cs2/[proc,pvp_icons_layout]")
    }
}
