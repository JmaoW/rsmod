package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Content decides where players may fight each other. A rule is asked every time a player starts or
 * continues an attack on another player, and answers with the message to show when the attack is
 * not allowed, or `null` when it is. With no rules registered every attack is allowed.
 *
 * Listeners hear about every attack that went through, which is where content hands out a skull.
 */
@Singleton
public class PvpRules @Inject constructor() {
    private val rules = ArrayList<(attacker: Player, target: Player) -> String?>()
    private val listeners = ArrayList<(attacker: Player, target: Player) -> Unit>()

    public fun register(rule: (attacker: Player, target: Player) -> String?) {
        rules += rule
    }

    public fun onAttack(listener: (attacker: Player, target: Player) -> Unit) {
        listeners += listener
    }

    /** The message explaining why [attacker] may not attack [target], or `null` if they may. */
    public fun denial(attacker: Player, target: Player): String? {
        for (rule in rules) {
            val message = rule(attacker, target)
            if (message != null) {
                return message
            }
        }
        return null
    }

    /** Tells the listeners that [attacker] attacked [target]. */
    public fun attacked(attacker: Player, target: Player) {
        for (listener in listeners) {
            listener(attacker, target)
        }
    }
}
