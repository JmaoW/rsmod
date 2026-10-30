package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/** What is waiting in the lobby chest for a player. */
enum class RewardKind(val code: Int) {
    /** The Hunllef was killed. */
    Completed(1),

    /** The run ended any other way with 50 or more points. */
    Incomplete(2),

    /** The run ended any other way with 1 to 49 points. */
    Junk(3);

    companion object {
        fun of(code: Int): RewardKind? = entries.firstOrNull { it.code == code }
    }
}

/** A rolled reward item, already as a note where the table says so. */
class RewardItem(val obj: ObjType, val count: Int)

/**
 * The reward chest in the lobby (wiki, Reward Chest (The Gauntlet)):
 * - defeating the Crystalline Hunllef: 5-9 crystal shards and two rolls on the regular table;
 * - defeating the Corrupted Hunllef: 7-12 crystal shards, the Gauntlet cape if not owned, and three
 *   rolls on the corrupted table;
 * - otherwise, by the run's points: 50 or more the incomplete table, 1-49 the junk table, 0
 *   nothing. Leaving by the teleport platform gives nothing; escaping at the barrier, dying or
 *   logging out are a normal loss.
 * - the tertiary items are rolled each on their own: an elite clue scroll, the crystal weapon and
 *   armour seeds, the enhanced crystal weapon seed and Youngllef.
 *
 * What a player has earned is kept as a number that is saved with them (a reward waits until it is
 * collected, and Bryn will not let them back in until then); what is in it is rolled when the chest
 * is opened. In a duo run each player has their own reward.
 */
class GauntletRewards
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
    private val announcer: LeagueAnnouncer,
    private val collectionLog: CollectionLog,
    private val relics: LeagueRelicTracker,
) {
    /** The noted form of an obj, by the obj's id. */
    private val notes: Map<Int, ObjType> by lazy {
        objTypes.values.filter { it.isCert }.associateBy { it.certlink }
    }

    /** What a run earned for a player who left it by [exit] holding [points]. */
    fun kindFor(exit: GauntletExit, points: Int): RewardKind? =
        when {
            exit == GauntletExit.Completed -> RewardKind.Completed
            exit == GauntletExit.Exit -> null
            points >= INCOMPLETE_POINTS -> RewardKind.Incomplete
            points > 0 -> RewardKind.Junk
            else -> null
        }

    /** Puts [kind] in the chest for [player]: it is theirs to collect. */
    fun award(player: Player, mode: GauntletMode, kind: RewardKind) {
        val code = kind.code + if (mode.corrupted) CORRUPTED_BIT else 0
        VarPlayerIntMapSetter.set(player, gauntlet_varps.reward, code)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.reward_available, 1)
    }

    /** The reward waiting for [player], if any. */
    fun pending(player: Player): Pair<RewardKind, GauntletMode>? {
        val code = player.vars[gauntlet_varps.reward]
        val kind = RewardKind.of(code and CORRUPTED_BIT.inv()) ?: return null
        return kind to GauntletMode.of(code and CORRUPTED_BIT != 0)
    }

    /** Shows the chest open (or shut) to match what is waiting. Called at login. */
    fun syncChest(player: Player) {
        val waiting = if (pending(player) != null) 1 else 0
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.reward_available, waiting)
    }

    /**
     * What a reward gives. [ownsCape] says whether the player already has the Gauntlet cape; [pick]
     * is a random number below its argument.
     */
    fun roll(
        kind: RewardKind,
        mode: GauntletMode,
        ownsCape: Boolean,
        multiplier: Double = 1.0,
        pick: (Int) -> Int,
    ): List<RewardItem> {
        val items = ArrayList<RewardItem>()
        when (kind) {
            RewardKind.Completed -> {
                val shards = if (mode.corrupted) 7..12 else 5..9
                items +=
                    RewardItem(
                        GauntletRewardObjs.prif_crystal_shard,
                        between(pick, shards.first, shards.last),
                    )
                if (mode.corrupted && !ownsCape) {
                    items += RewardItem(GauntletRewardObjs.gauntlet_crystalline_cape, 1)
                }
                val table =
                    if (mode.corrupted) GauntletRewardTables.corrupted
                    else GauntletRewardTables.crystalline
                repeat(if (mode.corrupted) 3 else 2) { items += weighted(table, pick) }
                for ((obj, odds) in tertiary(mode)) {
                    // The relics make the three seeds likelier, as the wiki's boosted list has
                    // them.
                    val boosted =
                        if (obj.id in BOOSTED) (odds / multiplier).toInt().coerceAtLeast(1)
                        else odds
                    if (pick(boosted) == 0) items += RewardItem(obj, 1)
                }
            }
            RewardKind.Incomplete -> items += line(GauntletRewardTables.incomplete, pick)
            RewardKind.Junk -> items += line(GauntletRewardTables.junk, pick)
        }
        return items
    }

    private fun tertiary(mode: GauntletMode): List<Pair<ObjType, Int>> =
        if (mode.corrupted) {
            listOf(
                GauntletRewardObjs.trail_elite_emote_exp1 to 20,
                GauntletRewardObjs.crystal_seed_old to 50,
                GauntletRewardObjs.prif_armour_seed to 50,
                GauntletRewardObjs.prif_weapon_seed_enhanced to 400,
                GauntletRewardObjs.gauntletpet_corrupt to 800,
            )
        } else {
            listOf(
                GauntletRewardObjs.trail_elite_emote_exp1 to 25,
                GauntletRewardObjs.crystal_seed_old to 120,
                GauntletRewardObjs.prif_armour_seed to 120,
                GauntletRewardObjs.prif_weapon_seed_enhanced to 2000,
                GauntletRewardObjs.gauntletpet to 2000,
            )
        }

    private fun weighted(table: List<Row>, pick: (Int) -> Int): RewardItem {
        var roll = pick(table.sumOf { it.weight })
        for (row in table) {
            if (roll < row.weight) return item(row, pick)
            roll -= row.weight
        }
        return item(table.last(), pick)
    }

    private fun line(table: List<Row>, pick: (Int) -> Int): RewardItem =
        item(table[pick(table.size)], pick)

    private fun item(row: Row, pick: (Int) -> Int): RewardItem {
        val count = between(pick, row.min, row.max)
        val note = if (row.noted) notes[row.obj.id] else null
        return RewardItem(note ?: row.obj, count)
    }

    private fun between(pick: (Int) -> Int, min: Int, max: Int) = min + pick(max - min + 1)

    /**
     * Opens the chest: one free slot is needed, and whatever does not fit goes on the ground.
     * Returns the items given, or `null` if nothing was waiting or there was no room.
     */
    fun ProtectedAccess.open(ownsCape: Boolean, pick: (Int) -> Int): List<RewardItem>? {
        val (kind, mode) =
            pending(player)
                ?: run {
                    mes("There is nothing waiting for you in this chest.")
                    return null
                }
        if (inv.isFull()) {
            mes("You need a free inventory space for whatever you may find in the chest.")
            return null
        }
        val items = roll(kind, mode, ownsCape, relics.dropMultiplier(player), pick)
        VarPlayerIntMapSetter.set(player, gauntlet_varps.reward, 0)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.reward_available, 0)
        mes("You open the chest.")
        for (item in items) {
            player.invAddOrDrop(objRepo, item.obj, count = item.count)
            collectionLog.receive(player, item.obj.id, item.count)
            announcer.drop(player, item.obj, item.count)
        }
        if (items.isNotEmpty()) {
            mes("You find some treasure in the chest!")
        }
        return items
    }

    companion object {
        /** Points a run needs for the incomplete table (wiki). */
        const val INCOMPLETE_POINTS = 50

        private const val CORRUPTED_BIT = 8

        /** The seeds: the wiki's list of drops that the league's drop-rate relics boost. */
        private val BOOSTED: Set<Int>
            get() =
                setOf(
                    GauntletRewardObjs.crystal_seed_old.id,
                    GauntletRewardObjs.prif_armour_seed.id,
                    GauntletRewardObjs.prif_weapon_seed_enhanced.id,
                )
    }
}
