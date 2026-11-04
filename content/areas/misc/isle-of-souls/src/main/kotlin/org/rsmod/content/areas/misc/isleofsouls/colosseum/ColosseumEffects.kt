package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.config.refs.hitmarks
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark

/**
 * What the modifiers that change the players or the monsters' blows do (wiki, "Fortis
 * Colosseum/Modifiers"):
 * - Blasphemy: prayer is drained by 20/40/60% of the damage taken.
 * - Doom: a stack per hit taken, shown in the Doom hitsplat with the count; 15/10/5 stacks kill.
 *   Stacks clear when a wave is completed.
 * - Frailty: base Hitpoints 10/20/40% lower and no healing above it. The engine has no temporary
 *   level cap (and lowering the saved level would come straight back with the next Hitpoints xp),
 *   so the Hitpoints level the client shows stays the same; healing above the cap is taken off
 *   every tick.
 * - Myopia: the player's attack range is 2/4/6 tiles shorter (spells cast by hand are not).
 * - Relentless: monsters ignore 33/66% of a player's Defence and hit 1/3/6 higher; at III they
 *   always hit. The Defence is ignored by lowering the player's defence roll by that share.
 * - Mantimayhem II: an orb not prayed against envenoms; the venom is cured when a wave ends.
 */
class ColosseumEffects @Inject constructor(private val venom: PlayerVenom) {
    fun maxHitBonus(run: ColosseumRun): Int =
        RELENTLESS_MAX_HIT[run.modifiers.tier(ColosseumModifier.Relentless)]

    /** Relentless III: every blow lands. */
    fun alwaysHits(run: ColosseumRun): Boolean =
        run.modifiers.tier(ColosseumModifier.Relentless) >= RELENTLESS_ALWAYS_TIER

    /** Sets a freshly spawned monster up for the run's Relentless. */
    fun setUp(run: ColosseumRun, npc: Npc) {
        npc.targetDefenceRollPercent =
            100 - RELENTLESS_BYPASS[run.modifiers.tier(ColosseumModifier.Relentless)]
    }

    /** Myopia: how much shorter the players' attack range is. */
    fun rangeLoss(run: ColosseumRun): Int =
        MYOPIA_LOSS[run.modifiers.tier(ColosseumModifier.Myopia)]

    /**
     * [member] took [damage] in the run: the wave's damage total, Blasphemy's drain and a stack of
     * Doom. [fromEnemy] marks the wave as not damage-free for them.
     */
    fun damageTaken(run: ColosseumRun, member: ColosseumMember, damage: Int, fromEnemy: Boolean) {
        if (damage <= 0) {
            return
        }
        val player = member.player
        if (fromEnemy) {
            member.hitByEnemy = true
        }
        member.damageTaken += damage
        VarPlayerIntMapSetter.set(player, colosseum_varps.wave_damage_taken, member.damageTaken)
        val drain = damage * BLASPHEMY_DRAIN[run.modifiers.tier(ColosseumModifier.Blasphemy)] / 100
        if (drain > 0) {
            player.statSub(stats.prayer, drain, 0)
        }
        val doom = run.modifiers.tier(ColosseumModifier.Doom)
        if (doom == 0) {
            return
        }
        member.doomStacks++
        val mark = hitmarks.doom.id
        player.showHitmark(Hitmark.fromNoSource(mark, mark, mark, member.doomStacks, 0))
        if (member.doomStacks == DOOM_LIMIT[doom]) {
            // Doom kills outright, whatever is left.
            player.queueHit(1, HitType.Typeless, player.hitpoints)
        }
    }

    /** Frailty: healing above the lowered level is lost. */
    fun frailty(run: ColosseumRun, member: ColosseumMember) {
        val tier = run.modifiers.tier(ColosseumModifier.Frailty)
        if (tier == 0) {
            return
        }
        val player = member.player
        val base = player.baseHitpointsLvl
        val cap = base - base * FRAILTY_LOSS[tier] / 100
        if (player.hitpoints > cap) {
            player.statSub(stats.hitpoints, player.hitpoints - cap, 0)
        }
    }

    /** Mantimayhem II: an orb [player] did not pray against envenoms them. */
    fun orbUnprayed(run: ColosseumRun, player: Player) {
        if (run.modifiers.tier(ColosseumModifier.Mantimayhem) >= MANTIMAYHEM_VENOM_TIER) {
            venom.envenom(player)
        }
    }

    /** A wave was completed: Doom's stacks and Mantimayhem's venom go. */
    fun waveCompleted(run: ColosseumRun, member: ColosseumMember) {
        member.doomStacks = 0
        if (run.modifiers.has(ColosseumModifier.Mantimayhem)) {
            venom.cure(member.player)
        }
    }

    companion object {
        val BLASPHEMY_DRAIN = listOf(0, 20, 40, 60)
        val DOOM_LIMIT = listOf(Int.MAX_VALUE, 15, 10, 5)
        val FRAILTY_LOSS = listOf(0, 10, 20, 40)
        val MYOPIA_LOSS = listOf(0, 2, 4, 6)
        val RELENTLESS_BYPASS = listOf(0, 33, 66, 66)
        val RELENTLESS_MAX_HIT = listOf(0, 1, 3, 6)
        const val RELENTLESS_ALWAYS_TIER = 3
        const val MANTIMAYHEM_VENOM_TIER = 2
    }
}
