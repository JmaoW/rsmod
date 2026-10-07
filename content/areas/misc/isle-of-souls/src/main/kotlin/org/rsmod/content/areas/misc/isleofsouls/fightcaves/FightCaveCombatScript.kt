package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.TargetingStyle
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How each cave monster attacks and dies. The monsters are aimed at players by [FightCaves]; once
 * aimed, the engine calls the handlers below every tick and they attack whenever the monster's
 * `actionDelay` has run out.
 */
class FightCaveCombatScript
@Inject
constructor(
    private val caves: FightCaves,
    private val attacks: NpcAttacks,
    private val targeting: NpcTargeting,
    private val random: GameRandom,
    private val npcRepo: NpcRepository,
    private val seqTypes: SeqTypeList,
    private val playerList: PlayerList,
    private val completions: FightCaveCompletions,
) : PluginScript() {
    private val jadImpact = NpcImpactListener { target, hit -> completions.jadHit(target, hit) }

    override fun ScriptContext.startup() {
        // Jad throws one attack at every player in the cave at once.
        targeting.setStyle(cave_npcs.jad, TargetingStyle.All, range = CAVE_RANGE)

        for (def in FightCaveMonsterDefs.all) {
            onAiOpPlayer2(def.type) { fight(def, it.target) }
            onAiApPlayer2(def.type) { fight(def, it.target) }
            onNpcQueue(def.type, queues.death) { die(def) }
        }

        // Both Tz-Keks (the big one and the small ones it leaves) recoil melee hits.
        onNpcHit(cave_npcs.tz_kek) { recoil() }
        onNpcHit(cave_npcs.tz_kek_spawn) { recoil() }
    }

    private fun StandardNpcAccess.fight(def: FightCaveMonsterDef, target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val rate =
            when (def.kind) {
                FightCaveMonster.TzKih -> melee(target, FightCaveAttacks.tzKih, drain = true)
                FightCaveMonster.TzKek ->
                    melee(
                        target,
                        if (def === FightCaveMonsterDefs.tzKekSpawn) FightCaveAttacks.tzKekSmall
                        else FightCaveAttacks.tzKek,
                    )
                FightCaveMonster.TokXil ->
                    meleeOrRanged(
                        target,
                        FightCaveAttacks.tokXilMelee,
                        FightCaveAttacks.tokXilRanged,
                    )
                FightCaveMonster.KetZek ->
                    meleeOrRanged(
                        target,
                        FightCaveAttacks.ketZekMelee,
                        FightCaveAttacks.ketZekMagic,
                    )
                FightCaveMonster.YtMejKot ->
                    if (def === FightCaveMonsterDefs.ytHurKot) {
                        melee(target, FightCaveAttacks.ytHurKot)
                    } else {
                        healOrMelee(target)
                    }
                FightCaveMonster.Jad -> jad(target)
            }
        actionDelay = mapClock + (rate ?: def.attackRate)
    }

    /** A melee attack. Tz-Kih also drains the target's prayer. */
    private fun StandardNpcAccess.melee(
        target: Player,
        attack: NpcAttack,
        drain: Boolean = false,
    ): Int? {
        attacks.attack(npc, target, attack)
        if (drain) {
            target.statSub(stats.prayer, FightCaveAttacks.TZ_KIH_PRAYER_DRAIN, 0)
        }
        return null
    }

    /**
     * Next to the target it has a [MELEE_ODDS] chance of fighting in melee, otherwise (and from a
     * distance) it uses its ranged or magic attack.
     */
    private fun StandardNpcAccess.meleeOrRanged(
        target: Player,
        melee: NpcAttack,
        distant: NpcAttack,
    ): Int? {
        val useMelee = isWithinDistance(target, 1) && random.of(MELEE_ODDS) == 0
        attacks.attack(npc, target, if (useMelee) melee else distant)
        return null
    }

    /**
     * Yt-MejKot sometimes heals itself or a hurt monster next to it instead of attacking, once a
     * monster is below half health.
     */
    private fun StandardNpcAccess.healOrMelee(target: Player): Int? {
        val hurt =
            caves.runOf(npc)?.monsters?.filter { isHurt(it) && isWithinDistance(it, HEAL_RANGE) }
        if (!hurt.isNullOrEmpty() && random.of(HEAL_ODDS) == 0) {
            val patient = hurt.minBy { it.hitpoints }
            npc.anim(cave_seqs.yt_mejkot_heal)
            patient.spotanim(cave_spotanims.heal)
            patient.hitpoints = minOf(patient.baseHitpointsLvl, patient.hitpoints + HEAL_AMOUNT)
            return null
        }
        return melee(target, FightCaveAttacks.ytMejKot)
    }

    /**
     * A Tz-Kek hurts any player who hits it with melee for [TZ_KEK_RECOIL] damage. Ranged and magic
     * hits (and melee hits that do no damage) cause no recoil.
     */
    private fun NpcHitEvents.Impact.recoil() {
        if (hit.type != HitType.Melee || hit.damage <= 0 || !hit.isFromPlayer) {
            return
        }
        val attacker = hit.resolvePlayerSource(playerList) ?: return
        if (attacker.isValidTarget()) {
            attacker.queueHit(npc, delay = 1, HitType.Typeless, TZ_KEK_RECOIL)
        }
    }

    private fun isHurt(npc: Npc): Boolean = npc.hitpoints * 2 < npc.baseHitpointsLvl

    /**
     * Jad: next to a player he picks bite, rocks or fire evenly; from a distance rocks or fire. The
     * animation is the warning, and the hit checks the target's protection prayer when it lands.
     */
    private fun StandardNpcAccess.jad(target: Player): Int? {
        val adjacent = isWithinDistance(target, 1)
        val roll = random.of(if (adjacent) 3 else 2)
        return when (roll) {
            0 -> {
                attacks.perform(npc, target, FightCaveAttacks.jadMagic, jadImpact)
                null
            }
            1 -> {
                attacks.perform(npc, target, FightCaveAttacks.jadRanged, jadImpact)
                null
            }
            else -> {
                attacks.attack(npc, target, FightCaveAttacks.jadMelee)
                JAD_MELEE_RATE
            }
        }
    }

    private suspend fun StandardNpcAccess.die(def: FightCaveMonsterDef) {
        caves.monsterDied(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(def.deathAnim)
        delay(def.deathAnim, seqTypes)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private companion object {
        /** The whole cave is in range of Jad: it is about 55 tiles across. */
        const val CAVE_RANGE = 64
        const val JAD_MELEE_RATE = 4
        const val TZ_KEK_RECOIL = 1
        /** The wiki lists no odds, so Tok-Xil and Ket-Zek melee half the time when adjacent. */
        const val MELEE_ODDS = 2
        const val HEAL_RANGE = 6
        const val HEAL_ODDS = 3
        const val HEAL_AMOUNT = 10
    }
}
