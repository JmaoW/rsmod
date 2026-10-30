package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The soul stacks of the Soulreaper axe. A player wielding it gains a stack each time they take
 * damage, up to [MAX_STACKS]; the stacks fade [DURATION] ticks after the last was gained. Behead
 * spends them all.
 */
@Singleton
class SoulStacks @Inject constructor() {
    private class Stacks(var count: Int, var until: Int)

    private val stacks = WeakHashMap<Player, Stacks>()

    /** The stacks [player] has right now. */
    fun count(player: Player): Int {
        val held = stacks[player] ?: return 0
        if (held.until <= player.currentMapClock) {
            stacks.remove(player)
            return 0
        }
        return held.count
    }

    /** Gives [player] a stack, which starts the fading over. */
    fun gain(player: Player) {
        val held = stacks.getOrPut(player) { Stacks(0, 0) }
        val current = count(player)
        held.count = minOf(MAX_STACKS, current + 1)
        held.until = player.currentMapClock + DURATION
    }

    /** Takes all of [player]'s stacks and returns how many there were. */
    fun consume(player: Player): Int {
        val count = count(player)
        stacks.remove(player)
        return count
    }

    companion object {
        const val MAX_STACKS = 5

        /** Twenty seconds. */
        const val DURATION = 33
    }
}

class SoulStackScript @Inject constructor(private val stacks: SoulStacks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerHitEvents.Impact> {
            val weapon = player.righthand
            if (damage > 0 && weapon != null && weapon.id == sobjs.soulreaper.id) {
                stacks.gain(player)
            }
        }
    }
}
