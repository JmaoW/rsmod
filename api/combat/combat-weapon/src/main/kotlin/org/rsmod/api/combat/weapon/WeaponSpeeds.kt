package org.rsmod.api.combat.weapon

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.styles.AttackStyle
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

public class WeaponSpeeds
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val styles: AttackStyles,
    private val masteries: CombatMasteryEffects,
) {
    public fun base(player: Player): Int {
        val weapon = player.righthand ?: return constants.combat_default_attackrate
        return objTypes[weapon].param(params.attackrate)
    }

    public fun actual(player: Player, style: AttackStyle?): Int {
        val baseSpeed = base(player)
        return if (style == AttackStyle.RapidRanged) {
            max(1, baseSpeed - 1)
        } else {
            baseSpeed
        }
    }

    // Avoids needing the [AttackStyle] dependency when calling `actual` unless explicitly required.
    public fun actual(player: Player): Int = actual(player, player.currentAttackStyle())

    /**
     * Returns the player-facing attack delay in ticks: the rapid-adjusted attack rate of the
     * weapon, after the attack rate masteries of the path the current attack style belongs to.
     */
    public fun playerAttackDelay(player: Player): Int =
        playerAttackDelay(player, masteryStyle(player.currentAttackStyle()))

    /** Same as [playerAttackDelay], for an attack known to be of [style]. */
    public fun playerAttackDelay(player: Player, style: MasteryStyle): Int =
        masteries.attackDelay(player, style, max(1, actual(player)))

    /** The delay after casting a standard spell, after the magic attack rate masteries. */
    public fun playerSpellDelay(player: Player): Int =
        playerMagicDelay(player, constants.combat_spell_attackrate)

    /** The delay after a powered staff attack, after the magic attack rate masteries. */
    public fun playerStaffDelay(player: Player): Int =
        playerMagicDelay(player, constants.combat_pstaff_attackrate)

    /** The delay of a magic attack whose own delay is [ticks], after the magic masteries. */
    public fun playerMagicDelay(player: Player, ticks: Int): Int =
        masteries.attackDelay(player, MasteryStyle.Magic, ticks)

    /** Returns the player-facing retaliate flinch delay: half the weapon's attack rate. */
    public fun playerRetaliateDelay(player: Player): Int {
        val attackRate = base(player)
        val rapidAdjustment = if (player.currentAttackStyle() == AttackStyle.RapidRanged) 1 else 0
        return max(1, (attackRate / 2) - rapidAdjustment)
    }

    private fun Player.currentAttackStyle(): AttackStyle? = styles.get(this)

    private fun masteryStyle(style: AttackStyle?): MasteryStyle =
        when {
            style == null -> MasteryStyle.Melee
            style.isRanged -> MasteryStyle.Ranged
            style.isMagic -> MasteryStyle.Magic
            else -> MasteryStyle.Melee
        }
}
