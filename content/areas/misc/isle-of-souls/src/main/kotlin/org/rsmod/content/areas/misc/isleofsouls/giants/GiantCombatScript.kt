package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How Obor and Bryophyta take damage and die, and how the growthlings bite and fall. The fights
 * themselves are [Obor] and [Bryophyta].
 */
class GiantCombatScript
@Inject
constructor(
    private val obor: Obor,
    private val bryophyta: Bryophyta,
    private val npcDeath: NpcDeath,
    private val npcRepo: NpcRepository,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in listOf(giant_npcs.obor, giant_npcs.bryophyta)) {
            // They never turn on, or walk towards, a player who hits them: the fight moves them.
            onNpcQueue(type, queues.com_retaliate_player) {}
        }
        onNpcQueue(giant_npcs.obor, queues.death) { die(obor, giant_seqs.obor_death) }
        onNpcQueue(giant_npcs.bryophyta, queues.death) {
            die(bryophyta, giant_seqs.bryophyta_death)
        }

        onAiOpPlayer2(giant_npcs.growthling) { bryophyta.growthlingBite(npc, it.target) }
        onNpcQueue(giant_npcs.growthling, queues.death) { dieGrowthling() }

        onEvent<NpcHitEvents.ModifyAny> {
            when (npc.type.id) {
                giant_npcs.growthling.id -> prune(npc, hit, source)
                giant_npcs.bryophyta.id -> if (bryophyta.isImmune(npc)) hit.damage = 0
                giant_npcs.obor.id -> if (!reachesObor(npc, source)) hit.damage = 0
            }
        }
    }

    /** Only a player in Obor's pit can hurt him: the safe zone is out of the fight. */
    private fun reachesObor(npc: Npc, source: Player?): Boolean {
        val run = obor.runOf(npc) ?: return true
        return source == null || obor.inPit(run, source)
    }

    /**
     * An axe or magic secateurs equipped kills a growthling at once; anything else can hurt it but
     * never kill it (wiki).
     */
    private fun prune(npc: Npc, hit: HitBuilder, source: Player?) {
        if (source == null) {
            return
        }
        if (canPrune(source)) {
            source.mes(PRUNE_MESSAGE)
            hit.damage = maxOf(1, npc.hitpoints)
            return
        }
        source.mes(NO_AXE_MESSAGE)
        hit.damage = minOf(hit.damage, maxOf(0, npc.hitpoints - 1))
    }

    private fun canPrune(player: Player): Boolean {
        val weapon = objTypes.getOrNull(player.righthand) ?: return false
        return GiantAxes.cutsGrowthlings(weapon.id, weapon.name)
    }

    /** They die where they stand; the drops fall at the middle of their body. */
    private suspend fun StandardNpcAccess.die(director: GiantDirector<*>, death: SeqType) {
        director.bossDied(npc)
        val dropAt = director.runOf(npc)?.dropAt ?: coords
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(death)
        delay(DEATH_TICKS)
        npcDeath.spawnDrops(this, dropAt)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    /** A growthling dies without drops. */
    private suspend fun StandardNpcAccess.dieGrowthling() {
        bryophyta.minionDied(npc)
        npcDeath.deathNoDrops(this)
    }

    private companion object {
        /** The death animations have no frame data in the cache to read a length from. A guess. */
        const val DEATH_TICKS = 5

        const val PRUNE_MESSAGE = "You prune the Growthling."
        const val NO_AXE_MESSAGE = "Cut the growthling down with an axe or secateurs."
    }
}

/** Which weapons kill a growthling: a woodcutting axe, or magic secateurs (wiki). */
object GiantAxes {
    /**
     * Woodcutting axes end in "axe"; battleaxes, pickaxes, the zombie axe and the blessed axe are
     * not (wiki).
     */
    private val AXE_NAME = Regex("(^|\\s)axe(\\s\\(.*\\))?$", RegexOption.IGNORE_CASE)
    private val NOT_AN_AXE = Regex("^(zombie|blessed) axe", RegexOption.IGNORE_CASE)

    fun isAxe(name: String): Boolean =
        AXE_NAME.containsMatchIn(name) && !NOT_AN_AXE.containsMatchIn(name)

    fun cutsGrowthlings(id: Int, name: String): Boolean =
        id == giant_objs.secateurs_magic.id || id == giant_objs.secateurs_magic_nz.id || isAxe(name)
}
