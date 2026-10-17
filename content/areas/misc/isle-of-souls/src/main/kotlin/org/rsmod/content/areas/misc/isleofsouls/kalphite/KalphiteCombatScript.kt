package org.rsmod.content.areas.misc.isleofsouls.kalphite

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How the Kalphite Queen takes damage, splits into her second form and dies. The queen herself is
 * driven by [KalphiteQueen]; her workers are ordinary npcs that fight with the default melee.
 *
 * The crawling form's "death" is not one: [moult] keeps the same npc and shows it as the airborne
 * form. While it lasts the npc is delayed, which makes it untargetable, and its hitpoints stay at 0
 * so a hit already in the air does nothing. Only the airborne form's death is real ([die]).
 */
class KalphiteCombatScript
@Inject
constructor(
    private val queen: KalphiteQueen,
    private val npcDeath: NpcDeath,
    private val npcRepo: NpcRepository,
    private val players: PlayerList,
    private val seqTypes: SeqTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in listOf(kalphite_npcs.queen, kalphite_npcs.airborne)) {
            // She never turns on, or walks towards, a player who hits her: the fight moves her.
            onNpcQueue(type, queues.com_retaliate_player) {}
            onNpcHit(type) {
                val attacker = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
                queen.hurt(npc, attacker)
            }
        }
        onNpcQueue(kalphite_npcs.queen, queues.death) { moult() }
        onNpcQueue(kalphite_npcs.airborne, queues.death) { die() }
    }

    /** The first form has fallen: she splits open and rises as the second. */
    private suspend fun StandardNpcAccess.moult() {
        // A second death queued by a hit that was already flying lands after the molt has ended.
        if (npc.hitpoints > 0) {
            return
        }
        if (!queen.beginMolt(npc)) {
            npcDeath.deathWithDrops(this)
            return
        }
        walk(coords)
        noneMode()
        arriveDelay()
        // The emerge animation is the airborne model's, so she is shown as that form first (tsps
        // does the same); showing it only after the delay left her stuck in the first form.
        queen.showAirborne(npc)
        anim(kalphite_seqs.emerge)
        spotanim(kalphite_spotanims.emerge)
        delay(KalphiteQueen.MOLT_TICKS)
        queen.endMolt(npc)
    }

    /** She dies where she stands; the drops fall at the middle of her 5x5. */
    private suspend fun StandardNpcAccess.die() {
        if (npc.hitpoints > 0) {
            return
        }
        queen.bossDied(npc)
        val run = queen.runOf(npc)
        if (run == null) {
            npcDeath.deathWithDrops(this)
            return
        }
        val dropAt = run.dropAt ?: coords
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(kalphite_seqs.airborne_death)
        delay(seqTypes[kalphite_seqs.airborne_death].tickDuration.takeIf { it > 0 } ?: DEATH_TICKS)
        npcDeath.spawnDrops(this, dropAt)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private companion object {
        /** Used if the death animation has no frame data to read a length from. A guess. */
        const val DEATH_TICKS = 6
    }
}
