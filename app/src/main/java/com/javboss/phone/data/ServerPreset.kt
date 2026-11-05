package com.javboss.phone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javboss.phone.data.api.ServerAddressRules
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.presetDataStore by preferencesDataStore(name = "server_presets")

/**
 * 一条预设连接地址。
 * url 使用归一化后的地址（见 [ServerAddressRules]），同时充当去重主键，不额外维护 id。
 *
 * [password] 为空表示该预设没存密码，点选后仍需手输；非空时点选即自动登录。
 * 密码只落在应用私有目录的 DataStore（AndroidManifest 已 allowBackup=false，不会进云备份）。
 */
@Serializable
data class ServerPreset(
    val name: String,
    val url: String,
    val password: String = "",
    val addedAt: Long = 0L,
    val usedAt: Long = 0L,
) {
    /** 点选后是否可直接登录 */
    val canAutoLogin: Boolean get() = password.isNotBlank()
}

/**
 * 预设列表的纯逻辑（无 Android 依赖，可直接单测）：
 * - 去重：以归一化 url 为键，同址只保留一条
 * - 排序：最近使用（usedAt 最大）排最前，列表顺序即展示顺序
 * - 容量：最多 [MAX_PRESETS] 条，超出时淘汰最久未使用的一条
 * - 命名：显式名称优先（去多余空白、截断 24 字），留空时用地址自动命名
 */
object PresetRules {
    const val MAX_PRESETS = 20

    /** 归一化后的 url 作为去重键；解析失败时退化为去空白、去末尾斜杠的原串 */
    fun keyOf(url: String): String =
        ServerAddressRules.normalize(url) ?: url.trim().trimEnd('/')

    fun sanitizeName(raw: String?, fallbackUrl: String): String {
        val singleLine = raw.orEmpty().trim().replace(Regex("\\s+"), " ")
        val clipped = singleLine.take(ServerAddressRules.MAX_NAME_LENGTH)
        return clipped.ifBlank { ServerAddressRules.suggestName(fallbackUrl) }
    }

    /**
     * 新增或更新一条预设：同址合并（保留 addedAt，刷新 usedAt 并移到最前）。
     * [password] 传 null 表示「保持原密码不动」，传空串表示「清掉已存密码」。
     */
    fun upsert(
        list: List<ServerPreset>,
        url: String,
        name: String?,
        password: String? = null,
        now: Long,
    ): List<ServerPreset> {
        val normalized = ServerAddressRules.normalize(url) ?: return list
        val key = keyOf(normalized)
        val existing = list.firstOrNull { keyOf(it.url) == key }
        val merged = ServerPreset(
            name = when {
                !name.isNullOrBlank() -> sanitizeName(name, normalized)
                existing != null -> existing.name
                else -> sanitizeName(null, normalized)
            },
            url = normalized,
            password = when {
                password != null -> password
                existing != null -> existing.password
                else -> ""
            },
            addedAt = existing?.addedAt ?: now,
            usedAt = now,
        )
        return (listOf(merged) + list.filterNot { keyOf(it.url) == key }).take(MAX_PRESETS)
    }

    /** 只改密码，不动名称与排序（自动登录失败时清掉失效密码用） */
    fun setPassword(list: List<ServerPreset>, url: String, password: String): List<ServerPreset> {
        val key = keyOf(url)
        return list.map { if (keyOf(it.url) == key) it.copy(password = password) else it }
    }

    fun rename(list: List<ServerPreset>, url: String, newName: String): List<ServerPreset> {
        val key = keyOf(url)
        if (newName.isBlank()) return list
        return list.map {
            if (keyOf(it.url) == key) it.copy(name = sanitizeName(newName, it.url)) else it
        }
    }

    fun remove(list: List<ServerPreset>, url: String): List<ServerPreset> {
        val key = keyOf(url)
        return list.filterNot { keyOf(it.url) == key }
    }

    fun find(list: List<ServerPreset>, url: String): ServerPreset? {
        val key = keyOf(url)
        return list.firstOrNull { keyOf(it.url) == key }
    }

    fun isSaved(list: List<ServerPreset>, url: String): Boolean = find(list, url) != null
}

/**
 * 预设持久化：DataStore Preferences（文件名 server_presets），
 * 整个列表序列化成一段 JSON 存在单个 key 里——条目上限 20，读写都是毫秒级，
 * 比建表更简单，也不会出现半截写入（DataStore 的写入是原子的）。
 */
class PresetManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(ServerPreset.serializer())
    private val keyPresets = stringPreferencesKey("presets_json")

    @Volatile
    var presets: List<ServerPreset> = emptyList()
        private set

    /** App 启动时恢复；JSON 损坏时回退为空列表，绝不因此崩溃 */
    suspend fun restore() {
        val raw = runCatching { context.presetDataStore.data.first()[keyPresets] }.getOrNull()
        presets = raw?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        }.orEmpty()
    }

    /**
     * 返回 true 表示新建，false 表示同址更新（去重合并）。
     * [password] 传 null 表示不动已存密码；传空串表示清掉密码。
     */
    suspend fun upsert(url: String, name: String? = null, password: String? = null): Boolean {
        val before = presets
        val existed = PresetRules.isSaved(before, url)
        persist(PresetRules.upsert(before, url, name, password, System.currentTimeMillis()))
        return !existed
    }

    /** 清掉某地址已存的密码（自动登录被服务端拒绝时用） */
    suspend fun clearPassword(url: String) {
        persist(PresetRules.setPassword(presets, url, ""))
    }

    suspend fun rename(url: String, newName: String) {
        persist(PresetRules.rename(presets, url, newName))
    }

    suspend fun remove(url: String) {
        persist(PresetRules.remove(presets, url))
    }

    private suspend fun persist(list: List<ServerPreset>) {
        presets = list
        runCatching {
            context.presetDataStore.edit { it[keyPresets] = json.encodeToString(serializer, list) }
        }
    }
}
