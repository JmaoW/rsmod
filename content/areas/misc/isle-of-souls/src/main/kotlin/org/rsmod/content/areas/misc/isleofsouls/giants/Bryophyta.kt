package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** What Bryophyta's lair remembers about one team's fight. */
class BryophytaRun(instance: TeamInstance, startClock: Int) :
    GiantRun(instance, startClock, GiantLairs.bryophyta) {
    /** The tick of her next attack. */
    var attackAt = 0

    /** Her growthlings are (or were, until the last one died) out. */
    var hadGrowthlings = false

    /** The tick the last batch of growthlings died. */
    var clearedAt = Int.MIN_VALUE / 2
}

/**
 * Bryophyta, the moss giantess, as captured by tsps with the wiki's rules (115 hitpoints, max hits
 * 16 melee and 10 magic, attack speed 6, poison 8):
 * - she never moves: next to her, half her attacks are a punch that can poison; every other attack
 *   (and everything further than a tile away, up to eight) is an earth blast;
 * - once she has lost a quarter of her hitpoints, each attack has a 1 in 5 chance to summon three
 *   growthlings, one batch at a time and at least 20 ticks after the last one died;
 * - while any growthling lives she takes no damage. An axe or magic secateurs equipped kills one at
 *   once ("You prune the Growthling."); anything else can hurt it, never kill it;
 * - protection prayers are fully effective against her.
 */
class Bryophyta
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    players: PlayerList,
    random: GameRandom,
    locRepo: LocRepository,
    locTypes: LocTypeList,
    private val attacks: NpcAttacks,
    private val poison: PlayerPoison,
) :
    GiantDirector<BryophytaRun>(
        GiantLairs.bryophyta,
        instances,
        npcRepo,
        npcTypes,
        interactions,
        collision,
        mapClock,
        players,
        random,
        locRepo,
        locTypes,
    ) {
    override val bossType: NpcType
        get() = giant_npcs.bryophyta

    override fun newRun(instance: TeamInstance): BryophytaRun =
        BryophytaRun(instance, mapClock.cycle)

    /** She attacks at once: the players are in the lair when she appears. */
    override fun bossSpawned(run: BryophytaRun, boss: Npc) {
        run.attackAt = mapClock.cycle
        run.hadGrowthlings = false
        run.clearedAt = Int.MIN_VALUE / 2
    }

    override fun engage(run: BryophytaRun, npc: Npc, target: Player): Boolean {
        if (npc.type.id != giant_npcs.growthling.id) {
            return false
        }
        engage(npc, target, ranged = false)
        return true
    }

    override fun fight(run: BryophytaRun, boss: Npc, targets: List<Player>, now: Int) {
        if (run.hadGrowthlings && liveGrowthlings(run) == 0) {
            run.hadGrowthlings = false
            run.clearedAt = now
        }
        val target = targets.minBy { boss.distanceTo(it) }
        boss.facePlayer(target)
        if (now < run.attackAt || boss.distanceTo(target) > REACH) {
            return
        }
        run.attackAt = now + ATTACK_RATE
        val melee = boss.distanceTo(target) <= MELEE_REACH && random.of(2) == 0
        attacks.attack(boss, target, if (melee) punch else blast, onImpact = poisonOnHit)
        maybeSummon(run, boss, now)
    }

    /** Poison from a punch that lands: 1 in 4, starting at 8 (wiki: members' worlds). */
    private val poisonOnHit = NpcImpactListener { target, hit ->
        if (hit.damage > 0 && hit.type == org.rsmod.game.hit.HitType.Melee) {
            if (random.of(POISON_ODDS) == 0) {
                poison.poison(target, POISON_DAMAGE)
            }
        }
    }

    /** How many of her growthlings are alive. */
    fun liveGrowthlings(run: GiantRun): Int =
        run.monsters.count { it.isSlotAssigned && it.hitpoints > 0 }

    /** Whether the boss [npc] cannot be hurt right now: a growthling is alive. */
    fun isImmune(npc: Npc): Boolean {
        val run = runOf(npc) ?: return false
        return liveGrowthlings(run) > 0
    }

    private fun maybeSummon(run: BryophytaRun, boss: Npc, now: Int) {
        val lost = boss.baseHitpointsLvl - boss.hitpoints
        if (lost * 100 < boss.baseHitpointsLvl * SUMMON_AFTER_PERCENT) {
            return
        }
        if (random.of(SUMMON_ODDS) != 0) {
            return
        }
        if (liveGrowthlings(run) > 0 || now - run.clearedAt < SUMMON_COOLDOWN) {
            return
        }
        summonGrowthlings(run, now)
    }

    /** Three growthlings come up on free tiles round her. */
    fun summonGrowthlings(run: BryophytaRun, now: Int) {
        val size = npcTypes[giant_npcs.growthling].size
        val taken = ArrayList<Pair<CoordGrid, Int>>()
        repeat(GROWTHLINGS) {
            val wanted =
                GiantLairs.bryophyta.spawn.translate(
                    1 + random.of(-GROWTHLING_RADIUS..GROWTHLING_RADIUS),
                    1 + random.of(-GROWTHLING_RADIUS..GROWTHLING_RADIUS),
                )
            val spot = freeSpot(run, wanted, size, taken)
            taken += spot to size
            val growthling = spawn(run, giant_npcs.growthling, spot)
            growthling.actionDelay = now + GROWTHLING_WAKE_TICKS
            run.monsters += growthling
        }
        run.hadGrowthlings = true
    }

    /** A growthling bites: slash, max hit 5, every 4 ticks (wiki). */
    fun growthlingBite(npc: Npc, target: Player) {
        if (npc.actionDelay > mapClock.cycle) {
            return
        }
        attacks.attack(npc, target, growthlingAttack)
        npc.actionDelay = mapClock.cycle + GROWTHLING_RATE
    }

    companion object {
        /** The wiki: attack speed 6; max hits 16 melee and 10 magic. */
        const val ATTACK_RATE = 6
        const val MELEE_MAX_HIT = 16
        const val MAGIC_MAX_HIT = 10

        /** She punches only a player next to her and casts at anyone within eight tiles. */
        const val MELEE_REACH = 1
        const val REACH = 8

        const val POISON_ODDS = 4
        const val POISON_DAMAGE = 8

        /** Wiki: growthlings can come once she has lost about a quarter of her hitpoints. */
        const val SUMMON_AFTER_PERCENT = 25
        const val SUMMON_ODDS = 5
        const val SUMMON_COOLDOWN = 20
        const val GROWTHLINGS = 3
        const val GROWTHLING_RADIUS = 5
        const val GROWTHLING_WAKE_TICKS = 2

        /** The growthling: max hit 5, attack speed 4 (wiki). */
        const val GROWTHLING_MAX_HIT = 5
        const val GROWTHLING_RATE = 4

        val punch =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = giant_seqs.bryophyta_punch,
                maxHit = MELEE_MAX_HIT,
            )

        val blast =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = giant_seqs.bryophyta_cast,
                sound = giant_synths.earth_blast_cast,
                projectile = giant_spotanims.earth_blast,
                projanim = projanims.magic_spell,
                impactSpot = giant_spotanims.earth_blast_impact,
                maxHit = MAGIC_MAX_HIT,
            )

        val growthlingAttack =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = giant_seqs.growthling_attack,
                maxHit = GROWTHLING_MAX_HIT,
            )
    }
}
