package com.sonora.visuals

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatMathTest {
    @Test fun bpmFromBeatIntervalsIsStable() {
        val intervals = listOf(500L, 501L, 499L, 500L)
        val bpm = (60000.0 / intervals.average()).toInt()
        assertEquals(120, bpm)
    }
}
