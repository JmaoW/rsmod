package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.api.drops.DropResult
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.ObjType

/** How hard a monster of the maze is, which decides what it drops and what it is worth. */
enum class GauntletTier(
    /** Reward points for a kill (wiki: weak 2, strong 5, demi-boss 10). */
    val points: Int
) {
    Weak(2),
    Strong(5),
    Demi(10),
}

/** The nine crystalline monsters (wiki), with the demi-bosses' components. */
enum class GauntletMonster(val tier: GauntletTier) {
    Rat(GauntletTier.Weak),
    Spider(GauntletTier.Weak),
    Bat(GauntletTier.Weak),
    Unicorn(GauntletTier.Strong),
    Scorpion(GauntletTier.Strong),
    Wolf(GauntletTier.Strong),
    Bear(GauntletTier.Demi),
    Dragon(GauntletTier.Demi),
    DarkBeast(GauntletTier.Demi);

    /** The monster as it stands in the maze. */
    fun type(mode: GauntletMode): NpcType =
        when (this) {
            Rat -> if (mode.corrupted) gauntlet_npcs.rat_hm else gauntlet_npcs.rat
            Spider -> if (mode.corrupted) gauntlet_npcs.spider_hm else gauntlet_npcs.spider
            Bat -> if (mode.corrupted) gauntlet_npcs.bat_hm else gauntlet_npcs.bat
            Unicorn -> if (mode.corrupted) gauntlet_npcs.unicorn_hm else gauntlet_npcs.unicorn
            Scorpion -> if (mode.corrupted) gauntlet_npcs.scorpion_hm else gauntlet_npcs.scorpion
            Wolf -> if (mode.corrupted) gauntlet_npcs.wolf_hm else gauntlet_npcs.wolf
            Bear -> if (mode.corrupted) gauntlet_npcs.bear_hm else gauntlet_npcs.bear
            Dragon -> if (mode.corrupted) gauntlet_npcs.dragon_hm else gauntlet_npcs.dragon
            DarkBeast ->
                if (mode.corrupted) gauntlet_npcs.dark_beast_hm else gauntlet_npcs.dark_beast
        }

    /** The component a demi-boss drops first (bear: spike, dragon: orb, dark beast: bowstring). */
    fun component(items: GauntletItemSet): ObjType? =
        when (this) {
            Bear -> items.spike
            Dragon -> items.orb
            DarkBeast -> items.bowstring
            else -> null
        }

    /** Whether it shoots from a distance rather than fighting next to its target. */
    val shoots: Boolean
        get() = this == Dragon || this == DarkBeast

    companion object {
        val WEAK = entries.filter { it.tier == GauntletTier.Weak }
        val STRONG = entries.filter { it.tier == GauntletTier.Strong }

        fun of(type: NpcType): GauntletMonster? =
            entries.firstOrNull { GauntletMode.entries.any { mode -> it.type(mode).id == type.id } }
    }
}

/** What one player has done in a run, for the drops that depend on it. */
class GauntletPlayerState {
    /** Kills of each tier. */
    val kills = IntArray(GauntletTier.entries.size)

    /** A strong monster has already dropped a weapon frame. */
    var strongFrame: Boolean = false

    /** Components that have dropped for this player in this run, by obj id. */
    val components: MutableSet<Int> = HashSet()
}

/**
 * What a monster of the maze drops (wiki, the Corrupted Gauntlet pages, whose weights are exact;
 * the crystalline pages only say common and uncommon, so both versions use these):
 * - weak: 20-30 shards; then nothing 9/24, 3-7 shards 9/24, 1-3 raw paddlefish 3/24, a grym leaf
 *   2/24, a teleport crystal 1/24. A weapon frame 1 in 4, and always from the first weak kill.
 * - strong: 80-100 shards; then 7-14 shards 9/21, nothing 6/21, 2-4 paddlefish 3/21, a grym leaf
 *   2/21, a teleport crystal 1/21. A weapon frame 2 in 7, and always by the second strong kill.
 * - demi-boss: 50-60 shards and a weapon frame; its own component if the player has not got it,
 *   otherwise any they have not got at equal chance, none once they have all three; then nothing
 *   3/18, 10-21 shards 9/18, 3-5 paddlefish 3/18, a grym leaf 2/18, a teleport crystal 1/18.
 */
object GauntletDrops {
    private class Line(
        val weight: Int,
        val what: ((GauntletItemSet) -> ObjType)?,
        val min: Int,
        val max: Int,
    )

    private class Table(
        val shardsMin: Int,
        val shardsMax: Int,
        val frameOdds: Pair<Int, Int>?,
        val rolls: List<Line>,
    )

    private val weak =
        Table(
            20,
            30,
            1 to 4,
            listOf(
                Line(9, null, 0, 0),
                Line(9, { it.shard }, 3, 7),
                Line(3, { GauntletShared.rawFood }, 1, 3),
                Line(2, { it.grymLeaf }, 1, 1),
                Line(1, { it.teleportCrystal }, 1, 1),
            ),
        )
    private val strong =
        Table(
            80,
            100,
            2 to 7,
            listOf(
                Line(9, { it.shard }, 7, 14),
                Line(6, null, 0, 0),
                Line(3, { GauntletShared.rawFood }, 2, 4),
                Line(2, { it.grymLeaf }, 1, 1),
                Line(1, { it.teleportCrystal }, 1, 1),
            ),
        )
    private val demi =
        Table(
            50,
            60,
            null,
            listOf(
                Line(3, null, 0, 0),
                Line(9, { it.shard }, 10, 21),
                Line(3, { GauntletShared.rawFood }, 3, 5),
                Line(2, { it.grymLeaf }, 1, 1),
                Line(1, { it.teleportCrystal }, 1, 1),
            ),
        )

    private fun table(tier: GauntletTier) =
        when (tier) {
            GauntletTier.Weak -> weak
            GauntletTier.Strong -> strong
            GauntletTier.Demi -> demi
        }

    /**
     * The drops for a kill of [monster] by a player whose record is [state]. Counts the kill in
     * [state]. [owns] says whether the player holds or wears an obj; [pick] is a random number
     * below its argument.
     */
    fun roll(
        monster: GauntletMonster,
        items: GauntletItemSet,
        state: GauntletPlayerState,
        owns: (ObjType) -> Boolean,
        pick: (Int) -> Int,
    ): List<DropResult> {
        val table = table(monster.tier)
        val drops = ArrayList<DropResult>()
        drops += DropResult(items.shard, between(pick, table.shardsMin, table.shardsMax))
        state.kills[monster.tier.ordinal]++
        val kills = state.kills[monster.tier.ordinal]

        val odds = table.frameOdds
        if (odds == null) {
            drops += DropResult(items.frame, 1)
        } else {
            // Wiki: always from the first weak kill; by the second strong kill at the latest.
            val guaranteed =
                (monster.tier == GauntletTier.Weak && kills == 1) ||
                    (monster.tier == GauntletTier.Strong && kills == 2 && !state.strongFrame)
            if (guaranteed || pick(odds.second) < odds.first) {
                drops += DropResult(items.frame, 1)
                if (monster.tier == GauntletTier.Strong) {
                    state.strongFrame = true
                }
            }
        }

        val own = monster.component(items)
        if (own != null) {
            val all = listOf(items.spike, items.orb, items.bowstring)
            val missing = all.filter { !owns(it) && it.id !in state.components }
            val chosen =
                when {
                    own in missing -> own
                    missing.isNotEmpty() -> missing[pick(missing.size)]
                    else -> null
                }
            if (chosen != null) {
                state.components += chosen.id
                drops += DropResult(chosen, 1)
            }
        }

        val total = table.rolls.sumOf { it.weight }
        var roll = pick(total)
        for (line in table.rolls) {
            if (roll < line.weight) {
                line.what?.let { drops += DropResult(it(items), between(pick, line.min, line.max)) }
                break
            }
            roll -= line.weight
        }
        return drops
    }

    private fun between(pick: (Int) -> Int, min: Int, max: Int) = min + pick(max - min + 1)
}
