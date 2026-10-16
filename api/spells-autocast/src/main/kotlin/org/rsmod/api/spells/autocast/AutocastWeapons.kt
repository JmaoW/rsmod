package org.rsmod.api.spells.autocast

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.spells.autocast.configs.autocast_params
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.WeaponCategory
import org.rsmod.game.type.obj.isType
import org.rsmod.game.type.varbit.VarBitType

@Singleton
public class AutocastWeapons @Inject constructor(private val spells: AutocastSpells) {
    /**
     * Returns `true` if [weapon] is a valid staff that can be used to cast the spell associated
     * with [autocastId]. Otherwise, sends the appropriate missing-requirement message to the player
     * and returns `false`.
     */
    public fun canStaffAutocast(player: Player, weapon: UnpackedObjType, autocastId: Int): Boolean {
        val isValidStaff = canStaffAutocast(weapon, autocastId)
        if (!isValidStaff) {
            player.mes("You can't autocast that spell with this staff.")
            return false
        }
        return true
    }

    public fun canStaffAutocast(weapon: UnpackedObjType, autocastId: Int): Boolean {
        val spell = spells[autocastId]

        // This can occur if a player was auto-casting a spell that is now removed (or at least
        // its ability to be autocast). We return false so it can be cleared by the caller.
        if (spell == null) {
            return false
        }

        // We return early if the spell is a standard autocast spell. (Can be cast by any staff)
        if (!spells.isRestrictedSpell(spell)) {
            return true
        }

        // Certain spells can only be autocast by specific weapons, such as Iban blast only being
        // available with Iban's staff.
        val staffAdditionalSpells = weapon.additionalAutocastSpells()
        val isValidAdditionalSpell = staffAdditionalSpells.any { it.isType(spell) }
        return isValidAdditionalSpell
    }

    private fun UnpackedObjType.additionalAutocastSpells(): Set<ObjType> {
        val additional1 = paramOrNull(autocast_params.additional_spell_autocast1)
        val additional2 = paramOrNull(autocast_params.additional_spell_autocast2)
        val additional3 = paramOrNull(autocast_params.additional_spell_autocast3)
        return setOfNotNull(additional1, additional2, additional3)
    }

    /**
     * The value for the `autocast_layout` varp that makes the client's spell picker (cs2
     * `autocast_spellpos`) position the spells that [weapon] can autocast in [book]. The client
     * only knows a few layouts, each selected by an obj id: `-1` is the standard book, `4675` the
     * Ancient Magicks, `9013` Arceuus, and the others add the spells of special staves.
     */
    public fun pickerLayout(weapon: UnpackedObjType, book: Spellbook): Int =
        when (book) {
            Spellbook.Ancients -> LAYOUT_ANCIENTS
            Spellbook.Arceuus -> LAYOUT_ARCEUUS
            Spellbook.Standard -> {
                val extra = weapon.additionalAutocastSpells()
                when {
                    extra.isEmpty() -> LAYOUT_STANDARD
                    extra.any { it.isType(objs.spell_iban_blast) } -> LAYOUT_IBAN
                    extra.all { it.isType(objs.spell_flames_of_zamorak) } -> LAYOUT_FLAMES
                    // The layout of the staff of the dead has every special spell in it.
                    else -> LAYOUT_ALL_SPECIAL
                }
            }

            else -> LAYOUT_STANDARD
        }

    public fun set(player: Player, varbits: StaffVarBits, autocastId: Int, defensiveCast: Boolean) {
        val (autocastVarBit, defensiveCastVarBit) = varbits
        VarPlayerIntMapSetter.set(player, autocastVarBit, autocastId)
        VarPlayerIntMapSetter.set(player, defensiveCastVarBit, if (defensiveCast) 1 else 0)
    }

    public fun reset(player: Player, varbits: StaffVarBits) {
        set(player, varbits, autocastId = 0, defensiveCast = false)
    }

    public fun reset(player: Player, weapon: UnpackedObjType) {
        val category = WeaponCategory.getOrUnarmed(weapon.weaponCategory)
        val varbits = getVarBits(category) ?: return
        reset(player, varbits)
    }

    // Note: If more autocast weapon categories are added in the future, we should consider storing
    // them in an enum config instead.
    public fun getVarBits(category: WeaponCategory): StaffVarBits? =
        when (category) {
            WeaponCategory.Staff ->
                StaffVarBits(
                    varbits.saved_autocast_spell_staff,
                    varbits.saved_defensive_casting_staff,
                )

            WeaponCategory.BladedStaff ->
                StaffVarBits(
                    varbits.saved_autocast_spell_bladed_staff,
                    varbits.saved_defensive_casting_bladed_staff,
                )

            else -> null
        }

    private companion object {
        const val LAYOUT_STANDARD = -1
        const val LAYOUT_ANCIENTS = 4675
        const val LAYOUT_ARCEUUS = 9013
        const val LAYOUT_IBAN = 1409
        const val LAYOUT_FLAMES = 27785
        const val LAYOUT_ALL_SPECIAL = 11791
    }

    public data class StaffVarBits(
        public val autocastId: VarBitType,
        public val defensiveCast: VarBitType,
    )
}
