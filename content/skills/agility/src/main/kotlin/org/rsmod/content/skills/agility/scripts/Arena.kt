package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ObjCurrencyShopOperations
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.ArenaCurrencies
import org.rsmod.content.skills.agility.configs.ArenaDispenser
import org.rsmod.content.skills.agility.configs.ArenaEntrance
import org.rsmod.content.skills.agility.configs.ArenaHut
import org.rsmod.content.skills.agility.configs.ArenaInvs
import org.rsmod.content.skills.agility.configs.ArenaLayout
import org.rsmod.content.skills.agility.configs.ArenaLocs
import org.rsmod.content.skills.agility.configs.ArenaNpcs
import org.rsmod.content.skills.agility.configs.ArenaObjs
import org.rsmod.content.skills.agility.configs.ArenaSeqs
import org.rsmod.content.skills.agility.configs.ArenaTimers
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Pirate Jackie shop, which takes vouchers instead of coins. */
class VoucherShopOperations
@Inject
constructor(objTypes: ObjTypeList, restockProcess: ShopRestockProcess, marketPrices: MarketPrices) :
    ObjCurrencyShopOperations(objTypes, restockProcess, marketPrices, ArenaObjs.voucher, "voucher")

// TODO:
// - the Karamja glove bonuses and the diary double tickets
// - the pillar-tag announcement and the logout and teleport-out re-entry delays
//
// The Brimhaven Agility Arena, which is the game's own map: 25 pillars in a 5 by 5 grid on level
// 3, joined by obstacles and traps (see [ArenaObstacles] and [ArenaTraps]), over a ground floor
// of pits with a rope each (see [ArenaLayout]). The way in is a ladder on the isle, past Cap'n
// Izzy, who takes a fee; the way out is the ladder on the last pillar.
//
// One of the 24 ticket dispensers is marked with a flashing arrow for a minute at a time. Tagging
// the marked one gives a ticket, a voucher and 30 Agility experience for every ten levels (up to
// 300). Tickets are traded for experience with Pirate Jackie, and vouchers for the pirate hook in
// her shop. This class also ticks the arena once a game cycle for the other arena scripts.
class Arena
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val shops: Shops,
    private val operationMap: ShopOperationMap,
    private val voucherOperations: VoucherShopOperations,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    /** The dispenser that is marked (`-1` before the first one), and when its minute is up. */
    var active: Int = -1
        private set

    /** Counts up each time a dispenser is marked. A player who tags each count keeps a streak. */
    var activation: Int = 0
        private set

    private var litUntil: Int = 0
    private var lastCycle: Int = -1

    private val lastTag = WeakHashMap<Player, Int>()
    private val streaks = WeakHashMap<Player, Int>()
    private val arrows = WeakHashMap<Player, Int>()
    private val paid = WeakHashMap<Player, Boolean>()

    private val dispenserAt: Map<Long, ArenaDispenser> by lazy {
        ArenaLayout.dispensers.associateBy { key(it.x, it.z) }
    }

    override fun ScriptContext.startup() {
        onGameStartup {
            registerCurrency()
            placeEntrance()
        }
        onPlayerLogin { player.softTimer(ArenaTimers.tick, 1) }
        onPlayerSoftTimer(ArenaTimers.tick) { tick(player) }

        for (name in ArenaLayout.dispensers.map { it.loc }.toSet()) {
            val type = locTypes.values.first { it.internalName == name }
            onOpLoc1(type) { tag(it.loc) }
        }
        onOpLoc1(ArenaLocs.ladder_down) { enter() }
        onOpLoc1(ArenaLocs.ladder_up) { leave() }
        onOpLoc1(ArenaLocs.climbing_rope) { climb(it.loc) }
        onOpNpc1(ArenaNpcs.clerk) { talkToClerk(it.npc) }
        onOpNpc1(ArenaNpcs.jackie) { talkToJackie(it.npc) }
        onOpNpc3(ArenaNpcs.jackie) { player.openShop(it.npc) }
    }

    fun registerCurrency() {
        operationMap.register(ArenaCurrencies.vouchers, voucherOperations)
    }

    /**
     * The hut, Cap'n Izzy, the ladder down to the arena and Pirate Jackie. The hut's walls and
     * furniture are placed first, after the beach bush that stood where it is built is cleared.
     */
    fun placeEntrance() {
        placeHut()
        val ladder = locTypes.values.first { it.internalName == "agilityarena_ladderdown" }
        locRepo.add(
            ArenaEntrance.ladder,
            ladder,
            Int.MAX_VALUE,
            LocAngle[LADDER_ANGLE],
            LocShape[10],
        )
        npcRepo.add(Npc(npcTypes[ArenaNpcs.clerk], ArenaEntrance.clerk), Int.MAX_VALUE)
        npcRepo.add(Npc(npcTypes[ArenaNpcs.jackie], ArenaEntrance.jackie), Int.MAX_VALUE)
    }

    private fun placeHut() {
        for (loc in locRepo.findAll(ArenaHut.BUSH_AT)) {
            if (locTypes[loc].internalName == ArenaHut.BUSH) {
                locRepo.del(loc, Int.MAX_VALUE)
            }
        }
        val byName = locTypes.values.associateBy { it.internalName }
        for (piece in ArenaHut.pieces) {
            val type = byName.getValue(piece.loc)
            locRepo.add(
                piece.at(ArenaHut.DOOR),
                type,
                Int.MAX_VALUE,
                LocAngle[piece.angle],
                LocShape[piece.shape],
            )
        }
    }

    /**
     * Runs for a player once a game cycle. The first player of a cycle also runs the arena's own
     * cycle: a new dispenser when the minute is up, then [Cycle] for the other arena scripts.
     */
    fun tick(player: Player) {
        val now = player.currentMapClock
        if (now != lastCycle) {
            lastCycle = now
            if (active == -1 || now >= litUntil) {
                mark(now)
            }
            eventBus.publish(Cycle(now))
        }
        if (inArena(player.coords)) {
            if (arrows[player] != activation) {
                arrows[player] = activation
                val dispenser = ArenaLayout.dispensers[active]
                HintArrows.tile(player, CoordGrid(dispenser.x, dispenser.z, 3), ARROW_HEIGHT)
            }
            eventBus.publish(PlayerTick(player, now))
        } else if (arrows.remove(player) != null) {
            HintArrows.reset(player)
        }
    }

    /** Marks a dispenser other than the last, for [LIT_TICKS]. */
    fun mark(now: Int) {
        val count = ArenaLayout.dispensers.size
        var next = random.of(count)
        if (next == active) {
            next = (next + 1) % count
        }
        active = next
        activation++
        litUntil = now + LIT_TICKS
    }

    private suspend fun ProtectedAccess.tag(loc: BoundLocInfo) {
        val dispenser = dispenserAt[key(loc.coords.x, loc.coords.z)] ?: return
        if (dispenser.index != active) {
            mes("You can only get a ticket when the flashing arrow is above the pillar.")
            return
        }
        val previous = lastTag[player]
        if (previous == activation) {
            mes("You can only get one ticket at a time, wait till the arrow moves again.")
            return
        }
        val streak = if (previous == activation - 1) (streaks[player] ?: 0) + 1 else 1
        lastTag[player] = activation
        streaks[player] = streak

        invAddOrDrop(objRepo, ArenaObjs.ticket, 1)
        invAddOrDrop(objRepo, ArenaObjs.voucher, 1)
        val xp = minOf(30 * (player.agilityLvl / 10), MAX_TAG_XP)
        if (xp > 0) {
            statAdvance(stats.agility, xp * xpMods.get(player, stats.agility))
        }
        mes("You have received an Agility Arena Ticket and Brimhaven Voucher!")
        publish(Tagged(player, streak, dispenser.index))
    }

    /** Down the ladder on the isle, once Cap'n Izzy has been paid. */
    private suspend fun ProtectedAccess.enter() {
        if (ArenaEntrance.FEE > 0 && paid[player] != true) {
            mes("You need to pay Cap'n Izzy No-Beard before you go down there.")
            return
        }
        paid.remove(player)
        anim(ArenaSeqs.climb)
        delay(2)
        resetAnim()
        telejump(ArenaLayout.entry.coords())
        mes("You climb down the ladder into the arena.")
    }

    private suspend fun ProtectedAccess.leave() {
        anim(ArenaSeqs.climb)
        delay(2)
        resetAnim()
        telejump(ArenaEntrance.exit)
        HintArrows.reset(player)
        arrows.remove(player)
    }

    /** Up a rope from a pit to the platform above it. */
    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo) {
        val pit =
            ArenaLayout.pits.firstOrNull { it.rope.x == loc.coords.x && it.rope.z == loc.coords.z }
                ?: return
        anim(ArenaSeqs.climb)
        delay(2)
        resetAnim()
        telejump(CoordGrid(pit.rope.x, pit.rope.z, 3))
    }

    private suspend fun ProtectedAccess.talkToClerk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Ahoy there! Welcome to the Brimhaven Agility Arena!")
            val choice =
                choice2("Pay ${ArenaEntrance.FEE} coins to go in.", 1, "Not right now, thanks.", 2)
            if (choice != 1) {
                chatPlayer(neutral, "Not right now, thanks.")
                return@startDialogue
            }
            chatPlayer(neutral, "I'd like to go in. Here's the ${ArenaEntrance.FEE} coins.")
            if (invTotal(inv, objs.coins) < ArenaEntrance.FEE) {
                chatNpc(sad, "Ye haven't got ${ArenaEntrance.FEE} coins, landlubber.")
                return@startDialogue
            }
            invDel(inv, objs.coins, ArenaEntrance.FEE)
            paid[player] = true
            mes("You give Cap'n Izzy the ${ArenaEntrance.FEE} coin entrance fee.")
            chatNpc(happy, "Right ye are. Take the ladder down, and watch yer step.")
        }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(
            this,
            npc,
            "Pirate Jackie the Fruit's Shop",
            ArenaInvs.shop,
            currency = ArenaCurrencies.vouchers,
        )
    }

    private suspend fun ProtectedAccess.talkToJackie(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Ahoy there! Been tagging the dispensers in the arena?")
            val choice =
                choice3(
                    "Exchange my tickets for experience.",
                    1,
                    "What do you sell?",
                    2,
                    "Nothing, thanks.",
                    3,
                )
            when (choice) {
                1 -> {
                    chatPlayer(neutral, "Exchange my tickets for experience.")
                    exchange()
                }
                2 -> {
                    chatPlayer(quiz, "What do you sell?")
                    chatNpc(
                        neutral,
                        "Fine things, for the vouchers the dispensers give out. " + "Have a look.",
                    )
                    player.openShop(npc)
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.exchange() {
        val count = invTotal(inv, ArenaObjs.ticket)
        if (count == 0) {
            mes("You have no agility arena tickets to exchange.")
            return
        }
        invDel(inv, ArenaObjs.ticket, count)
        val xp = TICKET_XP * count
        statAdvance(stats.agility, xp * xpMods.get(player, stats.agility))
        mes("You exchange $count agility arena tickets for experience.")
        publish(Exchanged(player, count))
    }

    private fun Tile.coords(): CoordGrid = CoordGrid(x, z, level)

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    /** Published when a marked dispenser is tagged; [streak] counts the dispensers in a row. */
    class Tagged(val player: Player, val streak: Int, val island: Int) : UnboundEvent

    /** Published when a player trades [tickets] to Pirate Jackie. */
    class Exchanged(val player: Player, val tickets: Int) : UnboundEvent

    /** Published once a game cycle, whoever is in the arena, for the arena's other scripts. */
    class Cycle(val now: Int) : UnboundEvent

    /** Published once a game cycle for each player in the arena. */
    class PlayerTick(val player: Player, val now: Int) : UnboundEvent

    companion object {
        /** How long a dispenser stays marked, a minute. */
        const val LIT_TICKS = 100

        /** Experience for tagging: 30 for each ten levels, to this. */
        const val MAX_TAG_XP = 300

        /** Experience for each ticket traded in. */
        const val TICKET_XP = 345.0

        /** The height of the flashing arrow above a dispenser. */
        const val ARROW_HEIGHT = 120

        /** The ladder is open on its east side; turned a quarter it is open to the south. */
        private const val LADDER_ANGLE = 1

        private const val MIN_X = 2752
        private const val MAX_X = 2812
        private const val MIN_Z = 9536
        private const val MAX_Z = 9598

        /** Whether [coords] is inside the arena, on a platform or down in a pit. */
        fun inArena(coords: CoordGrid): Boolean =
            coords.x in MIN_X..MAX_X && coords.z in MIN_Z..MAX_Z
    }
}
