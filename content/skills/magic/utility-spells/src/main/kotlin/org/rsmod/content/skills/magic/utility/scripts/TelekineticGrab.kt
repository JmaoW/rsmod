package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.ObjTEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpObjT
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: line of sight. The grab works through walls here; the real spell needs a clear line.
//
// Telekinetic Grab takes an item lying up to ten tiles away straight into the inventory, without
// walking to it. It works from where the caster stands, so it is the one spell that goes through
// the engine's spell-on-ground-item message (`OpObjT`) rather than an approach.
class TelekineticGrab
@Inject
constructor(
    private val caster: SpellCaster,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpObjT(caster.spell(objs.spell_tele_grab).component) { grab(it) }
    }

    private fun ProtectedAccess.grab(event: ObjTEvents.Cast) {
        val obj = event.obj
        if (distanceTo(obj.coords) > RANGE) {
            mes("That's too far away.")
            return
        }
        // Only a check: the transaction is dropped, since the runes come out of the inventory too.
        val space = player.invAdd(inv, obj.type, obj.count, autoCommit = false)
        if (space.failure) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }

        with(caster) { cast(objs.spell_tele_grab, COOLDOWN) } ?: return
        if (!objRepo.del(obj)) {
            mes("Too late - it's gone!")
            return
        }
        player.invAdd(inv, obj.type, obj.count)

        anim(utility_seqs.cast_telegrab)
        spotanimMap(world, utility_spotanims.telegrab_impact, obj.coords, height = SPOTANIM_HEIGHT)
        soundSynth(synths.telegrab_all)
        publish(Grabbed(player, event.type, obj.count))
    }

    /** Published once [count] of [type] has been grabbed from the ground. */
    class Grabbed(val player: Player, val type: UnpackedObjType, val count: Int) : UnboundEvent

    private companion object {
        const val RANGE = 10
        const val COOLDOWN = 3
        const val SPOTANIM_HEIGHT = 20
    }
}
