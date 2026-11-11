package com.javboss.phone.data

/**
 * 卡片封面角标的纯规则（不依赖 Compose / Android，可在 JVM 单测里回归）。
 */
object CardBadgeRules {

    /**
     * 封面右上角是否显示「服务端收藏」实心爱心。
     *
     * 对齐 Web 端 JAV / 女优 / 片商 / 系列四类卡：只有服务端
     * `favorite_count > 0`（已加入至少一个收藏夹）时才亮琥珀色实心爱心；
     * 0 或异常负值一律不画。Web 端未收藏时靠 `group-hover` 才显形，
     * 手机没有 hover，客户端也没有「加入收藏夹」入口，所以不画空心图标。
     */
    fun showFavoriteBadge(favoriteCount: Int): Boolean = favoriteCount > 0
}
