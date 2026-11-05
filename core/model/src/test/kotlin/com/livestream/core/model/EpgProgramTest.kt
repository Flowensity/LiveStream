package com.livestream.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgProgramTest {

    @Test
    fun `test program current playing time window`() {
        val program = EpgProgram(
            id = "prog_1",
            channelId = "cctv1",
            title = "新闻联播",
            startTimestamp = 1000L,
            endTimestamp = 2000L
        )

        assertTrue(program.isCurrentlyPlaying(1000L))
        assertTrue(program.isCurrentlyPlaying(1500L))
        assertFalse(program.isCurrentlyPlaying(999L))
        assertFalse(program.isCurrentlyPlaying(2000L))
    }
}
