package com.javboss.phone.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `config.video_hide_jav` 的解析规则。
 *
 * 关键约束：**读不到明确配置必须返回 null（未知），绝不能降级成 false**。
 * 以前把 `GET /config` 失败当成 false 并缓存，导致 /videos 不带 `hide_jav=1`、
 * 视频 tab 混入 JAV 关联视频，且缓存只在进程内、只有重启 App 才能恢复。
 */
class VideoHideJavTest {

    @Test
    fun `string true and false`() {
        assertEquals(true, parseVideoHideJav(JsonPrimitive("true")))
        assertEquals(false, parseVideoHideJav(JsonPrimitive("false")))
    }

    @Test
    fun `json booleans`() {
        assertEquals(true, parseVideoHideJav(JsonPrimitive(true)))
        assertEquals(false, parseVideoHideJav(JsonPrimitive(false)))
    }

    @Test
    fun `numeric 1 and 0`() {
        assertEquals(true, parseVideoHideJav(JsonPrimitive(1)))
        assertEquals(false, parseVideoHideJav(JsonPrimitive(0)))
    }

    @Test
    fun `value is case insensitive`() {
        assertEquals(true, parseVideoHideJav(JsonPrimitive("TRUE")))
        assertEquals(false, parseVideoHideJav(JsonPrimitive("False")))
    }

    @Test
    fun `missing field is unknown not false`() {
        assertNull(parseVideoHideJav(null))
    }

    @Test
    fun `unrecognised value is unknown not false`() {
        assertNull(parseVideoHideJav(JsonPrimitive("")))
        assertNull(parseVideoHideJav(JsonPrimitive("yes")))
        assertNull(parseVideoHideJav(JsonPrimitive("2")))
    }

    @Test
    fun `non primitive is unknown`() {
        assertNull(parseVideoHideJav(JsonObject(emptyMap())))
        assertNull(parseVideoHideJav(JsonNull))
    }
}
