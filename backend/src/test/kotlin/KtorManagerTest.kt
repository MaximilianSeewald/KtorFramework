package com.loudless

import com.loudless.config.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorManagerTest {
    @Test
    fun `cors origin parser handles full origin with port`() {
        val origin = parseCorsOrigin("http://localhost:4200")

        assertEquals("localhost:4200", origin.hostWithPort)
        assertEquals(listOf("http"), origin.schemes)
    }

    @Test
    fun `cors origin parser handles host and port without scheme`() {
        val origin = parseCorsOrigin("localhost:4200")

        assertEquals("localhost:4200", origin.hostWithPort)
        assertEquals(listOf("http", "https"), origin.schemes)
    }

    @Test
    fun `cors origin parser handles host without scheme`() {
        val origin = parseCorsOrigin("example.com")

        assertEquals("example.com", origin.hostWithPort)
        assertEquals(listOf("http", "https"), origin.schemes)
    }

    @Test
    fun `cors origin parser fails clearly when host is missing`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            parseCorsOrigin("http://:4200")
        }

        assertEquals("Invalid CORS origin 'http://:4200': host is required", failure.message)
    }

    @Test
    fun `websocket frame size is bounded`() {
        assertEquals(1024L * 1024L, BackendConfig.webSocketMaxFrameSize)
    }
}
