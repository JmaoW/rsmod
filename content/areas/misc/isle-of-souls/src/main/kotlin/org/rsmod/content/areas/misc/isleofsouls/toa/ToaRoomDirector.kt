package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.EnumMap
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Builds a room's runtime the first time anyone walks into it and keeps the raid's runtimes. A room
 * with no content registered is just a place.
 */
@Singleton
class ToaRoomRegistry @Inject constructor(private val env: ToaRoomEnv) {
    private val factories =
        EnumMap<ToaRoom, (ToaRoomEnv, ToaRaid) -> ToaRoomRuntime>(ToaRoom::class.java)
    private val runtimes = HashMap<Int, EnumMap<ToaRoom, ToaRoomRuntime>>()

    /** Content registers how to make the runtime of [room]. */
    fun register(room: ToaRoom, factory: (ToaRoomEnv, ToaRaid) -> ToaRoomRuntime) {
        factories[room] = factory
    }

    fun runtime(raid: ToaRaid, room: ToaRoom): ToaRoomRuntime? = runtimes[raid.id]?.get(room)

    fun runtimesOf(raid: ToaRaid): Collection<ToaRoomRuntime> = runtimes[raid.id]?.values.orEmpty()

    /** The runtime that [npc] belongs to, if it is a room npc. */
    fun runtimeOf(npc: Npc): ToaRoomRuntime? =
        runtimes.values.firstNotNullOfOrNull { rooms -> rooms.values.firstOrNull { it.owns(npc) } }

    /** The runtime of the room [raid]'s player stands in. */
    fun ensure(raid: ToaRaid, room: ToaRoom): ToaRoomRuntime? {
        val existing = runtime(raid, room)
        if (existing != null) {
            return existing
        }
        val factory = factories[room] ?: return null
        val created = factory(env, raid)
        runtimes.getOrPut(raid.id) { EnumMap(ToaRoom::class.java) }[room] = created
        created.build()
        return created
    }

    /** How many ticks [seq] plays for (0 when the cache gives no length). */
    fun ticksOf(seq: SeqType): Int = env.seqTypes[seq].tickDuration

    fun tick() {
        for (rooms in runtimes.values) {
            for (runtime in rooms.values) {
                runtime.tick()
            }
        }
    }

    fun reset(raid: ToaRaid, room: ToaRoom) {
        runtime(raid, room)?.reset()
    }

    fun destroy(raid: ToaRaid) {
        runtimes.remove(raid.id)?.values?.forEach { it.destroy() }
    }
}

/** Wires the rooms into the raid: building on entry, the tick, resets, clean-up and points. */
class ToaRoomDirector @Inject constructor(private val rooms: ToaRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<ToaEvents.RoomEntered> { rooms.ensure(raid, room) }
        onEvent<ToaEvents.RoomReset> { rooms.reset(raid, room) }
        onEvent<ToaEvents.RaidEnded> { rooms.destroy(raid) }
        onEvent<GameLifecycle.StartCycle> { rooms.tick() }

        // Hits are trimmed again as they land: one queued before a phase changed must not carry a
        // boss past where the fight now allows.
        onEvent<NpcHitEvents.Landing> {
            val runtime = rooms.runtimeOf(npc) ?: return@onEvent
            damage = runtime.landing(npc, hit, damage)
        }

        // Every point of damage on a room npc is room points for the player who dealt it.
        onEvent<NpcHitEvents.ModifyAny> {
            val player = source ?: return@onEvent
            val runtime = rooms.runtimeOf(npc) ?: return@onEvent
            runtime.modifyHit(npc, hit, source)
            val dealt = minOf(hit.damage, npc.hitpoints)
            if (dealt > 0) {
                runtime.damageDealt(player, npc, dealt)
            }
        }
    }
}
