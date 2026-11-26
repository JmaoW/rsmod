package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.EnumMap
import org.rsmod.api.config.refs.queues
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Keeps every raid's room runtimes: one per room (made by the factory registered for its type) and
 * one for the Great Olm's chamber.
 */
@Singleton
class CoxRoomRegistry @Inject constructor(private val env: CoxRoomEnv) {
    private val factories =
        EnumMap<CoxRoomType, (CoxRoomEnv, CoxRaid, CoxPlacement) -> CoxRoomRuntime>(
            CoxRoomType::class.java
        )
    var olmFactory: ((CoxRoomEnv, CoxRaid) -> CoxRoomRuntime)? = null
    private val runtimes = HashMap<Int, MutableMap<Int, CoxRoomRuntime>>()
    private val byNpc = HashMap<Npc, CoxRoomRuntime>()

    fun register(
        type: CoxRoomType,
        factory: (CoxRoomEnv, CoxRaid, CoxPlacement) -> CoxRoomRuntime,
    ) {
        factories[type] = factory
    }

    fun runtime(raid: CoxRaid, room: CoxPlacement): CoxRoomRuntime? =
        runtimes[raid.id]?.get(room.index)

    fun olm(raid: CoxRaid): CoxRoomRuntime? = runtimes[raid.id]?.get(OLM_INDEX)

    fun all(raid: CoxRaid): Collection<CoxRoomRuntime> = runtimes[raid.id]?.values ?: emptyList()

    fun runtimeOf(npc: Npc): CoxRoomRuntime? {
        byNpc[npc]?.let { if (it.owns(npc)) return it else byNpc.remove(npc) }
        val found =
            runtimes.values.firstNotNullOfOrNull { rooms ->
                rooms.values.firstOrNull { it.owns(npc) }
            }
        if (found != null) byNpc[npc] = found
        return found
    }

    fun build(raid: CoxRaid) {
        val rooms = runtimes.getOrPut(raid.id) { HashMap() }
        for (room in raid.rooms) {
            val factory = factories[room.type] ?: continue
            val runtime = factory(env, raid, room)
            rooms[room.index] = runtime
            runtime.build()
        }
        olmFactory?.let { factory ->
            val runtime = factory(env, raid)
            rooms[OLM_INDEX] = runtime
            runtime.build()
        }
    }

    fun ticksOf(seq: SeqType): Int = env.seqTypes[seq].tickDuration

    fun tick() {
        for (rooms in runtimes.values.toList()) {
            for (runtime in rooms.values.toList()) runtime.tick()
        }
    }

    fun destroy(raid: CoxRaid) {
        runtimes.remove(raid.id)?.values?.forEach { it.destroy() }
        byNpc.entries.removeIf { it.value.raid === raid }
    }

    companion object {
        const val OLM_INDEX = -1
    }
}

/** Wires the rooms into the raid: building, the tick, clean-up, hits and damage points. */
class CoxRoomDirector
@Inject
constructor(private val rooms: CoxRoomRegistry, private val raids: CoxRaids) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<CoxEvents.RaidBuilt> { rooms.build(raid) }
        onEvent<CoxEvents.RoomEntered> { rooms.runtime(raid, room)?.onEntered(player) }
        onEvent<CoxEvents.RaidEnded> { rooms.destroy(raid) }
        onEvent<GameLifecycle.StartCycle> { rooms.tick() }

        onEvent<NpcHitEvents.Landing> {
            val runtime = rooms.runtimeOf(npc) ?: return@onEvent
            damage = runtime.landing(npc, hit, damage)
        }
        onEvent<NpcHitEvents.ModifyAny> {
            val runtime = rooms.runtimeOf(npc) ?: return@onEvent
            runtime.modifyHit(npc, hit, source)
            val player = source ?: return@onEvent
            val dealt = minOf(hit.damage, npc.hitpoints)
            if (dealt > 0) {
                raids.addPoints(runtime.raid, player, dealt * runtime.pointsPerHitpoint(npc))
            }
        }
    }
}

/** Boss forms never fight back on their own or die of damage: [CoxRoomRuntime.hitpointsGone]. */
fun ScriptContext.coxBosses(rooms: CoxRoomRegistry, types: List<NpcType>) {
    for (type in types) {
        onNpcQueue(type, queues.com_retaliate_player) {}
        onNpcQueue(type, queues.death) { rooms.runtimeOf(npc)?.hitpointsGone(npc) }
    }
}

/**
 * Room monsters of [deaths] (with their death animation, or none) never chase on their own and die
 * without drops: the room hears first, then the death plays and they go.
 */
fun ScriptContext.coxMonsterDeaths(
    rooms: CoxRoomRegistry,
    npcRepo: NpcRepository,
    deaths: Map<NpcType, SeqType?>,
) {
    for ((type, death) in deaths) {
        onNpcQueue(type, queues.com_retaliate_player) { rooms.runtimeOf(npc)?.provoke(npc) }
        onNpcQueue(type, queues.death) { dieInRoom(rooms, npcRepo, death) }
    }
}

private fun CoxRoomRuntime.provoke(npc: Npc) {
    monsters[npc]?.provoked = true
}

private suspend fun StandardNpcAccess.dieInRoom(
    rooms: CoxRoomRegistry,
    npcRepo: NpcRepository,
    death: SeqType?,
) {
    rooms.runtimeOf(npc)?.died(npc)
    walk(coords)
    noneMode()
    hideAllOps()
    arriveDelay()
    if (death != null) {
        anim(death)
        delay(maxOf(DEATH_TICKS, rooms.ticksOf(death)))
    }
    if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
}

private const val DEATH_TICKS = 3
