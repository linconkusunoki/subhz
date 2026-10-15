package com.subhz.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the URL normalisation and authenticated-URL building that every network call
 * (Retrofit interceptor, streaming, cover art) depends on.
 */
class ServerConfigTest {

    @Test
    fun `parse defaults to https and drops trailing slash`() {
        val config = ServerConfig.parse("music.example.com/", "alice", "pw")
        assertEquals("https://music.example.com", config?.url)
        assertEquals("alice", config?.username)
    }

    @Test
    fun `parse keeps explicit scheme and sub path`() {
        val config = ServerConfig.parse("http://10.0.0.5:4533/navidrome/", "alice", "pw")
        assertEquals("http://10.0.0.5:4533/navidrome", config?.url)
    }

    @Test
    fun `parse rejects blank and malformed input`() {
        assertNull(ServerConfig.parse("", "alice", "pw"))
        assertNull(ServerConfig.parse("   ", "alice", "pw"))
    }

    @Test
    fun `authenticatedUrl carries path auth and query`() {
        val url = ServerConfig("https://music.example.com/sub", "alice", "pw")
            .authenticatedUrl("stream.view", "id" to "42")

        assertTrue(url, url.startsWith("https://music.example.com/sub/rest/stream.view?"))
        assertTrue(url, url.contains("u=alice"))
        assertTrue(url, url.contains("id=42"))
        assertTrue(url, url.contains("t="))
        assertTrue(url, url.contains("s="))
        assertTrue(url, url.contains("v=1.16.1"))
    }
}