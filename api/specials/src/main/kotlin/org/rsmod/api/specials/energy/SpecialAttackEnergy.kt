package org.rsmod.api.specials.energy

import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public class SpecialAttackEnergy {
    private var Player.specialEnergy by intVarp(varps.sa_energy)

    public fun hasSpecialEnergy(player: Player, energyInHundreds: Int): Boolean {
        return player.specialEnergy >= costFor(player, energyInHundreds)
    }

    public fun specialEnergy(player: Player): Int = player.specialEnergy

    public fun giveSpecialEnergy(player: Player, energyInHundreds: Int) {
        player.specialEnergy = minOf(MAX_ENERGY, player.specialEnergy + energyInHundreds)
    }

    public fun takeSpecialEnergy(player: Player, energyInHundreds: Int) {
        val cost = costFor(player, energyInHundreds)
        require(player.specialEnergy >= cost) {
            "Not enough special energy to take. Use `hasSpecialEnergy` first for validation."
        }
        player.specialEnergy -= cost
    }

    /**
     * What a special attack that normally costs [energyInHundreds] costs [player] now, after the
     * [SpecialEnergyCosts] content registered (a raid invocation, a potion).
     */
    public fun costFor(player: Player, energyInHundreds: Int): Int {
        if (isSpecializedRequirement(energyInHundreds)) {
            return energyInHundreds
        }
        var cost = energyInHundreds
        for (rule in SpecialEnergyCosts.rules) {
            cost = rule(player, cost)
        }
        return cost.coerceIn(0, MAX_ENERGY)
    }

    public fun isSpecializedRequirement(energyInHundreds: Int): Boolean {
        return energyInHundreds < 10
    }

    public companion object {
        public const val MAX_ENERGY: Int = 1000
    }
}

/**
 * Content that changes what special attacks cost, applied in the order registered: each rule gets
 * the cost so far (in tenths of a percent) and returns the new one.
 */
public object SpecialEnergyCosts {
    internal val rules = ArrayList<(player: Player, cost: Int) -> Int>()

    public fun register(rule: (player: Player, cost: Int) -> Int) {
        rules += rule
    }
}
