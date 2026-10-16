package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.script.onOpPlayerT
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Spells the isle does not have a use for. Casting one only tells the player so; nothing is spent.
// - Tele Block and Teleport to Target are for fighting other players
// - Teleport to House needs a house, and the farming, hunter and reanimation spells need those
//   skills (see the magic plan doc)
class DisabledSpells @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    override fun ScriptContext.startup() {
        with(caster) {
            for (spell in WITHOUT_TARGET) {
                onSpell(spell) { mes(UNAVAILABLE) }
            }
            for (spell in ON_ITEMS) {
                onItemSpell(spell) { mes(UNAVAILABLE) }
            }
        }
        onOpPlayerT(caster.spell(objs.spell_tele_block).component) { mes(NO_PLAYERS) }
        onOpPlayerT(caster.spell(objs.spell_cure_other).component) { mes(UNAVAILABLE) }
    }

    companion object {
        const val UNAVAILABLE = "That spell is not available on the Isle of Souls."
        const val NO_PLAYERS = "You cannot cast that on another player on the Isle of Souls."

        /** The reanimation spells, which are cast on ensouled heads. */
        val ON_ITEMS: List<ObjType> =
            listOf(
                objs.spell_basic_reanimation,
                objs.spell_adept_reanimation,
                objs.spell_expert_reanimation,
                objs.spell_master_reanimation,
            )

        val WITHOUT_TARGET: List<ObjType> =
            listOf(
                objs.spell_teleport_to_target,
                objs.spell_poh_teleport,
                // Lunar: no farming, hunter, dreams or poison on the isle, and no NPC Contact
                objs.spell_geomancy,
                objs.spell_hunter_kit,
                objs.spell_npc_contact,
                objs.spell_dream,
                objs.spell_cure_me,
                objs.spell_cure_group,
                // Arceuus: no pickpocketing, player prayer fights, farming or demon prayer drain
                objs.spell_shadow_veil,
                objs.spell_dark_lure,
                objs.spell_ward_of_arceuus,
                objs.spell_lesser_corruption,
                objs.spell_greater_corruption,
                objs.spell_resurrect_crops,
            )
    }
}
