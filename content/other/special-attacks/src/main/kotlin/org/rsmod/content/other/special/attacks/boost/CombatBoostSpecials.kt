package org.rsmod.content.other.special.attacks.boost

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.DamageGuards
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType

/**
 * The special attacks that are used on the spot and boost the player: Sanctuary (Excalibur),
 * Rampage (dragon battleaxe) and Power of Death (the staves of the dead, light and balance).
 */
class CombatBoostSpecials @Inject constructor(private val guards: DamageGuards) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerInstant(sobjs.excalibur) { sanctuary() }

        registerInstant(sobjs.dragon_battleaxe) { rampage() }
        registerInstant(sobjs.bh_dragon_battleaxe_corrupted) { rampage() }

        powerOfDeath(sobjs.sotd, fx_seqs.sotd_special, fx_spots.sotd_special_start)
        powerOfDeath(sobjs.br_sotd, fx_seqs.sotd_special, fx_spots.sotd_special_start)
        powerOfDeath(
            sobjs.toxic_sotd,
            fx_seqs.sotd_special_toxic_uncharged,
            fx_spots.sotd_special_start,
        )
        powerOfDeath(
            sobjs.toxic_sotd_charged,
            fx_seqs.sotd_special_toxic_charged,
            fx_spots.sotd_special_start,
        )
        powerOfDeath(
            sobjs.staff_of_light,
            fx_seqs.staff_of_light_special,
            fx_spots.staff_of_light_special_start,
        )
        powerOfDeath(
            sobjs.staff_of_balance,
            fx_seqs.staff_of_balance_special,
            fx_spots.staff_of_balance_special_start,
        )
    }

    private fun SpecialAttackRepository.powerOfDeath(
        staff: org.rsmod.game.type.obj.ObjType,
        seq: SeqType,
        spot: SpotanimType,
    ) {
        registerInstant(staff) {
            say("Spirits of deathly power!")
            anim(seq)
            spotanim(spot, height = 96, slot = constants.spotanim_slot_combat)
            guards.powerOfDeath(player, POWER_OF_DEATH_TICKS)
            true
        }
    }

    /** Raises Defence by 8 levels, past the level it is at. */
    private fun ProtectedAccess.sanctuary(): Boolean {
        say("For Camelot!")
        anim(fx_seqs.sanctuary)
        spotanim(fx_spots.sp_attackglow_blue, height = 96, slot = constants.spotanim_slot_combat)
        statAdd(stats.defence, constant = SANCTUARY_BOOST, percent = 0)
        return true
    }

    /**
     * Lowers Attack, Defence, Ranged and Magic by 10% of each, and raises Strength by 10 levels
     * and a quarter of the levels it took.
     */
    private fun ProtectedAccess.rampage(): Boolean {
        say("Raarrrrrgggggghhhhhhh!")
        anim(fx_seqs.rampage)
        spotanim(fx_spots.sp_attackglow_red, height = 96, slot = constants.spotanim_slot_combat)
        soundSynth(synths.rampage)
        var drained = 0
        for (stat in RAMPAGE_DRAINED) {
            val amount = player.stat(stat) / 10
            if (amount > 0) {
                statSub(stat, constant = amount, percent = 0)
                drained += amount
            }
        }
        statAdd(stats.strength, constant = RAMPAGE_BASE_BOOST + drained / 4, percent = 0)
        return true
    }

    private companion object {
        /** A minute. */
        const val POWER_OF_DEATH_TICKS = 100
        const val SANCTUARY_BOOST = 8
        const val RAMPAGE_BASE_BOOST = 10

        val RAMPAGE_DRAINED = listOf(stats.attack, stats.defence, stats.ranged, stats.magic)
    }
}
