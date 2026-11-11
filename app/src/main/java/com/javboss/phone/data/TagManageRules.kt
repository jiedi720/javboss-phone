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

    /** 详情页点击标签同款标题：`标签：XXX` */
    fun filterTitle(name: String): String = "标签：${name.trim()}"

    /** /videos 的 tags 参数是逗号分隔的标签名，去空白 / 去重 / 丢空项 */
    fun videoTagQuery(names: List<String>): String =
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /** 新建 / 重命名都要求非空名字，避免把空白名称发给服务端 */
    fun validName(name: String): Boolean = name.trim().isNotEmpty()
}
