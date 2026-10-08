package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.api.type.refs.comp.ComponentReferences

/** The components of `league_combat_mastery` that the mastery logic reacts to. */
object MasteryComponents : ComponentReferences() {
    /** Invisible click layer over the grid. Child index `n` is mastery card `n`. */
    val clickzones = find("league_combat_mastery:clickzones")
    val confirm_button = find("league_combat_mastery:confirm_button")
    val select_button = find("league_combat_mastery:select_button")
    val select_back = find("league_combat_mastery:select_back")
    val confirm_cancel = find("league_combat_mastery:confirm_cancel")
}

/**
 * The `league_combat_mastery` components handed to the client's
 * `league_combat_mastery_expanded_view` script, which draws one mastery's details over the grid.
 */
object MasteryViewComponents : ComponentReferences() {
    /** Parts of the grid that are hidden while one mastery is expanded. */
    val view_all = find("league_combat_mastery:view_all")
    val view_all_scrollbar = find("league_combat_mastery:view_all_scrollbar")

    /** The layer that holds the expanded mastery. */
    val view_one = find("league_combat_mastery:view_one")

    /** The "Loading..." layer that the client shows while it waits for the server. */
    val loading = find("league_combat_mastery:loading")

    val icon = find("league_combat_mastery:icon")
    val name = find("league_combat_mastery:name")
    val description_header = find("league_combat_mastery:description_header")
    val description = find("league_combat_mastery:description")
    val select_button = find("league_combat_mastery:select_button")
    val select_back = find("league_combat_mastery:select_back")

    /** The hidden layer that holds the "Are you sure?" dialog. */
    val confirm = find("league_combat_mastery:confirm")
    val confirm_frame = find("league_combat_mastery:confirm_frame")
    val confirm_text = find("league_combat_mastery:confirm_text")
    val confirm_button = find("league_combat_mastery:confirm_button")
    val confirm_cancel = find("league_combat_mastery:confirm_cancel")
    val passive_header = find("league_combat_mastery:passive_header")
    val passive_description = find("league_combat_mastery:passive_description")
}
