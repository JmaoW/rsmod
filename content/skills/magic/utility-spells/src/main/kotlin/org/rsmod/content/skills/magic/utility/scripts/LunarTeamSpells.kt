package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.events.interact.PlayerTEvents
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onApPlayerT
import org.rsmod.content.skills.herblore.configs.HerbloreObjs
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.Potions
import org.rsmod.content.skills.herblore.scripts.Drinking
import org.rsmod.content.skills.magic.utility.configs.lunar_seqs
import org.rsmod.content.skills.magic.utility.configs.lunar_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// The Lunar spells that help or look at other players. "Other players" are the caster's team (see
// [SpellTeams]) in place of the real game's Accept Aid setting.
// - Heal Other: gives a teammate up to 75% of the caster's hitpoints, as long as the caster keeps
//   11% of the hitpoints they have at full health
// - Heal Group: the caster gives up 75% of their hitpoints, and that plus 5 is shared between up to
//   five injured teammates within a tile
// - Energy Transfer: for 10 hitpoints and all of the caster's special attack, a teammate gets all
//   their run energy and special attack back
// - Stat Spy: lists a teammate's levels in chat, in place of the real game's interface
// - Stat Restore Pot Share and Boost Potion Share: one dose of a restoring (or boosting) potion
//   gives its effect to up to four teammates within two tiles, and to the caster for free
//
// Left out: cooldowns, which the real Heal Group has, and the Cure spells (see [DisabledSpells]).
class LunarTeamSpells
@Inject
constructor(
    private val caster: SpellCaster,
    private val teams: SpellTeams,
    private val drinking: Drinking,
    private val statTypes: StatTypeList,
) : PluginScript() {
    private var Player.specialEnergy by intVarp(varps.sa_energy)

    override fun ScriptContext.startup() {
        with(caster) {
            onApPlayerT(spell(objs.spell_heal_other).component) { healOther(it) }
            onSpell(objs.spell_heal_group) { healGroup() }
            onApPlayerT(spell(objs.spell_energy_transfer).component) { energyTransfer(it) }
            onApPlayerT(spell(objs.spell_stat_spy).component) { statSpy(it) }
            onItemSpell(objs.spell_res_pot_share) { sharePotion(objs.spell_res_pot_share, it) }
            onItemSpell(objs.spell_statboost_pot_share) {
                sharePotion(objs.spell_statboost_pot_share, it)
            }
        }
    }

    private fun ProtectedAccess.cast(spell: ObjType, cooldown: Int): Boolean =
        with(caster) { cast(spell, cooldown) } != null

    private fun ProtectedAccess.effects(seq: SeqType, spot: SpotanimType? = null) {
        anim(seq)
        if (spot != null) {
            spotanim(spot, height = SPOTANIM_HEIGHT)
        }
    }

    /** Whether [target] may be cast on; the caster is told if not. */
    private fun ProtectedAccess.canTarget(target: Player): Boolean {
        if (!teams.areTeammates(player, target)) {
            mes("You can only cast that on a member of your own team.")
            return false
        }
        return true
    }

    /**
     * Whether the caster keeps 11% of their hitpoints, which is what stops them killing themselves.
     */
    private fun Player.canSpare(): Boolean =
        hitpoints * 100 >= baseHitpointsLvl * MIN_HITPOINTS_PERCENT

    private fun ProtectedAccess.healOther(event: PlayerTEvents.Ap) {
        val target = event.target
        if (!canTarget(target)) {
            return
        }
        if (!player.canSpare()) {
            mes("You need more hitpoints to cast this spell.")
            return
        }
        val missing = target.baseHitpointsLvl - target.hitpoints
        if (missing <= 0) {
            mes("That player does not need healing.")
            return
        }
        if (!cast(objs.spell_heal_other, COOLDOWN)) {
            return
        }
        val amount = minOf(player.hitpoints * HEAL_PERCENT / PERCENT, missing).coerceAtLeast(1)
        statSub(stats.hitpoints, constant = amount, percent = 0)
        target.statHeal(stats.hitpoints, constant = amount, percent = 0)
        target.mes("${player.displayName} heals you.")
        effects(lunar_seqs.cast_group, lunar_spotanims.heal_group)
        publish(Healed(player, listOf(target), amount))
    }

    private fun ProtectedAccess.healGroup() {
        if (!player.canSpare()) {
            mes("You need more hitpoints to cast this spell.")
            return
        }
        val injured =
            teams
                .nearby(player, GROUP_RANGE)
                .filter { it.hitpoints < it.baseHitpointsLvl }
                .take(MAX_GROUP)
        if (injured.isEmpty()) {
            mes("There is nobody close enough who needs healing.")
            return
        }
        if (!cast(objs.spell_heal_group, COOLDOWN)) {
            return
        }
        val given = player.hitpoints * HEAL_PERCENT / PERCENT
        val share = (given + GROUP_BONUS) / injured.size
        statSub(stats.hitpoints, constant = given, percent = 0)
        for (target in injured) {
            target.statHeal(stats.hitpoints, constant = share, percent = 0)
            target.mes("${player.displayName} heals you.")
        }
        effects(lunar_seqs.cast_group, lunar_spotanims.heal_group)
        publish(Healed(player, injured, given))
    }

    private fun ProtectedAccess.energyTransfer(event: PlayerTEvents.Ap) {
        val target = event.target
        if (!canTarget(target)) {
            return
        }
        if (player.hitpoints <= ENERGY_HITPOINTS) {
            mes("You need more hitpoints to cast this spell.")
            return
        }
        if (player.specialEnergy < FULL_SPECIAL) {
            mes("You need full special attack energy to cast this spell.")
            return
        }
        if (!cast(objs.spell_energy_transfer, COOLDOWN)) {
            return
        }
        statSub(stats.hitpoints, constant = ENERGY_HITPOINTS, percent = 0)
        player.specialEnergy = 0
        target.specialEnergy = FULL_SPECIAL
        target.runEnergy = MAX_RUN_ENERGY
        UpdateRun.energy(target, MAX_RUN_ENERGY)
        target.mes("${player.displayName} restores your energy.")
        effects(lunar_seqs.cast_energy, lunar_spotanims.energy_transfer)
    }

    private fun ProtectedAccess.statSpy(event: PlayerTEvents.Ap) {
        val target = event.target
        if (!canTarget(target)) {
            return
        }
        if (!cast(objs.spell_stat_spy, COOLDOWN)) {
            return
        }
        mes("${target.displayName}'s levels (current/base):")
        val lines =
            statTypes.values.map { stat ->
                val current = target.stat(stat)
                "${stat.internalName?.replaceFirstChar(Char::uppercaseChar)} $current/${target.statBase(stat)}"
            }
        for (line in lines.chunked(STAT_LINE)) {
            mes(line.joinToString(", "))
        }
        effects(lunar_seqs.cast)
    }

    /**
     * Gives the effect of the potion in [target]'s slot to the caster and to nearby teammates. Each
     * teammate uses a dose; the caster's is free.
     */
    private fun ProtectedAccess.sharePotion(spell: ObjType, target: SpellTarget) {
        val (potion, doses) = Potions.find(target.obj) ?: return mes(NOT_A_POTION)
        val restoring = spell.id == objs.spell_res_pot_share.id
        val suits = if (restoring) potion.effect.restores() else potion.effect is PotionEffect.Boost
        if (!suits) {
            mes(if (restoring) NOT_RESTORING else NOT_BOOSTING)
            return
        }
        val nearby = teams.nearby(player, SHARE_RANGE).take(MAX_SHARED)
        if (nearby.isEmpty()) {
            mes("There is nobody close enough to share with.")
            return
        }
        val recipients = nearby.take(doses)
        if (!cast(spell, COOLDOWN)) {
            return
        }
        val left = doses - recipients.size
        val remaining = if (left > 0) potion.dose(left) else HerbloreObjs.vial_empty
        invReplaceSlot(inv, target.slot, 1, remaining)
        with(drinking) { applyShared(potion) }
        for (recipient in recipients) {
            drinking.share(recipient, potion)
            recipient.mes("${player.displayName} shares a potion with you.")
        }
        effects(lunar_seqs.cast_group)
        publish(PotionShared(player, potion, recipients))
    }

    private fun PotionEffect.restores(): Boolean =
        this is PotionEffect.Restore ||
            this is PotionEffect.PrayerPoints ||
            this is PotionEffect.RunEnergy ||
            this is PotionEffect.Stamina

    /** Published once [caster] has healed [targets] with [amount] hitpoints of their own. */
    class Healed(val caster: Player, val targets: List<Player>, val amount: Int) : UnboundEvent

    /** Published once [caster] has shared a dose of [potion] with [targets]. */
    class PotionShared(val caster: Player, val potion: Potion, val targets: List<Player>) :
        UnboundEvent

    private companion object {
        const val COOLDOWN = 4
        const val SPOTANIM_HEIGHT = 92
        const val PERCENT = 100

        const val MIN_HITPOINTS_PERCENT = 11
        const val HEAL_PERCENT = 75
        const val GROUP_RANGE = 1
        const val MAX_GROUP = 5
        const val GROUP_BONUS = 5

        const val ENERGY_HITPOINTS = 10
        const val FULL_SPECIAL = 1000
        const val MAX_RUN_ENERGY = 10_000

        const val STAT_LINE = 6

        const val SHARE_RANGE = 2
        const val MAX_SHARED = 4
        const val NOT_A_POTION = "This spell can only be cast on a potion."
        const val NOT_RESTORING = "That potion does not restore anything. Try Boost Potion Share."
        const val NOT_BOOSTING = "That potion does not boost anything. Try Stat Restore Pot Share."
    }
}
