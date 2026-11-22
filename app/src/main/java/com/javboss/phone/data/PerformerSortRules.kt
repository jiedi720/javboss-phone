package com.javboss.phone.data

/**
 * 女优 / 男优排序维度口径（v0.3.85）。
 *
 * 反查 dev 前端 bundle（`dev_frontend.js`）：
 * - `Je` 是女优全套排序：work / recent / birth(年龄) / height / bust / hips / waist / cup（8 项）。
 * - `Ye = {bust, hips, waist, cup}` 是女优专属的三围 / 罩杯维度。
 * - `Xe = Je.filter(e => !Ye.has(e.base))` 是男优排序：只剩 work / recent / birth / height（4 项）。
 * - `Ze(gender) = gender==='male' ? Xe : Je`；
 *   `_t()` 会把男优误用的三围维度纠正回 `Xe[0].defaultValue`（即 work）。
 *
 * 这里用 IdolSortBase 的枚举名（WORK / RECENT / AGE / HEIGHT / BUST / HIPS / WAIST / CUP）
 * 作为 key，避免 data 包反向依赖 ui.works 包；调用方传 `IdolSortBase.name` 即可。
 */
object PerformerSortRules {
    /** 男优允许的排序维度（对应服务端 Xe），用 IdolSortBase.name */
    val MALE_ALLOWED_NAMES = setOf("WORK", "RECENT", "AGE", "HEIGHT")

    /** 女优三围 / 罩杯维度（对应服务端 Ye），男优不应使用 */
    val MEASUREMENT_NAMES = setOf("BUST", "HIPS", "WAIST", "CUP")

    /** 该维度男优是否可用 */
    fun allowedForMale(name: String): Boolean = name in MALE_ALLOWED_NAMES

    /**
     * 对齐 Web 端 `_t()`：男优若当前维度是三围 / 罩杯，回退到 WORK（Xe[0]）。
     * 女优原样返回。
     */
    fun effectiveSortName(name: String, isMale: Boolean): String =
        if (isMale && !allowedForMale(name)) "WORK" else name
}
