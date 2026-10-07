package org.rsmod.content.areas.misc.isleofsouls.instances

import jakarta.inject.Inject
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * A kind of instance a team can own, such as the Fight Caves. Register it with
 * [TeamInstances.register] when the server starts.
 *
 * [entry] and [exit] are ordinary map coordinates: [entry] must lie inside a zone that [template]
 * copies (the player is placed at the matching spot in their own copy), and [exit] is where players
 * are sent when they leave, and where a player who was saved inside an instance wakes up.
 */
class TeamInstanceType(
    val name: String,
    val template: RegionTemplate,
    val entry: CoordGrid,
    val exit: CoordGrid,
)

/** A live copy of a [TeamInstanceType]. [owner] is the team (or lone player) it belongs to. */
class TeamInstance
internal constructor(val owner: String, val type: TeamInstanceType, val region: Region) {
    /** The engine frees a region once it is empty, which marks its slot as invalid. */
    val registered: Boolean
        get() = region.slot != Region.INVALID_SLOT

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.x < region.northEast.x &&
            coords.z >= region.southWest.z &&
            coords.z < region.northEast.z
}

enum class EnterResult {
    Entered,
    AlreadyInside,

    /** No free region slot was available. */
    NoSpace,

    /** The type's entry is not part of its template, or does not exist in the map. */
    InvalidEntry,
}

/**
 * Hands out one instance per team for each [TeamInstanceType], so both teammates enter the same
 * copy and can come and go while someone keeps it alive.
 *
 * An instance lives for as long as any player is inside it. When everyone has left, the next entry
 * creates a fresh copy instead of resuming a half-finished one, and the engine reclaims the empty
 * region the next time it needs a slot. Players who are not on a team get an instance of their own.
 * Everything here runs on the game thread.
 */
class TeamInstances
@Inject
constructor(
    private val teams: LeagueTeams,
    private val players: PlayerList,
    private val regionRepo: RegionRepository,
    private val collision: CollisionFlagMap,
    private val zoneActivity: ZonePlayerActivityBitSet,
) {
    private data class Key(val owner: String, val type: String)

    private val types = linkedMapOf<String, TeamInstanceType>()
    private val instances = linkedMapOf<Key, TeamInstance>()

    val registeredTypes: Collection<TeamInstanceType>
        get() = types.values

    fun register(type: TeamInstanceType) {
        require(type.name !in types) { "Instance type '${type.name}' is already registered." }
        types[type.name] = type
    }

    fun type(name: String): TeamInstanceType? = types[name]

    /**
     * Where a player saved inside an instance should wake up. Instances never survive a logout or a
     * restart, and the client builds its first map from the saved position, so those players must
     * be moved before they log in. `null` until a type is registered.
     */
    fun fallbackExit(): CoordGrid? = types.values.firstOrNull()?.exit

    /** Every instance that someone is currently inside. */
    fun live(): List<TeamInstance> {
        instances.values.removeAll { !isActive(it) }
        return instances.values.toList()
    }

    /** The instance [player] is standing in, if any. */
    fun instanceOf(player: Player): TeamInstance? =
        instances.values.firstOrNull { it.registered && it.contains(player.coords) }

    fun playersIn(instance: TeamInstance): List<Player> =
        players.filter { instance.contains(it.coords) }

    /** Puts [player] into their team's instance of [type], creating it if nobody is in one. */
    fun enter(player: Player, type: TeamInstanceType): EnterResult {
        val key = Key(ownerOf(player), type.name)

        var instance = instances[key]
        if (instance != null && !isActive(instance)) {
            instances.remove(key)
            instance = null
        }
        if (instance != null && instance.contains(player.coords)) {
            return EnterResult.AlreadyInside
        }

        if (instance == null) {
            val region = regionRepo.add(type.template) ?: return EnterResult.NoSpace
            instance = TeamInstance(key.owner, type, region)
            instances[key] = instance
        }

        val dest =
            runCatching { instance.region.normal[type.entry] }.getOrNull()
                ?: return EnterResult.InvalidEntry
        if (!collision.isZoneValid(dest)) {
            return EnterResult.InvalidEntry
        }
        PathingEntityCommon.telejump(player, collision, dest)

        // The engine only marks a zone as occupied when it next processes the player, and a region
        // that looks empty is reclaimed as soon as any other region is created. Without this,
        // another
        // team entering in the same tick could delete this instance from under its first player.
        zoneActivity.flag(ZoneKey.from(dest))
        return EnterResult.Entered
    }

    /** Sends [player] out of the instance they are in to its exit. `false` if they are in none. */
    fun leave(player: Player): Boolean {
        val instance = instanceOf(player) ?: return false
        val exit = instance.type.exit
        if (!collision.isZoneValid(exit)) {
            return false
        }
        PathingEntityCommon.telejump(player, collision, exit)
        return true
    }

    private fun isActive(instance: TeamInstance): Boolean =
        instance.registered && players.any { instance.contains(it.coords) }

    private fun ownerOf(player: Player): String {
        val team = teams.teamOf(player)
        return if (team != null) "team:${team.id}" else "solo:${player.accountId}"
    }
}
