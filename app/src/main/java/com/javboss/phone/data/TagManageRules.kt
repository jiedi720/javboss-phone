package com.javboss.phone.data

/**
 * 两套互不相通的标签数据（Web 端 `/jav/tags` 与 `/tags` 是两张表）。
 * JAV 界面顶栏的「标签」按钮管 [JAV]，视频界面的「标签」按钮管 [VIDEO]。
 */
enum class TagScope(val label: String) {
    JAV("JAV 标签"),
    VIDEO("视频标签"),
}

/**
 * 标签管理 / 标签筛选的纯规则（不依赖 Compose / Android，可在 JVM 单测回归）。
 *
 * 反查 Web bundle 得到的端点：
 *  - JAV  ：GET/POST `jav/tags`、PATCH/DELETE `jav/tags/{id}`
 *  - 视频 ：GET/POST `tags`、PATCH/DELETE `tags/{id}`、POST `videos/tags/{add,remove,replace}`
 *  - 视频列表按标签名筛选：`GET /videos?tags=<name1,name2>`（Web 端 `xt()` 拼 `tags` 参数）
 */
object TagManageRules {
    fun listPath(scope: TagScope): String = when (scope) {
        TagScope.JAV -> "jav/tags"
        TagScope.VIDEO -> "tags"
    }

    fun itemPath(scope: TagScope, id: Long): String = "${listPath(scope)}/$id"

    /** 分类列表端点（Web 端 `cn()` / `Br()` 各自 GET，返回分类数组） */
    fun categoryListPath(scope: TagScope): String = when (scope) {
        TagScope.JAV -> "jav/tag-categories"
        TagScope.VIDEO -> "tags/categories"
    }

    /** 详情页点击标签同款标题：`标签：XXX` */
    fun filterTitle(name: String): String = "标签：${name.trim()}"

    /** /videos 的 tags 参数是逗号分隔的标签名，去空白 / 去重 / 丢空项 */
    fun videoTagQuery(names: List<String>): String =
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /** 新建 / 重命名都要求非空名字，避免把空白名称发给服务端 */
    fun validName(name: String): Boolean = name.trim().isNotEmpty()

    // ---------- v0.3.65：分类管理 / 多选查找 ----------

    /** 分类排序端点：PUT `.../categories/order`，body {category_ids} */
    fun categoryOrderPath(scope: TagScope): String = "${categoryListPath(scope)}/order"

    /** 分类重命名 / 删除端点：PATCH|DELETE `.../categories/{id}` */
    fun categoryItemPath(scope: TagScope, id: Long): String = "${categoryListPath(scope)}/$id"

    /** 批量把标签归入分类：POST `.../category`，body {tag_ids, category_id} */
    fun categorizePath(scope: TagScope): String = "${listPath(scope)}/category"

    /** 多选查找的列表标题：`标签：A、B、C`（去空白 / 丢空 / 去重） */
    fun multiFilterTitle(names: List<String>): String {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (clean.isEmpty()) "" else "标签：${clean.joinToString("、")}"
    }

    /** JAV 列表按 tag id 多选筛选：/jav 的 tag_ids 是逗号分隔的 id */
    fun javTagIdsQuery(tags: List<com.javboss.phone.data.api.dto.TagDto>): String =
        tags.map { it.id }.filter { it > 0 }.distinct().joinToString(",")
}
