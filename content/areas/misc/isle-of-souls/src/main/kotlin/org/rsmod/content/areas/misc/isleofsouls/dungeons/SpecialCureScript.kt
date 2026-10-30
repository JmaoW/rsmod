package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.content.other.special.attacks.support.SpecialPlayerEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Answers the special attacks that burn up a player's sickness (Virulence) or cure it (Tumeken's
 * Light): the poison and the venom of the isle are cured, and what their next hit would have dealt
 * is handed back.
 */
class SpecialCureScript
@Inject
constructor(private val poison: PlayerPoison, private val venom: PlayerVenom) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SpecialPlayerEvents.CureSickness> {
            damage += poison.damageOf(player) + venom.damageOf(player)
            poison.cure(player)
            venom.cure(player)
        }
    }
}
