package com.livestream.core.common

import com.livestream.core.common.result.AppResult
import com.livestream.core.common.result.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `test success mapping`() {
        val result: AppResult<Int> = AppResult.Success(100)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is AppResult.Success)
        assertEquals(200, (mapped as AppResult.Success).data)
    }

    @Test
    fun `test error propagation`() {
        val exception = IllegalStateException("Test error")
        val result: AppResult<Int> = AppResult.Error(exception)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is AppResult.Error)
        assertEquals("Test error", (mapped as AppResult.Error).message)
    }
}
