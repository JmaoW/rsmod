package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.config.refs.invs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.interfaces.collectionlog.CollectionKillCounts
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * Finds the items the chest hands out by their display names, the way the drop tables do: the
 * internal name that matches the display name wins, then the lowest id. Notes are the cert whose
 * link is the base item.
 */
class ToaItems(objTypes: ObjTypeList) {
    private val byName: Map<String, UnpackedObjType> =
        objTypes.values
            .filter { !it.isCert && it.placeholdertemplate == 0 }
            .filter { it.name.isNotBlank() && !it.name.equals("null", ignoreCase = true) }
            .groupBy { it.name.lowercase() }
            .mapValues { (name, candidates) ->
                val wanted = name.replace(Regex("[^a-z0-9]+"), "_").trim('_')
                candidates.firstOrNull { it.internalName == wanted } ?: candidates.minBy { it.id }
            }

    private val notes: Map<Int, UnpackedObjType> =
        objTypes.values.filter { it.isCert }.associateBy { it.certlink }

    fun named(name: String): UnpackedObjType? = byName[name.lowercase()]

    fun noted(base: UnpackedObjType): UnpackedObjType? = notes[base.id]

    fun unique(unique: ToaUnique): UnpackedObjType? =
        named(
            when (unique) {
                ToaUnique.TumekensShadow -> "Tumeken's shadow (uncharged)"
                else -> unique.label
            }
        )
}

/** One stack in a player's chest. */
class ToaLootEntry(val obj: UnpackedObjType, val count: Int)

/**
 * The reward chest: rolls what the party earned when the Wardens fall and holds it in each player's
 * own chest inventory (saved with them, drawn by interface 771) until they take, bank or discard
 * it.
 *
 * Wiki (Chest (Tombs of Amascut)): the team may get one unique, with chance 1% per (10,500 - 20 x
 * level) of the team's summed loot points, given to a player by their share of those points;
 * whoever gets it takes no common rolls; everyone else gets three rolls on the common table sized
 * by their own loot points, or fossilised dung below 1,500 of them. The pet is one roll for the
 * team, given out the same way; the thread, the keris jewels and the clue are rolled on their own
 * for each player. The league's drop-rate relics ([relicBoost]) and [EventSettings]'
 * `toa.uniqueMultiplier` scale the unique chance for the event.
 */
@Singleton
class ToaRewards
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val invTypes: InvTypeList,
    private val eventBus: EventBus,
    private val random: GameRandom,
    private val raids: ToaRaids,
    private val tracker: LeagueTaskTracker,
    private val relics: LeagueRelicTracker,
    private val kills: CollectionKillCounts,
    private val collectionLog: CollectionLog,
) {
    private val items: ToaItems by lazy { ToaItems(objTypes) }

    /** The random numbers the dice use. Tests replace it. */
    var pick: (Int) -> Int = { random.of(it) }

    fun chest(player: Player): Inventory = player.invMap.getOrPut(invTypes[toa_invs.chests])

    fun hasLoot(player: Player): Boolean = chest(player).isNotEmpty()

    // Rolling
    // ------------------------------------------------------------------------------------------

    /** The Wardens fell: count the completion for everyone and fill their chests. */
    fun rollRaid(raid: ToaRaid) {
        val level = raid.finalRaidLevel
        val mode = ToaMode.of(level)
        val members = raid.active
        val before = members.associate { it.player.accountId to completions(it.player) }

        for (member in members) {
            kills.add(member.player, toa_varps.completions[mode.ordinal])
            completionTasks(member.player, mode)
        }

        val winner = uniqueWinner(raid, level)
        raid.uniqueFound = winner != null
        raid.uniqueFinder = winner?.first?.player
        val teamUniques = winner != null && level >= EventSettings.current.toa.teamUniquesFromLevel
        val petWinner = petWinner(raid, level)
        for (member in members) {
            val player = member.player
            val points = raid.points.lootPoints(player.accountId)
            val loot = ArrayList<ToaLootEntry>()
            val unique =
                when {
                    winner == null -> null
                    winner.first === member -> winner.second
                    // The league's team uniques: everyone else gets one of their own, in their
                    // own chest, in place of their common rolls.
                    teamUniques -> ToaLootMath.pickTeamUnique(level, pick)
                    else -> null
                }
            if (unique != null) {
                items.unique(unique)?.let { loot += ToaLootEntry(it, 1) }
                raid.active.forEach { other ->
                    other.player.mes(
                        "<col=ff0000>${player.displayName}</col> found something special: " +
                            "<col=ff0000>${unique.label}</col>"
                    )
                }
            } else if (ToaLootMath.givesDung(points)) {
                items.named("Fossilised dung")?.let { loot += ToaLootEntry(it, 1) }
            } else {
                repeat(COMMON_ROLLS) { commonRoll(points, level)?.let(loot::add) }
            }
            tertiaries(player, raid, before.getValue(player.accountId), points, loot)
            if (member === petWinner) {
                pet(player, raid, loot)
            }
            // The collection log hears of each item only when it is claimed (see [claimed]), so
            // its popup does not give the loot away before the chest is opened.
            for (entry in loot) {
                add(player, entry)
            }
            VarPlayerIntMapSetter.set(player, toa_varps.personal_contribution, points)
            sync(player)
            if (loot.isNotEmpty()) {
                player.mes("Your rewards await you in the chest.")
            }
        }
    }

    private fun uniqueWinner(raid: ToaRaid, level: Int): Pair<ToaMember, ToaUnique>? {
        val chance =
            min(
                ToaLootMath.uniqueChance(raid.points.teamLootPoints, level) *
                    relicBoost(raid) *
                    EventSettings.current.toa.uniqueMultiplier,
                1.0,
            )
        if (pick(PRECISION) >= chance * PRECISION) {
            return null
        }
        val unique = ToaLootMath.pickUnique(level, pick) ?: return null
        val member = byShare(raid) ?: return null
        return member to unique
    }

    /**
     * The league's drop-rate relics (monster drops' multiplier, 5x from tier 1 and 20x from tier 4
     * by default) applied to the wiki's unique chance after its 55% cap. Relics are per player, so
     * this is the team's average multiplier weighted by each player's loot points: a party all on
     * one tier gets exactly that tier's boost, and in a mixed party each player's boost counts for
     * what they earned. The pet is not boosted (Raging Echoes did not boost pets).
     */
    fun relicBoost(raid: ToaRaid): Double {
        var points = 0L
        var boosted = 0.0
        for (member in raid.members) {
            val earned = raid.points.lootPoints(member.player.accountId)
            points += earned
            boosted += earned * relics.dropMultiplier(member.player)
        }
        return if (points > 0) boosted / points else 1.0
    }

    /** Wiki: the pet is rolled like a unique, once for the team, and given out the same way. */
    private fun petWinner(raid: ToaRaid, level: Int): ToaMember? {
        val chance = ToaLootMath.petChance(raid.points.teamLootPoints, level)
        if (pick(PRECISION) >= chance * PRECISION) {
            return null
        }
        return byShare(raid)
    }

    /**
     * Wiki: the chance of a unique being yours is your share of the team's loot points. Players who
     * left keep their share (their points still count towards the chance), so a roll that lands on
     * one of them gives nobody the drop (a guess: the wiki does not say).
     */
    private fun byShare(raid: ToaRaid): ToaMember? {
        val members = raid.members
        val weights = members.map { raid.points.lootPoints(it.player.accountId) }
        val sum = weights.sum()
        if (sum <= 0) {
            return null
        }
        var point = pick(sum)
        for ((index, weight) in weights.withIndex()) {
            if (point < weight) {
                return members[index].takeIf { !it.left }
            }
            point -= weight
        }
        return null
    }

    private fun commonRoll(lootPoints: Int, level: Int): ToaLootEntry? {
        val drop = ToaLootMath.common[pick(ToaLootMath.COMMON_ODDS)]
        var obj = items.named(drop.name) ?: return null
        if (drop.noted) {
            obj = items.noted(obj) ?: obj
        }
        return ToaLootEntry(obj, ToaLootMath.commonQuantity(drop, lootPoints, level))
    }

    private fun tertiaries(
        player: Player,
        raid: ToaRaid,
        completionsBefore: Int,
        lootPoints: Int,
        loot: MutableList<ToaLootEntry>,
    ) {
        items.named("Thread of Elidinis")?.let { thread ->
            val chance = ToaLootMath.threadChance(completionsBefore, owns(player, thread))
            if (pick(PRECISION) < chance * PRECISION) {
                loot += ToaLootEntry(thread, 1)
            }
        }
        val jewels = JEWELS.mapNotNull { items.named(it) }
        val jewelChance = ToaLootMath.jewelChance(completionsBefore) * jewels.size
        if (jewels.isNotEmpty() && pick(PRECISION) < jewelChance * PRECISION) {
            val missing = jewels.filter { !owns(player, it) }.ifEmpty { jewels }
            loot += ToaLootEntry(missing[pick(missing.size)], 1)
        }
        // Mod Ash: the clue counts the player's own contribution.
        items.named("Clue scroll (elite)")?.let { clue ->
            if (pick(PRECISION) < ToaLootMath.eliteClueChance(lootPoints) * PRECISION) {
                loot += ToaLootEntry(clue, 1)
            }
        }
    }

    /** [player] won the team's pet roll; one they already own is not given again. */
    private fun pet(player: Player, raid: ToaRaid, loot: MutableList<ToaLootEntry>) {
        val pet = items.named("Tumeken's guardian") ?: return
        if (owns(player, pet)) {
            return
        }
        loot += ToaLootEntry(pet, 1)
        raid.active.forEach {
            it.player.mes(
                "<col=ff0000>${player.displayName} has a funny feeling like they would " +
                    "have been followed...</col>"
            )
        }
    }

    private fun owns(player: Player, obj: UnpackedObjType): Boolean =
        obj in player.inv ||
            obj in player.worn ||
            obj in chest(player) ||
            obj in player.invMap.getOrPut(invTypes[invs.bank])

    private fun completions(player: Player): Int = toa_varps.completions.sumOf { player.vars[it] }

    /** Completing the raid counts for the league tasks in Normal and Expert mode only. */
    private fun completionTasks(player: Player, mode: ToaMode) {
        if (!mode.countsForTasks) {
            return
        }
        val total = player.vars[toa_varps.completions[1]] + player.vars[toa_varps.completions[2]]
        tracker.complete(player, TASK_COMPLETE)
        if (total >= 25) tracker.complete(player, TASK_25)
        if (total >= 50) tracker.complete(player, TASK_50)
        if (total >= 100) tracker.complete(player, TASK_100)
    }

    private fun add(player: Player, entry: ToaLootEntry) {
        player.invAdd(chest(player), entry.obj, entry.count)
    }

    /** Shows the chest as full or empty: the vault and lobby chests switch on this. */
    fun sync(player: Player) {
        VarPlayerIntMapSetter.set(
            player,
            toa_varbits.should_have_loot,
            if (hasLoot(player)) 1 else 0,
        )
    }

    // Taking it
    // ------------------------------------------------------------------------------------------

    /** Opens the reward interface on the player's chest. */
    fun open(player: Player) {
        val inv = chest(player)
        if (inv.isEmpty()) {
            player.mes("There is nothing to claim.")
            sync(player)
            return
        }
        player.ifOpenMainModal(toa_interfaces.chests, eventBus)
        redraw(player)
    }

    private fun redraw(player: Player) {
        val inv = chest(player)
        UpdateInventory.updateInvFull(player, inv)
        player.runClientScript(
            ITEM_OPS_SCRIPT,
            toa_components.chests_items.packed,
            inv.type.id,
            2,
            3,
            0,
            -1,
            "Take",
            "Take-5",
            "Take-10",
            "Take-All",
            "",
        )
        player.ifSetEvents(
            toa_components.chests_items,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op10,
        )
        sync(player)
    }

    /** Take, Take-5, Take-10 or Take-All of one slot into the inventory. */
    fun take(player: Player, slot: Int, amount: Int) {
        val inv = chest(player)
        val stack = inv[slot] ?: return
        val wanted = min(amount, stack.count)
        val moved = intoInventory(player, stack.id, wanted)
        if (moved > 0) {
            player.invDel(inv, stack.id, moved, slot = slot)
            claimed(player, stack.id, moved)
        }
        redraw(player)
    }

    /** Moves everything it can into the inventory or the bank. */
    fun takeAll(player: Player, toBank: Boolean) {
        val inv = chest(player)
        if (inv.isEmpty()) {
            player.mes("There is nothing to ${if (toBank) "bank" else "put in your inventory"}.")
            return
        }
        for (slot in inv.indices.toList()) {
            val stack = inv[slot] ?: continue
            val moved =
                if (toBank) intoBank(player, stack.id, stack.count)
                else intoInventory(player, stack.id, stack.count)
            if (moved > 0) {
                player.invDel(inv, stack.id, moved, slot = slot)
                claimed(player, stack.id, moved)
            }
        }
        redraw(player)
    }

    /**
     * [count] of [obj] left the chest for the player's inventory or bank: only now does it count
     * for the collection log, whether claimed in the vault or later from the lobby's chest.
     */
    private fun claimed(player: Player, obj: Int, count: Int) {
        collectionLog.receive(player, obj, count)
    }

    fun discardAll(player: Player) {
        val inv = chest(player)
        for (slot in inv.indices) {
            inv[slot] = null
        }
        UpdateInventory.updateInvFull(player, inv)
        sync(player)
    }

    private fun intoInventory(player: Player, obj: Int, count: Int): Int {
        val type = objTypes[obj] ?: return 0
        val inventory = player.inv
        val fits =
            if (type.stackable) {
                if (inventory.hasFreeSpace() || inventory.count(type) > 0) count else 0
            } else {
                min(count, inventory.freeSpace())
            }
        if (fits <= 0) {
            player.mes("You don't have enough inventory space.")
            return 0
        }
        return if (player.invAdd(inventory, type, fits).success) fits else 0
    }

    private fun intoBank(player: Player, obj: Int, count: Int): Int {
        val type = objTypes[obj] ?: return 0
        val bank = player.invMap.getOrPut(invTypes[invs.bank])
        if (!player.invAdd(bank, type, count).success) {
            player.mes("You need more space in your bank.")
            return 0
        }
        return count
    }

    companion object {
        const val COMMON_ROLLS = 3
        const val ITEM_OPS_SCRIPT = 149
        private const val PRECISION = 1_000_000

        private val JEWELS =
            listOf(
                "Breach of the scarab",
                "Eye of the corruptor",
                "Jewel of the sun",
                "Jewel of Amascut",
            )

        const val TASK_COMPLETE = 1186
        const val TASK_25 = 1187
        const val TASK_50 = 1188
        const val TASK_100 = 1189
    }
}
