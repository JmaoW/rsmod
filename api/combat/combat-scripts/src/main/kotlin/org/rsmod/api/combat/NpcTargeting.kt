package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.npc.NpcType

/** Who a npc attacks when it makes an attack. */
public enum class TargetingStyle {
    /** Only the player it is fighting. How npcs normally work. */
    One,

    /** One player at a time, taking turns between every player in range. */
    Alternating,

    /** Every player in range at once, each with their own accuracy and damage roll. */
    All,
}

/**
 * Decides whether [player] counts as being in the same place as [npc] for targeting. Return `null`
 * to have no opinion and leave it to the next scope (and finally the distance check). Team
 * instances use this because two instances can be next to each other on the map.
 */
public fun interface TargetScope {
    public fun inScope(npc: Npc, player: Player): Boolean?
}

/**
 * Decides which players a npc's attack lands on. Every area in this league is multi-combat, so a
 * monster may have several players to choose from: a npc type is given a [TargetingStyle] by the
 * script that implements its attacks (for example, TzTok-Jad uses [TargetingStyle.All] and fires a
 * projectile at each teammate).
 *
 * "In range" means on the same level, within [DEFAULT_RANGE] tiles (or the range passed in), and
 * allowed by every [TargetScope] that has an opinion (so players in a neighbouring instance are not
 * in range).
 */
@Singleton
public class NpcTargeting @Inject constructor(private val players: PlayerList) {
    private val styles = HashMap<Int, TargetingStyle>()
    private val ranges = HashMap<Int, Int>()
    private val turns = WeakHashMap<Npc, Int>()
    private val scopes = java.util.concurrent.CopyOnWriteArrayList<TargetScope>()

    public fun addScope(scope: TargetScope) {
        scopes += scope
    }

    public fun style(npc: Npc): TargetingStyle = styles[npc.type.id] ?: TargetingStyle.One

    /** How far from [npc] a player can be and still count as in range for its attacks. */
    public fun range(npc: Npc): Int = ranges[npc.type.id] ?: DEFAULT_RANGE

    /**
     * Sets how [type] picks its targets. [range] is how far away players can be and still be
     * picked: a boss in a large arena may need more than the default.
     */
    public fun setStyle(type: NpcType, style: TargetingStyle, range: Int = DEFAULT_RANGE) {
        val id = TypeResolver[type] ?: error("Npc type has not been resolved: $type")
        styles[id] = style
        ranges[id] = range
    }

    /** Every player the [npc] could attack right now, in a stable order. */
    public fun inRange(npc: Npc, range: Int = DEFAULT_RANGE): List<Player> =
        players
            .filter {
                it.isValidTarget() &&
                    it.coords.level == npc.coords.level &&
                    npc.isWithinDistance(it, range) &&
                    scopes.firstNotNullOfOrNull { scope -> scope.inScope(npc, it) } != false
            }
            .sortedBy { it.slotId }

    /**
     * The players that an attack by [npc] lands on, given the player [primary] it is fighting. The
     * result is never empty: it falls back to [primary].
     */
    public fun select(npc: Npc, primary: Player, range: Int = range(npc)): List<Player> =
        when (style(npc)) {
            TargetingStyle.One -> listOf(primary)
            TargetingStyle.All -> {
                val targets = inRange(npc, range)
                if (primary in targets) targets else listOf(primary) + targets
            }
            TargetingStyle.Alternating -> {
                val targets = inRange(npc, range)
                if (targets.isEmpty()) {
                    listOf(primary)
                } else {
                    val turn = ((turns[npc] ?: -1) + 1).mod(targets.size)
                    turns[npc] = turn
                    listOf(targets[turn])
                }
            }
        }

    public companion object {
        public const val DEFAULT_RANGE: Int = 32
    }
}
