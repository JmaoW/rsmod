package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pets in monster drop tables: their chance follows the event's `petOdds` (never the relics' drop
 * boost), and a pet that is rolled is not dropped on the floor: it is handed out by [Pets.award]
 * (follower, backpack, Probita or duplicate). The collection log still counts it from the roll.
 */
class PetDropScript @Inject constructor(private val pets: Pets) : PluginScript() {
    private val petObjs: Set<Int> by lazy {
        pets.catalog.flatMap { pet -> pet.forms.map { it.obj.id } }.toSet()
    }

    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Modify> {
            val odds = pets.oddsMultiplier
            for (obj in petObjs) {
                chanceMultipliers[obj] = odds
            }
        }
        onEvent<NpcDropEvents.Claim> {
            val iterator = drops.iterator()
            while (iterator.hasNext()) {
                val drop = iterator.next()
                val (pet, form) = pets.ofObj(drop.obj.id) ?: continue
                iterator.remove()
                pets.award(hero, pet, form, log = false)
            }
        }
    }
}
