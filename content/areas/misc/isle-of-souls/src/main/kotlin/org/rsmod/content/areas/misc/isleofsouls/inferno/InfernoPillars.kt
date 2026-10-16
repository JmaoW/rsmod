package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit as queueNpcHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * One of the three pillars of an Inferno run. It is [SIZE] by [SIZE] tiles with [southWest] (a real
 * coordinate inside the run's instance) as its south-west corner, and holds [hitpoints] until the
 * nibblers have eaten it. [index] says which of the three it is (it picks the loc and the varbit).
 */
class InfernoPillar(val southWest: CoordGrid, val index: Int) {
    var hitpoints: Int = HITPOINTS

    /** The 3x3 loc standing on the pillar, which is what the players see. */
    var loc: LocInfo? = null

    /** The invisible "Rocky support" npc that carries the pillar's health bar. */
    var target: Npc? = null

    /** The rubble that plays the collapse once the pillar has fallen. */
    var rubble: Npc? = null

    val alive: Boolean
        get() = hitpoints > 0

    /** What the pillar's varbit holds: `0` for a whole pillar up to `255` for a ruined one. */
    val damageTaken: Int
        get() = HITPOINTS - hitpoints

    val centre: CoordGrid
        get() = CoordGrid(southWest.x + SIZE / 2, southWest.z + SIZE / 2, southWest.level)

    /** Whether [coords] is on the pillar itself. */
    fun covers(coords: CoordGrid): Boolean =
        coords.level == southWest.level &&
            coords.x in southWest.x until southWest.x + SIZE &&
            coords.z in southWest.z until southWest.z + SIZE

    /** The tiles in a ring one tile wide around the pillar, diagonals included. */
    fun isNextTo(coords: CoordGrid): Boolean =
        coords.level == southWest.level &&
            coords.x in southWest.x - 1..southWest.x + SIZE &&
            coords.z in southWest.z - 1..southWest.z + SIZE &&
            !covers(coords)

    companion object {
        /** The wiki: each pillar has 255 hitpoints. */
        const val HITPOINTS = 255

        const val SIZE = 3

        /**
         * Which of the pillar loc's four rocks shows at [hitpoints]: the first while more than
         * three quarters are left, then one for more than half, one for more than a quarter, and
         * the last one after. (The loc picks it by itself from the varbit, 64 damage to each rock.)
         */
        fun stageFor(hitpoints: Int): Int =
            when {
                hitpoints * 4 > HITPOINTS * 3 -> 0
                hitpoints * 2 > HITPOINTS -> 1
                hitpoints * 4 > HITPOINTS -> 2
                else -> 3
            }
    }
}

/**
 * Builds, wears down and collapses the pillars the way the real Inferno does: a pillar is a 3x3 loc
 * with no model of its own (it shows one of four rocks from a varbit that each player in the run
 * gets), whose collision blocks walking and line of sight on all nine tiles, plus an invisible 3x3
 * "Rocky support" npc standing on it. The npc is what the nibblers bite, so that the hit splats and
 * the health bar show on the pillar. The npc attack code already needs line of sight, so monsters
 * behind a pillar simply cannot attack.
 */
class InfernoPillars
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val seqTypes: SeqTypeList,
    private val instances: TeamInstances,
) {
    /** Puts the pillars into [instance] and returns them. */
    fun place(instance: TeamInstance): List<InfernoPillar> =
        InfernoArena.pillarSouthWest.mapIndexed { index, site ->
            val pillar = InfernoPillar(instance.region.normal[site], index)
            pillar.loc =
                locRepo.add(
                    pillar.southWest,
                    locTypes[InfernoPillarLocs.pillars[index]],
                    duration = Int.MAX_VALUE,
                    angle = LocAngle.West,
                    shape = LocShape.CentrepieceStraight,
                )
            pillar.target = spawnTarget(pillar)
            pillar
        }

    /** Tells [player] how worn out each pillar is, so they see the right rocks. */
    fun showTo(player: Player, run: InfernoRun) {
        for (pillar in run.pillars) {
            val varbit = InfernoVarBits.pillar_damage[pillar.index]
            VarPlayerIntMapSetter.set(player, varbit, pillar.damageTaken)
        }
    }

    /**
     * The nibbler [source] (or nothing in particular) hits [pillar] for [damage]; it changes look
     * as it weakens and then collapses.
     */
    fun damage(run: InfernoRun, pillar: InfernoPillar, damage: Int, source: Npc? = null) {
        if (!pillar.alive) {
            return
        }
        val before = pillar.hitpoints
        pillar.hitpoints = maxOf(0, before - damage)
        if (!pillar.alive) {
            collapse(run, pillar)
            return
        }
        showDamage(pillar, before, before - pillar.hitpoints, source)
        for (player in instances.playersIn(run.instance)) {
            showTo(player, run)
        }
    }

    /** Brings down every pillar still standing (when the first Jad wave starts). */
    fun collapseAll(run: InfernoRun) {
        for (pillar in run.pillars) {
            if (pillar.alive) {
                pillar.hitpoints = 0
                collapse(run, pillar)
            }
        }
    }

    /** Removes the pillars of a finished [run] without hurting anyone. */
    fun remove(run: InfernoRun) {
        for (pillar in run.pillars) {
            pillar.hitpoints = 0
            take(pillar)
            pillar.rubble?.let {
                if (it.isSlotAssigned) {
                    npcRepo.del(it, Int.MAX_VALUE)
                }
            }
            pillar.rubble = null
        }
    }

    /**
     * Shows a bite of [damage] on the pillar's health bar. The npc's hitpoints are set from the
     * pillar's own first, so the bar is always right however the npc has regenerated.
     */
    private fun showDamage(pillar: InfernoPillar, before: Int, damage: Int, source: Npc?) {
        val target = pillar.target?.takeIf { it.isSlotAssigned } ?: return
        target.hitpoints = before
        if (source != null && source.isSlotAssigned) {
            target.queueNpcHit(source, 1, HitType.Melee, damage, NpcHitModifier {})
        } else {
            target.queueNpcHit(1, HitType.Typeless, damage, NpcHitModifier {})
        }
    }

    private fun spawnTarget(pillar: InfernoPillar): Npc {
        val npc = Npc(npcTypes[inferno_npcs.pillar], pillar.southWest)
        npc.mode = NpcMode.None
        npc.baseHitpointsLvl = InfernoPillar.HITPOINTS
        npc.hitpoints = InfernoPillar.HITPOINTS
        npcRepo.add(npc, LIFETIME)
        return npc
    }

    private fun collapse(run: InfernoRun, pillar: InfernoPillar) {
        take(pillar)
        showRubble(pillar)
        for (player in instances.playersIn(run.instance)) {
            if (player.isValidTarget() && pillar.isNextTo(player.coords)) {
                player.queueHit(delay = 1, HitType.Typeless, COLLAPSE_DAMAGE)
            }
        }
        for (npc in run.monsters.toList()) {
            if (npc.isSlotAssigned && npc.hitpoints > 0 && touches(pillar, npc)) {
                npc.queueNpcHit(delay = 1, HitType.Typeless, COLLAPSE_DAMAGE, NpcHitModifier {})
            }
        }
    }

    /** The pillar's rubble, which plays the collapse once and is gone again. */
    private fun showRubble(pillar: InfernoPillar) {
        val npc = Npc(npcTypes[inferno_npcs.pillar_rubble], pillar.southWest)
        npc.mode = NpcMode.None
        npcRepo.add(npc, maxOf(1, seqTypes[inferno_seqs.pillar_collapse].tickDuration))
        npc.anim(inferno_seqs.pillar_collapse)
        pillar.rubble = npc
    }

    /** Whether any tile of [npc] is next to [pillar]. */
    private fun touches(pillar: InfernoPillar, npc: Npc): Boolean {
        val size = npc.size
        for (dz in 0 until size) {
            for (dx in 0 until size) {
                val tile = CoordGrid(npc.coords.x + dx, npc.coords.z + dz, npc.coords.level)
                if (pillar.isNextTo(tile)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Takes the pillar's loc (and with it its collision) and its health bar npc out of the arena.
     */
    private fun take(pillar: InfernoPillar) {
        pillar.loc?.let { locRepo.del(it, Int.MAX_VALUE) }
        pillar.loc = null
        pillar.target?.let {
            if (it.isSlotAssigned) {
                npcRepo.del(it, Int.MAX_VALUE)
            }
        }
        pillar.target = null
    }

    companion object {
        /**
         * What the pillar loc puts on each of its nine tiles: it blocks walking and projectiles.
         */
        const val MASK = CollisionFlag.LOC or CollisionFlag.LOC_PROJ_BLOCKER

        /**
         * A collapsing pillar hurts anyone next to it. The wiki says 49 in one place and "half
         * their health" in another; this uses 49 (a guess for monsters as well as players).
         */
        const val COLLAPSE_DAMAGE = 49

        /** The pillar's health bar npc never expires on its own: the run takes it away. */
        private const val LIFETIME = 100_000
    }
}
