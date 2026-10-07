package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.api.type.refs.comp.ComponentReferences

/**
 * The `league_relics` components handed to the client's `league_relic_expanded_view` script, which
 * draws a single relic's details over the list of relics.
 */
object RelicViewComponents : ComponentReferences() {
    /** Parts of the relic list that are hidden while one relic is expanded. */
    val relic_available_header = find("league_relics:relic_available_header")
    val view_all = find("league_relics:view_all")
    val view_all_scrollbar = find("league_relics:view_all_scrollbar")

    /** The layer that holds the expanded relic. */
    val view_one = find("league_relics:view_one")

    /** The "Loading..." layer that the client shows while it waits for the server. */
    val loading = find("league_relics:loading")

    val icon = find("league_relics:icon")
    val relic_name = find("league_relics:name")
    val description_header = find("league_relics:description_header")
    val description = find("league_relics:description")

    /**
     * The hidden layer that holds the "Are you sure?" dialog. The Select button unhides it and the
     * Cancel button hides it again.
     */
    val confirm = find("league_relics:confirm")
    val passive_header = find("league_relics:passive_header")
    val passive_description = find("league_relics:passive_description")

    val select_button = find("league_relics:select_button")
    val select_back = find("league_relics:select_back")

    /** The "Are you sure...?" dialog shown after pressing Select. */
    val confirm_frame = find("league_relics:confirm_frame")
    val confirm_text = find("league_relics:confirm_text")
    val confirm_button = find("league_relics:confirm_button")
    val confirm_cancel = find("league_relics:confirm_cancel")
}
