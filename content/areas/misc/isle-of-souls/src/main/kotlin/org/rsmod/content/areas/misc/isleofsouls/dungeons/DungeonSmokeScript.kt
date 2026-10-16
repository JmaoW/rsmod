package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onEvent
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object SmokeObjs : ObjReferences() {
    val face_mask = find("slayer_facemask")
}

/**
 * The Smoke Devil Dungeon is full of dust. A player in it with no face mask, slayer helmet (of any
 * colour, imbued or not) coughs and loses a hitpoint every few ticks; it never kills them, and at 1
 * hitpoint they wheeze instead. The Wiki says the smoke "periodically" hurts without giving the
 * damage or the interval, so these are guesses: 1 hitpoint every 4 ticks.
 */
class DungeonSmokeScript
@Inject
constructor(private val players: PlayerList, private val objTypes: ObjTypeList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.StartCycle> {
            for (player in players) {
                if (player.currentMapClock % INTERVAL == 0 && inDust(player)) {
                    breathe(player)
                }
            }
        }
    }

    private fun inDust(player: Player): Boolean = player.coords in SMOKE_AREA

    /** Hurts [player] a little if nothing covers their face. */
    fun breathe(player: Player) {
        if (isProtected(player)) {
            return
        }
        if (player.hitpoints <= 1) {
            player.say("*wheeze*")
            return
        }
        player.say("*cough*")
        player.queueHit(1, HitType.Typeless, 1)
    }

    fun isProtected(player: Player): Boolean {
        val head = player.hat ?: return false
        if (head.isType(SmokeObjs.face_mask)) {
            return true
        }
        val type = objTypes[head]
        if (type.param(params.slayer_helm) != 0 || type.param(params.slayer_helm_imbued) != 0) {
            return true
        }
        // The coloured slayer helmets do not all carry the cache's helmet params.
        val name = type.internalName ?: return false
        return !name.startsWith("placeholder") && name.contains("slayer_helm")
    }

    companion object {
        const val INTERVAL = 4

        /** The dungeon's map area, which the dust fills. */
        val SMOKE_AREA = DungeonArea(2304, 9408, 2431, 9471, level = 0)

        private operator fun DungeonArea.contains(coords: CoordGrid): Boolean =
            (level == null || coords.level == level) &&
                coords.x in minX..maxX &&
                coords.z in minZ..maxZ
    }
}
