package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.config.refs.stats
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.game.type.varp.VarpType
import org.rsmod.map.CoordGrid

/** The doors, pillars and bridges the dungeons' gates are clicked on. */
object DungeonGateLocs : LocReferences() {
    val hydra_door = find("karuulm_hydra_room_door")
    val hydra_door_m = find("karuulm_hydra_room_door_m")

    val bandos_door = find("godwars_icecave_bandos_door")
    val grapple_pillar = find("godwars_grapple_pillar")

    // The god's chamber doors: a multi-state object that shows its normal door (the private one
    // only turns up for a player with an instance, which the isle has none of).
    val boss_door = find("godwars_dungeon_door_normal")
    val boss_door_private = find("godwars_dungeon_door_private")

    // The broken ice bridge to Zamorak's fortress: five pieces down each side and five along the
    // middle, the same pieces at both ends of the river.
    val bridge_side_1 = find("godwars_icecave_bridge01")
    val bridge_side_2 = find("godwars_icecave_bridge02")
    val bridge_side_3 = find("godwars_icecave_bridge03")
    val bridge_side_4 = find("godwars_icecave_bridge04")
    val bridge_side_5 = find("godwars_icecave_bridge05")
    val bridge_mirror_1 = find("godwars_icecave_bridge01_mirror")
    val bridge_mirror_2 = find("godwars_icecave_bridge02_mirror")
    val bridge_mirror_3 = find("godwars_icecave_bridge03_mirror")
    val bridge_mirror_4 = find("godwars_icecave_bridge04_mirror")
    val bridge_mirror_5 = find("godwars_icecave_bridge05_mirror")
    val bridge_middle_1 = find("godwars_icecave_bridge_middle01")
    val bridge_middle_2 = find("godwars_icecave_bridge_middle02")
    val bridge_middle_3 = find("godwars_icecave_bridge_middle03")
    val bridge_middle_4 = find("godwars_icecave_bridge_middle04")
    val bridge_middle_5 = find("godwars_icecave_bridge_middle05")

    val bridge_pieces: List<LocType> =
        listOf(
            bridge_side_1,
            bridge_side_2,
            bridge_side_3,
            bridge_side_4,
            bridge_side_5,
            bridge_mirror_1,
            bridge_mirror_2,
            bridge_mirror_3,
            bridge_mirror_4,
            bridge_mirror_5,
            bridge_middle_1,
            bridge_middle_2,
            bridge_middle_3,
            bridge_middle_4,
            bridge_middle_5,
        )
}

/**
 * The bridge's pieces have no options in the cache (the real crossing is a cutscene-like walk), so
 * they get a "Cross" option here, for [DungeonGates.zamorakBridge]. Needs `packCache`.
 */
internal object DungeonBridgeEdits : LocEditor() {
    init {
        for (piece in DungeonGateLocs.bridge_pieces) {
            edit(piece) {
                name = "Ice bridge"
                op1 = "Cross"
                active = 1
            }
        }
    }
}

/** The kill counts and the state of the doors, in the God Wars Dungeon's own varbits. */
object GodWarsVarbits : VarBitReferences() {
    val saradomin_kills = find("godwars_counter_saradomin")
    val armadyl_kills = find("godwars_counter_armadyl")
    val bandos_kills = find("godwars_counter_bandos")
    val zamorak_kills = find("godwars_counter_zamorak")

    /** Whether the chamber doors show their private (instance) version. */
    val door_state = find("godwars_entrance")

    /** The entrance rope, and the two ropes inside, which each start untied. */
    val entrance_rope = find("godwars_entrance_rope")
    val saradomin_rope_1 = find("godwars_saradomin_rope1")
    val saradomin_rope_2 = find("godwars_saradomin_rope2")
}

/**
 * One of the four gods whose followers are counted.
 *
 * @property aligned the switch that is `1` while the player wears an item of this god ([GodItems]).
 */
enum class God(val title: String, val kills: VarBitType, val aligned: VarpType) {
    ARMADYL("Armadyl", GodWarsVarbits.armadyl_kills, GodAlignmentVars.armadyl),
    BANDOS("Bandos", GodWarsVarbits.bandos_kills, GodAlignmentVars.bandos),
    SARADOMIN("Saradomin", GodWarsVarbits.saradomin_kills, GodAlignmentVars.saradomin),
    ZAMORAK("Zamorak", GodWarsVarbits.zamorak_kills, GodAlignmentVars.zamorak);

    companion object {
        /** The god whose follower a monster called [name] (its cache name) is, if any. */
        fun ofFollower(name: String): God? {
            if (!name.startsWith("godwars_")) {
                return null
            }
            // The gods themselves, their pets and bodyguards fight in the chambers and do not
            // count.
            if (
                listOf("avatar", "bodyguard", "lion", "centaur", "unicorn").any {
                    name.contains(it)
                }
            ) {
                return null
            }
            return when {
                name.contains("armadyl") -> ARMADYL
                name.contains("bandos") -> BANDOS
                name.contains("saradomin") || name.startsWith("godwars_icefiend") -> SARADOMIN
                name.contains("zamorak") -> ZAMORAK
                BANDOS_FOLLOWERS.any { name.startsWith(it) } -> BANDOS
                ZAMORAK_FOLLOWERS.any { name.startsWith(it) } -> ZAMORAK
                else -> null
            }
        }

        private val BANDOS_FOLLOWERS =
            listOf(
                "godwars_goblin",
                "godwars_sergeant_goblin",
                "godwars_gorak",
                "godwars_ancient_cyclops",
                "godwars_ancient_jogre",
                "godwars_ancient_ogre",
                "godwars_ancient_ork",
                "godwars_ancient_hobgoblin",
            )

        private val ZAMORAK_FOLLOWERS =
            listOf(
                "godwars_ancient_werewolf",
                "godwars_ancient_vampire",
                "godwars_ancient_imp",
                "godwars_ancient_hellhound",
                "godwars_ancient_lesser_demon",
                "godwars_ancient_greater_demon",
                "godwars_ancient_black_demon",
                "godwars_bloodveld",
                "godwars_pyrefiend",
            )
    }
}

/** The gates that have a sequence of their own ([GodWarsCrossings]) rather than a plain step. */
enum class GateCrossing {
    GONG,
    PILLAR,
    BRIDGE,
}

/** What a gate asks of the player who goes through it. */
sealed interface GateRule {
    /** Nothing: a door that only needs opening. */
    data object Free : GateRule

    /**
     * A skill level, [level] or more (boosted counts, unless [base]). It is asked going [a] to [b]
     * only, unless [bothWays].
     */
    data class Level(
        val stat: StatType,
        val level: Int,
        val skill: String,
        val base: Boolean = false,
        val bothWays: Boolean = false,
    ) : GateRule

    /**
     * [KILL_COUNT] kills of [god]'s followers, needed once: going in does not spend them. A player
     * with fewer can use one ecumenical key instead.
     */
    data class KillCount(val god: God) : GateRule
}

/** An object that starts a [DungeonGate]: [coords] is where it stands, or any tile when null. */
data class GateClick(val type: LocType, val coords: CoordGrid? = null)

/**
 * A way through that the cache has no open version of: using one of [clicks] takes the player from
 * the side of [a] and [b] they stand on to the other, if they meet the [rule]. Going from [a] to
 * [b] is "in".
 */
class DungeonGate(
    val key: String,
    val name: String,
    val clicks: List<GateClick>,
    val a: CoordGrid,
    val b: CoordGrid,
    val rule: GateRule = GateRule.Free,
    val message: String? = null,
    val crossing: GateCrossing? = null,
)

/** The kill count the god's chamber door asks for (the Wiki's 40, before Combat Achievements). */
const val KILL_COUNT = 40

object DungeonGates {
    private val locs = DungeonGateLocs

    val hydraDoorUpper =
        DungeonGate(
            key = "hydra_door_upper",
            name = "Alchemical door",
            clicks = listOf(GateClick(locs.hydra_door, CoordGrid(1355, 10259, 0))),
            a = CoordGrid(1354, 10259, 0),
            b = CoordGrid(1356, 10259, 0),
        )

    val hydraDoorLower =
        DungeonGate(
            key = "hydra_door_lower",
            name = "Alchemical door",
            clicks = listOf(GateClick(locs.hydra_door_m, CoordGrid(1355, 10258, 0))),
            a = CoordGrid(1354, 10258, 0),
            b = CoordGrid(1356, 10258, 0),
        )

    /**
     * Bandos' big door: 70 Strength, the Wiki says, to ring the gong (with a hammer if
     * [GodWarsCrossingItems] is on).
     */
    val bandosDoor =
        DungeonGate(
            key = "bandos_door",
            name = "Big door",
            clicks = listOf(GateClick(locs.bandos_door, CoordGrid(2851, 5333, 2))),
            a = CoordGrid(2852, 5333, 2),
            b = CoordGrid(2850, 5333, 2),
            rule = GateRule.Level(stats.strength, 70, "Strength"),
            crossing = GateCrossing.GONG,
        )

    /**
     * Armadyl's pillar: 70 Ranged to grapple it (the crossbow and grapple only if
     * [GodWarsCrossingItems] is on).
     */
    val armadylPillar =
        DungeonGate(
            key = "armadyl_pillar",
            name = "Pillar",
            clicks = listOf(GateClick(locs.grapple_pillar, CoordGrid(2871, 5270, 2))),
            a = CoordGrid(2872, 5279, 2),
            b = CoordGrid(2872, 5269, 2),
            rule = GateRule.Level(stats.ranged, 70, "Ranged", bothWays = true),
            crossing = GateCrossing.PILLAR,
        )

    /** Zamorak's ice bridge: a swim for 70 Hitpoints; Zamorak's side takes every Prayer point. */
    val zamorakBridge =
        DungeonGate(
            key = "zamorak_bridge",
            name = "Ice bridge",
            clicks = locs.bridge_pieces.map { GateClick(it) },
            a = CoordGrid(2885, 5328, 2),
            b = CoordGrid(2885, 5349, 2),
            rule = GateRule.Level(stats.hitpoints, 70, "Hitpoints", base = true, bothWays = true),
            crossing = GateCrossing.BRIDGE,
        )

    private fun chamberDoor(god: God, door: CoordGrid, outside: CoordGrid, inside: CoordGrid) =
        DungeonGate(
            key = "${god.name.lowercase()}_chamber",
            name = "Big door",
            clicks =
                listOf(GateClick(locs.boss_door, door), GateClick(locs.boss_door_private, door)),
            a = outside,
            b = inside,
            rule = GateRule.KillCount(god),
        )

    val armadylChamber =
        chamberDoor(
            God.ARMADYL,
            CoordGrid(2839, 5295, 2),
            CoordGrid(2839, 5294, 2),
            CoordGrid(2839, 5296, 2),
        )

    val bandosChamber =
        chamberDoor(
            God.BANDOS,
            CoordGrid(2863, 5354, 2),
            CoordGrid(2862, 5354, 2),
            CoordGrid(2864, 5354, 2),
        )

    val saradominChamber =
        chamberDoor(
            God.SARADOMIN,
            CoordGrid(2908, 5265, 0),
            CoordGrid(2909, 5265, 0),
            CoordGrid(2907, 5265, 0),
        )

    val zamorakChamber =
        chamberDoor(
            God.ZAMORAK,
            CoordGrid(2925, 5332, 2),
            CoordGrid(2925, 5333, 2),
            CoordGrid(2925, 5331, 2),
        )

    val all: List<DungeonGate> =
        listOf(
            hydraDoorUpper,
            hydraDoorLower,
            bandosDoor,
            armadylPillar,
            zamorakBridge,
            armadylChamber,
            bandosChamber,
            saradominChamber,
            zamorakChamber,
        )
}
