package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

typealias isle_components = IsleOfSoulsComponents

typealias isle_interfaces = IsleOfSoulsInterfaces

typealias isle_varbits = IsleOfSoulsVarBits

typealias isle_varps = IsleOfSoulsVarps

object IsleOfSoulsComponents : ComponentReferences() {
    val panel_info = find("league_side_panel:info")
    val panel_summary = find("league_side_panel:summary")
    val panel_rank = find("league_side_panel:rank")
    val panel_masteries = find("league_side_panel:mastery_button")
    val panel_tasks = find("league_side_panel:tasks_button")
    val panel_areas = find("league_side_panel:areas_button")
    val panel_relics = find("league_side_panel:relics_button")

    val tasks_close = find("league_tasks:close_button")
    val relics_close = find("league_relics:close_button")
    val areas_close = find("trailblazer_areas:close_button")
    val masteries_close = find("league_combat_mastery:close_button")
    val rank_close = find("league_rank:close_button")
    val info_close = find("league_info:close_button")

    /** Layer that holds the dynamically-built frame (and close button) of `league_summary`. */
    val summary_frame = find("league_summary:frame")

    /** The panel's progress bar graphic, which `league_side_panel_update_bar` redraws. */
    val panel_fill = find("league_side_panel:fill")

    /** The top-level overlay slot that hosts the notification pop-up. */
    val toplevel_notifications = find("toplevel_osrs_stretch:notifications")
}

object IsleOfSoulsInterfaces : InterfaceReferences() {
    val league_side_panel = find("league_side_panel")
    val notification_display = find("notification_display")
    val league_tasks = find("league_tasks")
    val league_relics = find("league_relics")
    val league_info = find("league_info")
    val league_summary = find("league_summary")
    val league_rank = find("league_rank")
    val league_combat_mastery = find("league_combat_mastery")
    val league_areas = find("trailblazer_areas")
}

object IsleOfSoulsVarBits : VarBitReferences() {
    val league_type = find("league_type")
    val league_tutorial_completed = find("league_tutorial_completed")
}

object IsleOfSoulsVarps : VarpReferences() {
    /** The client's cached copy of the current world's flags (see `league_world` clientscript). */
    val map_flags_cached = find("map_flags_cached")
}
