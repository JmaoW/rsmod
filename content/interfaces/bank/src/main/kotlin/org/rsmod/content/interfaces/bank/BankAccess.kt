package org.rsmod.content.interfaces.bank

import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.invs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.interfaces.bank.configs.bank_queues
import org.rsmod.content.interfaces.bank.configs.deposit_interfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.inv.InvTypeList

/**
 * The one place a bank is opened from the world (booths, chests, bankers).
 *
 * A bank is normally one player's own inventory, so nobody else can be in it. Some content hands
 * several players the very same bank inventory (a shared team bank). The bank screen addresses
 * items by slot number and shifts slots around as items come and go, so two players in it at once
 * would click each other's items by mistake. This refuses to open a bank another player is still
 * using, and does nothing for an ordinary bank because nobody else holds it.
 */
class BankAccess
@Inject
constructor(private val players: PlayerList, private val invTypes: InvTypeList) {
    /**
     * The other player using the same bank inventory as [player], or `null` if there is none.
     *
     * Someone counts as using it from the moment their bank screen opens until the tidy-up the bank
     * queues when it closes has run, because that tidy-up moves items too.
     */
    fun otherUser(player: Player): Player? {
        val type = invTypes[invs.bank]
        val bank = player.invMap[type] ?: return null
        return players.firstOrNull { it !== player && it.invMap[type] === bank && it.usesBank() }
    }

    /** Sends the "in use" message and returns `true` if another player is using [access]'s bank. */
    fun blocked(access: ProtectedAccess): Boolean {
        val user = otherUser(access.player) ?: return false
        access.player.mes(inUse(user))
        return true
    }

    /** Opens the bank screen for [access] unless [blocked]. Returns whether it opened. */
    fun open(access: ProtectedAccess): Boolean {
        if (blocked(access)) {
            return false
        }
        access.ifOpenMainSidePair(main = interfaces.bank_main, side = interfaces.bank_side)
        return true
    }

    private fun Player.usesBank(): Boolean =
        ui.containsModal(interfaces.bank_main) ||
            ui.containsModal(deposit_interfaces.deposit_box) ||
            bank_queues.bank_compress in queueList ||
            bank_queues.bank_compress in weakQueueList

    private fun inUse(user: Player): String {
        val name = user.displayName.ifBlank { user.username }
        return "$name is using this bank right now."
    }
}
