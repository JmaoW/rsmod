package org.rsmod.content.generic.npcs.wyrm

import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences

internal typealias wyrm_npcs = WyrmNpcs

internal typealias wyrm_seqs = WyrmSeqs

object WyrmNpcs : NpcReferences() {
    val wyrm_dark = find("wyrm_dark")
    val wyrm_light = find("wyrm_light")
    val superior_wyrm_dark = find("superior_wyrm_dark")
    val superior_wyrm_light = find("superior_wyrm_light")
    val league_superior_wyrm_dark = find("league_superior_wyrm_dark")
    val league_superior_wyrm_light = find("league_superior_wyrm_light")
}

object WyrmSeqs : SeqReferences() {
    val transition_up = find("wyrm_transition_up")
    val transition_down = find("wyrm_transition_down")
    val superior_transition_up = find("superior_wyrm_transition_up")
}
