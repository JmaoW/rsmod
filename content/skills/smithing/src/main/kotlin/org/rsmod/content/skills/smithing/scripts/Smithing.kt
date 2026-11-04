package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.ProductionBatch
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.configs.SmithingComponents
import org.rsmod.content.skills.smithing.configs.SmithingInterfaces
import org.rsmod.content.skills.smithing.configs.SmithingMetal
import org.rsmod.content.skills.smithing.configs.SmithingObjs
import org.rsmod.content.skills.smithing.configs.SmithingProduct
import org.rsmod.content.skills.smithing.configs.SmithingProducts
import org.rsmod.content.skills.smithing.configs.SmithingSeqs
import org.rsmod.content.skills.smithing.configs.SmithingVarBits
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - cannonballs, the Superheat Item spell, and the Blast Furnace
// - Lovakite and Shayzien items, dragon and other special items, and the Giants' Foundry
// - the smithing skillcape, the Smiths' outfit, and the Goldsmith gauntlets
//
// An anvil is any loc named "Anvil" that has a "Smith" op. Clicking it (or using a hammer or a bar
// on it) opens the anvil interface for one metal, which is picked by the `smithing_bar_type`
// varbit. The client draws everything else itself: the items, which are greyed out for a missing
// level or too few bars, and the names and bar costs. Once an item is clicked the interface closes
// and the player smiths it until the bars run out, continuing through the otherwise unused op4 of
// the anvil.
class Smithing
@Inject
constructor(private val locTypes: LocTypeList, private val xpMods: XpModifiers) : PluginScript() {
    /** The anvil, and the metal shown, for the player's open anvil interface. */
    private val sessions = WeakHashMap<Player, Session>()

    /** What each player is currently smithing, so the loop knows what comes next. */
    private val jobs = WeakHashMap<Player, SmithJob>()

    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            if (!isAnvil(type)) {
                continue
            }
            if (!onOpLocNamed(type, SMITH_OP) { open(it, metal = null) }) {
                continue
            }
            onOpLocU(type) { useOn(it.loc, it.objType) }
            onOpLoc4(type) { smithNext(it.loc) }
        }

        for ((slot, component) in SmithingComponents.slots.withIndex()) {
            onIfModalButton(component) { select(slot) }
        }
    }

    private fun ProtectedAccess.useOn(loc: BoundLocInfo, obj: ObjType) {
        when {
            obj.id == SmithingObjs.hammer.id -> open(loc, metal = null)
            else -> {
                val metal = SmithingMetal.forBar(obj)
                if (metal == null) {
                    mes("Nothing interesting happens.")
                    return
                }
                open(loc, metal)
            }
        }
    }

    /**
     * Opens the interface for [metal], or for the best metal that the player has bars of if [metal]
     * is `null`.
     */
    private fun ProtectedAccess.open(loc: BoundLocInfo, metal: SmithingMetal?) {
        if (SmithingObjs.hammer !in inv) {
            mes("You need a hammer to work the metal with.")
            return
        }
        val shown = metal ?: bestMetal()
        if (shown == null) {
            mes("You do not have any bars to work with.")
            return
        }
        sessions[player] = Session(loc, shown)
        player.barType = shown.barType
        ifOpenMainModal(SmithingInterfaces.anvil)
    }

    private fun ProtectedAccess.bestMetal(): SmithingMetal? =
        SmithingMetal.entries.lastOrNull { it.bar in inv }

    private fun ProtectedAccess.select(slot: Int) {
        val session = sessions.remove(player) ?: return
        val product = SmithingProducts.find(session.metal, slot) ?: return
        ifClose()
        if (!canSmith(product)) {
            return
        }
        jobs[player] = SmithJob(product)
        actionDelay = mapClock + SMITH_DELAY
        skillAnimDelay = mapClock + ANIM_INTERVAL
        anim(SmithingSeqs.smith)
        opLoc4(session.loc)
    }

    private fun ProtectedAccess.smithNext(loc: BoundLocInfo) {
        val job = jobs[player] ?: return
        if (!hasHammer() || !hasBars(job.product)) {
            stop()
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_INTERVAL
            anim(SmithingSeqs.smith)
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + SMITH_DELAY
        } else if (actionDelay == mapClock) {
            // A [ProductionBatch] rule smiths as many as the bars allow at once.
            val possible = invTotal(inv, job.product.metal.bar) / job.product.bars
            val made =
                ProductionBatch.make(player, possible) { first ->
                    (first || hasBars(job.product)) && smithOne(job.product)
                }
            if (made == 0) {
                stop()
                return
            }
            if (!hasBars(job.product)) {
                stop()
                return
            }
        }

        opLoc4(loc)
    }

    private fun ProtectedAccess.smithOne(product: SmithingProduct): Boolean {
        val metal = product.metal
        if (!invDel(inv, metal.bar, product.bars).success) {
            return false
        }
        if (!invAdd(inv, product.obj, product.quantity).success) {
            return false
        }
        spam("You hammer the ${metal.noun} and make ${describe(product)}.")
        statAdvance(stats.smithing, product.xp * xpMods.get(player, stats.smithing))
        publish(ItemSmithed(player, product.obj, metal.bar, product.bars, product.quantity))
        return true
    }

    /** Checks hammer, level, and bars. Sends a message for the first problem. */
    private fun ProtectedAccess.canSmith(product: SmithingProduct): Boolean {
        if (!hasHammer()) {
            mes("You need a hammer to work the metal with.")
            return false
        }
        if (player.smithingLvl < product.level) {
            mes("You need a Smithing level of ${product.level} to make this.")
            return false
        }
        if (!hasBars(product)) {
            val name = product.metal.noun
            mes("You do not have enough $name bars to make this.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.hasHammer(): Boolean = SmithingObjs.hammer in inv

    private fun ProtectedAccess.hasBars(product: SmithingProduct): Boolean =
        invTotal(inv, product.metal.bar) >= product.bars

    private fun ProtectedAccess.stop() {
        jobs.remove(player)
        resetAnim()
    }

    private class Session(val loc: BoundLocInfo, val metal: SmithingMetal)

    private class SmithJob(val product: SmithingProduct)

    /**
     * Published once an item has been smithed, after the bars were used. [quantity] is how many of
     * [obj] one smith gave (arrowtips and nails come in stacks), and [bar] is what it was made
     * from.
     */
    class ItemSmithed(
        val player: Player,
        val obj: ObjType,
        val bar: ObjType,
        val bars: Int,
        val quantity: Int,
    ) : UnboundEvent

    companion object {
        private const val SMITH_OP = "Smith"

        /**
         * Ticks to wait before each item is smithed, minus one. The loop spends one extra tick
         * between items, so this gives a five tick interval.
         */
        private const val SMITH_DELAY = 4
        private const val ANIM_INTERVAL = 4

        fun isAnvil(type: UnpackedLocType): Boolean =
            type.name.equals("Anvil", ignoreCase = true) && SMITH_OP in type.op

        /** The words after `make` in the chat message for a smithed [product]. */
        internal fun describe(product: SmithingProduct): String {
            val label = product.label.lowercase()
            return when {
                product.quantity > 1 -> "${product.quantity} $label"
                product.label in PLURAL_LABELS -> "some $label"
                label.first() in "aeiou" -> "an $label"
                else -> "a $label"
            }
        }

        /** Labels that are plural (or uncountable) even when one is made. */
        private val PLURAL_LABELS = setOf("Plate legs", "Studs", "Limbs", "Bronze wire")
    }
}

private var Player.barType by intVarBit(SmithingVarBits.bar_type)
