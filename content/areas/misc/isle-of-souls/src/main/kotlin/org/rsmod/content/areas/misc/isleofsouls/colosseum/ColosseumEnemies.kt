package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

/**
 * The Colosseum's seven kinds of monster (wiki: "Fortis Colosseum" and its Strategies page; tsps'
 * notes on the live game for the animations, graphics and timings):
 *
 * | Monster | Max hit | Range | Speed | | |---------|---------|-------|-------|--| | Berserker | 29
 * | 1 | 6 | stab; always takes a max hit from magic | | Seer | 12 | 1 | 6 | magic from melee range;
 * ranged always max hits it | | Archer | 14 | 1 | 6 | ranged from melee range; melee always max
 * hits it | | Serpent shaman | 28 | 10 | 5 | magic | | Jaguar warrior | 47 x3 | 1 | 5 | slash,
 * three hits each rolled on its own | | Javelin Colossus | 48 | 15 | 5 | every fifth attack a sky
 * javelin, up to 40 typeless | | Manticore | 31/36/31 | 15 | 10 | magic, ranged, melee orbs, prayed
 * against as thrown | | Shockwave Colossus | 56 | 15 | 5 | magic | | Minotaur | 74 | 1 | 5 | crush,
 * lands a tick later; heals the others out of melee range |
 *
 * Every monster waits 3 ticks after it appears; the trio then swing a tick apart. The warband run,
 * walk through other npcs and head each for their own side of their target (berserker north, seer
 * east, archer west, Quartet's extra one south), and a swing that comes while one is still moving
 * is skipped. The Minotaur is easily safespotted, except the Red Flag one, which finds its way.
 */
class ColosseumEnemies
@Inject
constructor(
    private val world: ColosseumWorld,
    private val attacks: NpcAttacks,
    private val interactions: AiPlayerInteractions,
    private val effects: ColosseumEffects,
    private val hazards: ColosseumHazards,
) {
    /** The kind of monster [type] is, if it is one of the waves'. */
    fun kindOf(type: NpcType): ColosseumMonster? = kinds[type.id]

    private val kinds: Map<Int, ColosseumMonster> by lazy {
        val n = colosseum_npcs
        mapOf(
            n.berserker.id to ColosseumMonster.Berserker,
            n.seer.id to ColosseumMonster.Seer,
            n.archer.id to ColosseumMonster.Archer,
            n.shaman.id to ColosseumMonster.Shaman,
            n.jaguar.id to ColosseumMonster.Jaguar,
            n.javelin.id to ColosseumMonster.Javelin,
            n.manticore.id to ColosseumMonster.Manticore,
            n.shockwave.id to ColosseumMonster.Shockwave,
            n.minotaur.id to ColosseumMonster.Minotaur,
            n.minotaur_routefind.id to ColosseumMonster.Minotaur,
            n.sol.id to ColosseumMonster.Sol,
        )
    }

    fun typeOf(kind: ColosseumMonster, redFlag: Boolean): NpcType =
        when (kind) {
            ColosseumMonster.Berserker -> colosseum_npcs.berserker
            ColosseumMonster.Seer -> colosseum_npcs.seer
            ColosseumMonster.Archer -> colosseum_npcs.archer
            ColosseumMonster.Shaman -> colosseum_npcs.shaman
            ColosseumMonster.Jaguar -> colosseum_npcs.jaguar
            ColosseumMonster.Javelin -> colosseum_npcs.javelin
            ColosseumMonster.Manticore -> colosseum_npcs.manticore
            ColosseumMonster.Shockwave -> colosseum_npcs.shockwave
            ColosseumMonster.Minotaur ->
                if (redFlag) colosseum_npcs.minotaur_routefind else colosseum_npcs.minotaur
            ColosseumMonster.Sol -> colosseum_npcs.sol
        }

    /** A monster has just appeared: the 3-tick wait (and the trio's stagger), and its setup. */
    fun spawned(run: ColosseumRun, npc: Npc, state: ColosseumMonsterState) {
        effects.setUp(run, npc)
        npc.actionDelay = world.now + SPAWN_DELAY + (TRIO_STAGGER[state.kind] ?: 0)
        if (state.kind.isWarband) {
            npc.defaultMoveSpeed = MoveSpeed.Run
            npc.anim(colosseum_seqs.human_spawn)
        } else if (state.kind == ColosseumMonster.Shaman) {
            npc.anim(colosseum_seqs.human_spawn)
        } else if (state.kind == ColosseumMonster.Manticore) {
            // Each of a wave's two Manticores takes its own pose.
            val others =
                run.states.count { (other, it) ->
                    other !== npc && it.kind == ColosseumMonster.Manticore
                }
            npc.anim(
                if (others == 0) colosseum_seqs.manticore_spawn_1
                else colosseum_seqs.manticore_spawn_2
            )
        } else if (state.kind == ColosseumMonster.Minotaur) {
            npc.anim(colosseum_seqs.minotaur_spawn)
        }
        if (npc.type.id == colosseum_npcs.minotaur_routefind.id) {
            npc.defaultMoveSpeed = MoveSpeed.Walk
        }
    }

    // Aiming ------------------------------------------------------------------------------------

    /**
     * Keeps every monster of the wave on a player: the one it is fighting while that player is
     * still in the fight, else the nearest. The warband and Red Flag's Minotaur move by themselves
     * (see [tick]); the rest are left to the engine's chase.
     */
    fun aim(run: ColosseumRun) {
        val targets = run.fighters().map { it.player }.filter { it.isValidTarget() }
        if (targets.isEmpty()) {
            return
        }
        for (npc in run.monsters) {
            val state = run.states[npc] ?: continue
            if (!npc.isSlotAssigned || npc.hitpoints <= 0 || state.kind == ColosseumMonster.Sol) {
                continue
            }
            val current = (npc.interaction as? InteractionPlayerOp)?.target ?: state.target
            val target =
                if (current != null && current in targets) current
                else targets.minBy { it.distanceTo(npc) }
            state.target = target
            // The arena is bigger than an npc's leash from where it appeared.
            npc.spawnCoords = target.coords
            if (scripted(npc, state)) {
                continue
            }
            val engaged = (npc.interaction as? InteractionPlayerOp)?.target === target
            if (!engaged || npc.mode == NpcMode.None) {
                if (MELEE.contains(state.kind)) {
                    npc.opPlayer2(target, interactions)
                } else {
                    npc.apPlayer2(target, interactions)
                }
            }
        }
    }

    /** The warband and Red Flag's Minotaur: moved and made to attack by [tick], not the engine. */
    private fun scripted(npc: Npc, state: ColosseumMonsterState): Boolean =
        state.kind.isWarband || npc.type.id == colosseum_npcs.minotaur_routefind.id

    /** Every tick of a wave: the scripted movers, the Minotaurs' healing. */
    fun tick(run: ColosseumRun) {
        val trio = run.monsters.filter { run.states[it]?.kind?.isWarband == true }
        for (npc in run.monsters.toList()) {
            val state = run.states[npc] ?: continue
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                continue
            }
            when {
                state.kind.isWarband -> warband(run, npc, state, trio)
                npc.type.id == colosseum_npcs.minotaur_routefind.id -> routefinder(run, npc, state)
            }
            if (state.kind == ColosseumMonster.Minotaur) {
                minotaurHeals(run, npc, state)
            }
        }
    }

    // The warband ---------------------------------------------------------------------------------

    private fun warband(
        run: ColosseumRun,
        npc: Npc,
        state: ColosseumMonsterState,
        trio: List<Npc>,
    ) {
        val target = state.target ?: return
        if (npc.mode != NpcMode.None) {
            npc.mode = NpcMode.None
        }
        npc.facePlayer(target)
        val moved = state.walkTarget != null && npc.coords != state.walkTarget
        val tile = sideTile(run, npc, state, target, trio)
        if (tile != null && npc.coords != tile) {
            if (state.walkTarget != tile || !moved) {
                state.walkTarget = tile
                world.walk(npc, tile, smart = true)
            }
            state.movedAt = world.now
        }
        if (world.now < npc.actionDelay) {
            return
        }
        val beside = besideCardinal(npc.coords, target.coords)
        if (!beside) {
            return
        }
        if (state.movedAt == world.now) {
            // A swing that comes while it is still moving is skipped: the next waits a full cycle.
            npc.actionDelay = world.now + WARBAND_SPEED
            return
        }
        attack(run, npc, state.kind, target)
        npc.actionDelay = world.now + WARBAND_SPEED
    }

    /**
     * Where one of the trio heads: its own side of its target, or the nearest open side no other
     * warbander claims when a pillar takes it; `null` to hold still.
     */
    private fun sideTile(
        run: ColosseumRun,
        npc: Npc,
        state: ColosseumMonsterState,
        target: Player,
        trio: List<Npc>,
    ): CoordGrid? {
        val at = target.coords
        val (dx, dz) = state.side
        val own = at.translate(dx, dz)
        if (freeInstance(own)) {
            return own
        }
        val claimed = trio.filter { it !== npc }.mapNotNull { run.states[it]?.side }.toSet()
        return CARDINALS.filter { it !in claimed }
            .map { (x, z) -> at.translate(x, z) }
            .filter { freeInstance(it) }
            .minByOrNull { ColosseumWorld.chebyshev(it, npc.coords) }
    }

    private fun freeInstance(tile: CoordGrid): Boolean =
        (world.collision[tile.x, tile.z, tile.level] and ColosseumWorld.BLOCKED) == 0

    private fun besideCardinal(a: CoordGrid, b: CoordGrid): Boolean {
        val dx = abs(a.x - b.x)
        val dz = abs(a.z - b.z)
        return a.level == b.level && dx + dz == 1
    }

    /** Red Flag's Minotaur finds its way round the pillars to its target, then swings. */
    private fun routefinder(run: ColosseumRun, npc: Npc, state: ColosseumMonsterState) {
        val target = state.target ?: return
        if (npc.mode != NpcMode.None) {
            npc.mode = NpcMode.None
        }
        npc.facePlayer(target)
        if (npc.distanceTo(target) > 1) {
            if (state.walkTarget != target.coords) {
                state.walkTarget = target.coords
                world.walk(npc, target.coords, smart = true)
            }
            return
        }
        npc.abortRoute()
        state.walkTarget = null
        if (world.now >= npc.actionDelay) {
            attack(run, npc, state.kind, target)
            npc.actionDelay = world.now + speedOf(state.kind)
        }
    }

    /**
     * A Minotaur not in melee range of its target heals every hurt monster within 6 tiles to full,
     * every 5 ticks. The wiki says it heals and gives no rate; tsps uses 5.
     */
    private fun minotaurHeals(run: ColosseumRun, npc: Npc, state: ColosseumMonsterState) {
        val target = state.target ?: return
        if (
            (world.now - state.spawnedAt) % MINOTAUR_HEAL_EVERY != 0 || npc.distanceTo(target) <= 1
        ) {
            return
        }
        for (other in run.monsters) {
            if (other === npc || !other.isSlotAssigned || other.hitpoints <= 0) {
                continue
            }
            if (ColosseumWorld.chebyshev(other.coords, npc.coords) > MINOTAUR_HEAL_RANGE) {
                continue
            }
            if (other.hitpoints < other.baseHitpointsLvl) {
                other.hitpoints = other.baseHitpointsLvl
            }
        }
    }

    // Attacks -----------------------------------------------------------------------------------

    /**
     * The engine's chase has [npc] in range of [target] (or a scripted mover is beside it): it
     * attacks when its last attack's delay has run out.
     */
    fun fight(run: ColosseumRun, npc: Npc, target: Player) {
        val state = run.states[npc] ?: return
        if (!target.isValidTarget() || world.now < npc.actionDelay) {
            return
        }
        val speed = attack(run, npc, state.kind, target)
        npc.actionDelay = world.now + speed
    }

    /** Makes [npc]'s attack on [target]. Returns the ticks until its next one. */
    private fun attack(run: ColosseumRun, npc: Npc, kind: ColosseumMonster, target: Player): Int {
        when (kind) {
            ColosseumMonster.Javelin -> javelin(run, npc, target)
            ColosseumMonster.Manticore -> manticore(run, npc, target)
            else -> attacks.attack(npc, target, relentless(run, ATTACKS.getValue(kind)))
        }
        return speedOf(kind)
    }

    private fun speedOf(kind: ColosseumMonster): Int = SPEEDS.getValue(kind)

    /** The attack with Relentless's higher max hit and, at III, no miss. */
    private fun relentless(run: ColosseumRun, attack: NpcAttack): NpcAttack {
        val bonus = effects.maxHitBonus(run)
        if (bonus == 0) {
            return attack
        }
        return attack.copy(
            maxHit = (attack.maxHit ?: 0) + bonus,
            alwaysHits = attack.alwaysHits || effects.alwaysHits(run),
        )
    }

    /** Every fifth attack is the javelin thrown into the air, landing on the target's tile. */
    private fun javelin(run: ColosseumRun, npc: Npc, target: Player) {
        val state = run.states[npc] ?: return
        state.attacks++
        if (state.attacks % ARTILLERY_EVERY != 0) {
            attacks.attack(npc, target, relentless(run, ATTACKS.getValue(ColosseumMonster.Javelin)))
            return
        }
        npc.anim(colosseum_seqs.javelin_artillery)
        npc.spotanim(colosseum_spotanims.artillery_fire, delay = ARTILLERY_FIRE_DELAY)
        val at = run.real(target.coords)
        world.later(run, ARTILLERY_LANDS) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0 || run.stage != ColosseumStage.Wave) {
                return@later
            }
            world.graphic(run, colosseum_spotanims.artillery_shadow, at, delay = 30)
            world.graphic(run, colosseum_spotanims.artillery_land, at, delay = 30)
            hazards.javelinLanded(run, at)
            for (member in run.fighters()) {
                if (run.real(member.player.coords) == at) {
                    // The sky javelin ignores prayer and does not cost the "no damage" Glory.
                    val max = ARTILLERY_MAX + effects.maxHitBonus(run)
                    world.hurt(member.player, world.between(0, max))
                }
            }
        }
    }

    /**
     * The Manticore charges three orbs, magic and ranged in either order and then melee (any order
     * with Mantimayhem III), shown on its body as they charge, and throws them a tick apart from
     * the sixth tick. Each orb is prayed against as it is thrown (wiki), so the hit ignores the
     * prayer the player has when it lands. Mantimayhem I throws two of each orb, II envenoms when
     * an orb is not prayed against.
     */
    private fun manticore(run: ColosseumRun, npc: Npc, target: Player) {
        npc.anim(colosseum_seqs.manticore_charge)
        val any = run.modifiers.tier(ColosseumModifier.Mantimayhem) >= 3
        val orders = if (any) ORB_ORDERS_ANY else ORB_ORDERS
        val order = orders[world.roll(orders.size)]
        for ((index, orb) in order.withIndex()) {
            for (tick in index until MANTICORE_THROW + index) {
                world.later(run, maxOf(1, tick)) {
                    if (npc.isSlotAssigned && npc.hitpoints > 0) {
                        npc.spotanim(orb.charged(), height = orb.height, slot = index + 1)
                    }
                }
            }
            world.later(run, MANTICORE_THROW + index) { throwOrb(run, npc, target, orb, index) }
        }
    }

    private fun throwOrb(run: ColosseumRun, npc: Npc, target: Player, orb: Orb, index: Int) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || run.stage != ColosseumStage.Wave) {
            return
        }
        if (!target.isValidTarget() || run.memberOf(target)?.dead != false) {
            return
        }
        if (index == 0) {
            npc.anim(colosseum_seqs.manticore_throw)
        }
        val prayed = target.vars[orb.protection()] == 1
        if (!prayed) {
            effects.orbUnprayed(run, target)
        }
        val blocked = NpcImpactListener { _, hit -> if (prayed) hit.damage = 0 }
        val base = orb.attack().copy(typeless = true)
        val count = if (run.modifiers.has(ColosseumModifier.Mantimayhem)) 2 else 1
        repeat(count) { attacks.attack(npc, target, relentless(run, base), onImpact = blocked) }
    }

    private class Orb(
        val charged: () -> SpotanimType,
        val height: Int,
        val protection: () -> VarBitType,
        val attack: () -> NpcAttack,
    )

    private val MAGIC_ORB =
        Orb({ colosseum_spotanims.orb_magic }, 380, { varbits.protect_from_magic }) {
            orbAttack(
                NpcAttackStyle.Magic,
                colosseum_spotanims.orb_magic,
                colosseum_spotanims.orb_magic_impact,
                31,
            )
        }
    private val RANGED_ORB =
        Orb({ colosseum_spotanims.orb_ranged }, 290, { varbits.protect_from_missiles }) {
            orbAttack(
                NpcAttackStyle.Ranged,
                colosseum_spotanims.orb_ranged,
                colosseum_spotanims.orb_ranged_impact,
                36,
            )
        }
    private val MELEE_ORB =
        Orb({ colosseum_spotanims.orb_melee }, 470, { varbits.protect_from_melee }) {
            orbAttack(
                NpcAttackStyle.Melee,
                colosseum_spotanims.orb_melee,
                colosseum_spotanims.orb_melee_impact,
                31,
            )
        }

    private val ORB_ORDERS: List<List<Orb>> by lazy {
        listOf(listOf(MAGIC_ORB, RANGED_ORB, MELEE_ORB), listOf(RANGED_ORB, MAGIC_ORB, MELEE_ORB))
    }

    private val ORB_ORDERS_ANY: List<List<Orb>> by lazy {
        val orbs = listOf(MAGIC_ORB, RANGED_ORB, MELEE_ORB)
        orbs.flatMap { a ->
            orbs
                .filter { it !== a }
                .flatMap { b -> orbs.filter { it !== a && it !== b }.map { c -> listOf(a, b, c) } }
        }
    }

    private fun orbAttack(
        style: NpcAttackStyle,
        travel: SpotanimType,
        impact: SpotanimType,
        max: Int,
    ): NpcAttack =
        NpcAttack(
            style = style,
            anim = colosseum_seqs.manticore_throw,
            projectile = travel,
            projanim = colosseum_projanims.orb,
            impactSpot = impact,
            maxHit = max,
        )

    /** The ordinary attacks, by monster (the Javelin's is its plain throw). */
    private val ATTACKS: Map<ColosseumMonster, NpcAttack> by lazy {
        val s = colosseum_spotanims
        val q = colosseum_seqs
        val p = colosseum_projanims
        mapOf(
            ColosseumMonster.Berserker to
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = q.berserker_attack,
                    maxHit = 29,
                    meleeType = MeleeAttackType.Stab,
                ),
            ColosseumMonster.Seer to
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = q.seer_attack,
                    projectile = s.seer_travel,
                    projanim = p.seer,
                    impactSpot = s.seer_impact,
                    maxHit = 12,
                ),
            ColosseumMonster.Archer to
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = q.archer_attack,
                    projectile = s.archer_travel,
                    projanim = p.archer,
                    maxHit = 14,
                ),
            ColosseumMonster.Shaman to
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = q.shaman_attack,
                    castSpot = s.shaman_cast,
                    castHeight = 92,
                    projectile = s.shaman_travel,
                    projanim = p.shaman,
                    impactSpot = s.shaman_impact,
                    maxHit = 28,
                ),
            ColosseumMonster.Jaguar to
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = q.jaguar_attack,
                    maxHit = 47,
                    meleeType = MeleeAttackType.Slash,
                    extraHits = 2,
                ),
            ColosseumMonster.Javelin to
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = q.javelin_attack,
                    projectile = s.javelin_travel,
                    projanim = p.javelin,
                    maxHit = 48,
                ),
            ColosseumMonster.Shockwave to
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = q.shockwave_attack,
                    projectile = s.shockwave_travel,
                    projanim = p.shockwave,
                    maxHit = 56,
                ),
            ColosseumMonster.Minotaur to
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = q.minotaur_attack,
                    maxHit = 74,
                    meleeType = MeleeAttackType.Crush,
                    hitDelay = 2,
                ),
        )
    }

    companion object {
        /** Every monster waits 3 ticks after it appears; the trio then swing a tick apart. */
        const val SPAWN_DELAY = 3
        val TRIO_STAGGER =
            mapOf(
                ColosseumMonster.Berserker to 0,
                ColosseumMonster.Seer to 1,
                ColosseumMonster.Archer to 2,
            )

        const val WARBAND_SPEED = 6
        val SPEEDS =
            mapOf(
                ColosseumMonster.Berserker to 6,
                ColosseumMonster.Seer to 6,
                ColosseumMonster.Archer to 6,
                ColosseumMonster.Shaman to 5,
                ColosseumMonster.Jaguar to 5,
                ColosseumMonster.Javelin to 5,
                ColosseumMonster.Manticore to 10,
                ColosseumMonster.Shockwave to 5,
                ColosseumMonster.Minotaur to 5,
                ColosseumMonster.Sol to 7,
            )

        /** The monsters that fight next to their target (the rest shoot from their range). */
        val MELEE = setOf(ColosseumMonster.Jaguar, ColosseumMonster.Minotaur)

        /** The warband's sides: berserker north, seer east, archer west, an extra one south. */
        val SIDES =
            mapOf(
                ColosseumMonster.Berserker to (0 to 1),
                ColosseumMonster.Seer to (1 to 0),
                ColosseumMonster.Archer to (-1 to 0),
            )
        val EXTRA_SIDE = 0 to -1
        val CARDINALS = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

        const val ARTILLERY_EVERY = 5

        /** tsps (from captures): the javelin lands 3 ticks after the throw. The wiki says 6. */
        const val ARTILLERY_LANDS = 3
        const val ARTILLERY_MAX = 40
        const val ARTILLERY_FIRE_DELAY = 60

        /** The Manticore throws its first orb on the sixth tick of its charge. */
        const val MANTICORE_THROW = 6

        const val MINOTAUR_HEAL_EVERY = 5
        const val MINOTAUR_HEAL_RANGE = 6
    }
}
