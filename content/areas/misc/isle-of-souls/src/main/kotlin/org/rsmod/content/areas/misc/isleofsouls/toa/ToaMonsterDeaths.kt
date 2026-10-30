package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.config.refs.queues
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The room monsters of [deaths] (each with its death animation, or none) never chase a player who
 * hits them (the room moves them), and they die without drops: the room hears of it first, then the
 * monster plays its death and leaves once the animation has finished (at least [ticks] ticks). The
 * animation is played here rather than read from the type's `death_anim`, so nothing has to be
 * packed into the cache for it.
 */
fun ScriptContext.toaMonsterDeaths(
    rooms: ToaRoomRegistry,
    npcRepo: NpcRepository,
    deaths: Map<NpcType, SeqType?>,
    ticks: Int = DEATH_TICKS,
) {
    for ((type, death) in deaths) {
        onNpcQueue(type, queues.com_retaliate_player) {}
        onNpcQueue(type, queues.death) { dieInRoom(rooms, npcRepo, death, ticks) }
    }
}

/**
 * The bosses of [types] never chase a player who hits them and never die of damage: the room takes
 * the next step from the hit that would have killed (see [ToaRoomRuntime.hitpointsGone]).
 */
fun ScriptContext.toaBosses(rooms: ToaRoomRegistry, types: List<NpcType>) {
    for (type in types) {
        onNpcQueue(type, queues.com_retaliate_player) {}
        onNpcQueue(type, queues.death) { rooms.runtimeOf(npc)?.hitpointsGone(npc) }
    }
}

private suspend fun StandardNpcAccess.dieInRoom(
    rooms: ToaRoomRegistry,
    npcRepo: NpcRepository,
    death: SeqType?,
    ticks: Int,
) {
    rooms.runtimeOf(npc)?.died(npc)
    walk(coords)
    noneMode()
    hideAllOps()
    arriveDelay()
    if (death != null) {
        anim(death)
        delay(maxOf(ticks, rooms.ticksOf(death)))
    }
    if (npc.isSlotAssigned) {
        npcRepo.del(npc, Int.MAX_VALUE)
    }
}

/** The least time a death animation is given before the monster goes. */
private const val DEATH_TICKS = 3
