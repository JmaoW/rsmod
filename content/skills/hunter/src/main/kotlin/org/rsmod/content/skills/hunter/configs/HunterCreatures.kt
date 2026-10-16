package org.rsmod.content.skills.hunter.configs

import kotlin.math.floor
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType

/** The kinds of trap. */
enum class TrapKind(val label: String) {
    Bird("bird snare"),
    Box("box trap"),
    Net("net trap"),
    Deadfall("deadfall"),
    Pit("pitfall"),
    MagicBox("magic box"),
}

/** One thing a trapped creature gives: [min] to [max] of [obj]. */
class Reward(val obj: ObjType, val min: Int, val max: Int = min)

/**
 * The locs a trap family passes through. A trap is laid as [idle], or set on a [base] loc that was
 * lying about (a boulder, a young tree). A net trap also has a net on the ground [beside] its tree.
 * When it catches nothing it shows [failing] and then [failed] (or goes back to its [base] when
 * there is no [failed]).
 */
class TrapLocs(
    val base: LocType? = null,
    val idle: LocType,
    val beside: LocType? = null,
    val failing: LocType? = null,
    val failed: LocType? = null,
)

/** How a big cat's fur comes out: [perfect], or [tatty] when the trapper's level is too low. */
class Fur(val perfect: ObjType, val tatty: ObjType, val meat: ObjType)

/**
 * A creature that can be trapped. The numbers are from the OSRS Wiki through the tsps hunter data
 * (see `docs/isle_of_souls_hunter_plan.md`): [level] and [xp] to trap it, and [low] and [high] the
 * chance (out of 256) of a catch at level 1 and level 99, with the chance between worked out from
 * the two (see [chance]).
 *
 * [npcName] is what the cache calls the creature. [catching] is the trap as the creature is caught
 * (four locs, one per side it can come from, for a box trap) and [full] the trap holding its catch.
 * [baits] are what a trapper can leave in the trap for a little better chance.
 */
class Creature(
    val key: String,
    val npcName: String,
    val kind: TrapKind,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val loot: List<Reward>,
    val locs: TrapLocs?,
    val catching: List<LocType> = emptyList(),
    val full: LocType? = null,
    val baits: List<ObjType> = emptyList(),
    val fur: Fur? = null,
) {
    /** The chance, from 0 to 1, that a trapper of [level] catches this creature. */
    fun chance(level: Int): Double = chance(low, high, level)

    companion object {
        /**
         * The Wiki's skilling success chart: the roll is out of 256 and runs in a straight line
         * from [low] at level 1 to [high] at level 99, rounded.
         */
        fun chance(low: Int, high: Int, level: Int): Double {
            val roll = floor(low + (high - low) * (level - 1) / 98.0 + 0.5).toInt() + 1
            return (roll / 256.0).coerceIn(0.0, 1.0)
        }
    }
}

object HunterCreatures {
    private val o = hunter_objs
    private val l = hunter_locs

    private fun bird(
        key: String,
        name: String,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
        feather: ObjType,
        catching: LocType,
        full: LocType,
    ) =
        Creature(
            key = key,
            npcName = name,
            kind = TrapKind.Bird,
            level = level,
            xp = xp,
            low = low,
            high = high,
            loot = listOf(Reward(o.bones, 1), Reward(o.raw_bird_meat, 1), Reward(feather, 5, 10)),
            locs = Birds,
            catching = listOf(catching),
            full = full,
        )

    private val Birds =
        TrapLocs(idle = l.snare_idle, failing = l.snare_failing, failed = l.snare_broken)

    private val Boxes = TrapLocs(idle = l.box_idle, failing = l.box_failing, failed = l.box_failed)

    private val Magic = TrapLocs(idle = l.magic_idle, failed = l.magic_failed)

    private val Deadfalls =
        TrapLocs(base = l.boulder, idle = l.deadfall_idle, failing = l.deadfall_failing)

    private fun net(
        tree: LocType,
        set: LocType,
        beside: LocType,
        failing: LocType,
        failed: LocType,
    ) = TrapLocs(base = tree, idle = set, beside = beside, failing = failing, failed = failed)

    private val SwampNets =
        net(
            l.tree_swamp,
            l.tree_set_swamp,
            l.net_set_swamp,
            l.net_failing_swamp,
            l.net_failed_swamp,
        )
    private val OrangeNets =
        net(
            l.tree_orange,
            l.tree_set_orange,
            l.net_set_orange,
            l.net_failing_orange,
            l.net_failed_orange,
        )
    private val RedNets =
        net(l.tree_red, l.tree_set_red, l.net_set_red, l.net_failing_red, l.net_failed_red)
    private val BlackNets =
        net(
            l.tree_black,
            l.tree_set_black,
            l.net_set_black,
            l.net_failing_black,
            l.net_failed_black,
        )
    private val MountainNets =
        net(
            l.tree_mountain,
            l.tree_set_mountain,
            l.net_set_mountain,
            l.net_failing_mountain,
            l.net_failed_mountain,
        )

    private fun box(
        key: String,
        name: String,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
        loot: ObjType,
        catching: List<LocType>,
        full: LocType,
        baits: List<ObjType> = emptyList(),
    ) =
        Creature(
            key = key,
            npcName = name,
            kind = TrapKind.Box,
            level = level,
            xp = xp,
            low = low,
            high = high,
            loot = listOf(Reward(loot, 1)),
            locs = Boxes,
            catching = catching,
            full = full,
            baits = baits,
        )

    private fun deadfall(
        key: String,
        name: String,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
        loot: List<Reward>,
        catching: LocType,
        full: LocType,
        bait: ObjType,
    ) =
        Creature(
            key = key,
            npcName = name,
            kind = TrapKind.Deadfall,
            level = level,
            xp = xp,
            low = low,
            high = high,
            loot = loot,
            locs = Deadfalls,
            catching = listOf(catching),
            full = full,
            baits = listOf(bait),
        )

    private fun netCreature(
        key: String,
        name: String,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
        loot: ObjType,
        locs: TrapLocs,
        catching: LocType,
        full: LocType,
        tar: ObjType,
    ) =
        Creature(
            key = key,
            npcName = name,
            kind = TrapKind.Net,
            level = level,
            xp = xp,
            low = low,
            high = high,
            loot = listOf(Reward(loot, 1)),
            locs = locs,
            catching = listOf(catching),
            full = full,
            baits = listOf(tar),
        )

    private fun pit(
        key: String,
        name: String,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
        loot: List<Reward>,
        fur: Fur? = null,
    ) =
        Creature(
            key = key,
            npcName = name,
            kind = TrapKind.Pit,
            level = level,
            xp = xp,
            low = low,
            high = high,
            loot = loot,
            locs = null,
            fur = fur,
        )

    val all: List<Creature> =
        listOf(
            bird(
                "crimson_swift",
                "Crimson swift",
                1,
                34.0,
                100,
                420,
                o.feather_red,
                l.snare_catching_jungle,
                l.snare_full_jungle,
            ),
            bird(
                "golden_warbler",
                "Golden warbler",
                5,
                47.0,
                92,
                400,
                o.feather_yellow,
                l.snare_catching_desert,
                l.snare_full_desert,
            ),
            bird(
                "copper_longtail",
                "Copper longtail",
                9,
                61.2,
                85,
                390,
                o.feather_orange,
                l.snare_catching_woodland,
                l.snare_full_woodland,
            ),
            bird(
                "cerulean_twitch",
                "Cerulean twitch",
                11,
                64.5,
                82,
                380,
                o.feather_blue,
                l.snare_catching_polar,
                l.snare_full_polar,
            ),
            bird(
                "tropical_wagtail",
                "Tropical wagtail",
                19,
                95.2,
                75,
                370,
                o.feather_stripy,
                l.snare_catching_coloured,
                l.snare_full_coloured,
            ),
            box(
                "ferret",
                "Ferret",
                27,
                115.2,
                100,
                420,
                o.ferret,
                listOf(
                    l.box_catching_ferret_n,
                    l.box_catching_ferret_e,
                    l.box_catching_ferret_s,
                    l.box_catching_ferret_w,
                ),
                l.box_full_ferret,
            ),
            box(
                "chinchompa",
                "Chinchompa",
                53,
                198.4,
                6,
                268,
                o.chinchompa,
                listOf(
                    l.box_catching_chinchompa_n,
                    l.box_catching_chinchompa_e,
                    l.box_catching_chinchompa_s,
                    l.box_catching_chinchompa_w,
                ),
                l.box_full_chinchompa,
                listOf(o.spicy_tomato),
            ),
            box(
                "carnivorous_chinchompa",
                "Carnivorous chinchompa",
                63,
                265.0,
                -78,
                228,
                o.chinchompa_red,
                listOf(
                    l.box_catching_big_n,
                    l.box_catching_big_e,
                    l.box_catching_big_s,
                    l.box_catching_big_w,
                ),
                l.box_full_big,
                listOf(o.spicy_minced_meat),
            ),
            box(
                "black_chinchompa",
                "Black chinchompa",
                73,
                315.0,
                -78,
                228,
                o.chinchompa_black,
                listOf(
                    l.box_catching_black_n,
                    l.box_catching_black_e,
                    l.box_catching_black_s,
                    l.box_catching_black_w,
                ),
                l.box_full_black,
                listOf(o.spicy_minced_meat),
            ),
            deadfall(
                "wild_kebbit",
                "Wild kebbit",
                23,
                102.4,
                29,
                385,
                listOf(Reward(o.bones, 1), Reward(o.kebbit_claws, 1)),
                l.deadfall_catching_claw,
                l.deadfall_full_claw,
                o.raw_beef,
            ),
            deadfall(
                "barb_tailed_kebbit",
                "Barb-tailed kebbit",
                33,
                134.4,
                -220,
                1037,
                listOf(Reward(o.bones, 1), Reward(o.barb_tail_harpoon, 1)),
                l.deadfall_catching_barbed,
                l.deadfall_full_barbed,
                o.raw_rainbow_fish,
            ),
            deadfall(
                "prickly_kebbit",
                "Prickly kebbit",
                37,
                147.2,
                -70,
                331,
                listOf(Reward(o.bones, 1), Reward(o.kebbit_spike, 1)),
                l.deadfall_catching_spike,
                l.deadfall_full_spike,
                o.barley,
            ),
            deadfall(
                "sabre_toothed_kebbit",
                "Sabre-toothed kebbit",
                51,
                160.0,
                -434,
                820,
                listOf(Reward(o.bones, 1), Reward(o.kebbit_teeth, 1)),
                l.deadfall_catching_sabre,
                l.deadfall_full_sabre,
                o.raw_beef,
            ),
            netCreature(
                "swamp_lizard",
                "Swamp lizard",
                29,
                152.0,
                52,
                360,
                o.swamp_lizard,
                SwampNets,
                l.net_catching_swamp,
                l.net_full_swamp,
                o.tar_guam,
            ),
            netCreature(
                "orange_salamander",
                "Orange salamander",
                47,
                224.0,
                16,
                288,
                o.orange_salamander,
                OrangeNets,
                l.net_catching_orange,
                l.net_full_orange,
                o.tar_marrentill,
            ),
            netCreature(
                "red_salamander",
                "Red salamander",
                59,
                272.0,
                0,
                240,
                o.red_salamander,
                RedNets,
                l.net_catching_red,
                l.net_full_red,
                o.tar_tarromin,
            ),
            netCreature(
                "black_salamander",
                "Black salamander",
                67,
                319.2,
                0,
                212,
                o.black_salamander,
                BlackNets,
                l.net_catching_black,
                l.net_full_black,
                o.tar_harralander,
            ),
            pit(
                "spined_larupia",
                "Spined larupia",
                31,
                180.0,
                40,
                256,
                listOf(Reward(o.big_bones, 1), Reward(o.raw_larupia, 1)),
                Fur(o.larupia_fur, o.tatty_larupia_fur, o.raw_larupia),
            ),
            pit(
                "horned_graahk",
                "Horned graahk",
                41,
                240.0,
                0,
                256,
                listOf(Reward(o.big_bones, 1), Reward(o.raw_graahk, 1)),
                Fur(o.graahk_fur, o.tatty_graahk_fur, o.raw_graahk),
            ),
            pit(
                "sabre_toothed_kyatt",
                "Sabre-toothed kyatt",
                55,
                300.0,
                -40,
                256,
                listOf(Reward(o.big_bones, 1), Reward(o.raw_kyatt, 1)),
                Fur(o.kyatt_fur, o.tatty_kyatt_fur, o.raw_kyatt),
            ),
            Creature(
                key = "imp",
                npcName = "Imp",
                kind = TrapKind.MagicBox,
                level = 71,
                xp = 450.0,
                low = 0,
                high = 197,
                loot = listOf(Reward(o.imp_in_a_box_full, 1)),
                locs = Magic,
                catching = listOf(l.magic_catching),
                full = l.magic_full,
                baits = listOf(o.red_bead, o.yellow_bead, o.black_bead, o.white_bead),
            ),
            box(
                "embertailed_jerboa",
                "Embertailed jerboa",
                39,
                137.0,
                6,
                268,
                o.jerboa_tail,
                listOf(
                    l.box_catching_jerboa_n,
                    l.box_catching_jerboa_e,
                    l.box_catching_jerboa_s,
                    l.box_catching_jerboa_w,
                ),
                l.box_full_jerboa,
            ),
            deadfall(
                "pyre_fox",
                "Pyre fox",
                57,
                177.6,
                -475,
                750,
                listOf(Reward(o.bones, 1), Reward(o.raw_pyre_fox, 1), Reward(o.fox_fur, 1)),
                l.deadfall_catching_fennec,
                l.deadfall_full_fennec,
                o.jerboa_tail,
            ),
            netCreature(
                "tecu_salamander",
                "Tecu salamander",
                79,
                344.0,
                1,
                212,
                o.tecu_salamander_young,
                MountainNets,
                l.net_catching_mountain,
                l.net_full_mountain,
                o.tar_irit,
            ),
            pit(
                "sunlight_antelope",
                "Sunlight antelope",
                72,
                380.0,
                255,
                255,
                listOf(
                    Reward(o.big_bones, 1),
                    Reward(o.sunlight_antler, 1),
                    Reward(o.sunlight_fur, 1),
                    Reward(o.raw_sunlight_antelope, 1),
                    Reward(o.sunfire_splinters, 2, 6),
                ),
            ),
            pit(
                "moonlight_antelope",
                "Moonlight antelope",
                91,
                450.0,
                255,
                255,
                listOf(
                    Reward(o.big_bones, 1),
                    Reward(o.moonlight_antler, 1),
                    Reward(o.moonlight_fur, 1),
                    Reward(o.raw_moonlight_antelope, 1),
                ),
            ),
        )

    private val byName: Map<String, Creature> = all.associateBy { it.npcName.lowercase() }

    private val byKey: Map<String, Creature> = all.associateBy { it.key }

    /** The locs of the first trap family of [kind] (bird snares and box traps only have one). */
    fun locsOf(kind: TrapKind): TrapLocs = all.first { it.kind == kind }.locs!!

    /** The creature that npcs called [npcName] are, if they can be trapped. */
    fun forNpcName(npcName: String): Creature? = byName[npcName.lowercase()]

    operator fun get(key: String): Creature = byKey.getValue(key)
}
