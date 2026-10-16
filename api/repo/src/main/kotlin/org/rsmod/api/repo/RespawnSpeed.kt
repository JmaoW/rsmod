package org.rsmod.api.repo

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.roundToInt

/**
 * A single global knob that speeds up every respawn: npcs, resource locs (rocks, trees, stalls,
 * crops) and permanent ground item spawns.
 *
 * [speed] is a multiplier on how fast things come back: `1.0` leaves every timer as it is (the
 * feature is off), `2.0` halves them, `0.5` doubles them. The repositories expose this as
 * `respawnSpeed`; content that schedules a *respawn* passes its timer through [scale]. Timers that
 * are not respawns (doors, traps, temporary effects) must not use it.
 */
@Singleton
public class RespawnSpeed @Inject constructor() {
    public var speed: Double = OFF
        set(value) {
            require(value.isFinite() && value > 0.0) { "Respawn speed must be above 0: $value" }
            field = value
        }

    public val isOff: Boolean
        get() = speed == OFF

    /**
     * Returns [ticks] shortened by [speed], never below 1 tick. Zero, negative and "never"
     * ([Int.MAX_VALUE]) timers are returned unchanged.
     */
    public fun scale(ticks: Int): Int {
        if (isOff || ticks <= 1 || ticks == Int.MAX_VALUE) {
            return ticks
        }
        return (ticks / speed).roundToInt().coerceAtLeast(1)
    }

    public companion object {
        public const val OFF: Double = 1.0
    }
}
