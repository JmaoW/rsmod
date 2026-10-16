package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid

/**
 * A general's room as it exists once: its [chamber] and the [instance] of it, `null` for the public
 * one.
 */
class GodRoom(val chamber: GodChamber, val instance: TeamInstance?) {
    val isPrivate: Boolean
        get() = instance != null

    override fun equals(other: Any?): Boolean =
        other is GodRoom && other.chamber === chamber && other.instance === instance

    override fun hashCode(): Int =
        31 * System.identityHashCode(chamber) + (instance?.hashCode() ?: 0)
}

/**
 * A monster that stands in a room: the npc's cache name and its tile in the real (public) chamber.
 */
class RoomSpawn(val name: String, val at: CoordGrid)

/**
 * One general's room, as a copy of the real map: the [gate] is its door (outside the door is where
 * a private room's players come out), the [blocks] are the zones copied, and the [spawns] are what
 * stands in it.
 */
class GodRoomDef(
    val key: String,
    val god: God,
    val chamber: GodChamber,
    val gate: DungeonGate,
    private val zoneX: Int,
    private val zoneZ: Int,
    private val zoneWidth: Int,
    private val zoneLength: Int,
    val spawns: List<RoomSpawn>,
) {
    val template: RegionTemplate =
        RegionTemplate.create {
            copy(zoneX, zoneZ, chamber.level) {
                this.zoneWidth = this@GodRoomDef.zoneWidth
                this.zoneLength = this@GodRoomDef.zoneLength
            }
        }

    /** The kind of instance a team's private copy of this room is. */
    val type = TeamInstanceType("godwars_$key", template, entry = gate.b, exit = gate.a)
}

/**
 * The generals' private rooms: each team gets its own copy of a general's chamber (the door's "Open
 * (private)" option), shared by the whole team through [TeamInstances], while the door's "Open
 * (normal)" option still leads to the one public chamber.
 *
 * A private room is made when the first player goes in: the general and the bodyguards are spawned
 * in it (they come back after they die, like the public ones) and it lasts as long as someone is
 * inside. When the last player leaves, [sweep] removes everything that stands in it, and the next
 * team entry makes a fresh one.
 *
 * Every boss script asks this class where a player or npc is, because the same chamber exists at
 * its normal coordinates (the public room) and at the instance's coordinates (a private one): see
 * [roomOf], [toNormal] and [toReal].
 */
@Singleton
class GodRooms
@Inject
constructor(
    private val instances: TeamInstances,
    private val players: PlayerList,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) {
    val defs: List<GodRoomDef> =
        listOf(
            GodRoomDef(
                key = "armadyl",
                god = God.ARMADYL,
                chamber = GodChambers.armadyl,
                gate = DungeonGates.armadylChamber,
                zoneX = 352,
                zoneZ = 661,
                zoneWidth = 4,
                zoneLength = 3,
                spawns =
                    listOf(
                        RoomSpawn(GodWarsNames.KREE, CoordGrid(2832, 5302, 2)),
                        RoomSpawn(GodWarsNames.SKREE, CoordGrid(2840, 5303, 2)),
                        RoomSpawn(GodWarsNames.GEERIN, CoordGrid(2828, 5299, 2)),
                        RoomSpawn(GodWarsNames.KILISA, CoordGrid(2833, 5297, 2)),
                    ),
            ),
            GodRoomDef(
                key = "bandos",
                god = God.BANDOS,
                chamber = GodChambers.bandos,
                gate = DungeonGates.bandosChamber,
                zoneX = 357,
                zoneZ = 668,
                zoneWidth = 3,
                zoneLength = 4,
                spawns =
                    listOf(
                        RoomSpawn(GodWarsNames.GRAARDOR, CoordGrid(2872, 5358, 2)),
                        RoomSpawn(GodWarsNames.STRONGSTACK, CoordGrid(2866, 5358, 2)),
                        RoomSpawn(GodWarsNames.STEELWILL, CoordGrid(2872, 5352, 2)),
                        RoomSpawn(GodWarsNames.GRIMSPIKE, CoordGrid(2868, 5362, 2)),
                    ),
            ),
            GodRoomDef(
                key = "saradomin",
                god = God.SARADOMIN,
                chamber = GodChambers.saradomin,
                gate = DungeonGates.saradominChamber,
                zoneX = 360,
                zoneZ = 657,
                zoneWidth = 4,
                zoneLength = 3,
                spawns =
                    listOf(
                        RoomSpawn(GodWarsNames.ZILYANA, CoordGrid(2897, 5269, 0)),
                        RoomSpawn(GodWarsNames.STARLIGHT, CoordGrid(2903, 5261, 0)),
                        RoomSpawn(GodWarsNames.GROWLER, CoordGrid(2896, 5264, 0)),
                        RoomSpawn(GodWarsNames.BREE, CoordGrid(2902, 5274, 0)),
                    ),
            ),
            GodRoomDef(
                key = "zamorak",
                god = God.ZAMORAK,
                chamber = GodChambers.zamorak,
                gate = DungeonGates.zamorakChamber,
                zoneX = 364,
                zoneZ = 664,
                zoneWidth = 4,
                zoneLength = 3,
                spawns =
                    listOf(
                        RoomSpawn(GodWarsNames.KRIL, CoordGrid(2925, 5322, 2)),
                        RoomSpawn(GodWarsNames.TSTANON, CoordGrid(2932, 5328, 2)),
                        RoomSpawn(GodWarsNames.ZAKLN, CoordGrid(2919, 5327, 2)),
                        RoomSpawn(GodWarsNames.BALFRUG, CoordGrid(2921, 5319, 2)),
                    ),
            ),
        )

    private val byGate: Map<DungeonGate, GodRoomDef> = defs.associateBy { it.gate }
    private val typeNames: Set<String> = defs.mapTo(hashSetOf()) { it.type.name }

    /** What each live private room has spawned, to remove when it empties. */
    private val spawned = IdentityHashMap<TeamInstance, MutableList<Npc>>()

    init {
        for (def in defs) {
            instances.register(def.type)
        }
    }

    fun defOf(gate: DungeonGate): GodRoomDef? = byGate[gate]

    /** The private room [player] is standing in, if any. */
    fun instanceOf(player: Player): TeamInstance? =
        instances.instanceOf(player)?.takeIf { it.type.name in typeNames }

    /** Where in the real map the room-instance tile [coords] is, or [coords] if it is in none. */
    fun toNormal(coords: CoordGrid): CoordGrid {
        val instance = godInstanceAt(coords) ?: return coords
        return toNormal(instance, coords)
    }

    private fun toNormal(instance: TeamInstance, real: CoordGrid): CoordGrid {
        val entry = instance.type.entry
        val entryReal = instance.region.normal[entry]
        return CoordGrid(
            real.x - (entryReal.x - entry.x),
            real.z - (entryReal.z - entry.z),
            real.level,
        )
    }

    /**
     * Where the real-map tile [normal] is in [room]'s copy of the map (itself for the public room).
     */
    fun toReal(room: GodRoom, normal: CoordGrid): CoordGrid {
        val instance = room.instance ?: return normal
        return instance.region.normal[normal]
    }

    private fun godInstanceAt(coords: CoordGrid): TeamInstance? =
        instances.live().firstOrNull { it.type.name in typeNames && it.contains(coords) }

    /**
     * The room of [chamber] that [coords] is in: the public one, or a private copy, or `null` if
     * they are in neither.
     */
    fun roomOf(chamber: GodChamber, coords: CoordGrid): GodRoom? {
        val instance = godInstanceAt(coords)
        val normal = if (instance == null) coords else toNormal(instance, coords)
        if (!chamber.contains(normal)) {
            return null
        }
        if (instance != null && instance.type.name != typeNameOf(chamber)) {
            return null
        }
        return GodRoom(chamber, instance)
    }

    private fun typeNameOf(chamber: GodChamber): String? =
        defs.firstOrNull { it.chamber === chamber }?.type?.name

    /** Whether [a] and [b] are both in the same room of [chamber]. */
    fun sameRoom(chamber: GodChamber, a: CoordGrid, b: CoordGrid): Boolean {
        val first = roomOf(chamber, a) ?: return false
        return first == roomOf(chamber, b)
    }

    fun playersIn(room: GodRoom): List<Player> =
        players.filter { it.isValidTarget() && roomOf(room.chamber, it.coords) == room }

    fun isEmpty(room: GodRoom): Boolean = playersIn(room).isEmpty()

    /**
     * A boss's timer check. Returns the room [npc] is in, or `null` if it has been removed: a
     * private room's monster goes when nobody is in the room, and so does one whose room was
     * already reclaimed by the engine.
     */
    fun checkIn(npc: Npc, chamber: GodChamber): GodRoom? {
        val room = roomOf(chamber, npc.coords)
        if (room == null) {
            if (RegionRegistry.inWorkingArea(npc.coords)) {
                remove(npc)
            }
            return null
        }
        if (room.isPrivate && isEmpty(room)) {
            sweep()
            return null
        }
        return room
    }

    /** Puts [player] into their team's private room for [def], making the room if it is new. */
    fun enterPrivate(player: Player, def: GodRoomDef): EnterResult {
        sweep()
        val result = instances.enter(player, def.type)
        if (result == EnterResult.Entered) {
            val instance = checkNotNull(instances.instanceOf(player))
            if (instance !in spawned) {
                spawned[instance] = spawnMonsters(def, instance)
            }
        }
        return result
    }

    /** Takes [player] out of the private room they are in. Returns `false` if they are in none. */
    fun leave(player: Player): Boolean {
        if (instanceOf(player) == null) {
            return false
        }
        val left = instances.leave(player)
        sweep(except = player)
        return left
    }

    private fun spawnMonsters(def: GodRoomDef, instance: TeamInstance): MutableList<Npc> {
        val made = mutableListOf<Npc>()
        for (spawn in def.spawns) {
            val type = GodWarsRoster.byName.getValue(spawn.name)
            val npc = Npc(npcTypes[type], instance.region.normal[spawn.at])
            // Permanent, so they come back after they die like the public ones do.
            npcRepo.add(npc, Int.MAX_VALUE)
            made += npc
        }
        return made
    }

    /**
     * Removes the monsters of every private room with nobody in it. [except] is a player who is
     * leaving and should not count (they are still online during their logout).
     */
    fun sweep(except: Player? = null) {
        val iterator = spawned.entries.iterator()
        while (iterator.hasNext()) {
            val (instance, npcs) = iterator.next()
            val live = instance.registered && instances.playersIn(instance).any { it !== except }
            if (!live) {
                npcs.forEach(::remove)
                iterator.remove()
            }
        }
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** How many players are in the public room of [def]'s chamber. */
    fun publicCount(def: GodRoomDef): Int = playersIn(GodRoom(def.chamber, null)).size
}
