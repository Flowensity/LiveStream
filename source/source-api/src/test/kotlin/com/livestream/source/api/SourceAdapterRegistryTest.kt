package com.livestream.source.api

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SourceAdapterRegistryTest {

    private class FakeSourceAdapter(override val providerId: String) : SourceAdapter {
        override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution =
            SourceResolution.NativeMedia(uri = "https://example.com/test.m3u8", protocol = "HLS")

        override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult =
            AvailabilityResult.Available(latencyMs = 30L)
    }

    @Test
    fun `test register and retrieve adapter`() {
        val registry = DefaultSourceAdapterRegistry()
        val adapter = FakeSourceAdapter("provider_test")

        registry.register(adapter)
        val retrieved = registry.getAdapter("provider_test")

        assertNotNull(retrieved)
        assertEquals("provider_test", retrieved?.providerId)
    }

    @Test
    fun `test unregister adapter`() {
        val registry = DefaultSourceAdapterRegistry()
        val adapter = FakeSourceAdapter("provider_test")

        registry.register(adapter)
        registry.unregister("provider_test")

        assertNull(registry.getAdapter("provider_test"))
    }
}
