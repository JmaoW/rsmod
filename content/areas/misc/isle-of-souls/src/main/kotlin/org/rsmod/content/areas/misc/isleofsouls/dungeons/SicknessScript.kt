package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.manager.PlayerPoisonEvents
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.content.skills.herblore.configs.PoisonCure
import org.rsmod.content.skills.herblore.configs.VenomCure
import org.rsmod.content.skills.herblore.scripts.SicknessTreated
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What antipoison potions, antivenoms, Guthix rest and curing foods do to the poison
 * ([PlayerPoison]) and venom ([PlayerVenom]) a player has (OSRS Wiki, "Poison" and "Venom"). Poison
 * is cured or weakened first, so the poison that venom turns into survives the dose that made it.
 * It also gives the poison that another player's weapons and spells deal ([PlayerPoisonEvents]),
 * which is the same poison an npc deals.
 */
class SicknessScript
@Inject
constructor(private val poison: PlayerPoison, private val venom: PlayerVenom) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SicknessTreated> { treat(this) }
        onEvent<PlayerPoisonEvents.Apply> { applied = poison.poison(target, damage) }
    }

    fun treat(event: SicknessTreated) {
        val player = event.player
        when (event.poison) {
            PoisonCure.Cure -> poison.cure(player)
            PoisonCure.Weaken -> poison.weaken(player)
            PoisonCure.None -> {}
        }
        if (!venom.isVenomed(player)) {
            return
        }
        event.wasVenomed = true
        val damage = venom.damageOf(player)
        when (event.venom) {
            VenomCure.Cure -> venom.cure(player)
            VenomCure.Convert -> {
                venom.cure(player)
                poison.poisonFromVenom(player, damage)
            }
            VenomCure.None -> {}
        }
    }
}
