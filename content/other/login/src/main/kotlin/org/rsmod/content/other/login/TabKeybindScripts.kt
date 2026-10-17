package org.rsmod.content.other.login

import org.rsmod.api.type.builders.clientscript.ClientScriptBuilder

/**
 * Packs the two clientscripts that tell the server which key a player picked for a side tab.
 *
 * Neither keybinding screen applies the pick on the client. Both expect the server to do it:
 * - The all-settings screen builds every dropdown with `settings_setting_choose_transmit = 1`, so
 *   `[clientscript,setting_dropdown_entry_op]` skips its own apply (`settings_set_keybind` is never
 *   reached) and nothing tells the server which entry was picked.
 * - The `keybinding` interface's dropdown entries call `[clientscript,keybind_dropdown_select]`,
 *   which only hides the dropdown.
 *
 * Each file is the cache's own script from revision 233 with one block added, which has the same
 * effect as the player typing a `::` command into the chatbox (the cache's `[proc,docheat]` strips
 * the `::` and calls `docheat`, op 5020):
 * ```
 * fkeyset <setting id> <entry index>          (setting_dropdown_entry_op: arg10, arg2)
 * fkeydrop <dropdown component> <entry index> (keybind_dropdown_select: arg2, arg1)
 * ```
 *
 * In `setting_dropdown_entry_op` the block sits where the script's two "skip the apply" jumps land
 * and only runs for keybind dropdowns (`arg0 == 3`, the kind `settings_create_keybind_entry`
 * passes). The entry index is the key code (`1..12` F1-F12, `13` Esc, `0` none) because both
 * dropdowns list the keys of enum 1160 in order. [TabKeybindCommands] handles the commands. If the
 * cache is updated to a new revision, add the same blocks to the new scripts and replace the files.
 * A block is plain instructions: `sconst "fkeyset "`, `iload`, `append_num` (4100), `sconst " "`,
 * `append` (4101), `iload`, `append_num`, `docheat` (5020).
 *
 * The client reads scripts from the cache, so this needs `./gradlew packCache` to reach it.
 */
object TabKeybindScripts : ClientScriptBuilder() {
    override fun onPackCs2Task() {
        resourceFile<TabKeybindScripts>("cs2/[clientscript,setting_dropdown_entry_op]")
        resourceFile<TabKeybindScripts>("cs2/[clientscript,keybind_dropdown_select]")
    }
}
