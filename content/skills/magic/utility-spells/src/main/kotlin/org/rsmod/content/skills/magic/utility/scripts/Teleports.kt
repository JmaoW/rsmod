package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

// Every teleport spell that has a destination (the `spell_telecoord` param) teleports the caster
// there after the cast animation, for its runes and cast experience. The destinations are the
// cache's by default; the Isle of Souls overrides them all. Home Teleport spells have no
// destination and are left to the isle's own script. Teleports work everywhere, in combat and in
// the Fight Caves and the Inferno alike.
class Teleports
@Inject
constructor(
    private val caster: SpellCaster,
    private val registry: MagicSpellRegistry,
    private val destinations: TeleportDestinations,
    private val teams: SpellTeams,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    /** How a spellbook's teleports look. */
    private class Style(val seq: SeqType, val spotanim: SpotanimType, val reverse: SeqType?)

    private val standard =
        Style(
            utility_seqs.cast_teleport,
            utility_spotanims.teleport,
            utility_seqs.cast_teleport_reverse,
        )

    private val ancient =
        Style(utility_seqs.cast_teleport_ancient, utility_spotanims.teleport_ancient, null)

    private val lunar =
        Style(utility_seqs.cast_teleport_lunar, utility_spotanims.teleport_lunar, null)

    override fun ScriptContext.startup() {
        with(caster) {
            for (spell in registry.teleportSpells()) {
                if (!destinations.has(spell.obj)) {
                    continue
                }
                onSpell(spell.obj) { teleport(spell) }
            }
        }
    }

    /** Where [spell] takes the caster, or `null` if it is not a plain teleport. */
    fun destination(spell: MagicSpell): CoordGrid? = destinations.destination(spell.obj)

    private fun styleOf(spell: MagicSpell): Style =
        when (spell.spellbook) {
            Spellbook.Ancients -> ancient
            Spellbook.Lunars -> lunar
            else -> standard
        }

    private suspend fun ProtectedAccess.teleport(spell: MagicSpell) {
        val destination = destination(spell) ?: return
        with(caster) { cast(spell.obj, COOLDOWN) } ?: return
        val style = styleOf(spell)
        anim(style.seq)
        spotanim(style.spotanim, height = SPOTANIM_HEIGHT)
        soundSynth(synths.teleport_all)
        // A group teleport takes the teammates standing within a tile along.
        val group =
            if (spell.obj.id in GROUP_SPELLS) teams.nearby(player, GROUP_RANGE) else emptyList()
        for (companion in group) {
            companion.spotanim(style.spotanim, height = SPOTANIM_HEIGHT)
        }
        beginTeleport(CAST_TICKS)
        delay(CAST_TICKS)
        // `telejump` makes the client snap its camera to the new tile instead of gliding there.
        telejump(destination)
        for (companion in group) {
            PathingEntityCommon.telejump(companion, collision, destination)
            companion.resetSpotanim()
            companion.mes("${player.displayName} teleports you.")
        }
        soundSynth(synths.teleport_reverse)
        if (style.reverse != null) {
            anim(style.reverse)
        }
        resetSpotanim()
        publish(Teleported(player, spell, destination))
    }

    /** Published once [player] has arrived at [destination] by casting [spell]. */
    class Teleported(val player: Player, val spell: MagicSpell, val destination: CoordGrid) :
        UnboundEvent

    private companion object {
        /** The Tele Group spells, which take along the teammates within [GROUP_RANGE]. */
        val GROUP_SPELLS: Set<Int> =
            setOf(
                    objs.spell_moonclan_telegroup,
                    objs.spell_waterbirth_telegroup,
                    objs.spell_barboutpost_telegroup,
                    objs.spell_portkhazard_telegroup,
                    objs.spell_fishguard_telegroup,
                    objs.spell_catherby_telegroup,
                    objs.spell_lunarghorrock_telegroup,
                )
                .map { it.id }
                .toSet()

        const val GROUP_RANGE = 1
        const val COOLDOWN = 5
        const val CAST_TICKS = 3
        const val SPOTANIM_HEIGHT = 92
    }
}
