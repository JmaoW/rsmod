package org.rsmod.content.skills.thieving.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.api.type.refs.varp.VarpReferences

object ThievingSeqs : SeqReferences() {
    val pickpocket = find("human_pickpocket")
    val steal = find("human_pickuptable")
}

object ThievingSpotanims : SpotanimReferences() {
    val stunned = find("stunned_thieving")
}

object ThievingSynths : SynthReferences() {
    val pickpocket = find("pick")
}

/**
 * Wear that is kept on the player: how many pickpockets the gloves of silence have failed (they
 * wear out at [org.rsmod.content.skills.thieving.scripts.Pickpocketing.GLOVES_LIFE]) and how many
 * times the dodgy necklace has saved its wearer (it crumbles after
 * [org.rsmod.content.skills.thieving.scripts.Pickpocketing.NECKLACE_CHARGES]). Both need
 * `packCache` and a line in `.data/symbols/varp.sym`.
 */
object ThievingVarps : VarpReferences() {
    val gloves_fails = find("thieving_gloves_fails")
    val necklace_saves = find("thieving_necklace_saves")
}

internal object ThievingVarpBuilder : VarpBuilder() {
    init {
        build("thieving_gloves_fails") {}
        build("thieving_necklace_saves") {}
    }
}
