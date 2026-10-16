package org.rsmod.content.areas.misc.isleofsouls.waves

import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.game.entity.Npc

/**
 * One team's attempt at a wave-based fight (the Fight Cave, the Inferno). It lives as long as its
 * [instance] has someone in it. A [WaveDirector] advances it; a game adds its own state by
 * extending this class.
 */
abstract class WaveRun(val instance: TeamInstance, startClock: Int) {
    var state = WaveState.WaitingForNextWave

    /**
     * The number of the wave that is on the floor, or the last one that was. 0 before the first.
     */
    var waveNumber = 0

    /** When the next wave spawns, once the state is [WaveState.WaitingForNextWave]. */
    var nextWaveAt = startClock + FIRST_WAVE_DELAY

    /** When everyone is sent out after a win. */
    var leaveAt = 0

    /** The monsters of the current wave that are alive. */
    val monsters = ArrayList<Npc>()

    /** Used so that however many players tick the run in one game tick, it only runs once. */
    var lastTick = -1

    /** How many waves the run's wave list has. */
    abstract val waveCount: Int

    val isLastWave: Boolean
        get() = waveNumber >= waveCount

    /** Every npc the run owns, so that [WaveDirector.clearMonsters] can remove them. */
    open fun allNpcs(): List<Npc> = monsters.toList()

    /** Forgets every npc the run owns (they have been removed from the world). */
    open fun clearNpcs() {
        monsters.clear()
    }

    companion object {
        const val FIRST_WAVE_DELAY = 10

        /** The pause between a wave being cleared and the next one spawning. */
        const val WAVE_PAUSE = 8

        /** How long after the boss dies everyone is sent out. */
        const val LEAVE_DELAY = 12
    }
}
