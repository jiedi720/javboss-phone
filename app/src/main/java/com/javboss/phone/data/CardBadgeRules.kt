package com.javboss.phone.data

/**
 * 卡片封面角标的纯规则（不依赖 Compose / Android，可在 JVM 单测里回归）。
 */
object CardBadgeRules {

    /**
     * 封面右上角星标是否画成「已收藏」样式：黄底 + 黑色实心五角星。
     *
     * 对齐 Web 端 JAV / 女优 / 片商 / 系列四类卡（`Qw = StarRounded` /
     * `Zw = StarBorderRounded`，已收藏 `bg-amber-400 text-amber-950`）：
     * `favorite_count > 0`（已加入至少一个收藏夹）时亮黄底实心星；0 或异常负值
     * 画半透明黑底白色描边星。Web 端未收藏靠 `group-hover` 才显形，手机没有
     * hover，客户端把空心星常显，作为「加入收藏夹」的点击入口。
     *
     * v0.3.59 这个函数曾决定「是否绘制角标」；v0.3.60 起角标常显，
     * 它只决定两种样式中的哪一种（实心 / 描边）。
     */
    fun showFavoriteBadge(favoriteCount: Int): Boolean = favoriteCount > 0

    /** 收藏星标直径：24dp，右上角位置固定用，同时决定已观看徽标下移的距离 */
    const val FAVORITE_BADGE_SIZE_DP = 24

    /** 角标距封面上 / 右边的统一边距 */
    const val BADGE_EDGE_PADDING_DP = 6

    /**
     * 已观看徽标的 top padding（dp）。
     *
     * 收藏星标固定右上角（top = 6dp，高 24dp），已观看徽标要挂在它的**竖直正下方**
     * 而不是并排/左移，所以 top = 6 + 24 + 6 = 36dp；视频卡没有收藏星标时用 6dp。
     */
    fun watchedBadgeTopDp(belowFavorite: Boolean): Int =
        if (belowFavorite) {
            BADGE_EDGE_PADDING_DP + FAVORITE_BADGE_SIZE_DP + BADGE_EDGE_PADDING_DP
        } else {
            BADGE_EDGE_PADDING_DP
        }
}
