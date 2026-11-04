package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/** The kind of attack a player makes, as [ForcedMaxHit] rules see it. */
public enum class PlayerAttackKind {
    Melee,
    Ranged,
    Magic,
}

/**
 * A rule that makes some of a player's attacks always hit, for their maximum: a monster that is
 * weak to a style, or a perfect parry that empowers the next blow.
 */
public fun interface ForcedMaxHit {
    public fun isForced(source: Player, target: PathingEntity, kind: PlayerAttackKind): Boolean
}

/**
 * The [ForcedMaxHit] rules content has registered. [PlayerAttackManager] asks them on every
 * accuracy and damage roll: a forced attack skips the accuracy roll and does its max hit.
 */
@Singleton
public class ForcedMaxHits @Inject constructor() {
    private val rules = ArrayList<ForcedMaxHit>()

    public fun register(rule: ForcedMaxHit) {
        rules += rule
    }

    public fun isForced(source: Player, target: PathingEntity, kind: PlayerAttackKind): Boolean =
        rules.any { it.isForced(source, target, kind) }
}
