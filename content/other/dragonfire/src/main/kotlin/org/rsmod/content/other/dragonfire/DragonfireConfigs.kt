package org.rsmod.content.other.dragonfire

import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.type.npc.NpcType

/**
 * The dragons that breathe fire (OSRS Wiki, "Dragonfire"). Baby dragons cannot, so they are not
 * here.
 *
 * @param longRanged Whether the dragon breathes from a distance. The others only breathe when they
 *   are close enough to bite.
 * @param fireOdds Out of how many attacks next to the target one is a breath (the rest are melee).
 *   Past melee range a long-ranged dragon always breathes. The Wiki gives `6` for the chromatic
 *   dragons and nothing for the others: those are our guesses. The drake does not use it: it
 *   shoots, and breathes after every seven shots (see [DragonScript]).
 */
enum class DragonKind(val longRanged: Boolean, val fireOdds: Int) {
    Chromatic(longRanged = false, fireOdds = 6),
    Brutal(longRanged = true, fireOdds = 3),
    Metallic(longRanged = true, fireOdds = 3),
    KingBlackDragon(longRanged = true, fireOdds = 2),
    Elvarg(longRanged = true, fireOdds = 2),
    Drake(longRanged = true, fireOdds = 0),
}

object DragonNpcs : NpcReferences() {
    /** Green, blue, red and black dragons, the lava dragon and the reanimated dragon. */
    val chromatic =
        listOf(
            find("green_dragon"),
            find("green_dragon2"),
            find("green_dragon3"),
            find("green_dragon4"),
            find("green_dragon5"),
            find("green_dragon_myth_guild"),
            find("green_dragon_myth_guild_2"),
            find("wild_cave_green_dragon"),
            find("wild_cave_green_dragon2"),
            find("wild_cave_green_dragon3"),
            find("blue_dragon"),
            find("blue_dragon2"),
            find("blue_dragon3"),
            find("blue_dragon4"),
            find("blue_dragon5"),
            find("blue_dragon_strongholdcave_1"),
            find("blue_dragon_strongholdcave_2"),
            find("blue_dragon_strongholdcave_3"),
            find("blue_dragon_strongholdcave_4"),
            find("blue_dragon_strongholdcave_5"),
            find("blue_dragon_tapoyauik_1"),
            find("blue_dragon_tapoyauik_2"),
            find("red_dragon"),
            find("red_dragon2"),
            find("red_dragon3"),
            find("red_dragon4"),
            find("red_dragon5"),
            find("black_dragon"),
            find("black_dragon2"),
            find("black_dragon3"),
            find("black_dragon4"),
            find("black_dragon5"),
            find("black_dragon_strongholdcave_1"),
            find("black_dragon_strongholdcave_2"),
            find("black_dragon_strongholdcave_3"),
            find("wild_cave_black_dragon"),
            find("wild_cave_black_dragon2"),
            find("wild_cave_black_dragon3"),
            find("lava_dragon"),
            find("lava_dragon2"),
            find("lava_dragon3"),
            find("arceuus_reanimated_dragon"),
        )

    /** Brutal green, blue, red and black dragons. */
    val brutal =
        listOf(
            find("brut_green_dragon"),
            find("kourend_blue_dragon"),
            find("kourend_red_dragon"),
            find("kourend_black_dragon"),
            find("brutal_blue_dragon_tapoyauik"),
        )

    /** Bronze, iron, steel, mithril, adamant and rune dragons. */
    val metallic =
        listOf(
            find("bronze_dragon"),
            find("bronze_dragon_strongholdcave"),
            find("kourend_bronze_dragon"),
            find("iron_dragon"),
            find("iron_dragon_strongholdcave"),
            find("kourend_iron_dragon"),
            find("steel_dragon"),
            find("steel_dragon_strongholdcave"),
            find("kourend_steel_dragon"),
            find("brut_mithril_dragon"),
            find("adamant_dragon"),
            find("rune_dragon"),
        )

    val king_black_dragon = listOf(find("king_dragon"))

    val elvarg = listOf(find("elvarg"), find("elvarg_alive"))

    val drake = listOf(find("drake"))

    fun of(kind: DragonKind): List<NpcType> =
        when (kind) {
            DragonKind.Chromatic -> chromatic
            DragonKind.Brutal -> brutal
            DragonKind.Metallic -> metallic
            DragonKind.KingBlackDragon -> king_black_dragon
            DragonKind.Elvarg -> elvarg
            DragonKind.Drake -> drake
        }
}

object DragonObjs : ObjReferences() {
    /** Everything that blocks dragonfire from the shield slot, charged or not. */
    val shields =
        listOf(
            find("antidragonbreathshield"),
            find("dragonfire_shield"),
            find("dragonfire_shield_uncharged"),
            find("dragonfire_ward"),
            find("dragonfire_ward_uncharged"),
            find("wyvern_shield"),
            find("wyvern_shield_uncharged"),
        )
}

object DragonSeqs : SeqReferences() {
    val breath = find("dragon_firebreath_attack")

    /** The King Black Dragon's breath: tsps and the old games play 84, the "middle" breath. */
    val king_breath = find("dragon_firebreath_middle_attack")
    val drake_attack = find("drake_ranged")
}

object DragonSpotanims : SpotanimReferences() {
    /** The small fireball of the chromatic dragons. */
    val fireball = find("firebreath_travel")

    /** The breath of the metal, brutal and king dragons, then the King Black Dragon's others. */
    val fire = find("dragon_ranged_fire_attack")
    val toxic = find("dragon_ranged_toxic_attack")
    val ice = find("dragon_ranged_ice_attack")
    val shock = find("dragon_ranged_lightning_attack")

    /** The drake's arrows, its volcanic breath in flight and where the breath burns the player. */
    val drake_range = find("drake_range_proj")
    val drake_burn = find("drake_burn_proj")
    val drake_burn_impact = find("drake_burn_impact")
}

/**
 * The long-ranged dragons need a range to breathe from; the cache gives them melee range. Needs
 * `packCache`.
 */
internal object DragonNpcEdits : NpcEditor() {
    init {
        for (kind in DragonKind.entries) {
            if (!kind.longRanged) {
                continue
            }
            for (type in DragonNpcs.of(kind)) {
                edit(type) { attackRange = DragonScript.BREATH_RANGE }
            }
        }
    }
}
