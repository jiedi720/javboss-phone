package com.javboss.phone.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOriginTest {
    @Test
    fun `accepts only the configured origin`() {
        val server = "http://192.168.1.10:8655"

        assertTrue(ServerOrigin.matches(server, "http://192.168.1.10:8655/videos/1/stream"))
        assertFalse(ServerOrigin.matches(server, "https://192.168.1.10:8655/videos/1/stream"))
        assertFalse(ServerOrigin.matches(server, "http://192.168.1.10:8080/videos/1/stream"))
        assertFalse(ServerOrigin.matches(server, "http://example.com/videos/1/stream"))
    }

    @Test
    fun `https url target keeps same-origin check`() {
        val server = ServerAddressRules.normalize("https://app-demo.example.com/")!!

        assertTrue(ServerOrigin.matches(server, "https://app-demo.example.com/videos/1/stream"))
        assertFalse(ServerOrigin.matches(server, "http://app-demo.example.com/videos/1/stream"))
        assertFalse(ServerOrigin.matches(server, "https://evil.ugdocker.link/videos/1/stream"))
    }

    @Test
    fun `path prefix does not break same-origin check`() {
        val server = ServerAddressRules.normalize("https://example.com/javboss/")!!

        assertTrue(ServerOrigin.matches(server, "https://example.com/javboss/videos/1/thumbnail"))
        assertTrue(ServerOrigin.matches(server, "https://example.com/other"))
    }
}
