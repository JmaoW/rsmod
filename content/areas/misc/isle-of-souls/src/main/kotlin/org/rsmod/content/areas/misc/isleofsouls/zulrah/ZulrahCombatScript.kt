package org.rsmod.content.areas.misc.isleofsouls.zulrah

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcMeleeBlocks
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateAp
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How Zulrah takes damage and dies, and how her snakelings fight. Zulrah herself is driven by
 * [Zulrah]; the snakelings are aimed at players by it and, once aimed, attack here whenever their
 * `actionDelay` has run out.
 */
class ZulrahCombatScript
@Inject
constructor(
    private val zulrah: Zulrah,
    private val attacks: NpcAttacks,
    private val venom: PlayerVenom,
    private val players: PlayerList,
    private val npcRepo: NpcRepository,
    private val npcDeath: NpcDeath,
    private val seqTypes: SeqTypeList,
    private val interactions: AiPlayerInteractions,
    private val npcTypes: NpcTypeList,
    private val meleeBlocks: NpcMeleeBlocks,
) : PluginScript() {
    private val venomOnHit = NpcImpactListener { target, hit ->
        venom.hitMayEnvenom(target, hit.damage)
    }

    override fun ScriptContext.startup() {
        for (form in ZulrahForm.entries) {
            val type = zulrah_npcs.of(form)
            // Only a halberd's long reach gets round the water she swims in.
            meleeBlocks.block(npcTypes[type], reachable = true)
            // She never turns on, or walks towards, a player who hits her.
            onNpcQueue(type, queues.com_retaliate_player) {}
            onNpcQueue(type, queues.death) { die() }
        }

        onAiOpPlayer2(zulrah_npcs.snakeling_melee) { fight(it.target, magic = false) }
        onAiApPlayer2(zulrah_npcs.snakeling_magic) { fight(it.target, magic = true) }
        onNpcQueue(zulrah_npcs.snakeling_melee, queues.death) { dieSnakeling() }
        onNpcQueue(zulrah_npcs.snakeling_magic, queues.death) { dieSnakeling() }
        // A magic snakeling fights back by casting: it only moves if the player is out of range.
        onNpcQueue(zulrah_npcs.snakeling_magic, queues.com_retaliate_player) {
            npc.combatDefaultRetaliateAp(interactions)
        }
    }

    private fun StandardNpcAccess.fight(target: Player, magic: Boolean) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val attack: NpcAttack =
            if (magic) ZulrahAttacks.snakelingMagic else ZulrahAttacks.snakelingMelee
        attacks.attack(npc, target, attack, onImpact = venomOnHit)
        actionDelay = mapClock + ZulrahAttacks.SNAKELING_RATE
    }

    /**
     * Zulrah dies where she is, in the water, so her drops go where the player who did the most
     * damage stands (or at the start of the platform if nobody can be found).
     */
    private suspend fun StandardNpcAccess.die() {
        zulrah.bossDied(npc)
        val hero = npc.findHero(players)
        val dropAt: CoordGrid =
            hero?.coords?.takeIf { zulrah.runOf(npc)?.instance?.contains(it) == true }
                ?: zulrah.runOf(npc)?.instance?.region?.normal?.get(ZulrahArena.start)
                ?: coords
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(zulrah_seqs.death)
        delay(zulrah_seqs.death, seqTypes)
        npcDeath.spawnDrops(this, dropAt)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private suspend fun StandardNpcAccess.dieSnakeling() {
        zulrah.snakelingDied(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(zulrah_seqs.snakeling_death)
        delay(zulrah_seqs.snakeling_death, seqTypes)
        npcRepo.del(npc, Int.MAX_VALUE)
    }
}
