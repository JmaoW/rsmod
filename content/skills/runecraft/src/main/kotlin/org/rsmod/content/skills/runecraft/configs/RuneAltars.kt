package org.rsmod.content.skills.runecraft.configs

import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

/**
 * One runic altar. Levels, experience and the levels that give more runes per essence are from the
 * Old School wiki's Runecraft page.
 *
 * @property xp experience for each essence that is bound.
 * @property pureOnly whether rune essence is no good here and only pure essence will do.
 * @property extraRuneLevels the Runecraft level needed for 2x, 3x and so on runes per essence.
 * @property tiaraXp experience for binding the altar's talisman into a tiara, or `0` for none.
 * @property rift the Abyss rift that leads here.
 * @property inside the tile of the altar's exit portal, which is where a player arrives. The
 *   nearest free tile to it is used.
 */
class RuneAltar(
    val name: String,
    val altar: LocType,
    val rune: ObjType,
    val level: Int,
    val xp: Double,
    val pureOnly: Boolean,
    val extraRuneLevels: List<Int>,
    val talisman: ObjType?,
    val tiara: ObjType?,
    val tiaraXp: Double,
    val rift: LocType,
    val exitPortal: LocType?,
    val inside: CoordGrid,
) {
    /** How many runes each essence gives to a player of [runecraftLevel]. */
    fun runesPerEssence(runecraftLevel: Int): Int =
        1 + extraRuneLevels.count { runecraftLevel >= it }
}

/**
 * A combination rune, made at either altar of its two elements from pure essence, the other
 * element's runes and talisman. [lowXp] is for the altar of the lower level, [highXp] the other.
 */
class ComboRune(
    val rune: ObjType,
    val level: Int,
    val first: RuneAltar,
    val second: RuneAltar,
    val lowXp: Double,
    val highXp: Double,
) {
    /** The altar a player stands at to turn the runes of [other] into this rune. */
    fun other(altar: RuneAltar): RuneAltar = if (altar === first) second else first

    fun xpAt(altar: RuneAltar): Double = if (altar.level <= other(altar).level) lowXp else highXp
}

object RuneAltars {
    // Where a player lands for each altar: the room's exit portal. Astral is on the surface of
    // Lunar Isle, where there is no portal, so the isle spawns one (see [RunecraftSpawns]).
    val air =
        RuneAltar(
            "air",
            RunecraftLocs.air_altar,
            RunecraftObjs.air_rune,
            level = 1,
            xp = 5.0,
            pureOnly = false,
            extraRuneLevels = listOf(11, 22, 33, 44, 55, 66, 77, 88, 99),
            talisman = RunecraftObjs.air_talisman,
            tiara = RunecraftObjs.air_tiara,
            tiaraXp = 25.0,
            rift = RunecraftLocs.air_rift,
            exitPortal = RunecraftLocs.air_exit,
            inside = CoordGrid(2841, 4828, 0),
        )
    val mind =
        RuneAltar(
            "mind",
            RunecraftLocs.mind_altar,
            RunecraftObjs.mind_rune,
            level = 2,
            xp = 5.5,
            pureOnly = false,
            extraRuneLevels = listOf(14, 28, 42, 56, 70, 84, 98),
            talisman = RunecraftObjs.mind_talisman,
            tiara = RunecraftObjs.mind_tiara,
            tiaraXp = 27.5,
            rift = RunecraftLocs.mind_rift,
            exitPortal = RunecraftLocs.mind_exit,
            inside = CoordGrid(2793, 4827, 0),
        )
    val water =
        RuneAltar(
            "water",
            RunecraftLocs.water_altar,
            RunecraftObjs.water_rune,
            level = 5,
            xp = 6.0,
            pureOnly = false,
            extraRuneLevels = listOf(19, 38, 57, 76, 95),
            talisman = RunecraftObjs.water_talisman,
            tiara = RunecraftObjs.water_tiara,
            tiaraXp = 30.0,
            rift = RunecraftLocs.water_rift,
            exitPortal = RunecraftLocs.water_exit,
            inside = CoordGrid(2727, 4832, 0),
        )
    val earth =
        RuneAltar(
            "earth",
            RunecraftLocs.earth_altar,
            RunecraftObjs.earth_rune,
            level = 9,
            xp = 6.5,
            pureOnly = false,
            extraRuneLevels = listOf(26, 52, 78, 104),
            talisman = RunecraftObjs.earth_talisman,
            tiara = RunecraftObjs.earth_tiara,
            tiaraXp = 32.5,
            rift = RunecraftLocs.earth_rift,
            exitPortal = RunecraftLocs.earth_exit,
            inside = CoordGrid(2655, 4829, 0),
        )
    val fire =
        RuneAltar(
            "fire",
            RunecraftLocs.fire_altar,
            RunecraftObjs.fire_rune,
            level = 14,
            xp = 7.0,
            pureOnly = false,
            extraRuneLevels = listOf(35, 70),
            talisman = RunecraftObjs.fire_talisman,
            tiara = RunecraftObjs.fire_tiara,
            tiaraXp = 35.0,
            rift = RunecraftLocs.fire_rift,
            exitPortal = RunecraftLocs.fire_exit,
            inside = CoordGrid(2574, 4850, 0),
        )
    val body =
        RuneAltar(
            "body",
            RunecraftLocs.body_altar,
            RunecraftObjs.body_rune,
            level = 20,
            xp = 7.5,
            pureOnly = false,
            extraRuneLevels = listOf(46, 92),
            talisman = RunecraftObjs.body_talisman,
            tiara = RunecraftObjs.body_tiara,
            tiaraXp = 37.5,
            rift = RunecraftLocs.body_rift,
            exitPortal = RunecraftLocs.body_exit,
            inside = CoordGrid(2521, 4833, 0),
        )
    val cosmic =
        RuneAltar(
            "cosmic",
            RunecraftLocs.cosmic_altar,
            RunecraftObjs.cosmic_rune,
            level = 27,
            xp = 8.0,
            pureOnly = true,
            extraRuneLevels = listOf(59),
            talisman = RunecraftObjs.cosmic_talisman,
            tiara = RunecraftObjs.cosmic_tiara,
            tiaraXp = 40.0,
            rift = RunecraftLocs.cosmic_rift,
            exitPortal = RunecraftLocs.cosmic_exit,
            inside = CoordGrid(2142, 4812, 0),
        )
    val chaos =
        RuneAltar(
            "chaos",
            RunecraftLocs.chaos_altar,
            RunecraftObjs.chaos_rune,
            level = 35,
            xp = 8.5,
            pureOnly = true,
            extraRuneLevels = listOf(74),
            talisman = RunecraftObjs.chaos_talisman,
            tiara = RunecraftObjs.chaos_tiara,
            tiaraXp = 42.5,
            rift = RunecraftLocs.chaos_rift,
            exitPortal = RunecraftLocs.chaos_exit,
            inside = CoordGrid(2282, 4837, 0),
        )
    val astral =
        RuneAltar(
            "astral",
            RunecraftLocs.astral_altar,
            RunecraftObjs.astral_rune,
            level = 40,
            xp = 8.7,
            pureOnly = true,
            extraRuneLevels = listOf(82),
            talisman = null,
            tiara = null,
            tiaraXp = 0.0,
            rift = RunecraftLocs.astral_rift,
            exitPortal = null,
            inside = CoordGrid(2158, 3860, 0),
        )
    val nature =
        RuneAltar(
            "nature",
            RunecraftLocs.nature_altar,
            RunecraftObjs.nature_rune,
            level = 44,
            xp = 9.0,
            pureOnly = true,
            extraRuneLevels = listOf(91),
            talisman = RunecraftObjs.nature_talisman,
            tiara = RunecraftObjs.nature_tiara,
            tiaraXp = 45.0,
            rift = RunecraftLocs.nature_rift,
            exitPortal = RunecraftLocs.nature_exit,
            inside = CoordGrid(2400, 4834, 0),
        )
    val law =
        RuneAltar(
            "law",
            RunecraftLocs.law_altar,
            RunecraftObjs.law_rune,
            level = 54,
            xp = 9.5,
            pureOnly = true,
            extraRuneLevels = listOf(95),
            talisman = RunecraftObjs.law_talisman,
            tiara = RunecraftObjs.law_tiara,
            tiaraXp = 47.5,
            rift = RunecraftLocs.law_rift,
            exitPortal = RunecraftLocs.law_exit,
            inside = CoordGrid(2464, 4817, 0),
        )
    val death =
        RuneAltar(
            "death",
            RunecraftLocs.death_altar,
            RunecraftObjs.death_rune,
            level = 65,
            xp = 10.0,
            pureOnly = true,
            extraRuneLevels = listOf(99),
            talisman = RunecraftObjs.death_talisman,
            tiara = RunecraftObjs.death_tiara,
            tiaraXp = 50.0,
            rift = RunecraftLocs.death_rift,
            exitPortal = RunecraftLocs.death_exit,
            inside = CoordGrid(2208, 4829, 0),
        )

    /**
     * The true Blood Altar, which takes pure essence. Kourend's takes dark essence and is left out.
     */
    val blood =
        RuneAltar(
            "blood",
            RunecraftLocs.blood_altar,
            RunecraftObjs.blood_rune,
            level = 77,
            xp = 10.5,
            pureOnly = true,
            extraRuneLevels = emptyList(),
            talisman = RunecraftObjs.blood_talisman,
            tiara = null,
            tiaraXp = 0.0,
            rift = RunecraftLocs.blood_rift,
            exitPortal = RunecraftLocs.blood_exit,
            inside = CoordGrid(3224, 4832, 0),
        )
    val wrath =
        RuneAltar(
            "wrath",
            RunecraftLocs.wrath_altar,
            RunecraftObjs.wrath_rune,
            level = 95,
            xp = 8.0,
            pureOnly = true,
            extraRuneLevels = emptyList(),
            talisman = RunecraftObjs.wrath_talisman,
            tiara = RunecraftObjs.wrath_tiara,
            tiaraXp = 52.5,
            rift = RunecraftLocs.wrath_rift,
            exitPortal = RunecraftLocs.wrath_exit,
            inside = CoordGrid(2335, 4825, 1),
        )

    val all: List<RuneAltar> =
        listOf(
            air,
            mind,
            water,
            earth,
            fire,
            body,
            cosmic,
            chaos,
            astral,
            nature,
            law,
            death,
            blood,
            wrath,
        )

    /** Combination runes, made at the altar of either of the two elements. */
    val combos: List<ComboRune> =
        listOf(
            ComboRune(RunecraftObjs.mist_rune, 6, air, water, 8.0, 8.5),
            ComboRune(RunecraftObjs.dust_rune, 10, air, earth, 8.3, 9.0),
            ComboRune(RunecraftObjs.mud_rune, 13, water, earth, 9.3, 9.5),
            ComboRune(RunecraftObjs.smoke_rune, 15, air, fire, 8.5, 9.5),
            ComboRune(RunecraftObjs.steam_rune, 19, water, fire, 9.3, 10.0),
            ComboRune(RunecraftObjs.lava_rune, 23, earth, fire, 10.0, 10.5),
        )

    /** The combination runes that can be made at [altar]. */
    fun combosAt(altar: RuneAltar): List<ComboRune> =
        combos.filter { it.first === altar || it.second === altar }
}
