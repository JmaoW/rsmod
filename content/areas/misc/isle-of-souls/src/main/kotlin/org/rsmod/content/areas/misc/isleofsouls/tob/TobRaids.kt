package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Runs the raids: one [TobRaid] per party that has gone in, each in its own copy of the theatre
 * ([TobLayout]). The rooms come in a fixed order; the leader starts each fight at its barrier, the
 * way on opens when the room is won, and everyone who died in the fight comes back then. If the
 * whole party is dead at once the raid fails and everyone is put outside in Ver Sinhaza (items are
 * kept, as for every death on the isle).
 *
 * What happens in a room is its own content's ([TobRoomRuntime]), which calls [completeRoom] and
 * [raidWon] when a fight is won.
 */
@Singleton
class TobRaids
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val collision: CollisionFlagMap,
    private val zoneActivity: ZonePlayerActivityBitSet,
    private val clock: MapClock,
    private val players: PlayerList,
    private val eventBus: EventBus,
    private val parties: TobParties,
    private val random: GameRandom,
    private val hud: TobHud,
    private val objTypes: ObjTypeList,
) {
    private val raids = LinkedHashMap<Int, TobRaid>()
    private var nextId = 1

    /** Players who died in a raid, and where the death's respawn puts them. */
    private val respawning = HashMap<Int, CoordGrid>()

    /** Raids where everyone may be dead, checked a few ticks after a death. */
    private val wipeChecks = HashMap<TobRaid, Int>()

    private val widened = HashSet<Player>()

    var pick: (Int) -> Int = { random.of(it) }

    /** Ticks between the screen going red and the move. Tests set 0 to move at once. */
    var fadeTicks: Int = FADE_TICKS

    private class FadeMove(val at: Int, val player: Player, val text: String, val move: () -> Unit)

    private val fadeMoves = ArrayList<FadeMove>()

    fun running(): Collection<TobRaid> = raids.values.filter { !it.ended }

    fun raidOf(player: Player): TobRaid? =
        raids.values.firstOrNull { raid ->
            !raid.ended && raid.contains(player.coords) && raid.member(player)?.left == false
        }

    /** The raid [player] belongs to, wherever they stand. */
    fun memberRaid(player: Player): TobRaid? =
        raids.values.firstOrNull { !it.ended && it.member(player)?.left == false }

    fun isJailed(player: Player): Boolean = raidOf(player)?.member(player)?.jailed == true

    /**
     * The screen goes red, [move] runs [fadeTicks] later and the screen clears with [text] (the
     * theatre's own fade, on its HUD).
     */
    fun fadeMove(player: Player, text: String = "", move: () -> Unit) {
        if (fadeTicks <= 0 || !hud.isOpen(player)) {
            move()
            return
        }
        hud.fadeRed(player)
        player.abortRoute()
        player.delay = clock.cycle + fadeTicks
        fadeMoves += FadeMove(clock.cycle + fadeTicks, player, text, move)
    }

    private fun tickFades() {
        if (fadeMoves.isEmpty()) {
            return
        }
        val due = fadeMoves.filter { it.at <= clock.cycle }
        fadeMoves.removeAll(due.toSet())
        for (pending in due) {
            if (pending.player.isSlotAssigned) {
                pending.move()
                hud.fade(pending.player, FADE_CLEAR, pending.text)
            }
        }
    }

    // Going in
    // ---------------------------------------------------------------------------------------

    /** The leader takes the party in; the others follow once it has started. */
    fun enter(player: Player): TobEntry {
        sweep()
        val party = parties.currentParty(player) ?: return TobEntry.NoParty
        if (raidOf(player) != null) {
            return TobEntry.AlreadyInside
        }
        if (player.vars[tob_varbits.should_have_loot] != 0) {
            return TobEntry.RewardWaiting
        }
        var raid = party.raid
        if (raid != null && raid.ended) {
            party.raid = null
            raid = null
        }
        if (raid == null) {
            if (!party.isLeader(player)) {
                return TobEntry.LeaderFirst
            }
            raid = start(party) ?: return TobEntry.NoSpace
            for (other in party.members) {
                if (other !== player) {
                    other.mes(
                        "${player.displayName} has entered the Theatre of Blood (Normal Mode). " +
                            "Step inside to join them..."
                    )
                }
            }
        }
        val member = raid.member(player)
        if (member == null || member.left) {
            return TobEntry.NotInRaid
        }
        player.mes("You enter the Theatre of Blood (Normal Mode)...")
        hud.raid(raid, player)
        fadeMove(player, "The Theatre awaits...") { place(raid, member, raid.room) }
        hud.namesForAll(raid)
        return TobEntry.Entered
    }

    private fun start(party: TobParty): TobRaid? {
        val region = regionRepo.add(TobLayout.template()) ?: return null
        val raid = TobRaid(id = nextId++, region = region, startedCycle = clock.cycle)
        party.members.forEachIndexed { slot, player -> raid.members += TobMember(player, slot) }
        party.raid = raid
        parties.unlist(party)
        raids[raid.id] = raid
        // Nobody stands in the region until the first player is placed; any region made in
        // between would reclaim it.
        zoneActivity.flag(region.southWestZone)
        return raid
    }

    /** Puts [member] at the arrival of [room]. `false` if no tile there can be stood on. */
    fun place(raid: TobRaid, member: TobMember, room: TobRoom): Boolean {
        val dest = arrival(raid, room) ?: return false
        move(member.player, dest)
        val changed = member.room != room
        member.room = room
        member.inFight = false
        if (changed) {
            eventBus.publish(TobEvents.RoomEntered(raid, room, member.player))
        }
        hud.refreshAll(raid)
        return true
    }

    private fun move(player: Player, dest: CoordGrid) {
        PathingEntityCommon.telejump(player, collision, dest)
        zoneActivity.flag(ZoneKey.from(dest))
    }

    private fun arrival(raid: TobRaid, room: TobRoom): CoordGrid? {
        repeat(ARRIVAL_ATTEMPTS) {
            val dx = if (room.spreadX > 0) pick(room.spreadX + 1) else 0
            val dz = if (room.spreadZ > 0) pick(room.spreadZ + 1) else 0
            val tile = raid.regionOf(room.arrival.translate(dx, dz))
            if (collision.isZoneValid(tile) && walkable(tile)) {
                return tile
            }
        }
        return raid.regionOf(room.arrival).takeIf { collision.isZoneValid(it) }
    }

    private fun walkable(tile: CoordGrid): Boolean =
        (collision[tile.x, tile.z, tile.level] and CollisionFlag.BLOCK_WALK) == 0

    // Barriers and the way on
    // -------------------------------------------------------------------------------------

    enum class BarrierResult {
        /** Walked through (out of a won fight, or into a fight already on). */
        Crossed,

        /** The leader is asked to start the fight. */
        AskToStart,
        LeaderFirst,
        StayAndFight,
        NotHere,
    }

    /** [player] wants through the barrier tile [tile] (a region tile). */
    fun barrier(player: Player, tile: CoordGrid): BarrierResult {
        val raid = raidOf(player) ?: return BarrierResult.NotHere
        val member = checkNotNull(raid.member(player))
        val real = raid.realOf(player.coords) ?: return BarrierResult.NotHere
        val room = member.room ?: return BarrierResult.NotHere
        val barrierReal = raid.realOf(tile) ?: return BarrierResult.NotHere
        val line =
            room.barriers.firstOrNull { barrierReal in it.tiles } ?: return BarrierResult.NotHere
        val entering = !line.onFightSide(real)
        val won = room in raid.completed
        if (entering) {
            if (won || isStarted(raid, room)) {
                cross(raid, member, line, real, intoFight = !won)
                return BarrierResult.Crossed
            }
            if (!raid.isLeader(player)) {
                return BarrierResult.LeaderFirst
            }
            return BarrierResult.AskToStart
        }
        if (isStarted(raid, room) && member.inFight && !won) {
            return BarrierResult.StayAndFight
        }
        cross(raid, member, line, real, intoFight = false)
        return BarrierResult.Crossed
    }

    /** Whether everyone in the raid has reached [player]'s room (else they would be abandoned). */
    fun everyoneHere(player: Player): Boolean {
        val raid = raidOf(player) ?: return true
        val room = raid.member(player)?.room
        return raid.active.all { it.room == room }
    }

    /** The leader said yes: the fight begins and they walk in. */
    fun startFight(player: Player, tile: CoordGrid) {
        val raid = raidOf(player) ?: return
        val member = checkNotNull(raid.member(player))
        val room = member.room ?: return
        val real = raid.realOf(player.coords) ?: return
        val barrierReal = raid.realOf(tile) ?: return
        val line = room.barriers.firstOrNull { barrierReal in it.tiles } ?: return
        if (room in raid.completed) {
            return
        }
        if (!isStarted(raid, room)) {
            started += raid.id to room
            eventBus.publish(TobEvents.FightStarted(raid, room))
        }
        cross(raid, member, line, real, intoFight = true)
    }

    /** Verzik's fight has no barrier: the leader starts it by talking to her. */
    fun startFightHere(raid: TobRaid, room: TobRoom) {
        if (isStarted(raid, room) || room in raid.completed) {
            return
        }
        started += raid.id to room
        for (member in raid.active) {
            if (member.room == room) {
                member.inFight = true
                member.beforeBarrier = raid.realOf(member.player.coords)
            }
        }
        eventBus.publish(TobEvents.FightStarted(raid, room))
    }

    private val started = HashSet<Pair<Int, TobRoom>>()

    fun isStarted(raid: TobRaid, room: TobRoom): Boolean = (raid.id to room) in started

    private fun cross(
        raid: TobRaid,
        member: TobMember,
        line: TobBarrier,
        real: CoordGrid,
        intoFight: Boolean,
    ) {
        val across = line.across(real)
        if (intoFight) {
            member.beforeBarrier = across
        }
        member.inFight = intoFight
        member.player.abortRoute()
        move(member.player, raid.regionOf(across))
        hud.refreshAll(raid)
    }

    enum class PassResult {
        Moved,
        NotComplete,
        NotHere,
    }

    /**
     * The way on from [player]'s room (a passage, Xarpus's door, the treasure room): only once the
     * room is won. The first through takes the party on to the next room.
     */
    fun pass(player: Player): PassResult {
        val raid = raidOf(player) ?: return PassResult.NotHere
        val member = checkNotNull(raid.member(player))
        val room = member.room ?: return PassResult.NotHere
        val next = room.next ?: return PassResult.NotHere
        if (room !in raid.completed) {
            return PassResult.NotComplete
        }
        if (raid.room.ordinal < next.ordinal) {
            raid.room = next
            raid.bar = TobBar.NONE
            if (next != TobRoom.Vault) {
                broadcast(
                    raid,
                    "${player.displayName} has advanced to Wave ${next.wave}. Join them...",
                    player,
                )
            }
        }
        fadeMove(player, next.label) { place(raid, member, next) }
        return PassResult.Moved
    }

    // Winning
    // --------------------------------------------------------------------------------------------

    /**
     * [room]'s fight is won: everyone is restored (hitpoints, prayer, special attack and run
     * energy), the dead come back where they crossed in, and the times are told.
     */
    fun completeRoom(raid: TobRaid, room: TobRoom, ticks: Int) {
        if (!raid.completed.add(room)) {
            return
        }
        raid.totalTicks += ticks
        raid.bar = TobBar.NONE
        val total =
            if (room == TobRoom.Verzik) "" else " Total: <col=ef1020>${time(raid.totalTicks)}</col>"
        val line =
            "Wave '${room.label}' (Normal Mode) complete!<br>" +
                "Duration: <col=ef1020>${time(ticks)}</col>$total"
        for (member in raid.active) {
            member.player.mes(line)
            restore(member.player)
            if (member.jailed) {
                release(raid, member, room)
            }
            member.inFight = false
        }
        wipeChecks.remove(raid)
        eventBus.publish(TobEvents.RoomCompleted(raid, room))
        hud.refreshAll(raid)
    }

    /** Verzik fell: the completion counts go up and the reward code rolls the loot. */
    fun raidWon(raid: TobRaid) {
        if (raid.won) {
            return
        }
        raid.won = true
        raid.endCycle = clock.cycle
        eventBus.publish(TobEvents.RaidWon(raid))
    }

    fun restore(player: Player) {
        for (stat in listOf(stats.hitpoints, stats.prayer)) {
            val missing = player.statBase(stat) - player.stat(stat)
            if (missing > 0) {
                player.statAdd(stat, missing, percent = 0)
            }
        }
        VarPlayerIntMapSetter.set(player, varps.sa_energy, FULL_SPECIAL)
        player.runEnergy = constants.run_max_energy
        player.mes("You feel refreshed as your health is replenished.")
    }

    private fun time(ticks: Int): String {
        val seconds = ticks * 6 / 10
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    // Deaths
    // -------------------------------------------------------------------------------------------

    /**
     * [player] died. In a fight that is still on they wait in the room's jail; anywhere else they
     * get up at the room's arrival. Nothing is lost.
     */
    fun died(player: Player) {
        val raid = raidOf(player) ?: return
        val member = checkNotNull(raid.member(player))
        val room = member.room ?: raid.roomAt(player.coords) ?: raid.room
        val inFight = member.inFight && isStarted(raid, room) && room !in raid.completed
        if (!inFight) {
            respawning[player.accountId] = arrival(raid, room) ?: raid.regionOf(room.arrival)
            return
        }
        raid.totalDeaths++
        member.deaths++
        member.deathsIn.merge(room, 1, Int::plus)
        member.deathTile = raid.realOf(player.coords)
        val jails = room.jails.ifEmpty { listOf(room.arrival) }
        val jail = jails[raid.active.count { it.jailed } % jails.size]
        member.jail = jail
        member.jailed = true
        member.inFight = false
        respawning[player.accountId] = raid.regionOf(jail)
        broadcast(raid, "${player.displayName} has died.", player)
        wipeChecks[raid] = clock.cycle + WIPE_DELAY
        hud.refreshAll(raid)
    }

    /** Where a player who just died in a raid wakes up, once; `null` for everyone else. */
    fun respawnFor(player: Player): CoordGrid? {
        val coords = respawning.remove(player.accountId) ?: return null
        val raid = memberRaid(player) ?: return coords
        raid.member(player)?.stanced = false
        hud.refreshAll(raid)
        return coords
    }

    private fun release(raid: TobRaid, member: TobMember, room: TobRoom) {
        member.jailed = false
        member.jail = null
        member.player.resetAnim()
        val back = if (room == TobRoom.Verzik) member.deathTile else member.beforeBarrier
        val tile = (back ?: room.arrival).let(raid::regionOf)
        if (collision.isZoneValid(tile)) {
            move(member.player, tile)
        }
    }

    private fun checkWipes() {
        if (wipeChecks.isEmpty()) {
            return
        }
        for ((raid, at) in wipeChecks.toList()) {
            if (clock.cycle < at) {
                continue
            }
            if (respawning.keys.any { id -> raid.active.any { it.player.accountId == id } }) {
                // A death still playing out: look again once everyone is in the jail.
                wipeChecks[raid] = clock.cycle + 1
                continue
            }
            wipeChecks.remove(raid)
            if (raid.ended || raid.active.isEmpty()) {
                continue
            }
            if (raid.active.all { it.jailed }) {
                fail(raid)
            }
        }
    }

    /** Everyone is dead: the raid is lost and the party is put outside. */
    private fun fail(raid: TobRaid) {
        raid.failed = true
        for (member in raid.active.toList()) {
            member.player.mes("Your party has failed.")
            leave(member.player, TobExit.Failed)
            restore(member.player)
        }
    }

    // Leaving
    // --------------------------------------------------------------------------------------------

    /**
     * Takes [player] out of the raid, and out of the theatre to Ver Sinhaza unless they logged out
     * or something else moved them. The raid ends when its last member leaves.
     */
    fun leave(player: Player, why: TobExit): Boolean {
        val raid = raids.values.firstOrNull { it.member(player)?.left == false } ?: return false
        val member = checkNotNull(raid.member(player))
        member.left = true
        member.jailed = false
        member.inFight = false
        member.room = null
        respawning.remove(player.accountId)
        fadeMoves.removeAll { it.player === player }
        removeDawnbringer(player)
        player.resetAnim()
        parties.stateOf(player).current?.let { parties.leave(it, player) }
        eventBus.publish(TobEvents.PlayerLeft(raid, player))
        val moveOut =
            why != TobExit.LoggedOut && why != TobExit.Left && raid.contains(player.coords)
        if (moveOut) {
            move(player, VerSinhaza.OUTSIDE)
            hud.fade(
                player,
                FADE_CLEAR,
                if (why == TobExit.Failed) "Your party has failed." else "",
            )
        }
        if (why == TobExit.LoggedOut) {
            hud.close(player)
        } else {
            hud.lobby(player, null)
        }
        if (raid.active.isEmpty()) {
            end(raid)
        } else {
            hud.namesForAll(raid)
            hud.refreshAll(raid)
            wipeChecks[raid] = clock.cycle + WIPE_DELAY
        }
        return true
    }

    /** The Dawnbringer never leaves Verzik's room. */
    fun removeDawnbringer(player: Player) {
        val type = objTypes[tob_objs.dawnbringer]
        val carried = player.inv.count(type)
        if (carried > 0) {
            player.invDel(player.inv, type, carried)
        }
        if (player.worn.count(type) > 0) {
            player.invDel(player.worn, type, player.worn.count(type))
            player.rebuildAppearance()
        }
    }

    private fun end(raid: TobRaid) {
        if (raid.ended) {
            return
        }
        raid.ended = true
        if (raid.endCycle < 0) {
            raid.endCycle = clock.cycle
        }
        wipeChecks.remove(raid)
        started.removeIf { it.first == raid.id }
        eventBus.publish(TobEvents.RaidEnded(raid))
    }

    /** Drops raids that nobody is in any more. */
    fun sweep() {
        evictStrays()
        val online = players.toList()
        for (raid in raids.values.toList()) {
            val someoneInside =
                raid.active.any { m ->
                    raid.contains(m.player.coords) && online.any { it === m.player }
                }
            val someoneArriving = fadeMoves.any { pending -> raid.member(pending.player) != null }
            if (!someoneInside && !someoneArriving) {
                raid.members.forEach { it.left = true }
                end(raid)
            }
            if (raid.ended) {
                raids.remove(raid.id)
            }
        }
    }

    /** Whoever is in a raid but no longer in its region (a teleport moved them) has left it. */
    private fun evictStrays() {
        for (raid in raids.values.toList()) {
            for (member in raid.active.toList()) {
                if (member.room != null && !raid.contains(member.player.coords)) {
                    leave(member.player, TobExit.Left)
                }
            }
        }
    }

    // Time
    // ---------------------------------------------------------------------------------------------

    fun tick() {
        tickFades()
        tickViewDistance()
        if (raids.isEmpty()) {
            return
        }
        checkWipes()
        holdJailed()
        if (clock.cycle % HUD_TICKS == 0) {
            for (raid in raids.values) {
                if (!raid.ended) {
                    hud.refreshAll(raid)
                }
            }
        }
        if (clock.cycle % STRAY_TICKS == 0) {
            evictStrays()
        }
        if (clock.cycle % SWEEP_TICKS == 0) {
            sweep()
        }
    }

    /** The jailed stay on their jail tile and cannot act. */
    private fun holdJailed() {
        for (raid in raids.values) {
            for (member in raid.active) {
                if (!member.jailed || member.player.accountId in respawning) {
                    continue
                }
                val jail = member.jail?.let(raid::regionOf) ?: continue
                if (member.player.coords != jail) {
                    move(member.player, jail)
                } else if (!member.stanced) {
                    member.stanced = true
                    member.player.anim(tob_seqs.purgatory)
                    member.player.mes("If your party survives the wave, you will respawn.")
                }
                member.player.abortRoute()
                member.player.clearInteraction()
            }
        }
    }

    /** Big bosses across a wide arena stay drawn: raid players are told of npcs further out. */
    private fun tickViewDistance() {
        if (raids.isEmpty() && widened.isEmpty()) {
            return
        }
        val inside = HashSet<Player>()
        for (raid in raids.values) {
            if (raid.ended) continue
            for (member in raid.active) {
                if (raid.contains(member.player.coords)) {
                    inside += member.player
                }
            }
        }
        for (player in inside) {
            if (widened.add(player)) {
                player.client.setNpcViewDistance(NPC_VIEW_DISTANCE)
            }
        }
        val gone = widened.filter { it !in inside }
        if (gone.isNotEmpty()) {
            val online = players.toList()
            for (player in gone) {
                widened -= player
                if (online.any { it === player }) {
                    player.client.resetNpcViewDistance()
                }
            }
        }
    }

    fun broadcast(raid: TobRaid, text: String, except: Player? = null) {
        for (member in raid.active) {
            if (member.player !== except) {
                member.player.mes(text)
            }
        }
    }

    companion object {
        private const val ARRIVAL_ATTEMPTS = 8
        private const val HUD_TICKS = 2
        private const val STRAY_TICKS = 5
        private const val SWEEP_TICKS = 50

        /** As TSPS has it: the wipe is called three ticks after the last death. */
        const val WIPE_DELAY = 3
        const val FADE_TICKS = 2
        private const val FADE_CLEAR = 255
        private const val FULL_SPECIAL = 1000
        const val NPC_VIEW_DISTANCE = 40
    }
}

private fun TobHud.namesForAll(raid: TobRaid) {
    for (member in raid.active) {
        names(raid, member.player)
    }
}
