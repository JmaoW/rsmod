package org.rsmod.content.skills.herblore.configs

import org.rsmod.api.config.refs.stats
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

/** Raises [stat] by [constant] plus [percent] percent of its base level. */
class StatChange(val stat: StatType, val constant: Int, val percent: Int)

/** The timed effects that only matter while they run, kept by `PotionEffects`. */
enum class TimedEffect(val message: String) {
    Antipoison("Your immunity to poison has worn off."),
    Antifire("Your resistance to dragonfire has run out."),
    ExtendedAntifire("Your resistance to dragonfire has run out."),
    Goading("Your goading potion has worn off."),
    Stamina("Your stamina potion has worn off."),
    Divine("The effects of your divine potion have worn off."),
}

/** What drinking a potion does. */
sealed interface PotionEffect {
    /** Raises the stats in [changes], not past the boost's own limit. */
    class Boost(val changes: List<StatChange>) : PotionEffect {
        constructor(vararg changes: StatChange) : this(changes.toList())
    }

    /**
     * A [Boost] that is also held for [DIVINE_TICKS] without wearing off, and that costs
     * [DIVINE_DAMAGE] hitpoints.
     */
    class Divine(val changes: List<StatChange>) : PotionEffect {
        constructor(vararg changes: StatChange) : this(changes.toList())
    }

    /** Restores every stat but hitpoints (and prayer, unless [prayer]) towards its base level. */
    class Restore(val constant: Int, val percent: Int, val prayer: Boolean) : PotionEffect

    /** Restores prayer points. */
    class PrayerPoints(val constant: Int, val percent: Int) : PotionEffect

    /** Restores [percent] percent of run energy. */
    class RunEnergy(val percent: Int) : PotionEffect

    /** Restores run energy and slows its drain for [STAMINA_TICKS]. */
    class Stamina(val percent: Int) : PotionEffect

    /** Starts [effect], which lasts [ticks]. */
    class Timed(val effect: TimedEffect, val ticks: Int) : PotionEffect

    /** One prayer point every [REGEN_INTERVAL] ticks, for [REGEN_POINTS] points a dose. */
    data object PrayerRegeneration : PotionEffect

    data object SaradominBrew : PotionEffect

    data object ZamorakBrew : PotionEffect

    companion object {
        const val DIVINE_TICKS = 500
        const val DIVINE_DAMAGE = 10
        const val STAMINA_TICKS = 200
        const val REGEN_INTERVAL = 12
        const val REGEN_POINTS = 66
    }
}

/**
 * A potion, [doses] being its four dose objs with four doses first. Potions are made with
 * [MADE_DOSES] doses.
 */
class Potion(val name: String, val doses: List<ObjType>, val effect: PotionEffect) {
    /** The obj of this potion with [count] doses, from `1` to `4`. */
    fun dose(count: Int): ObjType = doses[MAX_DOSES - count]

    companion object {
        const val MAX_DOSES = 4
        const val MADE_DOSES = 3
    }
}

/** Every potion herblore makes and drinks, with the effects and numbers from the wiki. */
object Potions {
    private fun boost(stat: StatType, constant: Int, percent: Int) =
        StatChange(stat, constant, percent)

    private val combatChanges =
        listOf(
            boost(stats.attack, 5, 15),
            boost(stats.strength, 5, 15),
            boost(stats.defence, 5, 15),
        )

    val attack =
        Potion("Attack potion", HerbloreObjs.attack, PotionEffect.Boost(boost(stats.attack, 3, 10)))
    val strength =
        Potion(
            "Strength potion",
            HerbloreObjs.strength,
            PotionEffect.Boost(boost(stats.strength, 3, 10)),
        )
    val defence =
        Potion(
            "Defence potion",
            HerbloreObjs.defence,
            PotionEffect.Boost(boost(stats.defence, 3, 10)),
        )
    val restore =
        Potion("Restore potion", HerbloreObjs.restore, PotionEffect.Restore(10, 30, false))
    val prayer = Potion("Prayer potion", HerbloreObjs.prayer, PotionEffect.PrayerPoints(7, 25))
    val super_attack =
        Potion(
            "Super attack",
            HerbloreObjs.super_attack,
            PotionEffect.Boost(boost(stats.attack, 5, 15)),
        )
    val super_strength =
        Potion(
            "Super strength",
            HerbloreObjs.super_strength,
            PotionEffect.Boost(boost(stats.strength, 5, 15)),
        )
    val super_defence =
        Potion(
            "Super defence",
            HerbloreObjs.super_defence,
            PotionEffect.Boost(boost(stats.defence, 5, 15)),
        )
    val ranging =
        Potion(
            "Ranging potion",
            HerbloreObjs.ranging,
            PotionEffect.Boost(boost(stats.ranged, 4, 10)),
        )
    val magic =
        Potion("Magic potion", HerbloreObjs.magic, PotionEffect.Boost(boost(stats.magic, 4, 0)))
    val antipoison =
        Potion(
            "Antipoison",
            HerbloreObjs.antipoison,
            PotionEffect.Timed(TimedEffect.Antipoison, 150),
        )
    val super_antipoison =
        Potion(
            "Superantipoison",
            HerbloreObjs.super_antipoison,
            PotionEffect.Timed(TimedEffect.Antipoison, 600),
        )
    val energy = Potion("Energy potion", HerbloreObjs.energy, PotionEffect.RunEnergy(10))
    val super_energy = Potion("Super energy", HerbloreObjs.super_energy, PotionEffect.RunEnergy(20))
    val super_restore =
        Potion("Super restore", HerbloreObjs.super_restore, PotionEffect.Restore(8, 25, true))
    val agility =
        Potion(
            "Agility potion",
            HerbloreObjs.agility,
            PotionEffect.Boost(boost(stats.agility, 3, 0)),
        )
    val fishing =
        Potion(
            "Fishing potion",
            HerbloreObjs.fishing,
            PotionEffect.Boost(boost(stats.fishing, 3, 0)),
        )
    val hunter =
        Potion("Hunter potion", HerbloreObjs.hunter, PotionEffect.Boost(boost(stats.hunter, 3, 0)))
    val combat =
        Potion(
            "Combat potion",
            HerbloreObjs.combat,
            PotionEffect.Boost(boost(stats.attack, 3, 10), boost(stats.strength, 3, 10)),
        )
    val super_combat =
        Potion("Super combat", HerbloreObjs.super_combat, PotionEffect.Boost(combatChanges))
    val antifire =
        Potion(
            "Antifire potion",
            HerbloreObjs.antifire,
            PotionEffect.Timed(TimedEffect.Antifire, 600),
        )
    val extended_antifire =
        Potion(
            "Extended antifire",
            HerbloreObjs.extended_antifire,
            PotionEffect.Timed(TimedEffect.ExtendedAntifire, 1200),
        )
    val saradomin_brew =
        Potion("Saradomin brew", HerbloreObjs.saradomin_brew, PotionEffect.SaradominBrew)
    val zamorak_brew = Potion("Zamorak brew", HerbloreObjs.zamorak_brew, PotionEffect.ZamorakBrew)
    val stamina = Potion("Stamina potion", HerbloreObjs.stamina, PotionEffect.Stamina(20))
    val goading =
        Potion("Goading potion", HerbloreObjs.goading, PotionEffect.Timed(TimedEffect.Goading, 500))
    val prayer_regeneration =
        Potion(
            "Prayer regeneration potion",
            HerbloreObjs.prayer_regeneration,
            PotionEffect.PrayerRegeneration,
        )

    val divine_super_attack =
        Potion(
            "Divine super attack",
            HerbloreObjs.divine_super_attack,
            PotionEffect.Divine(boost(stats.attack, 5, 15)),
        )
    val divine_super_strength =
        Potion(
            "Divine super strength",
            HerbloreObjs.divine_super_strength,
            PotionEffect.Divine(boost(stats.strength, 5, 15)),
        )
    val divine_super_defence =
        Potion(
            "Divine super defence",
            HerbloreObjs.divine_super_defence,
            PotionEffect.Divine(boost(stats.defence, 5, 15)),
        )
    val divine_ranging =
        Potion(
            "Divine ranging",
            HerbloreObjs.divine_ranging,
            PotionEffect.Divine(boost(stats.ranged, 4, 10)),
        )
    val divine_magic =
        Potion(
            "Divine magic",
            HerbloreObjs.divine_magic,
            PotionEffect.Divine(boost(stats.magic, 4, 0)),
        )
    val divine_super_combat =
        Potion(
            "Divine super combat",
            HerbloreObjs.divine_super_combat,
            PotionEffect.Divine(combatChanges),
        )

    val all =
        listOf(
            attack,
            strength,
            defence,
            restore,
            prayer,
            super_attack,
            super_strength,
            super_defence,
            ranging,
            magic,
            antipoison,
            super_antipoison,
            energy,
            super_energy,
            super_restore,
            agility,
            fishing,
            hunter,
            combat,
            super_combat,
            antifire,
            extended_antifire,
            saradomin_brew,
            zamorak_brew,
            stamina,
            goading,
            prayer_regeneration,
            divine_super_attack,
            divine_super_strength,
            divine_super_defence,
            divine_ranging,
            divine_magic,
            divine_super_combat,
        )
}
