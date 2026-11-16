package com.sonora.visuals
import org.junit.Assert.assertEquals
import org.junit.Test
class BeatMathTest {
    @Test fun stableTempoFromIntervals() {
        val intervals = listOf(500L, 501L, 499L, 500L)
        assertEquals(120, (60000.0 / intervals.average()).toInt())
    }
}
