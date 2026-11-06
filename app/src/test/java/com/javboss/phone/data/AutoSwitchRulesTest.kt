package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「按所在网络自动切换登录」的纯规则：
 * 角色识别（私网 IP = 家里、其余 = 外面）、目标选择、探测地址回退。
 */
class AutoSwitchRulesTest {

    private fun preset(url: String, password: String = "pw") =
        ServerPreset(name = url, url = url, password = password)

    private val lan = "http://192.168.1.88:8655"
    private val wan = "https://app-demo.example.com"

    @Test
    fun `private ipv4 is lan`() {
        assertTrue(AutoSwitchRules.isLanUrl(lan))
        assertTrue(AutoSwitchRules.isLanUrl("http://10.0.0.5:8655"))
        assertTrue(AutoSwitchRules.isLanUrl("http://172.20.1.1:8655"))
    }

    @Test
    fun `public ip and domain are not lan`() {
        assertFalse(AutoSwitchRules.isLanUrl("http://8.8.8.8:8655"))
        assertFalse(AutoSwitchRules.isLanUrl(wan))
    }

    @Test
    fun `guess roles from one lan and one wan preset`() {
        assertEquals(lan to wan, AutoSwitchRules.guessRoles(listOf(preset(lan), preset(wan))))
    }

    @Test
    fun `presets without saved password are ignored`() {
        val noPassword = preset("http://192.168.1.99:8655", password = "")
        assertEquals(
            lan to wan,
            AutoSwitchRules.guessRoles(listOf(noPassword, preset(lan), preset(wan))),
        )
    }

    @Test
    fun `only lan preset leaves wan empty`() {
        assertEquals(lan to "", AutoSwitchRules.guessRoles(listOf(preset(lan))))
    }

    @Test
    fun `only wan preset leaves lan empty`() {
        assertEquals("" to wan, AutoSwitchRules.guessRoles(listOf(preset(wan))))
    }

    @Test
    fun `two lan presets leave wan empty`() {
        val otherLan = "http://192.168.1.89:8655"
        assertEquals(lan to "", AutoSwitchRules.guessRoles(listOf(preset(lan), preset(otherLan))))
    }

    @Test
    fun `target follows reachability`() {
        assertEquals(lan, AutoSwitchRules.targetFor(true, lan, wan))
        assertEquals(wan, AutoSwitchRules.targetFor(false, lan, wan))
    }

    @Test
    fun `probe url falls back to lan preset`() {
        assertEquals(lan, AutoSwitchRules.probeUrl("", lan))
        assertEquals(lan, AutoSwitchRules.probeUrl("   ", lan))
    }

    @Test
    fun `probe url uses a valid override and normalizes it`() {
        assertEquals(
            "http://192.168.1.1:8655",
            AutoSwitchRules.probeUrl("192.168.1.1:8655/", lan),
        )
    }

    @Test
    fun `invalid probe override falls back to lan preset`() {
        assertEquals(lan, AutoSwitchRules.probeUrl("ftp://bad", lan))
        assertEquals(lan, AutoSwitchRules.probeUrl("not a url", lan))
    }
}
