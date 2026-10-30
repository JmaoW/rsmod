package org.rsmod.api.spells.attack

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/**
 * Content that makes a spell always land on a particular target (the first freezing spell on
 * Callisto or Artio, which is guaranteed to hit). A rule says whether [spell], cast by [player] at
 * [target], is certain to hit; the usual accuracy roll is skipped when any rule says so.
 */
@Singleton
public class SpellAccuracyOverrides @Inject constructor() {
    private val rules = ArrayList<(Player, PathingEntity, ObjType) -> Boolean>()

    public fun register(rule: (player: Player, target: PathingEntity, spell: ObjType) -> Boolean) {
        rules += rule
    }

    /** Whether the spell is certain to hit [target]. */
    public fun guaranteed(player: Player, target: PathingEntity, spell: ObjType): Boolean =
        rules.any { it(player, target, spell) }
}
