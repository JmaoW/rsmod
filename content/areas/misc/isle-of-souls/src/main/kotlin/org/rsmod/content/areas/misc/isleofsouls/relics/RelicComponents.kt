package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.api.type.refs.comp.ComponentReferences

/** The components of `league_relics` that the relic logic reacts to. */
object RelicComponents : ComponentReferences() {
    /** Invisible click layer over the relic cards. Child index `n` is relic card `n`. */
    val clickzones = find("league_relics:clickzones")
    val confirm_button = find("league_relics:confirm_button")
    val select_button = find("league_relics:select_button")
    val select_back = find("league_relics:select_back")
    val confirm_cancel = find("league_relics:confirm_cancel")
}

/**
 * Every named component of `league_relics`. The client only offers (and sends) a click for a
 * component whose events the server has enabled, and most of this screen's buttons are built by
 * clientscripts, so all of them are enabled when the screen opens.
 */
internal object RelicEventTargets : ComponentReferences() {
    val c0 = find("league_relics:infinity")
    val c1 = find("league_relics:universe")
    val c2 = find("league_relics:frame")
    val c3 = find("league_relics:content_outer")
    val c4 = find("league_relics:close_button")
    val c5 = find("league_relics:league_btn_menu")
    val c6 = find("league_relics:league_menu_frame")
    val c7 = find("league_relics:progress_bar")
    val c8 = find("league_relics:divider")
    val c9 = find("league_relics:content")
    val c10 = find("league_relics:relic_available_header")
    val c11 = find("league_relics:league_menu_overlay")
    val c12 = find("league_relics:confirm")
    val c13 = find("league_relics:tutorial_highlight")
    val c14 = find("league_relics:view_all")
    val c15 = find("league_relics:header")
    val c16 = find("league_relics:tutorial")
    val c17 = find("league_relics:backgrounds")
    val c18 = find("league_relics:outlines")
    val c19 = find("league_relics:icons")
    val c20 = find("league_relics:dividers")
    val c21 = find("league_relics:names")
    val c22 = find("league_relics:clickzones")
    val c23 = find("league_relics:view_all_scrollbar")
    val c24 = find("league_relics:loading")
    val c25 = find("league_relics:loading_overlay")
    val c26 = find("league_relics:com_26")
    val c27 = find("league_relics:view_one")
    val c28 = find("league_relics:extra_info")
    val c29 = find("league_relics:extra_info_backing")
    val c30 = find("league_relics:extra_info_holder")
    val c31 = find("league_relics:extra_info_frame")
    val c32 = find("league_relics:extra_info_content")
    val c33 = find("league_relics:name")
    val c34 = find("league_relics:description_relic")
    val c35 = find("league_relics:com_35")
    val c36 = find("league_relics:com_36")
    val c37 = find("league_relics:description_holder")
    val c38 = find("league_relics:description_header")
    val c39 = find("league_relics:description")
    val c40 = find("league_relics:description_scrollbar")
    val c41 = find("league_relics:description_passive")
    val c42 = find("league_relics:view_one_button_holder")
    val c43 = find("league_relics:icon")
    val c44 = find("league_relics:select_button")
    val c45 = find("league_relics:confirm_overlay")
    val c46 = find("league_relics:select_back")
    val c47 = find("league_relics:confirm_universe")
    val c48 = find("league_relics:confirm_frame")
    val c49 = find("league_relics:confirm_text")
    val c50 = find("league_relics:confirm_button")
    val c51 = find("league_relics:confirm_cancel")
    val c52 = find("league_relics:tooltip")
    val c53 = find("league_relics:com_53")
    val c54 = find("league_relics:com_54")
    val c55 = find("league_relics:passive_header")
    val c56 = find("league_relics:passive_description")
    val c57 = find("league_relics:passive_clickzone")
    val c58 = find("league_relics:extra_info_close_button")
    val c59 = find("league_relics:extra_text_holder")
    val c60 = find("league_relics:com_60")
    val c61 = find("league_relics:extra_text")
    val c62 = find("league_relics:extra_info_scroll")

    val all =
        listOf(
            c0,
            c1,
            c2,
            c3,
            c4,
            c5,
            c6,
            c7,
            c8,
            c9,
            c10,
            c11,
            c12,
            c13,
            c14,
            c15,
            c16,
            c17,
            c18,
            c19,
            c20,
            c21,
            c22,
            c23,
            c24,
            c25,
            c26,
            c27,
            c28,
            c29,
            c30,
            c31,
            c32,
            c33,
            c34,
            c35,
            c36,
            c37,
            c38,
            c39,
            c40,
            c41,
            c42,
            c43,
            c44,
            c45,
            c46,
            c47,
            c48,
            c49,
            c50,
            c51,
            c52,
            c53,
            c54,
            c55,
            c56,
            c57,
            c58,
            c59,
            c60,
            c61,
            c62,
        )
}
