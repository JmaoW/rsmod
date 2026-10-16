package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.synths
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.type.content.ContentGroupType
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.synth.SynthType

/** The dungeons' doors and gates, by the cache's own names. */
object DungeonDoorLocs : LocReferences() {
    // Doors that have an open version in the cache: the generic door script swings them.
    val prison_door = find("prisondoor")
    val prison_door_open = find("prisondooropen")
    val prison_door_left = find("prisondoor_l")
    val prison_door_left_open = find("prisondooropen_l")
    val prison_door_right = find("prisondoor_r")
    val prison_door_right_open = find("prisondooropen_r")
    val tower_door = find("slayertower_small_door")
    val tower_door_open = find("slayertower_small_door_open")
    val tower_door_left = find("slayertower_small_door_l")
    val tower_door_left_open = find("slayertower_small_door_open_l")
    val tower_door_right = find("slayertower_small_door_r")
    val tower_door_right_open = find("slayertower_small_door_open_r")
    val odd_wall_left = find("oddwall_l")
    val odd_wall_left_open = find("openoddwall_l")
    val odd_wall_right = find("oddwall_r")
    val odd_wall_right_open = find("openoddwall_r")

    // Metal gates, a left and a right half, swung like double doors (from the middle, outwards).
    val metal_gate_left = find("metalgateclosedl")
    val metal_gate_left_open = find("metalgateopenl")
    val metal_gate_right = find("metalgateclosedr")
    val metal_gate_right_open = find("metalgateopenr")

    // Doors and gates with no open version: using one takes it away for a while.
    val member_gate_left = find("membergatel")
    val member_gate_right = find("membergater")
    val cauldron_door = find("cauldrondoor")
    val cauldron_door_left = find("cauldrondoor_l")
    val deep_dungeon_gate = find("deepdungeondoor")
    val dungeon_jail_door = find("dungeonjail")
    val temple_door_west = find("hosdun_west_door")
    val temple_door_east = find("hosdun_east_door")

    // The Forthos Dungeon's locked door, which Thieving opens.
    val grubby_door = find("hosdun_grubby_door_locked")
    val grubby_door_open = find("hosdun_grubby_door_open")

    // Spider webs, slashed apart.
    val web = find("bigweb_slashable")

    // Brimhaven's vines, chopped down.
    val vine_1 = find("karam_dungeon_vineblocking1")
    val vine_2 = find("karam_dungeon_vineblocking2")
    val vine_3 = find("karam_dungeon_vineblocking3")
    val vine_4 = find("karam_dungeon_vineblocking4")
    val vine_5 = find("karam_dungeon_vineblocking5")
}

/**
 * Gives the dungeons' doors the open versions the cache has for them, so the generic door and
 * picket-gate scripts swing them like any other. Needs `packCache`.
 */
internal object DungeonDoorEdits : LocEditor() {
    init {
        door(DungeonDoorLocs.prison_door, DungeonDoorLocs.prison_door_open)
        leftDoor(DungeonDoorLocs.prison_door_left, DungeonDoorLocs.prison_door_left_open)
        rightDoor(DungeonDoorLocs.prison_door_right, DungeonDoorLocs.prison_door_right_open)
        door(DungeonDoorLocs.tower_door, DungeonDoorLocs.tower_door_open)
        leftDoor(DungeonDoorLocs.tower_door_left, DungeonDoorLocs.tower_door_left_open)
        rightDoor(DungeonDoorLocs.tower_door_right, DungeonDoorLocs.tower_door_right_open)
        leftDoor(DungeonDoorLocs.odd_wall_left, DungeonDoorLocs.odd_wall_left_open)
        rightDoor(DungeonDoorLocs.odd_wall_right, DungeonDoorLocs.odd_wall_right_open)

        leftDoor(
            DungeonDoorLocs.metal_gate_left,
            DungeonDoorLocs.metal_gate_left_open,
            synths.picketgate_open,
            synths.picketgate_close,
        )
        rightDoor(
            DungeonDoorLocs.metal_gate_right,
            DungeonDoorLocs.metal_gate_right_open,
            synths.picketgate_open,
            synths.picketgate_close,
        )
    }

    private fun door(closed: LocType, opened: LocType) {
        edit(closed) {
            param[params.next_loc_stage] = opened
            contentGroup = content.closed_single_door
        }
        edit(opened) {
            param[params.next_loc_stage] = closed
            contentGroup = content.opened_single_door
        }
    }

    /**
     * A left half of a double door: it and its right half swing apart, hinged at the outer ends.
     */
    private fun leftDoor(
        closed: LocType,
        opened: LocType,
        openSound: SynthType? = null,
        closeSound: SynthType? = null,
    ) =
        pair(
            closed,
            opened,
            content.closed_left_door,
            content.opened_left_door,
            openSound,
            closeSound,
        )

    private fun rightDoor(
        closed: LocType,
        opened: LocType,
        openSound: SynthType? = null,
        closeSound: SynthType? = null,
    ) =
        pair(
            closed,
            opened,
            content.closed_right_door,
            content.opened_right_door,
            openSound,
            closeSound,
        )

    private fun pair(
        closed: LocType,
        opened: LocType,
        closedGroup: ContentGroupType,
        openedGroup: ContentGroupType,
        openSound: SynthType?,
        closeSound: SynthType?,
    ) {
        edit(closed) {
            param[params.next_loc_stage] = opened
            // The double door script plays the open sound on both opening and closing.
            openSound?.let { param[params.opensound] = it }
            contentGroup = closedGroup
        }
        edit(opened) {
            param[params.next_loc_stage] = closed
            (closeSound ?: openSound)?.let { param[params.opensound] = it }
            closeSound?.let { param[params.closesound] = it }
            contentGroup = openedGroup
        }
    }
}
