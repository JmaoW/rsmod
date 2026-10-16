package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcMeleeBlocks
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateAp
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.NpcTInteractions
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionNpcT
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object KrakenNpcs : NpcReferences() {
    /** The "Whirlpool" the cove's cave krakens hide as; "Disturb" brings one up. */
    val whirlpool = find("slayer_kraken_sub")
    val cave_kraken = find("slayer_kraken")
}

object KrakenSeqs : SeqReferences() {
    /** The kraken rising out of the water. */
    val arise = find("slayer_kraken_arise")

    /**
     * The kraken's cast. The cache names the animations of the kraken's model after the Swan Queen,
     * and the attack anim derived into `npc_anims.toml` picked its melee swing instead of the cast.
     */
    val cast = find("swan_queen_spellcast")
}

/**
 * The cave kraken sits in the water and casts from a distance, so it needs a range to cast from and
 * no way to wander or chase. Needs `packCache`.
 */
internal object KrakenNpcEdits : NpcEditor() {
    init {
        edit(KrakenNpcs.cave_kraken) {
            attackRange = DungeonKrakenScript.ATTACK_RANGE
            giveChase = false
            defaultMode = NpcMode.None
        }
    }
}

/**
 * Kraken Cove's whirlpools. The map spawns every cave kraken as a whirlpool with a "Disturb"
 * option, which is its attack option: disturbing one (or casting a spell on it) fires an attack at
 * it, ranged or magic, and melee cannot reach it. When the attack lands the whirlpool is hidden and
 * a real cave kraken rises in its place and fights the player. The damage of the hit that disturbed
 * it, and of any other hit already in the air, goes to the kraken instead of the whirlpool. When
 * the kraken dies the whirlpool comes back after a while. The kraken is the Wiki's: magic, a hit of
 * up to 13 every 6 ticks, and it does not attack unless it is disturbed.
 *
 * The Kraken boss's own whirlpools (the `Crevice` room) are not handled here.
 */
class DungeonKrakenScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val seqTypes: SeqTypeList,
    private val attacks: NpcAttacks,
    private val interactions: AiPlayerInteractions,
    private val playerList: PlayerList,
    private val meleeBlocks: NpcMeleeBlocks,
    private val hitModifier: NpcHitModifier,
    private val npcDeath: NpcDeath,
    private val npcInteractions: NpcInteractions,
    private val npcTInteractions: NpcTInteractions,
) : PluginScript() {
    /** Each revealed kraken and the whirlpool it came out of. */
    private val whirlpools = java.util.IdentityHashMap<Npc, Npc>()

    /** The damage of the player hits on each whirlpool that are in the air, in order. */
    private val incoming = java.util.IdentityHashMap<Npc, ArrayDeque<Int>>()

    override fun ScriptContext.startup() {
        // "Disturb" is the whirlpool's attack option, so the default combat code does the
        // attacking: a weapon or a spell fires at it. The hit does no damage to the whirlpool,
        // brings the kraken up, and its damage is dealt to the kraken. Melee cannot reach a
        // whirlpool in the water.
        meleeBlocks.block(npcTypes[KrakenNpcs.whirlpool])
        onModifyNpcHit(KrakenNpcs.whirlpool) { takeDamage(npc, hit) }
        onNpcHit(KrakenNpcs.whirlpool) { disturbedBy(npc, hit) }
        // A kraken casts from range, and fights back by casting; next to a player it casts as well.
        onAiOpPlayer2(KrakenNpcs.cave_kraken) { fight(it.target) }
        onAiApPlayer2(KrakenNpcs.cave_kraken) { fight(it.target) }
        onNpcQueue(KrakenNpcs.cave_kraken, queues.com_retaliate_player) {
            npc.combatDefaultRetaliateAp(interactions)
            engageAdjacent(npc)
        }
        onNpcQueue(KrakenNpcs.cave_kraken, queues.death) { die() }
        onEvent<NpcDeathEvents.Killed> {
            whirlpools.remove(npc)?.let { npcRepo.despawn(it, returnTicks()) }
        }
    }

    /** The kraken dies, and its drops fall under the player who got the kill, who is in range. */
    private suspend fun StandardNpcAccess.die() {
        npcDeath.deathNoDrops(this)
        val hero = npc.findHero(playerList)
        npcDeath.spawnDrops(this, hero?.coords ?: coords)
    }

    /** Keeps the damage of a player's hit for the kraken, and leaves the whirlpool unharmed. */
    private fun takeDamage(whirlpool: Npc, hit: HitBuilder) {
        if (hit.isFromPlayer) {
            incoming.getOrPut(whirlpool) { ArrayDeque() }.addLast(hit.damage)
        }
        hit.damage = 0
    }

    /**
     * The first hit to land brings the kraken up, and a hidden whirlpool never takes the hits that
     * are still in the air, so all of them are dealt to the kraken: this one now, the rest a tick
     * apart.
     */
    private fun disturbedBy(whirlpool: Npc, hit: Hit) {
        if (!hit.isFromPlayer) {
            return
        }
        val player = hit.resolvePlayerSource(playerList) ?: return
        val damages = incoming.remove(whirlpool) ?: return
        val kraken = reveal(whirlpool, player)
        continueCombat(player, whirlpool, kraken)
        for ((index, damage) in damages.withIndex()) {
            kraken.queueHit(
                player,
                delay = index + 1,
                type = hit.type,
                damage = damage,
                modifier = hitModifier,
            )
            if (damage > 0) {
                kraken.heroPoints(player, damage)
            }
        }
    }

    /**
     * The player keeps attacking, now the kraken, the way they were attacking the whirlpool (with
     * their weapon or autocast, or by casting the same spell), without clicking it again. Anyone
     * who was doing something else is left alone.
     */
    private fun continueCombat(player: Player, whirlpool: Npc, kraken: Npc) {
        when (val interaction = player.interaction) {
            is InteractionNpcOp ->
                if (interaction.target === whirlpool) {
                    npcInteractions.interact(player, kraken, interaction.op)
                }
            is InteractionNpcT ->
                if (interaction.target === whirlpool) {
                    npcTInteractions.interact(
                        player,
                        kraken,
                        interaction.component,
                        interaction.comsub,
                        interaction.objType,
                    )
                }
            else -> {}
        }
    }

    /**
     * How long the whirlpool takes to come back: the kraken's respawn, so the global speed applies.
     */
    private fun returnTicks(): Int = npcRepo.respawnSpeed.scale(WHIRLPOOL_RETURN_TICKS)

    /** Hides [whirlpool], raises a cave kraken on its tile and sets it on [target]. */
    fun reveal(whirlpool: Npc, target: Player): Npc {
        val kraken = Npc(npcTypes[KrakenNpcs.cave_kraken], whirlpool.coords)
        // The kraken has a lifetime (and the whirlpool comes back with it): a permanent npc would
        // respawn as a kraken when killed. Its death brings the whirlpool back sooner.
        npcRepo.add(kraken, KRAKEN_LIFETIME)
        npcRepo.despawn(whirlpool, KRAKEN_LIFETIME + returnTicks())
        whirlpools[kraken] = whirlpool
        incoming.remove(whirlpool)
        kraken.anim(KrakenSeqs.arise)
        kraken.actionDelay = whirlpool.currentMapClock + seqTypes[KrakenSeqs.arise].tickDuration
        kraken.apPlayer2(target, interactions)
        engageAdjacent(kraken)
        return kraken
    }

    /**
     * A caster's approach mode never fires for a player right next to it, so a kraken whose target
     * is adjacent fights from op range instead.
     */
    private fun engageAdjacent(kraken: Npc) {
        val interaction = kraken.interaction as? InteractionPlayer ?: return
        val target = interaction.uid.resolve(playerList) ?: return
        if (kraken.isWithinDistance(target, 1)) {
            kraken.opPlayer2(target, interactions)
        }
    }

    private fun StandardNpcAccess.fight(target: Player) {
        if (actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, MAGIC)
        actionDelay = mapClock + ATTACK_RATE
    }

    companion object {
        private val MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = KrakenSeqs.cast,
                projectile = spotanims.waterwave_travel,
                projanim = projanims.magic_spell,
                impactSpot = spotanims.waterwave_impact,
                maxHit = 13,
            )

        const val ATTACK_RATE = 6

        /** How far a kraken casts from. */
        const val ATTACK_RANGE = 10

        /** How long a revealed kraken stays up if nobody kills it: half an hour. */
        const val KRAKEN_LIFETIME = 3000

        /** How long a killed kraken's whirlpool stays gone. */
        const val WHIRLPOOL_RETURN_TICKS = 50
    }
}
