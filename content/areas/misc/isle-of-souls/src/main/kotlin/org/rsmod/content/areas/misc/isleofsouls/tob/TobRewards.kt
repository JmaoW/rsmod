package org.rsmod.content.areas.misc.isleofsouls.tob

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
import org.rsmod.content.areas.misc.isleofsouls.pets.PetAward
import org.rsmod.content.areas.misc.isleofsouls.pets.Pets
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaItems
import org.rsmod.content.interfaces.collectionlog.CollectionKillCounts
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/** One stack of a player's loot. */
class TobLootEntry(val obj: UnpackedObjType, val count: Int)

/**
 * A player's rolled loot, waiting in the treasure room: nothing is given or announced until they
 * open their chest. [unique] and [pet] are told to the party then; [cabbage] marks the loot of a
 * player who dealt no damage.
 */
class TobUnclaimed(
    val loot: List<TobLootEntry>,
    val unique: TobUnique?,
    val pet: Boolean,
    val cabbage: Boolean,
)

/** The theatre's uniques and their weights out of 19 (wiki). */
enum class TobUnique(val label: String, val weight: Int) {
    Hilt("Avernic defender hilt", 8),
    Rapier("Ghrazi rapier", 2),
    Staff("Sanguinesti staff (uncharged)", 2),
    Faceguard("Justiciar faceguard", 2),
    Chestguard("Justiciar chestguard", 2),
    Legguards("Justiciar legguards", 2),
    Scythe("Scythe of vitur (uncharged)", 1),
}

/** A common drop: its name, weight out of 30, the amounts, and whether it comes noted. */
class TobCommon(
    val name: String,
    val weight: Int,
    val min: Int,
    val max: Int,
    val noted: Boolean = false,
    /** The cache name when the display name is shared with another item (a quest's vial). */
    val internal: String? = null,
)

/**
 * The numbers behind the vault's chests (wiki, "Monumental chest"), apart from the rolling so they
 * can be checked on their own.
 */
object TobLootMath {
    /** A team without deaths gets a unique 1 time in 9.1, whatever its size. */
    const val UNIQUE_CHANCE = 1.0 / 9.1

    /**
     * Each death costs a fifth of the player's standing (TSPS's guess: the wiki gives no formula).
     */
    const val DEATH_PENALTY = 0.2

    const val COMMON_ROLLS = 3
    const val COMMON_ODDS = 30

    /** Tertiaries, scaled by the player's standing: the elite clue and Lil' Zik (wiki). */
    const val CLUE_CHANCE = 3.0 / 25
    const val PET_CHANCE = 1.0 / 650

    /**
     * The ornament kits only drop in Hard Mode in the real theatre, which this event does not run;
     * they are rolled here at the Hard Mode rates so the league's kit tasks can be done.
     */
    const val HOLY_KIT_CHANCE = 1.0 / 100
    const val SANGUINE_KIT_CHANCE = 1.0 / 150

    fun standing(deaths: Int): Double = (1.0 - DEATH_PENALTY * deaths).coerceAtLeast(0.0)

    val commons =
        listOf(
            TobCommon("Vial of blood", 2, 45, 60, internal = "vial_blood"),
            TobCommon("Death rune", 1, 500, 600),
            TobCommon("Blood rune", 1, 500, 600),
            TobCommon("Swamp tar", 1, 500, 600),
            TobCommon("Coal", 1, 500, 600, noted = true),
            TobCommon("Gold ore", 1, 300, 360, noted = true),
            TobCommon("Molten glass", 1, 200, 240, noted = true),
            TobCommon("Adamantite ore", 1, 130, 156, noted = true),
            TobCommon("Runite ore", 1, 60, 72, noted = true),
            TobCommon("Wine of zamorak", 1, 50, 60, noted = true),
            TobCommon("Potato cactus", 1, 50, 60, noted = true),
            TobCommon("Grimy cadantine", 1, 50, 60, noted = true),
            TobCommon("Grimy avantoe", 1, 40, 48, noted = true),
            TobCommon("Grimy toadflax", 1, 37, 44, noted = true),
            TobCommon("Grimy kwuarm", 1, 36, 43, noted = true),
            TobCommon("Grimy irit leaf", 1, 34, 40, noted = true),
            TobCommon("Grimy ranarr weed", 1, 30, 36, noted = true),
            TobCommon("Grimy snapdragon", 1, 27, 32, noted = true),
            TobCommon("Grimy lantadyme", 1, 26, 31, noted = true),
            TobCommon("Grimy dwarf weed", 1, 24, 28, noted = true),
            TobCommon("Grimy torstol", 1, 20, 24, noted = true),
            TobCommon("Battlestaff", 1, 15, 18, noted = true),
            TobCommon("Rune battleaxe", 1, 4, 4, noted = true),
            TobCommon("Rune platebody", 1, 4, 4, noted = true),
            TobCommon("Rune chainbody", 1, 4, 4, noted = true),
            TobCommon("Palm tree seed", 1, 3, 3),
            TobCommon("Yew seed", 1, 3, 3),
            TobCommon("Magic seed", 1, 3, 3),
            TobCommon("Mahogany seed", 1, 10, 12),
        )

    init {
        check(commons.sumOf { it.weight } == COMMON_ODDS) { "The common table must add up to 30." }
    }

    fun pickUnique(pick: (Int) -> Int): TobUnique {
        var point = pick(TobUnique.entries.sumOf { it.weight })
        for (unique in TobUnique.entries) {
            if (point < unique.weight) {
                return unique
            }
            point -= unique.weight
        }
        return TobUnique.Hilt
    }

    fun pickCommon(pick: (Int) -> Int): TobCommon {
        var point = pick(COMMON_ODDS)
        for (common in commons) {
            if (point < common.weight) {
                return common
            }
            point -= common.weight
        }
        return commons.first()
    }
}

/**
 * The vault's loot: rolled when Verzik falls, held in each player's own chest inventory (saved with
 * them, drawn by interface 23) until they take, bank or discard it, in the vault or later from the
 * Rewards Chest by Ver Sinhaza's bank.
 *
 * The unique chance is the wiki's 1 in 9.1, lowered by deaths, then boosted like the Tombs of
 * Amascut's: by the league's drop-rate relics (each player's multiplier weighted by their standing)
 * and `tob.uniqueMultiplier`, capped at certain. It goes to one player by damage dealt and
 * standing; with `tob.teamUniques` everyone else in the raid gets a unique of their own too, in
 * place of their common loot.
 */
@Singleton
class TobRewards
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val invTypes: InvTypeList,
    private val eventBus: EventBus,
    private val random: GameRandom,
    private val tracker: LeagueTaskTracker,
    private val relics: LeagueRelicTracker,
    private val kills: CollectionKillCounts,
    private val collectionLog: CollectionLog,
    private val pets: Pets,
) {
    private val items: ToaItems by lazy { ToaItems(objTypes) }

    /** The dice. Tests replace it. */
    var pick: (Int) -> Int = { random.of(it) }

    fun chest(player: Player): Inventory = player.invMap.getOrPut(invTypes[tob_invs.chests])

    fun hasLoot(player: Player): Boolean = chest(player).isNotEmpty()

    fun completions(player: Player): Int = player.vars[tob_varps.completions]

    // Rolling
    // ------------------------------------------------------------------------------------------

    fun rollRaid(raid: TobRaid) {
        val members = raid.active
        for (member in members) {
            countCompletion(member.player)
        }
        val winner = uniqueWinner(raid)
        raid.uniqueFound = winner != null
        raid.uniqueFinder = winner?.first?.player
        val teamUniques = winner != null && EventSettings.current.tob.teamUniques
        for (member in members) {
            val player = member.player
            val standing = TobLootMath.standing(member.deaths)
            val loot = ArrayList<TobLootEntry>()
            val unique =
                when {
                    winner == null -> null
                    winner.first === member -> winner.second
                    teamUniques -> TobLootMath.pickUnique(pick)
                    else -> null
                }
            val cabbage = unique == null && member.damage <= 0
            when {
                unique != null -> items.named(unique.label)?.let { loot += TobLootEntry(it, 1) }
                cabbage -> items.named("Cabbage")?.let { loot += TobLootEntry(it, 1) }
                else -> repeat(TobLootMath.COMMON_ROLLS) { common(standing)?.let(loot::add) }
            }
            val pet = tertiaries(player, standing, loot)
            member.unclaimed = TobUnclaimed(loot, unique, pet, cabbage)
        }
    }

    /**
     * [player] opens their chest in the treasure room: the loot rolled for them goes into it, and
     * the party hears of a unique or a pet. With [announce] off (they left the raid first) nobody
     * is told, and the loot waits in the Rewards Chest in Ver Sinhaza. `false` if nothing was
     * waiting.
     */
    fun claim(raid: TobRaid, player: Player, announce: Boolean = true): Boolean {
        val member = raid.member(player) ?: return false
        val unclaimed = member.unclaimed ?: return false
        member.unclaimed = null
        for (entry in unclaimed.loot) {
            player.invAdd(chest(player), entry.obj, entry.count)
        }
        sync(player)
        if (!announce) {
            if (unclaimed.loot.isNotEmpty()) {
                player.mes("Your unclaimed loot awaits you in the Rewards Chest in Ver Sinhaza.")
            }
            return true
        }
        val party = raid.active.map { it.player }.ifEmpty { listOf(player) }
        unclaimed.unique?.let { unique ->
            for (other in party) {
                other.mes(
                    "<col=ff0000>${player.displayName}</col> found something special: " +
                        "<col=ff0000>${unique.label}</col>"
                )
            }
        }
        if (unclaimed.pet) {
            for (other in party) {
                other.mes(
                    "<col=ff0000>${player.displayName} has a funny feeling like they would have " +
                        "been followed...</col>"
                )
            }
        }
        if (unclaimed.cabbage) {
            player.mes("You receive a cabbage, as you did not contribute to the fight.")
        }
        return true
    }

    /** The completion count goes up, with the game's message, and the league's tasks hear of it. */
    fun countCompletion(player: Player) {
        val count = completions(player) + 1
        VarPlayerIntMapSetter.set(player, tob_varps.completions, count)
        kills.add(player, tob_varps.clog_completions)
        player.mes("Your completed Theatre of Blood count is: <col=ff0000>$count</col>.")
        if (count >= 25) tracker.complete(player, TASK_25)
        if (count >= 50) tracker.complete(player, TASK_50)
        if (count >= 100) tracker.complete(player, TASK_100)
    }

    private fun uniqueWinner(raid: TobRaid): Pair<TobMember, TobUnique>? {
        val members = raid.members
        val averageStanding =
            members.sumOf { TobLootMath.standing(it.deaths) } / members.size.coerceAtLeast(1)
        val chance =
            min(
                TobLootMath.UNIQUE_CHANCE *
                    averageStanding *
                    relicBoost(raid) *
                    EventSettings.current.tob.uniqueMultiplier,
                1.0,
            )
        if (pick(PRECISION) >= chance * PRECISION) {
            return null
        }
        val unique = TobLootMath.pickUnique(pick)
        val member = byContribution(raid) ?: return null
        return member to unique
    }

    /**
     * The league's drop-rate relics (as for the Tombs of Amascut): each player's multiplier counts
     * by their standing, so a party all on one tier gets exactly that tier's boost.
     */
    fun relicBoost(raid: TobRaid): Double {
        var weight = 0.0
        var boosted = 0.0
        for (member in raid.members) {
            val standing = TobLootMath.standing(member.deaths).coerceAtLeast(0.01)
            weight += standing
            boosted += standing * relics.dropMultiplier(member.player)
        }
        return if (weight > 0) boosted / weight else 1.0
    }

    /** Who gets the unique: weighted by damage dealt and standing. A leaver's share is nobody's. */
    private fun byContribution(raid: TobRaid): TobMember? {
        val members = raid.members
        val weights =
            members.map {
                (it.damage.coerceAtLeast(1) * TobLootMath.standing(it.deaths) * 100).toInt()
            }
        val sum = weights.sum()
        if (sum <= 0) {
            return members.firstOrNull { !it.left }
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

    private fun common(standing: Double): TobLootEntry? {
        val drop = TobLootMath.pickCommon(pick)
        var obj = (drop.internal?.let(::byInternal) ?: items.named(drop.name)) ?: return null
        if (drop.noted) {
            obj = items.noted(obj) ?: obj
        }
        val rolled = drop.min + pick(drop.max - drop.min + 1)
        val count = (rolled * standing).toInt().coerceAtLeast(1)
        return TobLootEntry(obj, count)
    }

    /** Adds the tertiary drops to [loot]; `true` if the pet is among them. */
    private fun tertiaries(
        player: Player,
        standing: Double,
        loot: MutableList<TobLootEntry>,
    ): Boolean {
        if (roll(TobLootMath.CLUE_CHANCE * standing)) {
            items.named("Clue scroll (elite)")?.let { loot += TobLootEntry(it, 1) }
        }
        if (roll(TobLootMath.HOLY_KIT_CHANCE * standing)) {
            items.named("Holy ornament kit")?.let { loot += TobLootEntry(it, 1) }
        }
        if (roll(TobLootMath.SANGUINE_KIT_CHANCE * standing)) {
            items.named("Sanguine ornament kit")?.let { loot += TobLootEntry(it, 1) }
        }
        if (roll(TobLootMath.PET_CHANCE * standing * pets.oddsMultiplier)) {
            val pet = byInternal("verzikpet") ?: return false
            if (owns(player, pet)) {
                return false
            }
            loot += TobLootEntry(pet, 1)
            return true
        }
        return false
    }

    private fun byInternal(name: String): UnpackedObjType? =
        objTypes.values.firstOrNull { it.internalName == name }

    private fun roll(chance: Double): Boolean = pick(PRECISION) < chance * PRECISION

    private fun owns(player: Player, obj: UnpackedObjType): Boolean =
        obj in player.inv ||
            obj in player.worn ||
            obj in chest(player) ||
            obj in player.invMap.getOrPut(invTypes[invs.bank])

    /** Shows the Rewards Chest by the bank while the player has loot waiting. */
    fun sync(player: Player) {
        VarPlayerIntMapSetter.set(
            player,
            tob_varbits.should_have_loot,
            if (hasLoot(player)) 1 else 0,
        )
    }

    // Taking it
    // ------------------------------------------------------------------------------------------

    fun open(player: Player) {
        if (chest(player).isEmpty()) {
            player.mes("The chest is empty.")
            sync(player)
            return
        }
        player.ifOpenMainModal(tob_interfaces.chests, eventBus)
        redraw(player)
    }

    private fun redraw(player: Player) {
        val inv = chest(player)
        UpdateInventory.updateInvFull(player, inv)
        player.runClientScript(
            ITEM_OPS_SCRIPT,
            tob_components.chests_items.packed,
            inv.type.id,
            2,
            3,
            0,
            -1,
            "Take",
            "",
            "",
            "",
            "",
        )
        player.ifSetEvents(tob_components.chests_items, inv.indices, IfEvent.Op1, IfEvent.Op10)
        sync(player)
    }

    fun take(player: Player, slot: Int) {
        val inv = chest(player)
        val stack = inv[slot] ?: return
        val moved = intoInventory(player, stack.id, stack.count)
        if (moved > 0) {
            player.invDel(inv, stack.id, moved, slot = slot)
            collectionLog.receive(player, stack.id, moved)
        }
        redraw(player)
    }

    fun takeAll(player: Player, toBank: Boolean) {
        val inv = chest(player)
        for (slot in inv.indices.toList()) {
            val stack = inv[slot] ?: continue
            val moved =
                if (toBank) intoBank(player, stack.id, stack.count)
                else intoInventory(player, stack.id, stack.count)
            if (moved > 0) {
                player.invDel(inv, stack.id, moved, slot = slot)
                collectionLog.receive(player, stack.id, moved)
            }
        }
        redraw(player)
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
        claimPet(player, obj)?.let {
            return count
        }
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

    /**
     * A pet leaving the chest is handed out as pets are (follower, backpack, Probita), not put in
     * the inventory or bank like loot. The chest's claim logs it.
     */
    private fun claimPet(player: Player, obj: Int): PetAward? {
        val (pet, form) = pets.ofObj(obj) ?: return null
        return pets.award(player, pet, form, log = false)
    }

    private fun intoBank(player: Player, obj: Int, count: Int): Int {
        claimPet(player, obj)?.let {
            return count
        }
        val type = objTypes[obj] ?: return 0
        val bank = player.invMap.getOrPut(invTypes[invs.bank])
        if (!player.invAdd(bank, type, count).success) {
            player.mes("You need more space in your bank.")
            return 0
        }
        return count
    }

    companion object {
        const val ITEM_OPS_SCRIPT = 149
        private const val PRECISION = 1_000_000
        const val TASK_25 = 946
        const val TASK_50 = 947
        const val TASK_100 = 948
    }
}
