package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.combat.PvnRules
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.BlockWalk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Jaws of Gluttony: Zebak's room, his jugs, the rock steps out of the water. */
class ZebakScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val npcRepo: NpcRepository,
    private val pvnRules: PvnRules,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.CrondisBoss) { env, raid -> ZebakRuntime(env, raid) }
        // Queues go by the form he shows, so the enraged and dead forms need the same handling.
        toaBosses(rooms, listOf(zebak_npcs.zebak, zebak_npcs.enraged, zebak_npcs.dead))
        toaMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(zebak_npcs.blood_cloud to null, zebak_npcs.blood_cloud_small to null),
        )

        // A jug never fights back; a blow (any weapon, from any range) shatters it.
        toaBosses(rooms, listOf(zebak_npcs.jug, zebak_npcs.jug_rolling))
        pvnRules.allowAttack(zebak_npcs.jug.id)
        for (jug in listOf(zebak_npcs.jug, zebak_npcs.jug_rolling)) {
            // Push and pull work from any of the 8 tiles around the jug, so the diagonals start
            // them from the approach range before the route reaches a side.
            onApNpc1(jug) {
                if (isWithinApRange(it.npc, distance = 1)) {
                    room()?.moveJug(player, it.npc, push = true)
                }
            }
            onOpNpc1(jug) { room()?.moveJug(player, it.npc, push = true) }
            onApNpc3(jug) {
                if (isWithinApRange(it.npc, distance = 1)) {
                    room()?.moveJug(player, it.npc, push = false)
                }
            }
            onOpNpc3(jug) { room()?.moveJug(player, it.npc, push = false) }
            // "Hit" is an attack with the player's weapon: combat walks into melee range or
            // shoots from where they stand.
            onApNpc4(jug) { hitJug(it.npc) }
            onOpNpc4(jug) { hitJug(it.npc) }
        }
        onOpLoc1(zebak_locs.climbing_rock) {
            val room = room() ?: return@onOpLoc1
            room.raid.realOf(it.loc.coords)?.let { rock -> room.climbOut(player, rock) }
        }
    }

    /** "Hit" hands over to combat; the landed blow shatters the jug ([ZebakRuntime.modifyHit]). */
    private fun ProtectedAccess.hitJug(jug: Npc) {
        if (room() != null) {
            opNpc2(jug)
        }
    }

    private fun ProtectedAccess.room(): ZebakRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        if (raid.member(player)?.room != ToaRoom.CrondisBoss) {
            return null
        }
        return rooms.runtime(raid, ToaRoom.CrondisBoss) as? ZebakRuntime
    }
}

/**
 * The tidal waves never block anything (they already move through everything): without this they
 * hold up rolling jugs, blood clouds and crocodiles on the tiles they sweep. Needs `packCache`.
 */
internal object ZebakEdits : NpcEditor() {
    init {
        for (type in listOf(zebak_npcs.wave, zebak_npcs.wave_bloody)) {
            edit(type) { blockWalk = BlockWalk.None }
        }
    }
}
