package org.rsmod.game.type.seq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeqTypeDurationTest {
    private fun seq(block: SeqTypeBuilder.() -> Unit): UnpackedSeqType =
        SeqTypeBuilder("test_seq")
            .apply {
                frameGroup = ShortArray(0)
                frameIndex = ShortArray(0)
                delay = ShortArray(0)
                block()
            }
            .build(0)

    @Test
    fun `frame seq duration comes from its delays`() {
        // 30 + 30 client cycles of 20ms = 1200ms = 2 ticks.
        val type = seq { delay = shortArrayOf(30, 30) }
        assertEquals(2, type.tickDuration)
    }

    @Test
    fun `keyframe seq duration comes from its keyframe range`() {
        // npc_zebak01_death: keyframes 0..120 at one per client cycle = 2400ms = 4 ticks.
        val type = seq {
            keyframeSet = 29097984
            keyframeRangeStart = 0
            keyframeRangeEnd = 120
        }
        assertEquals(4, type.tickDuration)
    }

    @Test
    fun `keyframe seq with a partial tick rounds up`() {
        val type = seq {
            keyframeSet = 1
            keyframeRangeStart = 10
            keyframeRangeEnd = 41
        }
        assertEquals(2, type.tickDuration)
    }

    @Test
    fun `keyframe seq without a range has no known duration`() {
        val type = seq { keyframeSet = 1 }
        assertEquals(0, type.tickDuration)
    }
}
