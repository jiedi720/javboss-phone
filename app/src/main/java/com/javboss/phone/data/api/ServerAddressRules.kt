package com.javboss.phone.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 用户可选的两种连接目标形态，仅用于展示与自动命名，不改变请求方式 */
enum class ServerAddressKind(val label: String) {
    IP("IP 地址"),
    URL("URL 地址"),
}

/** 地址解析结果：合法时给出可直接入库的归一化地址 */
sealed interface ServerAddressResult {
    data class Valid(
        /** 归一化地址：scheme://host[:port][/path]，无末尾斜杠、无 query/fragment */
        val url: String,
        val kind: ServerAddressKind,
        /** host[:port]，用于自动命名与展示 */
        val hostPort: String,
        val isHttps: Boolean,
        val isPrivateNetwork: Boolean,
    ) : ServerAddressResult

    data class Invalid(val reason: String) : ServerAddressResult
}

/**
 * 连接地址规则（IP 与完整 URL 两类共用同一套校验）
 *
 * 判定顺序：
 * 1. 去首尾空白；空串、含内部空白、超过 300 字符直接拒绝。
 * 2. 不含 "://" 视为「IP/主机:端口」写法，自动补 http://（保持旧版输入习惯）。
 * 3. 协议只允许 http / https，其它（ftp、ws、market 等）拒绝。
 * 4. 交给 OkHttp 解析：主机名非法、端口越界（1–65535）、编码错误都会解析失败 → 拒绝。
 * 5. 不允许携带用户名/密码（user:pass@host），避免 Cookie 同源判定被绕过。
 * 6. 归一化：丢弃 query 与 #fragment，去掉末尾斜杠，省略协议默认端口（http:80 / https:443）。
 * 7. 类型判定：主机是 IPv4 字面量或含 ":" 的 IPv6 字面量 → [ServerAddressKind.IP]，否则 [ServerAddressKind.URL]。
 *
 * 注意：无协议头时一律补 http://（与旧版一致），https 目标必须显式写 https://。
 */
object ServerAddressRules {

    private const val MAX_LENGTH = 300

    /** 预设名称最大长度 */
    const val MAX_NAME_LENGTH = 40

    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun resolve(raw: String): ServerAddressResult {
        val input = raw.trim()
        if (input.isEmpty()) return ServerAddressResult.Invalid("请填写服务器地址")
        if (input.length > MAX_LENGTH) return ServerAddressResult.Invalid("地址过长（最多 $MAX_LENGTH 个字符）")
        if (input.any { it.isWhitespace() }) return ServerAddressResult.Invalid("地址中不能包含空格或换行")

        val withScheme = if (input.contains("://")) input else "http://$input"
        val scheme = withScheme.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") {
            return ServerAddressResult.Invalid("只支持 http:// 或 https:// 开头的地址")
        }

        val parsed = withScheme.toHttpUrlOrNull()
            ?: return ServerAddressResult.Invalid("地址格式不正确，请检查主机名与端口（端口需在 1–65535 之间）")

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return ServerAddressResult.Invalid("地址中不要包含用户名或密码")
        }
        val host = parsed.host
        if (host.isBlank()) return ServerAddressResult.Invalid("地址缺少主机名")

        val isIpLiteral = isIpv4Literal(host) || host.contains(':')
        val defaultPort = if (parsed.scheme == "https") 443 else 80
        val portPart = if (parsed.port == defaultPort) "" else ":${parsed.port}"
        // OkHttp 返回的 IPv6 主机不带方括号，拼回 URL 时要补上
        val hostPart = if (host.contains(':')) "[$host]" else host
        val path = parsed.encodedPath.trimEnd('/')

        val normalized = "${parsed.scheme}://$hostPart$portPart$path"
        return ServerAddressResult.Valid(
            url = normalized,
            kind = if (isIpLiteral) ServerAddressKind.IP else ServerAddressKind.URL,
            hostPort = hostPart + portPart,
            isHttps = parsed.scheme == "https",
            isPrivateNetwork = isIpLiteral && isPrivateIpv4(host),
        )
    }

    /** 合法时返回归一化地址，否则 null（写入会话前统一走这里） */
    fun normalize(raw: String): String? = (resolve(raw) as? ServerAddressResult.Valid)?.url

    fun isValid(raw: String): Boolean = resolve(raw) is ServerAddressResult.Valid

    /**
     * 预设项自动命名：局域网 IP → “局域网 192.168.1.10:8655”，其它 → host[:port]，最长 40 字符。
     * 用户显式填写的名称优先，此函数只作兜底。
     */
    fun suggestName(raw: String): String {
        val valid = resolve(raw) as? ServerAddressResult.Valid
            ?: return raw.trim().take(MAX_NAME_LENGTH)
        return when {
            valid.isPrivateNetwork -> "局域网 ${valid.hostPort}"
            else -> valid.hostPort
        }.take(MAX_NAME_LENGTH)
    }

    private fun isIpv4Literal(host: String): Boolean {
        val match = IPV4.matchEntire(host) ?: return false
        return match.groupValues.drop(1).all { it.toIntOrNull()?.let { n -> n <= 255 } == true }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        if (!IPV4.matches(host)) return false
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            a == 127 ||
            (a == 169 && b == 254)
    }
}
