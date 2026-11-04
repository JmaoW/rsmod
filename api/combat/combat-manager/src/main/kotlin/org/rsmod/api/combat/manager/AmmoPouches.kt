package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * A second place a player keeps ammunition (Dizana's quiver's pouch, say). Before a ranged attack
 * reads the player's ammo slot, [ready] may move the pouch's ammunition into it when that is what
 * [weapon] fires and the slot holds something else. Everything after (bonuses, ammo use, bolt
 * effects) then works on the ammo slot as usual.
 */
public fun interface AmmoPouch {
    public fun ready(player: Player, weapon: UnpackedObjType)
}

/** The [AmmoPouch]es content has registered, asked in the order they were added. */
@Singleton
public class AmmoPouches @Inject constructor() {
    private val pouches = ArrayList<AmmoPouch>()

    public fun register(pouch: AmmoPouch) {
        pouches += pouch
    }

    public fun ready(player: Player, weapon: UnpackedObjType) {
        for (pouch in pouches) {
            pouch.ready(player, weapon)
        }
    }
}
