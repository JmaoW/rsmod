package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.game.entity.Npc

enum class FightCaveState {
    /** Waiting for the pause between waves (or the start) to run out. */
    WaitingForNextWave,

    /** A wave is on the floor. */
    InWave,

    /** Jad is dead: the run ends once everyone has been sent out. */
    Won,
}

/**
 * One team's attempt at the cave. It lives as long as its [instance] has someone in it; the mode
 * and wave list are fixed when it starts, so changing the setting never changes a run in progress.
 */
class FightCaveRun(
    val instance: TeamInstance,
    val mode: FightCaveMode,
    val waves: FightCaveWaves,
    val rotation: Int,
    startClock: Int,
) {
    val planner = FightCaveSpawnPlanner(rotation)

    var state = FightCaveState.WaitingForNextWave

    /**
     * The number of the wave that is on the floor, or the last one that was. 0 before the first.
     */
    var waveNumber = 0

    /** When the next wave spawns, once the state is [FightCaveState.WaitingForNextWave]. */
    var nextWaveAt = startClock + FIRST_WAVE_DELAY

    /** When everyone is sent out after a win. */
    var leaveAt = 0

    /** The monsters of the current wave that are alive. Healers are kept apart in [healers]. */
    val monsters = ArrayList<Npc>()

    val healers = ArrayList<Npc>()
    var jad: Npc? = null
    var healersSummoned = false

    /** Used so that however many players tick the run in one game tick, it only runs once. */
    var lastTick = -1

    val isLastWave: Boolean
        get() = waveNumber >= waves.count

    companion object {
        const val FIRST_WAVE_DELAY = 10

        /** The pause between a wave being cleared and the next one spawning. */
        const val WAVE_PAUSE = 8

        /** How long after Jad dies everyone is sent out. */
        const val LEAVE_DELAY = 12
    }
}
