package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Admin commands for pets:
 * - `::pet <key or name> [form]` hands a pet out as the game would (any pet, even one with no
 *   source on the isle);
 * - `::pets` lists the pet keys;
 * - `::petunlock <unlock|all>` unlocks metamorphoses;
 * - `::petreset` forgets every pet the player received and their unlocks (Probita's records).
 */
class PetCommands @Inject constructor(private val pets: Pets) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("pet") {
            modLevel = modlevels.admin
            desc = "Hand out a pet as if it had dropped"
            invalidArgs = "Use as ::pet <key or name> [form] (ex: ::pet rock_golem iron)"
            cheat {
                val words = args.toList()
                val (pet, used) =
                    find(words)
                        ?: run {
                            player.mes("No pet `${words.joinToString(" ")}`. Try ::pets.")
                            return@cheat
                        }
                val label = words.drop(used).joinToString(" ")
                val form =
                    if (label.isEmpty()) pet.base
                    else pet.forms.firstOrNull { it.label.equals(label, ignoreCase = true) }
                if (form == null) {
                    player.mes("${pet.name} has the forms: ${pet.forms.joinToString { it.label }}.")
                    return@cheat
                }
                val result = pets.award(player, pet, form)
                player.mes("${pet.name}: ${result.name.lowercase()}.")
            }
        }
        onCommand("pets") {
            modLevel = modlevels.admin
            desc = "List the pet keys for ::pet"
            cheat {
                pets.catalog
                    .map { it.key }
                    .chunked(LIST_CHUNK)
                    .forEach { player.mes(it.joinToString(", ")) }
            }
        }
        onCommand("petunlock") {
            modLevel = modlevels.admin
            desc = "Unlock a pet metamorphosis (or all of them)"
            invalidArgs = "Use as ::petunlock <${PetUnlock.entries.joinToString("|")}|all>"
            cheat {
                val name = args.getOrNull(0) ?: "all"
                val unlocks =
                    if (name.equals("all", ignoreCase = true)) PetUnlock.entries
                    else PetUnlock.entries.filter { it.name.equals(name, ignoreCase = true) }
                if (unlocks.isEmpty()) {
                    player.mes("No unlock `$name`.")
                    return@cheat
                }
                unlocks.forEach { pets.unlock(player, it) }
                player.mes("Unlocked: ${unlocks.joinToString { it.name }}.")
            }
        }
        onCommand("petreset") {
            modLevel = modlevels.admin
            desc = "Forget every pet received (insurance) and every metamorphosis unlocked"
            cheat {
                pet_varps.insured.forEach { VarPlayerIntMapSetter.set(player, it, 0) }
                VarPlayerIntMapSetter.set(player, pet_varps.unlocks, 0)
                VarPlayerIntMapSetter.set(player, pet_varps.rift_unlocks, 0)
                player.mes("Your pet records are cleared.")
            }
        }
    }

    /** The pet named by the first words of [words], and how many words its name took. */
    private fun find(words: List<String>): Pair<Pet, Int>? {
        for (count in words.size downTo 1) {
            val text = words.take(count).joinToString(" ")
            val pet =
                pets.petOrNull(text.lowercase())
                    ?: pets.catalog.firstOrNull { it.name.equals(text, ignoreCase = true) }
            if (pet != null) {
                return pet to count
            }
        }
        return null
    }

    private companion object {
        const val LIST_CHUNK = 12
    }
}
