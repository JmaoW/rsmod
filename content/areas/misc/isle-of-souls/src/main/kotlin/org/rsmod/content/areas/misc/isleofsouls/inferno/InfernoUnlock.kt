package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.pets.Pets
import org.rsmod.game.entity.Player

/**
 * Who may enter the Inferno. The first fire cape a player gives up unlocks it for good; after that
 * they walk in whenever they like (as in the real game). Nothing hands a cape in at the moment: the
 * gatekeeper who took them was removed 2026-10-10 until the exchange is reworked, so the Inferno
 * stays locked.
 */
class InfernoUnlock
@Inject
constructor(
    private val completions: InfernoCompletions,
    private val rewards: InfernoRewards,
    private val pets: Pets,
) {
    fun isUnlocked(player: Player): Boolean = player.vars[inferno_vars.unlocked] != 0

    /** Whether [player] holds a fire cape, in their pack or on their back. */
    fun hasFireCape(player: Player): Boolean =
        cave_objs.fire_cape in player.inv || cave_objs.fire_cape in player.worn

    /**
     * Takes one fire cape from [player] (the pack first) and unlocks the Inferno. Returns `false`
     * if they had no cape to give.
     */
    fun sacrifice(player: Player): Boolean {
        val fromPack = cave_objs.fire_cape in player.inv
        val inv = if (fromPack) player.inv else player.worn
        if (!hasFireCape(player) || !player.invDel(inv, cave_objs.fire_cape, 1).success) {
            return false
        }
        VarPlayerIntMapSetter.set(player, inferno_vars.unlocked, 1)
        completions.sacrificed(player)
        return true
    }

    /**
     * Takes an infernal cape from [player]'s pack for another chance at the pet. Returns `null` if
     * they had none to hand in, or already own the pet (wiki: it cannot be gambled for again until
     * it is lost), otherwise whether the pet came.
     */
    fun tradeInCape(player: Player): Boolean? {
        if (inferno_objs.infernal_cape !in player.inv) {
            return null
        }
        if (pets.owns(player, pets.pet("jal_nib_rek"))) {
            return null
        }
        if (!player.invDel(player.inv, inferno_objs.infernal_cape, 1).success) {
            return null
        }
        return rewards.petRoll(player)
    }
}
