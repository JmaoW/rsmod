package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.movement.BlockWalk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sands of Time: Akkha's room, and keeping what stands in it from turning on whoever hits it. */
class AkkhaScript @Inject constructor(private val rooms: ToaRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.HetBoss) { env, raid -> AkkhaRuntime(env, raid) }

        // The fight moves the bosses: none of them walks to, or hits back at, a player who hits it,
        // and none of them dies of damage (the room takes the next step from a hit that would
        // kill).
        for (type in
            listOf(
                akkha_npcs.melee,
                akkha_npcs.range,
                akkha_npcs.mage,
                akkha_npcs.shadow,
                akkha_npcs.enrage,
                akkha_npcs.shadow_enrage,
            )) {
            onNpcQueue(type, queues.com_retaliate_player) {}
            onNpcQueue(type, queues.death) { rooms.runtimeOf(npc)?.hitpointsGone(npc) }
        }
    }
}

/**
 * Akkha and his shadows do not flinch when hit (they replay their idle), and the orbs of the room
 * never block each other or anyone else. Needs `packCache`.
 */
internal object AkkhaEdits : NpcEditor() {
    init {
        for (type in
            listOf(
                akkha_npcs.melee,
                akkha_npcs.range,
                akkha_npcs.mage,
                akkha_npcs.shadow,
                akkha_npcs.enrage,
            )) {
            edit(type) { param[params.defend_anim] = akkha_seqs.ready }
        }
        // The final stand's fake shadows keep the cache's shadow models (the immune look is a tint,
        // see AkkhaTint) but are clickable, as the shadows are.
        edit(akkha_npcs.shadow_enrage) {
            op2 = "Attack"
            param[params.defend_anim] = akkha_seqs.ready
        }
        for (type in
            listOf(
                akkha_npcs.unstable_orb,
                akkha_npcs.darkness_orb,
                akkha_npcs.lightning_orb,
                akkha_npcs.frozen_orb,
                akkha_npcs.burning_orb,
                het_npcs.orb,
            )) {
            edit(type) { blockWalk = BlockWalk.None }
        }
    }
}
