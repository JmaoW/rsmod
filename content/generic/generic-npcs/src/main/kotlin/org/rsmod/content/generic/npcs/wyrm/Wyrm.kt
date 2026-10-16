package org.rsmod.content.generic.npcs.wyrm

import jakarta.inject.Inject
import java.util.Collections
import java.util.IdentityHashMap
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Wyrms are spawned burrowed (a mound that idles with `wyrm_walk`). When a player hits one it rises
// into its standing form (the `_light` type, `wyrm_ready`) and fights from there. Once it has been
// out of combat for a while it burrows again. The standing form is a transmog, so it keeps its
// uid, and with it the fight in progress.
class Wyrm
@Inject
constructor(private val npcTypes: NpcTypeList, private val seqTypes: SeqTypeList) : PluginScript() {
    /** The wyrms playing their burrowing animation: they sink back into the ground next check. */
    private val burrowing: MutableSet<Npc> = Collections.newSetFromMap(IdentityHashMap())

    override fun ScriptContext.startup() {
        for (form in FORMS) {
            onNpcHit(form.burrowed) { rise(form, npc, hit.isFromPlayer) }
            onNpcHit(form.risen) { stayUp(npc) }
            onAiTimer(form.risen) { check(npc) }
        }
    }

    private fun rise(form: WyrmForm, wyrm: Npc, fromPlayer: Boolean) {
        if (!fromPlayer || wyrm.hitpoints <= 0) {
            return
        }
        val anim = form.riseAnim
        wyrm.transmog(npcTypes[form.risen], Int.MAX_VALUE)
        wyrm.anim(anim)
        // It cannot attack until it has finished getting up.
        wyrm.actionDelay = wyrm.currentMapClock + seqTypes[anim].tickDuration
        burrowing.remove(wyrm)
        wyrm.aiTimer(CHECK_TICKS)
    }

    /** A hit while it is sinking back down brings it up again. */
    private fun stayUp(wyrm: Npc) {
        if (burrowing.remove(wyrm)) {
            wyrm.anim(wyrm_seqs.transition_up)
            wyrm.aiTimer(CHECK_TICKS)
        }
    }

    private fun check(wyrm: Npc) {
        if (burrowing.remove(wyrm)) {
            wyrm.resetTransmog()
            wyrm.aiTimer(0)
        } else if (!wyrm.isInCombat()) {
            burrowing += wyrm
            wyrm.anim(wyrm_seqs.transition_down)
            wyrm.aiTimer(seqTypes[wyrm_seqs.transition_down].tickDuration)
        }
    }

    private class WyrmForm(val burrowed: NpcType, val risen: NpcType, val riseAnim: SeqType)

    private companion object {
        /** How often a standing wyrm checks whether the fight is over. */
        const val CHECK_TICKS = 8

        val FORMS =
            listOf(
                WyrmForm(wyrm_npcs.wyrm_dark, wyrm_npcs.wyrm_light, wyrm_seqs.transition_up),
                WyrmForm(
                    wyrm_npcs.superior_wyrm_dark,
                    wyrm_npcs.superior_wyrm_light,
                    wyrm_seqs.superior_transition_up,
                ),
                WyrmForm(
                    wyrm_npcs.league_superior_wyrm_dark,
                    wyrm_npcs.league_superior_wyrm_light,
                    wyrm_seqs.superior_transition_up,
                ),
            )
    }
}
