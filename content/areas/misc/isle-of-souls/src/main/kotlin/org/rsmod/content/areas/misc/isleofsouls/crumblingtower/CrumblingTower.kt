package org.rsmod.content.areas.misc.isleofsouls.crumblingtower

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object CrumblingTowerLocs : LocReferences() {
    /** The trapdoor beside the tower's west wall, which is the way down to the basement. */
    val trapdoor = find("sw_tower_basement_trapdoor")

    /** The basement's ladder back up to the trapdoor. */
    val ladder = find("sw_tower_basement_ladder")

    /** The three pieces of the tower's spiral staircase, ground floor to top floor. */
    val stairs_bottom = find("wild6_spiralstairs")
    val stairs_middle = find("wild6_spiralstairs_middle")
    val stairs_top = find("wild6_spiralstairstop")
}

/**
 * The tower's staircase is made of the same pieces as every other spiral staircase, so giving them
 * the generic content groups is all it takes for the generic stair script to climb them. Needs
 * `packCache`.
 */
internal object CrumblingTowerStairEdits : LocEditor() {
    init {
        edit(CrumblingTowerLocs.stairs_bottom) { contentGroup = content.spiralstaircase_up }
        edit(CrumblingTowerLocs.stairs_middle) { contentGroup = content.spiralstaircase_option }
        edit(CrumblingTowerLocs.stairs_top) { contentGroup = content.spiralstaircase_down }
    }
}

/**
 * The trapdoor and the basement ladder. The basement is not 6400 tiles from the tower like the
 * cache's other dungeons, so the generic dungeon-ladder script does not apply: each end sends the
 * player to the open side of the other.
 */
class CrumblingTowerScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(CrumblingTowerLocs.trapdoor) { climbDown() }
        onOpLoc1(CrumblingTowerLocs.ladder) { climbUp() }
    }

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        anim(seqs.human_pickupfloor)
        delay(1)
        telejump(BASEMENT_ARRIVAL)
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(seqs.human_reachforladder)
        delay(1)
        telejump(TRAPDOOR_ARRIVAL)
    }

    companion object {
        /** The trapdoor's tile. It is only open on its east side. */
        val TRAPDOOR = CoordGrid(2131, 2994, 0)

        /** The tile east of the trapdoor, where a player climbing up out of the basement lands. */
        val TRAPDOOR_ARRIVAL = TRAPDOOR.translateX(1)

        /** The basement ladder's tile. It is only open on its west side. */
        val LADDER = CoordGrid(1941, 9451, 0)

        /** The tile west of the ladder, where a player climbing down into the basement lands. */
        val BASEMENT_ARRIVAL = LADDER.translateX(-1)
    }
}
