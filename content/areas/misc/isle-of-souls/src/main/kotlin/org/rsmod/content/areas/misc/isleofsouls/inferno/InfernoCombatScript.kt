package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.TargetingStyle
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateAp
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateOp
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How each ordinary Inferno monster attacks and dies. The monsters are aimed at players by
 * [Inferno]; once aimed, the engine calls the handlers below every tick and they attack whenever
 * the monster's `actionDelay` has run out.
 */
class InfernoCombatScript
@Inject
constructor(
    private val inferno: Inferno,
    private val attacks: NpcAttacks,
    private val targeting: NpcTargeting,
    private val random: GameRandom,
    private val npcRepo: NpcRepository,
    private val seqTypes: SeqTypeList,
    private val interactions: AiPlayerInteractions,
) : PluginScript() {
    private val batHit = NpcImpactListener { target, hit -> batDrain(target, hit.type, hit.damage) }

    override fun ScriptContext.startup() {
        // Jad throws one attack at every player in the Inferno at once.
        targeting.setStyle(inferno_npcs.jad, TargetingStyle.All, range = ARENA_RANGE)
        targeting.setStyle(inferno_npcs.jad_final, TargetingStyle.All, range = ARENA_RANGE)

        for (def in InfernoMonsterDefs.all) {
            onAiOpPlayer2(def.type) { fight(def, it.target) }
            onAiApPlayer2(def.type) { fight(def, it.target) }
            onNpcQueue(def.type, queues.death) { die(def) }
            if (def.apRange != null) {
                // A monster that shoots fights back by shooting: it only moves if the player is
                // out of its range, instead of the engine's default of walking up to melee them.
                onNpcQueue(def.type, queues.com_retaliate_player) {
                    npc.combatDefaultRetaliateAp(interactions)
                }
            }
        }
        // Zuk and his healers never turn on, or walk towards, a player who hits them.
        for (type in listOf(inferno_npcs.zuk, inferno_npcs.zuk_healer)) {
            onNpcQueue(type, queues.com_retaliate_player) {}
        }
        // A nibbler that is hit does not fight back while there is a pillar to eat.
        onNpcQueue(inferno_npcs.nibbler, queues.com_retaliate_player) {
            if (!inferno.pillarsStanding(npc)) {
                npc.combatDefaultRetaliateOp(interactions)
            }
        }
    }

    private fun StandardNpcAccess.fight(def: InfernoMonsterDef, target: Player) {
        if (!target.isValidTarget()) {
            return
        }
        if (def === InfernoMonsterDefs.nibbler && inferno.pillarsStanding(npc)) {
            // Nibblers only turn on the players once the pillars are gone.
            noneMode()
            return
        }
        if (actionDelay > mapClock) {
            if (def === InfernoMonsterDefs.blob) {
                readPrayer(target)
            }
            return
        }
        val rate =
            when {
                def === InfernoMonsterDefs.nibbler -> melee(target, InfernoAttacks.nibbler)
                def === InfernoMonsterDefs.bat -> bat(target)
                def === InfernoMonsterDefs.blob -> blob(target)
                def === InfernoMonsterDefs.babyRanged -> shoot(target, InfernoAttacks.babyRanged)
                def === InfernoMonsterDefs.babyMagic -> shoot(target, InfernoAttacks.babyMagic)
                def === InfernoMonsterDefs.babyMelee -> melee(target, InfernoAttacks.babyMelee)
                def.kind == InfernoMonster.Melee -> {
                    inferno.noteAttack(npc)
                    melee(target, InfernoAttacks.melee)
                }
                def.kind == InfernoMonster.Ranger ->
                    meleeOrShoot(target, InfernoAttacks.rangerMelee, InfernoAttacks.rangerRanged)
                def.kind == InfernoMonster.Mager -> mager(target)
                InfernoMonsterDefs.isJad(def) -> jad(target)
                InfernoMonsterDefs.isHealer(def) -> melee(target, InfernoAttacks.healer)
                else -> null
            }
        actionDelay = mapClock + (rate ?: def.attackRate)
    }

    private fun StandardNpcAccess.melee(target: Player, attack: NpcAttack): Int? {
        attacks.attack(npc, target, attack)
        return null
    }

    private fun StandardNpcAccess.shoot(target: Player, attack: NpcAttack): Int? {
        attacks.attack(npc, target, attack)
        return null
    }

    /**
     * Next to the target it has a [MELEE_ODDS] chance of fighting in melee, otherwise (and from a
     * distance) it shoots.
     */
    private fun StandardNpcAccess.meleeOrShoot(
        target: Player,
        melee: NpcAttack,
        distant: NpcAttack,
    ): Int? {
        val useMelee = isWithinDistance(target, 1) && random.of(MELEE_ODDS) == 0
        attacks.attack(npc, target, if (useMelee) melee else distant)
        return null
    }

    /**
     * The bat's shots drain the target's run energy and combat levels unless they are protected.
     */
    private fun StandardNpcAccess.bat(target: Player): Int? {
        attacks.attack(npc, target, InfernoAttacks.bat, onImpact = batHit)
        return null
    }

    /**
     * Called when a bat's shot lands: a hit that did damage drains the target's run energy and one
     * combat level, unless Protect from Missiles is on.
     */
    fun batDrain(target: Player, type: HitType, damage: Int) {
        if (type != HitType.Ranged || damage <= 0) {
            return
        }
        if (target.vars[varbits.protect_from_missiles] == 1) {
            return
        }
        target.runEnergy = maxOf(0, target.runEnergy - BAT_RUN_DRAIN)
        target.statSub(DRAINED_STATS[random.of(DRAINED_STATS.size)], BAT_STAT_DRAIN, 0)
    }

    /** Three ticks before the blob attacks it looks at the target's prayer and picks its style. */
    private fun StandardNpcAccess.readPrayer(target: Player) {
        if (actionDelay - mapClock != InfernoBlobAi.READ_AHEAD) {
            return
        }
        inferno.stateOf(npc)?.blobStyle = blobStyle(target)
    }

    private fun StandardNpcAccess.blobStyle(target: Player): NpcAttackStyle =
        InfernoBlobAi.chooseStyle(protectionOf(target), isWithinDistance(target, 1), random::of)

    private fun protectionOf(player: Player): Protection =
        when {
            player.vars[varbits.protect_from_melee] == 1 -> Protection.Melee
            player.vars[varbits.protect_from_magic] == 1 -> Protection.Magic
            player.vars[varbits.protect_from_missiles] == 1 -> Protection.Missiles
            else -> Protection.None
        }

    /**
     * The blob attacks with the style it picked a few ticks ago, or, if it has not had the chance,
     * with one it picks now. A bite it planned is dropped if the target is no longer next to it.
     */
    private fun StandardNpcAccess.blob(target: Player): Int? {
        val state = inferno.stateOf(npc)
        var style = state?.blobStyle ?: blobStyle(target)
        state?.blobStyle = null
        if (style == NpcAttackStyle.Melee && !isWithinDistance(target, 1)) {
            style = blobStyle(target)
        }
        val attack =
            when (style) {
                NpcAttackStyle.Ranged -> InfernoAttacks.blobRanged
                NpcAttackStyle.Magic -> InfernoAttacks.blobMagic
                NpcAttackStyle.Melee -> InfernoAttacks.blobMelee
            }
        attacks.attack(npc, target, attack)
        return null
    }

    /**
     * The Jal-Zek sometimes brings back a monster that died this wave instead of attacking (not the
     * one in Zuk's set), and is busy for a while afterwards. Otherwise: magic from a distance, and
     * next to the target half magic and half stab.
     */
    private fun StandardNpcAccess.mager(target: Player): Int? {
        val run = inferno.runOf(npc)
        // The Jal-Zek of Zuk's set never revives anything (the ordinary waves' one does).
        val reviver = npc.type.id != inferno_npcs.mager_final.id
        if (run != null && reviver && random.of(REVIVE_ODDS) == 0 && inferno.revive(run, npc)) {
            return REVIVE_RECOVERY
        }
        return meleeOrShoot(target, InfernoAttacks.magerMelee, InfernoAttacks.magerMagic)
    }

    /**
     * Jad: next to a player he picks bite, rocks or fire evenly; from a distance rocks or fire. The
     * animation is the warning, and the hit checks the target protection prayer when it lands.
     * Returns the ticks until his next attack, which depends on the wave (see [Inferno]).
     */
    private fun StandardNpcAccess.jad(target: Player): Int {
        val rate = inferno.stateOf(npc)?.jadRate ?: Inferno.JAD_RATE
        val adjacent = isWithinDistance(target, 1)
        return when (random.of(if (adjacent) 3 else 2)) {
            0 -> {
                attacks.perform(npc, target, InfernoAttacks.jadMagic)
                rate
            }
            1 -> {
                attacks.perform(npc, target, InfernoAttacks.jadRanged)
                rate
            }
            else -> {
                attacks.attack(npc, target, InfernoAttacks.jadMelee)
                // A lone Jad bites faster; in the triple wave the cycle must stay steady.
                if (rate == Inferno.JAD_RATE) JAD_MELEE_RATE else rate
            }
        }
    }

    private suspend fun StandardNpcAccess.die(def: InfernoMonsterDef) {
        inferno.monsterDied(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        anim(def.deathAnim)
        delay(def.deathAnim, seqTypes)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private companion object {
        /** The whole arena is in range of Jad: it is about 30 tiles across. */
        const val ARENA_RANGE = 64

        /** The wiki: Jad can bite every 4 ticks. */
        const val JAD_MELEE_RATE = 4

        /** The wiki lists no odds, so rangers and magers melee half the time when adjacent. */
        const val MELEE_ODDS = 2

        /** The wiki: a Jal-Zek revives 1 time in 10 instead of attacking. */
        const val REVIVE_ODDS = 10

        /** The wiki: it attacks again 8 ticks after reviving. */
        const val REVIVE_RECOVERY = 8

        /** The wiki: 3 run energy per hit; run energy here counts in tenths of a percent. */
        const val BAT_RUN_DRAIN = 30

        /** "Slightly drains" combat stats (wiki): one level of one stat per hit. A guess. */
        const val BAT_STAT_DRAIN = 1

        val DRAINED_STATS =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
    }
}
