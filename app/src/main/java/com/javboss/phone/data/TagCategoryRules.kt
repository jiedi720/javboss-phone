package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto

/**
 * 标签分类回填 / 分类管理的纯规则（v0.3.65）。
 *
 * 反查服务端契约发现的坑：`/jav`、`/videos` 列表接口里内嵌的 `tags[]` 是**简化对象**
 * （只有 id / name / provider / count），不带 `category`；分类名只存在于标签管理端点
 * `GET /jav/tags`、`GET /tags` 返回的完整 TagDto 里（Web 端 `mq` 面板按 `t.category`
 * 分组就是靠它）。v0.3.64 的 [CoverTagFoldRules] 直接读列表卡的 `TagDto.category`，
 * 恒为空 → 折叠条件永不成立 → 用户反馈「这条没修正」。
 *
 * 修复：仓库层把标签管理端点的全量标签表做成 `id -> category` 索引，在列表数据
 * 交给 UI 之前用 [enrich] 回填。这样 [CoverTagFoldRules] 一定能拿到分类名。
 * 若某个标签在全量表里也查不到（或服务端本来就没给它分类），保持原值不臆造。
 */
object TagCategoryRules {

    /** 从标签管理端点的全量标签表建立 id -> 分类名 索引；空分类 / 非法 id 不写入 */
    fun indexOf(tags: List<TagDto>): Map<Long, String> =
        tags.filter { it.id > 0 && it.category.isNotBlank() }
            .associate { it.id to it.category.trim() }

    /**
     * 给列表接口的 tags 补分类名：
     *  - 已经带非空 category 的保持原值（服务端若将来补全了字段，不会覆盖）；
     *  - category 为空且索引里能按 id 查到 → 回填；
     *  - 索引为空 / 查不到 / 名字为空 → 原样返回，不臆造分类。
     */
    fun enrich(tags: List<TagDto>, categoryById: Map<Long, String>): List<TagDto> {
        if (categoryById.isEmpty()) return tags
        return tags.map { tag ->
            val category = categoryById[tag.id]?.trim().orEmpty()
            if (tag.category.isNotBlank() || category.isEmpty()) tag
            else tag.copy(category = category)
        }
    }

    /**
     * 分类上移 / 下移后的新 id 顺序（提交给 `PUT .../categories/order` 的 category_ids）。
     * 越界或原地不动时原样返回，避免把非法请求发给服务端。
     */
    fun moveCategory(ids: List<Long>, from: Int, to: Int): List<Long> {
        if (from == to || from !in ids.indices || to !in ids.indices) return ids
        val result = ids.toMutableList()
        val moved = result.removeAt(from)
        result.add(to, moved)
        return result
    }
}
