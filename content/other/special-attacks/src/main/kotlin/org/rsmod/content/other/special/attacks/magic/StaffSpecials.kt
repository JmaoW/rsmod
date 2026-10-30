package org.rsmod.content.other.special.attacks.magic

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.type.obj.ObjType
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.StaffSpecial
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.game.entity.PathingEntity

/**
 * The staves with a magic special attack: Immolate and Invocate (the Nightmare staves), Soul Rend
 * (Eye of Ayak) and Pulsate (the Dawnbringer, which the cache files as the Verzik special weapon).
 */
class StaffSpecials
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val spellAttacks: SpellAttackRegistry,
    private val runes: MagicRuneManager,
    private val worldQueues: WorldQueueList,
    private val energy: SpecialAttackEnergy,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val immolate = Immolate(manager)
        registerMagic(sobjs.nightmare_staff_volatile, immolate)
        registerMagic(sobjs.br_nightmare_staff_volatile, immolate)
        registerMagic(sobjs.deadman_blighted_volatile_staff, immolate)
        registerMagic(sobjs.deadman_nightmare_staff_volatile, immolate)

        registerMagic(sobjs.nightmare_staff_eldritch, Invocate(manager))

        registerMagic(sobjs.eye_of_ayak, SoulRend(manager))
        registerMagic(sobjs.verzik_special_weapon, Pulsate(manager))

        val scatter = ScatterAshes(manager, spells, spellAttacks, runes, worldQueues, energy)
        registerMagic(sobjs.purging_staff, scatter)
    }

    /**
     * The Nightmare staves' own spell. Its max hit grows with the Magic level, 44 at 99, before the
     * player's magic damage gear.
     */
    private fun ProtectedAccess.nightmareMaxHit(): Int = player.stat(stats.magic) / 3 + 11

    /** A powerful spell with 50% more accuracy that costs no runes. */
    private inner class Immolate(manager: SpecialAttackManager) : StaffSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.nightmare_staff_special,
                fx_spots.nightmare_staff_volatile_cast_spotanim,
                targetSpot = fx_spots.nightmare_staff_volatile_hit_spotanim,
            )

        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollStaff(target, attack, nightmareMaxHit(), accuracy = 1.5)
            hitStaff(target, attack, roll.damage)
            return resume(target)
        }
    }

    /**
     * The same spell, which gives the caster half the damage as Prayer points, up to 120 (past the
     * Prayer level).
     */
    private inner class Invocate(manager: SpecialAttackManager) : StaffSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.nightmare_staff_special,
                fx_spots.nightmare_staff_eldritch_cast_spotanim,
                targetSpot = fx_spots.nightmare_staff_eldritch_hit_spotanim,
            )

        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollStaff(target, attack, nightmareMaxHit())
            hitStaff(target, attack, roll.damage)
            val room = PRAYER_CAP - player.stat(stats.prayer)
            val restored = minOf(roll.damage / 2, room)
            if (restored > 0) {
                statAdd(stats.prayer, constant = restored, percent = 0)
            }
            return resume(target)
        }
    }

    /** Double accuracy and a 30% higher max hit, and it drains the target's magic defence. */
    private class SoulRend(manager: SpecialAttackManager) : StaffSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.human_eye_of_ayak_special,
                fx_spots.vfx_ayak_player_special_spotanim,
                targetSpot = fx_spots.vfx_ayak_impact_special_spotanim,
            )

        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollStaff(target, attack, EYE_BASE_MAX_HIT, accuracy = 2.0, damage = 1.3)
            hitStaff(target, attack, roll.damage)
            if (roll.landed) {
                TargetStats.lower(target, CombatSkill.Magic, roll.damage)
            }
            return resume(target)
        }

        private companion object {
            /** The Eye's own max hit before its Magic level and gear. */
            const val EYE_BASE_MAX_HIT = 30
        }
    }

    /** A burst of energy that cannot miss and deals between 75 and 150 damage. */
    private class Pulsate(manager: SpecialAttackManager) : StaffSpecial(manager) {
        private val fx =
            SpecialFx(
                spot = fx_spots.dawnbringer_casting_spec,
                targetSpot = fx_spots.dawnbringer_impact_spec,
            )

        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            fx.playAll(this, target)
            val damage = random.of(MIN_DAMAGE..MAX_DAMAGE)
            hitStaff(target, attack, damage)
            return resume(target)
        }

        private companion object {
            const val MIN_DAMAGE = 75
            const val MAX_DAMAGE = 150
        }
    }

    /**
     * Casts the best demonbane spell the player has the level and runes for. If the spell kills a
     * demon, the special attack energy it cost is given back and the next attack comes sooner.
     */
    private class ScatterAshes(
        manager: SpecialAttackManager,
        private val spells: MagicSpellRegistry,
        private val spellAttacks: SpellAttackRegistry,
        private val runes: MagicRuneManager,
        private val worldQueues: WorldQueueList,
        private val energy: SpecialAttackEnergy,
    ) : StaffSpecial(manager) {
        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            var obj: ObjType? = null
            var spell: MagicSpell? = null
            for (candidate in DEMONBANE) {
                val magic = spells.getObjSpell(candidate) ?: continue
                if (runes.canCastSpell(player, magic)) {
                    obj = candidate
                    spell = magic
                    break
                }
            }
            if (obj == null || spell == null) {
                return refuse("You do not have the level and runes to cast a demonbane spell.")
            }
            val spellAttack = spellAttacks[obj] ?: return refuse("Nothing happens.")
            val energy = manager.specialEnergy(this)
            val demon = target is Npc && target.visType.param(params.demon) != 0
            val cast = CombatAttack.Spell(attack.weapon, spell, false)
            when (target) {
                is Npc -> spellAttack.attack(this, target, cast)
                is Player -> spellAttack.attack(this, target, cast)
            }
            if (demon) {
                val player = player
                worldQueues.add(KILL_CHECK_DELAY) {
                    if (target.slotId == PathingEntity.INVALID_SLOT || (target as Npc).hitpoints <= 0) {
                        player.refund(energy)
                    }
                }
            }
            return resume(target)
        }

        /** Gives back the energy the special used and brings the next attack forward. */
        private fun Player.refund(spent: Int) {
            energy.giveSpecialEnergy(this, minOf(REFUND, spent))
            actionDelay = maxOf(currentMapClock, actionDelay - 2)
        }

        private companion object {
            /** The special costs 25%. */
            const val REFUND = 250
            const val KILL_CHECK_DELAY = 6

            /** Best first. */
            val DEMONBANE = listOf(objs.spell_dark_demonbane, objs.spell_superior_demonbane, objs.spell_inferior_demonbane)
        }
    }

    private companion object {
        /** Invocate can raise Prayer this high. */
        const val PRAYER_CAP = 120
    }
}
