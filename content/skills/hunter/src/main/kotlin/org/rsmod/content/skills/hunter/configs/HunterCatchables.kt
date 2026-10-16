package org.rsmod.content.skills.hunter.configs

import org.rsmod.api.config.refs.stats
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

/** What releasing a butterfly or moth does for the hunter. */
sealed class Boost {
    /** Raises [stat] by 4 and 15 percent, as a potion does. */
    class Stat(val stat: StatType) : Boost()

    /** Heals 8 hitpoints. */
    object Hitpoints : Boost()

    /** Restores every drained stat a little, and 8 hitpoints. */
    object Restore : Boost()

    /** Restores some prayer points. */
    object Prayer : Boost()
}

/**
 * A butterfly or moth. [level] and [xp] are for catching it with a net; barehanded needs [hands]
 * and gives [handsXp]. [low] and [high] are the catch chart (out of 256) at level 1 and 99 with a
 * net, and [handsLow] and [handsHigh] barehanded.
 */
class Butterfly(
    val name: String,
    val level: Int,
    val xp: Double,
    val hands: Int,
    val handsXp: Double,
    val jar: ObjType,
    val boost: Boost,
    val low: Int = 20,
    val high: Int = 296,
    val handsLow: Int = low,
    val handsHigh: Int = high,
)

/**
 * An impling. [level] is for catching it with a net and [hands] barehanded; [xp] is what either
 * gives. [low] and [high] are the catch chart (out of 256) at level 1 and 99. [key] names its loot
 * table and [jar] is the jar it is caught in.
 */
class Impling(
    val key: String,
    val level: Int,
    val hands: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val jar: ObjType,
) {
    val name: String
        get() = "$key impling"
}

object HunterCatchables {
    private val o = hunter_objs

    val butterflies: List<Butterfly> =
        listOf(
            Butterfly(
                "Ruby harvest",
                15,
                24.0,
                25,
                44.0,
                o.ruby_harvest_jar,
                Boost.Stat(stats.attack),
            ),
            Butterfly(
                "Sapphire glacialis",
                25,
                34.0,
                35,
                54.0,
                o.sapphire_glacialis_jar,
                Boost.Stat(stats.defence),
            ),
            Butterfly("Snowy knight", 35, 44.0, 45, 64.0, o.snowy_knight_jar, Boost.Hitpoints),
            Butterfly(
                "Black warlock",
                45,
                54.0,
                55,
                74.0,
                o.black_warlock_jar,
                Boost.Stat(stats.strength),
            ),
            Butterfly("Sunlight moth", 65, 74.0, 75, 94.0, o.sunlight_moth_jar, Boost.Restore),
            Butterfly(
                "Moonlight moth",
                75,
                84.0,
                85,
                104.0,
                o.moonlight_moth_jar,
                Boost.Prayer,
                low = 0,
                high = 276,
                handsLow = 20,
                handsHigh = 286,
            ),
        )

    val implings: List<Impling> =
        listOf(
            Impling("Baby", 17, 27, 20.0, 79, 402, o.baby_impling_jar),
            Impling("Young", 22, 32, 22.0, 69, 351, o.young_impling_jar),
            Impling("Gourmet", 28, 38, 24.0, 61, 325, o.gourmet_impling_jar),
            Impling("Earth", 36, 46, 27.0, 51, 302, o.earth_impling_jar),
            Impling("Essence", 42, 52, 29.0, 40, 275, o.essence_impling_jar),
            Impling("Eclectic", 50, 60, 32.0, 30, 250, o.eclectic_impling_jar),
            Impling("Nature", 58, 68, 36.0, 20, 200, o.nature_impling_jar),
            Impling("Magpie", 65, 75, 216.0, 15, 177, o.magpie_impling_jar),
            Impling("Ninja", 74, 84, 240.0, 10, 151, o.ninja_impling_jar),
            Impling("Dragon", 83, 93, 300.0, 5, 125, o.dragon_impling_jar),
            Impling("Crystal", 80, 90, 280.0, 8, 141, o.crystal_impling_jar),
            Impling("Lucky", 89, 99, 380.0, 3, 100, o.lucky_impling_jar),
        )

    private val butterfliesByName = butterflies.associateBy { it.name.lowercase() }
    private val implingsByName = implings.associateBy { it.name.lowercase() }

    fun butterfly(npcName: String): Butterfly? = butterfliesByName[npcName.lowercase()]

    fun impling(npcName: String): Impling? = implingsByName[npcName.lowercase()]

    fun implingOfJar(jar: ObjType): Impling? = implings.firstOrNull { it.jar.id == jar.id }

    fun butterflyOfJar(jar: ObjType): Butterfly? = butterflies.firstOrNull { it.jar.id == jar.id }
}

/** The three kebbits a falcon hunts. [low] and [high] are the catch chart out of 256. */
class Kebbit(
    val name: String,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val fur: ObjType,
)

object Falconry {
    val kebbits: List<Kebbit> =
        listOf(
            Kebbit("Spotted kebbit", 43, 104.0, 26, 310, hunter_objs.spotted_kebbit_fur),
            Kebbit("Dark kebbit", 57, 132.0, 0, 253, hunter_objs.dark_kebbit_fur),
            Kebbit("Dashing kebbit", 69, 156.0, 0, 205, hunter_objs.dashing_kebbit_fur),
        )

    fun forNpcName(name: String): Kebbit? = kebbits.firstOrNull { it.name.equals(name, true) }
}
