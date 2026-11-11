package com.javboss.phone.ui.components

import com.javboss.phone.data.FavoriteRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卡片星标「修改收藏夹」悬浮窗的端点映射契约。
 *
 * 服务端按实体类型分端点（/tmp/wb.js 反查）：
 *   /jav/{kind}-favorite-groups          kind = jav | idol | studio | series
 *   /jav/{entity}/{id}/favorite-groups  entity = items | idols | studios | series
 * jav → items、idol → idols，其余按复数派生。这里锁死本轮用到的两条。
 */
class FavoriteTargetTest {

    @Test
    fun `jav target uses jav groups and items entity`() {
        val target = FavoriteTarget.jav(42)
        assertEquals("jav", target.kind)
        assertEquals("items", target.entity)
        assertEquals(42L, target.id)
        assertEquals("作品收藏夹", target.title)
        assertEquals(FavoriteRepository.KIND_JAV, target.kind)
        assertEquals(FavoriteRepository.ENTITY_JAV, target.entity)
    }

    @Test
    fun `idol target uses idol groups and idols entity`() {
        val target = FavoriteTarget.idol(7)
        assertEquals("idol", target.kind)
        assertEquals("idols", target.entity)
        assertEquals(7L, target.id)
        assertEquals("女优收藏夹", target.title)
        assertEquals(FavoriteRepository.KIND_IDOL, target.kind)
        assertEquals(FavoriteRepository.ENTITY_IDOL, target.entity)
    }

    @Test
    fun `target keeps the server entity id`() {
        assertEquals(0L, FavoriteTarget.jav(0).id)
        assertEquals(Long.MAX_VALUE, FavoriteTarget.idol(Long.MAX_VALUE).id)
    }
}
