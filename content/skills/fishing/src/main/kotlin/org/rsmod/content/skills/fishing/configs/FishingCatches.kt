package org.rsmod.content.skills.fishing.configs

import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType

internal object FishingObjs : ObjReferences() {
    val small_net = find("net")
    val big_net = find("big_net")
    val lobster_pot = find("lobster_pot")
    val fishing_rod = find("fishing_rod")
    val fly_fishing_rod = find("fly_fishing_rod")
    val fishing_bait = find("fishing_bait")
    val feather = find("feather")
    val karambwan_vessel = find("tbwt_karambwan_vessel")
    val karambwan_vessel_loaded = find("tbwt_karambwan_vessel_loaded_with_karambwanji")

    val harpoon = find("harpoon")
    val dragon_harpoon = find("dragon_harpoon")
    val dragon_harpoon_or = find("trailblazer_harpoon_no_infernal")
    val infernal_harpoon = find("infernal_harpoon")
    val infernal_harpoon_uncharged = find("infernal_harpoon_empty")
    val infernal_harpoon_or = find("trailblazer_harpoon")
    val infernal_harpoon_or_uncharged = find("trailblazer_harpoon_empty")
    val trailblazer_reloaded_harpoon = find("trailblazer_reloaded_harpoon")
    val trailblazer_reloaded_harpoon_empty = find("trailblazer_reloaded_harpoon_empty")
    val trailblazer_reloaded_harpoon_no_infernal = find("trailblazer_reloaded_harpoon_no_infernal")
    val crystal_harpoon = find("crystal_harpoon")
    val crystal_harpoon_inactive = find("crystal_harpoon_inactive")
    val league_trailblazer_harpoon = find("league_trailblazer_harpoon")

    val raw_shrimp = find("raw_shrimp")
    val raw_anchovies = find("raw_anchovies")
    val raw_sardine = find("raw_sardine")
    val raw_herring = find("raw_herring")
    val raw_pike = find("raw_pike")
    val raw_trout = find("raw_trout")
    val raw_salmon = find("raw_salmon")
    val raw_lobster = find("raw_lobster")
    val raw_tuna = find("raw_tuna")
    val raw_swordfish = find("raw_swordfish")
    val raw_shark = find("raw_shark")
    val raw_monkfish = find("raw_monkfish")
    val raw_mackerel = find("raw_mackerel")
    val raw_cod = find("raw_cod")
    val raw_bass = find("raw_bass")
    val raw_karambwanji = find("tbwt_raw_karambwanji")
    val raw_karambwan = find("tbwt_raw_karambwan")
}

internal object FishingSeqs : SeqReferences() {
    val small_net = find("human_smallnet")
    val big_net = find("human_largenet")
    val lobster_pot = find("human_lobster")
    val rod_lure = find("human_fishing_casting")
    val rod_bait = find("human_fish_onspot")
    val karambwan_vessel = find("human_octopus_pot")

    val harpoon = find("human_harpoon")
    val harpoon_dragon = find("human_harpoon_dragon")
    val harpoon_dragon_or = find("human_harpoon_trailblazer_no_infernal")
    val harpoon_infernal = find("human_harpoon_infernal")
    val harpoon_infernal_or = find("human_harpoon_trailblazer")
    val harpoon_reloaded = find("human_harpoon_trailblazer_reloaded")
    val harpoon_reloaded_no_infernal = find("human_harpoon_trailblazer_reloaded_no_infernal")
    val harpoon_crystal = find("human_harpoon_crystal")
    val harpoon_league_trailblazer = find("human_harpoon_league_trailblazer")
}

/**
 * Fishing spots whose options do not follow the usual net/bait/lure/cage/harpoon pairings. These
 * are matched by their npc type instead of by their option text.
 */
internal object FishingNpcs : NpcReferences() {
    /** The Piscatoris Fishing Colony spot: net for monkfish, harpoon for tuna and swordfish. */
    val monkfish_spots = listOf(find("swan_fishingspot"))
    val karambwanji_spots = listOf(find("0_43_47_karambwanji"), find("general_karambwanji"))
    val karambwan_spots =
        listOf(find("0_45_48_karambwan"), find("general_karambwan"), find("lubufu_karambwan"))
}

internal enum class FishingMethod(
    /** Chat message shown when the player first starts fishing with this method. */
    val startMessage: String,
    /** Chat message when the player is missing the required tool. */
    val missingToolMessage: String,
) {
    SmallNet(
        startMessage = "You cast out your net...",
        missingToolMessage = "You need a small fishing net to catch these fish.",
    ),
    SeaBait(
        startMessage = "You cast out your line...",
        missingToolMessage = "You need a fishing rod to fish at this spot.",
    ),
    RiverBait(
        startMessage = "You cast out your line...",
        missingToolMessage = "You need a fishing rod to fish at this spot.",
    ),
    FlyRod(
        startMessage = "You cast out your line...",
        missingToolMessage = "You need a fly fishing rod to fish at this spot.",
    ),
    LobsterPot(
        startMessage = "You attempt to catch a lobster.",
        missingToolMessage = "You need a lobster pot to catch lobsters.",
    ),
    Harpoon(
        startMessage = "You start harpooning fish.",
        missingToolMessage = "You need a harpoon to catch these fish.",
    ),
    BigNet(
        startMessage = "You cast out your net...",
        missingToolMessage = "You need a big fishing net to catch these fish.",
    ),
    MonkfishNet(
        startMessage = "You cast out your net...",
        missingToolMessage = "You need a small fishing net to catch these fish.",
    ),
    SharkHarpoon(
        startMessage = "You start harpooning fish.",
        missingToolMessage = "You need a harpoon to catch these fish.",
    ),
    Karambwanji(
        startMessage = "You cast out your net...",
        missingToolMessage = "You need a small fishing net to catch these fish.",
    ),
    Karambwan(
        startMessage = "You start fishing for karambwan.",
        missingToolMessage = "You need a karambwan vessel to catch these fish.",
    ),
}

/**
 * A catchable fish.
 *
 * [low] and [high] are the wiki's success rate values: the catch chance at level 1 and level 99,
 * out of 256, linearly interpolated between the two by the player's Fishing level.
 */
internal class FishDef(
    val raw: ObjType,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val plural: String,
    val catchMessage: String,
)

internal object FishingCatches {
    val shrimp =
        FishDef(FishingObjs.raw_shrimp, 1, 10.0, 48, 256, "shrimps", "You catch some shrimps.")
    val anchovies =
        FishDef(
            FishingObjs.raw_anchovies,
            15,
            40.0,
            24,
            128,
            "anchovies",
            "You catch some anchovies.",
        )
    val sardine =
        FishDef(FishingObjs.raw_sardine, 5, 20.0, 32, 192, "sardines", "You catch a sardine.")
    val herring =
        FishDef(FishingObjs.raw_herring, 10, 30.0, 24, 128, "herrings", "You catch a herring.")
    val pike = FishDef(FishingObjs.raw_pike, 25, 60.0, 16, 96, "pike", "You catch a pike.")
    val trout = FishDef(FishingObjs.raw_trout, 20, 50.0, 32, 192, "trout", "You catch a trout.")
    val salmon = FishDef(FishingObjs.raw_salmon, 30, 70.0, 16, 96, "salmon", "You catch a salmon.")
    val lobster =
        FishDef(FishingObjs.raw_lobster, 40, 90.0, 6, 95, "lobsters", "You catch a lobster.")
    val tuna = FishDef(FishingObjs.raw_tuna, 35, 80.0, 8, 64, "tuna", "You catch a tuna.")
    val swordfish =
        FishDef(FishingObjs.raw_swordfish, 50, 100.0, 4, 48, "swordfish", "You catch a swordfish.")
    val shark = FishDef(FishingObjs.raw_shark, 76, 110.0, 3, 40, "sharks", "You catch a shark.")
    val monkfish =
        FishDef(FishingObjs.raw_monkfish, 62, 120.0, 48, 90, "monkfish", "You catch a monkfish.")
    val mackerel =
        FishDef(FishingObjs.raw_mackerel, 16, 20.0, 5, 65, "mackerel", "You catch a mackerel.")
    val cod = FishDef(FishingObjs.raw_cod, 23, 45.0, 4, 55, "cod", "You catch a cod.")
    val bass = FishDef(FishingObjs.raw_bass, 46, 100.0, 3, 40, "bass", "You catch a bass.")
    val karambwanji =
        FishDef(
            FishingObjs.raw_karambwanji,
            5,
            5.0,
            100,
            250,
            "karambwanji",
            "You catch a karambwanji.",
        )
    val karambwan =
        FishDef(FishingObjs.raw_karambwan, 65, 50.0, 5, 160, "karambwan", "You catch a karambwan.")

    private val pools: Map<FishingMethod, List<FishDef>> =
        mapOf(
            FishingMethod.SmallNet to listOf(shrimp, anchovies),
            FishingMethod.SeaBait to listOf(sardine, herring),
            FishingMethod.RiverBait to listOf(pike),
            FishingMethod.FlyRod to listOf(trout, salmon),
            FishingMethod.LobsterPot to listOf(lobster),
            FishingMethod.Harpoon to listOf(tuna, swordfish),
            FishingMethod.BigNet to listOf(mackerel, cod, bass),
            FishingMethod.MonkfishNet to listOf(monkfish),
            FishingMethod.SharkHarpoon to listOf(shark),
            FishingMethod.Karambwanji to listOf(karambwanji),
            FishingMethod.Karambwan to listOf(karambwan),
        )

    fun pool(method: FishingMethod): List<FishDef> = pools.getValue(method)

    /** Fish from the [method] pool that [level] can catch, ordered from highest to lowest level. */
    fun eligible(method: FishingMethod, level: Int): List<FishDef> =
        pool(method).filter { level >= it.level }.sortedByDescending { it.level }

    /** The lowest Fishing level that can catch anything with [method]. */
    fun minLevel(method: FishingMethod): Int = pool(method).minOf { it.level }
}

/** The two methods offered by a fishing spot, by the option slot they appear in (op1 and op3). */
internal data class SpotMethods(val first: FishingMethod, val second: FishingMethod?)

internal object FishingSpots {
    /**
     * Resolves the methods a spot with the given first and third option text offers, or `null` if
     * the option pairing is not one we handle.
     */
    fun fromOps(op1: String?, op3: String?): SpotMethods? =
        when {
            (op1 == "Small Net" || op1 == "Net") && op3 == "Bait" ->
                SpotMethods(FishingMethod.SmallNet, FishingMethod.SeaBait)
            op1 == "Lure" && op3 == "Bait" ->
                SpotMethods(FishingMethod.FlyRod, FishingMethod.RiverBait)
            op1 == "Cage" && op3 == "Harpoon" ->
                SpotMethods(FishingMethod.LobsterPot, FishingMethod.Harpoon)
            // Shark spots. Some of them label the big net option as just "Net".
            (op1 == "Big Net" || op1 == "Net") && op3 == "Harpoon" ->
                SpotMethods(FishingMethod.BigNet, FishingMethod.SharkHarpoon)
            else -> null
        }
}
