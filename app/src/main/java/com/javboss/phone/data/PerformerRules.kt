package com.javboss.phone.data

/**
 * 演员性别口径（v0.3.82）。
 *
 * 服务端与 Web 前端一致：女优 = `female`、男优 = `male`；列表端点分别为
 * `/jav/idols` 与 `/jav/actors`，详情页演员项则共用 `idols` 数组并用 `gender` 区分。
 * 任何缺失/未知值都按女优处理，保证旧服务端行为不变。
 */
object PerformerRules {
    const val GENDER_FEMALE = "female"
    const val GENDER_MALE = "male"

    fun isMale(gender: String?): Boolean = GENDER_MALE.equals(gender, ignoreCase = true)

    /** 男优列表端点；与 Web 端 `cn(e)` 同口径 */
    fun listPath(gender: String?): String =
        if (isMale(gender)) "jav/actors" else "jav/idols"
}
