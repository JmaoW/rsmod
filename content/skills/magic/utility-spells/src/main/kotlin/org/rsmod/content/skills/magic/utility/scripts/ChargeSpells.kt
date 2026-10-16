package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpLocT
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.skills.magic.utility.configs.utility_locs
import org.rsmod.content.skills.magic.utility.configs.utility_objs
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_timers
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// The four Charge Orb spells turn an unpowered orb into an elemental orb when cast on the matching
// obelisk. The obelisks are the Wilderness'; the isle has none yet, so these spells wait for a
// placed obelisk (the spell itself works on any loc of that type).
class ChargeOrbs @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    private class Orb(val spell: ObjType, val obelisk: LocType, val orb: ObjType)

    private val orbs =
        listOf(
            Orb(objs.spell_chargeorb_water, utility_locs.obelisk_water, utility_objs.water_orb),
            Orb(objs.spell_chargeorb_earth, utility_locs.obelisk_earth, utility_objs.earth_orb),
            Orb(objs.spell_chargeorb_fire, utility_locs.obelisk_fire, utility_objs.fire_orb),
            Orb(objs.spell_chargeorb_air, utility_locs.obelisk_air, utility_objs.air_orb),
        )

    override fun ScriptContext.startup() {
        for (orb in orbs) {
            val component = caster.spell(orb.spell).component
            onOpLocT(orb.obelisk, component) { charge(orb) }
        }
    }

    private fun ProtectedAccess.charge(orb: Orb) {
        if (invTotal(inv, utility_objs.unpowered_orb) == 0) {
            mes("You need an unpowered orb to cast this spell.")
            return
        }
        with(caster) { cast(orb.spell, COOLDOWN) } ?: return
        invDel(inv, utility_objs.unpowered_orb, 1)
        invAdd(inv, orb.orb, 1)
        anim(utility_seqs.cast_charge_orb)
        soundSynth(
            when (orb.spell.id) {
                objs.spell_chargeorb_water.id -> synths.charge_water_orb
                objs.spell_chargeorb_earth.id -> synths.charge_earth_orb
                objs.spell_chargeorb_fire.id -> synths.charge_fire_orb
                else -> synths.charge_air_orb
            }
        )
        publish(OrbCharged(player, orb.orb))
    }

    /** Published once an unpowered orb has become [orb]. */
    class OrbCharged(val player: Player, val orb: ObjType) : UnboundEvent

    private companion object {
        const val COOLDOWN = 5
    }
}

// Charge lifts the god spells' maximum hit from 20 to 30 for seven minutes. The damage formulas
// look at the `buff_charge_spell_disabled` varbit for this (its name is the client's, and it is
// the flag they use), so it is set while the charge lasts. Charging again has to wait for the
// minute after the cast.
class ChargeSpell @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    private var Player.charged by intVarBit(varbits.buff_charge_spell_disabled)

    override fun ScriptContext.startup() {
        with(caster) { onSpell(objs.spell_charge) { charge() } }

        onPlayerSoftTimer(utility_timers.charge) { fade(player) }
        // A charge does not outlast a logout.
        onPlayerLogin { player.charged = 0 }
    }

    private fun ProtectedAccess.charge() {
        if (player.charged != 0) {
            mes("Your magical charge is still active.")
            return
        }
        with(caster) { cast(objs.spell_charge, COOLDOWN) } ?: return
        player.charged = 1
        player.softTimer(utility_timers.charge, DURATION)
        anim(utility_seqs.cast_god_charge)
        mes("You feel charged with magic power.")
        publish(Charged(player))
    }

    private fun fade(player: Player) {
        player.clearSoftTimer(utility_timers.charge)
        player.charged = 0
        player.mes("Your magical charge fades away.")
    }

    /** Published once [player] has charged their god spells. */
    class Charged(val player: Player) : UnboundEvent

    private companion object {
        /** Seven minutes. */
        const val DURATION = 700
        const val COOLDOWN = 5
    }
}
