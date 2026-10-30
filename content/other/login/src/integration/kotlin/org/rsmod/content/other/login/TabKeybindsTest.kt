package org.rsmod.content.other.login

import com.google.inject.AbstractModule
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player

class TabKeybindsTest {
    @Test
    fun GameTestState.`new character gets the vanilla tab hotkeys`() =
        runGameTest(LoginScript::class) {
            val expected =
                mapOf(
                    varbits.stone_combat_key to 5,
                    varbits.stone_stats_key to 6,
                    varbits.stone_journal_key to 7,
                    varbits.stone_worn_key to 2,
                    varbits.stone_prayer_key to 3,
                    varbits.stone_magic_key to 4,
                    varbits.stone_clanchat_key to 8,
                    varbits.stone_friends_key to 9,
                    varbits.stone_account_key to 0,
                    varbits.stone_options1_key to 10,
                    varbits.stone_options2_key to 11,
                    varbits.stone_music_key to 12,
                    varbits.stone_inv_key to 1,
                    varbits.stone_logout_key to 13,
                )
            for ((varbit, key) in expected) {
                val actual = player.vars[varbit]
                check(actual == key) { "Expected $key for ${varbit.internalName}, got $actual." }
            }
        }

    @Test
    fun GameTestState.`existing hotkeys are left alone`() =
        runGameTest(LoginScript::class) {
            val custom = Player()
            custom.setVarBit(varbits.stone_magic_key, 3)
            registerPlayer(player = custom)

            check(custom.vars[varbits.stone_magic_key] == 3) { "Custom binding was overwritten." }
            check(custom.vars[varbits.stone_combat_key] == 0) {
                "Defaults applied over a custom set."
            }
        }

    private object NoBindings : AbstractModule()

    class Deps @Inject constructor(val commands: CheatCommandMap)

    private fun GameTestState.runCommandTest(body: GameTestScope.(CheatCommandMap) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            NoBindings,
            LoginScript::class,
            TabKeybindCommands::class,
        ) { deps ->
            body(deps.commands)
        }

    private fun GameTestScope.key(varbit: org.rsmod.game.type.varbit.VarBitType) =
        player.vars[varbit]

    @Test
    fun GameTestState.`settings screen pick binds the tab`() = runCommandTest { commands ->
        // Setting 17 is the prayer tab, entry 9 is F9.
        check(commands.execute(player, "fkeyset", listOf("17", "9")))
        check(key(varbits.stone_prayer_key) == 9) { "Prayer key not set." }
        // F9 was the friends tab's key, a key opens one tab only.
        check(key(varbits.stone_friends_key) == 0) { "Old owner of F9 kept the key." }
    }

    @Test
    fun GameTestState.`keybinding interface pick binds the tab`() = runCommandTest { commands ->
        // Dropdown component 44 is the sixth dropdown, the prayer tab. Entry 0 unbinds.
        val uid = (121 shl 16) or 44
        check(commands.execute(player, "fkeydrop", listOf(uid.toString(), "0")))
        check(key(varbits.stone_prayer_key) == 0) { "Prayer key not cleared." }
        check(commands.execute(player, "fkeydrop", listOf(uid.toString(), "12")))
        check(key(varbits.stone_prayer_key) == 12) { "Prayer key not set." }
        check(key(varbits.stone_music_key) == 0) { "Old owner of F12 kept the key." }
    }

    @Test
    fun GameTestState.`bad picks change nothing`() = runCommandTest { commands ->
        commands.execute(player, "fkeyset", listOf("17", "14"))
        commands.execute(player, "fkeyset", listOf("99", "3"))
        commands.execute(player, "fkeydrop", listOf("7", "3"))
        check(key(varbits.stone_prayer_key) == 3) { "Prayer key changed." }
        check(key(varbits.stone_combat_key) == 5) { "Combat key changed." }
    }

    @Test
    fun `patched client scripts report the pick`() {
        for ((name, command) in
            mapOf(
                "[clientscript,setting_dropdown_entry_op]" to "fkeyset ",
                "[clientscript,keybind_dropdown_select]" to "fkeydrop ",
            )) {
            val bytes =
                TabKeybindScripts::class.java.getResourceAsStream("cs2/$name")?.readBytes()
                    ?: error("Missing cs2/$name")
            check(String(bytes, Charsets.ISO_8859_1).contains(command)) { "$name lacks $command" }
        }
    }
}
