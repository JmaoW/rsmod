package org.rsmod.content.areas.misc.isleofsouls.tob

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

/** Builds a room's runtime the first time the party walks into it and keeps the raid's runtimes. */
@Singleton
class TobRoomRegistry @Inject constructor(private val env: TobRoomEnv) {
    private val factories =
        EnumMap<TobRoom, (TobRoomEnv, TobRaid) -> TobRoomRuntime>(TobRoom::class.java)
    private val runtimes = HashMap<Int, EnumMap<TobRoom, TobRoomRuntime>>()

    fun register(room: TobRoom, factory: (TobRoomEnv, TobRaid) -> TobRoomRuntime) {
        factories[room] = factory
    }

    fun runtime(raid: TobRaid, room: TobRoom): TobRoomRuntime? = runtimes[raid.id]?.get(room)

    fun runtimeOf(npc: Npc): TobRoomRuntime? =
        runtimes.values.firstNotNullOfOrNull { rooms -> rooms.values.firstOrNull { it.owns(npc) } }

    fun ensure(raid: TobRaid, room: TobRoom): TobRoomRuntime? {
        runtime(raid, room)?.let {
            return it
        }
        val factory = factories[room] ?: return null
        val created = factory(env, raid)
        runtimes.getOrPut(raid.id) { EnumMap(TobRoom::class.java) }[room] = created
        created.build()
        return created
    }

    fun ticksOf(seq: SeqType): Int = env.seqTypes[seq].tickDuration

    fun tick() {
        for (rooms in runtimes.values.toList()) {
            for (runtime in rooms.values.toList()) {
                runtime.tick()
            }
        }
    }

    fun destroy(raid: TobRaid) {
        runtimes.remove(raid.id)?.values?.forEach { it.destroy() }
    }
}

/** Wires the rooms into the raid: building on arrival, starting, the tick, clean-up and hits. */
class TobRoomDirector @Inject constructor(private val rooms: TobRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<TobEvents.RoomEntered> { rooms.ensure(raid, room) }
        onEvent<TobEvents.FightStarted> { rooms.ensure(raid, room)?.start() }
        onEvent<TobEvents.RaidEnded> { rooms.destroy(raid) }
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
                runtime.damageDealt(player, dealt)
            }
        }
    }
}

/**
 * The bosses of [types] never chase a player who hits them and never die of damage: the room takes
 * the next step from the hit that would have killed (see [TobRoomRuntime.hitpointsGone]). Every
 * form a boss is transformed into needs this, since queues go by the form shown.
 */
fun ScriptContext.tobBosses(rooms: TobRoomRegistry, types: List<NpcType>) {
    for (type in types) {
        onNpcQueue(type, queues.com_retaliate_player) {}
        onNpcQueue(type, queues.death) { rooms.runtimeOf(npc)?.hitpointsGone(npc) }
    }
}

/**
 * The room monsters of [deaths] (each with its death animation, or none) never chase a player who
 * hits them, and die without drops: the room hears first, then the death plays and they go.
 */
fun ScriptContext.tobMonsterDeaths(
    rooms: TobRoomRegistry,
    npcRepo: NpcRepository,
    deaths: Map<NpcType, SeqType?>,
) {
    for ((type, death) in deaths) {
        onNpcQueue(type, queues.com_retaliate_player) {}
        onNpcQueue(type, queues.death) { dieInRoom(rooms, npcRepo, death) }
    }
}

private suspend fun StandardNpcAccess.dieInRoom(
    rooms: TobRoomRegistry,
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
    if (npc.isSlotAssigned) {
        npcRepo.del(npc, Int.MAX_VALUE)
    }
}

private const val DEATH_TICKS = 3
