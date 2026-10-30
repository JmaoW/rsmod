package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ape-ex Predator: Ba-Ba's room, and what in it dies or never does. */
class BaBaScript
@Inject
constructor(private val rooms: ToaRoomRegistry, private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.ApmekenBoss) { env, raid -> BaBaRuntime(env, raid) }
        toaBosses(rooms, listOf(baba_npcs.baba, baba_npcs.digging))
        toaMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(
                baba_npcs.baboon to baba_seqs.death,
                baba_npcs.boulder to null,
                baba_npcs.boulder_cracked to null,
            ),
        )
    }
}
