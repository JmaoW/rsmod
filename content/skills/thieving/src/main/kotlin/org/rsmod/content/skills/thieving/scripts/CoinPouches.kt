package org.rsmod.content.skills.thieving.scripts

import kotlin.math.min
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.content.skills.thieving.configs.CoinPouch
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.content.skills.thieving.configs.ThievingObjs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Opening the coin pouches that pickpocketing gives. "Open-all" (the first option) opens every
 * pouch of that kind in the inventory and "Open" (the second) opens one. A pouch is worth a random
 * number of coins in the range its target gives.
 */
class CoinPouches : PluginScript() {
    override fun ScriptContext.startup() {
        val pouches = PickpocketTargets.all.mapNotNull { it.pouch }.distinctBy { it.obj.id }
        for (pouch in pouches) {
            onOpHeld1(pouch.obj) { open(pouch, all = true) }
            onOpHeld2(pouch.obj) { open(pouch, all = false) }
        }
    }

    private fun ProtectedAccess.open(pouch: CoinPouch, all: Boolean) {
        val held = invTotal(inv, pouch.obj)
        val count = if (all) held else min(1, held)
        if (count == 0) {
            return
        }
        invDel(inv, pouch.obj, count)
        var coins = 0L
        repeat(count) { coins += random.of(pouch.coins) }
        val amount = coins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        invAdd(inv, ThievingObjs.coins, amount)
        mes(
            "You open ${if (count == 1) "the coin pouch" else "$count coin pouches"} and find $amount coins."
        )
        publish(PouchesOpened(player, count, amount))
    }

    /** Published after [count] coin pouches have been opened together for [coins] coins. */
    class PouchesOpened(val player: Player, val count: Int, val coins: Int) : UnboundEvent
}
