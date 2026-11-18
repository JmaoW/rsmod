package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object MaidenNpcs : NpcReferences() {
    val forms =
        listOf("tob_maiden_100", "tob_maiden_70", "tob_maiden_50", "tob_maiden_30").map(::find)
    val dying_a = find("tob_maiden_dying_a")
    val dying_b = find("tob_maiden_dying_b")
    val matomenos = find("maiden_elemental")
    val blood_spawn = find("maiden_blood_slug")
}

object MaidenLocs : LocReferences() {
    val waiting = find("tob_maiden_initial")
    val pool = find("tob_maiden_dead_remains")
    val trail = find("tob_maiden_blood")
}

object MaidenSeqs : SeqReferences() {
    val blood = find("maiden_attack_blood")
    val blackstorm = find("maiden_attack_special")
    val death_a = find("maiden_death_a")
    val death_b = find("maiden_death_b")
    val matomenos_death = find("elemental_death")
    val blood_spawn_death = find("tob_blood_spawn_death")
}

object MaidenSpots : SpotanimReferences() {
    val shadow = find("maiden_shadow_proj")
    val blood = find("maiden_blood_proj")
    val lingering = find("maiden_lingering_blood")
}

object MaidenSynths : SynthReferences() {
    val blackstorm = find("shadow_blitz_impact")
    val blood = find("tob_maiden_blood_throw")
}

/**
 * Wave 1, the Maiden of Sugadinti. She appears when the fight starts and attacks every 10 ticks:
 * Blackstorm at the nearest player (always lands, up to 36 + 3.5 per Matomenos that reached her,
 * halved by Protect from Magic, draining stats half the time), or blood thrown on every player's
 * tile and two more round one of them (each splat hurts 10 + 2 per Matomenos a tick, heals her and
 * drains prayer; a splat may become a blood spawn, more likely if stood on). At 70, 50 and 30
 * percent she changes form and two Nylocas Matomenos per player (ten at most) walk from the walls
 * to her; each one that reaches her heals her twice its hitpoints. Frozen ones stop.
 *
 * Wiki numbers; TSPS's captures for the timings, projectiles and the Matomenos' tiles. Guesses:
 * blood one attack in four (no more than once in three), splats lasting 10 ticks, the blood spawn
 * roll, the trails' damage and life, the drained stats (attack and strength, or the style's).
 */
class MaidenRuntime(env: TobRoomEnv, raid: TobRaid) : TobRoomRuntime(env, raid, TobRoom.Maiden) {
    private class Splat(var ticks: Int, var stepped: Boolean)

    var maiden: Npc? = null
        private set

    private var form = 0
    var absorbed = 0
        private set

    private var attackIn = ATTACK_TICKS
    private var bloodCooldown = 0
    private val splats = LinkedHashMap<CoordGrid, Splat>()
    private val trails = LinkedHashMap<CoordGrid, Int>()
    val matomenos = LinkedHashSet<Npc>()
    val bloodSpawns = LinkedHashMap<Npc, CoordGrid>()
    private var dying = false

    override fun build() {
        placeLoc(SPAWN, MaidenLocs.waiting, angle = LocAngle.East)
    }

    override fun onStart() {
        removeLoc(SPAWN)
        val npc = spawn(MaidenNpcs.forms[0], SPAWN, scale = true, facing = Direction.East)
        maiden = npc
        bossBar = npc
    }

    override fun onTick() {
        val boss = maiden ?: return
        if (dying) {
            return
        }
        checkForms(boss)
        tickFloor(boss)
        tickMatomenos(boss)
        tickBloodSpawns()
        if (--attackIn <= 0) {
            attackIn = ATTACK_TICKS
            attack(boss)
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== maiden || dying) {
            return
        }
        dying = true
        transform(npc, MaidenNpcs.dying_a)
        npc.anim(MaidenSeqs.death_a)
        for (other in matomenos.toList() + bloodSpawns.keys.toList()) {
            despawn(other)
        }
        matomenos.clear()
        bloodSpawns.clear()
        clearTrails()
        splats.clear()
        complete()
        val fall = ticksOf(MaidenSeqs.death_a, least = 3)
        later(fall) {
            transform(npc, MaidenNpcs.dying_b)
            npc.anim(MaidenSeqs.death_b)
        }
        later(fall + ticksOf(MaidenSeqs.death_b, least = 3)) {
            despawn(npc)
            placeLoc(SPAWN, MaidenLocs.pool, angle = LocAngle.East)
        }
    }

    override fun bar(): TobBar = maiden?.let { barOf(it) } ?: TobBar.NONE

    // Attacks
    // -------------------------------------------------------------------------------------------

    private fun attack(boss: Npc) {
        val targets = fightPlayers()
        if (targets.isEmpty()) {
            return
        }
        if (bloodCooldown > 0) {
            bloodCooldown--
        }
        if (bloodCooldown == 0 && chance(BLOOD_ONE_IN)) {
            throwBlood(boss, targets)
        } else {
            blackstorm(boss, targets)
        }
    }

    private fun blackstorm(boss: Npc, targets: List<Player>) {
        val target = targets.minBy { gap(boss, it.coords) }
        boss.facePlayer(target)
        boss.anim(MaidenSeqs.blackstorm)
        sound(MaidenSynths.blackstorm)
        val from = centre(boss)
        val to = realOf(target.coords) ?: return
        val ticks = projectileBy(from, to, MaidenSpots.shadow, 109, 0, 10, heights = 0 to 0)
        val max = BLACKSTORM_BASE + (BLACKSTORM_PER * absorbed).toInt()
        val attack =
            NpcAttack(NpcAttackStyle.Magic, maxHit = max, alwaysHits = true, hitDelay = ticks)
        strike(boss, target, attack, through = 50, playAnim = false) { victim, hit ->
            if (hit.damage > 0 && env.random.of(2) == 0) {
                drain(victim, hit.damage)
            }
        }
    }

    /** Wiki: (damage + 1) / 5 off the stats of the target's style. */
    private fun drain(player: Player, damage: Int) {
        val amount = (damage + 1) / 5
        if (amount <= 0) {
            return
        }
        for (stat in listOf(stats.attack, stats.strength)) {
            player.statDrain(stat, amount, percent = 0)
        }
        player.mes("Your stats are drained.")
    }

    private fun throwBlood(boss: Npc, targets: List<Player>) {
        bloodCooldown = BLOOD_COOLDOWN + 1
        boss.anim(MaidenSeqs.blood)
        sound(MaidenSynths.blood)
        val from = centre(boss)
        val tiles = ArrayList<Pair<CoordGrid, Int>>()
        for (player in targets) {
            realOf(player.coords)?.let { tiles += it to BLOOD_DELAY }
        }
        val around = realOf(pick(targets).coords)
        if (around != null) {
            repeat(2) { tiles += around.translate(roll(-2, 2), roll(-2, 2)) to BLOOD_EXTRA_DELAY }
        }
        val aimed = HashSet<CoordGrid>()
        for ((tile, delay) in tiles) {
            if (!aimed.add(tile) || tile in splats || !room.inArena(tile) || !walkable(tile)) {
                continue
            }
            val ticks = projectileBy(from, tile, MaidenSpots.blood, delay, 0, 15, heights = 40 to 0)
            later(ticks) { landSplat(tile) }
        }
    }

    private fun landSplat(tile: CoordGrid) {
        if (!isStarted || tile in splats) {
            return
        }
        graphic(MaidenSpots.lingering, tile)
        val stepped = fightPlayers().any { realOf(it.coords) == tile }
        val splat = Splat(SPLAT_TICKS, stepped)
        splats[tile] = splat
        later(SPAWN_ROLL_TICKS) { rollBloodSpawn(tile, splat.stepped) }
    }

    private fun rollBloodSpawn(tile: CoordGrid, stepped: Boolean) {
        if (!isStarted || bloodSpawns.size >= MAX_BLOOD_SPAWNS) {
            return
        }
        val percent = if (stepped) 20 else 10
        if (env.random.of(100) >= percent) {
            return
        }
        bloodSpawns[spawn(MaidenNpcs.blood_spawn, tile)] = tile
    }

    // The floor
    // ------------------------------------------------------------------------------------------

    private fun tickFloor(boss: Npc) {
        splats.entries.removeIf { --it.value.ticks <= 0 }
        val expired = trails.filterValues { it <= 1 }.keys
        for (tile in expired) {
            trails.remove(tile)
            removeLoc(tile)
        }
        trails.replaceAll { _, left -> left - 1 }
        for (player in fightPlayers()) {
            val here = realOf(player.coords) ?: continue
            val splat = splats[here]
            if (splat != null) {
                splat.stepped = true
                val damage = SPLAT_BASE + SPLAT_PER * absorbed
                hurt(player, damage)
                heal(boss, damage)
                player.statSub(stats.prayer, damage / 2, percent = 0)
            } else if (here in trails) {
                val damage = roll(TRAIL_MIN, TRAIL_MAX)
                hurt(player, damage)
                heal(boss, damage)
            }
        }
    }

    private fun clearTrails() {
        for (tile in trails.keys) {
            removeLoc(tile)
        }
        trails.clear()
    }

    // Forms and Matomenos
    // -----------------------------------------------------------------------------------------

    private fun checkForms(boss: Npc) {
        while (
            form < THRESHOLDS.size &&
                boss.hitpoints * 100 <= boss.baseHitpointsLvl * THRESHOLDS[form]
        ) {
            form++
            transform(boss, MaidenNpcs.forms[form])
            spawnMatomenos()
        }
    }

    private fun spawnMatomenos() {
        val count = minOf(MAX_MATOMENOS, 2 * maxOf(1, fightPlayers().size))
        val tiles =
            if (count >= MATOMENOS_TILES.size) MATOMENOS_TILES
            else shuffled(MATOMENOS_TILES).take(count)
        for (tile in tiles) {
            val facing = if (tile.z > SPAWN.z) Direction.SouthWest else Direction.NorthWest
            matomenos +=
                spawn(MaidenNpcs.matomenos, tile, scale = true, facing = facing, passThrough = true)
        }
    }

    private fun tickMatomenos(boss: Npc) {
        for (nylo in matomenos.toList()) {
            if (!nylo.isSlotAssigned || nylo.hitpoints <= 0) {
                matomenos -= nylo
                continue
            }
            if (gap(boss, nylo.coords) <= 1) {
                heal(boss, 2 * nylo.hitpoints)
                absorbed++
                matomenos -= nylo
                despawn(nylo)
                continue
            }
            if (nylo.isFrozen) {
                continue
            }
            val home = boss.coords
            val towards =
                CoordGrid(
                    home.x + boss.size,
                    nylo.coords.z.coerceIn(home.z, home.z + boss.size - 1),
                    home.level,
                )
            stepToward(nylo, towards, keepOff = false)
        }
    }

    private fun tickBloodSpawns() {
        for ((spawn, home) in bloodSpawns.toList()) {
            if (!spawn.isSlotAssigned || spawn.hitpoints <= 0) {
                bloodSpawns.remove(spawn)
                continue
            }
            val tile = realOf(spawn.coords) ?: continue
            leaveTrail(tile)
            if (spawn.isFrozen || !chance(WANDER_ONE_IN)) {
                continue
            }
            val dest =
                home.translate(
                    roll(-WANDER_RADIUS, WANDER_RADIUS),
                    roll(-WANDER_RADIUS, WANDER_RADIUS),
                )
            if (room.inArena(dest)) {
                stepToward(spawn, regionOf(dest), keepOff = false)
            }
        }
    }

    private fun leaveTrail(tile: CoordGrid) {
        if (tile !in trails) {
            placeLoc(tile, MaidenLocs.trail)
        }
        trails[tile] = TRAIL_TICKS
    }

    override fun monsterDied(npc: Npc) {
        matomenos -= npc
        bloodSpawns.remove(npc)
    }

    private fun centre(npc: Npc): CoordGrid {
        val real = realOf(npc.coords) ?: SPAWN
        return real.translate(npc.size / 2, npc.size / 2)
    }

    companion object {
        /** Her south-west tile (the map's waiting Maiden stands here). */
        val SPAWN = CoordGrid(3162, 4444, 0)

        const val ATTACK_TICKS = 10
        private const val BLOOD_ONE_IN = 4
        private const val BLOOD_COOLDOWN = 2
        private const val BLOOD_DELAY = 20
        private const val BLOOD_EXTRA_DELAY = 45
        const val BLACKSTORM_BASE = 36
        const val BLACKSTORM_PER = 3.5
        const val SPLAT_BASE = 10
        const val SPLAT_PER = 2
        private const val SPLAT_TICKS = 10
        private const val SPAWN_ROLL_TICKS = 9
        private const val MAX_BLOOD_SPAWNS = 8
        private const val WANDER_ONE_IN = 3
        private const val WANDER_RADIUS = 4
        private const val TRAIL_TICKS = 40
        private const val TRAIL_MIN = 5
        private const val TRAIL_MAX = 13
        val THRESHOLDS = listOf(70, 50, 30)
        private const val MAX_MATOMENOS = 10

        /** Five each side, north then south, as TSPS captured them. */
        val MATOMENOS_TILES =
            listOf(
                CoordGrid(3173, 4456, 0),
                CoordGrid(3177, 4456, 0),
                CoordGrid(3181, 4456, 0),
                CoordGrid(3184, 4456, 0),
                CoordGrid(3184, 4454, 0),
                CoordGrid(3173, 4436, 0),
                CoordGrid(3177, 4436, 0),
                CoordGrid(3181, 4436, 0),
                CoordGrid(3184, 4436, 0),
                CoordGrid(3184, 4438, 0),
            )
    }
}

class TobMaidenScript
@Inject
constructor(private val rooms: TobRoomRegistry, private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Maiden) { env, raid -> MaidenRuntime(env, raid) }
        tobBosses(rooms, MaidenNpcs.forms + listOf(MaidenNpcs.dying_a, MaidenNpcs.dying_b))
        tobMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(
                MaidenNpcs.matomenos to MaidenSeqs.matomenos_death,
                MaidenNpcs.blood_spawn to MaidenSeqs.blood_spawn_death,
            ),
        )
    }
}
