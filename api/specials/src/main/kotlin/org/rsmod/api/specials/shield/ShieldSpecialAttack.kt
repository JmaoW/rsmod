package org.rsmod.api.specials.shield

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * A special attack that a worn shield makes in place of the player's next attack, such as the
 * dragonfire shield's. The player enables it from the shield itself (its "Activate" option), not
 * from the special attack bar, and the swing that uses it is made whatever weapon is wielded.
 *
 * The shield's special attack energy requirement is a specialized one (below `10`), so the engine
 * neither checks nor takes any energy: charges, and whatever else the shield needs, are for the
 * implementation to check and take.
 *
 * Return `true` if the attack was made and replaces the player's normal swing, or `false` to let
 * the player swing normally (no charges left, say).
 *
 * @see [org.rsmod.api.specials.SpecialAttackRepository.registerShield]
 */
public interface ShieldSpecialAttack {
    public suspend fun ProtectedAccess.attack(target: Npc): Boolean

    public suspend fun ProtectedAccess.attack(target: Player): Boolean
}
