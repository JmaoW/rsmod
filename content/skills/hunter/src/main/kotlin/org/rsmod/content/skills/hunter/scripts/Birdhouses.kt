package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.HunterVarps
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.configs.hunter_seqs
import org.rsmod.content.skills.hunter.configs.hunter_timers
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.varp.VarpType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the clue nests and the scroll boxes the Wiki's rolls would add (the isle has no clue scrolls)
// - the Imcando hammer, which makes the birdhouse faster to build
//
// The four birdhouse spots are locs whose picture comes from one of the player's varps (so each
// hunter has houses of their own): 0 for an empty spot, then 1 + 3 * tier + state, where the state
// is
// 0 for a house with no seeds, 1 for one filling with birds and 2 for one with birds ready to
// collect.
// Seeds put in and the minute the house was filled are kept in two more varps for each spot.
// The Wiki has birds take fifty minutes; here it is [GROWTH_MINUTES].
class Birdhouses
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    /** One kind of birdhouse. */
    private class Tier(
        val name: String,
        val house: ObjType,
        val logs: ObjType,
        val hunterLevel: Int,
        val craftingLevel: Int,
        val craftingXp: Double,
        val xp: Double,
        val nestChance: Double,
    )

    private class Spot(
        val base: String,
        val state: VarpType,
        val filled: VarpType,
        val seeds: VarpType,
    )

    private val o = hunter_objs

    private val tiers =
        listOf(
            Tier("normal", o.birdhouse, o.logs, 5, 5, 15.0, 112.0, 0.1),
            Tier("oak", o.oak_birdhouse, o.oak_logs, 14, 15, 20.0, 168.0, 0.125),
            Tier("willow", o.willow_birdhouse, o.willow_logs, 24, 25, 25.0, 224.0, 0.128),
            Tier("teak", o.teak_birdhouse, o.teak_logs, 34, 35, 30.0, 280.0, 0.13),
            Tier("maple", o.maple_birdhouse, o.maple_logs, 44, 45, 35.0, 369.0, 0.14),
            Tier("mahogany", o.mahogany_birdhouse, o.mahogany_logs, 49, 50, 40.0, 480.0, 0.15),
            Tier("yew", o.yew_birdhouse, o.yew_logs, 59, 60, 45.0, 612.0, 0.16),
            Tier("magic", o.magic_birdhouse, o.magic_logs, 74, 75, 50.0, 969.0, 0.17),
            Tier("redwood", o.redwood_birdhouse, o.redwood_logs, 89, 90, 55.0, 1200.0, 0.175),
        )

    private val spots =
        listOf(
            Spot(
                "birdhouse_1",
                HunterVarps.birdhouse_a,
                HunterVarps.birdhouse_a_filled,
                HunterVarps.birdhouse_a_seeds,
            ),
            Spot(
                "birdhouse_2",
                HunterVarps.birdhouse_b,
                HunterVarps.birdhouse_b_filled,
                HunterVarps.birdhouse_b_seeds,
            ),
            Spot(
                "birdhouse_3",
                HunterVarps.birdhouse_c,
                HunterVarps.birdhouse_c_filled,
                HunterVarps.birdhouse_c_seeds,
            ),
            Spot(
                "birdhouse_4",
                HunterVarps.birdhouse_d,
                HunterVarps.birdhouse_d_filled,
                HunterVarps.birdhouse_d_seeds,
            ),
        )

    /**
     * Seed names (the cache's own) and how much of a house each fills: hop, herb, allotment, flower
     * and bush seeds.
     */
    private val seedValues: Map<ObjType, Int> by lazy {
        val byName =
            objTypes.values.filter { it.internalName != null }.associateBy { it.internalName }
        val map = LinkedHashMap<ObjType, Int>()
        for (name in LOW_SEEDS) byName[name]?.let { map[it] = 1 }
        for (name in HIGH_SEEDS) byName[name]?.let { map[it] = 2 }
        map
    }

    override fun ScriptContext.startup() {
        onOpLoc1(locNamed("birdhouse_not_built")) { build(it.loc) }
        for (tier in tiers) {
            onOpLocU(locNamed("birdhouse_not_built"), tier.house) { place(it.loc, tier) }
            val built = locNamed("birdhouse_${tier.name}_built")
            val full = locNamed("birdhouse_${tier.name}_full")
            val bird = locNamed("birdhouse_${tier.name}_bird")
            onOpLoc2(built) { seed(it.loc) }
            onOpLoc2(full) { seed(it.loc) }
            onOpLoc3(full) { dismantle(it.loc) }
            onOpLoc1(bird) { interact(it.loc) }
            onOpLoc2(bird) { seed(it.loc) }
            onOpLoc3(bird) { collect(it.loc, rebuild = false) }
            onOpLoc4(bird) { collect(it.loc, rebuild = true) }
        }

        onEvent<HunterTraps.Tick> { tick(this) }
        onPlayerLogin {
            ripen(player)
            if (growing(player)) {
                player.softTimer(hunter_timers.tick, 1)
            }
        }
    }

    // region building

    private suspend fun ProtectedAccess.build(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        val tier =
            tiers.lastOrNull { invTotal(inv, it.house) > 0 && stat(stats.hunter) >= it.hunterLevel }
        if (tier == null) {
            mes(
                "Bring a birdhouse that suits your Hunter level, which you can craft from logs and a clockwork."
            )
            return
        }
        place(loc, tier, spot)
    }

    private suspend fun ProtectedAccess.place(loc: BoundLocInfo, tier: Tier) {
        val spot = spotOf(loc) ?: return
        place(loc, tier, spot)
    }

    private suspend fun ProtectedAccess.place(loc: BoundLocInfo, tier: Tier, spot: Spot) {
        if (stat(stats.hunter) < tier.hunterLevel) {
            mes("You need a Hunter level of ${tier.hunterLevel} to set this birdhouse.")
            return
        }
        anim(hunter_seqs.lay_trap)
        delay(2)
        if (player.vars[spot.state] != 0 || !invDel(inv, tier.house, 1).success) {
            return
        }
        VarPlayerIntMapSetter.set(player, spot.seeds, 0)
        VarPlayerIntMapSetter.set(player, spot.state, value(tiers.indexOf(tier), BUILT))
        mes("You place the birdhouse. Fill it with seeds to start catching birds.")
        publish(Built(player, tier.name))
    }

    private suspend fun ProtectedAccess.seed(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        ripen(player, spot)
        val state = player.vars[spot.state]
        if (status(state) == BIRDS) {
            mes("There are birds in the birdhouse waiting for you.")
            return
        }
        val held = seedValues.entries.firstOrNull { invTotal(inv, it.key) > 0 }
        if (status(state) == FILLING || held == null) {
            if (status(state) == FILLING)
                mes("This birdhouse is full of seeds. The birds will be along.")
            else mes("You need suitable hop, herb, allotment, flower or bush seeds.")
            return
        }
        val have = player.vars[spot.seeds]
        val seed = held.key
        val worth = held.value
        val wanted = (FULL_SEEDS - have + worth - 1) / worth
        val amount = minOf(wanted, invTotal(inv, seed))
        anim(hunter_seqs.bait)
        delay(1)
        if (amount <= 0 || !invDel(inv, seed, amount).success) {
            return
        }
        val total = minOf(FULL_SEEDS, have + amount * worth)
        VarPlayerIntMapSetter.set(player, spot.seeds, total)
        if (total >= FULL_SEEDS) {
            VarPlayerIntMapSetter.set(player, spot.filled, minutes())
            VarPlayerIntMapSetter.set(player, spot.state, value(tier(state), FILLING))
            player.softTimer(hunter_timers.tick, 1)
            mes("The birdhouse is ready. Come back in $GROWTH_MINUTES minutes.")
        } else {
            mes("You add some seeds to the birdhouse.")
        }
    }

    private suspend fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        anim(hunter_seqs.take)
        delay(2)
        val tier = tier(player.vars[spot.state])
        if (player.vars[spot.state] == 0) {
            return
        }
        VarPlayerIntMapSetter.set(player, spot.state, 0)
        VarPlayerIntMapSetter.set(player, spot.seeds, 0)
        give(o.clockwork)
        publish(Dismantled(player, tiers[tier].name))
        mes("You dismantle the birdhouse and recover the clockwork.")
    }

    private suspend fun ProtectedAccess.interact(loc: BoundLocInfo) {
        mes("The birds have been busy. Empty the birdhouse to collect what they caught.")
    }

    // endregion

    // region collecting

    private suspend fun ProtectedAccess.collect(loc: BoundLocInfo, rebuild: Boolean) {
        val spot = spotOf(loc) ?: return
        ripen(player, spot)
        val state = player.vars[spot.state]
        if (status(state) != BIRDS) {
            mes("The birdhouse is not ready yet.")
            return
        }
        val tier = tiers[tier(state)]
        if (rebuild) {
            if (
                invTotal(inv, tier.logs) < 1 ||
                    invTotal(inv, o.hammer) < 1 ||
                    invTotal(inv, o.chisel) < 1
            ) {
                mes("Bring ${tier.name} logs, a hammer and a chisel to reset this birdhouse.")
                return
            }
            if (stat(stats.crafting) < tier.craftingLevel) {
                mes("You need a Crafting level of ${tier.craftingLevel} to rebuild this birdhouse.")
                return
            }
        }
        anim(hunter_seqs.take)
        delay(2)
        if (player.vars[spot.state] != state) {
            return
        }
        if (rebuild && !invDel(inv, tier.logs, 1).success) {
            return
        }
        val items = ArrayList<Pair<ObjType, Int>>()
        items += o.raw_bird_meat to MEAT
        items += o.feather to (FEATHERS_MIN + random.of(FEATHERS_MAX - FEATHERS_MIN + 1))
        val nests = rollNests(tier)
        items += nests.map { it to 1 }
        if (!rebuild) {
            items += o.clockwork to 1
        }
        if (rebuild) {
            VarPlayerIntMapSetter.set(player, spot.seeds, 0)
            VarPlayerIntMapSetter.set(player, spot.state, value(tiers.indexOf(tier), BUILT))
            statAdvance(stats.crafting, tier.craftingXp * xpMods.get(player, stats.crafting))
        } else {
            VarPlayerIntMapSetter.set(player, spot.seeds, 0)
            VarPlayerIntMapSetter.set(player, spot.state, 0)
        }
        for ((obj, count) in items) {
            if (!invAdd(inv, obj, count).success) {
                invAddOrDrop(objRepo, obj, count)
            }
        }
        statAdvance(stats.hunter, tier.xp * xpMods.get(player, stats.hunter))
        mes(
            if (rebuild) "You collect the birds and build the birdhouse again."
            else "You collect the birds and recover the clockwork."
        )
        publish(Collected(player, tier.name, nests.size))
    }

    /** The nests this house's birds left: five rolls, more likely at higher Hunter levels. */
    private fun ProtectedAccess.rollNests(tier: Tier): List<ObjType> {
        val level = stat(stats.hunter)
        val factor = if (level <= 50) 0.5 else 0.5 + (level - 50) / 98.0
        val chance = tier.nestChance * 3 * factor
        val nests = ArrayList<ObjType>()
        repeat(NEST_ROLLS) {
            if (random.randomDouble() >= chance) {
                return@repeat
            }
            val draw = random.of(100)
            nests +=
                when {
                    draw < 65 -> o.bird_nest_seeds
                    draw < 97 -> o.bird_nest_ring
                    else ->
                        listOf(o.bird_nest_red, o.bird_nest_green, o.bird_nest_blue)[random.of(3)]
                }
        }
        return nests
    }

    // endregion

    // region time

    private fun tick(event: HunterTraps.Tick) {
        val player = event.player
        if (ripen(player)) {
            player.mes("Birds have settled in your birdhouse.")
        }
        if (growing(player)) {
            event.keep = true
        }
    }

    /** Turns any house that has had long enough into one with birds. Returns whether any did. */
    private fun ripen(player: Player): Boolean {
        var changed = false
        for (spot in spots) {
            changed = ripen(player, spot) || changed
        }
        return changed
    }

    private fun ripen(player: Player, spot: Spot): Boolean {
        val state = player.vars[spot.state]
        if (status(state) != FILLING) {
            return false
        }
        if (minutes() - player.vars[spot.filled] < GROWTH_MINUTES) {
            return false
        }
        VarPlayerIntMapSetter.set(player, spot.state, value(tier(state), BIRDS))
        return true
    }

    private fun growing(player: Player): Boolean =
        spots.any { status(player.vars[it.state]) == FILLING }

    private fun minutes(): Int = (System.currentTimeMillis() / 60_000L).toInt() - EPOCH_MINUTES

    // endregion

    private fun locNamed(name: String): LocType =
        locTypes.values.firstOrNull { it.internalName == name } ?: error("No loc called $name.")

    private fun spotOf(loc: BoundLocInfo): Spot? {
        val name = locTypes[loc.id]?.internalName ?: return null
        return spots.firstOrNull { it.base == name }
    }

    private fun ProtectedAccess.give(obj: ObjType) {
        if (!invAdd(inv, obj, 1).success) {
            invAddOrDrop(objRepo, obj, 1)
        }
    }

    /** Published when a birdhouse of the kind called [house] is put up. */
    class Built(val player: Player, val house: String) : UnboundEvent

    /** Published when the birds of a birdhouse are emptied out. */
    class Collected(val player: Player, val house: String, val nests: Int) : UnboundEvent

    /** Published when a birdhouse is taken down. */
    class Dismantled(val player: Player, val house: String) : UnboundEvent

    companion object {
        /** Minutes a full birdhouse takes to bring in its birds (the Wiki has fifty). */
        const val GROWTH_MINUTES = 10

        /** Seeds that fill a house. */
        const val FULL_SEEDS = 10

        const val MEAT = 10
        const val FEATHERS_MIN = 30
        const val FEATHERS_MAX = 100
        const val NEST_ROLLS = 5

        /** The state of a house in a spot's varp. */
        const val BUILT = 0
        const val FILLING = 1
        const val BIRDS = 2

        /** The minute that counts as zero for the filled-at varps: 1 October 2026. */
        const val EPOCH_MINUTES = 29_846_880

        fun value(tier: Int, status: Int): Int = 1 + tier * 3 + status

        fun tier(value: Int): Int = (value - 1) / 3

        fun status(value: Int): Int = if (value == 0) -1 else (value - 1) % 3

        private val LOW_SEEDS =
            listOf(
                "guam_seed",
                "marrentill_seed",
                "tarromin_seed",
                "harralander_seed",
                "barley_seed",
                "hammerstone_hop_seed",
                "asgarnian_hop_seed",
                "jute_seed",
                "yanillian_hop_seed",
                "krandorian_hop_seed",
                "potato_seed",
                "onion_seed",
                "cabbage_seed",
                "tomato_seed",
                "sweetcorn_seed",
                "strawberry_seed",
                "redberry_bush_seed",
                "cadavaberry_bush_seed",
                "dwellberry_bush_seed",
                "jangerberry_bush_seed",
                "marigold_seed",
                "rosemary_seed",
                "nasturtium_seed",
                "woad_seed",
                "limpwurt_seed",
            )

        private val HIGH_SEEDS =
            listOf(
                "ranarr_seed",
                "toadflax_seed",
                "irit_seed",
                "avantoe_seed",
                "kwuarm_seed",
                "snapdragon_seed",
                "cadantine_seed",
                "lantadyme_seed",
                "dwarf_weed_seed",
                "torstol_seed",
                "wildblood_hop_seed",
                "watermelon_seed",
                "snape_grass_seed",
                "whiteberry_bush_seed",
                "poisonivy_bush_seed",
            )
    }
}
