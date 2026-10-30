package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Content decides when a player may not attack a npc (a ghost, a swimmer, someone stunned). A rule
 * is asked every time a player starts or continues an attack on a npc, and answers with the message
 * to show when the attack is not allowed, or `null` when it is. With no rules every attack goes on.
 */
@Singleton
public class PvnRules @Inject constructor() {
    private val rules = ArrayList<(attacker: Player, target: Npc) -> String?>()
    private val attackableTypes = HashSet<Int>()

    public fun register(rule: (attacker: Player, target: Npc) -> String?) {
        rules += rule
    }

    /**
     * Lets players fight npcs of [type] although the type has no "Attack" op, for content whose own
     * op starts an attack (Zebak's jugs: "Hit" swings the player's weapon at them).
     */
    public fun allowAttack(type: Int) {
        attackableTypes += type
    }

    /** Whether [target] may be attacked without an "Attack" op (see [allowAttack]). */
    public fun isAttackable(target: Npc): Boolean = target.visType.id in attackableTypes

    /** The message explaining why [attacker] may not attack [target], or `null` if they may. */
    public fun denial(attacker: Player, target: Npc): String? {
        for (rule in rules) {
            val message = rule(attacker, target)
            if (message != null) {
                return message
            }
        }
        return null
    }
}
