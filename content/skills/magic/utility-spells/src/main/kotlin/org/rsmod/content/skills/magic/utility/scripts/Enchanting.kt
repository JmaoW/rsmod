package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.magic.utility.configs.Enchantment
import org.rsmod.content.skills.magic.utility.configs.Enchantments
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - Enchant Crossbow Bolt (the bolt spell opens its own interface)
// - the Silvthrill rod and the sickles, which have no source on the isle
//
// Each of the seven Enchant spells turns the jewellery of one or two gems into its enchanted form,
// one item per cast (3 ticks). Charged items come out full, since the isle has no charging.
class Enchanting @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    private class Level(
        val spell: ObjType,
        val level: Int,
        val seq: SeqType,
        val spotanim: SpotanimType,
        val sound: SynthType,
    )

    private val levels =
        listOf(
            Level(
                objs.spell_enchant_lvl_1,
                1,
                utility_seqs.enchant_level_1,
                utility_spotanims.enchant_level_1,
                synths.enchant_sapphire_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_2,
                2,
                utility_seqs.enchant_level_2,
                utility_spotanims.enchant_level_2,
                synths.enchant_emerald_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_3,
                3,
                utility_seqs.enchant_level_3,
                utility_spotanims.enchant_level_3,
                synths.enchant_ruby_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_4,
                4,
                utility_seqs.enchant_ring,
                utility_spotanims.enchant_level_4,
                synths.enchant_diamond_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_5,
                5,
                utility_seqs.enchant_ring,
                utility_spotanims.enchant_level_5,
                synths.enchant_dragon_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_6,
                6,
                utility_seqs.enchant_ring,
                utility_spotanims.enchant_level_6,
                synths.enchant_onyx_amulet,
            ),
            Level(
                objs.spell_enchant_lvl_7,
                7,
                utility_seqs.enchant_ring,
                utility_spotanims.enchant_level_6,
                synths.enchant_onyx_amulet,
            ),
        )

    override fun ScriptContext.startup() {
        with(caster) {
            for (level in levels) {
                onItemSpell(level.spell) { enchant(level, it) }
            }
        }
    }

    private fun ProtectedAccess.enchant(level: Level, target: SpellTarget) {
        val enchantment =
            Enchantments.byLevel.getValue(level.level).firstOrNull { it.input.id == target.obj.id }
        if (enchantment == null) {
            mes("This spell can't be cast on that item.")
            return
        }

        with(caster) { cast(level.spell, COOLDOWN) } ?: return
        invDel(inv, enchantment.input, 1, slot = target.slot)
        invAdd(inv, enchantment.output, 1)

        anim(level.seq)
        spotanim(level.spotanim, height = SPOTANIM_HEIGHT)
        soundSynth(level.sound)
        publish(Enchanted(player, level.level, enchantment))
    }

    /**
     * Published once a Lvl-[level] Enchant spell has turned an item into [enchantment]'s output.
     */
    class Enchanted(val player: Player, val level: Int, val enchantment: Enchantment) :
        UnboundEvent

    private companion object {
        const val COOLDOWN = 3
        const val SPOTANIM_HEIGHT = 92
    }
}
