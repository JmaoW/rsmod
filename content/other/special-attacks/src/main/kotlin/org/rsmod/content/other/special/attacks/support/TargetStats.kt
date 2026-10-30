package org.rsmod.content.other.special.attacks.support

import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/** The levels a special attack can drain. An npc has no Prayer level. */
enum class CombatSkill {
    Attack,
    Strength,
    Defence,
    Ranged,
    Magic,
    Prayer,
}

/**
 * Reading and lowering the combat levels of whatever a special attack hits. A lowered npc level
 * comes back by itself (see `NpcRegenProcessor`), a lowered player level the usual way.
 */
object TargetStats {
    private fun CombatSkill.stat(): StatType =
        when (this) {
            CombatSkill.Attack -> stats.attack
            CombatSkill.Strength -> stats.strength
            CombatSkill.Defence -> stats.defence
            CombatSkill.Ranged -> stats.ranged
            CombatSkill.Magic -> stats.magic
            CombatSkill.Prayer -> stats.prayer
        }

    /** The current level of [skill], or `0` for an npc's Prayer. */
    fun level(target: PathingEntity, skill: CombatSkill): Int =
        when (target) {
            is Npc -> npcLevel(target, skill)
            is Player -> target.stat(skill.stat())
        }

    /**
     * Lowers the level of [skill] by [amount], but never below [minimum].
     *
     * @return how much it was lowered.
     */
    fun lower(target: PathingEntity, skill: CombatSkill, amount: Int, minimum: Int = 1): Int {
        if (amount <= 0) {
            return 0
        }
        val current = level(target, skill)
        val drained = minOf(amount, current - minimum)
        if (drained <= 0) {
            return 0
        }
        when (target) {
            is Npc -> setNpcLevel(target, skill, current - drained)
            is Player -> target.statSub(skill.stat(), constant = drained, percent = 0)
        }
        return drained
    }

    /** Lowers [skill] by [percent] percent of its current level. */
    fun lowerPercent(
        target: PathingEntity,
        skill: CombatSkill,
        percent: Int,
        minimum: Int = 1,
    ): Int = lower(target, skill, level(target, skill) * percent / 100, minimum)

    /** The level [skill] has when it is not lowered, or `0` for an npc's Prayer. */
    fun baseLevel(target: PathingEntity, skill: CombatSkill): Int =
        when (target) {
            is Npc -> npcBaseLevel(target, skill)
            is Player -> target.statBase(skill.stat())
        }

    /** Lowers [skill] by [percent] percent of its base level, plus [flat]. */
    fun lowerBasePercent(
        target: PathingEntity,
        skill: CombatSkill,
        percent: Int,
        flat: Int = 0,
        minimum: Int = 1,
    ): Int = lower(target, skill, baseLevel(target, skill) * percent / 100 + flat, minimum)

    private fun npcBaseLevel(npc: Npc, skill: CombatSkill): Int =
        when (skill) {
            CombatSkill.Attack -> npc.baseAttackLvl
            CombatSkill.Strength -> npc.baseStrengthLvl
            CombatSkill.Defence -> npc.baseDefenceLvl
            CombatSkill.Ranged -> npc.baseRangedLvl
            CombatSkill.Magic -> npc.baseMagicLvl
            CombatSkill.Prayer -> 0
        }

    private fun npcLevel(npc: Npc, skill: CombatSkill): Int =
        when (skill) {
            CombatSkill.Attack -> npc.attackLvl
            CombatSkill.Strength -> npc.strengthLvl
            CombatSkill.Defence -> npc.defenceLvl
            CombatSkill.Ranged -> npc.rangedLvl
            CombatSkill.Magic -> npc.magicLvl
            CombatSkill.Prayer -> 0
        }

    private fun setNpcLevel(npc: Npc, skill: CombatSkill, level: Int) {
        when (skill) {
            CombatSkill.Attack -> npc.attackLvl = level
            CombatSkill.Strength -> npc.strengthLvl = level
            CombatSkill.Defence -> npc.defenceLvl = level
            CombatSkill.Ranged -> npc.rangedLvl = level
            CombatSkill.Magic -> npc.magicLvl = level
            CombatSkill.Prayer -> Unit
        }
    }
}
