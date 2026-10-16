package org.rsmod.content.skills.magic.spell.attacks.ancients

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.standard.NpcFreezes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType
import org.rsmod.map.CoordGrid

// TODO:
// - the Ancient sceptres (more freeze, more accuracy) and the Smoke staff's bonus beyond what the
//   damage formulas already give
// - freezing and poisoning other players: the isle has no player fighting
//
// The Ancient Magicks' sixteen combat spells. Each element has a Rush, Burst, Blitz and Barrage,
// and an effect that happens when the spell hits (not when it splashes):
// - Smoke: a 1 in 8 chance to poison, from 2 damage (Rush, Burst) or 4 (Blitz, Barrage)
// - Shadow: lowers the target's Attack by 10% (Rush, Burst) or 15% (Blitz, Barrage)
// - Blood: heals the caster for a quarter of the damage dealt
// - Ice: freezes the target for 8, 16, 24 or 32 ticks (Rush, Burst, Blitz, Barrage)
// Burst and Barrage also hit every attackable npc in the 3x3 around the target (nine at most),
// but only in a multi-combat area, rolling each hit separately.
class AncientSpells
@Inject
constructor(
    private val freezes: NpcFreezes,
    private val poison: NpcPoison,
    private val npcRepo: NpcRepository,
) : SpellAttackMap {
    /** What a spell does when it hits. */
    private enum class Effect {
        Poison,
        Drain,
        Heal,
        Freeze,
    }

    /**
     * @property strength the effect's number: poison starting damage, attack drain or heal percent,
     *   or freeze ticks.
     * @property travel the projectile, or `null` for a spell that has none.
     */
    private class Def(
        val spell: ObjType,
        val effect: Effect,
        val strength: Int,
        val maxHit: Int,
        val area: Boolean,
        val travel: SpotanimType?,
        val impact: SpotanimType,
        val castSound: SynthType,
        val hitSound: SynthType,
    ) {
        val castAnim: SeqType
            get() = if (area) ancient_seqs.cast_area else ancient_seqs.cast_single
    }

    private val defs: List<Def> =
        listOf(
            // Smoke
            Def(
                objs.spell_smoke_rush,
                Effect.Poison,
                2,
                13,
                false,
                ancient_spotanims.smoke_rush_travel,
                ancient_spotanims.smoke_rush_impact,
                ancient_synths.smoke_cast,
                ancient_synths.smoke_rush_impact,
            ),
            Def(
                objs.spell_smoke_burst,
                Effect.Poison,
                2,
                17,
                true,
                ancient_spotanims.smoke_burst_travel,
                ancient_spotanims.smoke_burst_impact,
                ancient_synths.smoke_cast,
                ancient_synths.smoke_burst_impact,
            ),
            Def(
                objs.spell_smoke_blitz,
                Effect.Poison,
                4,
                23,
                false,
                ancient_spotanims.smoke_blitz_travel,
                ancient_spotanims.smoke_blitz_impact,
                ancient_synths.smoke_cast,
                ancient_synths.smoke_blitz_impact,
            ),
            Def(
                objs.spell_smoke_barrage,
                Effect.Poison,
                4,
                27,
                true,
                ancient_spotanims.smoke_barrage_travel,
                ancient_spotanims.smoke_barrage_impact,
                ancient_synths.smoke_cast,
                ancient_synths.smoke_barrage_impact,
            ),
            // Shadow
            Def(
                objs.spell_shadow_rush,
                Effect.Drain,
                10,
                14,
                false,
                ancient_spotanims.shadow_rush_travel,
                ancient_spotanims.shadow_rush_impact,
                ancient_synths.shadow_cast,
                ancient_synths.shadow_rush_impact,
            ),
            Def(
                objs.spell_shadow_burst,
                Effect.Drain,
                10,
                18,
                true,
                null,
                ancient_spotanims.shadow_burst_impact,
                ancient_synths.shadow_cast,
                ancient_synths.shadow_burst_impact,
            ),
            Def(
                objs.spell_shadow_blitz,
                Effect.Drain,
                15,
                24,
                false,
                ancient_spotanims.shadow_blitz_travel,
                ancient_spotanims.shadow_blitz_impact,
                ancient_synths.shadow_cast,
                ancient_synths.shadow_blitz_impact,
            ),
            Def(
                objs.spell_shadow_barrage,
                Effect.Drain,
                15,
                28,
                true,
                null,
                ancient_spotanims.shadow_barrage_impact,
                ancient_synths.shadow_cast,
                ancient_synths.shadow_barrage_impact,
            ),
            // Blood
            Def(
                objs.spell_blood_rush,
                Effect.Heal,
                25,
                15,
                false,
                ancient_spotanims.blood_rush_travel,
                ancient_spotanims.blood_rush_impact,
                ancient_synths.blood_cast,
                ancient_synths.blood_rush_impact,
            ),
            Def(
                objs.spell_blood_burst,
                Effect.Heal,
                25,
                21,
                true,
                null,
                ancient_spotanims.blood_burst_impact,
                ancient_synths.blood_cast,
                ancient_synths.blood_burst_impact,
            ),
            Def(
                objs.spell_blood_blitz,
                Effect.Heal,
                25,
                25,
                false,
                ancient_spotanims.blood_blitz_travel,
                ancient_spotanims.blood_blitz_impact,
                ancient_synths.blood_cast,
                ancient_synths.blood_blitz_impact,
            ),
            Def(
                objs.spell_blood_barrage,
                Effect.Heal,
                25,
                29,
                true,
                null,
                ancient_spotanims.blood_barrage_impact,
                ancient_synths.blood_cast,
                ancient_synths.blood_barrage_impact,
            ),
            // Ice
            Def(
                objs.spell_ice_rush,
                Effect.Freeze,
                8,
                16,
                false,
                ancient_spotanims.ice_rush_travel,
                ancient_spotanims.ice_rush_impact,
                ancient_synths.ice_cast,
                ancient_synths.ice_rush_impact,
            ),
            Def(
                objs.spell_ice_burst,
                Effect.Freeze,
                16,
                22,
                true,
                ancient_spotanims.ice_burst_travel,
                ancient_spotanims.ice_burst_impact,
                ancient_synths.ice_cast,
                ancient_synths.ice_burst_impact,
            ),
            Def(
                objs.spell_ice_blitz,
                Effect.Freeze,
                24,
                26,
                false,
                ancient_spotanims.ice_blitz_travel,
                ancient_spotanims.ice_blitz_impact,
                ancient_synths.ice_cast,
                ancient_synths.ice_blitz_impact,
            ),
            Def(
                objs.spell_ice_barrage,
                Effect.Freeze,
                32,
                30,
                true,
                ancient_spotanims.ice_barrage_travel,
                ancient_spotanims.ice_barrage_impact,
                ancient_synths.ice_cast,
                ancient_synths.ice_barrage_impact,
            ),
        )

    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        for (def in defs) {
            register(def.spell, AncientAttack(manager, def))
        }
    }

    private inner class AncientAttack(
        private val manager: SpellAttackManager,
        private val def: Def,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(primary: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            anim(def.castAnim)

            // A spell with no projectile lands a couple of ticks after the cast.
            val (serverDelay, clientDelay) =
                if (def.travel != null) {
                    manager
                        .spawnProjectile(this, primary, def.travel, projanims.magic_spell)
                        .durations
                } else {
                    ProjAnim.Duration(NO_PROJECTILE_SERVER_DELAY, NO_PROJECTILE_CLIENT_DELAY)
                }

            val targets = listOf(primary) + secondaries(primary)
            for ((index, target) in targets.withIndex()) {
                val first = index == 0
                val sound = if (first) def.castSound else null

                val splash = manager.rollSplash(this, target, attack, castResult)
                if (splash) {
                    manager.playSplashFx(this, target, clientDelay, sound, soundRadius = 8)
                    manager.queueSplashHit(this, target, def.spell, clientDelay, serverDelay)
                    continue
                }

                val damage = manager.rollMaxHit(this, target, attack, castResult, def.maxHit)
                manager.playHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = sound,
                    soundRadius = 8,
                    hitSpot = def.impact,
                    hitSpotHeight = IMPACT_HEIGHT,
                    hitSound = def.hitSound,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, def.spell, damage, clientDelay, serverDelay)
                applyEffect(target, damage)
            }
            manager.continueCombatIfAutocast(this, primary)
        }

        /** The other npcs a Burst or Barrage hits: those around [primary], in multi-combat. */
        private fun ProtectedAccess.secondaries(primary: PathingEntity): List<PathingEntity> {
            if (!def.area || primary !is Npc || !mapMultiway()) {
                return emptyList()
            }
            return targetsAround(primary)
        }

        private fun ProtectedAccess.applyEffect(target: PathingEntity, damage: Int) {
            when (def.effect) {
                Effect.Poison -> {
                    if (target is Npc && random.randomBoolean(POISON_CHANCE)) {
                        poison.poison(target, player, def.strength)
                    }
                }
                Effect.Drain -> drain(target)
                Effect.Heal -> {
                    val dealt = minOf(damage, hitpointsOf(target))
                    val heal = dealt * def.strength / 100
                    if (heal > 0) {
                        statHeal(stats.hitpoints, constant = heal, percent = 0)
                    }
                }
                Effect.Freeze -> {
                    if (target is Npc && canFreeze(target)) {
                        freezes.freeze(target, def.strength, mapClock)
                    }
                }
            }
        }

        private fun ProtectedAccess.canFreeze(npc: Npc): Boolean =
            npc.visType.param(params.freeze_resistance) < FULL_RESISTANCE &&
                !freezes.isHeld(npc, mapClock)

        private fun hitpointsOf(target: PathingEntity): Int =
            when (target) {
                is Npc -> target.hitpoints
                is Player -> target.hitpoints
            }

        /** Lowers Attack by the spell's percent; it never lowers it further than that. */
        private fun drain(target: PathingEntity) {
            when (target) {
                is Npc -> {
                    val lowered = target.baseAttackLvl - target.baseAttackLvl * def.strength / 100
                    if (target.attackLvl > lowered) {
                        target.attackLvl = lowered
                    }
                }
                is Player -> target.statDrain(stats.attack, constant = 0, percent = def.strength)
            }
        }
    }

    /**
     * The other npcs a Burst or Barrage hits along with [primary]: the attackable npcs in the 3x3
     * around it, so that no more than nine are hit in all.
     */
    fun targetsAround(primary: Npc): List<Npc> {
        val others = LinkedHashSet<Npc>()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val coords =
                    CoordGrid(primary.coords.x + dx, primary.coords.z + dz, primary.coords.level)
                for (npc in npcRepo.findAll(coords)) {
                    if (npc !== primary && npc.isAttackable()) {
                        others += npc
                    }
                }
            }
        }
        return others.take(MAX_TARGETS - 1)
    }

    private fun Npc.isAttackable(): Boolean = hitpoints > 0 && visType.hasOp(InteractionOp.Op2)

    companion object {
        /** The most targets one Burst or Barrage hits. */
        const val MAX_TARGETS = 9

        /** Smoke poisons one hit in this many. */
        const val POISON_CHANCE = 8

        private const val FULL_RESISTANCE = 100
        private const val IMPACT_HEIGHT = 124
        private const val NO_PROJECTILE_SERVER_DELAY = 2
        private const val NO_PROJECTILE_CLIENT_DELAY = 30
    }
}
