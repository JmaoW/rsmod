package org.rsmod.content.areas.misc.isleofsouls.waves

enum class WaveState {
    /** Waiting for the pause between waves (or the start) to run out. */
    WaitingForNextWave,

    /** A wave is on the floor. */
    InWave,

    /** The boss is dead: the run ends once everyone has been sent out. */
    Won,
}
