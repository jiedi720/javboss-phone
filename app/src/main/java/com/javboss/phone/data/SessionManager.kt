package com.javboss.phone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javboss.phone.data.api.ServerAddressRules
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * 会话持久化：服务器地址 + 登录 Cookie。
 * 服务端会话有效期 14 天且每 24h 自动续期，所以本地只要跟着存最新 Cookie 即可。
 */
class SessionManager(private val context: Context) {

    private val keyServerUrl = stringPreferencesKey("server_url")
    private val keyCookie = stringPreferencesKey("cookie_header")

    @Volatile
    var serverUrl: String = ""
        private set

    @Volatile
    var cookieHeader: String = ""
        private set

    val hasSession: Boolean get() = serverUrl.isNotBlank() && cookieHeader.isNotBlank()

    /** App 启动时从 DataStore 恢复（阻塞读一次，量在字节级，可接受） */
    suspend fun restore() {
        val prefs = context.dataStore.data.first()
        val stored = prefs[keyServerUrl] ?: ""
        // 旧版本写入的地址可能带末尾斜杠或省略协议头，这里统一按新规则归一化并回写
        serverUrl = ServerAddressRules.normalize(stored) ?: stored
        if (serverUrl != stored && stored.isNotBlank()) {
            context.dataStore.edit { it[keyServerUrl] = serverUrl }
        }
        cookieHeader = prefs[keyCookie] ?: ""
    }

    /**
     * 保存并归一化服务器地址。
     * 兼容旧写法：`192.168.1.10:8655` 自动补 http://；完整 URL（含 https、路径前缀）原样保留，
     * 去掉 query/fragment 与末尾斜杠。解析失败时退回旧版行为，避免异常路径下写不进去。
     */
    suspend fun saveServerUrl(url: String) {
        val normalized = ServerAddressRules.normalize(url) ?: url.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }
        serverUrl = normalized
        context.dataStore.edit { it[keyServerUrl] = normalized }
    }

    suspend fun saveCookie(header: String) {
        cookieHeader = header
        context.dataStore.edit { it[keyCookie] = header }
    }

    suspend fun clear() {
        serverUrl = ""
        cookieHeader = ""
        context.dataStore.edit {
            it.remove(keyServerUrl)
            it.remove(keyCookie)
        }
    }
}
