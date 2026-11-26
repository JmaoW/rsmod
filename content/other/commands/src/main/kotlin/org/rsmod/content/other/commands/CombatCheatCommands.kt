package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Admin toggles for testing fights, kept until logout:
 * - `::invincible`: hits land for nothing, and hitpoints are topped up every tick (for damage that
 *   takes hitpoints without a hit).
 * - `::tenx`: every hit the admin deals to an npc does ten times its damage (after the npc's own
 *   rules, so caps and reductions are multiplied too).
 */
class CombatCheatCommands @Inject constructor(private val playerList: PlayerList) : PluginScript() {
    private val invincible = HashSet<Player>()
    private val tenfold = HashSet<Player>()

    override fun ScriptContext.startup() {
        onCommand("invincible", "Toggle taking no damage", ::toggleInvincible)
        onCommand("tenx", "Toggle dealing 10x damage to npcs", ::toggleTenfold)

        onEvent<PlayerHitEvents.Landing> {
            if (player in invincible) {
                damage = 0
            }
        }
        onEvent<NpcHitEvents.ModifyAny> {
            val player = source ?: return@onEvent
            if (player in tenfold && hit.damage > 0) {
                hit.damage *= DAMAGE_MULTIPLIER
            }
        }
        onEvent<GameLifecycle.StartCycle> { topUp() }
        onPlayerLogout {
            invincible -= player
            tenfold -= player
        }
    }

    private fun toggleInvincible(cheat: Cheat) =
        with(cheat) {
            val on = invincible.add(player)
            if (!on) {
                invincible -= player
            }
            player.mes(if (on) "You are now invincible." else "You are no longer invincible.")
        }

    private fun toggleTenfold(cheat: Cheat) =
        with(cheat) {
            val on = tenfold.add(player)
            if (!on) {
                tenfold -= player
            }
            player.mes(
                if (on) "Your hits on npcs now do ${DAMAGE_MULTIPLIER}x damage."
                else "Your hits on npcs do normal damage again."
            )
        }

    private fun topUp() {
        if (invincible.isEmpty()) {
            return
        }
        val online = playerList.toSet()
        invincible.retainAll(online)
        for (player in invincible) {
            val missing = player.statBase(stats.hitpoints) - player.stat(stats.hitpoints)
            if (missing > 0) {
                player.statAdd(stats.hitpoints, missing, percent = 0)
            }
        }
    }

    private companion object {
        const val DAMAGE_MULTIPLIER = 10
    }
}
