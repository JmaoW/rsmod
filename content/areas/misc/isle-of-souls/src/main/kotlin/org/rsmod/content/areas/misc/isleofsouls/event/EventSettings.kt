package org.rsmod.content.areas.misc.isleofsouls.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.SortedMap
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumMode
import org.rsmod.content.areas.misc.isleofsouls.colosseum.ColosseumSettings
import org.rsmod.content.areas.misc.isleofsouls.cox.CoxSettings
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveSettings
import org.rsmod.content.areas.misc.isleofsouls.pvp.PvpSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaSettings
import org.rsmod.content.areas.misc.isleofsouls.tob.TobSettings

/**
 * The event's tunable numbers, read once at startup from `.data/league/event-settings.json`:
 * ```
 * {
 *   "baseXpRate": 150.0,
 *   "xpMultiplier": { "base": 5.0, "byRelicTier": { "2": 8.0, "5": 12.0, "7": 16.0 },
 *                     "combatBonus": 2.0, "combatBonusTier": 3 },
 *   "dropMultiplier": { "byRelicTier": { "1": 5.0, "4": 20.0 } },
 *   "relicPointThresholds": [0, 750, 1500, 2500, 5000, 8000, 16000, 25000],
 *   "taskPoints": { "easy": 10, "medium": 30, "hard": 80, "elite": 200, "master": 400 },
 *   "masteryPointsPerTask": 2,
 *   "revenantEtherMultiplier": 10.0,
 *   "zulrahScaleMultiplier": 10.0,
 *   "gauntletNodesDeplete": false,
 *   "autosave": { "intervalTicks": 200, "requestDelayTicks": 50 },
 *   "pvp": { "soulWarsArena": false, "revenantCaves": false, "wildernessBossLairs": false,
 *            "escapeCaves": false },
 *   "colosseum": { "mode": "full", "shortenedWaves": [1, 4, 7, 10, 12], "bankChestGlory": 0,
 *                  "ringGlory": 0 },
 *   "petOdds": 1.0
 * }
 * ```
 *
 * Every key is optional and a missing one keeps the default shown. Relic tiers are numbered from 1,
 * as on the relics screen. `fightCaveMode`, `infernoMode` and `respawnSpeed` live in the same file
 * and are read by [FightCaveSettings],
 * [org.rsmod.content.areas.misc.isleofsouls.inferno.InfernoSettings] and [RespawnSettings].
 *
 * Content reads [current] when it needs a number rather than keeping a copy, so
 * [EventSettingsScript] can replace it once the server starts.
 */
class EventSettings(
    val baseXpRate: Double? = null,
    val xpBase: Double = DEFAULT_XP_BASE,
    val xpByRelicTier: SortedMap<Int, Double> = DEFAULT_XP_TIERS.toSortedMap(),
    val combatXpBonus: Double = DEFAULT_COMBAT_BONUS,
    val combatXpBonusTier: Int = DEFAULT_COMBAT_BONUS_TIER,
    val dropByRelicTier: SortedMap<Int, Double> = DEFAULT_DROP_TIERS.toSortedMap(),
    val relicPointThresholds: List<Int> = DEFAULT_THRESHOLDS,
    val taskPoints: Map<String, Int> = DEFAULT_TASK_POINTS,
    val masteryPointsPerTask: Int = DEFAULT_MASTERY_POINTS,
    val revenantEtherMultiplier: Double = DEFAULT_ETHER_MULTIPLIER,
    val zulrahScaleMultiplier: Double = DEFAULT_SCALE_MULTIPLIER,
    val gauntletNodesDeplete: Boolean = false,
    val autosaveIntervalTicks: Int = DEFAULT_AUTOSAVE_INTERVAL,
    val autosaveRequestDelayTicks: Int = DEFAULT_AUTOSAVE_REQUEST_DELAY,
    /** Where players may fight each other; every area is off unless the file turns it on. */
    val pvp: PvpSettings = PvpSettings(),
    /** The Tombs of Amascut: how many paths a raid needs, and the unique chance multiplier. */
    val toa: ToaSettings = ToaSettings(),
    /** The Theatre of Blood: the party cap and the unique chance (see [TobSettings]). */
    val tob: TobSettings = TobSettings(),
    /** The Chambers of Xeric: the party cap and the unique chance (see [CoxSettings]). */
    val cox: CoxSettings = CoxSettings(),
    /**
     * The Fortis Colosseum: which waves a run plays, and its Glory gates (see [ColosseumSettings]).
     */
    val colosseum: ColosseumSettings = ColosseumSettings(),
    /**
     * Makes every pet this many times as likely (1 = the game's own odds). Drop-table pets, raid
     * and minigame pets and skilling pets all use it; the relics' drop boost never applies to pets.
     */
    val petOdds: Double = DEFAULT_PET_ODDS,
    /** Keys in the file that no setting reads, most likely typos. */
    val unknownKeys: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_XP_BASE = 5.0
        val DEFAULT_XP_TIERS = mapOf(2 to 8.0, 5 to 12.0, 7 to 16.0)
        const val DEFAULT_COMBAT_BONUS = 2.0
        const val DEFAULT_COMBAT_BONUS_TIER = 3
        val DEFAULT_DROP_TIERS = mapOf(1 to 5.0, 4 to 20.0)
        val DEFAULT_THRESHOLDS = listOf(0, 750, 1500, 2500, 5000, 8000, 16000, 25000)
        val DEFAULT_TASK_POINTS =
            mapOf("easy" to 10, "medium" to 30, "hard" to 80, "elite" to 200, "master" to 400)
        const val DEFAULT_MASTERY_POINTS = 2
        const val DEFAULT_ETHER_MULTIPLIER = 10.0
        const val DEFAULT_SCALE_MULTIPLIER = 10.0
        const val DEFAULT_AUTOSAVE_INTERVAL = 200
        const val DEFAULT_AUTOSAVE_REQUEST_DELAY = 50
        const val DEFAULT_PET_ODDS = 1.0

        /** How many relic tiers there are; the threshold list must have one entry for each. */
        const val RELIC_TIERS = 8

        private val OTHER_KEYS = setOf("fightCaveMode", "infernoMode", "respawnSpeed")

        /** What content reads. Replaced at startup by [EventSettingsScript]. */
        @Volatile var current: EventSettings = EventSettings()

        /** Reads the settings file from [dir]. A missing file means the defaults. */
        fun load(dir: Path = LeagueFiles.DATA_DIR): EventSettings {
            val file = dir.resolve(FightCaveSettings.FILE)
            if (!Files.exists(file)) {
                return EventSettings()
            }
            return try {
                parse(Files.readString(file))
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                        "Fix or move the file and start the server again.",
                    e,
                )
            }
        }

        fun parse(text: String): EventSettings {
            val root = ObjectMapper().readTree(text)
            if (root == null || !root.isObject) {
                throw IllegalArgumentException("the file must hold a JSON object")
            }
            val xp = root.obj("xpMultiplier")
            val drops = root.obj("dropMultiplier")
            val autosave = root.obj("autosave")
            val pvp = root.obj("pvp")
            val toa = root.obj("toa")
            val tob = root.obj("tob")
            val cox = root.obj("cox")
            val colosseum = root.obj("colosseum")
            val known =
                OTHER_KEYS +
                    setOf(
                        "baseXpRate",
                        "xpMultiplier",
                        "dropMultiplier",
                        "relicPointThresholds",
                        "taskPoints",
                        "masteryPointsPerTask",
                        "revenantEtherMultiplier",
                        "zulrahScaleMultiplier",
                        "gauntletNodesDeplete",
                        "autosave",
                        "pvp",
                        "toa",
                        "tob",
                        "cox",
                        "colosseum",
                        "petOdds",
                    )
            val unknown =
                root.unknown(known) +
                    xp.unknown(
                        setOf("base", "byRelicTier", "combatBonus", "combatBonusTier"),
                        "xpMultiplier.",
                    ) +
                    drops.unknown(setOf("byRelicTier"), "dropMultiplier.") +
                    autosave.unknown(setOf("intervalTicks", "requestDelayTicks"), "autosave.") +
                    pvp.unknown(PvpSettings.KEYS, "pvp.") +
                    toa.unknown(ToaSettings.KEYS, "toa.") +
                    tob.unknown(TobSettings.KEYS, "tob.") +
                    cox.unknown(CoxSettings.KEYS, "cox.") +
                    colosseum.unknown(ColosseumSettings.KEYS, "colosseum.")
            return EventSettings(
                baseXpRate = root.number("baseXpRate", above = 0.0),
                xpBase = xp.number("base", above = 0.0, path = "xpMultiplier") ?: DEFAULT_XP_BASE,
                xpByRelicTier =
                    xp.tierMultipliers("byRelicTier", "xpMultiplier")
                        ?: DEFAULT_XP_TIERS.toSortedMap(),
                combatXpBonus =
                    xp.number("combatBonus", above = 0.0, path = "xpMultiplier")
                        ?: DEFAULT_COMBAT_BONUS,
                combatXpBonusTier =
                    xp.whole("combatBonusTier", atLeast = 1, path = "xpMultiplier")?.also {
                        require(it <= RELIC_TIERS) {
                            "xpMultiplier.combatBonusTier must be 1 to $RELIC_TIERS, not $it"
                        }
                    } ?: DEFAULT_COMBAT_BONUS_TIER,
                dropByRelicTier =
                    drops.tierMultipliers("byRelicTier", "dropMultiplier")
                        ?: DEFAULT_DROP_TIERS.toSortedMap(),
                relicPointThresholds = root.thresholds() ?: DEFAULT_THRESHOLDS,
                taskPoints = root.taskPoints() ?: DEFAULT_TASK_POINTS,
                masteryPointsPerTask =
                    root.whole("masteryPointsPerTask", atLeast = 0) ?: DEFAULT_MASTERY_POINTS,
                revenantEtherMultiplier =
                    root.number("revenantEtherMultiplier", above = 0.0) ?: DEFAULT_ETHER_MULTIPLIER,
                zulrahScaleMultiplier =
                    root.number("zulrahScaleMultiplier", above = 0.0) ?: DEFAULT_SCALE_MULTIPLIER,
                gauntletNodesDeplete = root.flag("gauntletNodesDeplete") ?: false,
                autosaveIntervalTicks =
                    autosave.whole("intervalTicks", atLeast = 1, path = "autosave")
                        ?: DEFAULT_AUTOSAVE_INTERVAL,
                autosaveRequestDelayTicks =
                    autosave.whole("requestDelayTicks", atLeast = 1, path = "autosave")
                        ?: DEFAULT_AUTOSAVE_REQUEST_DELAY,
                pvp = pvp.pvpSettings(),
                toa = toa.toaSettings(),
                tob = tob.tobSettings(),
                cox = cox.coxSettings(),
                colosseum = colosseum.colosseumSettings(),
                petOdds = root.number("petOdds", above = 0.0) ?: DEFAULT_PET_ODDS,
                unknownKeys = unknown,
            )
        }

        private fun JsonNode?.obj(key: String): JsonNode? {
            val node = this?.get(key) ?: return null
            require(node.isObject) { "$key must be a JSON object, not $node" }
            return node
        }

        private fun JsonNode?.unknown(known: Set<String>, prefix: String = ""): List<String> =
            this?.fieldNames()?.asSequence()?.filter { it !in known }?.map { prefix + it }?.toList()
                ?: emptyList()

        private fun JsonNode?.number(key: String, above: Double, path: String? = null): Double? {
            val node = this?.get(key) ?: return null
            val name = if (path == null) key else "$path.$key"
            require(node.isNumber && node.asDouble().isFinite() && node.asDouble() > above) {
                "$name must be a number above ${above.toInt()}, not $node"
            }
            return node.asDouble()
        }

        private fun JsonNode?.whole(key: String, atLeast: Int, path: String? = null): Int? {
            val node = this?.get(key) ?: return null
            val name = if (path == null) key else "$path.$key"
            require(node.isIntegralNumber && node.canConvertToInt() && node.asInt() >= atLeast) {
                "$name must be a whole number of $atLeast or more, not $node"
            }
            return node.asInt()
        }

        private fun JsonNode?.flag(key: String): Boolean? {
            val node = this?.get(key) ?: return null
            require(node.isBoolean) { "$key must be true or false, not $node" }
            return node.asBoolean()
        }

        private fun JsonNode?.toaSettings(): ToaSettings {
            if (this == null) {
                return ToaSettings()
            }
            val paths = whole("pathsRequired", atLeast = 1, path = "toa") ?: 4
            require(paths <= 4) { "toa.pathsRequired must be 1 to 4, not $paths" }
            val unique = number("uniqueMultiplier", above = 0.0, path = "toa") ?: 1.0
            val teamUniques = whole("teamUniquesFromLevel", atLeast = 0, path = "toa") ?: 0
            return ToaSettings(paths, unique, teamUniques)
        }

        private fun JsonNode?.tobSettings(): TobSettings {
            if (this == null) {
                return TobSettings()
            }
            val cap = whole("partyCap", atLeast = 1, path = "tob") ?: TobSettings.DEFAULT_PARTY_CAP
            require(cap <= TobSettings.MAX_PARTY_CAP) {
                "tob.partyCap must be 1 to ${TobSettings.MAX_PARTY_CAP}, not $cap"
            }
            val unique = number("uniqueMultiplier", above = 0.0, path = "tob") ?: 1.0
            val teamUniques =
                get("teamUniques")?.let { node ->
                    require(node.isBoolean) { "tob.teamUniques must be true or false, not $node" }
                    node.asBoolean()
                } ?: true
            return TobSettings(cap, unique, teamUniques)
        }

        private fun JsonNode?.coxSettings(): CoxSettings {
            if (this == null) {
                return CoxSettings()
            }
            val cap = whole("partyCap", atLeast = 1, path = "cox") ?: CoxSettings.DEFAULT_PARTY_CAP
            require(cap <= CoxSettings.MAX_PARTY_CAP) {
                "cox.partyCap must be 1 to ${CoxSettings.MAX_PARTY_CAP}, not $cap"
            }
            val unique = number("uniqueMultiplier", above = 0.0, path = "cox") ?: 1.0
            val teamUniques =
                get("teamUniques")?.let { node ->
                    require(node.isBoolean) { "cox.teamUniques must be true or false, not $node" }
                    node.asBoolean()
                } ?: true
            val from = whole("teamUniquesFromPoints", atLeast = 0, path = "cox") ?: 0
            return CoxSettings(cap, unique, teamUniques, from)
        }

        private fun JsonNode?.colosseumSettings(): ColosseumSettings {
            if (this == null) {
                return ColosseumSettings()
            }
            val mode =
                get("mode")?.let { raw ->
                    ColosseumMode.fromKey(raw.asText())
                        ?: throw IllegalArgumentException(
                            "colosseum.mode must be \"full\" or \"shortened\", not $raw"
                        )
                } ?: ColosseumMode.Full
            val shortened =
                get("shortenedWaves")?.let { node ->
                    require(node.isArray && node.all { it.isIntegralNumber }) {
                        "colosseum.shortenedWaves must be a list of wave numbers, not $node"
                    }
                    val waves = node.map { it.asInt() }
                    require(ColosseumSettings.validShortened(waves)) {
                        "colosseum.shortenedWaves must rise from 1 to 12 and end on wave 12, " +
                            "not $node"
                    }
                    waves
                } ?: ColosseumSettings.DEFAULT_SHORTENED
            return ColosseumSettings(
                mode = mode,
                shortenedWaves = shortened,
                bankChestGlory = whole("bankChestGlory", atLeast = 0, path = "colosseum") ?: 0,
                ringGlory = whole("ringGlory", atLeast = 0, path = "colosseum") ?: 0,
            )
        }

        private fun JsonNode?.pvpSettings(): PvpSettings {
            if (this == null) {
                return PvpSettings()
            }
            return PvpSettings(
                soulWarsArena = flag("soulWarsArena") ?: false,
                revenantCaves = flag("revenantCaves") ?: false,
                wildernessBossLairs = flag("wildernessBossLairs") ?: false,
                escapeCaves = flag("escapeCaves") ?: false,
            )
        }

        /** A `{ "<relic tier>": multiplier }` object, keyed by tier number from 1. */
        private fun JsonNode?.tierMultipliers(key: String, path: String): SortedMap<Int, Double>? {
            val node = this?.get(key) ?: return null
            require(node.isObject) { "$path.$key must be a JSON object, not $node" }
            val result = sortedMapOf<Int, Double>()
            for ((name, value) in node.fields()) {
                val tier = name.toIntOrNull()
                require(tier != null && tier in 1..RELIC_TIERS) {
                    "$path.$key: \"$name\" is not a relic tier from 1 to $RELIC_TIERS"
                }
                require(value.isNumber && value.asDouble().isFinite() && value.asDouble() > 0.0) {
                    "$path.$key.$name must be a number above 0, not $value"
                }
                result[tier] = value.asDouble()
            }
            return result
        }

        private fun JsonNode.thresholds(): List<Int>? {
            val node = get("relicPointThresholds") ?: return null
            require(node.isArray && node.size() == RELIC_TIERS) {
                "relicPointThresholds must be a list of $RELIC_TIERS numbers (one per relic tier), not $node"
            }
            val list = node.map { it }
            require(list.all { it.isIntegralNumber && it.canConvertToInt() && it.asInt() >= 0 }) {
                "relicPointThresholds must hold whole numbers of 0 or more, not $node"
            }
            val values = list.map { it.asInt() }
            require(values.zipWithNext().all { (a, b) -> a <= b }) {
                "relicPointThresholds must not go down from one tier to the next, not $node"
            }
            return values
        }

        private fun JsonNode.taskPoints(): Map<String, Int>? {
            val node = get("taskPoints") ?: return null
            require(node.isObject) { "taskPoints must be a JSON object, not $node" }
            val result = DEFAULT_TASK_POINTS.toMutableMap()
            for ((name, value) in node.fields()) {
                require(name in DEFAULT_TASK_POINTS) {
                    "taskPoints: \"$name\" is not a task tier (${DEFAULT_TASK_POINTS.keys.joinToString()})"
                }
                require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() >= 0) {
                    "taskPoints.$name must be a whole number of 0 or more, not $value"
                }
                result[name] = value.asInt()
            }
            return result
        }
    }
}
