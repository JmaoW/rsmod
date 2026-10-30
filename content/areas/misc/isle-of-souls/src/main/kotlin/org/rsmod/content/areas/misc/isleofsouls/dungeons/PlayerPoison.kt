package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.zulrah.zulrah_vars
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

object PoisonTimers : TimerReferences() {
    /** Poison: ticks on the poisoned player every 30 game ticks. */
    val poison = find("godwars_poison")
}

object PoisonVars : VarpReferences() {
    /** The damage of the player's next poison hit: `0` for none. */
    val damage = find("isle_godwars_poison")

    /** How many poison hits have landed at the current damage (the damage drops every five). */
    val hits = find("isle_godwars_poison_hits")

    /** The client's poison orb: a value from 1 to 999,999 shows poison. */
    val orb = find("poison")
}

internal object PoisonVarpBuilder : VarpBuilder() {
    init {
        build("isle_godwars_poison") { permanent = true }
        build("isle_godwars_poison_hits") { permanent = true }
    }
}

/**
 * Poison, the weaker sickness (OSRS Wiki, "Poison"): K'ril Tsutsaroth's swipe can give it, starting
 * at 16. A poisoned player takes that much damage every [INTERVAL] ticks, and the damage drops by 1
 * after every [HITS_PER_STEP] hits until it reaches 0 and the poison ends. A second dose while
 * poisoned changes nothing. The protection of an antipoison potion stops it, and drinking one cures
 * any that is there (see `SicknessScript`). It is saved on log-out and picks up again on log-in.
 */
@Singleton
class PlayerPoison @Inject constructor(private val potions: PotionEffects) {
    fun damageOf(player: Player): Int = player.vars[PoisonVars.damage]

    fun isPoisoned(player: Player): Boolean = damageOf(player) > 0

    /**
     * Poisons [player] at [damage] unless they are protected, already poisoned or envenomed (venom
     * takes the place of poison, the two never run together).
     */
    fun poison(player: Player, damage: Int): Boolean {
        if (isPoisoned(player) || potions.hasPoisonProtection(player) || isVenomed(player)) {
            return false
        }
        set(player, damage, hits = 0)
        player.softTimer(PoisonTimers.poison, INTERVAL)
        player.mes("You have been poisoned!")
        return true
    }

    fun cure(player: Player) {
        if (!isPoisoned(player)) {
            return
        }
        set(player, 0, hits = 0)
        player.clearSoftTimer(PoisonTimers.poison)
    }

    /** Poison that venom turns into when it is treated: the same damage, whatever protects. */
    fun poisonFromVenom(player: Player, damage: Int) {
        if (isPoisoned(player)) {
            return
        }
        set(player, minOf(damage, MAX_DAMAGE), hits = 0)
        player.softTimer(PoisonTimers.poison, INTERVAL)
    }

    /** Takes 1 off the damage of each poison hit, which cures it when that reaches 0. */
    fun weaken(player: Player) {
        val damage = damageOf(player)
        when {
            damage <= 0 -> return
            damage == 1 -> cure(player)
            else -> set(player, damage - 1, hits = player.vars[PoisonVars.hits])
        }
    }

    /**
     * The poison timer: hurts [player] and weakens the poison. Returns `false` to stop the timer,
     * which it does once the poison is gone or has been cured.
     */
    fun tick(player: Player): Boolean {
        val damage = damageOf(player)
        if (damage <= 0) {
            return false
        }
        player.queueHit(1, HitType.Typeless, damage, hitmark_groups.poison_damage)
        val hits = player.vars[PoisonVars.hits] + 1
        if (hits >= HITS_PER_STEP) {
            val next = damage - 1
            set(player, next, hits = 0)
            return next > 0
        }
        VarPlayerIntMapSetter.set(player, PoisonVars.hits, hits)
        return true
    }

    /**
     * Picks a poison back up when [player] logs in. The poison is saved with the account, but
     * timers are not and the client forgets the poison orb, so the timer restarts and the orb is
     * shown again.
     */
    fun resume(player: Player) {
        if (!isPoisoned(player)) {
            return
        }
        player.softTimer(PoisonTimers.poison, INTERVAL)
        set(player, damageOf(player), hits = player.vars[PoisonVars.hits])
    }

    private fun isVenomed(player: Player): Boolean = player.vars[zulrah_vars.venom] > 0

    private fun set(player: Player, damage: Int, hits: Int) {
        VarPlayerIntMapSetter.set(player, PoisonVars.damage, damage)
        VarPlayerIntMapSetter.set(player, PoisonVars.hits, hits)
        VarPlayerIntMapSetter.set(player, PoisonVars.orb, damage)
    }

    companion object {
        /** Poison hurts every 30 game ticks (18 seconds). */
        const val INTERVAL = 30

        /** The most a poison hit does, the damage K'ril's swipe starts at. */
        const val MAX_DAMAGE = 16

        /** The damage drops by 1 after this many hits. */
        const val HITS_PER_STEP = 5
    }
}
