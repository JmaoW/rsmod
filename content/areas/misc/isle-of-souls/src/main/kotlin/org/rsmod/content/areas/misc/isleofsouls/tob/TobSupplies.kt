package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The supply chests after the Pestilent Bloat and Sotetseg, and their shop (interface 405).
 *
 * Wiki ("Chest (Theatre of Blood)"): the vampyres grant points by how the player did in the two
 * rooms before the chest; the points carry over to the second chest; one stamina potion per chest;
 * a player who died in both rooms gets only an onion. Cache: enum 1952 is the stock by slot and
 * 1953 the prices, script 2346 (the interface's own onload) draws it with "Points Available" from
 * `tob_midwaychest_points` and the ops Value, Buy-1, Buy-(last), Buy-All, Buy-X and Examine.
 *
 * The grading (TSPS's guess, the wiki gives no formula): 6-9, 8-11 or 10-13 points for doing under
 * three quarters, about, or over five quarters of the party's average damage in the two rooms, a
 * grade lower for each death in them.
 */
class TobSuppliesScript
@Inject
constructor(
    private val raids: TobRaids,
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<TobEvents.RoomCompleted> {
            val rooms = CHEST_ROOMS[room] ?: return@onEvent
            grant(raid, rooms)
            val chest = room.supplyChest ?: return@onEvent
            locRepo.add(
                raid.regionOf(chest.real),
                locTypes[tob_locs.supply_open],
                Int.MAX_VALUE,
                chest.angle,
                LocShape.CentrepieceStraight,
            )
            for (member in raid.active) {
                if (member.room == room) {
                    member.player.mes(
                        "The Vampyres are impressed by your prowess. <col=ef1020>Check the chest" +
                            "</col> to see how many points they've granted you."
                    )
                }
            }
        }
        onOpLoc1(tob_locs.supply_closed) {
            mes("You can't open the chest until the challenge is complete.")
        }
        onOpLoc2(tob_locs.supply_open) { search() }
        onIfModalButton(tob_components.supplies_items) { buy(it.comsub, it.op) }
    }

    private fun grant(raid: TobRaid, rooms: List<TobRoom>) {
        val damage =
            raid.active.associateWith { member -> rooms.sumOf { member.damageIn[it] ?: 0 } }
        val average = damage.values.sum().toDouble() / damage.size.coerceAtLeast(1)
        for (member in raid.active) {
            member.staminaBought = false
            member.onionOnly = rooms.all { (member.deathsIn[it] ?: 0) > 0 }
            if (member.onionOnly) {
                continue
            }
            val share = if (average > 0) damage.getValue(member) / average else 1.0
            var grade =
                when {
                    share >= 1.25 -> 2
                    share <= 0.75 -> 0
                    else -> 1
                }
            grade -= rooms.sumOf { member.deathsIn[it] ?: 0 }
            val band = BANDS[grade.coerceAtLeast(0)]
            member.supplyPoints += band.first + random.of(band.last - band.first + 1)
            sync(member)
        }
    }

    private fun sync(member: TobMember) {
        VarPlayerIntMapSetter.set(member.player, tob_varbits.supply_points, member.supplyPoints)
        VarPlayerIntMapSetter.set(
            member.player,
            tob_varbits.supply_stamina,
            if (member.staminaBought) 1 else 0,
        )
    }

    private fun member(player: Player): TobMember? = raids.raidOf(player)?.member(player)

    private fun ProtectedAccess.search() {
        val member = member(player) ?: return
        if (member.onionOnly) {
            member.onionOnly = false
            if (player.inv.freeSpace() > 0) {
                player.invAdd(player.inv, objTypes[tob_objs.onion], 1)
                mes("The vampyres were not impressed. They have left you an onion.")
            }
            return
        }
        sync(member)
        player.ifOpenMainModal(tob_interfaces.supplies, eventBus)
        player.ifSetEvents(
            tob_components.supplies_items,
            STOCK.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )
    }

    private suspend fun ProtectedAccess.buy(slot: Int, op: IfButtonOp) {
        val member = member(player) ?: return
        val (name, cost) = STOCK.getOrNull(slot) ?: return
        val type = objTypes.values.firstOrNull { it.internalName == name } ?: return
        when (op) {
            IfButtonOp.Op1 -> {
                mes("${type.name} costs $cost point${if (cost == 1) "" else "s"}.")
                return
            }
            IfButtonOp.Op10 -> {
                mes(type.desc)
                return
            }
            else -> Unit
        }
        val wanted =
            when (op) {
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> member.lastBuy.coerceAtLeast(1)
                IfButtonOp.Op4 -> Int.MAX_VALUE
                IfButtonOp.Op5 -> countDialog().also { member.lastBuy = it }
                else -> return
            }
        var amount = wanted
        if (name == STAMINA) {
            if (member.staminaBought) {
                mes("This item is out of stock.")
                return
            }
            amount = 1
        }
        val affordable = member.supplyPoints / cost
        if (affordable <= 0) {
            mes("You don't have enough points to buy ${type.name}.")
            return
        }
        amount = minOf(amount, affordable, player.inv.freeSpace())
        if (amount <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        if (player.invAdd(player.inv, type, amount).success) {
            member.supplyPoints -= amount * cost
            if (name == STAMINA) {
                member.staminaBought = true
            }
            sync(member)
        }
    }

    private companion object {
        val CHEST_ROOMS =
            mapOf(
                TobRoom.Bloat to listOf(TobRoom.Maiden, TobRoom.Bloat),
                TobRoom.Sotetseg to listOf(TobRoom.Nylocas, TobRoom.Sotetseg),
            )

        val BANDS = listOf(6..9, 8..11, 10..13)

        const val STAMINA = "4dosestamina"

        /** Enum 1952 by slot, with enum 1953's prices. */
        val STOCK =
            listOf(
                STAMINA to 1,
                "4doseprayerrestore" to 2,
                "4dosepotionofsaradomin" to 3,
                "4dose2restore" to 3,
                "potato_mushroom+onion" to 1,
                "shark" to 1,
                "seaturtle" to 2,
                "mantaray" to 2,
            )
    }
}
