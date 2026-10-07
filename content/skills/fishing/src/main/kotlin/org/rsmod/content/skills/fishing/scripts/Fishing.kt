package org.rsmod.content.skills.fishing.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.fishing.configs.FishingCatches
import org.rsmod.content.skills.fishing.configs.FishingMethod
import org.rsmod.content.skills.fishing.configs.FishingNpcs
import org.rsmod.content.skills.fishing.configs.FishingObjs
import org.rsmod.content.skills.fishing.configs.FishingSeqs
import org.rsmod.content.skills.fishing.configs.FishingSpots
import org.rsmod.content.skills.fishing.configs.SpotMethods
import org.rsmod.events.UnboundEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.isType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - spots moving around after a number of catches
// - the other spot types (barbarian, aerial, minnows, anglerfish, dark crabs, eels, drift nets)
// - spirit flakes, shark lures, and other catch-rate modifiers
//
// Fishing spots use a real option for op1 and op3 (for example "Net"/"Bait"), so the fishing loop
// continues through the otherwise unused op2 (for the op1 method) and op4 (for the op3 method).
class Fishing
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val npcTypes: NpcTypeList,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val mapClock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in npcTypes.values) {
            val spot = spotMethods(type.name, type.op, type.id) ?: continue
            onOpNpc1(type) { attempt(it.npc, spot.first, FIRST_SLOT) }
            onOpNpc2(type) { fish(it.npc, spot.first, FIRST_SLOT) }
            val second = spot.second ?: continue
            onOpNpc3(type) { attempt(it.npc, second, SECOND_SLOT) }
            onOpNpc4(type) { fish(it.npc, second, SECOND_SLOT) }
        }
    }

    private fun spotMethods(name: String, ops: Array<String?>, id: Int): SpotMethods? {
        if (id in monkfishIds) {
            return SpotMethods(FishingMethod.MonkfishNet, FishingMethod.Harpoon)
        }
        if (id in karambwanjiIds) {
            return SpotMethods(FishingMethod.Karambwanji, null)
        }
        if (id in karambwanIds) {
            return SpotMethods(FishingMethod.Karambwan, null)
        }
        if (!name.endsWith("Fishing spot", ignoreCase = true)) {
            return null
        }
        return FishingSpots.fromOps(ops.getOrNull(0), ops.getOrNull(2))
    }

    private val monkfishIds: Set<Int> by lazy { FishingNpcs.monkfish_spots.map { it.id }.toSet() }
    private val karambwanjiIds: Set<Int> by lazy {
        FishingNpcs.karambwanji_spots.map { it.id }.toSet()
    }
    private val karambwanIds: Set<Int> by lazy { FishingNpcs.karambwan_spots.map { it.id }.toSet() }

    private fun ProtectedAccess.attempt(npc: Npc, method: FishingMethod, slot: Int) {
        val gear = findGear(method) ?: return
        if (!canStart(method)) {
            return
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + gear.rollDelay
            skillAnimDelay = mapClock + 3
            retrigger(npc, slot)
        } else {
            anim(gear.anim)
            spam(method.startMessage)
            fish(npc, method, slot)
        }
    }

    private fun ProtectedAccess.fish(npc: Npc, method: FishingMethod, slot: Int) {
        val gear = findGear(method) ?: return
        if (!canStart(method)) {
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + 4
            anim(gear.anim)
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + gear.rollDelay
        } else if (actionDelay == mapClock) {
            roll(npc, method)
        }

        reinteract(npc, slot)
    }

    private fun ProtectedAccess.roll(npc: Npc, method: FishingMethod) {
        val candidates = FishingCatches.eligible(method, player.fishingLvl)

        // Try the highest level fish first. The first fish whose roll succeeds is the catch.
        val natural =
            candidates.firstOrNull { statRandom(stats.fishing, it.low, it.high, invisibleLvls) }
        val caught = natural ?: candidates.last()
        val roll = CatchRoll(player, npc, caught.raw, natural != null)
        publish(roll)
        if (!roll.success) {
            return
        }

        val bait = baitFor(method)
        if (bait != null) {
            invDel(inv, bait, 1)
        }

        val xp = caught.xp * xpMods.get(player, stats.fishing)
        spam(caught.catchMessage)
        statAdvance(stats.fishing, xp)
        if (!roll.bank || !invAdd(bank, roll.product).success) {
            invAdd(inv, roll.product)
        }
        publish(FishCaught(player, npc, caught.raw, roll.product))
    }

    /** Triggers the same op the player originally clicked. */
    private fun ProtectedAccess.retrigger(npc: Npc, slot: Int) {
        if (slot == FIRST_SLOT) {
            opNpc1(npc)
        } else {
            opNpc3(npc)
        }
    }

    /** Continues fishing through the hidden op that pairs with the clicked op. */
    private fun ProtectedAccess.reinteract(npc: Npc, slot: Int) {
        if (slot == FIRST_SLOT) {
            opNpc2(npc)
        } else {
            opNpc4(npc)
        }
    }

    /** Checks level, tools, bait, and inventory space. Sends a message for the first problem. */
    private fun ProtectedAccess.canStart(method: FishingMethod): Boolean {
        val level = player.fishingLvl
        val minLevel = FishingCatches.minLevel(method)
        if (level < minLevel) {
            val lowest = FishingCatches.pool(method).minBy { it.level }
            mes("You need a Fishing level of $minLevel to catch ${lowest.plural}.")
            return false
        }

        val bait = baitFor(method)
        if (bait != null && bait !in inv) {
            mes(missingBaitMessage(method))
            return false
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more fish.")
            soundSynth(synths.pillory_wrong)
            return false
        }
        return true
    }

    private fun ProtectedAccess.findGear(method: FishingMethod): Gear? {
        val gear =
            when (method) {
                FishingMethod.SmallNet,
                FishingMethod.MonkfishNet,
                FishingMethod.Karambwanji -> toolGear(FishingObjs.small_net, FishingSeqs.small_net)
                FishingMethod.BigNet -> toolGear(FishingObjs.big_net, FishingSeqs.big_net)
                FishingMethod.SeaBait -> toolGear(FishingObjs.fishing_rod, FishingSeqs.rod_bait)
                FishingMethod.RiverBait -> toolGear(FishingObjs.fishing_rod, FishingSeqs.rod_bait)
                FishingMethod.FlyRod -> toolGear(FishingObjs.fly_fishing_rod, FishingSeqs.rod_lure)
                FishingMethod.LobsterPot ->
                    toolGear(FishingObjs.lobster_pot, FishingSeqs.lobster_pot)
                FishingMethod.Harpoon,
                FishingMethod.SharkHarpoon -> findHarpoon(player, objTypes)
                FishingMethod.Karambwan ->
                    findEchoHarpoon(player, objTypes)
                        ?: if (
                            FishingObjs.karambwan_vessel in inv ||
                                FishingObjs.karambwan_vessel_loaded in inv
                        ) {
                            Gear(FishingSeqs.karambwan_vessel, DEFAULT_ROLL_DELAY)
                        } else {
                            null
                        }
            }
        if (gear == null) {
            mes(method.missingToolMessage)
        }
        return gear
    }

    /**
     * The Echo harpoon works as a net, big net, lobster pot, rod, or karambwan vessel without
     * needing any bait, and is one tick faster than the standard equipment.
     */
    private fun ProtectedAccess.toolGear(tool: ObjType, anim: SeqType): Gear? =
        findEchoHarpoon(player, objTypes)
            ?: if (tool in inv) Gear(anim, DEFAULT_ROLL_DELAY) else null

    /** The bait or feathers [method] uses up. The Echo harpoon needs none. */
    private fun ProtectedAccess.baitFor(method: FishingMethod): ObjType? {
        if (findEchoHarpoon(player, objTypes) != null) {
            return null
        }
        return when (method) {
            FishingMethod.SeaBait,
            FishingMethod.RiverBait -> FishingObjs.fishing_bait
            FishingMethod.FlyRod -> FishingObjs.feather
            FishingMethod.Karambwan -> FishingObjs.raw_karambwanji
            else -> null
        }
    }

    private fun missingBaitMessage(method: FishingMethod): String =
        when (method) {
            FishingMethod.FlyRod -> "You don't have any feathers left."
            FishingMethod.Karambwan -> "You need raw karambwanji to use as bait."
            else -> "You don't have any fishing bait left."
        }

    /** The animation and number of ticks to wait between catch attempts for a set of equipment. */
    internal class Gear(val anim: SeqType, val rollDelay: Int)

    /**
     * Published on every roll to catch a fish, after the normal roll. [fish] is the fish that was
     * caught, or the lowest level fish the player could have caught if the roll failed. Listeners
     * can turn a failed roll into a [success], ask for the item to be sent to the [bank] instead of
     * the inventory, and swap the item received by changing [product] (for example, to a cooked
     * version of the [fish]).
     */
    class CatchRoll(val player: Player, val npc: Npc, val fish: ObjType, var success: Boolean) :
        UnboundEvent {
        var bank: Boolean = false
        var product: ObjType = fish
    }

    /** Published once a fish has been caught. [product] is the item the player received. */
    class FishCaught(val player: Player, val npc: Npc, val fish: ObjType, val product: ObjType) :
        UnboundEvent

    companion object {
        private const val FIRST_SLOT = 1
        private const val SECOND_SLOT = 3

        /**
         * Ticks to wait before each catch attempt, minus one. The loop spends one extra tick
         * between attempts, so this gives a five tick interval between catch rolls.
         */
        internal const val DEFAULT_ROLL_DELAY = 4

        /** Ticks between catch attempts for the Echo harpoon: one faster than the standard five. */
        private const val ECHO_ROLL_DELAY = DEFAULT_ROLL_DELAY - 1

        internal fun findEchoHarpoon(player: Player, objTypes: ObjTypeList): Gear? {
            val worn = player.righthand?.let { objTypes[it] }
            val carried =
                player.inv.filterNotNull {
                    objTypes[it].isType(FishingObjs.league_trailblazer_harpoon)
                }
            val hasEcho =
                worn.isType(FishingObjs.league_trailblazer_harpoon) || carried.isNotEmpty()
            return if (hasEcho) Gear(FishingSeqs.harpoon_league_trailblazer, ECHO_ROLL_DELAY)
            else null
        }

        internal fun findHarpoon(player: Player, objTypes: ObjTypeList): Gear? {
            val worn = player.righthand?.let { objTypes[it] }?.takeIf { it.harpoonGear() != null }
            val carried =
                player.inv
                    .filterNotNull { objTypes[it].harpoonGear() != null }
                    .map { objTypes[it] }
                    .minByOrNull { it.harpoonGear()!!.rollDelay }
            val best =
                listOfNotNull(worn, carried).minByOrNull { it.harpoonGear()!!.rollDelay }
                    ?: return null
            return best.harpoonGear()
        }

        private fun UnpackedObjType.harpoonGear(): Gear? =
            when {
                isType(FishingObjs.harpoon) -> Gear(FishingSeqs.harpoon, DEFAULT_ROLL_DELAY)
                isType(FishingObjs.dragon_harpoon) -> Gear(FishingSeqs.harpoon_dragon, 3)
                isType(FishingObjs.dragon_harpoon_or) -> Gear(FishingSeqs.harpoon_dragon_or, 3)
                isType(FishingObjs.infernal_harpoon) ||
                    isType(FishingObjs.infernal_harpoon_uncharged) ->
                    Gear(FishingSeqs.harpoon_infernal, 3)
                isType(FishingObjs.infernal_harpoon_or) ||
                    isType(FishingObjs.infernal_harpoon_or_uncharged) ->
                    Gear(FishingSeqs.harpoon_infernal_or, 3)
                isType(FishingObjs.trailblazer_reloaded_harpoon) ||
                    isType(FishingObjs.trailblazer_reloaded_harpoon_empty) ||
                    isType(FishingObjs.trailblazer_reloaded_harpoon_no_infernal) ->
                    Gear(FishingSeqs.harpoon_reloaded, 3)
                isType(FishingObjs.crystal_harpoon) ||
                    isType(FishingObjs.crystal_harpoon_inactive) ->
                    Gear(FishingSeqs.harpoon_crystal, 2)
                isType(FishingObjs.league_trailblazer_harpoon) ->
                    Gear(FishingSeqs.harpoon_league_trailblazer, ECHO_ROLL_DELAY)
                else -> null
            }
    }
}
