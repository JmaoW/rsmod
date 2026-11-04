package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.ui.ifCloseModal
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvType
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList

/**
 * The Colosseum's two screens: the intermission (the loot so far and the next wave's, and three
 * modifiers to pick from) and the rewards chest. The client's own scripts draw them from three
 * inventories and a few numbers (tsps' capture of the live game):
 * - the intermission is interface 865 opened as the main modal, with the inventories 843 (earned so
 *   far), 844 (the next wave's) and 845 (the last wave's) sent first; script 4931 then takes the
 *   waves cleared, the three modifier keys, the three inventories' values and a 0. Its buttons
 *   15-17 pick (varbit 9788) and redraw through scripts 4934 and 4932; button 41 confirms.
 * - the chest is interface 246: its items are inventory 843 (script 149 sets their ops up), with
 *   Bank-all (5), Take-all (7) and Discard-all (9), and "Total Value" on component 3.
 */
class ColosseumScreens
@Inject
constructor(
    private val eventBus: EventBus,
    private val invTypes: InvTypeList,
    private val objTypes: ObjTypeList,
) {
    /** What [items] are worth, by the items' value (the client shows it as the loot's value). */
    fun valueOf(items: List<ColosseumItem>): Int {
        var total = 0L
        for (item in items) {
            total += objTypes[item.obj].cost.toLong() * item.count
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun inventory(player: Player, type: InvType): Inventory =
        player.invMap.getOrPut(invTypes[type])

    /** Puts [items] into [player]'s copy of the client inventory [type] and sends it. */
    fun send(player: Player, type: InvType, items: List<ColosseumItem>) {
        val inv = inventory(player, type)
        for (slot in 0 until inv.size) {
            inv[slot] = items.getOrNull(slot)?.let { InvObj(it.obj, it.count) }
        }
        player.startInvTransmit(inv)
    }

    fun sendLoot(member: ColosseumMember) {
        send(member.player, colosseum_invs.rewards, member.rewards)
        send(member.player, colosseum_invs.future, member.future)
        send(member.player, colosseum_invs.previous, member.previous)
    }

    /** Sets every tiered modifier's tier varbit, which the intermission screen reads. */
    fun syncTiers(player: Player, modifiers: ColosseumModifierSet) {
        for (modifier in ColosseumModifier.entries) {
            val varbit = modifier.tierVarbit ?: continue
            VarPlayerIntMapSetter.set(player, varbit, modifiers.tier(modifier))
        }
    }

    // The intermission ----------------------------------------------------------------------------

    fun openIntermission(run: ColosseumRun, member: ColosseumMember) {
        val player = member.player
        sendLoot(member)
        syncTiers(player, run.modifiers)
        VarPlayerIntMapSetter.set(player, colosseum_varbits.selected_modifier, 0)
        player.ifOpenMainModal(colosseum_interfaces.intermission, eventBus)
        val keys = run.offered.map { it.key }
        val values =
            listOf(valueOf(member.rewards), valueOf(member.future), valueOf(member.previous))
        player.runClientScript(INTERMISSION_INIT, listOf(wavesDone(run)) + keys + values + 0)
        val c = colosseum_components
        player.ifSetEvents(c.left_loot, 0..3, IfEvent.Op1)
        player.ifSetEvents(c.right_loot, 0..3, IfEvent.Op1)
        player.ifSetEvents(c.mod_list, 0..26, IfEvent.Op1)
        player.ifSetEvents(c.tab_content, 0..30, IfEvent.Op1)
    }

    /** A modifier button: the selection, then the screen's own redraws (its click script's). */
    fun select(run: ColosseumRun, player: Player, choice: Int) {
        VarPlayerIntMapSetter.set(player, colosseum_varbits.selected_modifier, choice)
        player.runClientScript(INTERMISSION_BUTTONS, run.offered.map { it.key } + wavesDone(run))
        player.runClientScript(INTERMISSION_CONTINUE, wavesDone(run))
    }

    fun closeIntermission(player: Player) {
        VarPlayerIntMapSetter.set(player, colosseum_varbits.selected_modifier, 0)
        if (colosseum_interfaces.intermission in player.ui) {
            player.ifCloseModal(colosseum_interfaces.intermission, eventBus)
        }
    }

    /**
     * The "waves cleared" the screen is told: the wave before the next, so a shortened run's screen
     * names the real wave to come.
     */
    private fun wavesDone(run: ColosseumRun): Int = (run.nextWave ?: ColosseumWaves.FINAL_WAVE) - 1

    // The chest -----------------------------------------------------------------------------------

    fun openChest(member: ColosseumMember) {
        val player = member.player
        refreshChest(member)
        player.ifOpenMainModal(colosseum_interfaces.reward_chest, eventBus)
        val items = colosseum_components.chest_items
        player.runClientScript(
            ITEM_OPS,
            items.packed,
            invTypes[colosseum_invs.rewards].id,
            ITEM_COLUMNS,
            ITEM_ROWS,
            0,
            -1,
            "Take",
            "",
            "",
            "",
            "",
        )
        player.ifSetEvents(items, 0 until CHEST_SIZE, IfEvent.Op1, IfEvent.Op10)
    }

    fun refreshChest(member: ColosseumMember) {
        send(member.player, colosseum_invs.rewards, member.rewards)
        member.player.ifSetText(
            colosseum_components.chest_value,
            "Total Value: ${"%,d".format(valueOf(member.rewards))}",
        )
    }

    companion object {
        const val INTERMISSION_INIT = 4931
        const val INTERMISSION_BUTTONS = 4934
        const val INTERMISSION_CONTINUE = 4932
        const val ITEM_OPS = 149
        const val ITEM_COLUMNS = 3
        const val ITEM_ROWS = 5
        const val CHEST_SIZE = 28
    }
}
