package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.combat.PvnRules
import org.rsmod.api.config.refs.stats
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.specials.energy.SpecialEnergyCosts
import org.rsmod.content.other.food.EatRules
import org.rsmod.content.other.food.Eating
import org.rsmod.content.skills.herblore.scripts.DrinkRules
import org.rsmod.game.MapClock
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The helpful spirit and the supplies bag, the raid's own supplies, and the raid-wide rules: the
 * invocations that stop food (On a Diet), healing potions (Dehydration), drain prayer (Deadly
 * Prayers) or change special attack costs (Overly Draining), and what ghosts, swimmers and the
 * entombed cannot do.
 */
class ToaSuppliesScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val supplies: ToaSupplies,
    private val clock: MapClock,
    private val pvnRules: PvnRules,
    private val eatRules: EatRules,
    private val drinkRules: DrinkRules,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ToaSupplyNpcs.spirit) {
            val raid = raids.raidOf(player) ?: return@onOpNpc1
            if (!blockedAsGhost()) {
                supplies.claim(raid, player)
            }
        }
        for ((index, button) in ToaSupplyComponents.choose.withIndex()) {
            onIfModalButton(button) {
                raids.raidOf(player)?.let { supplies.choose(it, player, index) }
            }
        }

        onOpHeld1(ToaSupplyObjs.bag) { supplies.openBag(player) }
        onOpHeld2(ToaSupplyObjs.bag) { supplies.withdraw(player, slot = null, count = 1) }
        onOpHeld3(ToaSupplyObjs.bag) { supplies.withdraw(player, slot = null, count = BAG_SIZE) }
        onOpHeld4(ToaSupplyObjs.bag) { supplies.resupply(player) }
        onIfModalButton(ToaSupplyComponents.bag_items) {
            when (it.op) {
                IfButtonOp.Op1 -> supplies.withdraw(player, it.comsub, 1)
                IfButtonOp.Op2 -> supplies.withdraw(player, it.comsub, 5)
                IfButtonOp.Op3 -> supplies.withdraw(player, it.comsub, BAG_SIZE)
                IfButtonOp.Op4 -> supplies.drop(player, it.comsub)
                else -> {}
            }
        }
        for (chain in ToaSupplyObjs.chains) {
            for (dose in chain) {
                onOpHeldU(dose, ToaSupplyObjs.bag) { supplies.store(player, it.first) }
            }
        }

        consumable(ToaSupplyObjs.tears, food = false) { raid, _ -> supplies.tears(raid, player) }
        consumable(ToaSupplyObjs.salts, food = false) { _, _ -> supplies.salts(player) }
        consumable(ToaSupplyObjs.scarab, food = false) { _, _ -> supplies.scarab(player) }
        consumable(ToaSupplyObjs.adrenaline, food = false) { _, now ->
            supplies.adrenaline(player, now)
        }
        consumable(ToaSupplyObjs.silk, food = true) { _, _ -> supplies.silk(player) }
        // Honey locusts are eaten through the food module (20 hitpoints); the raid adds the rest.
        onEvent<Eating.Eaten> {
            if (obj.id != ToaSupplyObjs.locust.id) {
                return@onEvent
            }
            raids.raidOf(player)?.let { supplies.locust(it, player, healed) }
        }

        onEvent<GameLifecycle.StartCycle> { tick() }
        onEvent<ToaEvents.PathCompleted> { supplies.pathCompleted(raid, clock.cycle) }
        onEvent<ToaEvents.RaidEnded> { supplies.raidEnded(raid) }
        onEvent<ToaEvents.PlayerLeft> { supplies.strip(player) }
        onEvent<ToaEvents.RoomReset> { supplies.roomFailed(raid, room) }
        onEvent<ToaEvents.RoomEntered> {
            ToaSpecialCosts.overlyDraining[player] = raid.settings.isActive(Inv.OVERLY_DRAINING)
        }
        onPlayerLogin {
            if (raids.raidOf(player) == null) {
                supplies.strip(player)
            }
        }

        // Deadly Prayers: prayer drains by a fifth of the damage taken (wiki).
        onEvent<PlayerHitEvents.Impact> {
            if (damage <= 0) {
                return@onEvent
            }
            val raid = raids.raidOf(player) ?: return@onEvent
            if (raid.settings.isActive(Inv.DEADLY_PRAYERS)) {
                player.statSub(stats.prayer, damage * DEADLY_PRAYER_PERCENT / 100, 0)
            }
        }

        registerRules()
    }

    private fun registerRules() {
        ToaSpecialCosts.clock = { clock.cycle }
        if (!costsRegistered) {
            costsRegistered = true
            SpecialEnergyCosts.register { player, cost -> ToaSpecialCosts.cost(player, cost) }
        }
        pvnRules.register { player, _ ->
            val raid = raids.raidOf(player) ?: return@register null
            val member = raid.member(player) ?: return@register null
            when {
                member.ghost -> GHOST_MESSAGE
                member.room == ToaRoom.CrondisBoss &&
                    (rooms.runtime(raid, ToaRoom.CrondisBoss) as? ZebakRuntime)?.isSwimming(
                        player
                    ) == true -> "You can't attack while swimming."
                member.room == ToaRoom.WardensFirst &&
                    (rooms.runtime(raid, ToaRoom.WardensFirst) as? WardensRuntime)?.isStoned(
                        player
                    ) == true -> "You can't attack while entombed."
                else -> null
            }
        }
        eatRules.register { player, food ->
            val raid = raids.raidOf(player)
            when {
                raid == null -> if (food.id == ToaSupplyObjs.locust.id) OUTSIDE_MESSAGE else null
                raid.member(player)?.ghost == true -> GHOST_MESSAGE
                raid.settings.isActive(Inv.ON_A_DIET) ->
                    "The invocation On a Diet prevents you from eating."
                else -> null
            }
        }
        drinkRules.register { player, potion ->
            val raid = raids.raidOf(player)
            val supply =
                ToaSupplyObjs.chains.any { chain ->
                    chain.any { dose -> potion.doses.any { it.id == dose.id } }
                }
            when {
                raid == null -> if (supply) OUTSIDE_MESSAGE else null
                raid.member(player)?.ghost == true -> GHOST_MESSAGE
                raid.settings.isActive(Inv.DEHYDRATION) && DrinkRules.restoresHitpoints(potion) ->
                    "The invocation Dehydration prevents you from drinking that."
                else -> null
            }
        }
    }

    private fun tick() {
        val now = clock.cycle
        for (raid in raids.running()) {
            for (member in raid.active) {
                supplies.tick(member.player, now)
            }
        }
    }

    /**
     * One of the raid's own supplies, used from the inventory: one dose goes and [effect] applies.
     * [food] supplies (silk dressings, honey locusts) obey On a Diet.
     */
    private fun ScriptContext.consumable(
        chain: List<ObjType>,
        food: Boolean,
        effect: ProtectedAccess.(ToaRaid, Int) -> Unit,
    ) {
        for ((index, dose) in chain.withIndex()) {
            val next = chain.getOrNull(index + 1)
            onOpHeld1(dose) { use(it, dose, next, food, effect) }
        }
    }

    private fun ProtectedAccess.use(
        event: HeldObjEvents.Op1,
        dose: ObjType,
        next: ObjType?,
        food: Boolean,
        effect: ProtectedAccess.(ToaRaid, Int) -> Unit,
    ) {
        val raid = raids.raidOf(player)
        if (raid == null) {
            mes(OUTSIDE_MESSAGE)
            return
        }
        if (blockedAsGhost()) {
            return
        }
        if (food && raid.settings.isActive(Inv.ON_A_DIET)) {
            mes("The invocation On a Diet prevents you from eating.")
            return
        }
        if (!supplies.takeTurn(player, clock.cycle)) {
            return
        }
        val taken =
            if (next != null) {
                invReplaceSlot(event.inventory, event.slot, 1, next)
            } else {
                invDel(event.inventory, dose, 1, slot = event.slot)
            }
        if (taken.failure) {
            return
        }
        anim(ToaSupplySeqs.consume)
        effect(raid, clock.cycle)
    }

    private fun ProtectedAccess.blockedAsGhost(): Boolean {
        if (!raids.isGhost(player)) {
            return false
        }
        mes(GHOST_MESSAGE)
        return true
    }

    private companion object {
        const val BAG_SIZE = 28
        const val DEADLY_PRAYER_PERCENT = 20
        const val GHOST_MESSAGE = "A mysterious force prevents you from doing that."
        const val OUTSIDE_MESSAGE = "You can only use this within the Tombs of Amascut."

        /** The engine's cost rule is global; register it once however many servers start. */
        var costsRegistered = false
    }
}
