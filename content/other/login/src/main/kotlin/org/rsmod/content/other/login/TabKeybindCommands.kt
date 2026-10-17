package org.rsmod.content.other.login

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Receives the key a player picked in a keybinding screen. The patched client scripts send these as
 * `::` commands (see [TabKeybindScripts]); they are not meant to be typed, and only ever change the
 * sender's own tab keys.
 */
class TabKeybindCommands : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onCommand("fkeyset") {
            desc = "Set a tab key from the all-settings screen: <setting id> <key code>"
            cheat {
                logger.info { "fkeyset ${args.joinToString(" ")} from $player" }
                val slot = TabKeybinds.bySetting(args[0].toInt()) ?: return@cheat
                val key = args[1].toInt()
                if (TabKeybinds.isValidKey(key)) {
                    TabKeybinds.bind(player, slot, key)
                }
            }
        }

        onCommand("fkeydrop") {
            desc = "Set a tab key from the keybinding interface: <dropdown component> <key code>"
            cheat {
                logger.info { "fkeydrop ${args.joinToString(" ")} from $player" }
                val component = args[0].toInt() and 0xFFFF
                val slot = TabKeybinds.byDropdown(component) ?: return@cheat
                val key = args[1].toInt()
                if (TabKeybinds.isValidKey(key)) {
                    TabKeybinds.bind(player, slot, key)
                }
            }
        }
    }
}
