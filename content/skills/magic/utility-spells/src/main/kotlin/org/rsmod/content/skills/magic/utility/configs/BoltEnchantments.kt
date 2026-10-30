package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.type.obj.ObjType

object BoltObjs : ObjReferences() {
    val opal = find("opal_bolt")
    val jade = find("xbows_crossbow_bolts_blurite_tipped_jade")
    val pearl = find("pearl_bolt")
    val topaz = find("xbows_crossbow_bolts_steel_tipped_redtopaz")
    val sapphire = find("xbows_crossbow_bolts_mithril_tipped_sapphire")
    val emerald = find("xbows_crossbow_bolts_mithril_tipped_emerald")
    val ruby = find("xbows_crossbow_bolts_adamantite_tipped_ruby")
    val diamond = find("xbows_crossbow_bolts_adamantite_tipped_diamond")
    val dragonstone = find("xbows_crossbow_bolts_runite_tipped_dragonstone")
    val onyx = find("xbows_crossbow_bolts_runite_tipped_onyx")

    val opal_e = find("xbows_crossbow_bolts_bronze_tipped_opal_enchanted")
    val jade_e = find("xbows_crossbow_bolts_blurite_tipped_jade_enchanted")
    val pearl_e = find("xbows_crossbow_bolts_iron_tipped_pearl_enchanted")
    val topaz_e = find("xbows_crossbow_bolts_steel_tipped_redtopaz_enchanted")
    val sapphire_e = find("xbows_crossbow_bolts_mithril_tipped_sapphire_enchanted")
    val emerald_e = find("xbows_crossbow_bolts_mithril_tipped_emerald_enchanted")
    val ruby_e = find("xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted")
    val diamond_e = find("xbows_crossbow_bolts_adamantite_tipped_diamond_enchanted")
    val dragonstone_e = find("xbows_crossbow_bolts_runite_tipped_dragonstone_enchanted")
    val onyx_e = find("xbows_crossbow_bolts_runite_tipped_onyx_enchanted")

    val dragon_opal = find("dragon_bolts_unenchanted_opal")
    val dragon_jade = find("dragon_bolts_unenchanted_jade")
    val dragon_pearl = find("dragon_bolts_unenchanted_pearl")
    val dragon_topaz = find("dragon_bolts_unenchanted_topaz")
    val dragon_sapphire = find("dragon_bolts_unenchanted_sapphire")
    val dragon_emerald = find("dragon_bolts_unenchanted_emerald")
    val dragon_ruby = find("dragon_bolts_unenchanted_ruby")
    val dragon_diamond = find("dragon_bolts_unenchanted_diamond")
    val dragon_dragonstone = find("dragon_bolts_unenchanted_dragonstone")
    val dragon_onyx = find("dragon_bolts_unenchanted_onyx")

    val dragon_opal_e = find("dragon_bolts_enchanted_opal")
    val dragon_jade_e = find("dragon_bolts_enchanted_jade")
    val dragon_pearl_e = find("dragon_bolts_enchanted_pearl")
    val dragon_topaz_e = find("dragon_bolts_enchanted_topaz")
    val dragon_sapphire_e = find("dragon_bolts_enchanted_sapphire")
    val dragon_emerald_e = find("dragon_bolts_enchanted_emerald")
    val dragon_ruby_e = find("dragon_bolts_enchanted_ruby")
    val dragon_diamond_e = find("dragon_bolts_enchanted_diamond")
    val dragon_dragonstone_e = find("dragon_bolts_enchanted_dragonstone")
    val dragon_onyx_e = find("dragon_bolts_enchanted_onyx")
}

object BoltSeqs : SeqReferences() {
    val enchant = find("xbows_enchanting_tips_spot")
}

object BoltSpotanims : SpotanimReferences() {
    val enchant = find("xbows_enchanting_tips_spot")
}

/**
 * One Enchant Crossbow Bolt spell: a cast turns up to [BoltEnchantments.BOLTS_PER_CAST] of the
 * [input] (or, once those run out, of the [dragonInput]) into [output] (or [dragonOutput]).
 */
class BoltEnchantment(
    val gem: String,
    val spell: ObjType,
    val input: ObjType,
    val output: ObjType,
    val dragonInput: ObjType,
    val dragonOutput: ObjType,
)

/** The ten Enchant Crossbow Bolt spells, each for the bolts of one gem (OSRS Wiki). */
object BoltEnchantments {
    const val BOLTS_PER_CAST = 10

    val all: List<BoltEnchantment> =
        listOf(
            BoltEnchantment(
                "opal",
                objs.spell_enchant_bolt_opal,
                BoltObjs.opal,
                BoltObjs.opal_e,
                BoltObjs.dragon_opal,
                BoltObjs.dragon_opal_e,
            ),
            BoltEnchantment(
                "sapphire",
                objs.spell_enchant_bolt_sapphire,
                BoltObjs.sapphire,
                BoltObjs.sapphire_e,
                BoltObjs.dragon_sapphire,
                BoltObjs.dragon_sapphire_e,
            ),
            BoltEnchantment(
                "jade",
                objs.spell_enchant_bolt_jade,
                BoltObjs.jade,
                BoltObjs.jade_e,
                BoltObjs.dragon_jade,
                BoltObjs.dragon_jade_e,
            ),
            BoltEnchantment(
                "pearl",
                objs.spell_enchant_bolt_pearl,
                BoltObjs.pearl,
                BoltObjs.pearl_e,
                BoltObjs.dragon_pearl,
                BoltObjs.dragon_pearl_e,
            ),
            BoltEnchantment(
                "emerald",
                objs.spell_enchant_bolt_emerald,
                BoltObjs.emerald,
                BoltObjs.emerald_e,
                BoltObjs.dragon_emerald,
                BoltObjs.dragon_emerald_e,
            ),
            BoltEnchantment(
                "red topaz",
                objs.spell_enchant_bolt_topaz,
                BoltObjs.topaz,
                BoltObjs.topaz_e,
                BoltObjs.dragon_topaz,
                BoltObjs.dragon_topaz_e,
            ),
            BoltEnchantment(
                "ruby",
                objs.spell_enchant_bolt_ruby,
                BoltObjs.ruby,
                BoltObjs.ruby_e,
                BoltObjs.dragon_ruby,
                BoltObjs.dragon_ruby_e,
            ),
            BoltEnchantment(
                "diamond",
                objs.spell_enchant_bolt_diamond,
                BoltObjs.diamond,
                BoltObjs.diamond_e,
                BoltObjs.dragon_diamond,
                BoltObjs.dragon_diamond_e,
            ),
            BoltEnchantment(
                "dragonstone",
                objs.spell_enchant_bolt_dragonstone,
                BoltObjs.dragonstone,
                BoltObjs.dragonstone_e,
                BoltObjs.dragon_dragonstone,
                BoltObjs.dragon_dragonstone_e,
            ),
            BoltEnchantment(
                "onyx",
                objs.spell_enchant_bolt_onyx,
                BoltObjs.onyx,
                BoltObjs.onyx_e,
                BoltObjs.dragon_onyx,
                BoltObjs.dragon_onyx_e,
            ),
        )
}
