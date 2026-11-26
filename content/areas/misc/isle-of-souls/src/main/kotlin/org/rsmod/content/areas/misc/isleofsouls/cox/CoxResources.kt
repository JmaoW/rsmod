package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The raid's gathering, wherever its locs are: saplings and kindling (with kindling fires to cook
 * on; the Cooking module already knows the raid's fish and bats), fishing, herb patches and weeds,
 * the gourd tree and geysers, and the tools lying in rooms.
 *
 * Wiki: kindling 1 to (Woodcutting / 12, at most 8) a chop, 30 to 81.5 XP a chop, 28 at most, 48
 * Firemaking XP each, saplings back in 30 seconds; the fish's levels and XP; patches of golpar
 * (Farming 27), buchu (39) and noxifer (55). Not on the Wiki (guesses, listed in the plan doc): a
 * try every 4 ticks for chops and 5 for fish, success 40% + level / 2 %, a sapling 1 in 4 to wear
 * out, rakes of 1-3 seeds, a stage every 12 ticks, and the cave snake as a 1 in 40 bite with no
 * npc.
 */
class CoxResources
@Inject
constructor(
    private val raids: CoxRaids,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
    private val rooms: CoxRoomRegistry,
    private val clock: org.rsmod.game.MapClock,
) : PluginScript() {
    /** Herb patches growing, by raid tile. */
    private val patches = HashMap<CoordGrid, Patch>()

    private class Patch(val herb: CoxHerb, var stage: Int, var nextAt: Int)

    override fun ScriptContext.startup() {
        onOpLoc1(cox_locs.sapling) { chop(it.loc) }
        onOpHeldU(cox_objs.tinderbox, cox_objs.kindling) { lightFire() }
        onOpLoc1(cox_locs.fishing_spot) { fish(it.loc) }

        onOpLoc1(cox_locs.weeds) { rake(it.loc) }
        onOpLoc2(cox_locs.patch_empty) {
            mes("The patch is empty. Plant a seed in it with a seed dibber.")
        }
        for (herb in CoxHerb.entries) {
            onOpLocU(cox_locs.patch_empty, herb.seed) { plant(it.loc, herb) }
            onOpLoc1(herb.grown) { pickHerb(it.loc, herb) }
            onOpLoc3(herb.grown) { clearPatch(it.loc) }
        }

        onOpLoc1(cox_locs.gourd_tree) { pickGourds(lots = false) }
        onOpLoc2(cox_locs.gourd_tree) { pickGourds(lots = true) }
        onOpLoc1(cox_locs.geyser) { fillGourds() }
        onOpLocU(cox_locs.geyser, cox_objs.gourd_vial) { fillGourds() }

        onOpLoc1(cox_locs.old_tools) {
            takeTools(listOf(cox_objs.rake, cox_objs.spade, cox_objs.seed_dibber))
        }
        onOpLoc2(cox_locs.old_tools) { takeTools(listOf(cox_objs.rake)) }
        onOpLoc3(cox_locs.old_tools) { takeTools(listOf(cox_objs.spade)) }
        onOpLoc4(cox_locs.old_tools) { takeTools(listOf(cox_objs.seed_dibber)) }
        onOpLoc1(cox_locs.icedemon_axe) { takeTools(listOf(cox_objs.bronze_axe)) }
        onOpLoc1(cox_locs.icedemon_tinderbox) { takeTools(listOf(cox_objs.tinderbox)) }
        onOpLoc1(cox_locs.crabs_hammer) { takeTools(listOf(cox_objs.hammer)) }
        onOpLoc1(cox_locs.guardians_pickaxe) { takeTools(listOf(cox_objs.iron_pickaxe)) }

        for (bat in cox_npcs.bat_types) onOpNpc1(bat) { catchBat(it.npc) }

        onEvent<org.rsmod.api.game.process.GameLifecycle.StartCycle> { growPatches() }
        onEvent<CoxEvents.RaidEnded> { patches.keys.removeIf { raid.contains(it) } }
        rooms.register(CoxRoomType.ResourcesB) { env, raid, room -> CoxBatRoom(env, raid, room) }
    }

    private fun ProtectedAccess.advance(stat: StatType, xp: Double) {
        statAdvance(stat, xp * xpMods.get(player, stat))
    }

    private fun ProtectedAccess.holds(obj: ObjType): Boolean = obj in inv || obj in worn

    private fun ProtectedAccess.hasAxe(): Boolean =
        (inv.toList() + worn.toList()).filterNotNull().any { obj ->
            val name = objTypes[obj].name.lowercase()
            name.endsWith("axe") && !name.endsWith("pickaxe") && !name.contains("battleaxe")
        }

    private fun level(player: Player, stat: StatType): Int = player.stat(stat)

    private fun success(level: Int): Boolean = random.of(100) < (40 + level / 2).coerceAtMost(95)

    // Saplings and kindling
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.chop(loc: BoundLocInfo) {
        if (raids.raidOf(player) == null) return
        if (!hasAxe()) {
            mes("You need an axe to chop this sapling.")
            return
        }
        val held = inv.count(objTypes[cox_objs.kindling])
        if (held >= KINDLING_CAP) {
            mes("You can't carry any more kindling.")
            return
        }
        if (inv.isFull() && held == 0) {
            mes("Your inventory is too full to hold any kindling.")
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + CHOP_TICKS
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.woodcutting)
            opLoc1(loc)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.woodcutting)
        }
        if (actionDelay == mapClock) {
            val wc = level(player, stats.woodcutting)
            if (success(wc)) {
                val most = (wc / 12).coerceIn(1, 8)
                val amount = random.of(1, most).coerceAtMost(KINDLING_CAP - held)
                invAdd(inv, cox_objs.kindling, amount)
                advance(stats.woodcutting, 30.0 + (wc - 1).coerceAtLeast(0) * 51.5 / 95.0)
                if (random.of(4) == 0) {
                    val tile = loc.coords
                    locRepo.findExact(tile, locTypes[cox_locs.sapling])?.let {
                        locRepo.change(it, locTypes[cox_locs.sapling_depleted], SAPLING_RESPAWN)
                    }
                    resetAnim()
                    return
                }
                if (inv.count(objTypes[cox_objs.kindling]) >= KINDLING_CAP) {
                    resetAnim()
                    return
                }
            }
            actionDelay = mapClock + CHOP_TICKS
        }
        opLoc1(loc)
    }

    private fun ProtectedAccess.lightFire() {
        val here = coords
        if (locRepo.findAll(here).any { it.layer == 2 }) {
            mes("You can't light a fire here.")
            return
        }
        invDel(inv, cox_objs.kindling, 1)
        anim(cox_seqs.firemaking)
        locRepo.add(
            here,
            locTypes[cox_locs.fire],
            FIRE_TICKS,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        advance(stats.firemaking, KINDLING_FM_XP)
        mes("The kindling catches and a fire springs up.")
    }

    // Fishing
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.fish(loc: BoundLocInfo) {
        if (raids.raidOf(player) == null) return
        if (!holds(cox_objs.fishing_rod)) {
            mes("You need a fishing rod to fish here.")
            return
        }
        if (cox_objs.cave_worms !in inv) {
            mes("You need some cave worms to use as bait.")
            return
        }
        if (inv.isFull()) {
            mes("You can't carry any more fish.")
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + FISH_TICKS
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.fishing)
            mes("You cast out your line...")
            opLoc1(loc)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.fishing)
        }
        if (actionDelay == mapClock) {
            if (random.of(SNAKE_CHANCE) == 0) {
                snakeBite()
                resetAnim()
                return
            }
            val lvl = level(player, stats.fishing)
            if (success(lvl)) {
                val best = FISH.indexOfLast { lvl >= it.level }.coerceAtLeast(0)
                // The best fish the level allows, now and then one lower.
                val tier = if (best > 0 && random.of(3) == 0) best - 1 else best
                invDel(inv, cox_objs.cave_worms, 1)
                invAdd(inv, cox_objs.raw_fish[tier], 1)
                advance(stats.fishing, FISH[tier].xp)
                if (inv.isFull() || cox_objs.cave_worms !in inv) {
                    resetAnim()
                    return
                }
            }
            actionDelay = mapClock + FISH_TICKS
        }
        opLoc1(loc)
    }

    /** One of the spots hides a cave snake (Wiki); it rises, bites and steals a fish. */
    private fun ProtectedAccess.snakeBite() {
        mes("A cave snake rises from the water and bites you!")
        val raid = raids.raidOf(player) ?: return
        player.queueHit(1, HitType.Typeless, random.of(3, 6))
        val food =
            cox_objs.cooked_fish.firstOrNull { it in inv }
                ?: cox_objs.raw_fish.lastOrNull { it in inv }
        if (food != null) {
            invDel(inv, food, 1)
            mes("The snake steals some of your food.")
        }
        raid.member(player)
    }

    // Farming
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.rake(loc: BoundLocInfo) {
        if (raids.raidOf(player) == null) return
        if (!holds(cox_objs.rake)) {
            mes("You need a rake to clear these weeds.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have room for any seeds.")
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + RAKE_TICKS
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.rake)
            opLoc1(loc)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_TICKS
            anim(cox_seqs.rake)
        }
        if (actionDelay == mapClock) {
            val farming = level(player, stats.farming)
            val allowed =
                CoxHerb.entries
                    .filter { farming >= it.farmingLevel }
                    .ifEmpty { listOf(CoxHerb.Golpar) }
            val herb = allowed[random.of(allowed.size)]
            invAdd(inv, herb.seed, random.of(1, 3))
            advance(stats.farming, RAKE_XP)
            mes("You find some seeds in the weeds.")
            actionDelay = mapClock + RAKE_TICKS
        }
        opLoc1(loc)
    }

    private fun ProtectedAccess.plant(loc: BoundLocInfo, herb: CoxHerb) {
        if (raids.raidOf(player) == null) return
        if (level(player, stats.farming) < herb.farmingLevel) {
            mes("You need a Farming level of ${herb.farmingLevel} to plant this seed.")
            return
        }
        if (!holds(cox_objs.seed_dibber)) {
            mes("You need a seed dibber to plant seeds.")
            return
        }
        val patch = locRepo.findExact(loc.coords, locTypes[cox_locs.patch_empty]) ?: return
        anim(cox_seqs.dibbing)
        invDel(inv, herb.seed, 1)
        locRepo.change(patch, locTypes[herb.stages[0]], Int.MAX_VALUE)
        patches[loc.coords] = Patch(herb, 0, clock.cycle + GROW_TICKS)
        advance(stats.farming, herb.plantXp)
        mes("You plant the seed in the patch.")
    }

    private fun growPatches() {
        if (patches.isEmpty()) return
        val now = raidsClock()
        for ((tile, patch) in patches.entries.toList()) {
            if (patch.stage >= patch.herb.stages.size - 1 || now < patch.nextAt) continue
            val current = patch.herb.stages[patch.stage]
            val loc =
                locRepo.findExact(tile, locTypes[current])
                    ?: run {
                        patches.remove(tile)
                        null
                    }
                    ?: continue
            patch.stage++
            patch.nextAt = now + GROW_TICKS
            locRepo.change(loc, locTypes[patch.herb.stages[patch.stage]], Int.MAX_VALUE)
        }
    }

    private fun raidsClock(): Int = clock.cycle

    private fun ProtectedAccess.pickHerb(loc: BoundLocInfo, herb: CoxHerb) {
        if (inv.isFull()) {
            mes("You don't have room for any herbs.")
            return
        }
        anim(cox_seqs.picking)
        invAdd(inv, herb.grimy, 1)
        advance(stats.farming, herb.pickXp)
        // About three herbs a patch (TSPS: three lives), then it is empty again.
        if (random.of(3) == 0) clearPatch(loc)
    }

    private fun ProtectedAccess.clearPatch(loc: BoundLocInfo) {
        val current =
            CoxHerb.entries.firstNotNullOfOrNull {
                locRepo.findExact(loc.coords, locTypes[it.grown])
            } ?: return
        locRepo.change(current, locTypes[cox_locs.patch_empty], Int.MAX_VALUE)
        patches.remove(loc.coords)
    }

    // Gourds, geysers and tools
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.pickGourds(lots: Boolean) {
        if (inv.isFull()) {
            mes("You don't have room for any gourds.")
            return
        }
        anim(cox_seqs.picking)
        val amount = if (lots) inv.freeSpace() else 1
        invAdd(inv, cox_objs.gourd_vial, amount)
        mes(if (lots) "You pick some gourds from the tree." else "You pick a gourd from the tree.")
    }

    private fun ProtectedAccess.fillGourds() {
        val empty = objTypes[cox_objs.gourd_vial]
        val count = inv.count(empty)
        if (count == 0) {
            mes("You need some empty gourd vials to fill.")
            return
        }
        anim(cox_seqs.pickup_table)
        invDel(inv, cox_objs.gourd_vial, count)
        invAdd(inv, cox_objs.gourd_water, count)
        mes("You fill the gourd vials with water.")
    }

    private fun ProtectedAccess.takeTools(tools: List<ObjType>) {
        val missing = tools.filter { !holds(it) }
        if (missing.isEmpty()) {
            mes("You already have that.")
            return
        }
        if (inv.freeSpace() < missing.size) {
            mes("You don't have enough room in your inventory.")
            return
        }
        anim(cox_seqs.pickup_table)
        for (tool in missing) invAdd(inv, tool, 1)
    }

    // Bats
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.catchBat(npc: Npc) {
        val raid = raids.raidOf(player) ?: return
        val tier = cox_npcs.bat_types.indexOfFirst { it.id == npc.visType.id }.coerceAtLeast(0)
        val hunter = level(player, stats.hunter)
        if (hunter < BATS[tier].level) {
            mes("You need a Hunter level of ${BATS[tier].level} to catch this bat.")
            return
        }
        val net = holds(cox_objs.butterfly_net) || holds(cox_objs.magic_butterfly_net)
        if (!net && hunter < 99) {
            mes("You need a butterfly net to catch bats.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have room for a bat.")
            return
        }
        anim(cox_seqs.net)
        delay(2)
        if (!npc.isSlotAssigned || !success(hunter)) {
            mes("You fail to catch the bat.")
            return
        }
        invAdd(inv, cox_objs.raw_bat[tier], 1)
        advance(stats.hunter, BATS[tier].xp)
        val room = raid.roomAt(npc.coords)
        val runtime = room?.let { rooms.runtime(raid, it) } as? CoxBatRoom
        runtime?.caught(npc)
    }

    companion object {
        const val KINDLING_CAP = 28
        const val KINDLING_FM_XP = 48.0
        const val CHOP_TICKS = 4
        const val FISH_TICKS = 5
        const val RAKE_TICKS = 4
        const val ANIM_TICKS = 4
        const val GROW_TICKS = 12
        const val SAPLING_RESPAWN = 50
        const val FIRE_TICKS = 150
        const val SNAKE_CHANCE = 40
        const val RAKE_XP = 2.0

        class Catch(val level: Int, val xp: Double)

        /** Wiki: the raid fish's Fishing levels and XP, pysk to kyren. */
        val FISH =
            listOf(
                Catch(1, 20.0),
                Catch(15, 23.0),
                Catch(30, 26.0),
                Catch(45, 29.0),
                Catch(60, 32.0),
                Catch(75, 35.0),
                Catch(90, 38.0),
            )

        /** Wiki: the raid bats' Hunter levels and XP, guanic to psykk. */
        val BATS =
            listOf(
                Catch(1, 5.0),
                Catch(15, 9.0),
                Catch(30, 13.0),
                Catch(45, 17.0),
                Catch(60, 21.0),
                Catch(75, 25.0),
                Catch(90, 29.0),
            )
    }
}

/** The raid's herbs: seed, patch stages, grimy herb, levels and XP (Farming XP are guesses). */
enum class CoxHerb(
    val farmingLevel: Int,
    val seed: ObjType,
    val grimy: ObjType,
    val stages: List<LocType>,
    val plantXp: Double,
    val pickXp: Double,
) {
    Golpar(
        27,
        cox_objs.golpar_seed,
        cox_objs.grimy_golpar,
        listOf(
            cox_locs.patch_golpar_seed,
            cox_locs.patch_golpar_1,
            cox_locs.patch_golpar_2,
            cox_locs.patch_golpar_3,
            cox_locs.patch_golpar_grown,
        ),
        8.0,
        10.0,
    ),
    Buchu(
        39,
        cox_objs.buchu_seed,
        cox_objs.grimy_buchu,
        listOf(
            cox_locs.patch_buchu_seed,
            cox_locs.patch_buchu_1,
            cox_locs.patch_buchu_2,
            cox_locs.patch_buchu_3,
            cox_locs.patch_buchu_grown,
        ),
        12.0,
        14.0,
    ),
    Noxifer(
        55,
        cox_objs.noxifer_seed,
        cox_objs.grimy_noxifer,
        listOf(
            cox_locs.patch_noxifer_seed,
            cox_locs.patch_noxifer_1,
            cox_locs.patch_noxifer_2,
            cox_locs.patch_noxifer_3,
            cox_locs.patch_noxifer_grown,
        ),
        16.0,
        18.0,
    );

    val grown: LocType
        get() = stages.last()
}

/**
 * The bats' resource room: 2 + raiders / 8 bats (6 at most) of one of the three best kinds the
 * party's top Hunter level allows (TSPS's reading of the Wiki). A caught bat is back 10 ticks later
 * (a guess); they flit about the room.
 */
class CoxBatRoom(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val bats = ArrayList<org.rsmod.game.entity.Npc>()

    override fun build() {
        complete()
    }

    override fun onEntered(player: Player) {
        if (bats.isNotEmpty() || !raid.started) return
        val hunter = raid.raiders.maxOfOrNull { it.player.stat(stats.hunter) } ?: 1
        val best = CoxResources.BATS.indexOfLast { hunter >= it.level }.coerceAtLeast(0)
        val count = (2 + raid.scaling.size / 8).coerceAtMost(6)
        repeat(count) { spawnBat(best) }
    }

    private fun spawnBat(best: Int) {
        val tier = (best - roll(0, 2)).coerceAtLeast(0)
        val home = open(tile(checkNotNull(room).type.sourceX(variant) + 16, room.type.sourceZ + 18))
        val at = open(home.translate(roll(-4, 4), roll(-4, 4)))
        bats += spawnAt(cox_npcs.bat_types[tier], at, passThrough = true)
    }

    fun caught(npc: org.rsmod.game.entity.Npc) {
        bats -= npc
        despawn(npc)
        val hunter = raid.raiders.maxOfOrNull { it.player.stat(stats.hunter) } ?: 1
        val best = CoxResources.BATS.indexOfLast { hunter >= it.level }.coerceAtLeast(0)
        later(BAT_RESPAWN) { spawnBat(best) }
    }

    override fun onAfter() {
        if (now % 3 != 0) return
        for (bat in bats) {
            if (!bat.isSlotAssigned || chance(2)) continue
            val dest = bat.coords.translate(roll(-1, 1), roll(-1, 1))
            if (inRoom(dest) && walkable(dest)) bat.walk(dest)
        }
    }

    companion object {
        const val BAT_RESPAWN = 10
    }
}
