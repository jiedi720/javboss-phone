package com.javboss.phone.ui.favorites

import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.data.api.dto.IdolDto

/**
 * 女优收藏夹**内页**的排序维度，文案 / 方向全部对齐 Web 端 `Pe` 与主界面 `IdolSortBase`。
 *
 * 收藏夹 items 接口只返回服务端固定顺序，和作品收藏夹一样全部按本地元数据排序；
 * 元数据来自 `/jav/idols?favorite_group_id=...`（完整 IdolDto，按 id 建索引）。
 * 比主界面多一项「默认顺序」（服务端返回顺序），对应作品收藏夹的 DEFAULT。
 */
enum class FavIdolSortBase(
    val label: String,
    val ascLabel: String,
    val descLabel: String,
    val defaultAsc: Boolean,
) {
    DEFAULT("默认顺序", "原序", "倒序", true),
    WORK("作品数量", "少→多", "多→少", false),
    RECENT("加入时间", "远→近", "近→远", false),
    AGE("年龄", "小→大", "大→小", false),
    HEIGHT("身高", "低→高", "高→低", false),
    BUST("胸围", "小→大", "大→小", false),
    HIPS("臀围", "小→大", "大→小", false),
    WAIST("腰围", "小→大", "大→小", false),
    CUP("罩杯", "小→大", "大→小", false),
}

/** 纯排序规则，便于 JVM 单测覆盖 8 个维度与升降序 */
object FavIdolSortRules {
    fun sort(
        items: List<FavoriteItemDto>,
        meta: Map<Long, IdolDto>,
        sort: FavIdolSortBase,
        asc: Boolean,
    ): List<FavoriteItemDto> {
        // 每个分支都按「ascLabel 方向」排列，asc=false 时统一反转。
        // AGE 特殊：生日越晚年龄越小，所以「小→大」= birthDate 降序。
        val sorted: List<FavoriteItemDto> = when (sort) {
            FavIdolSortBase.DEFAULT -> items
            FavIdolSortBase.WORK -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.workCount ?: 0 }
            )
            FavIdolSortBase.RECENT -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.id ?: 0L }
            )
            FavIdolSortBase.AGE -> items.sortedWith(
                compareByDescending<FavoriteItemDto> { meta[it.id]?.birthDate.orEmpty() }
            )
            FavIdolSortBase.HEIGHT -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.heightCm ?: 0 }
            )
            FavIdolSortBase.BUST -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.bust ?: 0 }
            )
            FavIdolSortBase.HIPS -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.hips ?: 0 }
            )
            FavIdolSortBase.WAIST -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.waist ?: 0 }
            )
            FavIdolSortBase.CUP -> items.sortedWith(
                compareBy<FavoriteItemDto> { meta[it.id]?.cup ?: 0 }
            )
        }
        return if (asc) sorted else sorted.reversed()
    }
}
