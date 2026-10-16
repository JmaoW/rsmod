package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid

object CrossingSeqs : SeqReferences() {
    val gong = find("godwars_hammer_gong_hammer")
    val crossbow_swing = find("dorgesh_xbow_swing")
    val pillar_swing = find("dorgesh_grapple_swing_reverse")
    val swim = find("godwars_human_swim_double")
}

object CrossingSpotanims : SpotanimReferences() {
    val grapple = find("dorgesh_grapple_spot")
}

object CrossingObjs : ObjReferences() {
    val hammer = find("hammer")
    val rope = find("rope")
    val grapple_bolt = find("xbows_grapple_tip_bolt_mithril_rope")
}

/** The god's chamber door, closed, and the two pieces of it that show while it swings open. */
object DoorLocs : LocReferences() {
    val armadyl = find("godwars_dungeon_door_armadyl")
    val bandos = find("godwars_dungeon_door_bandos")
    val saradomin = find("godwars_dungeon_door_saradomin")
    val zamorak = find("godwars_dungeon_door_zamorak")
    val open = find("godwars_dungeon_door_open")
    val open_visible = find("godwars_dungeon_door_open_visible")
}

/**
 * Whether the God Wars crossings ask for the items the Wiki lists, switched in
 * `.data/league/event-settings.json` with `"godWarsCrossingItems": true` or, until the next
 * restart, with `::gwditems on|off`. **Off** (the default): the crossings only need their levels.
 * **On:**
 * - Bandos' big door needs a hammer to ring the gong;
 * - Armadyl's pillar needs a crossbow and a mithril grapple bolt (with its rope) worn;
 * - Saradomin's rocks start untied, and tying one takes (and spends) a rope.
 */
object GodWarsCrossingItems {
    @Volatile var required: Boolean = false
}

/** The God Wars part of `event-settings.json` (the other event scripts read the same file). */
class GodWarsSettings(val crossingItems: Boolean = false) {
    companion object {
        fun load(dir: Path = LeagueFiles.DATA_DIR): GodWarsSettings {
            val file = dir.resolve(FightCaveSettings.FILE)
            if (!Files.exists(file)) {
                return GodWarsSettings()
            }
            return try {
                parse(Files.readString(file))
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                        "Fix or move the file and start the server again.",
                    e,
                )
            }
        }

        fun parse(text: String): GodWarsSettings {
            val root = ObjectMapper().readTree(text)
            if (root == null || !root.isObject) {
                throw IllegalArgumentException("the file must hold a JSON object")
            }
            val raw = root.get("godWarsCrossingItems") ?: return GodWarsSettings()
            if (!raw.isBoolean) {
                throw IllegalArgumentException(
                    "godWarsCrossingItems must be true or false, not $raw"
                )
            }
            return GodWarsSettings(raw.asBoolean())
        }
    }
}

/**
 * The three crossings that have a sequence of their own, with the captured animations, sounds and
 * timings (rsprox; ticks are counted from the click):
 * - **Bandos' big door.** Going in, the player faces the door and bangs the gong with a hammer; the
 *   door sounds at +3 and the player steps through. From the other side the door just opens.
 * - **Armadyl's pillar.** The player fires a mithril grapple from a crossbow, the pillar swings and
 *   two exact moves (at +4 and +5) carry the player over.
 * - **Zamorak's ice bridge.** A swim under the bridge: into the water at +2, out at +8. Going north
 *   (into Zamorak's side) the evil there leaves no Prayer points.
 *
 * The level rules are checked before these run; the item rules are [GodWarsCrossingItems].
 */
class GodWarsCrossings
@Inject
constructor(
    private val worldRepo: WorldRepository,
    private val locRepo: LocRepository,
    private val objTypes: ObjTypeList,
) {
    /** Whether the player may start [crossing]; tells them what they lack if not. */
    fun ProtectedAccess.hasItems(crossing: GateCrossing): Boolean {
        if (!GodWarsCrossingItems.required) {
            return true
        }
        return when (crossing) {
            GateCrossing.GONG -> {
                val has = invTotal(inv, CrossingObjs.hammer) > 0
                if (!has) mes("You need a hammer to bang the gong.")
                has
            }
            GateCrossing.PILLAR -> {
                val objs = player.worn.objs.filterNotNull()
                val crossbow = objs.any { objTypes[it].internalName?.contains("crossbow") == true }
                val bolt = objs.any { it.id == CrossingObjs.grapple_bolt.id }
                if (!(crossbow && bolt)) {
                    mes("You need a mithril grapple-tipped bolt with a rope to do that.")
                }
                crossbow && bolt
            }
            GateCrossing.BRIDGE -> true
        }
    }

    suspend fun ProtectedAccess.cross(
        gate: DungeonGate,
        crossing: GateCrossing,
        forward: Boolean,
        loc: BoundLocInfo,
    ) {
        when (crossing) {
            GateCrossing.GONG -> gong(gate, forward)
            GateCrossing.PILLAR -> pillar(gate, forward, loc)
            GateCrossing.BRIDGE -> bridge(forward)
        }
    }

    private suspend fun ProtectedAccess.gong(gate: DungeonGate, forward: Boolean) {
        if (!forward) {
            delay(1)
            telejump(gate.a)
            return
        }
        faceDirection(Direction.West)
        anim(CrossingSeqs.gong)
        delay(3)
        soundSynth(GodWarsSynths.gong_door)
        telejump(gate.b)
    }

    private suspend fun ProtectedAccess.pillar(
        gate: DungeonGate,
        forward: Boolean,
        loc: BoundLocInfo,
    ) {
        val dest = if (forward) gate.b else gate.a
        val facing = if (forward) constants.em_face_south else constants.em_face_north
        faceDirection(if (forward) Direction.South else Direction.North)
        mes("You fire your grapple at the pillar...")
        delay(1)
        anim(CrossingSeqs.crossbow_swing, delay = 6)
        spotanim(CrossingSpotanims.grapple, delay = 6, height = 82)
        soundSynth(GodWarsSynths.grapple_fire)
        soundSynth(GodWarsSynths.grapple_swing, delay = 6)
        worldRepo.locAnim(loc, CrossingSeqs.pillar_swing)
        delay(3)
        val middle = CoordGrid(PILLAR_X, PILLAR_MIDDLE_Z, dest.level)
        exactMove(coords, middle, 15, 29, facing)
        delay(1)
        exactMove(middle, dest, 0, 21, facing)
        delay(1)
        soundSynth(GodWarsSynths.grapple_land)
        mes("...and swing safely to the other side.")
    }

    private suspend fun ProtectedAccess.bridge(forward: Boolean) {
        // Forward is northbound: from the southern bank to Zamorak's side.
        val level = coords.level
        val water = CoordGrid(BRIDGE_X, if (forward) SOUTH_WATER_Z else NORTH_WATER_Z, level)
        val landing = CoordGrid(BRIDGE_X, if (forward) NORTH_BANK_Z else SOUTH_BANK_Z, level)
        faceDirection(if (forward) Direction.North else Direction.South)
        delay(2)
        telejump(water)
        anim(CrossingSeqs.swim)
        soundSynth(GodWarsSynths.swim)
        delay(5)
        telejump(landing)
        mes("Dripping, you climb out of the water.")
        if (forward) {
            mes("The extreme evil of this area leaves your Prayer drained.")
            statSub(stats.prayer, 255, 0)
        }
    }

    /**
     * Swings [god]'s chamber door open for two ticks, as captured: the closed door is taken away
     * and an invisible open door and its visible swung leaf stand in its place.
     */
    fun swingDoor(god: God) {
        val door = DOORS.getValue(god)
        val closed = locRepo.findExact(door.coords, door.closed) ?: return
        if (!locRepo.del(closed, DOOR_TICKS)) {
            return
        }
        locRepo.add(
            door.coords,
            DoorLocs.open,
            DOOR_TICKS,
            LocAngle[door.openAngle],
            LocShape.CentrepieceStraight,
        )
        locRepo.add(
            door.coords,
            DoorLocs.open_visible,
            DOOR_TICKS,
            LocAngle[door.leafAngle],
            LocShape.WallStraight,
        )
    }

    private class Door(
        val closed: LocType,
        val coords: CoordGrid,
        val openAngle: Int,
        val leafAngle: Int,
    )

    companion object {
        const val DOOR_TICKS = 2

        const val PILLAR_X = 2872
        const val PILLAR_MIDDLE_Z = 5274

        const val BRIDGE_X = 2885
        const val SOUTH_BANK_Z = 5333
        const val SOUTH_WATER_Z = 5334
        const val NORTH_WATER_Z = 5344
        const val NORTH_BANK_Z = 5345

        private val DOORS =
            mapOf(
                God.BANDOS to Door(DoorLocs.bandos, CoordGrid(2863, 5354, 2), 0, 1),
                God.ARMADYL to Door(DoorLocs.armadyl, CoordGrid(2839, 5295, 2), 3, 0),
                God.SARADOMIN to Door(DoorLocs.saradomin, CoordGrid(2908, 5265, 0), 2, 3),
                God.ZAMORAK to Door(DoorLocs.zamorak, CoordGrid(2925, 5332, 2), 1, 2),
            )
    }
}
