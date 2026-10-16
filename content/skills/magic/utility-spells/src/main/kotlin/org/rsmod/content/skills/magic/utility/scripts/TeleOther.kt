package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.events.interact.PlayerTEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApPlayerT
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

// Teleother sends another player where the spell's teleport would send the caster. The real game
// asks the target to accept; here the target must be on the caster's team (see [SpellTeams]),
// which counts as accepting. The caster pays the runes and gets the cast experience.
class TeleOther
@Inject
constructor(
    private val caster: SpellCaster,
    private val destinations: TeleportDestinations,
    private val teams: SpellTeams,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val spells: List<ObjType> =
        listOf(
            objs.spell_teleother_lumbridge,
            objs.spell_teleother_falador,
            objs.spell_teleother_camelot,
        )

    override fun ScriptContext.startup() {
        for (spell in spells) {
            val component = caster.spell(spell).component
            onApPlayerT(component) { teleport(spell, it) }
        }
    }

    private suspend fun ProtectedAccess.teleport(spell: ObjType, event: PlayerTEvents.Ap) {
        val target = event.target
        if (!teams.areTeammates(player, target)) {
            mes("You can only teleport members of your own team.")
            return
        }
        val destination = destinations.destination(spell)
        if (destination == null) {
            mes("That teleport has nowhere to go.")
            return
        }

        with(caster) { cast(spell, COOLDOWN) } ?: return
        anim(utility_seqs.cast_teleport_other)
        spotanim(utility_spotanims.teleport_other_casting, height = SPOTANIM_HEIGHT)
        soundSynth(synths.tele_other_cast)
        target.anim(utility_seqs.teleport_other_impact)
        target.spotanim(utility_spotanims.teleport_other_impact, height = SPOTANIM_HEIGHT)
        delay(CAST_TICKS)

        PathingEntityCommon.telejump(target, collision, destination)
        target.resetAnim()
        target.mes("${player.displayName} teleports you.")
        publish(TeleportedOther(player, target, spell))
    }

    /** Published once [caster] has teleported [target]. */
    class TeleportedOther(val caster: Player, val target: Player, val spell: ObjType) :
        UnboundEvent

    private companion object {
        const val COOLDOWN = 5
        const val CAST_TICKS = 3
        const val SPOTANIM_HEIGHT = 92
    }
}
