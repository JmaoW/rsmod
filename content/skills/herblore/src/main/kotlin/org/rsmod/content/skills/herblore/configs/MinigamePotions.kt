package org.rsmod.content.skills.herblore.configs

import org.rsmod.api.config.refs.stats
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

/**
 * The potions that Herblore does not make but that minigames, bosses and shops hand out: copies of
 * the potions it does, the barbarian mixes, Nightmare Zone and Chambers of Xeric potions and the
 * rest. The effects come from the OSRS wiki and from the tsps potion plugin (`Potions.plugin.js`),
 * which checked them against the game.
 *
 * Where the wiki is silent a guess was made, and it is marked as one:
 * - Nectar lowers the offensive stats like a Saradomin brew does, by the same amount.
 * - The Chambers of Xeric potions can be drunk anywhere, and their gourds leave nothing behind.
 */
object MinigamePotions {
    private fun boost(stat: StatType, constant: Int, percent: Int) =
        StatChange(stat, constant, percent)

    private val combatChanges =
        listOf(
            boost(stats.attack, 5, 15),
            boost(stats.strength, 5, 15),
            boost(stats.defence, 5, 15),
        )

    /** The potion of [source] in another set of objs, such as a minigame's copy. */
    private fun copy(source: Potion, doses: List<ObjType>) =
        Potion(source.name, doses, source.effect)

    private fun potion(
        name: String,
        doses: List<ObjType>,
        effect: PotionEffect,
        empty: ObjType? = HerbloreObjs.vial_empty,
    ) = Potion(name, doses, effect, empty)

    /** A barbarian mix: two doses of [source]'s effect, and [heal] hitpoints with each. */
    private fun mix(name: String, doses: List<ObjType>, source: PotionEffect, heal: Int) =
        Potion(name, doses, source, HerbloreObjs.vial_empty, heal)

    private val offence = listOf(stats.attack, stats.strength, stats.magic, stats.ranged)

    val ba_prayer = copy(Potions.prayer, MinigamePotionObjs.ba_prayer)
    val ba_ranging = copy(Potions.ranging, MinigamePotionObjs.ba_ranging)
    val ba_saradomin_brew = copy(Potions.saradomin_brew, MinigamePotionObjs.ba_saradomin_brew)
    val ba_stamina = copy(Potions.stamina, MinigamePotionObjs.ba_stamina)
    val ba_super_combat = copy(Potions.super_combat, MinigamePotionObjs.ba_super_combat)
    val ba_super_energy = copy(Potions.super_energy, MinigamePotionObjs.ba_super_energy)
    val ba_super_restore = copy(Potions.super_restore, MinigamePotionObjs.ba_super_restore)
    val deadman_combat = copy(Potions.combat, MinigamePotionObjs.deadman_combat)
    val blighted_super_restore =
        potion(
            "Blighted super restore",
            MinigamePotionObjs.blighted_super_restore,
            Potions.super_restore.effect,
        )

    val overload = potion("Overload", MinigamePotionObjs.overload, PotionEffect.Overload(5, 15))
    val blighted_overload =
        potion(
            "Blighted overload",
            MinigamePotionObjs.blighted_overload,
            PotionEffect.Overload(5, 15),
        )
    val super_ranging =
        potion(
            "Super ranging",
            MinigamePotionObjs.super_ranging,
            PotionEffect.Boost(boost(stats.ranged, 5, 15)),
        )
    val super_magic =
        potion(
            "Super magic potion",
            MinigamePotionObjs.super_magic,
            PotionEffect.Boost(boost(stats.magic, 5, 15)),
        )
    val bastion =
        potion(
            "Bastion potion",
            MinigamePotionObjs.bastion,
            PotionEffect.Boost(boost(stats.ranged, 5, 15), boost(stats.defence, 5, 15)),
        )
    val battlemage =
        potion(
            "Battlemage potion",
            MinigamePotionObjs.battlemage,
            PotionEffect.Boost(boost(stats.magic, 4, 0), boost(stats.defence, 5, 15)),
        )
    val divine_bastion =
        potion(
            "Divine bastion potion",
            MinigamePotionObjs.divine_bastion,
            PotionEffect.Divine(boost(stats.ranged, 5, 15), boost(stats.defence, 5, 15)),
        )
    val divine_battlemage =
        potion(
            "Divine battlemage potion",
            MinigamePotionObjs.divine_battlemage,
            PotionEffect.Divine(boost(stats.magic, 4, 0), boost(stats.defence, 5, 15)),
        )
    val magic_essence =
        potion(
            "Magic essence",
            MinigamePotionObjs.magic_essence,
            PotionEffect.Boost(boost(stats.magic, 3, 0)),
        )

    /** Prayer like a prayer potion, 40% run energy and the stamina effect (the Gauntlet). */
    val egniol =
        potion(
            "Egniol potion",
            MinigamePotionObjs.egniol,
            PotionEffect.Composite(PotionEffect.PrayerPoints(7, 25), PotionEffect.Stamina(40)),
            empty = null,
        )

    /** Restores every stat, prayer included, 4 plus 30% of the way, and cures poison. */
    val sanfew =
        potion(
            "Sanfew serum",
            MinigamePotionObjs.sanfew,
            PotionEffect.Composite(
                PotionEffect.Restore(4, 30, true),
                PotionEffect.Timed(TimedEffect.Antipoison, 600),
            ),
        )
    val ba_sanfew = copy(sanfew, MinigamePotionObjs.ba_sanfew)
    val antidote_plus =
        potion(
            "Antidote+",
            MinigamePotionObjs.antidote_plus,
            PotionEffect.Timed(TimedEffect.Antipoison, 900),
        )
    val antidote_plus_plus =
        potion(
            "Antidote++",
            MinigamePotionObjs.antidote_plus_plus,
            PotionEffect.Timed(TimedEffect.Antipoison, 1200),
        )
    val anti_venom =
        potion(
            "Anti-venom",
            MinigamePotionObjs.anti_venom,
            PotionEffect.Timed(TimedEffect.Antipoison, 1200),
        )
    val anti_venom_plus =
        potion(
            "Anti-venom+",
            MinigamePotionObjs.anti_venom_plus,
            PotionEffect.Timed(TimedEffect.Antipoison, 1500),
        )

    private fun brew(magicConstant: Int, magicPercent: Int) =
        PotionEffect.Composite(
            PotionEffect.Boost(boost(stats.magic, magicConstant, magicPercent)),
            PotionEffect.PrayerPoints(2, 10),
            PotionEffect.Drain(
                boost(stats.attack, 2, 10),
                boost(stats.strength, 2, 10),
                boost(stats.defence, 2, 10),
            ),
        )

    val ancient_brew = potion("Ancient brew", MinigamePotionObjs.ancient_brew, brew(2, 5))
    val forgotten_brew = potion("Forgotten brew", MinigamePotionObjs.forgotten_brew, brew(3, 8))
    val menaphite_remedy =
        potion(
            "Menaphite remedy",
            MinigamePotionObjs.menaphite_remedy,
            PotionEffect.MenaphiteRemedy,
        )

    /** A cup of herbal tea that heals 5 hitpoints, up to 5 above the base level. */
    val guthix_rest =
        potion(
            "Guthix rest",
            MinigamePotionObjs.guthix_rest,
            PotionEffect.Composite(PotionEffect.Heal(5, 0, true), PotionEffect.RunEnergy(5)),
            empty = MinigamePotionObjs.cup_empty,
        )

    /** Tombs of Amascut: heals 15% plus 3 above the base level and lowers the offensive stats. */
    val nectar =
        potion(
            "Nectar",
            MinigamePotionObjs.nectar,
            PotionEffect.Composite(
                PotionEffect.Heal(3, 15, true),
                PotionEffect.Drain(offence.map { boost(it, 2, 10) }),
            ),
            empty = null,
        )
    val ambrosia = potion("Ambrosia", MinigamePotionObjs.ambrosia, PotionEffect.Ambrosia, null)

    /** The three strengths of the Chambers of Xeric potions: weak (-), normal and strong (+). */
    private class Strength(
        val boostConstant: Int,
        val boostPercent: Int,
        val restoreConstant: Int,
        val restorePercent: Int,
        val enhanceTicks: Int,
        val aidUp: Int,
        val aidDown: Int,
        val antipoisonTicks: Int,
    )

    private val weak = Strength(4, 10, 6, 20, 268, 10, 5, 150)
    private val normal = Strength(5, 13, 8, 25, 375, 15, 7, 600)
    private val strong = Strength(6, 16, 11, 30, 483, 20, 10, 900)

    private fun cox(name: String, doses: List<ObjType>, effect: PotionEffect, suffix: String = "") =
        Potion("$name$suffix", doses, effect, empty = null)

    private fun aid(strength: Strength) =
        PotionEffect.Composite(
            PotionEffect.Boost(boost(stats.defence, 5, strength.aidUp)),
            PotionEffect.Heal(0, strength.aidUp, true),
            PotionEffect.Drain(offence.map { boost(it, 2, strength.aidDown) }),
        )

    private fun elder(s: Strength) =
        PotionEffect.Boost(
            boost(stats.attack, s.boostConstant, s.boostPercent),
            boost(stats.strength, s.boostConstant, s.boostPercent),
            boost(stats.defence, s.boostConstant, s.boostPercent),
        )

    private fun revitalisation(s: Strength) =
        PotionEffect.Restore(s.restoreConstant, s.restorePercent, true)

    val raids =
        listOf(
            cox("Elder", MinigamePotionObjs.elder_weak, elder(weak), " (-)"),
            cox("Elder", MinigamePotionObjs.elder, elder(normal), " potion"),
            cox("Elder", MinigamePotionObjs.elder_strong, elder(strong), " (+)"),
            cox(
                "Twisted",
                MinigamePotionObjs.twisted_weak,
                PotionEffect.Boost(boost(stats.ranged, weak.boostConstant, weak.boostPercent)),
                " (-)",
            ),
            cox(
                "Twisted",
                MinigamePotionObjs.twisted,
                PotionEffect.Boost(boost(stats.ranged, normal.boostConstant, normal.boostPercent)),
                " potion",
            ),
            cox(
                "Twisted",
                MinigamePotionObjs.twisted_strong,
                PotionEffect.Boost(boost(stats.ranged, strong.boostConstant, strong.boostPercent)),
                " (+)",
            ),
            cox(
                "Kodai",
                MinigamePotionObjs.kodai_weak,
                PotionEffect.Boost(boost(stats.magic, weak.boostConstant, weak.boostPercent)),
                " (-)",
            ),
            cox(
                "Kodai",
                MinigamePotionObjs.kodai,
                PotionEffect.Boost(boost(stats.magic, normal.boostConstant, normal.boostPercent)),
                " potion",
            ),
            cox(
                "Kodai",
                MinigamePotionObjs.kodai_strong,
                PotionEffect.Boost(boost(stats.magic, strong.boostConstant, strong.boostPercent)),
                " (+)",
            ),
            cox(
                "Revitalisation",
                MinigamePotionObjs.revitalisation_weak,
                revitalisation(weak),
                " (-)",
            ),
            cox(
                "Revitalisation",
                MinigamePotionObjs.revitalisation,
                revitalisation(normal),
                " potion",
            ),
            cox(
                "Revitalisation",
                MinigamePotionObjs.revitalisation_strong,
                revitalisation(strong),
                " (+)",
            ),
            cox(
                "Prayer enhance",
                MinigamePotionObjs.prayer_enhance_weak,
                PotionEffect.PrayerEnhance(weak.enhanceTicks),
                " (-)",
            ),
            cox(
                "Prayer enhance",
                MinigamePotionObjs.prayer_enhance,
                PotionEffect.PrayerEnhance(normal.enhanceTicks),
            ),
            cox(
                "Prayer enhance",
                MinigamePotionObjs.prayer_enhance_strong,
                PotionEffect.PrayerEnhance(strong.enhanceTicks),
                " (+)",
            ),
            cox("Xeric's aid", MinigamePotionObjs.xerics_aid_weak, aid(weak), " (-)"),
            cox("Xeric's aid", MinigamePotionObjs.xerics_aid, aid(normal)),
            cox("Xeric's aid", MinigamePotionObjs.xerics_aid_strong, aid(strong), " (+)"),
            cox(
                "Overload",
                MinigamePotionObjs.raids_overload_weak,
                PotionEffect.Overload(weak.boostConstant, weak.boostPercent),
                " (-)",
            ),
            cox(
                "Overload",
                MinigamePotionObjs.raids_overload_mid,
                PotionEffect.Overload(normal.boostConstant, normal.boostPercent),
            ),
            cox(
                "Overload",
                MinigamePotionObjs.raids_overload_strong,
                PotionEffect.Overload(strong.boostConstant, strong.boostPercent),
                " (+)",
            ),
            cox(
                "Antipoison",
                MinigamePotionObjs.raids_antipoison_weak,
                PotionEffect.Timed(TimedEffect.Antipoison, weak.antipoisonTicks),
                " (-)",
            ),
            cox(
                "Antipoison",
                MinigamePotionObjs.raids_antipoison,
                PotionEffect.Timed(TimedEffect.Antipoison, normal.antipoisonTicks),
                " potion",
            ),
            cox(
                "Antipoison",
                MinigamePotionObjs.raids_antipoison_strong,
                PotionEffect.Timed(TimedEffect.Antipoison, strong.antipoisonTicks),
                " (+)",
            ),
        )

    val mixes =
        listOf(
            mix("Attack mix", MinigamePotionObjs.attack_mix, Potions.attack.effect, 3),
            mix("Antipoison mix", MinigamePotionObjs.antipoison_mix, Potions.antipoison.effect, 3),
            mix("Relicym's mix", MinigamePotionObjs.relicyms_mix, PotionEffect.NoEffect, 3),
            mix("Strength mix", MinigamePotionObjs.strength_mix, Potions.strength.effect, 3),
            mix("Combat mix", MinigamePotionObjs.combat_mix, Potions.combat.effect, 3),
            mix("Restore mix", MinigamePotionObjs.restore_mix, Potions.restore.effect, 3),
            mix("Energy mix", MinigamePotionObjs.energy_mix, Potions.energy.effect, 3),
            mix("Defence mix", MinigamePotionObjs.defence_mix, Potions.defence.effect, 6),
            mix("Agility mix", MinigamePotionObjs.agility_mix, Potions.agility.effect, 6),
            mix("Prayer mix", MinigamePotionObjs.prayer_mix, Potions.prayer.effect, 6),
            mix(
                "Superattack mix",
                MinigamePotionObjs.super_attack_mix,
                Potions.super_attack.effect,
                6,
            ),
            mix(
                "Anti-poison supermix",
                MinigamePotionObjs.super_antipoison_mix,
                Potions.super_antipoison.effect,
                6,
            ),
            mix("Fishing mix", MinigamePotionObjs.fishing_mix, Potions.fishing.effect, 6),
            mix(
                "Super energy mix",
                MinigamePotionObjs.super_energy_mix,
                Potions.super_energy.effect,
                6,
            ),
            mix(
                "Super str. mix",
                MinigamePotionObjs.super_strength_mix,
                Potions.super_strength.effect,
                6,
            ),
            mix("Magic essence mix", MinigamePotionObjs.magic_essence_mix, magic_essence.effect, 6),
            mix(
                "Super restore mix",
                MinigamePotionObjs.super_restore_mix,
                Potions.super_restore.effect,
                6,
            ),
            mix(
                "Super def. mix",
                MinigamePotionObjs.super_defence_mix,
                Potions.super_defence.effect,
                6,
            ),
            mix("Antidote+ mix", MinigamePotionObjs.antidote_plus_mix, antidote_plus.effect, 6),
            mix("Antifire mix", MinigamePotionObjs.antifire_mix, Potions.antifire.effect, 6),
            mix("Ranging mix", MinigamePotionObjs.ranging_mix, Potions.ranging.effect, 6),
            mix("Magic mix", MinigamePotionObjs.magic_mix, Potions.magic.effect, 6),
            mix("Hunting mix", MinigamePotionObjs.hunting_mix, Potions.hunter.effect, 6),
            mix("Zamorak mix", MinigamePotionObjs.zamorak_mix, Potions.zamorak_brew.effect, 6),
            mix(
                "Extended antifire mix",
                MinigamePotionObjs.extended_antifire_mix,
                Potions.extended_antifire.effect,
                6,
            ),
            mix("Stamina mix", MinigamePotionObjs.stamina_mix, Potions.stamina.effect, 6),
            mix("Ancient mix", MinigamePotionObjs.ancient_mix, ancient_brew.effect, 6),
            mix(
                "Super antifire mix",
                MinigamePotionObjs.super_antifire_mix,
                Potions.super_antifire.effect,
                6,
            ),
            mix(
                "Extended super antifire mix",
                MinigamePotionObjs.extended_super_antifire_mix,
                Potions.extended_super_antifire.effect,
                6,
            ),
        )

    val all: List<Potion> =
        listOf(
            ba_prayer,
            ba_ranging,
            ba_saradomin_brew,
            ba_stamina,
            ba_super_combat,
            ba_super_energy,
            ba_super_restore,
            deadman_combat,
            blighted_super_restore,
            overload,
            blighted_overload,
            super_ranging,
            super_magic,
            bastion,
            battlemage,
            divine_bastion,
            divine_battlemage,
            magic_essence,
            egniol,
            sanfew,
            ba_sanfew,
            antidote_plus,
            antidote_plus_plus,
            anti_venom,
            anti_venom_plus,
            ancient_brew,
            forgotten_brew,
            menaphite_remedy,
            guthix_rest,
            nectar,
            ambrosia,
        ) + raids + mixes
}
