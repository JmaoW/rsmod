package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.game.entity.Npc

/**
 * One team's attempt at the cave. It lives as long as its [instance] has someone in it; the mode
 * and wave list are fixed when it starts, so changing the setting never changes a run in progress.
 */
class FightCaveRun(
    instance: TeamInstance,
    val mode: FightCaveMode,
    val waves: FightCaveWaves,
    val rotation: Int,
    startClock: Int,
) : WaveRun(instance, startClock) {
    val planner = FightCaveSpawnPlanner(rotation)

    /** Jad's healers, kept apart from the wave's [monsters]. */
    val healers = ArrayList<Npc>()
    var jad: Npc? = null
    var healersSummoned = false

    override val waveCount: Int
        get() = waves.count

    override fun allNpcs(): List<Npc> = (monsters + healers).toList()

    override fun clearNpcs() {
        monsters.clear()
        healers.clear()
    }
}
