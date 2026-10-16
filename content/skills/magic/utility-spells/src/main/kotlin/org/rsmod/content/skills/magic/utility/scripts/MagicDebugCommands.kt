package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.magic.utility.configs.Enchantments
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helpers for magic. Most spells need items that have no source on the isle yet, so:
 * - `::magickit <kit>` puts what is needed to try one part of Magic into the inventory (run it with
 *   no kit to list them)
 * - `::spellbook <book>` swaps to the standard, ancients, lunars or arceuus spellbook
 */
internal class MagicDebugCommands @Inject constructor(private val objTypes: ObjTypeList) :
    PluginScript() {
    private var Player.spellbook by intVarBit(varbits.spellbook)

    override fun ScriptContext.startup() {
        onCommand("magickit") {
            modLevel = modlevels.admin
            desc = "Give the items to test one part of Magic"
            invalidArgs = "Use as ::magickit <kit> (kits: ${kits().keys.joinToString(", ")})"
            cheat {
                val name = args.getOrNull(0)?.lowercase()
                val kit = name?.let { kits()[it] }
                if (kit == null) {
                    player.mes("Kits: ${kits().keys.joinToString(", ")}")
                    return@cheat
                }
                for ((obj, count) in kit) {
                    player.invAdd(player.inv, obj, count, strict = false)
                }
                player.mes("Gave the $name kit.")
            }
        }

        onCommand("spellbook") {
            modLevel = modlevels.admin
            desc = "Swap to another spellbook"
            invalidArgs = "Use as ::spellbook <standard|ancients|lunars|arceuus>"
            cheat {
                val book =
                    args.getOrNull(0)?.let { name -> Spellbook.entries.find { it.matches(name) } }
                if (book == null) {
                    player.mes("Use as ::spellbook <standard|ancients|lunars|arceuus>")
                    return@cheat
                }
                player.spellbook = book.varValue
                player.mes("You are now using the ${book.name.lowercase()} spellbook.")
            }
        }
    }

    private fun Spellbook.matches(name: String): Boolean =
        this.name.startsWith(name, ignoreCase = true)

    private fun obj(internal: String): ObjType? =
        objTypes.values.firstOrNull { it.internalName == internal }

    /** Each kit is a list of what to give and how many of it. */
    private fun kits(): Map<String, List<Pair<ObjType, Int>>> {
        fun items(vararg entries: Pair<String, Int>) =
            entries.mapNotNull { (name, count) -> obj(name)?.let { it to count } }

        fun jewellery(levels: IntRange) =
            levels.flatMap { Enchantments.byLevel.getValue(it) }.map { it.input to 1 }

        return linkedMapOf(
            "runes" to
                items(
                    "airrune" to 5000,
                    "waterrune" to 5000,
                    "earthrune" to 5000,
                    "firerune" to 5000,
                    "mindrune" to 5000,
                    "bodyrune" to 5000,
                    "cosmicrune" to 5000,
                    "chaosrune" to 5000,
                    "naturerune" to 5000,
                    "lawrune" to 5000,
                    "deathrune" to 5000,
                    "bloodrune" to 5000,
                    "soulrune" to 5000,
                    "astralrune" to 5000,
                    "wrathrune" to 5000,
                ),
            "alch" to
                items(
                    "gold_ring" to 1,
                    "rune_scimitar" to 1,
                    "rune_platebody" to 1,
                    "dragon_dagger" to 1,
                    "adamant_platebody" to 1,
                ),
            "superheat" to
                items(
                    "copper_ore" to 5,
                    "tin_ore" to 5,
                    "iron_ore" to 5,
                    "silver_ore" to 3,
                    "gold_ore" to 3,
                    "mithril_ore" to 3,
                    "adamantite_ore" to 3,
                    "runite_ore" to 2,
                    "coal" to 100,
                ),
            "bones" to
                items(
                    "bones" to 6,
                    "big_bones" to 6,
                    "bat_bones" to 6,
                    "wolf_bones" to 6,
                    "dragon_bones" to 4,
                ),
            "jewellery" to jewellery(1..3),
            "jewellery2" to jewellery(4..7),
            "orbs" to items("stafforb" to 4),
            "grab" to items("airrune" to 100, "lawrune" to 100),
            "staffs" to
                items(
                    "staff_of_air" to 1,
                    "staff_of_water" to 1,
                    "staff_of_earth" to 1,
                    "staff_of_fire" to 1,
                    "ibans_staff" to 1,
                    "slayer_staff" to 1,
                    "saradomin_staff" to 1,
                    "guthix_staff" to 1,
                    "zamorak_staff" to 1,
                    "saradomin_cape" to 1,
                    "guthix_cape" to 1,
                    "zamorak_cape" to 1,
                ),
        )
    }
}
