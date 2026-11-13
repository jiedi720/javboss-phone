package com.javboss.phone.data

import com.javboss.phone.data.api.dto.StudioCodePrefixDto

/**
 * v0.3.75：片商落地页「番号」分组筛选的纯规则，对齐服务端 Web bundle 的
 * `jk` / `Mk` / `NE` 与服务端 `internal/db/jav.go`：
 *
 * - `NE(d)` 读 `GET /jav/studios/{id}` 的 `code_prefixes` → `{prefix, work_count}`；
 *   前缀 = 番号第一个 `-` / `_` 之前的部分（`javCodePrefixSQL`），大写。
 * - `jk` 显示「番号 (N)」+ 排序切换按钮，两态：`name_asc` / `work_count_desc`。
 * - `Mk(items, sort, "prefix")`：`name_asc` 用 `localeCompare(numeric:true, sensitivity:base)`；
 *   `work_count_desc` 先按作品数降序，相等再按名称升序。
 * - 点胶囊 → JAV 查询同时带 `prefix` 与 `studio_id`（Web `include_studio_filter:true`）。
 *
 * 抽成纯类是为了让排序 / 归一化可单测，UI 只负责渲染。
 */
object JavPrefixRules {

    const val SORT_NAME_ASC = "name_asc"
    const val SORT_WORK_COUNT_DESC = "work_count_desc"

    /** 排序按钮文案：Web 的「名称」/「作品数」 */
    fun sortLabel(byName: Boolean): String = if (byName) "名称" else "作品数"

    fun sortKey(byName: Boolean): String =
        if (byName) SORT_NAME_ASC else SORT_WORK_COUNT_DESC

    /** Web `jk` 的切换：名称升序 ↔ 作品数从多到少 */
    fun toggle(byName: Boolean): Boolean = !byName

    /**
     * 对齐服务端 `normalizeJavCodePrefix`：去空白、转大写、只保留 A-Z0-9，
     * 含其它字符（如 `-`）一律判为无效（返回空串＝不筛选）。
     * 服务端对 `prefix` 查询参数也是这么校验的，客户端提前挡掉可避免无谓请求。
     */
    fun normalize(prefix: String): String {
        val value = prefix.trim().uppercase()
        if (value.isEmpty()) return ""
        for (c in value) {
            if ((c !in 'A'..'Z') && (c !in '0'..'9')) return ""
        }
        return value
    }

    /** 对应 Web `Mk`：稳定排序，数量相等时按名称升序 */
    fun sort(items: List<StudioCodePrefixDto>, byName: Boolean): List<StudioCodePrefixDto> {
        val comparator = Comparator<StudioCodePrefixDto> { a, b ->
            if (byName) {
                naturalCompare(a.prefix, b.prefix)
            } else {
                val byCount = b.workCount.compareTo(a.workCount)
                if (byCount != 0) byCount else naturalCompare(a.prefix, b.prefix)
            }
        }
        return items.sortedWith(comparator)
    }

    /**
     * 近似 JS `localeCompare(other, undefined, {numeric:true, sensitivity:'base'})`：
     * 大小写不敏感、连续数字段按数值比较（"MIA2" < "MIA10"），其余按字符序。
     * 番号前缀全是 ASCII，不需要处理重音 / 本地化排序规则。
     */
    fun naturalCompare(a: String, b: String): Int {
        val left = a.uppercase()
        val right = b.uppercase()
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val ca = left[i]
            val cb = right[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < left.length && left[endA].isDigit()) endA++
                var endB = j
                while (endB < right.length && right[endB].isDigit()) endB++
                // 去前导零后先比长度、再比字面，等价于数值比较且不会溢出
                val numA = left.substring(i, endA).trimStart('0')
                val numB = right.substring(j, endB).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                if (numA != numB) return numA.compareTo(numB)
                i = endA
                j = endB
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++
                j++
            }
        }
        return (left.length - i).compareTo(right.length - j)
    }
}
