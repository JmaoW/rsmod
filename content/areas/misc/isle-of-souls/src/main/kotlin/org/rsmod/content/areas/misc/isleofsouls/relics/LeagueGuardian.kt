package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varps
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcFollowWalker
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GuardianNpcs : NpcReferences() {
    /**
     * The guardian's outfits, in "Change-outfit" order. The cache's own `league_guardian` has no
     * model (the real game dresses it from the server), so these armoured humans stand in for it.
     */
    val outfits =
        listOf(
            find("kourend_royal_guard_female"),
            find("kourend_royal_guard_male"),
            find("white_knight"),
        )
}

object GuardianSeqs : SeqReferences() {
    val summon = find("trailblazer_summon_guardian_player_01")
    val summon_female = find("trailblazer_summon_guardian_player_01_female")
    val spawn = find("trailblazer_spawn_guardian_01")
    val cast = find("league05_guardian_attack_magic_01")
    val punch = find("human_unarmedpunch")
}

object GuardianSpotanims : SpotanimReferences() {
    val summon = find("league5_summon_guardian_player_01_spotanim_01")
    val cast = find("league5_guardian_01_attack_magic_01_spotanim_01")
    val magic_travel = find("league5_guardian_01_attack_magic_01_projanim_01")
    val ranged_travel = find("league5_guardian_01_attack_ranged_01_projanim_01")
    val magic_impact = find("trailblazer_guardian_01_attack_magic_01_impactanim_01")
    val ranged_impact = find("trailblazer_guardian_01_attack_ranged_01_impactanim_01")
}

/**
 * The Guardian relic (tier 7 in the code, "tier 8" in the plan and on the wiki). Its guardian horn
 * summons a guardian that follows the player for [DURATION_TICKS] ticks (30 minutes) and, every
 * [ATTACK_INTERVAL] ticks, attacks the npc the player is fighting:
 * - with the style the npc has the lowest defence bonus against (melee, ranged or magic);
 * - with an accuracy roll of [ACCURACY], hitting [MIN_HIT]-[MAX_HIT] (a miss is a 0);
 * - with ranged or magic in a multi-combat area (the whole isle is one), also hitting the other
 *   npcs next to the target, unless "Toggle-modes" has switched that off.
 *
 * The hits are the player's, so the kills and drops are theirs. It never attacks players, works
 * alongside an Arceuus thrall, and leaves when the player dies or logs out.
 *
 * It appears beside its owner and walks behind them ([NpcFollowWalker]), looking at its owner, or
 * at the npc they are fighting while that is within [RANGE].
 *
 * With melee it walks up to the target and strikes from beside it; with ranged or magic it attacks
 * from where it stands.
 *
 * Guesses: the area attack reaches the npcs within one tile of the target; ranged and magic reach
 * [RANGE] tiles, like the Arceuus thralls; "Toggle-modes" only switches the area attack (the
 * auto-summon mode is not built) and the choice is not saved over logout.
 */
class LeagueGuardian
@Inject
constructor(
    private val tools: LeagueRelicTools,
    private val npcRegistry: NpcRegistry,
    private val npcList: NpcList,
    private val npcTypes: NpcTypeList,
    private val worldQueues: WorldQueueList,
    private val worldRepo: WorldRepository,
    private val modifier: NpcHitModifier,
    private val walker: NpcFollowWalker,
    private val random: GameRandom,
) : PluginScript() {
    private class Guardian(val npc: Npc, val expires: Int) {
        var nextAttack: Int = 0
        val trail = NpcFollowWalker.Trail()
    }

    private val guardians = WeakHashMap<Player, Guardian>()
    private val outfits = WeakHashMap<Player, Int>()
    private val areaOff = WeakHashMap<Player, Boolean>()

    private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp(varps.aggressive_npc)

    /** The guardian [player] has summoned, if it is still out. */
    fun guardianOf(player: Player): Npc? = guardians[player]?.npc

    override fun ScriptContext.startup() {
        onOpHeld1(isle_objs.guardian_horn) { summon() }
        onOpHeld2(isle_objs.guardian_horn) { dismissByHorn() }
        onOpHeld3(isle_objs.guardian_horn) { toggleArea() }
        onOpHeld4(isle_objs.guardian_horn) { changeOutfit() }
        onPlayerLogout { dismiss(player) }
        onEvent<PlayerDeathEvents.Died> { dismiss(player) }
        // In the line behind the owner the guardian walks last, after a pet and a thrall.
        walker.register(NpcFollowWalker.Rank.Guardian) { lateCycle() }
        onEvent<GameLifecycle.LateCycle> { walker.lateCycle() }
    }

    private fun ProtectedAccess.summon() {
        if (!usable()) {
            return
        }
        dismiss(player)
        if (!spawn(player)) {
            mes("There is no room for your guardian here.")
            return
        }
        anim(if (isBodyTypeB()) GuardianSeqs.summon_female else GuardianSeqs.summon)
        spotanim(GuardianSpotanims.summon)
        mes("You blow the horn and your guardian answers.")
    }

    /** Spawns [player]'s guardian beside them. @return whether there was room for it. */
    private fun spawn(player: Player, expires: Int? = null): Boolean {
        val outfit = GuardianNpcs.outfits[outfits[player] ?: 0]
        val type = npcTypes[outfit]
        val npc = Npc(type, walker.tileBeside(player, type.size))
        walker.claim(npc)
        npc.facePlayer(player)
        if (!npcRegistry.add(npc).isSuccess()) {
            return false
        }
        npc.hideAllOps()
        npc.anim(GuardianSeqs.spawn)
        val clock = player.currentMapClock
        val guardian = Guardian(npc, expires ?: (clock + DURATION_TICKS))
        guardians[player] = guardian
        worldQueues.add(1) { tick(player, guardian) }
        return true
    }

    private fun ProtectedAccess.dismissByHorn() {
        if (guardians[player] == null) {
            mes("You have no guardian to dismiss.")
            return
        }
        dismiss(player)
        mes("Your guardian departs.")
    }

    private fun ProtectedAccess.toggleArea() {
        if (!usable()) {
            return
        }
        val off = areaOff[player] != true
        areaOff[player] = off
        val state = if (off) "will no longer" else "will now"
        mes("Your guardian ${LeagueChat.blue(state)} use area attacks in multi-combat.")
    }

    private fun ProtectedAccess.changeOutfit() {
        if (!usable()) {
            return
        }
        val next = ((outfits[player] ?: 0) + 1) % GuardianNpcs.outfits.size
        outfits[player] = next
        // Only the look changes: the same npc is transmogged, keeping its tile, walk and facing.
        val npc = guardians[player]?.npc
        if (npc != null) {
            val type = npcTypes[GuardianNpcs.outfits[next]]
            if (npc.type.isType(type)) {
                npc.resetTransmog()
            } else {
                npc.transmog(type, Int.MAX_VALUE)
            }
        }
        mes("Your guardian takes on a new look.")
    }

    private fun tick(owner: Player, guardian: Guardian) {
        if (guardians[owner] !== guardian) {
            return
        }
        val clock = owner.currentMapClock
        val gone =
            owner.slotId == PathingEntity.INVALID_SLOT ||
                guardian.npc.slotId == PathingEntity.INVALID_SLOT
        if (gone || clock >= guardian.expires) {
            dismiss(owner)
            if (!gone) {
                owner.mes("Your guardian's watch is over.")
            }
            return
        }
        if (clock >= guardian.nextAttack && attack(owner, guardian.npc)) {
            guardian.nextAttack = clock + ATTACK_INTERVAL
        }
        worldQueues.add(1) { tick(owner, guardian) }
    }

    /**
     * Once a tick, after players have moved: every guardian walks behind its owner (jumping back
     * after a teleport or a ladder), looking at what they are fighting while it is within reach,
     * and at its owner otherwise.
     */
    private fun lateCycle() {
        if (guardians.isEmpty()) {
            return
        }
        for ((owner, guardian) in guardians.entries.toList()) {
            val npc = guardian.npc
            if (
                owner.slotId == PathingEntity.INVALID_SLOT ||
                    npc.slotId == PathingEntity.INVALID_SLOT
            ) {
                continue
            }
            val target = target(owner, npc)
            val melee = target != null && weakestStyle(target) == HitType.Melee
            if (
                melee &&
                    npc.coords.level == owner.coords.level &&
                    npc.isWithinDistance(owner, LEASH)
            ) {
                walker.approach(npc, checkNotNull(target), guardian.trail)
            } else {
                walker.walk(npc, owner, guardian.trail, LEASH)
            }
            if (target != null) {
                npc.faceNpc(target)
            } else {
                npc.facePlayer(owner)
            }
        }
    }

    /** The npc [owner] is fighting, while it can be attacked and is within the guardian's reach. */
    private fun target(owner: Player, npc: Npc): Npc? {
        val uid = owner.aggressiveNpc ?: return null
        val target = npcList[uid.slotId] ?: return null
        if (target.uid != uid || !target.isValidTarget() || target.hitpoints <= 0) {
            return null
        }
        if (!npc.isWithinDistance(target, RANGE)) {
            return null
        }
        return target
    }

    /** Attacks what [owner] is fighting, if that is near. @return whether it attacked. */
    private fun attack(owner: Player, npc: Npc): Boolean {
        val target = target(owner, npc) ?: return false
        val style = weakestStyle(target)
        if (style == HitType.Melee && !walker.inMeleeReach(npc, target)) {
            // Melee strikes only from beside the target; it is still walking up.
            return false
        }
        npc.faceNpc(target)
        when (style) {
            HitType.Melee -> {
                npc.anim(target.visType.paramOrNull(params.attack_anim) ?: GuardianSeqs.punch)
                hit(owner, target, style, delay = 1)
            }
            else -> {
                npc.anim(GuardianSeqs.cast)
                npc.spotanim(GuardianSpotanims.cast)
                cast(owner, npc, target, style)
                if (areaOff[owner] != true) {
                    for (other in neighbours(target)) {
                        cast(owner, npc, other, style)
                    }
                }
            }
        }
        return true
    }

    private fun cast(owner: Player, npc: Npc, target: Npc, style: HitType) {
        val magic = style == HitType.Magic
        val travel = if (magic) GuardianSpotanims.magic_travel else GuardianSpotanims.ranged_travel
        val proj =
            worldRepo.projAnim(
                npc,
                target,
                travel,
                if (magic) projanims.magic_spell else projanims.arrow,
            )
        val impact = if (magic) GuardianSpotanims.magic_impact else GuardianSpotanims.ranged_impact
        target.spotanim(impact, delay = proj.clientCycles)
        hit(owner, target, style, delay = proj.serverCycles)
    }

    private fun hit(owner: Player, target: Npc, style: HitType, delay: Int) {
        val damage = if (rollHit(target, style)) random.of(MIN_HIT, MAX_HIT) else 0
        target.queueHit(owner, delay = delay, type = style, damage = damage, modifier = modifier)
    }

    /** The attackable npcs within one tile of [target], for the area attack. */
    private fun neighbours(target: Npc): List<Npc> {
        val found = ArrayList<Npc>()
        val centre = target.coords
        for (dx in -AREA_RADIUS..AREA_RADIUS) {
            for (dz in -AREA_RADIUS..AREA_RADIUS) {
                val tile = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                for (other in npcRegistry.findAll(tile)) {
                    if (other !== target && other !in found && isAttackable(other)) {
                        found += other
                    }
                }
            }
        }
        return found
    }

    private fun isAttackable(npc: Npc): Boolean =
        npc.isValidTarget() &&
            npc.hitpoints > 0 &&
            npc.visType.op.any { it.equals("Attack", ignoreCase = true) }

    /** Whether the [ACCURACY] roll beats [target]'s defence roll against [style]. */
    private fun rollHit(target: Npc, style: HitType): Boolean {
        val type = target.visType
        val (level, bonus) =
            when (style) {
                HitType.Magic -> target.magicLvl to type.param(params.defence_magic)
                HitType.Ranged -> target.defenceLvl to type.param(params.defence_ranged)
                else -> target.defenceLvl to meleeDefence(target)
            }
        val defence = (level + 9) * (bonus + 64)
        val chance =
            if (ACCURACY > defence) {
                1.0 - (defence + 2.0) / (2.0 * (ACCURACY + 1))
            } else {
                ACCURACY / (2.0 * (defence + 1))
            }
        return random.randomDouble() < chance
    }

    /** The style [target] is weakest against: its lowest defence bonus, melee first on a tie. */
    fun weakestStyle(target: Npc): HitType {
        val type = target.visType
        val melee = meleeDefence(target)
        val ranged = type.param(params.defence_ranged)
        val magic = type.param(params.defence_magic)
        return when {
            melee <= ranged && melee <= magic -> HitType.Melee
            ranged <= magic -> HitType.Ranged
            else -> HitType.Magic
        }
    }

    private fun meleeDefence(target: Npc): Int {
        val type = target.visType
        return minOf(
            type.param(params.defence_stab),
            type.param(params.defence_slash),
            type.param(params.defence_crush),
        )
    }

    private fun dismiss(player: Player) {
        val guardian = guardians.remove(player) ?: return
        removeNpc(guardian)
    }

    private fun removeNpc(guardian: Guardian) {
        if (guardian.npc.slotId != PathingEntity.INVALID_SLOT) {
            npcRegistry.del(guardian.npc)
        }
    }

    private fun ProtectedAccess.usable(): Boolean {
        if (tools.isChosen(player, RelicItem.Guardian)) {
            return true
        }
        mes("Only someone with the Guardian relic can blow the horn.")
        return false
    }

    companion object {
        const val DURATION_TICKS = 3000
        const val ATTACK_INTERVAL = 4
        const val ACCURACY = 45_000
        const val MIN_HIT = 5
        const val MAX_HIT = 13
        const val RANGE = 10
        const val AREA_RADIUS = 1
        const val LEASH = 15
    }
}
