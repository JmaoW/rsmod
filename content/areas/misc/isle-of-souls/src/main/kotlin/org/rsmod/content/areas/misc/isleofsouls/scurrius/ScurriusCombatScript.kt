package org.rsmod.content.areas.misc.isleofsouls.scurrius

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackDelays
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How Scurrius takes damage and dies, how his giant rats bite, and what a ratbane weapon does to
 * them. Scurrius himself is driven by [Scurrius]; the rats are aimed at players by it and, once
 * aimed, bite here whenever their `actionDelay` has run out.
 */
class ScurriusCombatScript
@Inject
constructor(
    private val scurrius: Scurrius,
    private val attacks: NpcAttacks,
    private val npcDeath: NpcDeath,
    private val npcRepo: NpcRepository,
    private val delays: NpcAttackDelays,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in listOf(scurrius_npcs.boss, scurrius_npcs.boss_public)) {
            // He never turns on, or walks towards, a player who hits him: the fight moves him.
            onNpcQueue(type, queues.com_retaliate_player) {}
            onNpcQueue(type, queues.death) { die() }
            // While he is eating at a pile he takes slightly less damage (wiki).
            onModifyNpcHit(type) {
                if (scurrius.isEating(npc)) {
                    hit.damage = hit.damage * EATING_DAMAGE_PERCENT / 100
                }
            }
        }

        onAiOpPlayer2(scurrius_npcs.rat) { bite(it.target) }
        onNpcQueue(scurrius_npcs.rat, queues.death) { dieRat() }

        // A ratbane weapon (the bone mace, bow and staff) has no delay against the giant rats.
        delays.register { player, target, _ ->
            if (target.type.id == scurrius_npcs.rat.id && wieldsRatbane(player)) RATBANE_DELAY
            else null
        }
    }

    private fun wieldsRatbane(player: Player): Boolean {
        val name = objTypes.getOrNull(player.righthand)?.internalName ?: return false
        return name in RATBANE
    }

    private fun StandardNpcAccess.bite(target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, ScurriusAttacks.rat, onImpact = ScurriusAttacks.ratBite)
        actionDelay = mapClock + ScurriusAttacks.RAT_RATE
    }

    /** He dies where he stands; the drops fall at the middle of his 3x3. */
    private suspend fun StandardNpcAccess.die() {
        scurrius.bossDied(npc)
        val dropAt = scurrius.runOf(npc)?.dropAt ?: coords
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(scurrius_seqs.death)
        delay(DEATH_TICKS)
        npcDeath.spawnDrops(this, dropAt)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    /** A rat dies without drops: they are not worth bones. */
    private suspend fun StandardNpcAccess.dieRat() {
        scurrius.ratDied(npc)
        npcDeath.deathNoDrops(this)
    }

    private companion object {
        const val EATING_DAMAGE_PERCENT = 90

        /** The death animation has no frame data in the cache to read a length from. A guess. */
        const val DEATH_TICKS = 6

        /** One tick: it attacks again on the next. */
        const val RATBANE_DELAY = 1

        val RATBANE = setOf("rat_bone_mace", "rat_bone_bow", "rat_bone_staff")
    }
}
