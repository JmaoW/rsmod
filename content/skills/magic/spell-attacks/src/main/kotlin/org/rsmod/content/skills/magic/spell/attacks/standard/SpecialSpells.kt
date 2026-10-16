package org.rsmod.content.skills.magic.spell.attacks.standard

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

// TODO:
// - Tome of water raising Vulnerability to 15%, and swampbark lengthening the binds
// - Iban's staff charges and Magic Dart's slayer staff (e) charges (the isle has no charging)
// - the Mage Arena 100 casts rule for the god spells (no quest locks on the isle)
//
// Frozen npcs are held with the same delay a stun gives them, so they cannot attack either. The
// real game lets a frozen npc hit back from where it stands; the isle has no npc pathfinding to
// make that distinction worth it yet.

/** A stat debuff, in percent of the target's level. Zero leaves the stat alone. */
class Debuff(val attack: Int = 0, val strength: Int = 0, val defence: Int = 0)

/**
 * The standard spellbook's combat spells that are not Strike, Bolt, Blast, Wave or Surge: the stat
 * debuffs, the binds, Crumble Undead, Iban Blast, Magic Dart and the three god spells.
 */
class SpecialSpells
@Inject
constructor(private val objTypes: ObjTypeList, private val freezes: NpcFreezes) : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        registerDebuffs(manager)
        registerBinds(manager)
        registerDamage(manager)
    }

    private fun SpellAttackRepository.registerDebuffs(manager: SpellAttackManager) {
        register(
            objs.spell_confuse,
            attack(
                manager,
                special_seqs.cast_confuse,
                special_seqs.cast_confuse_staff,
                special_spotanims.confuse_casting,
                special_spotanims.confuse_travel,
                special_spotanims.confuse_impact,
                projanims.confuse,
                synths.confuse_cast_and_fire,
                synths.confuse_hit,
                debuff = Debuff(attack = 5),
            ),
        )
        register(
            objs.spell_weaken,
            attack(
                manager,
                special_seqs.cast_weaken,
                special_seqs.cast_weaken_staff,
                special_spotanims.weaken_casting,
                special_spotanims.weaken_travel,
                special_spotanims.weaken_impact,
                projanims.magic_spell,
                synths.weaken_all,
                synths.weaken_all,
                debuff = Debuff(strength = 5),
            ),
        )
        register(
            objs.spell_curse,
            attack(
                manager,
                special_seqs.cast_curse,
                special_seqs.cast_curse_staff,
                special_spotanims.curse_casting,
                special_spotanims.curse_travel,
                special_spotanims.curse_impact,
                projanims.magic_spell,
                synths.curse_cast_and_fire,
                synths.curse_hit,
                debuff = Debuff(defence = 5),
            ),
        )
        register(
            objs.spell_vulnerability,
            attack(
                manager,
                special_seqs.cast_stun,
                special_seqs.cast_stun_staff,
                special_spotanims.vulnerability_casting,
                special_spotanims.vulnerability_travel,
                special_spotanims.vulnerability_impact,
                projanims.vulnerability,
                synths.vulnerability_all,
                synths.vulnerability_all,
                debuff = Debuff(defence = 10),
            ),
        )
        register(
            objs.spell_enfeeble,
            attack(
                manager,
                special_seqs.cast_enfeeble,
                special_seqs.cast_enfeeble_staff,
                special_spotanims.enfeeble_casting,
                special_spotanims.enfeeble_travel,
                special_spotanims.enfeeble_impact,
                projanims.enfeeble,
                synths.enfeeble_cast_and_fire,
                synths.enfeeble_hit,
                debuff = Debuff(strength = 10),
            ),
        )
        register(
            objs.spell_stun,
            attack(
                manager,
                special_seqs.cast_stun,
                special_seqs.cast_stun_staff,
                special_spotanims.stun_casting,
                special_spotanims.stun_travel,
                special_spotanims.stun_impact,
                projanims.stun,
                synths.stun_all,
                synths.stun_all,
                debuff = Debuff(attack = 10),
            ),
        )
    }

    private fun SpellAttackRepository.registerBinds(manager: SpellAttackManager) {
        fun bind(impact: SpotanimType, sound: SynthType, hit: SynthType, maxHit: Int, ticks: Int) =
            attack(
                manager,
                special_seqs.cast_entangle,
                special_seqs.cast_entangle_staff,
                special_spotanims.entangle_casting,
                special_spotanims.entangle_travel,
                impact,
                projanims.bind,
                sound,
                hit,
                getMaxHit = { maxHit },
                freeze = ticks,
            )

        register(
            objs.spell_bind,
            bind(special_spotanims.bind_impact, synths.bind_cast, synths.bind_impact, 0, 8),
        )
        register(
            objs.spell_snare,
            bind(special_spotanims.snare_impact, synths.snare_all, synths.snare_all, 2, 16),
        )
        register(
            objs.spell_entangle,
            bind(
                special_spotanims.entangle_impact,
                synths.entangle_cast_and_fire,
                synths.entangle_hit,
                5,
                24,
            ),
        )
    }

    private fun SpellAttackRepository.registerDamage(manager: SpellAttackManager) {
        register(
            objs.spell_crumble_undead,
            attack(
                manager,
                special_seqs.cast_crumble,
                special_seqs.cast_crumble_staff,
                special_spotanims.crumble_casting,
                special_spotanims.crumble_travel,
                special_spotanims.crumble_impact,
                projanims.crumble_undead,
                synths.crumble_cast_and_fire,
                synths.crumble_hit,
                getMaxHit = { 15 },
                undeadOnly = true,
            ),
        )
        register(
            objs.spell_iban_blast,
            attack(
                manager,
                special_seqs.cast_iban,
                special_seqs.cast_iban,
                special_spotanims.iban_casting,
                special_spotanims.iban_travel,
                special_spotanims.iban_impact,
                projanims.iban_blast,
                null,
                null,
                getMaxHit = { 25 },
            ),
        )
        register(
            objs.spell_magic_dart,
            attack(
                manager,
                special_seqs.cast_magic_dart,
                special_seqs.cast_magic_dart,
                null,
                special_spotanims.magic_dart_travel,
                special_spotanims.magic_dart_impact,
                projanims.magic_spell,
                null,
                synths.magic_dart_hit,
                // The real maximum comes from the Magic Dart rule in the damage formulas.
                getMaxHit = { 10 },
            ),
        )
        register(objs.spell_saradomin_strike, god(manager, special_spotanims.saradomin_strike))
        register(objs.spell_claws_of_guthix, god(manager, special_spotanims.claws_of_guthix))
        register(objs.spell_flames_of_zamorak, god(manager, special_spotanims.flames_of_zamorak))
    }

    /** A god spell: up to 20 damage (30 while charged), shown as the god's own effect. */
    private fun god(manager: SpellAttackManager, effect: SpotanimType): SpellAttack =
        attack(
            manager,
            special_seqs.cast_god,
            special_seqs.cast_god,
            null,
            effect,
            effect,
            projanims.magic_spell,
            null,
            null,
            getMaxHit = { 20 },
        )

    private fun attack(
        manager: SpellAttackManager,
        staffAnim: SeqType,
        unarmedAnim: SeqType,
        launch: SpotanimType?,
        travel: SpotanimType,
        impact: SpotanimType,
        projanim: ProjAnimType,
        castSound: SynthType?,
        hitSound: SynthType?,
        getMaxHit: (Player) -> Int = { 0 },
        debuff: Debuff? = null,
        freeze: Int = 0,
        undeadOnly: Boolean = false,
    ): SpellAttack =
        SpecialSpellAttack(
            manager = manager,
            objTypes = objTypes,
            freezes = freezes,
            staffAnim = staffAnim,
            unarmedAnim = unarmedAnim,
            launch = launch,
            travel = travel,
            impact = impact,
            projanim = projanim,
            castSound = castSound,
            hitSound = hitSound,
            getMaxHit = getMaxHit,
            debuff = debuff,
            freezeTicks = freeze,
            undeadOnly = undeadOnly,
        )

    private class SpecialSpellAttack(
        private val manager: SpellAttackManager,
        private val objTypes: ObjTypeList,
        private val freezes: NpcFreezes,
        private val staffAnim: SeqType,
        private val unarmedAnim: SeqType,
        private val launch: SpotanimType?,
        private val travel: SpotanimType,
        private val impact: SpotanimType,
        private val projanim: ProjAnimType,
        private val castSound: SynthType?,
        private val hitSound: SynthType?,
        private val getMaxHit: (Player) -> Int,
        private val debuff: Debuff?,
        private val freezeTicks: Int,
        private val undeadOnly: Boolean,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            // Refused before the runes are taken.
            val refusal = refusal(target)
            if (refusal != null) {
                mes(refusal)
                manager.stopCombat(this)
                return
            }

            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            val weaponType = objTypes.getOrNull(attack.weapon)
            anim(weaponType.castAnim())
            if (launch != null) {
                spotanim(launch, height = SPOTANIM_HEIGHT)
            }

            val proj = manager.spawnProjectile(this, target, travel, projanim)
            val (serverDelay, clientDelay) = proj.durations
            val spell = attack.spell.obj

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val baseMaxHit = getMaxHit(player)
            val damage = manager.rollMaxHit(this, target, attack, castResult, baseMaxHit)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = impact,
                hitSpotHeight = 124,
                hitSound = hitSound,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spell, damage, clientDelay, serverDelay)
            applyEffects(target)
            manager.continueCombatIfAutocast(this, target)
        }

        /** Why [target] cannot be cast on, or `null` if it can. */
        private fun ProtectedAccess.refusal(target: PathingEntity): String? {
            if (undeadOnly) {
                val undead = target is Npc && target.visType.param(params.undead) != 0
                if (!undead) {
                    return "This spell only affects skeletons, zombies, ghosts and shades."
                }
            }
            if (debuff != null && target is Npc && target.isLowered()) {
                return "The target's stats have already been lowered."
            }
            if (freezeTicks > 0 && target is Npc) {
                if (target.visType.param(params.freeze_resistance) >= FULL_RESISTANCE) {
                    return "That creature is immune to that spell."
                }
                if (freezes.isHeld(target, mapClock)) {
                    return "The target is currently immune to that spell."
                }
            }
            return null
        }

        private fun ProtectedAccess.applyEffects(target: PathingEntity) {
            if (debuff != null) {
                when (target) {
                    is Npc -> debuff.apply(target)
                    is Player -> debuff.apply(target)
                }
            }
            if (freezeTicks > 0 && target is Npc) {
                freezes.freeze(target, freezeTicks, mapClock)
            }
        }

        private fun UnpackedObjType?.castAnim(): SeqType =
            if (this != null && isCategoryType(categories.staff)) staffAnim else unarmedAnim

        private companion object {
            const val SPOTANIM_HEIGHT = 92
            const val FULL_RESISTANCE = 100
        }
    }

    companion object {
        /** Whether a debuff has already lowered any of [npc]'s combat stats. */
        fun Npc.isLowered(): Boolean =
            attackLvl < baseAttackLvl ||
                strengthLvl < baseStrengthLvl ||
                defenceLvl < baseDefenceLvl

        /** [level] lowered by [percent] percent of itself. */
        fun lowered(level: Int, percent: Int): Int = level - level * percent / 100

        fun Debuff.apply(npc: Npc) {
            npc.attackLvl = lowered(npc.attackLvl, attack)
            npc.strengthLvl = lowered(npc.strengthLvl, strength)
            npc.defenceLvl = lowered(npc.defenceLvl, defence)
        }

        fun Debuff.apply(player: Player) {
            if (attack > 0) player.statDrain(stats.attack, constant = 0, percent = attack)
            if (strength > 0) player.statDrain(stats.strength, constant = 0, percent = strength)
            if (defence > 0) player.statDrain(stats.defence, constant = 0, percent = defence)
        }
    }
}
