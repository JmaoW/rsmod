package org.rsmod.content.skills.magic.spell.attacks.arceuus

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.standard.NpcFreezes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType

// The Arceuus spellbook's attack spells:
// - Ghostly, Skeletal and Undead Grasp (12, 17 and 24 maximum hit) have a chance to bind the target
//   on a hit: 10% for 2 ticks, 25% for 3, 50% for 4. Mark of Darkness doubles the chance.
// - Inferior, Superior and Dark Demonbane (16, 23 and 30 maximum hit) can only be cast on demonic
//   creatures. Their bonus against demons, and Mark of Darkness making it bigger, is part of the
//   damage formulas.
//
// A bind freezes the npc, so it cannot move but can still attack. The Ward of Arceuus (which
// shortens the bind on the caster) is not built; see the magic plan.
class ArceuusSpells @Inject constructor(private val freezes: NpcFreezes) : SpellAttackMap {
    private class Def(
        val spell: ObjType,
        val maxHit: Int,
        val anim: SeqType,
        val launch: SpotanimType,
        val impact: SpotanimType,
        val demonOnly: Boolean = false,
        val bindChance: Int = 0,
        val bindTicks: Int = 0,
    )

    private val defs: List<Def> =
        listOf(
            Def(
                objs.spell_ghostly_grasp,
                12,
                arceuus_seqs.cast_grasp,
                arceuus_spotanims.ghostly_grasp_cast,
                arceuus_spotanims.ghostly_grasp_hit,
                bindChance = 10,
                bindTicks = 2,
            ),
            Def(
                objs.spell_skeletal_grasp,
                17,
                arceuus_seqs.cast_grasp,
                arceuus_spotanims.skeletal_grasp_cast,
                arceuus_spotanims.skeletal_grasp_hit,
                bindChance = 25,
                bindTicks = 3,
            ),
            Def(
                objs.spell_undead_grasp,
                24,
                arceuus_seqs.cast_grasp,
                arceuus_spotanims.undead_grasp_cast,
                arceuus_spotanims.undead_grasp_hit,
                bindChance = 50,
                bindTicks = 4,
            ),
            Def(
                objs.spell_inferior_demonbane,
                16,
                arceuus_seqs.cast_demonbane,
                arceuus_spotanims.inferior_demonbane_cast,
                arceuus_spotanims.inferior_demonbane_hit,
                demonOnly = true,
            ),
            Def(
                objs.spell_superior_demonbane,
                23,
                arceuus_seqs.cast_demonbane,
                arceuus_spotanims.superior_demonbane_cast,
                arceuus_spotanims.superior_demonbane_hit,
                demonOnly = true,
            ),
            Def(
                objs.spell_dark_demonbane,
                30,
                arceuus_seqs.cast_demonbane,
                arceuus_spotanims.dark_demonbane_cast,
                arceuus_spotanims.dark_demonbane_hit,
                demonOnly = true,
            ),
        )

    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        for (def in defs) {
            register(def.spell, ArceuusAttack(manager, def))
        }
    }

    private inner class ArceuusAttack(
        private val manager: SpellAttackManager,
        private val def: Def,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            // Refused before the runes are taken.
            if (def.demonOnly && !(target is Npc && target.visType.param(params.demon) != 0)) {
                mes("This spell can only be cast on demonic creatures.")
                manager.stopCombat(this)
                return
            }

            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            anim(def.anim)
            spotanim(def.launch, height = SPOTANIM_HEIGHT)
            // Neither kind of spell has a projectile, so it lands a couple of ticks after the cast.
            val (serverDelay, clientDelay) = ProjAnim.Duration(SERVER_DELAY, CLIENT_DELAY)

            if (manager.rollSplash(this, target, attack, castResult)) {
                manager.playSplashFx(this, target, clientDelay, null, soundRadius = 8)
                manager.queueSplashHit(this, target, def.spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val damage = manager.rollMaxHit(this, target, attack, castResult, def.maxHit)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = null,
                soundRadius = 8,
                hitSpot = def.impact,
                hitSpotHeight = IMPACT_HEIGHT,
                hitSound = null,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, def.spell, damage, clientDelay, serverDelay)
            if (def.bindTicks > 0 && target is Npc && bindRoll()) {
                freezes.hold(target, def.bindTicks)
            }
            manager.continueCombatIfAutocast(this, target)
        }

        private fun ProtectedAccess.bindRoll(): Boolean {
            val marked = player.vars[varbits.buff_mark_of_darkness_disabled] == 1
            val chance = if (marked) def.bindChance * 2 else def.bindChance
            return random.of(PERCENT) < chance
        }
    }

    private companion object {
        const val PERCENT = 100
        const val SPOTANIM_HEIGHT = 92
        const val IMPACT_HEIGHT = 124
        const val SERVER_DELAY = 2
        const val CLIENT_DELAY = 30
    }
}
