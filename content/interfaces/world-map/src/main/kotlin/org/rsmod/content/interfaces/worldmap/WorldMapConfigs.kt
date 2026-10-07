package org.rsmod.content.interfaces.worldmap

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.timer.TimerReferences

typealias world_map_interfaces = WorldMapInterfaces

typealias world_map_components = WorldMapComponents

typealias world_map_timers = WorldMapTimers

object WorldMapInterfaces : InterfaceReferences() {
    val worldmap = find("worldmap")
}

object WorldMapComponents : ComponentReferences() {
    val orb = find("orbs:worldmap")
    val close = find("worldmap:close")
}

object WorldMapTimers : TimerReferences() {
    val you_are_here = find("worldmap_you_are_here")
}
