package org.rsmod.content.other.food

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/**
 * Content decides where a player may not eat (a raid invocation, a minigame's own rules). A rule
 * answers with the message to show when [Player] may not eat the obj, or `null` when they may. With
 * no rules every food can be eaten.
 */
@Singleton
class EatRules @Inject constructor() {
    private val rules = ArrayList<(player: Player, food: ObjType) -> String?>()

    fun register(rule: (player: Player, food: ObjType) -> String?) {
        rules += rule
    }

    /** Why [player] may not eat [food], or `null` if they may. */
    fun denial(player: Player, food: ObjType): String? =
        rules.firstNotNullOfOrNull { it(player, food) }
}
