package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Runs the raids: one [CoxRaid] per party that has gone in, each in its own region laid out by
 * [CoxLayout] and built by [CoxMap].
 * - The first party member to walk in builds the raid; everyone lands in the first room, whose
 *   passage stays shut until the leader starts the raid from the side panel ("No-one may join the
 *   party after the raid begins.").
 * - Rooms without an encounter (resources, scavengers, the floors' ends) are open; the others open
 *   when their content calls [openRoom].
 * - The hole at a floor's end goes down to the next floor (or the Great Olm); reaching a floor
 *   completes the one above ("Upper level complete! Duration: m:ss") and moves the respawn there.
 * - Deaths cost 40% of the raider's points (or the team 5% if theirs were under 5% of the total,
 *   Wiki); raid-only items drop where the raider fell, the rest is kept (the isle's rule).
 */
@Singleton
class CoxRaids
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val collision: CollisionFlagMap,
    private val zoneActivity: ZonePlayerActivityBitSet,
    private val clock: MapClock,
    private val players: PlayerList,
    private val eventBus: EventBus,
    private val parties: CoxParties,
    private val random: GameRandom,
    private val hud: CoxHud,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
) {
    private val raids = LinkedHashMap<Int, CoxRaid>()
    private var nextId = 1
    private val respawning = HashMap<Int, CoordGrid>()

    /** The dice; tests replace it. */
    var pick: (Int) -> Int = { random.of(it) }

    /** Ticks a fade takes before the move; tests set 0. */
    var fadeTicks: Int = 1

    private class Move(val at: Int, val player: Player, val action: () -> Unit)

    private val moves = ArrayList<Move>()

    fun running(): List<CoxRaid> = raids.values.filter { !it.ended }

    fun raidOf(player: Player): CoxRaid? =
        raids.values.firstOrNull { raid ->
            !raid.ended && raid.contains(player.coords) && raid.member(player)?.left == false
        }

    fun memberRaid(player: Player): CoxRaid? =
        raids.values.firstOrNull { !it.ended && it.member(player)?.left == false }

    // Going in
    // ---------------------------------------------------------------------------------------------

    fun enter(player: Player): CoxEntry {
        val party = parties.partyOf(player) ?: return CoxEntry.NoParty
        if (raidOf(player) != null) return CoxEntry.Entered
        var raid = party.raid?.takeIf { !it.ended }
        if (raid == null) {
            if (!party.isLeader(player)) return CoxEntry.LeaderFirst
            raid = build(party) ?: return CoxEntry.NoSpace
        }
        var member = raid.member(player)
        if (member == null) {
            if (raid.started) return CoxEntry.AlreadyStarted
            member = CoxMember(player, raid.members.size)
            raid.members += member
        } else if (member.left) {
            return CoxEntry.AlreadyStarted
        }
        val start = raid.rooms.first()
        val spawn = landing(raid, start, CoxTiles.RAID_START_SPAWN[start.variant])
        fadeMove(player) {
            move(player, spawn)
            hud.open(raid, player)
            hud.timer(raid, player)
        }
        hud.membersAll(raid)
        hud.refreshAll(raid)
        return CoxEntry.Entered
    }

    private fun build(party: CoxParty): CoxRaid? {
        val layout = if (party.challenge) CoxLayout.challenge() else CoxLayout.normal(pick)
        val region = regionRepo.add(CoxMap.template(layout, pick)) ?: return null
        val raid = CoxRaid(nextId++, region, layout, party, clock.cycle)
        party.raid = raid
        party.advertised = false
        raids[raid.id] = raid
        zoneActivity.flag(region.southWestZone)
        for (room in layout.rooms) {
            if (room.type.kind in OPEN_KINDS) raid.opened += room.index
        }
        eventBus.publish(CoxEvents.RaidBuilt(raid))
        return raid
    }

    /** The leader pressed "Start raid". */
    fun start(player: Player): Boolean {
        val raid = raidOf(player) ?: return false
        if (raid.started || !raid.isLeader(player)) return false
        raid.started = true
        raid.startCycle = clock.cycle
        raid.stageCycle = clock.cycle
        raid.stage = CoxStage.Upper
        raid.opened += raid.rooms.first().index
        val here = raid.active.filter { raid.contains(it.player.coords) }
        // Anyone of the party not inside yet stays out of this raid.
        raid.members.removeIf { it !in here }
        for (member in here) member.started = true
        raid.scaling =
            CoxScalingInputs(
                size = here.size.coerceAtLeast(1),
                maxCombat = here.maxOf { it.player.combatLevel },
                maxHitpoints = here.maxOf { it.player.statBase(stats.hitpoints) },
                averageMining = here.sumOf { it.player.statBase(stats.mining) } / here.size,
                challenge = raid.challenge,
            )
        for (member in here) {
            member.player.mes("The raid has begun!")
            hud.timer(raid, member.player)
        }
        eventBus.publish(CoxEvents.RaidStarted(raid))
        hud.refreshAll(raid)
        hud.membersAll(raid)
        return true
    }

    // Getting around
    // ---------------------------------------------------------------------------------------------

    fun isOpen(raid: CoxRaid, room: CoxPlacement): Boolean = room.index in raid.opened

    /** [room]'s encounter is done: its way on opens. */
    fun openRoom(raid: CoxRaid, room: CoxPlacement) {
        if (raid.opened.add(room.index)) {
            eventBus.publish(CoxEvents.RoomCompleted(raid, room))
        }
    }

    enum class PassResult {
        Moved,
        NotStarted,
        Blocked,
        NotHere,
    }

    /**
     * [player] enters the passage whose footprint is [footprint] (raid tiles): they come out on its
     * other side. A room's exit passage needs the room open; the floor's end room's passage (on its
     * way in) is always open.
     */
    fun passage(player: Player, footprint: List<CoordGrid>): PassResult {
        val raid = raidOf(player) ?: return PassResult.NotHere
        val room = raid.roomAt(footprint.first()) ?: return PassResult.NotHere
        if (room.type == CoxRoomType.RaidStart && !raid.started) return PassResult.NotStarted
        if (room.type != CoxRoomType.FloorEnd && !isOpen(raid, room)) return PassResult.Blocked
        val dest = across(player.coords, footprint) ?: return PassResult.NotHere
        fadeMove(player) { move(player, dest) }
        return PassResult.Moved
    }

    /** Walks [player] through the fire or tendrils covering [footprint] (raid tiles). */
    fun crossOver(player: Player, footprint: List<CoordGrid>): Boolean {
        val dest = across(player.coords, footprint) ?: return false
        move(player, dest)
        return true
    }

    /** The tile on the far side of a passage from [from], straight through its thin axis. */
    private fun across(from: CoordGrid, footprint: List<CoordGrid>): CoordGrid? {
        val minX = footprint.minOf { it.x }
        val maxX = footprint.maxOf { it.x }
        val minZ = footprint.minOf { it.z }
        val maxZ = footprint.maxOf { it.z }
        // Crossed along whichever axis the player stands off it; for a long thin loc, its thin one.
        val alongZ =
            when {
                from.z in minZ..maxZ && from.x !in minX..maxX -> true
                from.x in minX..maxX && from.z !in minZ..maxZ -> false
                else -> (maxX - minX) < (maxZ - minZ)
            }
        val candidates =
            if (alongZ) {
                // The passage runs north-south; it is crossed east-west.
                val z = from.z.coerceIn(minZ, maxZ)
                if (from.x <= minX) listOf(CoordGrid(maxX + 1, z, from.level))
                else listOf(CoordGrid(minX - 1, z, from.level))
            } else {
                val x = from.x.coerceIn(minX, maxX)
                if (from.z <= minZ) listOf(CoordGrid(x, maxZ + 1, from.level))
                else listOf(CoordGrid(x, minZ - 1, from.level))
            }
        return candidates.firstOrNull { walkable(it) } ?: candidates.firstOrNull()?.let(::nearOpen)
    }

    /** The hole at a floor's end: down to the next floor's start, or the Great Olm. */
    fun descend(player: Player): Boolean {
        val raid = raidOf(player) ?: return false
        val room = raid.roomAt(player.coords) ?: return false
        if (room.type != CoxRoomType.FloorEnd) return false
        val next = raid.layout.next(room)
        val dest =
            if (next == null) {
                nearOpen(raid.olmTile(CoxTiles.OLM_ENTRANCE))
            } else {
                landing(raid, next, CoxTiles.FLOOR_START_LANDING[next.variant])
            }
        reachFloor(raid, if (next == null) raid.layout.floors else next.floor)
        fadeMove(player) { move(player, dest) }
        return true
    }

    /** A floor's rope (or the Olm's): back up to the end of the floor above. */
    fun climb(player: Player): Boolean {
        val raid = raidOf(player) ?: return false
        val above =
            if (raid.inOlmChamber(player.coords)) {
                raid.rooms.last()
            } else {
                val room = raid.roomAt(player.coords) ?: return false
                if (room.type != CoxRoomType.FloorStart) return false
                raid.layout.previous(room) ?: return false
            }
        val tile = CoxTiles.FLOOR_END_LANDING[above.variant]
        val dest = landing(raid, above, tile)
        fadeMove(player) { move(player, dest) }
        return true
    }

    /** The party reached floor [floor] (the layout's floor count is the Olm). */
    private fun reachFloor(raid: CoxRaid, floor: Int) {
        val stage =
            when {
                floor >= raid.layout.floors -> CoxStage.Olm
                raid.challenge && floor == 1 -> CoxStage.Middle
                floor >= 1 -> CoxStage.Lower
                else -> CoxStage.Upper
            }
        if (stage.value <= raid.stage.value) return
        val ticks = clock.cycle - raid.stageCycle
        val level =
            when (raid.stage) {
                CoxStage.Upper -> "Upper"
                CoxStage.Middle -> "Middle"
                else -> "Lower"
            }
        broadcast(
            raid,
            "<col=ff289d>$level level complete! Duration: </col><col=ff0000>${time(ticks)}</col>",
        )
        raid.stage = stage
        raid.stageCycle = clock.cycle
        hud.refreshAll(raid)
    }

    /** Where a raider who dies now wakes: the start of the floor the party has reached. */
    fun respawnTile(raid: CoxRaid): CoordGrid {
        val floor =
            when (raid.stage) {
                CoxStage.Waiting,
                CoxStage.Upper -> 0
                CoxStage.Middle -> 1
                CoxStage.Lower -> raid.layout.floors - 1
                else -> raid.layout.floors
            }
        if (floor >= raid.layout.floors) return nearOpen(raid.olmTile(CoxTiles.OLM_ENTRANCE))
        val start = raid.layout.floor(floor).first()
        return if (start.type == CoxRoomType.RaidStart) {
            landing(raid, start, CoxTiles.RAID_START_SPAWN[start.variant])
        } else {
            landing(raid, start, CoxTiles.FLOOR_START_LANDING[start.variant])
        }
    }

    fun landing(raid: CoxRaid, room: CoxPlacement, template: CoordGrid?): CoordGrid {
        val tile =
            template ?: CoordGrid(room.type.sourceX(room.variant) + 16, room.type.sourceZ + 16, 0)
        return nearOpen(raid.regionOf(room, tile))
    }

    /** [tile], or the nearest tile round it that can be stood on. */
    fun nearOpen(tile: CoordGrid): CoordGrid {
        if (walkable(tile)) return tile
        for (radius in 1..4) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != radius) continue
                    val next = tile.translate(dx, dz)
                    if (walkable(next)) return next
                }
            }
        }
        return tile
    }

    fun walkable(tile: CoordGrid): Boolean =
        collision.isZoneValid(tile) && (collision[tile.x, tile.z, tile.level] and BLOCKED) == 0

    fun move(player: Player, dest: CoordGrid) {
        player.abortRoute()
        PathingEntityCommon.telejump(player, collision, dest)
        zoneActivity.flag(ZoneKey.from(dest))
    }

    /** Fades the screen out, runs [action] [fadeTicks] later, then fades back in. */
    fun fadeMove(player: Player, action: () -> Unit) {
        if (fadeTicks <= 0) {
            action()
            return
        }
        hud.fadeOut(player)
        player.abortRoute()
        player.delay = clock.cycle + fadeTicks
        moves += Move(clock.cycle + fadeTicks, player, action)
    }

    private fun tickMoves() {
        if (moves.isEmpty()) return
        val due = moves.filter { it.at <= clock.cycle }
        moves.removeAll(due.toSet())
        for (move in due) {
            if (!move.player.isSlotAssigned) continue
            move.action()
            hud.fadeIn(move.player)
        }
    }

    // Points
    // ---------------------------------------------------------------------------------------------

    /** Gives [amount] points to [player] if they started the raid. */
    fun addPoints(raid: CoxRaid, player: Player, amount: Int) {
        if (amount <= 0) return
        val member = raid.member(player) ?: return
        if (!member.started || member.left) return
        member.points += amount
        for (other in raid.active) hud.points(raid, other.player)
    }

    // Deaths
    // ---------------------------------------------------------------------------------------------

    fun died(player: Player) {
        val raid = raidOf(player) ?: return
        val member = raid.member(player) ?: return
        member.deaths++
        if (member.started) {
            val total = raid.totalPoints
            if (total > 0 && member.points * 20 < total) {
                // Under 5% of the total: the team loses 5% instead (spread over everyone).
                val loss = total / 20
                var left = loss
                for (other in raid.raiders.sortedByDescending { it.points }) {
                    val share =
                        minOf(other.points, (loss * other.points) / total.coerceAtLeast(1) + 1)
                    other.points -= minOf(share, left)
                    left -= minOf(share, left)
                    if (left <= 0) break
                }
            } else {
                member.points -= member.points * 40 / 100
            }
        }
        dropRaidItems(player, player.coords)
        broadcast(raid, "${player.displayName} has died.", except = player)
        respawning[player.accountId] = respawnTile(raid)
        for (other in raid.active) hud.points(raid, other.player)
    }

    fun respawnFor(player: Player): CoordGrid? = respawning.remove(player.accountId)

    /** Raid-only items fall where [player] stands (on death) or vanish (leaving). */
    fun dropRaidItems(player: Player, at: CoordGrid?) {
        for (inv in listOf(player.inv, player.worn)) {
            for (slot in 0 until inv.size) {
                val obj = inv[slot] ?: continue
                val type = objTypes[obj]
                if (!isRaidItem(type)) continue
                player.invDel(inv, type, obj.count, slot)
                if (at != null) objRepo.add(type, at, DROP_TICKS, player, obj.count)
            }
        }
        player.rebuildAppearance()
    }

    // Leaving
    // ---------------------------------------------------------------------------------------------

    fun leave(player: Player, why: CoxExit): Boolean {
        val raid = raids.values.firstOrNull { it.member(player)?.left == false } ?: return false
        val member = raid.member(player)!!
        member.left = true
        if (member.started) raid.droppedPoints += member.points
        member.points = 0
        respawning.remove(player.accountId)
        moves.removeAll { it.player === player }
        dropRaidItems(player, null)
        eventBus.publish(CoxEvents.PlayerLeft(raid, player))
        if (why != CoxExit.LoggedOut && why != CoxExit.Left && raid.contains(player.coords)) {
            move(player, CoxTiles.OUTSIDE)
        }
        hud.close(player)
        parties.partyOf(player)?.let { party ->
            if (party.raid === raid) parties.leave(party, player)
        }
        if (raid.active.isEmpty()) end(raid)
        else {
            hud.membersAll(raid)
            hud.refreshAll(raid)
        }
        return true
    }

    private fun end(raid: CoxRaid) {
        if (raid.ended) return
        raid.ended = true
        if (raid.party.raid === raid) {
            parties.disband(raid.party)
        }
        eventBus.publish(CoxEvents.RaidEnded(raid))
    }

    /** The Olm fell: the counts go up and the loot is rolled by the rewards. */
    fun olmDefeated(raid: CoxRaid) {
        if (raid.olmDefeated) return
        raid.olmDefeated = true
        raid.completed = true
        raid.completeCycle = clock.cycle
        raid.stage = CoxStage.Done
        val ticks = clock.cycle - raid.stageCycle
        val total = clock.cycle - raid.startCycle
        broadcast(
            raid,
            "<col=ff289d>Lower level complete! Duration: </col><col=ff0000>${time(ticks)}</col>",
        )
        broadcast(
            raid,
            "Congratulations - your raid is complete! Duration: <col=ff0000>${time(total)}</col>",
        )
        eventBus.publish(CoxEvents.OlmDefeated(raid))
        hud.refreshAll(raid)
    }

    private fun sweep() {
        for (raid in raids.values.toList()) {
            for (member in raid.active.toList()) {
                val online = players.any { it === member.player }
                if (!online) {
                    leave(member.player, CoxExit.LoggedOut)
                } else if (
                    !raid.contains(member.player.coords) &&
                        moves.none { it.player === member.player }
                ) {
                    leave(member.player, CoxExit.Left)
                }
            }
            if (raid.active.isEmpty()) end(raid)
            if (raid.ended) raids.remove(raid.id)
        }
    }

    /** Raiders' rooms: the first time someone walks into a room the room hears of it. */
    private fun trackRooms() {
        for (raid in raids.values) {
            if (raid.ended) continue
            for (member in raid.active) {
                val room = raid.roomAt(member.player.coords)
                if (room != null && room != member.room) {
                    member.room = room
                    eventBus.publish(CoxEvents.RoomEntered(raid, room, member.player))
                }
            }
        }
    }

    fun tick() {
        tickMoves()
        if (raids.isEmpty()) return
        trackRooms()
        if (clock.cycle % SWEEP_TICKS == 0) sweep()
    }

    fun broadcast(raid: CoxRaid, text: String, except: Player? = null) {
        for (member in raid.active) {
            if (member.player !== except) member.player.mes(text)
        }
    }

    companion object {
        private val OPEN_KINDS =
            setOf(CoxRoomKind.End, CoxRoomKind.Resources, CoxRoomKind.Scavengers, CoxRoomKind.Start)
        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        private const val SWEEP_TICKS = 5
        private const val DROP_TICKS = 200

        fun time(ticks: Int): String {
            val seconds = ticks * 6 / 10
            return "%d:%02d".format(seconds / 60, seconds % 60)
        }

        private val KEEP =
            setOf(
                "raids_reward_book",
                "raids_spellunlock_tablet",
                "raids_prayerscroll",
                "raids_prayerscroll_augury",
                "raids_prayerscroll_preserve",
                "raids_ancient_relic",
                "raids_challenge_morph",
            )

        /** Items that only exist inside the chambers (cache names `raids_*`, bar the rewards). */
        fun isRaidItem(type: UnpackedObjType): Boolean {
            val name = type.internalName ?: return false
            if (!name.startsWith("raids_") || name in KEEP) return false
            return !name.startsWith("raids_cluefest")
        }
    }
}

/** Tiles the raid binds to, on the cache's map (TSPS, from rsprox captures; unverified here). */
object CoxTiles {
    /** Where leaving puts you: at the foot of the entrance's steps. */
    val OUTSIDE = CoordGrid(1233, 3572, 0)

    /** The first room's arrival, by version (west, north, east turning). */
    val RAID_START_SPAWN =
        listOf(CoordGrid(3278, 5187, 0), CoordGrid(3298, 5188, 0), CoordGrid(3330, 5188, 0))

    /** The end room's tile by its hole, where the rope up from the floor below lands. */
    val FLOOR_END_LANDING = listOf(CoordGrid(3278, 5168, 0), CoordGrid(3308, 5173, 0), null)

    /** The floor start room's tile by its rope, where the hole from the floor above lands. */
    val FLOOR_START_LANDING =
        listOf(CoordGrid(3280, 5711, 0), CoordGrid(3312, 5711, 0), CoordGrid(3344, 5711, 0))

    /** The Olm's chamber (cache tiles): the rope's landing and the middle of the arena. */
    val OLM_ENTRANCE = CoordGrid(3232, 5720, 0)
    val OLM_CENTRE = CoordGrid(3232, 5734, 0)
}
