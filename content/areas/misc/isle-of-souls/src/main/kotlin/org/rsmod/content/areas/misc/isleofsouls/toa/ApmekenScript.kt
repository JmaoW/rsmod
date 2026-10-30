package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Path of Apmeken: the crates, the roof supports and the neutralising potion, and the baboons'
 * deaths. The waves and Apmeken's Sight are [ApmekenPuzzleRuntime].
 */
class ApmekenScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.ApmekenPuzzle) { env, raid -> ApmekenPuzzleRuntime(env, raid) }

        onOpLoc1(apmeken_locs.hammers) { room(started = false)?.takeSupplies(player, true) }
        onOpLoc1(apmeken_locs.potions) { room(started = false)?.takeSupplies(player, false) }
        for (pillar in listOf(apmeken_locs.pillar, apmeken_locs.pillar_shaking)) {
            onOpLoc1(pillar) {
                val room = room() ?: return@onOpLoc1
                room.raid.realOf(it.loc.coords)?.let { real -> room.repair(player, real) }
            }
        }
        onOpHeld1(apmeken_objs.potion) { room()?.pour(player) }
        onOpPlayerU(apmeken_objs.potion) { room()?.cleanse(player, it.target) }

        toaMonsterDeaths(
            rooms,
            npcRepo,
            listOf(
                    apmeken_npcs.brawler,
                    apmeken_npcs.thrower,
                    apmeken_npcs.mage,
                    apmeken_npcs.brawler_large,
                    apmeken_npcs.thrower_large,
                    apmeken_npcs.mage_large,
                    apmeken_npcs.shaman,
                    apmeken_npcs.cursed,
                    apmeken_npcs.thrall,
                )
                .associateWith { apmeken_seqs.death },
        )
        // A volatile baboon's death is its explosion: the room plays the burst, not the usual
        // death, and removes it as the blast lands (see ApmekenPuzzleRuntime.monsterDied).
        onNpcQueue(apmeken_npcs.volatile, queues.com_retaliate_player) {}
        onNpcQueue(apmeken_npcs.volatile, queues.death) {
            val room = rooms.runtimeOf(npc)
            if (room == null) {
                npcRepo.del(npc, Int.MAX_VALUE)
                return@onNpcQueue
            }
            room.died(npc)
        }
    }

    private fun ProtectedAccess.room(started: Boolean = true): ApmekenPuzzleRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        if (raid.member(player)?.room != ToaRoom.ApmekenPuzzle) {
            return null
        }
        val room = rooms.ensure(raid, ToaRoom.ApmekenPuzzle) as? ApmekenPuzzleRuntime ?: return null
        if (started && !room.isStarted) {
            mes("You don't need to do that right now.")
            return null
        }
        return room
    }
}
