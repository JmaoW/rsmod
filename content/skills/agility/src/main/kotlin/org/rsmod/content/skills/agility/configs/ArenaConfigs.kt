package org.rsmod.content.skills.agility.configs

import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.currency.CurrencyReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType

object ArenaObjs : ObjReferences() {
    val ticket = find("agilityarena_ticket")

    /**
     * The Brimhaven voucher, which this cache does not name: its `agilityarena_ticket_new` is the
     * newer arena currency and shows as "Agility arena ticket" in the client.
     */
    val voucher = find("agilityarena_ticket_new")
    val pirate_hook = find("piratehook")
}

object ArenaLocs : LocReferences() {
    val ladder_down = find("agilityarena_ladderdown")
    val ladder_up = find("agilityarena_ladderup")
    val climbing_rope = find("agilityarena_climbingrope")
    val blade = find("agilityarena_trap_timedblade2")
    val plank_middle = find("agilityarena_plank_middle")
    val plank_broken = find("agilityarena_plank_broke1")
    val saw_blades = find("agilityarena_sawblades")
    val floor_spikes = find("agilityarena_floorspikes")
}

object ArenaNpcs : NpcReferences() {
    val jackie = find("agilityarena_tickettrader_3ops")
    val clerk = find("agilityarena_clerk")
}

object ArenaSeqs : SeqReferences() {
    val saw_twist = find("agilityarena_sawblades_twist")
    val spikes_activate = find("agilityarena_floorspikes_activate")
    val dive = find("agilityarena_dive_player")
    val land = find("agilityarena_land_player")
    val spiked = find("agilityarena_player_spiked")
    val climb = find("human_reachforladder")
}

object ArenaCurrencies : CurrencyReferences() {
    /** Needs a line in `.data/symbols/currency.sym`. */
    val vouchers = find("brimhaven_voucher")
}

object ArenaInvs : InvReferences() {
    /** A clothes shop in the cache that nothing on the isle uses, given to Pirate Jackie. */
    val shop = find("fancyclothesstore")
}

object ArenaTimers : TimerReferences() {
    /** Ticks on every player and moves the lit ticket dispenser on. Needs a line in `timer.sym`. */
    val tick = find("agility_arena_tick")
}

/**
 * Where the arena is entered on the isle: the bamboo hut from Brimhaven ([ArenaHut]) on the beach,
 * with Cap'n Izzy, the ladder down and Pirate Jackie inside. Every tile is placed from
 * [ArenaHut.DOOR], so moving the hut moves them with it.
 */
object ArenaEntrance {
    val ladder = ArenaHut.DOOR.translate(0, 5)

    /** Where a player who climbs up out of the arena stands. */
    val exit = ArenaHut.DOOR.translate(0, 4)
    val clerk = ArenaHut.DOOR.translate(-1, 3)
    val jackie = ArenaHut.DOOR.translate(1, 3)

    /** The coins Cap'n Izzy asks for each time in. */
    const val FEE = 200
}

/**
 * How an obstacle is passed: [level] to try it, [xp] for passing it, and the chance of passing (out
 * of 256 at Agility level 1 and 99, like the other obstacles). [sure] is the level from which it
 * cannot be failed. The Wiki gives the experience, levels and where nobody fails; the chances are
 * not given and are guesses by tier.
 */
class ArenaStat(
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val sure: Int = 99,
    val failMessage: String = "You slip and fall to the pit below.",
)

object ArenaStats {
    private const val EASY_LOW = 170
    private const val EASY_HIGH = 252
    private const val MEDIUM_LOW = 140
    private const val MEDIUM_HIGH = 250
    private const val HARD_LOW = 110
    private const val HARD_HIGH = 245

    fun of(kind: ArenaKind): ArenaStat =
        when (kind) {
            ArenaKind.Ledge -> ArenaStat(1, 16.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Log -> ArenaStat(1, 12.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Rope -> ArenaStat(1, 10.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Bars -> ArenaStat(1, 14.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Hands -> ArenaStat(20, 22.0, MEDIUM_LOW, MEDIUM_HIGH)
            ArenaKind.Plank -> ArenaStat(1, 6.0, 256, 256)
            ArenaKind.Swing -> ArenaStat(1, 20.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Pillar -> ArenaStat(1, 18.0, EASY_LOW, EASY_HIGH)
            ArenaKind.LowWall -> ArenaStat(1, 8.0, EASY_LOW, EASY_HIGH)
            ArenaKind.Pads ->
                ArenaStat(20, 26.0, MEDIUM_LOW, MEDIUM_HIGH, 50, "You were hit by the spikes!")
            ArenaKind.Spikes ->
                ArenaStat(20, 24.0, MEDIUM_LOW, MEDIUM_HIGH, 50, "You were hit by the spikes!")
            ArenaKind.Saw ->
                ArenaStat(40, 28.0, HARD_LOW, HARD_HIGH, 99, "You were hit by the spinning blades!")
            ArenaKind.Darts ->
                ArenaStat(
                    40,
                    30.0,
                    HARD_LOW,
                    HARD_HIGH,
                    99,
                    "You were hit by some darts, something on them makes you feel dizzy!",
                )
            ArenaKind.Blade -> ArenaStat(1, 0.0, 256, 256)
        }
}

/**
 * Pirate Jackie the Fruit shop, which sells for vouchers: the pirate hook, at 800 (the Wiki price,
 * set as the hook value). The Wiki toadflax, snapdragon, graceful recolour and amylase packs are
 * left out: the shop prices by an item value and these are used elsewhere. Needs `packCache`.
 */
internal object ArenaInvEdits : InvEditor() {
    init {
        edit(ArenaInvs.shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(ArenaObjs.pirate_hook, count = 100, restockCycles = 1)
        }
    }
}

internal object ArenaObjEdits : ObjEditor() {
    init {
        edit(ArenaObjs.pirate_hook) { cost = 800 }
    }
}

internal object ArenaNpcEdits : NpcEditor() {
    init {
        edit(ArenaNpcs.jackie) {
            wanderRange = 1
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 0
            param[ShopParams.shop_change_percentage] = 0
        }
        edit(ArenaNpcs.clerk) { wanderRange = 1 }
    }
}
