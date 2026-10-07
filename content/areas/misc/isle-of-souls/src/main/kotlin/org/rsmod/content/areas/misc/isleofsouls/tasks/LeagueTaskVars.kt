package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

internal typealias league_progress = LeagueProgressVarps

/** Number of `league_task_completed_N` varps the client reads (task ids `0..32*N-1`). */
internal const val COMPLETED_VARP_COUNT = 62

/** Client-owned league variables: task completion bits, points and the completed-task count. */
object LeagueTaskVars : VarpReferences() {
    /** `league_task_completed_N` holds the completion bit for task ids `N*32` until `N*32+31`. */
    val completed = List(COMPLETED_VARP_COUNT) { find("league_task_completed_$it") }

    /** Total points earned (summary screen and rank). */
    val points_completed = find("league_points_completed")

    /** Points counted towards relic tiers (side panel progress bar). */
    val points_claimed = find("league_points_claimed")
}

object LeagueTaskBitVars : VarBitReferences() {
    val total_tasks_completed = find("league_total_tasks_completed")
}

/** Server-only progress counters for tasks that need many repeats. Saved with the player. */
object LeagueProgressVarps : VarpReferences() {
    val tin_ore = find("isle_count_tin_ore")
    val iron_ore = find("isle_count_iron_ore")
    val coal = find("isle_count_coal")
    val mithril_ore = find("isle_count_mithril_ore")
    val adamantite_ore = find("isle_count_adamantite_ore")
    val willow_logs = find("isle_count_willow_logs")
    val maple_logs = find("isle_count_maple_logs")
    val yew_logs = find("isle_count_yew_logs")
    val magic_logs = find("isle_count_magic_logs")

    val catch_lobster = find("isle_count_catch_lobster")
    val catch_swordfish = find("isle_count_catch_swordfish")
    val catch_salmon = find("isle_count_catch_salmon")
    val catch_shark = find("isle_count_catch_shark")
    val catch_karambwan = find("isle_count_catch_karambwan")
    val catch_sardine = find("isle_count_catch_sardine")
    val catch_pike = find("isle_count_catch_pike")
    val cook_food = find("isle_count_cook_food")
    val cook_lobster = find("isle_count_cook_lobster")
    val cook_shark = find("isle_count_cook_shark")
    val cook_karambwan = find("isle_count_cook_karambwan")
    val cook_karambwan_row = find("isle_count_cook_karambwan_row")
    val cook_pies = find("isle_count_cook_pies")
    val cook_redberry_pies = find("isle_count_cook_redberry_pies")
    val cook_anglerfish = find("isle_count_cook_anglerfish")
    val cook_sardine = find("isle_count_cook_sardine")
    val cook_pike = find("isle_count_cook_pike")

    val burn_willow = find("isle_count_burn_willow")
    val burn_maple = find("isle_count_burn_maple")
    val burn_yew = find("isle_count_burn_yew")

    val smith_iron_arrowtips = find("isle_count_smith_iron_arrowtips")
    val smith_steel_bolts = find("isle_count_smith_steel_bolts")
    val smith_mithril_bolts = find("isle_count_smith_mithril_bolts")

    val fletch_iron_arrows = find("isle_count_fletch_iron_arrows")
    val fletch_oak_stocks = find("isle_count_fletch_oak_stocks")
    val fletch_arrow_shafts = find("isle_count_fletch_arrow_shafts")
    val fletch_magic_longbows = find("isle_count_fletch_magic_longbows")
    val fletch_maple_longbows = find("isle_count_fletch_maple_longbows")
    val fletch_dragon_javelins = find("isle_count_fletch_dragon_javelins")
    val fletch_dragon_darts = find("isle_count_fletch_dragon_darts")

    val fight_caves_5 = find("isle_count_fight_caves_5")
    val fight_caves_10 = find("isle_count_fight_caves_10")

    val clean_guam = find("isle_count_clean_guam")
    val clean_tarromin = find("isle_count_clean_tarromin")
    val clean_ranarr = find("isle_count_clean_ranarr")
    val clean_cadantine = find("isle_count_clean_cadantine")
    val clean_avantoe = find("isle_count_clean_avantoe")
    val clean_lantadyme = find("isle_count_clean_lantadyme")
    val make_prayer = find("isle_count_make_prayer")
    val make_stamina = find("isle_count_make_stamina")
    val make_ranging = find("isle_count_make_ranging")
    val make_magic = find("isle_count_make_magic")
    val make_goading = find("isle_count_make_goading")
    val make_prayer_regen = find("isle_count_make_prayer_regen")
    val make_divine_attack = find("isle_count_make_divine_attack")
    val make_divine_strength = find("isle_count_make_divine_strength")
    val make_divine_combat = find("isle_count_make_divine_combat")
    val make_divine_ranging = find("isle_count_make_divine_ranging")

    val craft_unpowered_orbs = find("isle_count_craft_unpowered_orbs")
    val craft_blue_dhide_bodies = find("isle_count_craft_blue_dhide_bodies")
    val blow_light_orbs = find("isle_count_blow_light_orbs")

    val golden_nuggets = find("isle_count_golden_nuggets")

    val runite_ore = find("isle_count_runite_ore")
    val gold_ore = find("isle_count_gold_ore")
    val mithril_ore_20 = find("isle_count_mithril_ore_20")
    val teak_logs = find("isle_count_teak_logs")
    val maple_logs_100 = find("isle_count_maple_logs_100")
    val yew_logs_15 = find("isle_count_yew_logs_15")
    val magic_logs_50 = find("isle_count_magic_logs_50")
    val magic_logs_20 = find("isle_count_magic_logs_20")
    val cook_shark_20 = find("isle_count_cook_shark_20")

    val all =
        listOf(
            tin_ore,
            iron_ore,
            coal,
            mithril_ore,
            adamantite_ore,
            willow_logs,
            maple_logs,
            yew_logs,
            magic_logs,
            catch_lobster,
            catch_swordfish,
            catch_salmon,
            catch_shark,
            catch_karambwan,
            catch_sardine,
            catch_pike,
            cook_food,
            cook_lobster,
            cook_shark,
            cook_karambwan,
            cook_karambwan_row,
            cook_pies,
            cook_redberry_pies,
            cook_anglerfish,
            cook_sardine,
            cook_pike,
            burn_willow,
            burn_maple,
            burn_yew,
            smith_iron_arrowtips,
            smith_steel_bolts,
            smith_mithril_bolts,
            fletch_iron_arrows,
            fletch_oak_stocks,
            fletch_arrow_shafts,
            fletch_magic_longbows,
            fletch_maple_longbows,
            fletch_dragon_javelins,
            fletch_dragon_darts,
            fight_caves_5,
            fight_caves_10,
            clean_guam,
            clean_tarromin,
            clean_ranarr,
            clean_cadantine,
            clean_avantoe,
            clean_lantadyme,
            make_prayer,
            make_stamina,
            make_ranging,
            make_magic,
            make_goading,
            make_prayer_regen,
            make_divine_attack,
            make_divine_strength,
            make_divine_combat,
            make_divine_ranging,
            craft_unpowered_orbs,
            craft_blue_dhide_bodies,
            blow_light_orbs,
            golden_nuggets,
            runite_ore,
            gold_ore,
            mithril_ore_20,
            teak_logs,
            maple_logs_100,
            yew_logs_15,
            magic_logs_50,
            magic_logs_20,
            cook_shark_20,
        )
}

internal object LeagueProgressVarpBuilder : VarpBuilder() {
    init {
        build("isle_count_tin_ore") {}
        build("isle_count_iron_ore") {}
        build("isle_count_coal") {}
        build("isle_count_mithril_ore") {}
        build("isle_count_adamantite_ore") {}
        build("isle_count_willow_logs") {}
        build("isle_count_maple_logs") {}
        build("isle_count_yew_logs") {}
        build("isle_count_magic_logs") {}
        build("isle_count_catch_lobster") {}
        build("isle_count_catch_swordfish") {}
        build("isle_count_catch_salmon") {}
        build("isle_count_catch_shark") {}
        build("isle_count_catch_karambwan") {}
        build("isle_count_catch_sardine") {}
        build("isle_count_catch_pike") {}
        build("isle_count_cook_food") {}
        build("isle_count_cook_lobster") {}
        build("isle_count_cook_shark") {}
        build("isle_count_cook_karambwan") {}
        build("isle_count_cook_karambwan_row") {}
        build("isle_count_cook_pies") {}
        build("isle_count_cook_redberry_pies") {}
        build("isle_count_cook_anglerfish") {}
        build("isle_count_cook_sardine") {}
        build("isle_count_cook_pike") {}
        build("isle_count_burn_willow") {}
        build("isle_count_burn_maple") {}
        build("isle_count_burn_yew") {}
        build("isle_count_smith_iron_arrowtips") {}
        build("isle_count_smith_steel_bolts") {}
        build("isle_count_smith_mithril_bolts") {}
        build("isle_count_fletch_iron_arrows") {}
        build("isle_count_fletch_oak_stocks") {}
        build("isle_count_fletch_arrow_shafts") {}
        build("isle_count_fletch_magic_longbows") {}
        build("isle_count_fletch_maple_longbows") {}
        build("isle_count_fletch_dragon_javelins") {}
        build("isle_count_fletch_dragon_darts") {}
        build("isle_count_fight_caves_5") {}
        build("isle_count_fight_caves_10") {}
        build("isle_count_clean_guam") {}
        build("isle_count_clean_tarromin") {}
        build("isle_count_clean_ranarr") {}
        build("isle_count_clean_cadantine") {}
        build("isle_count_clean_avantoe") {}
        build("isle_count_clean_lantadyme") {}
        build("isle_count_make_prayer") {}
        build("isle_count_make_stamina") {}
        build("isle_count_make_ranging") {}
        build("isle_count_make_magic") {}
        build("isle_count_make_goading") {}
        build("isle_count_make_prayer_regen") {}
        build("isle_count_make_divine_attack") {}
        build("isle_count_make_divine_strength") {}
        build("isle_count_make_divine_combat") {}
        build("isle_count_make_divine_ranging") {}
        build("isle_count_craft_unpowered_orbs") {}
        build("isle_count_craft_blue_dhide_bodies") {}
        build("isle_count_blow_light_orbs") {}
        build("isle_count_golden_nuggets") {}
        build("isle_count_runite_ore") {}
        build("isle_count_gold_ore") {}
        build("isle_count_mithril_ore_20") {}
        build("isle_count_teak_logs") {}
        build("isle_count_maple_logs_100") {}
        build("isle_count_yew_logs_15") {}
        build("isle_count_magic_logs_50") {}
        build("isle_count_magic_logs_20") {}
        build("isle_count_cook_shark_20") {}
    }
}
