package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.config.refs.invs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.pets.Pets
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaItems
import org.rsmod.content.interfaces.collectionlog.CollectionKillCounts
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** A common reward: its cache name, and the points one of it costs (131,071 / its most). */
class CoxCommon(val name: String, val perItem: Int, val noted: Boolean = true)

/** The numbers behind the ancient chest (Wiki, "Ancient chest" and "Chambers of Xeric"). */
object CoxLootMath {
    /** 1% for every 8,676 points, at most 65.7% (570,000 points). */
    const val POINTS_PER_PERCENT = 8676.0
    const val UNIQUE_CAP = 0.657
    const val COMMON_POINTS_CAP = 131071
    const val OLMLET_ODDS = 53
    const val CLUE_ODDS = 12
    const val TABLET_ODDS = 10
    const val KIT_ODDS = 75
    const val DUST_ODDS = 400
    const val CHALLENGE_BONUS = 5000

    /** The uniques and their weights (Wiki, after the 12 August 2026 update): normal, CM. */
    val UNIQUES: List<Triple<String, Int, Int>> =
        listOf(
            Triple("raids_prayerscroll", 14, 12),
            Triple("raids_prayerscroll_augury", 14, 12),
            Triple("twisted_buckler", 4, 4),
            Triple("dragonhunter_xbow", 4, 4),
            Triple("dinhs_bulwark", 3, 3),
            Triple("ancestral_hat", 4, 4),
            Triple("ancestral_robe_top", 4, 4),
            Triple("ancestral_robe_bottom", 4, 4),
            Triple("dragon_claws", 3, 3),
            Triple("elder_maul", 2, 2),
            Triple("kodai_insignia", 2, 2),
            Triple("twisted_bow", 2, 2),
        )

    /**
     * The common table, every entry 1 in 33 a roll (Wiki). The points per item are the Wiki's most
     * of each divided into 131,071 where the Wiki gives the most, otherwise from memory of the
     * Wiki's table (marked in the plan doc as unchecked).
     */
    val COMMONS =
        listOf(
            CoxCommon("deathrune", 36, noted = false),
            CoxCommon("bloodrune", 32, noted = false),
            CoxCommon("soulrune", 20, noted = false),
            CoxCommon("rune_arrow", 14, noted = false),
            CoxCommon("dragon_arrow", 202, noted = false),
            CoxCommon("unidentified_toadflax", 568),
            CoxCommon("unidentified_ranarr", 950),
            CoxCommon("unidentified_irit", 463),
            CoxCommon("unidentified_avantoe", 290),
            CoxCommon("unidentified_kwuarm", 334),
            CoxCommon("unidentified_snapdragon", 1131),
            CoxCommon("unidentified_cadantine", 334),
            CoxCommon("unidentified_lantadyme", 298),
            CoxCommon("unidentified_dwarf_weed", 240),
            CoxCommon("unidentified_torstol", 978),
            CoxCommon("silver_ore", 6),
            CoxCommon("coal", 6),
            CoxCommon("gold_ore", 44),
            CoxCommon("mithril_ore", 32),
            CoxCommon("adamantite_ore", 166),
            CoxCommon("runite_ore", 2016),
            CoxCommon("uncut_sapphire", 189),
            CoxCommon("uncut_emerald", 144),
            CoxCommon("uncut_ruby", 250),
            CoxCommon("uncut_diamond", 508),
            CoxCommon("lizardman_fang", 28, noted = false),
            CoxCommon("blankrune_high", 2),
            CoxCommon("hosidius_saltpetre", 12),
            CoxCommon("plank_teak", 96),
            CoxCommon("plank_mahogany", 238),
            CoxCommon("lovakengj_dynamite_fused", 54),
            CoxCommon("raids_prayerscroll_preserve", -1, noted = false),
            CoxCommon("raids_ancient_relic", -1, noted = false),
        )

    fun uniqueChance(totalPoints: Int): Double =
        min(totalPoints / POINTS_PER_PERCENT / 100.0, UNIQUE_CAP)

    /** How many of [common] [points] buys (at least one). */
    fun amount(common: CoxCommon, points: Int): Int =
        if (common.perItem <= 0) 1
        else (points.coerceAtMost(COMMON_POINTS_CAP) / common.perItem).coerceAtLeast(1)

    /** The Challenge Mode target times in ticks by team size (Wiki). */
    fun challengeTarget(size: Int): Int {
        val minutes =
            when {
                size <= 1 -> 70
                size == 2 -> 65
                size == 3 -> 50
                size == 4 -> 45
                size <= 10 -> 42
                size <= 15 -> 45
                size <= 23 -> 60
                else -> 80
            }
        return minutes * 100
    }
}

/**
 * The ancient chest's loot, rolled when the Great Olm falls and held in each raider's
 * `raids_rewards` inventory (saved with them) until taken:
 * - The unique chance is the Wiki's (1% per 8,676 team points, capped at 65.7%), times the league's
 *   drop-rate relic boost (each raider's weighted by their points) and `cox.uniqueMultiplier`,
 *   capped at certain. It goes to one raider weighted by personal points (a leaver's share is
 *   nobody's). With `cox.teamUniques` everyone else who started the raid gets a unique too, in
 *   place of their commons. The olmlet is 1 in 53 of uniques (unboosted).
 * - Otherwise two different commons, each as much as the raider's points buy (Wiki: scaled up to
 *   131,071 points).
 * - Tertiaries (Wiki): the dark journal unless banked, the ancient tablet 1 in 10 if not owned, an
 *   elite clue 1 in 12 without a unique; in Challenge Mode within the target time, +5,000 points
 *   each before rolling and the kit (1/75) and dust (1/400).
 */
@Singleton
class CoxRewards
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
    private val byName: Map<String, UnpackedObjType> by lazy {
        objTypes.values.mapNotNull { t -> t.internalName?.let { it to t } }.toMap()
    }

    var pick: (Int) -> Int = { random.of(it) }

    fun chest(player: Player): Inventory = player.invMap.getOrPut(invTypes[cox_invs.rewards])

    fun hasLoot(player: Player): Boolean = chest(player).isNotEmpty()

    private fun obj(name: String): UnpackedObjType? = byName[name]

    fun rollRaid(raid: CoxRaid) {
        val members = raid.raiders.filter { !it.left }
        val inTime =
            raid.challenge &&
                raid.completeCycle - raid.startCycle <=
                    CoxLootMath.challengeTarget(raid.scaling.size)
        if (inTime) {
            for (m in members) m.points += CoxLootMath.CHALLENGE_BONUS
        }
        for (member in members) countCompletion(member.player, raid.challenge)
        val total = raid.totalPoints
        val chance =
            min(
                CoxLootMath.uniqueChance(total) *
                    relicBoost(raid) *
                    EventSettings.current.cox.uniqueMultiplier,
                1.0,
            )
        val finder = if (pick(PRECISION) < chance * PRECISION) byPoints(raid) else null
        val settings = EventSettings.current.cox
        val teamUniques =
            finder != null && settings.teamUniques && total >= settings.teamUniquesFromPoints
        val purple = finder != null
        for (member in members) {
            val player = member.player
            val loot = ArrayList<Pair<UnpackedObjType, Int>>()
            val unique =
                when {
                    finder === member -> pickUnique(raid.challenge)
                    teamUniques -> pickUnique(raid.challenge)
                    else -> null
                }
            if (unique != null) {
                loot += unique to 1
                for (other in raid.active) {
                    other.player.mes(
                        "<col=ef20ff>Special loot:</col> <col=ff0000>${player.displayName} - ${unique.name}</col>"
                    )
                }
                val olmletOdds =
                    (CoxLootMath.OLMLET_ODDS / pets.oddsMultiplier).toInt().coerceAtLeast(1)
                if (pick(olmletOdds) == 0) {
                    obj("olmpet")?.let { pet ->
                        if (!owns(player, pet)) {
                            loot += pet to 1
                            raids(
                                raid,
                                "${player.displayName} has a funny feeling like they would have been followed...",
                            )
                        }
                    }
                }
            } else {
                commons(member.points).forEach(loot::add)
                if (pick(CoxLootMath.CLUE_ODDS) == 0)
                    items.named("Clue scroll (elite)")?.let { loot += it to 1 }
            }
            obj("raids_reward_book")?.let { journal ->
                if (!owns(player, journal)) loot += journal to 1
            }
            obj("raids_spellunlock_tablet")?.let { tablet ->
                if (!owns(player, tablet) && pick(CoxLootMath.TABLET_ODDS) == 0) loot += tablet to 1
            }
            if (inTime) {
                if (pick(CoxLootMath.KIT_ODDS) == 0)
                    obj("ancestral_robes_twisted_kit")?.let { loot += it to 1 }
                if (pick(CoxLootMath.DUST_ODDS) == 0)
                    obj("raids_challenge_morph")?.let { loot += it to 1 }
            }
            for ((type, count) in loot) player.invAdd(chest(player), type, count)
            VarPlayerIntMapSetter.set(player, cox_varbits.reward_indicator, if (purple) 2 else 1)
            player.mes("Your loot awaits you in the ancient chest.")
        }
    }

    private fun raids(raid: CoxRaid, text: String) {
        for (m in raid.active) m.player.mes("<col=ff0000>$text</col>")
    }

    private fun pickUnique(challenge: Boolean): UnpackedObjType? {
        val weights = CoxLootMath.UNIQUES.map { if (challenge) it.third else it.second }
        var point = pick(weights.sum())
        for ((i, w) in weights.withIndex()) {
            if (point < w) return obj(CoxLootMath.UNIQUES[i].first)
            point -= w
        }
        return obj(CoxLootMath.UNIQUES.last().first)
    }

    private fun commons(points: Int): List<Pair<UnpackedObjType, Int>> {
        val first = pick(CoxLootMath.COMMONS.size)
        var second = pick(CoxLootMath.COMMONS.size - 1)
        if (second >= first) second++
        return listOf(first, second).mapNotNull { index ->
            val common = CoxLootMath.COMMONS[index]
            var type = obj(common.name) ?: return@mapNotNull null
            val count = CoxLootMath.amount(common, points.coerceAtLeast(1))
            // A third of the herb rolls are seeds (Wiki): not modelled, the herbs always come.
            if (common.noted) type = items.noted(type) ?: type
            type to count
        }
    }

    /** The league's drop-rate relics, each raider's counted by their points. */
    fun relicBoost(raid: CoxRaid): Double {
        var weight = 0.0
        var boosted = 0.0
        for (member in raid.raiders) {
            val share = member.points.coerceAtLeast(1).toDouble()
            weight += share
            boosted += share * relics.dropMultiplier(member.player)
        }
        return if (weight > 0) boosted / weight else 1.0
    }

    private fun byPoints(raid: CoxRaid): CoxMember? {
        val members = raid.raiders
        val sum = members.sumOf { it.points.coerceAtLeast(0) } + raid.droppedPoints
        if (sum <= 0) return members.firstOrNull { !it.left }
        var point = pick(sum)
        for (member in members) {
            val w = member.points.coerceAtLeast(0)
            if (point < w) return member.takeIf { !it.left }
            point -= w
        }
        return null
    }

    fun countCompletion(player: Player, challenge: Boolean) {
        val varp = if (challenge) cox_varps.challenge_completions else cox_varps.completions
        val count = player.vars[varp] + 1
        VarPlayerIntMapSetter.set(player, varp, count)
        kills.add(
            player,
            if (challenge) cox_varps.clog_challenge_completions else cox_varps.clog_completions,
        )
        val label = if (challenge) "Chambers of Xeric Challenge Mode" else "Chambers of Xeric"
        player.mes("Your completed $label count is: <col=ff0000>$count</col>.")
        val total =
            player.vars[cox_varps.completions] + player.vars[cox_varps.challenge_completions]
        tracker.complete(player, TASK_1)
        if (total >= 25) tracker.complete(player, TASK_25)
        if (total >= 50) tracker.complete(player, TASK_50)
        if (total >= 100) tracker.complete(player, TASK_100)
    }

    private fun owns(player: Player, obj: UnpackedObjType): Boolean =
        obj in player.inv ||
            obj in player.worn ||
            obj in chest(player) ||
            obj in player.invMap.getOrPut(invTypes[invs.bank])

    // The chest
    // ---------------------------------------------------------------------------------------------

    fun open(player: Player) {
        if (!hasLoot(player)) {
            player.mes("The chest is empty.")
            return
        }
        player.ifOpenMainModal(cox_interfaces.rewards, eventBus)
        redraw(player)
    }

    private fun redraw(player: Player) {
        val inv = chest(player)
        UpdateInventory.updateInvFull(player, inv)
        VarPlayerIntMapSetter.set(player, cox_varbits.needs_reward_book, 0)
        player.ifSetEvents(cox_components.rewards_items, inv.indices, IfEvent.Op1, IfEvent.Op10)
    }

    fun take(player: Player, slot: Int) {
        val inv = chest(player)
        val stack = inv[slot] ?: return
        val type = objTypes[stack]
        val pet = pets.ofObj(stack.id)
        if (pet != null) {
            // A pet leaving the chest is handed out as pets are (follower, backpack, Probita).
            player.invDel(inv, type, stack.count, slot = slot)
            pets.award(player, pet.first, pet.second, log = false)
            collectionLog.receive(player, stack.id, 1)
            if (!hasLoot(player)) VarPlayerIntMapSetter.set(player, cox_varbits.reward_indicator, 0)
            redraw(player)
            return
        }
        val fits =
            if (type.stackable) {
                if (player.inv.hasFreeSpace() || player.inv.count(type) > 0) stack.count else 0
            } else {
                min(stack.count, player.inv.freeSpace())
            }
        if (fits <= 0) {
            player.mes("You don't have enough inventory space.")
            return
        }
        player.invDel(inv, type, fits, slot = slot)
        player.invAdd(player.inv, type, fits)
        collectionLog.receive(player, stack.id, fits)
        if (!hasLoot(player)) VarPlayerIntMapSetter.set(player, cox_varbits.reward_indicator, 0)
        redraw(player)
    }

    /** Leaving with loot unclaimed: it goes to the bank (OSRS drops it on the floor). */
    fun bankAll(player: Player) {
        val inv = chest(player)
        val bank = player.invMap.getOrPut(invTypes[invs.bank])
        for (slot in inv.indices) {
            val stack = inv[slot] ?: continue
            val type = objTypes[stack]
            val pet = pets.ofObj(stack.id)
            if (pet != null) {
                inv[slot] = null
                pets.award(player, pet.first, pet.second, log = false)
                collectionLog.receive(player, stack.id, 1)
                continue
            }
            if (player.invAdd(bank, type, stack.count).success) {
                collectionLog.receive(player, stack.id, stack.count)
                inv[slot] = null
            }
        }
        VarPlayerIntMapSetter.set(player, cox_varbits.reward_indicator, 0)
        if (inv.isNotEmpty())
            player.mes("Some of your loot didn't fit in your bank; collect it from the chest.")
    }

    companion object {
        private const val PRECISION = 1_000_000
        const val TASK_1 = 114
        const val TASK_25 = 115
        const val TASK_50 = 116
        const val TASK_100 = 117
        const val TASK_HONOUR = 120
        const val TASK_TALISMAN = 177
        const val TASK_BUCKLER = 1061
        const val TASK_ANCESTRAL_KIT = 1067
        const val TASK_TWISTED_BOW = 131
    }
}

/**
 * The chest, Captain Rimor, the tasks for Xeric's gear, and the twisted ancestral kit. Rimor's
 * capes go by Challenge Mode completions (100, 500, 1,000, 1,500, 2,000: a guess at the tiers, the
 * Wiki's page wasn't read for them).
 */
class CoxRewardScript
@Inject
constructor(
    private val rewards: CoxRewards,
    private val raids: CoxRaids,
    private val tracker: LeagueTaskTracker,
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<CoxEvents.OlmDefeated> {
            rewards.rollRaid(raid)
            val chest = raid.olmTile(CoxOlm.CHEST)
            locRepo.add(
                chest,
                locTypes[cox_locs.reward_chest],
                Int.MAX_VALUE,
                LocAngle.South,
                LocShape.CentrepieceStraight,
            )
        }
        onOpLoc1(cox_locs.reward_chest) { rewards.open(player) }
        onIfModalButton(cox_components.rewards_items) { rewards.take(player, it.comsub) }
        onEvent<CoxEvents.PlayerLeft> { if (rewards.hasLoot(player)) rewards.bankAll(player) }
        onPlayerLogin {
            if (rewards.hasLoot(player) && raids.memberRaid(player) == null) rewards.bankAll(player)
        }

        onOpNpc1(cox_npcs.captain_rimor) {
            startDialogue(it.npc) {
                chatNpcNoAnim(
                    "The Chambers lie beyond the steps. Earn your place among Xeric's own and I'll " +
                        "see you're given a cape to show it."
                )
            }
        }
        onOpNpc3(cox_npcs.captain_rimor) { claimCape() }

        onWearposChange { checkGear(player) }
        onPlayerLogin { checkGear(player) }
        for (piece in listOf(CoxAncestral.hat, CoxAncestral.top, CoxAncestral.bottom)) {
            onOpHeldU(CoxAncestral.kit, piece) { recolour(piece) }
        }
        CoxTalisman.honourUsed = { tracker.complete(it, CoxRewards.TASK_HONOUR) }
    }

    private fun ProtectedAccess.claimCape() {
        val count = player.vars[cox_varps.challenge_completions]
        val tier = CAPE_TIERS.indexOfLast { count >= it }
        if (tier < 0) {
            mes("You need ${CAPE_TIERS[0]} Challenge Mode completions for a cape. You have $count.")
            return
        }
        val cape = CoxAncestral.capes[tier]
        if (cape in player.inv || cape in player.worn) {
            mes("You already have that cape.")
            return
        }
        invAdd(inv, cape, 1)
        mes("Captain Rimor hands you a cape.")
    }

    private fun checkGear(player: Player) {
        val worn =
            player.worn.objs.mapNotNullTo(mutableSetOf()) {
                it?.let { o -> objTypes[o].internalName }
            }
        if (worn.any { it.startsWith("xeric_talisman") })
            tracker.complete(player, CoxRewards.TASK_TALISMAN)
        if ("twisted_buckler" in worn) tracker.complete(player, CoxRewards.TASK_BUCKLER)
        if ("twisted_bow" in worn) tracker.complete(player, CoxRewards.TASK_TWISTED_BOW)
    }

    private fun ProtectedAccess.recolour(piece: ObjType) {
        val twisted =
            when (piece.id) {
                CoxAncestral.hat.id -> CoxAncestral.hat_twisted
                CoxAncestral.top.id -> CoxAncestral.top_twisted
                else -> CoxAncestral.bottom_twisted
            }
        invDel(inv, CoxAncestral.kit, 1)
        invDel(inv, piece, 1)
        invAdd(inv, twisted, 1)
        mes("You recolour the ancestral robes.")
        tracker.complete(player, CoxRewards.TASK_ANCESTRAL_KIT)
    }

    private companion object {
        val CAPE_TIERS = listOf(100, 500, 1000, 1500, 2000)
    }
}

internal object CoxAncestral : ObjReferences() {
    val kit = find("ancestral_robes_twisted_kit")
    val hat = find("ancestral_hat")
    val top = find("ancestral_robe_top")
    val bottom = find("ancestral_robe_bottom")
    val hat_twisted = find("ancestral_hat_twisted")
    val top_twisted = find("ancestral_robe_top_twisted")
    val bottom_twisted = find("ancestral_robe_bottom_twisted")
    val capes = (1..5).map { find("cox_challenge_cape_t$it") }
}
