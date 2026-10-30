package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** A Mother's Curse: Kephri's room, and what in it dies or never does. */
class KephriScript
@Inject
constructor(private val rooms: ToaRoomRegistry, private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.ScabarasBoss) { env, raid -> KephriRuntime(env, raid) }
        // Queues go by the form she shows, so every form she can be hit in needs the handling,
        // the dead one too (a hit still in flight as she dies must not make her fight back).
        toaBosses(
            rooms,
            listOf(kephri_npcs.kephri, kephri_npcs.dazed, kephri_npcs.enraged, kephri_npcs.dead),
        )
        toaMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(
                kephri_npcs.swarm to kephri_seqs.swarm_death,
                kephri_npcs.egg to null,
                kephri_npcs.egg_hatching to null,
                kephri_npcs.agile to kephri_seqs.scarab_death,
                kephri_npcs.soldier to kephri_seqs.scarab_death,
                kephri_npcs.spitter to kephri_seqs.scarab_death,
                kephri_npcs.arcane to kephri_seqs.scarab_death,
            ),
        )
    }
}
