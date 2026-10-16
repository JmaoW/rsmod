package org.rsmod.content.areas.misc.isleofsouls.zulrah

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType

/**
 * Venom, the sickness Zulrah and her snakelings give. A venomed player takes damage every
 * [INTERVAL] ticks: 6 the first time, 2 more each time after, up to 20 (wiki). It lasts until it is
 * cured.
 *
 * The isle has no antivenom, so the cures are: wearing the charged serpentine helm (immune), the
 * protection of an antipoison potion (immune while it lasts). Leaving Zulrah's lair, logging out
 * and logging in do not cure it. Poison, the weaker sickness, is not modelled: nothing on the isle
 * poisons.
 */
@Singleton
class PlayerVenom
@Inject
constructor(private val potions: PotionEffects, private val random: GameRandom) {
    /** The damage of [player]'s next venom hit, or `0` if they are not venomed. */
    fun damageOf(player: Player): Int = player.vars[zulrah_vars.venom]

    fun isVenomed(player: Player): Boolean = damageOf(player) > 0

    /** Whether [player] cannot be venomed right now. */
    fun isImmune(player: Player): Boolean = wearsHelm(player) || potions.hasPoisonProtection(player)

    private fun wearsHelm(player: Player): Boolean =
        player.worn.objs.any { it != null && it.isType(zulrah_objs.serpentine_helm_charged) }

    /**
     * Envenoms [player] unless they are immune or already venomed (a second dose does not make it
     * worse). Returns whether they were envenomed now.
     */
    fun envenom(player: Player): Boolean {
        if (isVenomed(player) || isImmune(player)) {
            return false
        }
        set(player, FIRST_DAMAGE)
        player.softTimer(zulrah_timers.venom, INTERVAL)
        player.mes("You have been injected with venom!")
        return true
    }

    /**
     * A hit by Zulrah or a snakeling that did [damage] may envenom [target]: the wiki says their
     * attacks inflict venom but gives no chance, so it is one hit in [HIT_ODDS].
     */
    fun hitMayEnvenom(target: Player, damage: Int) {
        if (damage > 0 && random.of(HIT_ODDS) == 0) {
            envenom(target)
        }
    }

    /** Takes the venom away. */
    fun cure(player: Player) {
        if (!isVenomed(player)) {
            return
        }
        set(player, 0)
        player.clearSoftTimer(zulrah_timers.venom)
    }

    /**
     * The venom timer: hurts [player] and makes the next hit worse. Returns `false` to stop the
     * timer, which it does once the venom is gone.
     */
    fun tick(player: Player): Boolean {
        val damage = damageOf(player)
        if (damage <= 0) {
            return false
        }
        player.queueHit(1, HitType.Typeless, damage, hitmark_groups.venom)
        set(player, minOf(MAX_DAMAGE, damage + STEP))
        return true
    }

    /**
     * Picks a venom back up when [player] logs in. The venom is saved with the account, but timers
     * are not and the client forgets the poison orb, so the timer restarts and the orb is shown
     * again.
     */
    fun resume(player: Player) {
        if (!isVenomed(player)) {
            return
        }
        player.softTimer(zulrah_timers.venom, INTERVAL)
        set(player, damageOf(player))
    }

    private fun set(player: Player, damage: Int) {
        VarPlayerIntMapSetter.set(player, zulrah_vars.venom, damage)
        // The client's poison orb shows venom for a value from 1,000,000 up.
        val orb = if (damage > 0) VENOM_ORB_BASE + damage else 0
        VarPlayerIntMapSetter.set(player, zulrah_vars.poison_orb, orb)
    }

    companion object {
        /** The wiki: venom starts at 6, rises 2 a hit and stops at 20. */
        const val FIRST_DAMAGE = 6
        const val STEP = 2
        const val MAX_DAMAGE = 20

        /** Venom, like poison, hurts every 30 game ticks (18 seconds). */
        const val INTERVAL = 30

        const val VENOM_ORB_BASE = 1_000_000

        /** One hit in this many that does damage envenoms (a guess). */
        const val HIT_ODDS = 4
    }
}
