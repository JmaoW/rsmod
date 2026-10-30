package org.rsmod.content.areas.misc.isleofsouls.revenant

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_seqs
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_spotanims
import org.rsmod.content.areas.misc.isleofsouls.dungeons.Dungeon
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonEntered
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The revenant cave teleport: one use takes the player to where the Revenant Caves' entrance on the
 * isle takes them, the dungeon's arrival tile ([Dungeon.REVENANT_CAVES]). The scroll is used up.
 *
 * The teleport plays the standard spellbook teleport, as the Home Teleport does: the cast animation
 * and the teleport graphic, then the player moves after [CAST_TICKS], and the reverse animation
 * plays on landing. tsps uses the same standard teleport for its teleport items' jump (its
 * `TeleportType.NORMAL`); the numbers are from its capture of the skull sceptre, not of a scroll.
 *
 * The option is the client's op 6 ("Teleport"), which the held-op script hands to `onOpHeld4`: the
 * client's op 5 is skipped, as it is the built-in "Use". The message text is guessed.
 */
class RevenantCaveTeleportScript @Inject constructor(private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld4(ether_objs.revenant_cave_teleport) {
            invDel(inv, ether_objs.revenant_cave_teleport, 1)
            mes("You teleport into the Revenant Caves.")
            teleportToCaves()
        }
    }

    private suspend fun ProtectedAccess.teleportToCaves() {
        val dungeon = Dungeon.REVENANT_CAVES
        anim(lobby_seqs.cast_teleport)
        spotanim(lobby_spotanims.teleport_casting, height = SPOTANIM_HEIGHT)
        beginTeleport(CAST_TICKS)
        delay(CAST_TICKS)
        telejump(dungeon.arrival)
        anim(lobby_seqs.cast_teleport_reverse)
        resetSpotanim()
        eventBus.publish(DungeonEntered(player, dungeon))
    }

    private companion object {
        /** Ticks the teleport animation plays before the player moves. */
        const val CAST_TICKS = 3

        /** Raises the teleport graphic to the player's waist, as the normal teleport spells do. */
        const val SPOTANIM_HEIGHT = 92
    }
}
