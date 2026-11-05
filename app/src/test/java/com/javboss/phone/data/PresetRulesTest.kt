package com.javboss.phone.data

import com.javboss.phone.data.api.ServerAddressRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRulesTest {

    private fun preset(name: String, url: String, addedAt: Long = 1L, usedAt: Long = 1L) =
        ServerPreset(name = name, url = url, addedAt = addedAt, usedAt = usedAt)

    @Test
    fun `same address is deduplicated regardless of spelling`() {
        var list = emptyList<ServerPreset>()
        list = PresetRules.upsert(list, "192.168.1.10:8655", null, now = 1)
        list = PresetRules.upsert(list, "http://192.168.1.10:8655/", null, now = 2)
        list = PresetRules.upsert(list, "  http://192.168.1.10:8655  ", null, now = 3)

        assertEquals(1, list.size)
        assertEquals("http://192.168.1.10:8655", list[0].url)
        assertEquals(1L, list[0].addedAt) // 首次添加时间保留
        assertEquals(3L, list[0].usedAt) // 最近使用时间刷新
    }

    @Test
    fun `recently used preset moves to front`() {
        var list = emptyList<ServerPreset>()
        list = PresetRules.upsert(list, "http://192.168.1.10:8655", "家", now = 1)
        list = PresetRules.upsert(list, "https://demo.ugdocker.link", "隧道", now = 2)
        assertEquals("隧道", list[0].name)

        list = PresetRules.upsert(list, "http://192.168.1.10:8655", null, now = 3)
        assertEquals("家", list[0].name) // 无新名称时沿用旧名称
        assertEquals(2, list.size)
    }

    @Test
    fun `explicit name wins and is clipped`() {
        val list = PresetRules.upsert(emptyList(), "http://10.0.0.2:8655", "  公司   内网  ", now = 1)
        assertEquals("公司 内网", list[0].name)

        val long = "x".repeat(60)
        val clipped = PresetRules.upsert(emptyList(), "http://10.0.0.2:8655", long, now = 1)
        assertEquals(ServerAddressRules.MAX_NAME_LENGTH, clipped[0].name.length)
    }

    @Test
    fun `blank name falls back to address based name`() {
        val list = PresetRules.upsert(emptyList(), "192.168.1.10:8655", "   ", now = 1)
        assertEquals("局域网 192.168.1.10:8655", list[0].name)
    }

    @Test
    fun `invalid address is not saved`() {
        val list = PresetRules.upsert(emptyList(), "ftp://nope", null, now = 1)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `capacity keeps at most 20 presets and drops the oldest`() {
        var list = emptyList<ServerPreset>()
        repeat(25) { i ->
            list = PresetRules.upsert(list, "http://192.168.1.${i + 1}:8655", null, now = i.toLong())
        }
        assertEquals(PresetRules.MAX_PRESETS, list.size)
        assertEquals("http://192.168.1.25:8655", list[0].url)
        assertFalse(list.any { it.url == "http://192.168.1.1:8655" })
    }

    @Test
    fun `rename and remove work on normalized key`() {
        var list = PresetRules.upsert(emptyList(), "http://192.168.1.10:8655", "家", now = 1)
        list = PresetRules.rename(list, "192.168.1.10:8655", "客厅 NAS")
        assertEquals("客厅 NAS", list[0].name)

        list = PresetRules.remove(list, "http://192.168.1.10:8655/")
        assertTrue(list.isEmpty())
    }

    @Test
    fun `find reports saved state`() {
        val list = PresetRules.upsert(emptyList(), "https://demo.ugdocker.link", null, now = 1)
        assertTrue(PresetRules.isSaved(list, "https://demo.ugdocker.link/"))
        assertFalse(PresetRules.isSaved(list, "https://other.ugdocker.link"))
    }

    @Test
    fun `password is saved and survives later edits`() {
        var list = PresetRules.upsert(emptyList(), "http://10.0.0.2:8655", "家", password = "pw123", now = 1)
        assertEquals("pw123", list[0].password)
        assertTrue(list[0].canAutoLogin)

        // 改名不动密码
        list = PresetRules.rename(list, "http://10.0.0.2:8655", "客厅")
        assertEquals("pw123", list[0].password)

        // 再次 upsert 但 password 传 null（例如只保存名称）：密码保持
        list = PresetRules.upsert(list, "http://10.0.0.2:8655", "新名字", now = 2)
        assertEquals("pw123", list[0].password)
        assertEquals("新名字", list[0].name)
    }

    @Test
    fun `empty password is treated as not saved and can clear an old one`() {
        var list = PresetRules.upsert(emptyList(), "http://10.0.0.2:8655", null, password = "pw123", now = 1)
        assertTrue(list[0].canAutoLogin)

        // 显式传空串 = 清掉密码
        list = PresetRules.upsert(list, "http://10.0.0.2:8655", null, password = "", now = 2)
        assertFalse(list[0].canAutoLogin)
        assertEquals("", list[0].password)

        // setPassword 可单独改密码，不动名称与时间
        list = PresetRules.setPassword(list, "http://10.0.0.2:8655", "new")
        assertEquals("new", list[0].password)
        assertEquals(1L, list[0].addedAt)
    }
}
