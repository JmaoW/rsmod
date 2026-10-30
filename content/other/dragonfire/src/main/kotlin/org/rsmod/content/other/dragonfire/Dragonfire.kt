package org.rsmod.content.other.dragonfire

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.herblore.scripts.AntifireLevel
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.inv.isType

/**
 * Works out what a breath of dragonfire does to a player when it lands: how much of it their
 * shield, prayer and antifire potion stop, and the damage that is left. The table of numbers is
 * [Breath].
 */
@Singleton
class Dragonfire
@Inject
constructor(
    private val potions: PotionEffects,
    private val masteries: CombatMasteryEffects,
    private val random: GameRandom,
    private val shields: ShieldCharges,
) {
    /** What protects [player] from dragonfire right now. */
    fun protectionOf(player: Player): DragonfireProtection =
        DragonfireProtection(
            shield = player.wearsShield(),
            prayer = player.vars[varbits.protect_from_magic] == 1,
            potion = potions.antifire(player),
        )

    private fun Player.wearsShield(): Boolean {
        val worn = lefthand ?: return false
        return DragonObjs.shields.any { worn.isType(it) }
    }

    /**
     * Sets the damage of [hit], a breath of [breath] that has just landed on [target]. [burnt] is
     * the result of the accuracy roll made when the dragon breathed.
     *
     * The damage is rolled now, not when the dragon breathed, so a player who puts a shield on or
     * switches their prayer while the breath is in the air is protected.
     */
    fun resolve(target: Player, hit: HitBuilder, breath: Breath, burnt: Boolean) {
        val protection = protectionOf(target)
        val max = breath.maxHit(protection, burnt)
        setDamage(target, hit, random.of(0..max))
        target.mes(messageFor(protection, burnt, max))
        if (protection.shield) {
            // A shield that stores dragonfire takes a charge from the breath it absorbs.
            shields.charge(target)
        }
    }

    /**
     * Sets the damage of [hit], one of the hits of a drake's volcanic breath. [struck] is whether
     * the first hit landed on the player: one who had moved off the tile the breath was aimed at
     * takes no damage from any of them.
     */
    fun resolveDrake(target: Player, hit: HitBuilder, struck: Boolean) {
        val protection = protectionOf(target)
        val range = DrakeBreath.damage(protection)
        setDamage(target, hit, if (struck) random.of(range) else 0)
        if (struck && range.last == 0) {
            target.mes(messageFor(protection, burnt = false, max = 0))
        }
    }

    /**
     * The combat mastery that lowers damage taken applies to the final number, which replaces the
     * one the hit was queued with. It is applied before the cap to the player's hitpoints: capping
     * first would round a last hitpoint's hit down to 0 and the player could never die.
     */
    private fun setDamage(target: Player, hit: HitBuilder, rolled: Int) {
        hit.damage = minOf(masteries.reduceDamageTaken(target, rolled), target.hitpoints)
    }

    private fun messageFor(protection: DragonfireProtection, burnt: Boolean, max: Int): String =
        when {
            max == 0 && protection.potion != AntifireLevel.None -> POTION_MESSAGE
            protection.shield -> SHIELD_MESSAGE
            burnt -> BURNT_MESSAGE
            else -> RESISTED_MESSAGE
        }

    companion object {
        const val POTION_MESSAGE = "Your potion protects you from the heat of the dragon's breath!"
        const val SHIELD_MESSAGE = "Your shield absorbs most of the dragon fire!"
        const val BURNT_MESSAGE = "You are horribly burnt by the dragon fire!"
        const val RESISTED_MESSAGE = "You manage to resist some of the dragon fire!"
    }
}
