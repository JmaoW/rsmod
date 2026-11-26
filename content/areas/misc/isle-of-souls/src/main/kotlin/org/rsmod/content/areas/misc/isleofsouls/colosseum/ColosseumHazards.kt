package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.skills.herblore.scripts.PotionEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid

/**
 * The modifiers that put something in the arena (wiki, "Fortis Colosseum/Modifiers" and the
 * Strategies page; tsps' estimates where the wiki has no number, marked):
 * - Bees!: 1-3 swarms (1 HP) step towards the nearest player every 12 ticks. Under one, a player
 *   takes up to 10 poison damage a tick (halved by antipoison) and is poisoned. A killed swarm
 *   comes back 30 seconds later.
 * - Solarflare: an orb circles a pillar, a tile every 2 ticks resting 7 at each corner; II never
 *   rests; III moves every tick, rests 2 and turns prayers off. Damage up to 5/10/15 (estimate).
 * - Totemic: a monster at half health or below gets a healing totem (1 HP) to its south-west. It
 *   heals 30% of the monster's health every 5 ticks (estimate) until destroyed; a destroyed one
 *   returns two minutes later, and it goes with its monster. Sol Heredit's heals 75 every 7.
 * - Volatility: a monster explodes 2 ticks after it dies, one (I) or two (II, III) tiles past its
 *   size, for up to 15 (estimate). III leaves molten sand on the middle tile for the wave.
 * - Reentry: where a sky javelin lands, molten sand for the wave; II makes it permanent and adds
 *   the tile south-west, III the tile west of that one (a free neighbour if a tile is blocked).
 *   With Reentry II or III, Volatility III's sand is permanent too.
 * - Molten sand: up to 15 every other tick, never between waves; Sol Heredit's is 6-8 a tick.
 *
 * None of these count towards clearing a wave, and each goes when the wave ends.
 */
class ColosseumHazards
@Inject
constructor(
    private val world: ColosseumWorld,
    private val poison: PlayerPoison,
    private val potions: PotionEffects,
) {
    fun waveStarted(run: ColosseumRun) {
        repeat(run.modifiers.tier(ColosseumModifier.Bees)) { run.bees += BeeSwarm(spawnBees(run)) }
        if (run.modifiers.has(ColosseumModifier.Solarflare)) {
            spawnFlare(run)
        }
    }

    fun tick(run: ColosseumRun) {
        if (run.stage != ColosseumStage.Wave) {
            return
        }
        bees(run)
        flare(run)
        sand(run)
        totems(run)
    }

    /** The wave is over: swarms, the orb and totems go, and so does sand that is not permanent. */
    fun waveEnded(run: ColosseumRun) {
        for (bee in run.bees) {
            world.remove(bee.npc)
        }
        run.bees.clear()
        world.remove(run.solarFlare?.npc)
        run.solarFlare = null
        for (totem in run.totems.values) {
            world.remove(totem.npc)
        }
        run.totems.clear()
        clearSand(run, all = false)
    }

    fun cleared(run: ColosseumRun) {
        waveEnded(run)
        clearSand(run, all = true)
    }

    /** Whether [npc] is one of the run's hazards, which never count towards clearing a wave. */
    fun isHazard(run: ColosseumRun, npc: Npc): Boolean =
        run.bees.any { it.npc === npc } ||
            run.solarFlare?.npc === npc ||
            run.totems.values.any { it.npc === npc }

    // Bees -------------------------------------------------------------------------------------

    private fun spawnBees(run: ColosseumRun): Npc {
        val players = run.fighters().map { run.real(it.player.coords) }
        val far =
            ColosseumArena.spawnPoints.filter { point ->
                players.all { ColosseumWorld.chebyshev(it, point) >= BEE_MIN_DISTANCE }
            }
        val pool = far.ifEmpty { ColosseumArena.spawnPoints }
        val npc = world.spawn(run, colosseum_npcs.bees, pool[world.roll(pool.size)])
        npc.anim(colosseum_seqs.bees_spawn)
        return npc
    }

    private fun bees(run: ColosseumRun) {
        val fighters = run.fighters()
        for (bee in run.bees) {
            val npc = bee.npc
            if (npc == null || !npc.isSlotAssigned || npc.hitpoints <= 0) {
                if (npc != null) {
                    // A killed swarm breaks up in its death queue, which then removes it.
                    if (npc.hitpoints > 0) {
                        world.remove(npc)
                    }
                    bee.npc = null
                    bee.respawnAt = world.now + BEE_RESPAWN
                }
                if (world.now >= bee.respawnAt) {
                    bee.npc = spawnBees(run)
                }
                continue
            }
            if (fighters.isEmpty()) {
                continue
            }
            val nearest = fighters.minBy { it.player.distanceTo(npc) }.player
            if ((world.now - run.waveStart) % BEE_STEP == 0) {
                stepTowards(npc, nearest.coords)
            }
            var stung = false
            for (member in fighters) {
                if (!covers(npc, member.player.coords)) {
                    continue
                }
                stung = true
                val player = member.player
                val damage = world.between(1, BEE_MAX)
                val protected = potions.hasPoisonProtection(player)
                world.hurt(player, if (protected) maxOf(1, damage / 2) else damage)
                poison.poison(player, BEE_POISON)
            }
            if (stung && world.now % BEE_STING_ANIM == 0) {
                npc.anim(colosseum_seqs.bees_attack)
            }
        }
    }

    /** One step closer, so that the player ends up under the 2x2 swarm. */
    private fun stepTowards(npc: Npc, target: CoordGrid) {
        val size = npc.size
        val dx = if (target.x < npc.coords.x) -1 else if (target.x >= npc.coords.x + size) 1 else 0
        val dz = if (target.z < npc.coords.z) -1 else if (target.z >= npc.coords.z + size) 1 else 0
        if (dx != 0 || dz != 0) {
            npc.walk(npc.coords.translate(dx, dz))
        }
    }

    private fun covers(npc: Npc, tile: CoordGrid): Boolean =
        tile.x >= npc.coords.x &&
            tile.x < npc.coords.x + npc.size &&
            tile.z >= npc.coords.z &&
            tile.z < npc.coords.z + npc.size

    // Solarflare ---------------------------------------------------------------------------------

    /** The 16 tiles round a 3x3 square, clockwise from its south-west corner, and the corners. */
    fun ring(southWest: CoordGrid): List<Pair<CoordGrid, Boolean>> {
        val minX = southWest.x - 1
        val minZ = southWest.z - 1
        val maxX = southWest.x + ColosseumArena.PILLAR_SIZE
        val maxZ = southWest.z + ColosseumArena.PILLAR_SIZE
        val tiles = ArrayList<CoordGrid>()
        for (z in minZ until maxZ) tiles += CoordGrid(minX, z, 0)
        for (x in minX until maxX) tiles += CoordGrid(x, maxZ, 0)
        for (z in maxZ downTo minZ + 1) tiles += CoordGrid(maxX, z, 0)
        for (x in maxX downTo minX + 1) tiles += CoordGrid(x, minZ, 0)
        return tiles.map {
            it to ((it.x == minX || it.x == maxX) && (it.z == minZ || it.z == maxZ))
        }
    }

    private fun spawnFlare(run: ColosseumRun) {
        val around = if (run.isFinalWave) ColosseumArena.solFlareCorners else ColosseumArena.pillars
        val path = ring(around[world.roll(around.size)])
        val index = world.roll(path.size)
        val npc = world.spawn(run, colosseum_npcs.solar_flare, path[index].first)
        run.solarFlare = SolarFlare(npc, path, index)
    }

    private fun flare(run: ColosseumRun) {
        val flare = run.solarFlare ?: return
        val tier = run.modifiers.tier(ColosseumModifier.Solarflare)
        if (tier == 0) {
            return
        }
        val moveEvery = if (tier >= 3) 1 else 2
        val rest = FLARE_REST[tier]
        if (world.now >= flare.restUntil && (world.now - run.waveStart) % moveEvery == 0) {
            flare.index = (flare.index + 1) % flare.path.size
            val (next, corner) = flare.path[flare.index]
            flare.npc.walk(run.inst(next))
            if (corner && rest > 0) {
                flare.restUntil = world.now + moveEvery + rest
            }
        }
        for (member in run.fighters()) {
            val player = member.player
            val touching = flare.npc.coords == player.coords
            if (touching && flare.touching.add(player.accountId)) {
                world.hurt(player, world.between(1, FLARE_MAX[tier]))
                if (tier >= 3) {
                    player.disablePrayers()
                    for (varbit in PROTECTIONS) {
                        VarPlayerIntMapSetter.set(player, varbit(), 0)
                    }
                    player.mes("<col=ef1020>The solar flare burns away your prayers!")
                }
            } else if (!touching) {
                flare.touching -= player.accountId
            }
        }
    }

    // Molten sand --------------------------------------------------------------------------------

    fun hasSand(run: ColosseumRun, real: CoordGrid): Boolean = real in run.sand

    fun addSand(run: ColosseumRun, real: CoordGrid, permanent: Boolean, solSand: Boolean = false) {
        val existing = run.sand[real]
        if (existing != null) {
            existing.permanent = existing.permanent || permanent
            return
        }
        val loc = world.addLoc(run, colosseum_locs.molten_sand, real)
        run.sand[real] = SandPool(loc, permanent, solSand)
    }

    private fun clearSand(run: ColosseumRun, all: Boolean) {
        val iterator = run.sand.entries.iterator()
        while (iterator.hasNext()) {
            val (_, pool) = iterator.next()
            if (!all && pool.permanent) {
                continue
            }
            world.removeLoc(pool.loc)
            iterator.remove()
        }
    }

    private fun sand(run: ColosseumRun) {
        val ticks = world.now - run.waveStart
        for (member in run.fighters()) {
            val pool = run.sand[run.real(member.player.coords)] ?: continue
            if (pool.solSand) {
                world.hurt(member.player, world.between(SOL_SAND_MIN, SOL_SAND_MAX))
            } else if (ticks % SAND_EVERY == 0) {
                world.hurt(member.player, world.between(1, SAND_MAX))
            }
        }
    }

    /** A free tile without sand next to [real], for a pool whose own tile is blocked or taken. */
    private fun nearbyFree(run: ColosseumRun, real: CoordGrid): CoordGrid? {
        val options = ArrayList<CoordGrid>()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val tile = real.translate(dx, dz)
                if ((dx != 0 || dz != 0) && world.free(run, tile) && !hasSand(run, tile)) {
                    options += tile
                }
            }
        }
        return if (options.isEmpty()) null else options[world.roll(options.size)]
    }

    private fun placeOrNearby(run: ColosseumRun, real: CoordGrid): CoordGrid? =
        if (world.free(run, real) && !hasSand(run, real)) real else nearbyFree(run, real)

    /** Reentry: a sky javelin landed on [real]. */
    fun javelinLanded(run: ColosseumRun, real: CoordGrid) {
        val tier = run.modifiers.tier(ColosseumModifier.Reentry)
        if (tier == 0 || run.stage != ColosseumStage.Wave) {
            return
        }
        val permanent = tier >= 2
        addSand(run, real, permanent)
        if (tier < 2) {
            return
        }
        val second = placeOrNearby(run, real.translate(-1, -1)) ?: return
        addSand(run, second, permanent)
        if (tier < 3) {
            return
        }
        placeOrNearby(run, second.translate(-1, 0))?.let { addSand(run, it, permanent) }
    }

    // Totems -------------------------------------------------------------------------------------

    private fun totems(run: ColosseumRun) {
        if (!run.modifiers.has(ColosseumModifier.Totemic)) {
            return
        }
        for (monster in run.monsters.toList()) {
            if (!monster.isSlotAssigned || monster.hitpoints <= 0) {
                continue
            }
            val max = monster.baseHitpointsLvl
            var totem = run.totems[monster]
            val npc = totem?.npc
            if (totem != null && npc != null && (!npc.isSlotAssigned || npc.hitpoints <= 0)) {
                world.remove(npc)
                totem.npc = null
                totem.respawnAt = world.now + TOTEM_RESPAWN
            }
            val wounded = monster.hitpoints * 2 <= max
            if (totem?.npc == null && wounded && world.now >= (totem?.respawnAt ?: 0)) {
                val southWest = run.real(monster.coords).translate(-1, -1)
                val tile = placeOrNearby(run, southWest) ?: continue
                val spawned = world.spawn(run, colosseum_npcs.totem, tile)
                if (totem == null) {
                    totem = HealingTotem(spawned, world.now + TOTEM_FIRST_HEAL)
                    run.totems[monster] = totem
                } else {
                    totem.npc = spawned
                    totem.nextHeal = world.now + TOTEM_FIRST_HEAL
                }
            }
            val healer = totem?.npc ?: continue
            if (world.now < totem.nextHeal || monster.hitpoints >= max) {
                continue
            }
            val sol = monster === run.sol?.npc
            val amount = if (sol) SOL_TOTEM_HEAL else max * TOTEM_SHARE / 100
            totem.nextHeal = world.now + if (sol) SOL_TOTEM_EVERY else TOTEM_EVERY
            healer.anim(colosseum_seqs.totem_attack)
            world.later(run, TOTEM_TRAVEL) {
                if (!healer.isSlotAssigned || healer.hitpoints <= 0) {
                    return@later
                }
                if (!monster.isSlotAssigned || monster.hitpoints <= 0) {
                    return@later
                }
                monster.healHitpoints(amount, max)
                monster.spotanim(colosseum_spotanims.totem_impact)
            }
        }
    }

    // Volatility ---------------------------------------------------------------------------------

    /** A monster of the wave died: its totem goes, and under Volatility it explodes. */
    fun monsterDied(run: ColosseumRun, npc: Npc, kind: ColosseumMonster) {
        run.totems.remove(npc)?.let { world.remove(it.npc) }
        val tier = run.modifiers.tier(ColosseumModifier.Volatility)
        if (tier == 0) {
            return
        }
        val sw = run.real(npc.coords)
        val size = npc.size
        val reach = minOf(tier, 2)
        val centre = sw.translate(size / 2, size / 2)
        if (kind == ColosseumMonster.Manticore) {
            npc.anim(colosseum_seqs.manticore_explode)
        }
        world.later(run, EXPLOSION_DELAY) {
            world.graphic(run, explosion(kind), centre)
            for (member in run.fighters()) {
                val at = run.real(member.player.coords)
                val inside =
                    at.x >= sw.x - reach &&
                        at.x < sw.x + size + reach &&
                        at.z >= sw.z - reach &&
                        at.z < sw.z + size + reach
                if (inside) {
                    world.hurt(member.player, world.between(1, EXPLOSION_MAX))
                }
            }
            if (tier >= 3 && world.free(run, centre)) {
                addSand(run, centre, run.modifiers.tier(ColosseumModifier.Reentry) >= 2)
            }
        }
    }

    private fun explosion(kind: ColosseumMonster): SpotanimType =
        when (kind) {
            ColosseumMonster.Sol -> colosseum_spotanims.explode_sol
            ColosseumMonster.Manticore -> colosseum_spotanims.explode_manticore
            ColosseumMonster.Javelin,
            ColosseumMonster.Shockwave -> colosseum_spotanims.explode_colossus
            ColosseumMonster.Minotaur -> colosseum_spotanims.explode_minotaur
            else -> colosseum_spotanims.explode_human
        }

    companion object {
        const val BEE_STEP = 12
        const val BEE_MAX = 10
        const val BEE_POISON = 1
        const val BEE_RESPAWN = 50

        /** The sting animation is 2 ticks long: played every other tick while it stings. */
        const val BEE_STING_ANIM = 2
        const val BEE_MIN_DISTANCE = 4

        val FLARE_REST = listOf(0, 7, 0, 2)
        val FLARE_MAX = listOf(0, 5, 10, 15)

        const val SAND_MAX = 15
        const val SAND_EVERY = 2
        const val SOL_SAND_MIN = 6
        const val SOL_SAND_MAX = 8

        const val TOTEM_FIRST_HEAL = 5
        const val TOTEM_EVERY = 5
        const val TOTEM_SHARE = 30
        const val TOTEM_RESPAWN = 200
        const val TOTEM_TRAVEL = 2
        const val SOL_TOTEM_HEAL = 75
        const val SOL_TOTEM_EVERY = 7

        const val EXPLOSION_DELAY = 2
        const val EXPLOSION_MAX = 15

        private val PROTECTIONS =
            listOf(
                { varbits.protect_from_melee },
                { varbits.protect_from_missiles },
                { varbits.protect_from_magic },
            )
    }
}
