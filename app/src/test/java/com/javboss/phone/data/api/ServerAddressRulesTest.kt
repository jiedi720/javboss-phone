package com.javboss.phone.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressRulesTest {

    private fun valid(raw: String): ServerAddressResult.Valid =
        ServerAddressRules.resolve(raw) as? ServerAddressResult.Valid
            ?: error("应判定为合法地址：$raw")

    private fun invalid(raw: String): String =
        (ServerAddressRules.resolve(raw) as? ServerAddressResult.Invalid)?.reason
            ?: error("应判定为非法地址：$raw")

    @Test
    fun `ip with port keeps working without scheme`() {
        val result = valid("192.168.1.10:8655")
        assertEquals("http://192.168.1.10:8655", result.url)
        assertEquals(ServerAddressKind.IP, result.kind)
        assertFalse(result.isHttps)
        assertTrue(result.isPrivateNetwork)
    }

    @Test
    fun `ip with scheme and trailing slash normalizes`() {
        assertEquals("http://192.168.1.10:8655", ServerAddressRules.normalize("http://192.168.1.10:8655/"))
        assertEquals("http://192.168.1.10:8655", ServerAddressRules.normalize("  HTTP://192.168.1.10:8655  "))
    }

    @Test
    fun `full https url is accepted`() {
        val result = valid("https://app-demo.example.com/")
        assertEquals("https://app-demo.example.com", result.url)
        assertEquals(ServerAddressKind.URL, result.kind)
        assertTrue(result.isHttps)
        assertFalse(result.isPrivateNetwork)
    }

    @Test
    fun `url with path keeps path and drops query and fragment`() {
        assertEquals(
            "https://example.com/javboss",
            ServerAddressRules.normalize("https://example.com/javboss/?token=1#top"),
        )
    }

    @Test
    fun `default port is omitted from normalized url`() {
        assertEquals("https://example.com", ServerAddressRules.normalize("https://example.com:443"))
        assertEquals("http://example.com", ServerAddressRules.normalize("http://example.com:80"))
        assertEquals("https://example.com:8655", ServerAddressRules.normalize("https://example.com:8655"))
    }

    @Test
    fun `public ipv4 is not treated as a private network`() {
        assertFalse(valid("http://8.8.8.8:8655").isPrivateNetwork)
        assertTrue(valid("http://10.0.0.5:8655").isPrivateNetwork)
        assertTrue(valid("http://172.20.1.1:8655").isPrivateNetwork)
    }

    @Test
    fun `unsupported schemes are rejected`() {
        assertTrue(invalid("ftp://example.com").contains("http"))
        assertTrue(invalid("ws://example.com").contains("http"))
    }

    @Test
    fun `malformed inputs are rejected`() {
        assertTrue(invalid("").contains("请填写"))
        assertTrue(invalid("http://").contains("格式"))
        assertTrue(invalid("http://192.168.1.10:99999").contains("端口"))
        assertTrue(invalid("http://192.168.1.10 :8655").contains("空格"))
        assertTrue(invalid("http://user:pass@example.com").contains("用户名"))
    }

    @Test
    fun `suggested name prefers local network label`() {
        assertEquals("局域网 192.168.1.10:8655", ServerAddressRules.suggestName("192.168.1.10:8655"))
        assertEquals(
            "app-demo.example.com",
            ServerAddressRules.suggestName("https://app-demo.example.com/"),
        )
    }
}
