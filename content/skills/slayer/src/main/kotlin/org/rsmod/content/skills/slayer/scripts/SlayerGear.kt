package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Making Slayer equipment:
 * - the Slayer helmet, from a black mask, a facemask, earmuffs, a nose peg, a spiny helmet and an
 *   enchanted gem (used on the black mask), once "Malevolent Masquerade" is bought, with 55
 *   Crafting
 * - a lit bug lantern, from an unlit one and a tinderbox, with 33 Firemaking
 */
class SlayerGear @Inject constructor(private val engine: SlayerEngine) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(SlayerObjs.gem, SlayerObjs.black_mask) { assembleHelm() }
        onOpHeldU(SlayerObjs.tinderbox, SlayerObjs.bug_lantern) { lightLantern() }
    }

    fun ProtectedAccess.assembleHelm() {
        if (!engine.isUnlocked(player, HELM_UNLOCK)) {
            mes("You have not learned how to make a Slayer helmet.")
            return
        }
        if (player.statBase(stats.crafting) < HELM_CRAFTING) {
            mes("You need a Crafting level of $HELM_CRAFTING to make a Slayer helmet.")
            return
        }
        val missing = HELM_PARTS.firstOrNull { it !in player.inv }
        if (missing != null) {
            mes(
                "You need a facemask, earmuffs, a nose peg, a spiny helmet and an enchanted gem too."
            )
            return
        }
        for (part in HELM_PARTS) {
            invDel(player.inv, part, 1)
        }
        invAdd(player.inv, SlayerObjs.helm, 1)
        mes("You combine the parts into a Slayer helmet.")
        engine.helmAssembled(player)
    }

    fun ProtectedAccess.lightLantern() {
        if (player.statBase(stats.firemaking) < LANTERN_FIREMAKING) {
            mes("You need a Firemaking level of $LANTERN_FIREMAKING to light the lantern.")
            return
        }
        invDel(player.inv, SlayerObjs.bug_lantern, 1)
        invAdd(player.inv, SlayerObjs.bug_lantern_lit, 1)
        mes("You light the bug lantern.")
    }

    companion object {
        /** The "Malevolent Masquerade" unlock. */
        const val HELM_UNLOCK = 5
        const val HELM_CRAFTING = 55
        const val LANTERN_FIREMAKING = 33

        val HELM_PARTS: List<ObjType> =
            listOf(
                SlayerObjs.black_mask,
                SlayerObjs.facemask,
                SlayerObjs.earmuffs,
                SlayerObjs.nose_peg,
                SlayerObjs.spiny_helmet,
                SlayerObjs.gem,
            )
    }
}
