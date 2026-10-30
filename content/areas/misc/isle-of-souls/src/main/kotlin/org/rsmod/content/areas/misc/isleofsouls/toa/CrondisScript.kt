package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onOpObj3
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Path of Crondis: the water containers, the waterfalls and the palm, and the crocodiles' deaths.
 * The traps and the crocodiles' minds are [CrondisPuzzleRuntime].
 */
class CrondisScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.CrondisPuzzle) { env, raid -> CrondisPuzzleRuntime(env, raid) }

        onOpObj3(crondis_objs.container) { room()?.takeContainer(player) }
        onOpHeld1(crondis_objs.container) {
            val room = room()
            mes("Your water container is at ${room?.waterOf(player) ?: 0}% capacity.")
        }
        onOpHeld2(crondis_objs.container) { room()?.emptyContainer(player) }

        onOpLoc1(crondis_locs.waterfall) { fill(it.loc, dry = false) }
        onOpLoc1(crondis_locs.waterfall_dry) { fill(it.loc, dry = true) }
        onOpLocU(crondis_locs.waterfall, crondis_objs.container) { fill(it.loc, dry = false) }
        onOpLocU(crondis_locs.waterfall_dry, crondis_objs.container) { fill(it.loc, dry = true) }

        for (palm in crondis_npcs.palm) {
            onOpNpc1(palm) { room()?.waterPalm(player) }
            onOpNpcU(palm, crondis_objs.container) { room()?.waterPalm(player) }
        }

        toaMonsterDeaths(rooms, npcRepo, mapOf(crondis_npcs.crocodile to crondis_seqs.croc_death))
    }

    private fun ProtectedAccess.fill(loc: BoundLocInfo, dry: Boolean) {
        val room = room() ?: return
        val real = room.raid.realOf(loc.coords) ?: return
        room.fill(player, real, dry)
    }

    private fun ProtectedAccess.room(): CrondisPuzzleRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        if (raid.member(player)?.room != ToaRoom.CrondisPuzzle) {
            return null
        }
        return rooms.ensure(raid, ToaRoom.CrondisPuzzle) as? CrondisPuzzleRuntime
    }
}
