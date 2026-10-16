package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.magic.utility.configs.utility_objs
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Bones to Bananas and Bones to Peaches turn every bone in the inventory into a piece of fruit with
// one cast. Which bones count is the wiki's list; long and curved bones and the dragon bones are
// left alone, and noted bones do not count.
class BonesToFruit @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    override fun ScriptContext.startup() {
        with(caster) {
            onSpell(objs.spell_bones_to_bananas) { convert(objs.spell_bones_to_bananas) }
            onSpell(objs.spell_bones_to_peaches) { convert(objs.spell_bones_to_peaches) }
        }
    }

    private fun ProtectedAccess.convert(spell: ObjType) {
        val fruit =
            if (spell.id == objs.spell_bones_to_bananas.id) utility_objs.banana
            else utility_objs.peach
        val held = BONES.associateWith { invTotal(inv, it) }.filterValues { it > 0 }
        if (held.isEmpty()) {
            mes("You do not have any bones to convert.")
            return
        }

        with(caster) { cast(spell, COOLDOWN) } ?: return
        var total = 0
        for ((bone, count) in held) {
            invDel(inv, bone, count)
            total += count
        }
        invAdd(inv, fruit, total)

        anim(utility_seqs.cast_bones_to_bananas)
        spotanim(utility_spotanims.bones_to_bananas, height = SPOTANIM_HEIGHT)
        soundSynth(synths.bones_to_bananas_all)
        publish(BonesConverted(player, fruit, total))
    }

    /** Published once [count] bones have become [fruit]. */
    class BonesConverted(val player: Player, val fruit: ObjType, val count: Int) : UnboundEvent

    private companion object {
        const val COOLDOWN = 1
        const val SPOTANIM_HEIGHT = 92

        val BONES: List<ObjType> =
            listOf(
                utility_objs.bones,
                utility_objs.burnt_bones,
                utility_objs.bat_bones,
                utility_objs.big_bones,
                utility_objs.wolf_bones,
                utility_objs.monkey_bones,
                utility_objs.jogre_bones,
            )
    }
}
