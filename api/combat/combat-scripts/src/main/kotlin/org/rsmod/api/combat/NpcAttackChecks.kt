package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc

/**
 * Conditions content adds on a player attacking a npc (a Slayer level to harm it, for example). A
 * check that returns `false` stops the attack; it is up to the check to tell the player why.
 */
@Singleton
public class NpcAttackChecks @Inject constructor() {
    private val checks = ArrayList<(ProtectedAccess, Npc) -> Boolean>()

    public fun register(check: (ProtectedAccess, Npc) -> Boolean) {
        checks += check
    }

    public fun allows(access: ProtectedAccess, npc: Npc): Boolean = checks.all { it(access, npc) }
}
