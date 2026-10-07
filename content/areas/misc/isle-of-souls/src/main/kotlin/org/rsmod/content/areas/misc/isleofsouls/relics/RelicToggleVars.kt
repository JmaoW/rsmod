package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * Server-only on/off switches for relic effects. Saved with the player. A switch holds `0` until
 * the player first changes it (meaning the [RelicToggle.default]), then `1` for on or `2` for off.
 */
object RelicToggleVars : VarpReferences() {
    val auto_smelt = find("isle_toggle_auto_smelt")
    val auto_gem_cut = find("isle_toggle_auto_gem_cut")
    val bank_mining = find("isle_toggle_bank_mining")
    val auto_burn = find("isle_toggle_auto_burn")
    val auto_fletch = find("isle_toggle_auto_fletch")
    val bank_woodcutting = find("isle_toggle_bank_woodcutting")
    val auto_cook = find("isle_toggle_auto_cook")
    val bank_fishing = find("isle_toggle_bank_fishing")
}

internal object RelicToggleVarpBuilder : VarpBuilder() {
    init {
        build("isle_toggle_auto_smelt") {}
        build("isle_toggle_auto_gem_cut") {}
        build("isle_toggle_bank_mining") {}
        build("isle_toggle_auto_burn") {}
        build("isle_toggle_auto_fletch") {}
        build("isle_toggle_bank_woodcutting") {}
        build("isle_toggle_auto_cook") {}
        build("isle_toggle_bank_fishing") {}
    }
}

/**
 * The switches on the Echo tools' "Toggle" option.
 *
 * @property label the name shown in the menu and in chat, such as `auto-smelt`.
 * @property default whether the effect is on until the player changes it.
 */
enum class RelicToggle(val varp: VarpType, val label: String, val default: Boolean) {
    AutoSmelt(RelicToggleVars.auto_smelt, "auto-smelt", default = false),
    AutoGemCut(RelicToggleVars.auto_gem_cut, "auto-gem cut", default = false),
    MiningBank(RelicToggleVars.bank_mining, "auto-bank", default = true),
    AutoBurn(RelicToggleVars.auto_burn, "auto-burn", default = false),
    AutoFletch(RelicToggleVars.auto_fletch, "auto-fletch", default = false),
    WoodcuttingBank(RelicToggleVars.bank_woodcutting, "auto-bank", default = true),
    AutoCook(RelicToggleVars.auto_cook, "auto-cook", default = true),
    FishingBank(RelicToggleVars.bank_fishing, "auto-bank", default = true),
}

/** Reads and changes the [RelicToggle] switches of a player. */
class RelicToggles @Inject constructor() {
    fun isOn(player: Player, toggle: RelicToggle): Boolean =
        when (player.vars[toggle.varp]) {
            1 -> true
            2 -> false
            else -> toggle.default
        }

    fun set(player: Player, toggle: RelicToggle, on: Boolean) {
        VarPlayerIntMapSetter.set(player, toggle.varp, if (on) 1 else 2)
    }

    /**
     * Flips [toggle] and returns the switches that had to be turned off because of it: burning and
     * fletching both use up the logs, so only one of them can be on.
     */
    fun flip(player: Player, toggle: RelicToggle): List<RelicToggle> {
        val on = !isOn(player, toggle)
        set(player, toggle, on)
        val rival =
            when (toggle) {
                RelicToggle.AutoBurn -> RelicToggle.AutoFletch
                RelicToggle.AutoFletch -> RelicToggle.AutoBurn
                else -> null
            }
        if (on && rival != null && isOn(player, rival)) {
            set(player, rival, false)
            return listOf(rival)
        }
        return emptyList()
    }

    /** `on` in green or `off` in red, for chat. */
    fun state(player: Player, toggle: RelicToggle): String = state(isOn(player, toggle))

    fun state(on: Boolean): String = if (on) LeagueChat.green("on") else LeagueChat.red("off")
}
