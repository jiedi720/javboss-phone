package com.javboss.phone.data

import com.javboss.phone.data.api.ServerAddressResult
import com.javboss.phone.data.api.ServerAddressRules

/**
 * 「按所在网络自动切换登录」的纯逻辑（无 Android 依赖，可直接单测）。
 *
 * 两条已存密码的预设分别扮演「家里（局域网）」与「外面（外网）」：
 * - 家里：私网 IPv4 字面量的预设（如 192.168.x.x / 10.x.x.x）
 * - 外面：其余（域名 / 公网 IP）的预设
 * 只考虑存过密码的预设（[ServerPreset.canAutoLogin]），没密码没法在后台静默登录。
 */
object AutoSwitchRules {

    /** 该地址是否属于「家里」：私网 IPv4 字面量 */
    fun isLanUrl(url: String): Boolean =
        (ServerAddressRules.resolve(url) as? ServerAddressResult.Valid)?.isPrivateNetwork == true

    /**
     * 从已保存的预设里自动认出两条角色，返回 (家里 url, 外面 url)。
     * 认不出的那一项为空串，留给用户到设置页手动指定；两条绝不会指向同一地址。
     */
    fun guessRoles(presets: List<ServerPreset>): Pair<String, String> {
        val usable = presets.filter { it.canAutoLogin }
        val lan = usable.firstOrNull { isLanUrl(it.url) }?.url.orEmpty()
        val wan = usable.firstOrNull { !isLanUrl(it.url) && it.url != lan }?.url.orEmpty()
        return lan to wan
    }

    /** 探测结果 → 应该用哪条预设的地址：连得上用家里，连不上用外面 */
    fun targetFor(reachable: Boolean, lanUrl: String, wanUrl: String): String =
        if (reachable) lanUrl else wanUrl

    /**
     * 有效探测地址：留空则跟随「家里」预设的地址（最省事，也正是「能连上家里服务器就算在家」）。
     * 填了别的地址则以它为准；填的地址不合法时退回家里地址，避免误判成「不在家」。
     */
    fun probeUrl(override: String, lanUrl: String): String {
        val trimmed = override.trim()
        if (trimmed.isBlank()) return lanUrl
        return ServerAddressRules.normalize(trimmed) ?: lanUrl
    }
}

/** 切换器给 UI 的一次性通知 */
data class ServerSwitchNotice(
    /** 是否真的换了服务器（需要重置导航、重建各页面 ViewModel） */
    val changed: Boolean,
    /** Toast 文案 */
    val message: String,
)
