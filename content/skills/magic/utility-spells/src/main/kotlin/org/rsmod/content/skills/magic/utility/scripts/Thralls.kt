package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcFollowWalker
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.magic.utility.configs.arceuus_npcs
import org.rsmod.content.skills.magic.utility.configs.arceuus_seqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Book of the Dead, which the real spells want in the inventory or worn
// - Master Combat Achievement rewards doubling how long a thrall stays
//
// Resurrect Lesser, Superior and Greater Ghost, Skeleton and Zombie summon a thrall that follows
// the player and hits the monster the player is fighting every 4 ticks, always landing, for up to
// 1,
// 2 or 3 (a magic hit for a ghost, ranged for a skeleton, melee for a zombie). It stays for one
// tick
// for each Magic level, and there is one at a time: a new one replaces the old. The hits are the
// player's, so the kill and the drops are theirs. Summoning costs 2, 4 or 6 Prayer points, and the
// spell can be cast again after 16 ticks.
//
// A thrall appears beside its owner and walks behind them (NpcFollowWalker). It looks at its
// owner, or at the monster they are fighting while that is within 10 tiles of it. A ghost or a
// skeleton hits from where it stands; a zombie walks up to the monster and strikes from beside it.
class Thralls
@Inject
constructor(
    private val caster: SpellCaster,
    private val npcRegistry: NpcRegistry,
    private val npcList: NpcList,
    private val npcTypes: NpcTypeList,
    private val worldQueues: WorldQueueList,
    private val walker: NpcFollowWalker,
    private val modifier: NpcHitModifier,
    private val random: GameRandom,
) : PluginScript() {
    /** A kind of thrall: which spell summons it, what it hits with, and what that takes. */
    private class Def(
        val spell: ObjType,
        val npc: NpcType,
        val style: HitType,
        val maxHit: Int,
        val prayerCost: Int,
        val name: String,
    ) {
        /** A ghost casts, a skeleton shoots and a zombie strikes, each with its own animation. */
        val attackAnim: SeqType
            get() =
                when (style) {
                    HitType.Magic -> arceuus_seqs.thrall_ghost_attack
                    HitType.Ranged -> arceuus_seqs.thrall_skeleton_attack
                    else -> arceuus_seqs.thrall_zombie_attack
                }

        /** The zombie has no animation for appearing. */
        val spawnAnim: SeqType?
            get() =
                when (style) {
                    HitType.Magic -> arceuus_seqs.thrall_ghost_spawn
                    HitType.Ranged -> arceuus_seqs.thrall_skeleton_spawn
                    else -> null
                }
    }

    /** A summoned thrall, until [expires]. */
    private class Thrall(val npc: Npc, val def: Def, val expires: Int) {
        var nextAttack: Int = 0
        val trail = NpcFollowWalker.Trail()
    }

    private val defs: List<Def> =
        listOf(
            Def(
                objs.spell_resurrect_lesser_ghost,
                arceuus_npcs.ghost_lesser,
                HitType.Magic,
                1,
                2,
                "lesser ghost",
            ),
            Def(
                objs.spell_resurrect_lesser_skeleton,
                arceuus_npcs.skeleton_lesser,
                HitType.Ranged,
                1,
                2,
                "lesser skeleton",
            ),
            Def(
                objs.spell_resurrect_lesser_zombie,
                arceuus_npcs.zombie_lesser,
                HitType.Melee,
                1,
                2,
                "lesser zombie",
            ),
            Def(
                objs.spell_resurrect_superior_ghost,
                arceuus_npcs.ghost_superior,
                HitType.Magic,
                2,
                4,
                "superior ghost",
            ),
            Def(
                objs.spell_resurrect_superior_skeleton,
                arceuus_npcs.skeleton_superior,
                HitType.Ranged,
                2,
                4,
                "superior skeleton",
            ),
            Def(
                objs.spell_resurrect_superior_zombie,
                arceuus_npcs.zombie_superior,
                HitType.Melee,
                2,
                4,
                "superior zombie",
            ),
            Def(
                objs.spell_resurrect_greater_ghost,
                arceuus_npcs.ghost_greater,
                HitType.Magic,
                3,
                6,
                "greater ghost",
            ),
            Def(
                objs.spell_resurrect_greater_skeleton,
                arceuus_npcs.skeleton_greater,
                HitType.Ranged,
                3,
                6,
                "greater skeleton",
            ),
            Def(
                objs.spell_resurrect_greater_zombie,
                arceuus_npcs.zombie_greater,
                HitType.Melee,
                3,
                6,
                "greater zombie",
            ),
        )

    private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp(varps.aggressive_npc)

    private val thralls = WeakHashMap<Player, Thrall>()

    /** The npc [player] has summoned and still has, if any. */
    fun thrallOf(player: Player): Npc? = thralls[player]?.npc

    override fun ScriptContext.startup() {
        with(caster) {
            for (def in defs) {
                onSpell(def.spell) { summon(def) }
            }
        }
        onPlayerLogout { dismiss(player) }
        // In the line behind the owner a thrall walks after a pet and ahead of a guardian.
        walker.register(NpcFollowWalker.Rank.Thrall) { lateCycle() }
        onEvent<GameLifecycle.LateCycle> { walker.lateCycle() }
    }

    private fun ProtectedAccess.summon(def: Def) {
        if (player.prayerLvl < def.prayerCost) {
            mes("You need at least ${def.prayerCost} Prayer points to summon that thrall.")
            return
        }
        with(caster) { cast(def.spell, COOLDOWN) } ?: return
        statSub(stats.prayer, constant = def.prayerCost, percent = 0)
        dismiss(player)

        val type = npcTypes[def.npc]
        val npc = Npc(type, walker.tileBeside(player, type.size))
        walker.claim(npc)
        npc.facePlayer(player)
        def.spawnAnim?.let { npc.anim(it) }
        if (!npcRegistry.add(npc).isSuccess()) {
            mes("There is no room for a thrall here.")
            return
        }
        val thrall = Thrall(npc, def, expires = mapClock + player.magicLvl)
        thralls[player] = thrall
        anim(arceuus_seqs.resurrect)
        mes("You summon a ${def.name} thrall.")
        worldQueues.add(1) { tick(player, thrall) }
        publish(Summoned(player, def.spell))
    }

    private fun tick(owner: Player, thrall: Thrall) {
        if (thralls[owner] !== thrall) {
            return
        }
        val clock = owner.currentMapClock
        val gone =
            owner.slotId == PathingEntity.INVALID_SLOT ||
                thrall.npc.slotId == PathingEntity.INVALID_SLOT
        if (gone || clock >= thrall.expires) {
            dismiss(owner, expired = clock >= thrall.expires)
            return
        }
        if (clock >= thrall.nextAttack && attack(owner, thrall)) {
            thrall.nextAttack = clock + ATTACK_INTERVAL
        }
        worldQueues.add(1) { tick(owner, thrall) }
    }

    /**
     * Once a tick, after players have moved: every thrall walks behind its owner, looking at what
     * they are fighting while it is within reach, and at its owner otherwise.
     */
    private fun lateCycle() {
        if (thralls.isEmpty()) {
            return
        }
        for ((owner, thrall) in thralls.entries.toList()) {
            val npc = thrall.npc
            if (
                owner.slotId == PathingEntity.INVALID_SLOT ||
                    npc.slotId == PathingEntity.INVALID_SLOT
            ) {
                continue
            }
            val target = target(owner, thrall)
            if (target != null && thrall.def.style == HitType.Melee && npc.nearOwner(owner)) {
                walker.approach(npc, target, thrall.trail)
            } else {
                walker.walk(npc, owner, thrall.trail, LEASH)
            }
            if (target != null) {
                npc.faceNpc(target)
            } else {
                npc.facePlayer(owner)
            }
        }
    }

    /** Whether a zombie may go on walking up to its target: still within the leash of [owner]. */
    private fun Npc.nearOwner(owner: Player): Boolean =
        coords.level == owner.coords.level && isWithinDistance(owner, LEASH)

    /** The npc [owner] is fighting, while it is alive and within the thrall's reach. */
    private fun target(owner: Player, thrall: Thrall): Npc? {
        val uid = owner.aggressiveNpc ?: return null
        val target = npcList[uid.slotId] ?: return null
        if (target.uid != uid || target.hitpoints <= 0 || target === thrall.npc) {
            return null
        }
        if (!thrall.npc.isWithinDistance(target, RANGE)) {
            return null
        }
        return target
    }

    /** Hits what the owner is fighting, if that is near. @return whether it hit. */
    private fun attack(owner: Player, thrall: Thrall): Boolean {
        val target = target(owner, thrall) ?: return false
        if (thrall.def.style == HitType.Melee && !walker.inMeleeReach(thrall.npc, target)) {
            // A zombie strikes only from beside its target; it is still walking up.
            return false
        }
        val damage = random.of(0, thrall.def.maxHit)
        thrall.npc.faceNpc(target)
        thrall.npc.anim(thrall.def.attackAnim)
        target.queueHit(
            owner,
            delay = HIT_DELAY,
            type = thrall.def.style,
            damage = damage,
            modifier = modifier,
        )
        return true
    }

    private fun dismiss(player: Player, expired: Boolean = false) {
        val thrall = thralls.remove(player) ?: return
        if (thrall.npc.slotId != PathingEntity.INVALID_SLOT) {
            npcRegistry.del(thrall.npc)
        }
        if (expired) {
            player.mes("Your thrall returns to the grave.")
        }
    }

    /** Published once [player] has summoned a thrall with [spell]. */
    class Summoned(val player: Player, val spell: ObjType) : UnboundEvent

    private companion object {
        const val COOLDOWN = 16
        const val ATTACK_INTERVAL = 4
        const val RANGE = 10
        const val HIT_DELAY = 2

        /** A thrall further away than this jumps beside its owner. */
        const val LEASH = 15
    }
}
