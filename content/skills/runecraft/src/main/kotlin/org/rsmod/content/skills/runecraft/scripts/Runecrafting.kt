package org.rsmod.content.skills.runecraft.scripts

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.runecraft.configs.ComboRune
import org.rsmod.content.skills.runecraft.configs.RuneAltar
import org.rsmod.content.skills.runecraft.configs.RuneAltars
import org.rsmod.content.skills.runecraft.configs.RunecraftObjs
import org.rsmod.content.skills.runecraft.configs.RunecraftSeqs
import org.rsmod.content.skills.runecraft.configs.RunecraftSpotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Runecraft skillcape emote, Raiments of the Eye, the abyssal lantern and runic extracts
// - Magic Imbue (combination runes without the talisman), once the Lunar spells exist
// - daeyalt, dark and blood essence, the Ourania Altar, Guardians of the Rift, soul and sunfire
//   runes (see the runecraft plan doc)
// - enchanting splitbark into swampbark or bloodbark, and infusing boots
//
// Clicking an altar binds every essence in the inventory into runes in one go, as the real game
// does. The runes made for each essence depend on the Runecraft level the player had *before* the
// action (the wiki's multiple-rune table). A combination rune is made by using the runes or the
// talisman of the *other* element on an altar: each rune has a 50% chance, always with a binding
// necklace, and the talisman is used up either way. Using a talisman on its own altar together
// with a plain tiara binds them into a talisman tiara.
class Runecrafting
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val sites: RunecraftSites,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (altar in RuneAltars.all) {
            onOpLoc1(altar.altar) { craft(altar) }

            if (altar.talisman != null) {
                onOpLocU(altar.altar, altar.talisman) { bindTiara(altar) }
            }
            if (altar.tiara != null) {
                onOpLocU(altar.altar, RunecraftObjs.tiara) { bindTiara(altar) }
            }

            for (combo in RuneAltars.combosAt(altar)) {
                val other = combo.other(altar)
                onOpLocU(altar.altar, other.rune) { combine(altar, combo) }
                onOpLocU(altar.altar, other.talisman!!) { combine(altar, combo) }
            }
        }
        for (talisman in TALISMANS) {
            onOpHeld4(talisman) { locate() }
        }
    }

    private fun ProtectedAccess.craft(altar: RuneAltar) {
        val level = player.runecraftingLvl
        if (level < altar.level) {
            mes("You need a Runecraft level of ${altar.level} to craft ${altar.name} runes.")
            return
        }
        val pure = invTotal(inv, RunecraftObjs.pure_essence)
        val plain = if (altar.pureOnly) 0 else invTotal(inv, RunecraftObjs.rune_essence)
        val essence = pure + plain
        if (essence == 0) {
            val wrongKind = altar.pureOnly && invTotal(inv, RunecraftObjs.rune_essence) > 0
            mes(
                if (wrongKind) {
                    "You can only bind ${altar.name} runes from pure essence."
                } else {
                    "You do not have any essence to bind."
                }
            )
            return
        }

        val runes = essence * altar.runesPerEssence(level)
        invDel(inv, RunecraftObjs.pure_essence, pure)
        invDel(inv, RunecraftObjs.rune_essence, plain)
        if (invAdd(inv, altar.rune, runes).failure) {
            invAdd(inv, RunecraftObjs.pure_essence, pure)
            invAdd(inv, RunecraftObjs.rune_essence, plain)
            mes("You do not have enough inventory space to hold the runes.")
            return
        }

        playCraftEffects()
        rollRiftGuardian(altar, essence)
        statAdvance(stats.runecrafting, essence * altar.xp * xpMods.get(player, stats.runecrafting))
        mes("You bind the temple's power into ${altar.name} runes.")
        publish(RunesCrafted(player, altar, essence, runes))
    }

    private fun ProtectedAccess.combine(altar: RuneAltar, combo: ComboRune) {
        val other = combo.other(altar)
        if (player.runecraftingLvl < combo.level) {
            mes("You need a Runecraft level of ${combo.level} to craft those runes.")
            return
        }
        // Magic Imbue does without the talisman for a few seconds.
        val imbued = ImbueCheck(player).also { publish(it) }.imbued
        if (!imbued && invTotal(inv, other.talisman!!) == 0) {
            mes(
                "You need the ${other.name} talisman to combine ${other.name} runes with this altar."
            )
            return
        }
        val pure = invTotal(inv, RunecraftObjs.pure_essence)
        val runes = invTotal(inv, other.rune)
        val attempts = minOf(pure, runes)
        if (attempts == 0) {
            mes("You need pure essence and ${other.name} runes to make those runes.")
            return
        }

        invDel(inv, RunecraftObjs.pure_essence, attempts)
        invDel(inv, other.rune, attempts)
        if (!imbued) {
            invDel(inv, other.talisman!!, 1)
        }

        val binding =
            player.worn[Wearpos.Front.slot]?.isType(RunecraftObjs.binding_necklace) == true
        var made = 0
        repeat(attempts) {
            if (binding || random.randomDouble() < COMBINE_CHANCE) {
                made++
            }
        }
        playCraftEffects()
        rollRiftGuardian(altar, attempts)
        if (made == 0) {
            mes("You fail to combine the runes, and they are lost.")
            return
        }
        if (invAdd(inv, combo.rune, made).failure) {
            mes("You do not have enough inventory space to hold the runes.")
            return
        }
        statAdvance(
            stats.runecrafting,
            made * combo.xpAt(altar) * xpMods.get(player, stats.runecrafting),
        )
        mes("You bind the temple's power into ${nameOf(combo.rune)}s.")
        publish(ComboRunesCrafted(player, altar, combo, made))
    }

    /**
     * The rift guardian: one roll per essence bound (wiki `Rift guardian#Drop rates`), in the
     * altar's colour. A following rift guardian also takes the altar's colour.
     */
    private fun ProtectedAccess.rollRiftGuardian(altar: RuneAltar, essence: Int) {
        val base = if (altar.name == "soul") SOUL_PET_CHANCE else ALTAR_PET_CHANCE
        publish(SkillingPetRoll(player, SkillingPet.RiftGuardian, base, altar.name, essence))
    }

    private fun ProtectedAccess.bindTiara(altar: RuneAltar) {
        val talisman = altar.talisman
        val tiara = altar.tiara
        if (talisman == null || tiara == null) {
            return
        }
        if (invTotal(inv, talisman) == 0 || invTotal(inv, RunecraftObjs.tiara) == 0) {
            mes("You need a tiara and a ${altar.name} talisman to do that.")
            return
        }
        invDel(inv, talisman, 1)
        invDel(inv, RunecraftObjs.tiara, 1)
        if (invAdd(inv, tiara, 1).failure) {
            invAdd(inv, talisman, 1)
            invAdd(inv, RunecraftObjs.tiara, 1)
            mes("You do not have enough inventory space to do that.")
            return
        }
        playCraftEffects()
        statAdvance(stats.runecrafting, altar.tiaraXp * xpMods.get(player, stats.runecrafting))
        mes("You bind the power of the ${altar.name} talisman into the tiara.")
        publish(TiaraBound(player, altar))
    }

    /**
     * Talismans point towards the Mage of Zamorak, who is the way to every altar here. Using the
     * Locate option is what the "locate an altar" league task counts.
     */
    private fun ProtectedAccess.locate() {
        val home = sites.home
        val dx = home.x - player.coords.x
        val dz = home.z - player.coords.z
        if (abs(dx) <= NEAR && abs(dz) <= NEAR) {
            mes("The talisman glows brightly. The way to the altars is very close.")
        } else {
            mes("The talisman pulls towards the ${direction(dx, dz)}.")
        }
        publish(TalismanLocated(player))
    }

    private fun ProtectedAccess.playCraftEffects() {
        anim(RunecraftSeqs.runecraft)
        spotanim(RunecraftSpotanims.runecraft)
    }

    private fun direction(dx: Int, dz: Int): String {
        val northSouth =
            when {
                dz > abs(dx) / 2 -> "north"
                -dz > abs(dx) / 2 -> "south"
                else -> null
            }
        val eastWest =
            when {
                dx > abs(dz) / 2 -> "east"
                -dx > abs(dz) / 2 -> "west"
                else -> null
            }
        return listOfNotNull(northSouth, eastWest).joinToString("-").ifEmpty { "north" }
    }

    private fun nameOf(obj: ObjType): String = objTypes[obj].name.lowercase()

    /**
     * Published when runes are about to be combined, so that Magic Imbue can say the talisman is
     * not needed by setting [imbued].
     */
    class ImbueCheck(val player: Player) : UnboundEvent {
        var imbued: Boolean = false
    }

    /** Published once essence has been bound into [runes] at [altar]. */
    class RunesCrafted(val player: Player, val altar: RuneAltar, val essence: Int, val runes: Int) :
        UnboundEvent

    /** Published once [made] combination runes came out of a combining attempt. */
    class ComboRunesCrafted(
        val player: Player,
        val altar: RuneAltar,
        val combo: ComboRune,
        val made: Int,
    ) : UnboundEvent

    /** Published once a talisman has been bound into a tiara at [altar]. */
    class TiaraBound(val player: Player, val altar: RuneAltar) : UnboundEvent

    /** Published when a talisman's Locate option has been used. */
    class TalismanLocated(val player: Player) : UnboundEvent

    companion object {
        /** Wiki: the rift guardian's base chance per essence (soul runes are likelier). */
        const val ALTAR_PET_CHANCE = 1_795_758
        const val SOUL_PET_CHANCE = 782_999

        private const val COMBINE_CHANCE = 0.5
        private const val NEAR = 10

        /** Every talisman that has a Locate option. */
        val TALISMANS: List<ObjType> =
            listOf(
                RunecraftObjs.air_talisman,
                RunecraftObjs.mind_talisman,
                RunecraftObjs.water_talisman,
                RunecraftObjs.earth_talisman,
                RunecraftObjs.fire_talisman,
                RunecraftObjs.body_talisman,
                RunecraftObjs.cosmic_talisman,
                RunecraftObjs.chaos_talisman,
                RunecraftObjs.nature_talisman,
                RunecraftObjs.law_talisman,
                RunecraftObjs.death_talisman,
                RunecraftObjs.blood_talisman,
                RunecraftObjs.wrath_talisman,
                RunecraftObjs.elemental_talisman,
                RunecraftObjs.catalytic_talisman,
            )
    }
}
