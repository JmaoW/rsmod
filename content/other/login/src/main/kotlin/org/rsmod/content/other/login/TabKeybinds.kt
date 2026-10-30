package org.rsmod.content.other.login

import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varbit.VarBitType

/**
 * The hotkey assigned to each side tab. A tab's key lives in its own varbit as a client key code:
 * `1..12` are F1-F12, `13` is Esc and `0` is unbound.
 *
 * The client never applies a key picked in either keybinding screen itself (both leave it to the
 * server, see [TabKeybindScripts]), so the patched scripts in [TabKeybindScripts] send the pick to
 * the server and [TabKeybindCommands] stores it here. Being ordinary varbits, the keys then save
 * and come back on login like any other setting.
 */
internal object TabKeybinds {
    const val UNBOUND = 0
    const val KEY_F1 = 1
    const val KEY_F2 = 2
    const val KEY_F3 = 3
    const val KEY_F4 = 4
    const val KEY_F5 = 5
    const val KEY_F6 = 6
    const val KEY_F7 = 7
    const val KEY_F8 = 8
    const val KEY_F9 = 9
    const val KEY_F10 = 10
    const val KEY_F11 = 11
    const val KEY_F12 = 12
    const val KEY_ESC = 13

    /** `keybinding:dropdown_1`, the first of the 14 dropdowns of the keybinding interface. */
    private const val FIRST_DROPDOWN = 9
    private const val DROPDOWN_STRIDE = 7

    /**
     * @param settingId the setting id the all-settings screen uses for this tab, taken from the
     *   switch of `settings_get_keybind`.
     * @param dropdown the component of this tab's dropdown on the `keybinding` interface.
     * @param default the vanilla key for a new account.
     */
    class Slot(
        val varbit: VarBitType,
        val settingId: Int,
        val dropdown: Int,
        val default: Int = UNBOUND,
    )

    // Component ids are in the order the interface lists the tabs, which is not the tab order.
    val slots: List<Slot> by lazy {
        fun dropdown(index: Int) = FIRST_DROPDOWN + index * DROPDOWN_STRIDE
        listOf(
            Slot(varbits.stone_combat_key, settingId = 16, dropdown(0), KEY_F5),
            Slot(varbits.stone_stats_key, settingId = 19, dropdown(1), KEY_F6),
            Slot(varbits.stone_journal_key, settingId = 22, dropdown(2), KEY_F7),
            Slot(varbits.stone_inv_key, settingId = 25, dropdown(3), KEY_F1),
            Slot(varbits.stone_worn_key, settingId = 28, dropdown(4), KEY_F2),
            Slot(varbits.stone_prayer_key, settingId = 17, dropdown(5), KEY_F3),
            Slot(varbits.stone_magic_key, settingId = 20, dropdown(6), KEY_F4),
            Slot(varbits.stone_friends_key, settingId = 23, dropdown(7), KEY_F9),
            Slot(varbits.stone_account_key, settingId = 26, dropdown(8)),
            Slot(varbits.stone_logout_key, settingId = 29, dropdown(9), KEY_ESC),
            Slot(varbits.stone_options1_key, settingId = 18, dropdown(10), KEY_F10),
            Slot(varbits.stone_options2_key, settingId = 21, dropdown(11), KEY_F11),
            Slot(varbits.stone_clanchat_key, settingId = 24, dropdown(12), KEY_F8),
            Slot(varbits.stone_music_key, settingId = 27, dropdown(13), KEY_F12),
        )
    }

    fun bySetting(settingId: Int): Slot? = slots.firstOrNull { it.settingId == settingId }

    fun byDropdown(component: Int): Slot? = slots.firstOrNull { it.dropdown == component }

    fun isValidKey(key: Int): Boolean = key in UNBOUND..KEY_ESC

    /**
     * Binds [key] to [slot]. A key can only open one tab, so any other tab that had it is unbound.
     */
    fun bind(player: Player, slot: Slot, key: Int) {
        require(isValidKey(key)) { "Invalid key code: $key" }
        if (key != UNBOUND) {
            for (other in slots) {
                if (other !== slot && player.vars[other.varbit] == key) {
                    set(player, other.varbit, UNBOUND)
                }
            }
        }
        set(player, slot.varbit, key)
    }

    /** Gives a character that has never had any tab key the vanilla layout. */
    fun applyDefaults(player: Player) {
        if (slots.any { player.vars[it.varbit] != UNBOUND }) {
            return
        }
        for (slot in slots) {
            set(player, slot.varbit, slot.default)
        }
    }

    private fun set(player: Player, varbit: VarBitType, value: Int) {
        VarPlayerIntMapSetter.set(player, varbit, value)
    }
}
