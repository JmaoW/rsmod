package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.varp.VarpType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** What came of a player asking to go into the tombs from the lobby. */
enum class ToaEntry {
    Entered,
    NoParty,

    /** The leader has not gone in yet. */
    LeaderFirst,

    /** Left (or never was part of) this party's raid; it cannot be rejoined. */
    NotInRaid,
    AlreadyInside,

    /** There is a reward waiting in the chest. */
    RewardWaiting,
    NoSpace,
    InvalidEntry,
}

/** Why a player stopped being part of a raid. */
enum class ToaExit {
    /** Used an exit and confirmed. */
    Abandoned,
    Died,
    Failed,
    Completed,
    LoggedOut,

    /** Something moved them out of the tombs. */
    Left,
}

/**
 * Runs the raids: one [ToaRaid] per party that has gone in. A raid's rooms are one packed region
 * ([ToaPacking]); the manager moves the party through them (lobby, Nexus, a path, the Wardens, the
 * vault), and keeps score of deaths and the team's attempts.
 *
 * What happens inside a room is up to the room's own content, which listens for [ToaEvents] and
 * calls [completeRoom], [completePath] and the rest of this class when a challenge is won.
 */
@Singleton
class ToaRaids
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val collision: CollisionFlagMap,
    private val zoneActivity: ZonePlayerActivityBitSet,
    private val clock: MapClock,
    private val players: PlayerList,
    private val eventBus: EventBus,
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val locRepo: LocRepository,
    private val parties: ToaParties,
    private val random: GameRandom,
    private val hud: ToaHud,
) {
    private val raids = LinkedHashMap<Int, ToaRaid>()
    private var nextId = 1

    /** Players who died in a raid and are about to be put at [Respawn.coords] by the death. */
    private class Respawn(val coords: CoordGrid, val ghost: Boolean)

    private val respawning = HashMap<Int, Respawn>()

    /** Rooms where everyone may just have died, to check for a team wipe next tick. */
    private val wipeChecks = ArrayList<Pair<ToaRaid, ToaRoom>>()

    /** Players inside a raid who are told about npcs further away than usual. */
    private val widened = HashSet<Player>()

    /** Rooms whose challenge is won (the dead are back and the room stays done). */
    private val wonRooms = HashMap<Int, MutableSet<ToaRoom>>()

    /** The random numbers for tile spreads and path levelling. Tests replace it. */
    var pick: (Int) -> Int = { random.of(it) }

    /**
     * How many ticks the screen stays black before a player is moved between rooms ([fadeMove]).
     * Tests that do not care about the fade set 0, which moves at once with no fade.
     */
    var fadeTicks: Int = FADE_TICKS

    /** Moves waiting for the screen to go black: the tick to run at, who, and the move. */
    private class FadeMove(val at: Int, val player: Player, val move: () -> Unit)

    private val fadeMoves = ArrayList<FadeMove>()

    /** Fade overlays to close: the tick, and whose. */
    private val fadeCloses = ArrayList<Pair<Int, Player>>()

    /**
     * Moving between rooms: [player]'s screen fades to black, then [move] runs (they cannot act in
     * between), and the screen fades back in where they are.
     */
    fun fadeMove(player: Player, move: () -> Unit) {
        if (fadeTicks <= 0) {
            move()
            return
        }
        Cinematic.fadeOverlay(player, 0, FADE_CLEAR, 0, FADE_BLACK, FADE_CYCLES, eventBus)
        player.abortRoute()
        player.delay = clock.cycle + fadeTicks
        fadeMoves += FadeMove(clock.cycle + fadeTicks, player, move)
    }

    /**
     * For a move that has already happened (into the tombs, out of them): the screen starts black
     * and fades in.
     */
    fun fadeIn(player: Player) {
        if (fadeTicks <= 0) {
            return
        }
        Cinematic.fadeOverlay(player, 0, FADE_BLACK, 0, FADE_CLEAR, FADE_CYCLES, eventBus)
        fadeCloses += (clock.cycle + FADE_CLOSE_TICKS) to player
    }

    private fun tickFades() {
        if (fadeMoves.isNotEmpty()) {
            val due = fadeMoves.filter { it.at <= clock.cycle }
            fadeMoves.removeAll(due.toSet())
            for (pending in due) {
                if (pending.player.isSlotAssigned) {
                    pending.move()
                    fadeIn(pending.player)
                }
            }
        }
        if (fadeCloses.isNotEmpty()) {
            val due = fadeCloses.filter { it.first <= clock.cycle }
            fadeCloses.removeAll(due.toSet())
            for ((_, player) in due) {
                if (player.isSlotAssigned) {
                    Cinematic.closeFadeOverlay(player, eventBus)
                }
            }
        }
    }

    fun liveRaids(): List<ToaRaid> {
        sweep()
        return raids.values.toList()
    }

    /** The raids running now, without tidying up first (cheap enough for every tick). */
    fun running(): Collection<ToaRaid> = raids.values.filter { it.stage != RaidStage.Ended }

    /** The raid [player] is in, if they are inside its region and have not left it. */
    fun raidOf(player: Player): ToaRaid? =
        raids.values.firstOrNull { raid ->
            raid.stage != RaidStage.Ended &&
                raid.contains(player.coords) &&
                raid.member(player)?.left == false
        }

    /**
     * Where the build area of someone standing on [coords] in a raid's region starts, if anywhere.
     */
    fun sceneAnchor(coords: CoordGrid): CoordGrid? {
        val raid = raids.values.firstOrNull { it.contains(coords) } ?: return null
        val real = raid.realOf(coords) ?: return null
        return ToaPacking.sceneAnchor(real, raid.region.southWest)
    }

    /**
     * The raid's target time as "mm:ss", or `null` without a time-limit invocation (the RuneLite
     * plugin reads two-digit minutes from "Overall time to beat:").
     */
    private fun targetTime(raid: ToaRaid): String? {
        val ticks = raid.settings.timeLimitTicks ?: return null
        val seconds = ticks * 6 / 10
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    fun isGhost(player: Player): Boolean = raidOf(player)?.member(player)?.ghost == true

    fun isWon(raid: ToaRaid, room: ToaRoom): Boolean = room in wonRooms[raid.id].orEmpty()

    // Going in
    // ---------------------------------------------------------------------------------------

    /**
     * Takes [player] into the tombs from the lobby: the leader starts their party's raid, the
     * others follow once it has started.
     */
    fun enterFromLobby(player: Player): ToaEntry {
        sweep()
        val party = parties.currentParty(player) ?: return ToaEntry.NoParty
        if (raidOf(player) != null) {
            return ToaEntry.AlreadyInside
        }
        if (player.vars[toa_varbits.should_have_loot] != 0) {
            return ToaEntry.RewardWaiting
        }
        var raid = party.raid
        if (raid != null && raid.stage == RaidStage.Ended) {
            // A raid that ended while the party stood by (everyone was moved out of it).
            party.raid = null
            raid = null
        }
        if (raid == null) {
            if (!party.isLeader(player)) {
                return ToaEntry.LeaderFirst
            }
            raid = start(party) ?: return ToaEntry.NoSpace
        }
        val member = raid.member(player)
        if (member == null || member.left) {
            return ToaEntry.NotInRaid
        }
        if (!place(raid, member, ToaRoom.Nexus)) {
            return ToaEntry.InvalidEntry
        }
        fadeIn(player)
        hud.open(raid, player)
        hud.namesForAll(raid)
        player.mes("You enter the Tombs of Amascut (${raid.mode.label} Mode)...")
        targetTime(raid)?.let { target ->
            if (raid.challengeStartCycle < 0) {
                player.mes(
                    "Overall time to beat: <col=ef1020>$target</col>. " +
                        "The timer starts upon choosing your first path."
                )
            }
        }
        return ToaEntry.Entered
    }

    private fun start(party: ToaParty): ToaRaid? {
        val region = regionRepo.add(ToaPacking.template()) ?: return null
        val raid =
            ToaRaid(
                id = nextId++,
                region = region,
                settings = party.settings.copy(),
                startedCycle = clock.cycle,
                pathsRequired = EventSettings.current.toa.pathsRequired,
            )
        party.members.forEachIndexed { slot, player ->
            raid.members += ToaMember(player, slot)
            addStat(player, toa_varps.attempts[raid.mode.ordinal])
        }
        party.raid = raid
        parties.unlist(party)
        raids[raid.id] = raid
        // The region exists from now but nobody stands in it until the first player is placed; any
        // other region allocated in between would reclaim it.
        zoneActivity.flag(region.southWestZone)
        syncDoors(raid)
        return raid
    }

    /**
     * Puts [member] at the entry of [room], spread over the room's arrival tiles. `false` if no
     * tile of the room can be stood on.
     */
    fun place(raid: ToaRaid, member: ToaMember, room: ToaRoom, onFloor: Boolean = false): Boolean {
        val dest = (if (onFloor) floorArrival(raid, room) else null) ?: arrival(raid, room)
        if (dest == null) {
            return false
        }
        PathingEntityCommon.telejump(member.player, collision, dest)
        zoneActivity.flag(ZoneKey.from(dest))
        val changed = member.room != room
        member.room = room
        if (changed) {
            hud.refreshAllOrbs(raid)
            eventBus.publish(ToaEvents.RoomEntered(raid, room, member.player))
        }
        return true
    }

    private fun arrival(raid: ToaRaid, room: ToaRoom): CoordGrid? {
        val spawn = room.spawn
        repeat(ARRIVAL_ATTEMPTS) {
            val dx = if (room.spreadX > 0) pick(room.spreadX + 1) else 0
            val dz = if (room.spreadZ > 0) pick(room.spreadZ + 1) else 0
            val real = spawn.translate(dx, dz)
            val inside = ToaPacking.toRegion(real, raid.region.southWest) ?: return null
            if (collision.isZoneValid(inside)) {
                return inside
            }
        }
        val fallback = ToaPacking.toRegion(spawn, raid.region.southWest) ?: return null
        return fallback.takeIf { collision.isZoneValid(it) }
    }

    /** A free tile beside [room]'s challenge spot (the fight floor), if it has one. */
    private fun floorArrival(raid: ToaRaid, room: ToaRoom): CoordGrid? {
        val challenge = room.challenge ?: return null
        repeat(ARRIVAL_ATTEMPTS) {
            val real = challenge.translate(pick(FLOOR_SPREAD * 2 + 1) - FLOOR_SPREAD, 0)
            val inside = ToaPacking.toRegion(real, raid.region.southWest) ?: return null
            if (collision.isZoneValid(inside) && collision.isWalkable(inside)) {
                return inside
            }
        }
        return ToaPacking.toRegion(challenge, raid.region.southWest)
    }

    private fun CollisionFlagMap.isWalkable(tile: CoordGrid): Boolean =
        (this[tile.x, tile.z, tile.level] and CollisionFlag.BLOCK_WALK) == 0

    // Leaving
    // ----------------------------------------------------------------------------------------

    /**
     * Takes [player] out of the raid. They are left in the lobby (unless they are logging out or
     * were moved by something else). The raid ends when its last member leaves.
     */
    fun leave(player: Player, why: ToaExit): Boolean {
        val raid = raids.values.firstOrNull { it.member(player)?.left == false } ?: return false
        val member = checkNotNull(raid.member(player))
        member.left = true
        member.ghost = false
        member.room = null
        respawning.remove(player.accountId)
        // A player who leaves the raid leaves its party, so the board shows who is still in it.
        parties.stateOf(player).current?.let { parties.leave(it, player) }
        if (player.transmog != null) {
            player.transmog = null
            player.rebuildAppearance()
        }
        hud.close(player)
        eventBus.publish(ToaEvents.PlayerLeft(raid, player))
        val moved = why != ToaExit.LoggedOut && why != ToaExit.Left && raid.contains(player.coords)
        if (moved) {
            PathingEntityCommon.telejump(player, collision, ToaLobby.RETURN)
            fadeIn(player)
        }
        if (raid.active.isEmpty()) {
            end(raid)
        } else {
            hud.namesForAll(raid)
            hud.refreshAllOrbs(raid)
        }
        return true
    }

    private fun end(raid: ToaRaid) {
        if (raid.stage == RaidStage.Ended) {
            return
        }
        raid.stage = RaidStage.Ended
        if (raid.endCycle < 0) {
            raid.endCycle = clock.cycle
        }
        wonRooms.remove(raid.id)
        wipeChecks.removeAll { it.first === raid }
        eventBus.publish(ToaEvents.RaidEnded(raid))
    }

    /** Drops raids that nobody is in any more. */
    fun sweep() {
        evictStrays()
        val online = players.toList()
        for (raid in raids.values.toList()) {
            val someoneInside =
                raid.active.any { member ->
                    raid.contains(member.player.coords) && online.any { it === member.player }
                }
            if (!someoneInside) {
                for (member in raid.members) {
                    member.left = true
                }
                end(raid)
            }
            if (raid.stage == RaidStage.Ended) {
                raids.remove(raid.id)
            }
        }
    }

    /**
     * Whoever is in a raid but no longer in its region (a teleport, a spell, a command moved them)
     * has left it: they are taken out of the raid and its party, so they can start or join again.
     */
    private fun evictStrays() {
        for (raid in raids.values.toList()) {
            for (member in raid.active.toList()) {
                if (member.room != null && !raid.contains(member.player.coords)) {
                    leave(member.player, ToaExit.Left)
                }
            }
        }
    }

    // The Nexus and the paths
    // -------------------------------------------------------------------------

    /** What a player's walk through a path door came to. */
    enum class PathChoice {
        Started,
        Joined,
        AlreadyDone,
        OtherPathChosen,
        LeaderFirst,
        NotInNexus,
    }

    /**
     * A path door: the leader chooses the party's path and goes in, the others follow it. Other
     * doors are shut until the path is cleared.
     */
    fun choosePath(player: Player, path: ToaPath): PathChoice {
        val raid = raidOf(player) ?: return PathChoice.NotInNexus
        val member = checkNotNull(raid.member(player))
        if (member.room != ToaRoom.Nexus) {
            return PathChoice.NotInNexus
        }
        if (path in raid.completed) {
            return PathChoice.AlreadyDone
        }
        val chosen = raid.currentPath
        if (chosen != null && chosen != path) {
            return PathChoice.OtherPathChosen
        }
        if (chosen == null) {
            if (!raid.isLeader(player)) {
                return PathChoice.LeaderFirst
            }
            raid.currentPath = path
            if (raid.challengeStartCycle < 0) {
                raid.challengeStartCycle = clock.cycle
                raid.active.forEach { hud.timer(raid, it.player) }
                targetTime(raid)?.let { target ->
                    broadcast(
                        raid,
                        "Overall time to beat: <col=ef1020>$target</col>. The timer has started!",
                    )
                }
            }
            syncDoors(raid)
            broadcast(
                raid,
                "${player.displayName} has chosen to walk the Path of ${path.label}. Join them...",
                player,
            )
        }
        fadeMove(player) {
            place(raid, member, path.puzzle)
            hud.currentPath(player, path.hud)
            hud.refreshAllOrbs(raid)
        }
        return if (chosen == null) PathChoice.Started else PathChoice.Joined
    }

    /** The party walks back to the Nexus from [path]'s rooms (Osmumten's way, or a crystal). */
    fun returnToNexus(player: Player): Boolean {
        val raid = raidOf(player) ?: return false
        val member = checkNotNull(raid.member(player))
        if (member.room == ToaRoom.Nexus || member.room == null) {
            return false
        }
        val path = member.room?.path ?: raid.currentPath
        val room = ToaRoom.Nexus
        val dest = if (path != null) nexusBack(raid, path) else arrival(raid, room)
        if (dest == null) {
            return false
        }
        fadeMove(player) {
            PathingEntityCommon.telejump(player, collision, dest)
            member.room = room
            hud.currentPath(player, 0)
            hud.refreshAllOrbs(raid)
        }
        return true
    }

    private fun nexusBack(raid: ToaRaid, path: ToaPath): CoordGrid? {
        val dx = if (path.spread > 0) pick(path.spread + 1) else 0
        val inside = ToaPacking.toRegion(path.back.translate(dx, 0), raid.region.southWest)
        return inside?.takeIf { collision.isZoneValid(it) } ?: arrival(raid, ToaRoom.Nexus)
    }

    /**
     * A challenge room was won: everyone's room points join the total, the dead come back and
     * listeners are told. Puzzle completion points are the caller's to [ToaPoints.award].
     */
    fun completeRoom(raid: ToaRaid, room: ToaRoom) {
        if (!wonRooms.getOrPut(raid.id) { HashSet() }.add(room)) {
            return
        }
        raid.points.completeRoom()
        for (member in raid.active) {
            if (member.ghost && member.room == room) {
                revive(member)
            }
        }
        eventBus.publish(ToaEvents.RoomCompleted(raid, room))
        hud.refreshAllOrbs(raid)
    }

    /**
     * A path's boss fell: the path is cleared, its door is done and (with Walk the Path) two of the
     * other paths level up.
     */
    fun completePath(raid: ToaRaid, path: ToaPath) {
        if (!raid.completed.add(path)) {
            return
        }
        if (raid.currentPath == path) {
            raid.currentPath = null
        }
        raid.levelUpAfterClear(pick)
        syncDoors(raid)
        hud.pathLevelsForAll(raid)
        eventBus.publish(ToaEvents.PathCompleted(raid, path))
    }

    /**
     * The doors of the Nexus follow the raid: shut once cleared, dimmed while another is chosen.
     */
    fun syncDoors(raid: ToaRaid) {
        for (path in ToaPath.entries) {
            val base = doorBase(path)
            val state =
                when {
                    path in raid.completed -> 2
                    raid.currentPath != null && raid.currentPath != path -> 1
                    else -> 0
                }
            setLoc(raid, path.door, base, base + state, 2)
        }
        val wardens = ToaLocs.nexus_wardens.id
        setLoc(raid, WARDENS_DOOR, wardens, if (raid.wardensOpen) wardens + 1 else wardens, 1)
    }

    private fun doorBase(path: ToaPath): Int =
        when (path) {
            ToaPath.Scabaras -> ToaLocs.nexus_scabaras.id
            ToaPath.Het -> ToaLocs.nexus_het.id
            ToaPath.Apmeken -> ToaLocs.nexus_apmeken.id
            ToaPath.Crondis -> ToaLocs.nexus_crondis.id
        }

    /** Swaps the loc at [real] (any of `base..base+variants`) for the one with id [into]. */
    private fun setLoc(raid: ToaRaid, real: CoordGrid, base: Int, into: Int, variants: Int) {
        val coords = raid.regionOf(real)
        for (id in base..base + variants) {
            val current = locRepo.findExact(coords, locTypes.getValue(id)) ?: continue
            if (id != into) {
                locRepo.change(current, locTypes.getValue(into), Int.MAX_VALUE)
            }
            return
        }
    }

    // The Wardens and the end
    // --------------------------------------------------------------------------

    enum class WardensEntry {
        Entered,
        Sealed,
        LeaderFirst,
        NotInNexus,
    }

    /** The Nexus's lower door: opens once the required paths are cleared. */
    fun enterWardens(player: Player): WardensEntry {
        val raid = raidOf(player) ?: return WardensEntry.NotInNexus
        val member = checkNotNull(raid.member(player))
        if (member.room != ToaRoom.Nexus) {
            return WardensEntry.NotInNexus
        }
        if (!raid.wardensOpen) {
            return WardensEntry.Sealed
        }
        if (raid.stage == RaidStage.Paths) {
            if (!raid.isLeader(player)) {
                return WardensEntry.LeaderFirst
            }
            raid.stage = RaidStage.Wardens
            broadcast(
                raid,
                "${player.displayName} has proceeded to the lower level. Join them...",
                player,
            )
        }
        fadeMove(player) {
            place(raid, member, ToaRoom.WardensFirst)
            hud.currentPath(player, WARDENS_FIRST_HUD)
        }
        return WardensEntry.Entered
    }

    /**
     * The Wardens' first phases are over; the party goes to the final arena, onto its floor (its
     * arrival tiles at the back are where the dead come back).
     */
    fun advanceWardens(raid: ToaRaid) {
        for (member in raid.active) {
            if (member.room == ToaRoom.WardensFirst) {
                fadeMove(member.player) {
                    if (member.room == ToaRoom.WardensFirst) {
                        place(raid, member, ToaRoom.WardensFinal, onFloor = true)
                        hud.currentPath(member.player, WARDENS_FINAL_HUD)
                    }
                }
            }
        }
    }

    /**
     * The Wardens fell: the clock stops, a missed target time costs its raid levels, and the party
     * is taken to the vault. [ToaEvents.RaidWon] tells the reward code to roll the loot.
     */
    fun wardensDefeated(raid: ToaRaid) {
        if (raid.stage == RaidStage.Vault || raid.stage == RaidStage.Ended) {
            return
        }
        raid.stage = RaidStage.Vault
        raid.endCycle = clock.cycle
        raid.points.completeRoom()
        raid.finalRaidLevel = raid.levelAt(clock.cycle)
        for (member in raid.active) {
            if (member.ghost) {
                revive(member)
            }
            hud.timer(raid, member.player)
        }
        eventBus.publish(ToaEvents.RaidWon(raid))
        for (member in raid.active) {
            fadeMove(member.player) {
                place(raid, member, ToaRoom.Vault)
                hud.currentPath(member.player, 0)
            }
        }
    }

    // Deaths
    // -------------------------------------------------------------------------------------------

    /**
     * [player] died. They are put back at the start of their room (or the Nexus) by the death, and
     * in a challenge room they stay out of the fight as a ghost until the room is won or lost.
     */
    fun died(player: Player) {
        val raid = raidOf(player) ?: return
        val member = checkNotNull(raid.member(player))
        raid.totalDeaths++
        member.deaths++
        addStat(player, toa_varps.deaths[raid.mode.ordinal])
        raid.points.death(player.accountId)
        player.mes("You have died. Total deaths: ${raid.totalDeaths}.")
        broadcast(
            raid,
            "${player.displayName} has died. Total deaths: ${raid.totalDeaths}.",
            player,
        )
        val room = member.room ?: raid.roomAt(player.coords)
        val inChallenge = room != null && room.hasFight && !isWon(raid, room)
        val target = if (room == null || !inChallenge) ToaRoom.Nexus else room
        val coords = arrival(raid, target) ?: raid.regionOf(ToaRoom.Nexus.spawn)
        respawning[player.accountId] = Respawn(coords, inChallenge)
        if (inChallenge) {
            wipeChecks += raid to checkNotNull(room)
        }
    }

    /** Where a player who just died in a raid wakes up, once; `null` for everyone else. */
    fun respawnFor(player: Player): CoordGrid? {
        val respawn = respawning.remove(player.accountId) ?: return null
        val raid = raidOf(player) ?: return respawn.coords
        val member = raid.member(player)
        if (member != null) {
            member.room = raid.roomAt(respawn.coords)
            if (respawn.ghost) {
                makeGhost(member)
            }
        }
        hud.refreshAllOrbs(raid)
        return respawn.coords
    }

    private fun makeGhost(member: ToaMember) {
        member.ghost = true
        val ghost = npcTypes[ToaNpcs.ghost]
        member.player.transmog = ghost
    }

    private fun revive(member: ToaMember) {
        member.ghost = false
        if (member.player.transmog != null) {
            member.player.transmog = null
            member.player.rebuildAppearance()
        }
    }

    // Time
    // ---------------------------------------------------------------------------------------------

    /** Runs every game tick: team wipes after deaths, the health orbs, and tidying up. */
    fun tick() {
        tickFades()
        tickViewDistance()
        if (raids.isEmpty()) {
            return
        }
        if (wipeChecks.isNotEmpty()) {
            val pending = wipeChecks.toList()
            wipeChecks.clear()
            for ((raid, room) in pending) {
                checkWipe(raid, room)
            }
        }
        if (clock.cycle % ORB_REFRESH_TICKS == 0) {
            for (raid in raids.values) {
                if (raid.stage != RaidStage.Ended) {
                    hud.refreshAllOrbs(raid)
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

    /**
     * The client is normally told only about npcs within 15 tiles, measured from an npc's
     * south-west tile, so a big boss across its arena (Zebak from the far side of his) stopped
     * being drawn. Everyone inside a raid's region is told about npcs further out, and back to
     * normal on leaving.
     */
    private fun tickViewDistance() {
        if (raids.isEmpty() && widened.isEmpty()) {
            return
        }
        val inside = HashSet<Player>()
        for (raid in raids.values) {
            if (raid.stage == RaidStage.Ended) {
                continue
            }
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

    private fun checkWipe(raid: ToaRaid, room: ToaRoom) {
        if (raid.stage == RaidStage.Ended || isWon(raid, room)) {
            return
        }
        val inRoom =
            raid.active.filter { it.room == room || respawning.containsKey(it.player.accountId) }
        // Wait until every pending death has finished its sequence and the dead stand in the room.
        if (
            inRoom.isEmpty() ||
                respawning.keys.any { id -> raid.active.any { it.player.accountId == id } }
        ) {
            wipeChecks += raid to room
            return
        }
        val standing = inRoom.any { !it.ghost }
        if (standing) {
            return
        }
        teamWipe(raid, room)
    }

    /** Everyone in the challenge is dead: an attempt is spent, and the room starts again. */
    private fun teamWipe(raid: ToaRaid, room: ToaRoom) {
        raid.teamWipes++
        val left = raid.attemptsLeft
        for (member in raid.active) {
            if (member.room == room) {
                revive(member)
            }
        }
        if (left != null && left <= 0) {
            raid.failed = true
            for (member in raid.active.toList()) {
                member.player.mes("You failed to survive the Tombs of Amascut.")
                leave(member.player, ToaExit.Failed)
            }
            return
        }
        for (member in raid.active) {
            if (member.room == room) {
                member.player.mes(
                    if (left == null)
                        "Your party failed to complete the challenge. You may try again..."
                    else
                        "Your party failed to complete the challenge. You have $left attempts remaining..."
                )
                place(raid, member, room)
            }
        }
        eventBus.publish(ToaEvents.RoomReset(raid, room))
    }

    // Helpers
    // ------------------------------------------------------------------------------------------

    fun broadcast(raid: ToaRaid, text: String, except: Player? = null) {
        for (member in raid.active) {
            if (member.player !== except) {
                member.player.mes(text)
            }
        }
    }

    private fun addStat(player: Player, stat: VarpType) {
        VarPlayerIntMapSetter.set(player, stat, player.vars[stat] + 1)
    }

    /** Clears every player's loot flag and varp the lobby shows. Used by the reward code. */
    fun clearLootFlag(player: Player) {
        VarPlayerIntMapSetter.set(player, toa_varbits.should_have_loot, 0)
    }

    companion object {
        private const val ARRIVAL_ATTEMPTS = 8
        private const val ORB_REFRESH_TICKS = 2

        /** How far away raid players are told about npcs: across the biggest arena and more. */
        const val NPC_VIEW_DISTANCE = 40
        private const val SWEEP_TICKS = 50
        private const val STRAY_TICKS = 5

        /** Arriving on a fight floor: up to this many tiles either side of its challenge spot. */
        private const val FLOOR_SPREAD = 2

        /**
         * The fade between rooms (TSPS's capture): black after [FADE_CYCLES] client cycles, the
         * move [FADE_TICKS] ticks later, and the overlay closed once it has faded back in.
         */
        const val FADE_TICKS = 2
        private const val FADE_CYCLES = 15
        private const val FADE_CLOSE_TICKS = 2
        private const val FADE_CLEAR = 255
        private const val FADE_BLACK = 0

        /** The HUD's path values while in the Wardens' first and final rooms. */
        const val WARDENS_FIRST_HUD = 5
        const val WARDENS_FINAL_HUD = 6

        /** Real tile of the Nexus's door to the Wardens. */
        val WARDENS_DOOR = CoordGrid(3548, 5134, 0)
    }
}
