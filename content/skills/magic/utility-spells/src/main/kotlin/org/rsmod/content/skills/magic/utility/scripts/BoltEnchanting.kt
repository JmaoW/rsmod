package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.magic.utility.configs.BoltEnchantment
import org.rsmod.content.skills.magic.utility.configs.BoltEnchantments
import org.rsmod.content.skills.magic.utility.configs.BoltSeqs
import org.rsmod.content.skills.magic.utility.configs.BoltSpotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the real game lists the ten spells in the spellbook itself; no component for them could be
//   found in the cache, so the spellbook's "view bolt enchantment spells" button asks with a menu
//
// Each of the ten Enchant Crossbow Bolt spells turns the bolts of one gem in the inventory into
// their enchanted form, ten of them per cast (3 ticks). Regular bolts go first, then dragon bolts.
class BoltEnchanting @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    override fun ScriptContext.startup() {
        with(caster) { onSpell(objs.view_bolt_enchantment_spells) { choose() } }
    }

    /** Asks which gem's bolts to enchant, five spells to a page, and casts that spell. */
    private suspend fun ProtectedAccess.choose() {
        var page = 0
        while (true) {
            val picked =
                when (page) {
                    0 -> choice5(label(0), 0, label(1), 1, label(2), 2, label(3), 3, MORE, NEXT)
                    1 -> choice5(label(4), 4, label(5), 5, label(6), 6, label(7), 7, MORE, NEXT)
                    else -> choice3(label(8), 8, label(9), 9, BACK, NEXT)
                }
            if (picked == NEXT) {
                page = (page + 1) % PAGES
                continue
            }
            enchant(BoltEnchantments.all[picked])
            return
        }
    }

    private fun label(index: Int): String {
        val enchantment = BoltEnchantments.all[index]
        return "${enchantment.gem.replaceFirstChar { it.uppercase() }} (level ${level(enchantment)})"
    }

    private fun level(enchantment: BoltEnchantment): Int = caster.spell(enchantment.spell).levelReq

    fun ProtectedAccess.enchant(enchantment: BoltEnchantment) {
        val pairs =
            listOf(
                enchantment.input to enchantment.output,
                enchantment.dragonInput to enchantment.dragonOutput,
            )
        if (pairs.sumOf { (input, _) -> invTotal(inv, input) } == 0) {
            mes("You need ${enchantment.gem} bolts to cast this spell.")
            return
        }
        with(caster) { cast(enchantment.spell, COOLDOWN) } ?: return

        var left = BoltEnchantments.BOLTS_PER_CAST
        var enchanted = 0
        for ((input, output) in pairs) {
            val count = min(left, invTotal(inv, input))
            if (count == 0) {
                continue
            }
            invDel(inv, input, count)
            invAdd(inv, output, count)
            left -= count
            enchanted += count
        }

        anim(BoltSeqs.enchant)
        spotanim(BoltSpotanims.enchant, height = SPOTANIM_HEIGHT)
        publish(BoltsEnchanted(player, enchantment, enchanted))
    }

    /** Published once an Enchant Crossbow Bolt spell has enchanted [count] bolts. */
    class BoltsEnchanted(val player: Player, val enchantment: BoltEnchantment, val count: Int) :
        UnboundEvent

    private companion object {
        const val COOLDOWN = 3
        const val SPOTANIM_HEIGHT = 92
        const val PAGES = 3
        const val NEXT = -1
        const val MORE = "More..."
        const val BACK = "Back"
    }
}
