package org.rsmod.routefinder.util

public object Rotations {
    public fun rotate(angle: Int, dimensionA: Int, dimensionB: Int): Int =
        if (angle and 0x1 != 0) {
            dimensionB
        } else {
            dimensionA
        }

    /**
     * Rotates the four direction bits of [blockAccessFlags]. Any bit above them is ignored: the
     * cache's `forceApproachFlags` (loc opcode 69) always has the 0x10 bit set on top of the
     * directions (23, 27, 29 and 30 are the only values it takes), and letting it shift down into
     * the direction bits would block every side of a loc turned by any angle but 0.
     */
    public fun rotate(angle: Int, blockAccessFlags: Int): Int {
        val flags = blockAccessFlags and 0xF
        return if (angle == 0) {
            flags
        } else {
            ((flags shl angle) and 0xF) or (flags shr (4 - angle))
        }
    }
}
