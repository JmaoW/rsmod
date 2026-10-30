package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackDelays
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Path of Scabaras: the plates, tablet, button, matching tiles and passage, and the scarabs'
 * deaths. Stepping on plates is noticed by [ScabarasPuzzleRuntime] itself each tick.
 */
class ScabarasScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val npcRepo: NpcRepository,
    private val attackDelays: NpcAttackDelays,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.ScabarasPuzzle) { env, raid -> ScabarasPuzzleRuntime(env, raid) }

        onOpLoc5(scabaras_locs.light_plate) {
            val room = room() ?: return@onOpLoc5
            room.raid.realOf(it.loc.coords)?.let { tile -> room.flip(player, tile) }
        }
        onOpLoc1(scabaras_locs.sum_tablet) { room()?.readTablet(player) }
        onOpLoc1(scabaras_locs.memory_button) { room()?.pressButton(player) }
        // A matching tile turns from any of the 8 tiles around it, diagonals included.
        onApLoc1(scabaras_locs.match_tile) {
            if (isWithinApRange(it.loc, distance = 1)) {
                turnTile(it.loc.coords)
            }
        }
        onOpLoc1(scabaras_locs.match_tile) { turnTile(it.loc.coords) }
        onOpLoc1(scabaras_locs.passage) { room()?.crawl(player) }
        // The stone stands in the gap, so a click from the edge of either half starts the jump.
        onApLoc1(scabaras_locs.platform) {
            if (isWithinApRange(it.loc, PLATFORM_AP_RANGE)) {
                jumpPlatform()
            }
        }
        onOpLoc1(scabaras_locs.platform) { jumpPlatform() }

        toaMonsterDeaths(rooms, npcRepo, mapOf(scabaras_npcs.scarab to scabaras_seqs.scarab_death))
        // The obelisks never die (a hit only counts as touching one) and never fight back, and
        // touching one costs no attack delay: the next can be hit straight after.
        toaBosses(rooms, listOf(scabaras_npcs.obelisk, scabaras_npcs.obelisk_lit))
        val obeliskTypes = setOf(scabaras_npcs.obelisk.id, scabaras_npcs.obelisk_lit.id)
        attackDelays.register { _, target, _ -> if (target.type.id in obeliskTypes) 0 else null }
    }

    private fun ProtectedAccess.turnTile(coords: CoordGrid) {
        val room = room() ?: return
        room.raid.realOf(coords)?.let { tile -> room.turnTile(player, tile) }
    }

    /** Onto the stone, and two ticks later on to the far half. */
    private suspend fun ProtectedAccess.jumpPlatform() {
        val room = room() ?: return
        val at = room.raid.realOf(player.coords) ?: return
        val north = at.z < ScabarasPuzzleRuntime.MIDDLE_Z
        val far =
            if (north) ScabarasPuzzleRuntime.PLATFORM_NORTH
            else ScabarasPuzzleRuntime.PLATFORM_SOUTH
        val facing = if (north) FACE_NORTH else FACE_SOUTH
        room.jump(player, ScabarasPuzzleRuntime.PLATFORM, facing)
        delay(2)
        if (raids.raidOf(player) === room.raid) {
            room.jump(player, far, facing)
            delay(1)
        }
    }

    private fun ProtectedAccess.room(): ScabarasPuzzleRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        if (raid.member(player)?.room != ToaRoom.ScabarasPuzzle) {
            return null
        }
        return rooms.ensure(raid, ToaRoom.ScabarasPuzzle) as? ScabarasPuzzleRuntime
    }

    private companion object {
        /** How far from the stone a click starts the jump. */
        const val PLATFORM_AP_RANGE = 4

        /** Client angles: facing north and south. */
        const val FACE_NORTH = 1024
        const val FACE_SOUTH = 0
    }
}
