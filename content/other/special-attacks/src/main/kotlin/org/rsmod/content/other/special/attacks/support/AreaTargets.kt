package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.PvpRules
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * The other targets a special attack that hits more than one thing can reach. They are only ever
 * the same kind as the primary target (npcs around an npc, players around a player), and only in
 * multi-combat.
 */
@Singleton
class AreaTargets
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val playerRepo: PlayerRepository,
    private val pvpRules: PvpRules,
) {
    /**
     * The targets within [radius] tiles of [center], not counting [primary] or the attacker, up to
     * [limit] of them. Empty outside multi-combat.
     */
    fun ProtectedAccess.around(
        primary: PathingEntity,
        center: CoordGrid = primary.coords,
        radius: Int,
        limit: Int,
    ): List<PathingEntity> {
        if (!mapMultiway()) {
            return emptyList()
        }
        return when (primary) {
            is Npc -> npcsAround(primary, center, radius, limit)
            is Player -> playersAround(primary, center, radius, limit)
        }
    }

    /**
     * The other players within [radius] tiles of [player] that [player] cannot fight: the ones on
     * their side.
     */
    fun alliesAround(player: Player, radius: Int, limit: Int = 24): List<Player> {
        val allies = ArrayList<Player>()
        for (other in playerRepo.findAll(ZoneKey.from(player.coords), ZONE_RADIUS)) {
            if (other === player || other.hitpoints <= 0 || !other.isNear(player.coords, radius)) {
                continue
            }
            if (pvpRules.denial(player, other) != null) {
                allies += other
            }
            if (allies.size >= limit) {
                break
            }
        }
        return allies
    }

    private fun npcsAround(
        primary: Npc,
        center: CoordGrid,
        radius: Int,
        limit: Int,
    ): List<PathingEntity> {
        val found = ArrayList<PathingEntity>()
        for (npc in npcRepo.findAll(ZoneKey.from(center), ZONE_RADIUS)) {
            if (npc === primary || !npc.isAttackable() || !npc.isNear(center, radius)) {
                continue
            }
            found += npc
            if (found.size >= limit) {
                break
            }
        }
        return found
    }

    private fun ProtectedAccess.playersAround(
        primary: Player,
        center: CoordGrid,
        radius: Int,
        limit: Int,
    ): List<PathingEntity> {
        val found = ArrayList<PathingEntity>()
        for (other in playerRepo.findAll(ZoneKey.from(center), ZONE_RADIUS)) {
            val isOther = other !== primary && other !== player
            if (!isOther || other.hitpoints <= 0 || !other.isNear(center, radius)) {
                continue
            }
            if (pvpRules.denial(player, other) != null) {
                continue
            }
            found += other
            if (found.size >= limit) {
                break
            }
        }
        return found
    }

    private fun PathingEntity.isNear(center: CoordGrid, radius: Int): Boolean =
        coords.level == center.level &&
            kotlin.math.abs(coords.x - center.x) <= radius &&
            kotlin.math.abs(coords.z - center.z) <= radius

    private companion object {
        const val ZONE_RADIUS = 1

        fun Npc.isAttackable(): Boolean = isValidTarget() && visType.hasOp(InteractionOp.Op2)
    }
}
