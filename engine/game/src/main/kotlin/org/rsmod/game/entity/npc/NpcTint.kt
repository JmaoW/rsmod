package org.rsmod.game.entity.npc

/**
 * A colour overlay the client blends over every untextured face of an npc's model while it lasts.
 *
 * Each face colour is in the client's 16-bit HSL (hue 0-63, saturation 0-7, lightness 0-127), and
 * each channel moves toward the value given here by `weight / 128`. A channel set to [KEEP] is left
 * alone. Pulling lightness toward one high value flattens the model's shading, which gives a pale,
 * washed-out look (Akkha and his shadows while they are immune).
 *
 * @property weight how far each channel moves, 1 to 127 (the client ignores the tint at 0 and above
 *   127).
 */
public data class NpcTint(
    public val hue: Int,
    public val saturation: Int,
    public val lightness: Int,
    public val weight: Int,
) {
    init {
        require(hue == KEEP || hue in 0..63) { "`hue` must be in 0..63 or KEEP: $hue" }
        require(saturation == KEEP || saturation in 0..7) {
            "`saturation` must be in 0..7 or KEEP: $saturation"
        }
        require(lightness == KEEP || lightness in 0..127) {
            "`lightness` must be in 0..127 or KEEP: $lightness"
        }
        require(weight in 1..127) { "`weight` must be in 1..127: $weight" }
    }

    public companion object {
        /** Leaves a channel as the model has it. */
        public const val KEEP: Int = -1

        /** The client counts tint times in 20 ms cycles: 30 to a game tick. */
        public const val CLIENT_CYCLES_PER_TICK: Int = 30

        /** The longest a tint can be sent for, in ticks (the end time is an unsigned short). */
        public const val MAX_DURATION: Int = 65535 / CLIENT_CYCLES_PER_TICK
    }
}
