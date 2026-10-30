package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * How often [PlayerAutosaveProcess] saves players, in game cycles. The defaults are the usual two
 * minutes and thirty seconds; the Isle of Souls event sets them from its settings file at startup.
 */
@Singleton
public class AutosaveSettings @Inject constructor() {
    /** The cycles between a player's periodic saves. */
    public var interval: Int = PlayerAutosaveProcess.AUTOSAVE_INTERVAL
        set(value) {
            require(value >= 1) { "Autosave interval must be at least 1 cycle: $value" }
            field = value
        }

    /** The fewest cycles since the last save before a requested (non-urgent) save is made. */
    public var requestDelay: Int = PlayerAutosaveProcess.AUTOSAVE_REQUEST_DELAY
        set(value) {
            require(value >= 1) { "Autosave request delay must be at least 1 cycle: $value" }
            field = value
        }
}
