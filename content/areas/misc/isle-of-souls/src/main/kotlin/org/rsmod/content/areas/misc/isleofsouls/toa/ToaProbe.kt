package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.script.onCommand
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcTint
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The packed tombs as a bare team instance, for looking at the region in a client (`::instance
 * toa`) without starting a raid. Real coordinates are used for the entry point: the Nexus, where a
 * raid's players arrive first.
 */
object ToaProbeInstance {
    val type =
        TeamInstanceType(
            name = "toa",
            template = ToaPacking.template(),
            entry = ToaRoom.Nexus.spawn,
            exit = ToaIsle.exit,
        )
}

/**
 * Admin commands for trying the tombs without playing them:
 * - `::toa` takes you to the lobby.
 * - `::toatp room` moves you to a room of the raid (or of the bare instance) you are in.
 * - `::toaskip` clears the path you are on, or the Wardens when you are there.
 * - `::toawin [points] [level]` ends the raid as won: everyone gets the points (default 20,000) and
 *   the loot is rolled at the raid level given (default the invocations').
 * - `::toarooms` lists the room names.
 * - `::akkhatint [akkha|shadow|both hue sat light weight]` shows or changes the immune tint the
 *   Akkha fight uses (live, not saved).
 * - `::npctint hue sat light weight [ticks]` tints every npc near you, for trying values on
 *   anything (`::npctint off` clears it).
 * - `::toaroom` wins the puzzle, boss or Wardens room you stand in, as if the party had: the room's
 *   own completion runs (points, Osmumten, trophies, the next room).
 */
class ToaCommands
@Inject
constructor(
    private val instances: TeamInstances,
    private val raids: ToaRaids,
    private val collision: CollisionFlagMap,
    private val npcRegistry: NpcRegistry,
    private val rooms: ToaRoomRegistry,
) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(ToaProbeInstance.type)

        onCommand("toa") {
            modLevel = modlevels.admin
            desc = "Go to the Tombs of Amascut lobby"
            cheat {
                if (raids.raidOf(player) != null) {
                    player.mes("Leave your raid first.")
                    return@cheat
                }
                PathingEntityCommon.telejump(player, collision, ToaLobby.ENTRANCE)
            }
        }

        onCommand("toarooms") {
            modLevel = modlevels.admin
            desc = "List the Tombs of Amascut room names"
            cheat { player.mes(ToaRoom.entries.joinToString(", ") { it.name.lowercase() }) }
        }

        onCommand("toatp") {
            modLevel = modlevels.admin
            desc = "Move to a room of the Tombs of Amascut you are in"
            invalidArgs = "Use as ::toatp room (see ::toarooms)"
            cheat {
                val room =
                    ToaRoom.entries.firstOrNull { it.name.equals(args[0], ignoreCase = true) }
                if (room == null) {
                    player.mes("There is no room called ${args[0]}.")
                    return@cheat
                }
                val raid = raids.raidOf(player)
                val southWest =
                    raid?.region?.southWest
                        ?: instances
                            .instanceOf(player)
                            ?.takeIf { it.type === ToaProbeInstance.type }
                            ?.region
                            ?.southWest
                if (southWest == null) {
                    player.mes(
                        "You are not in the tombs. Use ::instance toa to look at the region."
                    )
                    return@cheat
                }
                val dest = ToaPacking.toRegion(room.spawn, southWest)
                if (dest == null || !collision.isZoneValid(dest)) {
                    player.mes("That room has no valid tile here.")
                    return@cheat
                }
                PathingEntityCommon.telejump(player, collision, dest)
                raid?.member(player)?.let { it.room = room }
            }
        }

        onCommand("toaskip") {
            modLevel = modlevels.admin
            desc = "Clear the path or the Wardens you are in"
            cheat {
                val raid = raids.raidOf(player)
                if (raid == null) {
                    player.mes("You are not in a raid.")
                    return@cheat
                }
                skip(player, raid)
            }
        }

        onCommand("toaroom") {
            modLevel = modlevels.admin
            desc = "Win the room you are in (puzzle, boss or Wardens)"
            cheat {
                val raid = raids.raidOf(player)
                val room = raid?.member(player)?.room
                if (raid == null || room == null || !room.hasFight) {
                    player.mes("Go into a puzzle, boss or Wardens room first.")
                    return@cheat
                }
                val runtime = rooms.ensure(raid, room)
                if (runtime == null) {
                    player.mes("That room has no content.")
                    return@cheat
                }
                runtime.start()
                when (runtime) {
                    is WardensRuntime -> runtime.skipToFinal()
                    is WardensFinalRuntime -> runtime.skipToEnd()
                    else -> runtime.complete()
                }
            }
        }

        onCommand("toawin") {
            modLevel = modlevels.admin
            desc = "Win the raid you are in: ::toawin [points] [raid level]"
            invalidArgs = "Use as ::toawin [points] [raid level]"
            cheat {
                val raid = raids.raidOf(player)
                if (raid == null) {
                    player.mes("You are not in a raid.")
                    return@cheat
                }
                val points = args.getOrNull(0)?.toIntOrNull() ?: DEFAULT_POINTS
                for (member in raid.active) {
                    raid.points.award(member.player.accountId, points)
                }
                args.getOrNull(1)?.toIntOrNull()?.let { raid.finalRaidLevel = it }
                raids.wardensDefeated(raid)
            }
        }

        onCommand("akkhatint") {
            modLevel = modlevels.admin
            desc = "Show or set the immune tint on Akkha and his shadows"
            invalidArgs = AKKHA_TINT_USAGE
            cheat {
                if (args.isEmpty()) {
                    player.mes("Akkha: ${describe(AkkhaTint.akkha)}")
                    player.mes("Shadows: ${describe(AkkhaTint.shadow)}")
                    player.mes(AKKHA_TINT_USAGE)
                    return@cheat
                }
                val target = args[0].lowercase()
                if (target !in setOf("akkha", "shadow", "both")) {
                    player.mes(AKKHA_TINT_USAGE)
                    return@cheat
                }
                val tint = parseTint(player, args.subList(1, 5)) ?: return@cheat
                if (target != "shadow") {
                    AkkhaTint.akkha = tint
                }
                if (target != "akkha") {
                    AkkhaTint.shadow = tint
                }
                player.mes("Akkha tint ($target) is now ${describe(tint)}.")
            }
        }

        onCommand("npctint") {
            modLevel = modlevels.admin
            desc =
                "Tint the npcs near you: ::npctint hue sat light weight [ticks], or ::npctint off"
            invalidArgs = NPC_TINT_USAGE
            cheat {
                val near = nearbyNpcs(player)
                if (args[0].equals("off", ignoreCase = true)) {
                    near.forEach { it.resetTint() }
                    player.mes("Cleared the tint on ${near.size} npcs.")
                    return@cheat
                }
                val tint = parseTint(player, args.subList(0, 4)) ?: return@cheat
                val ticks = args.getOrNull(4)?.toInt() ?: DEFAULT_TINT_TICKS
                if (ticks !in 1..NpcTint.MAX_DURATION) {
                    player.mes("Ticks must be 1 to ${NpcTint.MAX_DURATION}.")
                    return@cheat
                }
                near.forEach { it.tint(tint, ticks) }
                player.mes("Tinted ${near.size} npcs with ${describe(tint)} for $ticks ticks.")
            }
        }
    }

    /** Npcs within [TINT_RANGE] tiles of [player]. */
    private fun nearbyNpcs(player: Player): List<Npc> {
        val zones = TINT_RANGE / ZoneGrid.LENGTH + 1
        val found = ArrayList<Npc>()
        for (dx in -zones..zones) {
            for (dz in -zones..zones) {
                val zone =
                    ZoneKey.from(
                        player.coords.translate(dx * ZoneGrid.LENGTH, dz * ZoneGrid.LENGTH)
                    )
                npcRegistry
                    .findAll(zone)
                    .filter { it.isWithinDistance(player.coords, TINT_RANGE) }
                    .forEach(found::add)
            }
        }
        return found
    }

    /** Hue, saturation, lightness and weight from [values]; `-` (or -1) keeps a channel. */
    private fun parseTint(player: Player, values: List<String>): NpcTint? {
        val (hue, saturation, lightness) =
            values.take(3).map { if (it == "-") NpcTint.KEEP else it.toInt() }
        val weight = values[3].toInt()
        return try {
            NpcTint(hue, saturation, lightness, weight)
        } catch (e: IllegalArgumentException) {
            player.mes(e.message ?: "Those values are out of range.")
            null
        }
    }

    private fun describe(tint: NpcTint): String {
        fun channel(value: Int): String = if (value == NpcTint.KEEP) "-" else value.toString()
        return "hue ${channel(tint.hue)}, sat ${channel(tint.saturation)}, " +
            "light ${channel(tint.lightness)}, weight ${tint.weight}"
    }

    private fun skip(player: Player, raid: ToaRaid) {
        val room = raid.member(player)?.room
        when {
            room == ToaRoom.WardensFirst -> raids.advanceWardens(raid)
            room == ToaRoom.WardensFinal -> raids.wardensDefeated(raid)
            room?.path != null -> {
                val path = checkNotNull(room.path)
                raids.completeRoom(raid, path.puzzle)
                raids.completeRoom(raid, path.boss)
                raids.completePath(raid, path)
                for (member in raid.active) {
                    raids.returnToNexus(member.player)
                }
            }
            else -> player.mes("Go into a path or the Wardens first.")
        }
    }

    private companion object {
        const val DEFAULT_POINTS = 20_000
        const val DEFAULT_TINT_TICKS = 100
        const val TINT_RANGE = 15
        const val AKKHA_TINT_USAGE =
            "Use as ::akkhatint akkha|shadow|both hue(0-63) sat(0-7) light(0-127) " +
                "weight(1-127); - keeps a channel"
        const val NPC_TINT_USAGE =
            "Use as ::npctint hue(0-63) sat(0-7) light(0-127) weight(1-127) [ticks], " +
                "or ::npctint off; - keeps a channel"
    }
}
