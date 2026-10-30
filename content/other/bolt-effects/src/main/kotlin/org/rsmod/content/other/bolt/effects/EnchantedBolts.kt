package org.rsmod.content.other.bolt.effects

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.manager.BoltAdjust
import org.rsmod.api.combat.manager.BoltBoost
import org.rsmod.api.combat.manager.BoltEffectHandler
import org.rsmod.api.combat.manager.BoltShot
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.back
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.attack.NpcFreezeImmunity
import org.rsmod.content.other.dragonfire.Dragonfire
import org.rsmod.content.other.dragonfire.DragonfireProtection
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.skills.herblore.scripts.AntifireLevel
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * The effects of enchanted bolts (OSRS Wiki, "Bolt effects"). A crossbow shot with an enchanted
 * bolt asks this for its effect when the damage is rolled (see `BoltEffectRegistry`): each effect
 * has a chance to work, and one that does changes the roll, and then does the rest of what it does
 * (poison, drains, healing, holding) when the hit is queued.
 * - Opal: Lucky Lightning, extra damage of a tenth of the Ranged level.
 * - Jade: Earth's Fury, holds the target still for 8 ticks.
 * - Pearl: Sea Curse, extra damage of a twentieth of the Ranged level (a fifteenth against fiery
 *   targets). A player with a water staff is unaffected.
 * - Topaz: Down to Earth, lowers a player's Magic by 1. Players only.
 * - Sapphire: Clear Mind, drains Prayer and gives some of it to the shooter.
 * - Emerald: Magical Poison, poisons for 5.
 * - Ruby: Blood Forfeit, 20% of the target's hitpoints (at most 100) for 10% of the shooter's.
 * - Diamond: Armour Piercing, never misses and 15% more damage.
 * - Dragonstone: Dragon's Breath, extra damage of a fifth of the Ranged level, none against fiery
 *   or dragonfire-protected targets.
 * - Onyx: Life Leech, 20% more damage and the shooter heals a quarter of it, none against undead.
 *
 * The Zaryte crossbow makes every effect 10% stronger. A special attack can double the chances
 * (Armadyl Eye) or make the effect work on a hit (Evoke). A shot gets no Kandarin diary bonus: the
 * league has no diaries.
 */
@Singleton
class EnchantedBolts
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val random: GameRandom,
    private val dots: SpecialDots,
    private val dragonfire: Dragonfire,
    private val freezeImmunity: NpcFreezeImmunity,
) : BoltEffectHandler {
    private val gems = HashMap<Int, BoltGem?>()

    override fun begin(source: Player, target: PathingEntity, boost: BoltBoost?): BoltShot? {
        val weapon = source.righthand ?: return null
        val weaponType = objTypes[weapon]
        if (!weaponType.isCategoryType(categories.crossbow)) {
            return null
        }
        val ammo = source.quiver ?: return null
        if (ammo.id !in gems) {
            gems[ammo.id] = BoltGem.of(objTypes[ammo].internalName)
        }
        val gem = gems[ammo.id] ?: return null
        val zaryte = weaponType.internalName?.contains(ZARYTE) == true
        return Shot(source, target, gem, zaryte, boost)
    }

    private inner class Shot(
        val source: Player,
        val target: PathingEntity,
        val gem: BoltGem,
        val zaryte: Boolean,
        val boost: BoltBoost?,
    ) : BoltShot {
        private var onLanded: ((Hit) -> Unit)? = null

        override fun rollBypass(): BoltAdjust? {
            if (!gem.bypassesAccuracy || boost == BoltBoost.Guaranteed) {
                return null
            }
            return if (works()) effect() else null
        }

        override fun rollOnHit(): BoltAdjust? {
            if (gem.bypassesAccuracy && boost != BoltBoost.Guaranteed) {
                // Its chance was rolled before the accuracy roll.
                return null
            }
            return if (boost == BoltBoost.Guaranteed || works()) effect() else null
        }

        override fun landed(target: PathingEntity, hit: Hit, clientDelay: Int, hitDelay: Int) {
            target.spotanim(BoltSpotanims.of(gem), delay = clientDelay, height = SPOT_HEIGHT)
            onLanded?.invoke(hit)
        }

        private fun works(): Boolean {
            val chance = if (target is Npc) gem.pvm else gem.pvp
            val boosted = if (boost == BoltBoost.Doubled) chance * 2 else chance
            return random.of(PER_MILLE) < boosted
        }

        private val ranged: Int
            get() = source.stat(stats.ranged)

        /** The [percent] of [value] this shot's gem is worth: 10% more with the Zaryte crossbow. */
        private fun scaled(value: Int, percent: Int): Int =
            value * (if (zaryte) percent * 11 / 10 else percent) / 100

        /** What the effect changes in the roll, `null` if the target is immune to it. */
        private fun effect(): BoltAdjust? =
            when (gem) {
                BoltGem.Opal -> BoltAdjust(alwaysHit = true, extraDamage = divided(ranged, 10, 9))
                BoltGem.Jade -> jade()
                BoltGem.Pearl -> pearl()
                BoltGem.Topaz -> topaz()
                BoltGem.Sapphire -> sapphire()
                BoltGem.Emerald -> emerald()
                BoltGem.Ruby -> ruby()
                BoltGem.Diamond ->
                    BoltAdjust(alwaysHit = true, maxHitMultiplier = if (zaryte) 1.26 else 1.15)
                BoltGem.Dragonstone -> dragonstone()
                BoltGem.Onyx -> onyx()
            }

        /** A level divided by [normal], or by [boosted] with the Zaryte crossbow. */
        private fun divided(level: Int, normal: Int, boosted: Int): Int =
            level / (if (zaryte) boosted else normal)

        private fun jade(): BoltAdjust? {
            val player = target as? Player
            if (player != null && player.vars[varbits.protect_from_magic] == 1) {
                return null
            }
            if (target is Npc && freezeImmunity.isImmune(target, target.currentMapClock)) {
                return null
            }
            onLanded = { _: Hit ->
                if (target is Npc) {
                    val clock = target.currentMapClock
                    target.freeze(HOLD_TICKS)
                    freezeImmunity.immuneUntil(target, clock + HOLD_TICKS + HOLD_IMMUNITY)
                } else {
                    target.freeze(HOLD_TICKS)
                }
            }
            return BoltAdjust()
        }

        private fun pearl(): BoltAdjust? {
            if (target is Player && wieldsWaterStaff(target)) {
                return null
            }
            val extra = if (isFiery(target)) divided(ranged, 15, 13) else divided(ranged, 20, 18)
            return BoltAdjust(alwaysHit = true, extraDamage = extra)
        }

        private fun topaz(): BoltAdjust? {
            val player = target as? Player ?: return null
            onLanded = { _: Hit -> player.statSub(stats.magic, 1, 0) }
            return BoltAdjust()
        }

        private fun sapphire(): BoltAdjust {
            onLanded = { _: Hit ->
                when (target) {
                    is Player -> {
                        val drain = minOf(divided(ranged, 20, 18), target.stat(stats.prayer))
                        target.statSub(stats.prayer, drain, 0)
                        source.statHeal(stats.prayer, drain / 2, 0)
                    }
                    is Npc -> {
                        val level = target.visType.ranged / 10
                        val drain = if (zaryte) level * 11 / 10 else level
                        source.statHeal(stats.prayer, drain / 8, 0)
                    }
                }
            }
            return BoltAdjust()
        }

        private fun emerald(): BoltAdjust {
            onLanded = { _: Hit -> dots.poison(target, source, if (zaryte) 6 else 5) }
            return BoltAdjust()
        }

        private fun ruby(): BoltAdjust? {
            val own = source.stat(stats.hitpoints)
            if (own < RUBY_MIN_HITPOINTS) {
                return null
            }
            val victim = hitpointsOf(target)
            val damage = minOf(scaled(victim, 20), if (zaryte) 110 else 100)
            onLanded = { _: Hit -> source.queueHit(1, HitType.Typeless, own / 10) }
            return BoltAdjust(alwaysHit = true, fixedDamage = damage)
        }

        private fun dragonstone(): BoltAdjust? {
            if (isFiery(target) || (target is Player && hasDragonfireProtection(target))) {
                return null
            }
            return BoltAdjust(extraDamage = scaled(ranged, 20))
        }

        private fun onyx(): BoltAdjust? {
            if (target is Npc && target.visType.param(params.undead) != 0) {
                return null
            }
            onLanded = { hit -> source.statHeal(stats.hitpoints, hit.damage / 4, 0) }
            return BoltAdjust(maxHitMultiplier = if (zaryte) 1.32 else 1.2)
        }
    }

    private fun hitpointsOf(target: PathingEntity): Int =
        when (target) {
            is Npc -> target.hitpoints
            is Player -> target.stat(stats.hitpoints)
        }

    private fun hasDragonfireProtection(target: Player): Boolean {
        val protection: DragonfireProtection = dragonfire.protectionOf(target)
        return protection.shield || protection.potion != AntifireLevel.None
    }

    /** Dragons, fire giants, pyrefiends and fire elementals; players wielding fire. */
    private fun isFiery(target: PathingEntity): Boolean =
        when (target) {
            is Npc -> BoltTargets.isFieryNpc(target.type.internalName)
            is Player ->
                BoltTargets.isFireGear(
                    weapon = target.righthand?.let { objTypes[it].internalName },
                    cape = target.back?.let { objTypes[it].internalName },
                )
        }

    private fun wieldsWaterStaff(player: Player): Boolean =
        BoltTargets.isWaterStaff(player.righthand?.let { objTypes[it].internalName })

    private companion object {
        const val ZARYTE = "zaryte_xbow"
        const val PER_MILLE = 1000
        const val SPOT_HEIGHT = 96
        const val HOLD_TICKS = 8
        const val HOLD_IMMUNITY = 5
        const val RUBY_MIN_HITPOINTS = 10
    }
}
