package org.rsmod.api.specials.scripts

import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.timers
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.ring
import org.rsmod.api.player.specRegenInterval
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class SpecialEnergyScript : PluginScript() {
    private val Player.newAccount by boolVarBit(varbits.new_player_account)
    private var Player.specialAttackEnergy by intVarp(varps.sa_energy)

    override fun ScriptContext.startup() {
        onPlayerLogin { player.initRegenTimer() }
        onPlayerSoftTimer(timers.spec_regen) { player.specRegen() }
        onWearposChange {
            if (wearpos == Wearpos.Ring) {
                player.ringChange()
            }
        }
    }

    private fun Player.initRegenTimer() {
        if (newAccount) {
            specialAttackEnergy = constants.sa_max_energy
        }
        softTimer(timers.spec_regen, specRegenInterval())
    }

    private fun Player.specRegen() {
        val increased = min(constants.sa_max_energy, specialAttackEnergy + 100)
        if (increased > specialAttackEnergy) {
            specialAttackEnergy = increased
        }
        // Picks up the Lightbearer's rate after a ring change shortened just the one cycle.
        softTimer(timers.spec_regen, specRegenInterval())
    }

    /**
     * Lightbearer (wiki): equipping it never delays the next restore, it only brings it forward to
     * at most 25 cycles away; taking it off always restarts the normal 50-cycle timer. The timer's
     * interval tells whether it was worn: it is below 50 only while the ring is on.
     */
    private fun Player.ringChange() {
        val wearing = EquipmentChecks.isLightbearer(ring)
        val interval = softTimerInterval(timers.spec_regen)
        val wasWearing = interval != null && interval < constants.spec_regen_interval
        if (wearing == wasWearing) {
            return
        }
        if (wearing) {
            val remaining = softTimerRemaining(timers.spec_regen)
            val cap = constants.spec_regen_interval_lightbearer
            val next = if (remaining == null || remaining <= 0) cap else min(remaining, cap)
            softTimer(timers.spec_regen, next)
        } else {
            softTimer(timers.spec_regen, constants.spec_regen_interval)
        }
    }
}
