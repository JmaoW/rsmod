package org.rsmod.content.generic.npcs.abyssal

import jakarta.inject.Inject
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

// When a player lands a hit on an abyssal demon it has a 1/6 chance to teleport next to the player,
// and if that roll fails a 1/3 chance to teleport the player next to it (wiki). The wiki gives no
// destination rule, so both land on a random free tile of the 3x3 square round the other, never
// the middle tile. A tile that is blocked, or that a wall separates from the middle, is skipped;
// if none is free nothing happens.
class AbyssalDemon
@Inject
constructor(
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val playerList: PlayerList,
) : PluginScript() {
    private val steps = StepValidator(collision)

    override fun ScriptContext.startup() {
        for (type in DEMONS) {
            onNpcHit(type) { onHit(this) }
        }
    }

    private fun onHit(hit: NpcHitEvents.Impact) {
        val demon = hit.npc
        if (hit.hit.damage <= 0 || demon.hitpoints <= 0 || !hit.hit.isFromPlayer) {
            return
        }
        val player = hit.hit.resolvePlayerSource(playerList) ?: return
        if (demon.level != player.level) {
            return
        }
        if (random.randomBoolean(SELF_ODDS)) {
            teleportDemon(demon, player)
        } else if (random.randomBoolean(PLAYER_ODDS)) {
            teleportPlayer(demon, player)
        }
    }

    private fun teleportDemon(demon: Npc, player: Player) {
        val dest = freeTileAround(player.coords) ?: return
        // A teleport drops the npc's target. Without it the demon would stop attacking.
        val interaction = demon.interaction
        PathingEntityCommon.telejump(demon, collision, dest)
        demon.interaction = interaction
        demon.anim(abyssal_seqs.teleport)
        demon.spotanim(abyssal_spotanims.teleport)
    }

    private fun teleportPlayer(demon: Npc, player: Player) {
        val dest = freeTileAround(demon.coords) ?: return
        PathingEntityCommon.telejump(player, collision, dest)
        demon.anim(abyssal_seqs.teleport)
        player.spotanim(abyssal_spotanims.teleport)
    }

    /** A random tile of the 3x3 square round [center] that something can step onto from it. */
    private fun freeTileAround(center: CoordGrid): CoordGrid? {
        val free = ArrayList<CoordGrid>(OFFSETS.size)
        for ((dx, dz) in OFFSETS) {
            val canStep =
                steps.canTravel(center.level, center.x, center.z, dx, dz, extraFlag = BLOCKED)
            if (canStep) {
                free += center.translate(dx, dz)
            }
        }
        return random.pickOrNull(free)
    }

    private companion object {
        const val SELF_ODDS = 6
        const val PLAYER_ODDS = 3
        const val BLOCKED =
            CollisionFlag.LOC or CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS

        val DEMONS =
            listOf(
                abyssal_npcs.slayer_abyssal,
                abyssal_npcs.slayer_abyssal_strongholdcave,
                abyssal_npcs.arceuus_reanimated_abyssal,
                abyssal_npcs.kourend_abyssal,
                abyssal_npcs.superior_abyssal_demon,
                abyssal_npcs.wild_cave_abyssal,
                abyssal_npcs.deadman_breach_superior_abyssal_demon,
                abyssal_npcs.league_superior_abyssal_demon,
            )

        /** Every tile of the 3x3 square but the middle. */
        val OFFSETS =
            (-1..1).flatMap { dx -> (-1..1).map { dz -> dx to dz } }.filter { it != 0 to 0 }
    }
}
