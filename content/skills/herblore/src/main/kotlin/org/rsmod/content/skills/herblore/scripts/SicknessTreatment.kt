package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.skills.herblore.configs.PoisonCure
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.content.skills.herblore.configs.VenomCure
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published when something treats [player]'s poison and venom. Whatever models them handles it:
 * cures or weakens the poison, then cures or converts the venom. A handler that finds the player
 * envenomed sets [wasVenomed].
 */
class SicknessTreated(val player: Player, val poison: PoisonCure, val venom: VenomCure) :
    UnboundEvent {
    var wasVenomed: Boolean = false
}

/**
 * Antipoison potions, antivenoms, Guthix rest and the foods that cure (OSRS Wiki, "Poison" and
 * "Venom"): treats the sickness a player has, and starts the immunity that comes with it.
 */
@Singleton
class SicknessTreatment
@Inject
constructor(private val effects: PotionEffects, private val eventBus: EventBus) {
    /**
     * Treats [player]'s poison and venom, and gives [poisonTicks] of immunity to poison and
     * [venomTicks] of immunity to venom. A cure for venom always gives its immunity, other
     * treatments only to a player who is not envenomed.
     */
    fun treat(
        player: Player,
        poison: PoisonCure,
        venom: VenomCure,
        poisonTicks: Int = 0,
        venomTicks: Int = 0,
    ) {
        val event = SicknessTreated(player, poison, venom)
        eventBus.publish(event)
        if (poisonTicks > 0) {
            effects.start(player, TimedEffect.Antipoison, poisonTicks)
        }
        if (venomTicks > 0 && (venom == VenomCure.Cure || !event.wasVenomed)) {
            effects.start(player, TimedEffect.VenomImmunity, venomTicks)
        }
    }
}
