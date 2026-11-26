package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The skilling pets (wiki `Pet#Skilling pets`): each [SkillingPetRoll] a skill publishes is one
 * chance of `1 in (base - level * 25)`, using the player's base level in the pet's skill, fifteen
 * times as likely at 200 million experience, and scaled by the event's `petOdds`.
 *
 * The rock golem comes in the colour of the ore mined, the baby chinchompa in the colour of the
 * chinchompa caught, the rift guardian in the colour of the altar. While a rift guardian follows
 * its owner, crafting at an altar turns it that altar's colour (unless locked) and unlocks the
 * colour for good.
 */
class SkillingPets @Inject constructor(private val pets: Pets) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingPetRoll> { roll(this) }
    }

    fun roll(event: SkillingPetRoll) {
        val player = event.player
        val pet = pets.pet(KEYS.getValue(event.pet))
        if (event.pet == SkillingPet.RiftGuardian) {
            event.variant?.let { recolourRiftGuardian(player, pet, it) }
        }
        val oneIn = chanceOneIn(player, event.pet, event.baseChance)
        repeat(event.rolls.coerceAtLeast(1)) {
            if (pets.roll(oneIn)) {
                val form = formOf(pet, event.variant)
                if (event.pet == SkillingPet.RiftGuardian) {
                    unlockRiftColour(player, pet, form)
                }
                pets.award(player, pet, form)
                return
            }
        }
    }

    /** The "1 in this many" of one roll for [player] (wiki formula). */
    @OptIn(InternalApi::class)
    fun chanceOneIn(player: Player, pet: SkillingPet, baseChance: Int): Double {
        val stat = statOf(pet)
        val level = player.statBase(stat)
        val oneIn = maxOf(1, baseChance - level * LEVEL_FACTOR).toDouble()
        val maxed = player.statMap.getXP(stat) >= MAX_XP
        return if (maxed) oneIn / MAX_XP_BONUS else oneIn
    }

    private fun formOf(pet: Pet, variant: String?): PetForm {
        if (variant == null) {
            return pet.base
        }
        return pet.forms.firstOrNull { it.label.equals(variant, ignoreCase = true) } ?: pet.base
    }

    private fun recolourRiftGuardian(player: Player, pet: Pet, rune: String) {
        val follower = pets.followerOf(player) ?: return
        if (follower.pet != pet) {
            return
        }
        if (player.vars[pet_varps.rift_unlocks] and PetScript.RIFT_LOCK_BIT != 0) {
            return
        }
        val form = pet.forms.firstOrNull { it.label.equals(rune, ignoreCase = true) } ?: return
        if (form == follower.form || follower.form.label == "greatish guardian") {
            return
        }
        unlockRiftColour(player, pet, form)
        pets.transform(player, form)
    }

    private fun unlockRiftColour(player: Player, pet: Pet, form: PetForm) {
        val bit = 1 shl pet.forms.indexOf(form)
        val unlocks = player.vars[pet_varps.rift_unlocks]
        if (unlocks and bit == 0) {
            VarPlayerIntMapSetter.set(player, pet_varps.rift_unlocks, unlocks or bit)
            player.mes("You have unlocked a new metamorphosis for your rift guardian.")
        }
    }

    private fun statOf(pet: SkillingPet): StatType =
        when (pet) {
            SkillingPet.Heron -> stats.fishing
            SkillingPet.RockGolem -> stats.mining
            SkillingPet.Beaver -> stats.woodcutting
            SkillingPet.BabyChinchompa -> stats.hunter
            SkillingPet.GiantSquirrel -> stats.agility
            SkillingPet.Tangleroot -> stats.farming
            SkillingPet.Rocky -> stats.thieving
            SkillingPet.RiftGuardian -> stats.runecrafting
        }

    companion object {
        const val LEVEL_FACTOR = 25
        const val MAX_XP = 200_000_000
        const val MAX_XP_BONUS = 15.0

        val KEYS =
            mapOf(
                SkillingPet.Heron to "heron",
                SkillingPet.RockGolem to "rock_golem",
                SkillingPet.Beaver to "beaver",
                SkillingPet.BabyChinchompa to "baby_chinchompa",
                SkillingPet.GiantSquirrel to "giant_squirrel",
                SkillingPet.Tangleroot to "tangleroot",
                SkillingPet.Rocky to "rocky",
                SkillingPet.RiftGuardian to "rift_guardian",
            )
    }
}
