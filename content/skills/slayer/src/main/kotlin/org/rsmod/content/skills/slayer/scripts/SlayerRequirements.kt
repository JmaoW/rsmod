package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackChecks
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerTables
import org.rsmod.content.skills.slayer.configs.StatReq
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What it takes to harm a Slayer monster:
 * - the Slayer level the monster asks for, checked when the attack starts (current level, so a
 *   boost counts), as for every Slayer monster
 * - turoth and kurask only take damage from leaf-bladed weapons, broad ammunition and the Magic
 *   Dart spell cast with a Slayer's staff
 * - harpie bug swarms only take damage while the player wields a lit bug lantern
 *
 * A hit that does not meet its monster's rule does no damage.
 */
class SlayerRequirements @Inject constructor(private val attackChecks: NpcAttackChecks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        attackChecks.register { access, npc -> levelAllows(access, npc) }
        onEvent<NpcHitEvents.ModifyAny> {
            val player = source ?: return@onEvent
            val task = npc.visType.param(SlayerParams.task_id)
            val allowed =
                when (task) {
                    TUROTH,
                    KURASK -> leafBladed(hit)
                    HARPIE_BUG_SWARM -> litLantern(player)
                    else -> true
                }
            if (!allowed) {
                hit.damage = 0
            }
        }
    }

    private fun levelAllows(access: ProtectedAccess, npc: Npc): Boolean {
        val required = requiredLevel(npc)
        if (required <= 0 || access.player.stat(stats.slayer) >= required) {
            return true
        }
        access.mes("You need a higher Slayer level to know how to wound this monster.")
        return false
    }

    private fun leafBladed(hit: HitBuilder): Boolean =
        when (hit.type) {
            HitType.Melee -> LEAF_BLADED.any { hit.isRighthandObj(it) }
            HitType.Ranged -> BROAD_AMMO.any { hit.isSecondaryObj(it) }
            HitType.Magic ->
                hit.isSecondaryObj(objs.spell_magic_dart) &&
                    SLAYER_STAFFS.any { hit.isRighthandObj(it) }
            HitType.Typeless -> true
        }

    private fun litLantern(player: Player): Boolean =
        player.lefthand.isAny(listOf(SlayerObjs.bug_lantern_lit))

    private fun InvObj?.isAny(types: List<ObjType>): Boolean =
        this != null && types.any { isType(it) }

    companion object {
        const val TUROTH = 36
        const val KURASK = 45
        const val HARPIE_BUG_SWARM = 70

        private val LEAF_BLADED =
            listOf(
                SlayerObjs.leaf_bladed_spear,
                SlayerObjs.leaf_bladed_sword,
                SlayerObjs.leaf_bladed_battleaxe,
            )

        private val SLAYER_STAFFS = listOf(SlayerObjs.staff, SlayerObjs.slayer_staff_enchanted)

        private val BROAD_AMMO =
            listOf(
                SlayerObjs.broad_arrows,
                SlayerObjs.broad_arrows_2,
                SlayerObjs.broad_arrows_3,
                SlayerObjs.broad_arrows_4,
                SlayerObjs.broad_arrows_5,
                SlayerObjs.broad_bolts,
                SlayerObjs.broad_bolts_amethyst,
            )

        /**
         * The Slayer level needed to harm [npc]: the npc's own requirement, else the Slayer level
         * its task asks for.
         */
        fun requiredLevel(npc: Npc): Int {
            val type = npc.visType
            val own = type.param(params.slayer_levelrequire)
            if (own > 0) {
                return own
            }
            val task = SlayerTables.default.tasks[type.param(SlayerParams.task_id)] ?: return 0
            return task.stats.filter { it.statId == StatReq.SLAYER }.maxOfOrNull { it.level } ?: 0
        }
    }
}
