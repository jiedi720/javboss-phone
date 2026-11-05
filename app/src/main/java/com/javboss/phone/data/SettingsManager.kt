package com.javboss.phone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 播放引擎偏好 */
enum class EngineMode(val label: String, val desc: String) {
    AUTO("自动（推荐）", "ExoPlayer 优先，失败自动切 libVLC 兜底"),
    EXO("ExoPlayer", "硬解体验最好，但无法播放 wmv 等冷门格式"),
    VLC("libVLC", "全格式通吃，冷门格式软解较耗性能"),
}

/** 全屏拖动 seek 灵敏度：划满全屏对应的 seek 幅度 = factor × 视频时长 */
enum class SeekSensitivity(val label: String, val factor: Float) {
    LOW("低", 0.25f),
    MEDIUM("中", 1f),
    HIGH("高", 2f),
}

/** 应用设置持久化（播放引擎偏好、拖动 seek 灵敏度） */
class SettingsManager(private val context: Context) {

    private val keyEngineMode = stringPreferencesKey("engine_mode")
    private val keySeekSensitivity = stringPreferencesKey("seek_sensitivity")
    private val keyGridColumns = stringPreferencesKey("grid_columns")
    private val keyJavSort = stringPreferencesKey("jav_sort")
    private val keyJavSortAsc = booleanPreferencesKey("jav_sort_asc")
    private val keyIdolSort = stringPreferencesKey("idol_sort")
    private val keyIdolSortAsc = booleanPreferencesKey("idol_sort_asc")
    private val keyCoverMask = booleanPreferencesKey("cover_mask")
    private val keyFavSort = stringPreferencesKey("fav_sort")
    private val keyFavSortAsc = booleanPreferencesKey("fav_sort_asc")
    private val keyFavItemSort = stringPreferencesKey("fav_item_sort")
    private val keyFavItemSortAsc = booleanPreferencesKey("fav_item_sort_asc")

    @Volatile
    var engineMode: EngineMode = EngineMode.AUTO
        private set

    @Volatile
    var seekSensitivity: SeekSensitivity = SeekSensitivity.MEDIUM
        private set

    /**
     * 卡片墙每行数量（2/3/4）。用 StateFlow 暴露：
     * 在设置页改完，已经打开的「作品」页也能立刻跟着变，不用退出重进。
     */
    private val _gridColumns = MutableStateFlow(2)
    val gridColumnsFlow: StateFlow<Int> = _gridColumns
    val gridColumns: Int get() = _gridColumns.value

    /**
     * 封面毛玻璃（常开）。同样是 StateFlow：设置页一开，
     * 已经在显示的作品/女优墙立刻糊掉，不用退出重进。
     */
    private val _coverMask = MutableStateFlow(false)
    val coverMaskFlow: StateFlow<Boolean> = _coverMask
    val coverMask: Boolean get() = _coverMask.value

    /** 上次选用的排序（枚举名 + 升降序），下次打开 App 直接沿用，不回到默认值 */
    @Volatile
    var javSortName: String? = null
        private set

    @Volatile
    var javSortAsc: Boolean = false
        private set

    @Volatile
    var idolSortName: String? = null
        private set

    @Volatile
    var idolSortAsc: Boolean = false
        private set

    /** 收藏夹列表的排序（同样是枚举名 + 升降序） */
    @Volatile
    var favSortName: String? = null
        private set

    @Volatile
    var favSortAsc: Boolean = true
        private set

    /** 收藏夹内部条目（封面网格）的排序 */
    @Volatile
    var favItemSortName: String? = null
        private set

    @Volatile
    var favItemSortAsc: Boolean = true
        private set

    suspend fun restore() {
        val prefs = context.settingsDataStore.data.first()
        engineMode = prefs[keyEngineMode]
            ?.let { runCatching { EngineMode.valueOf(it) }.getOrNull() }
            ?: EngineMode.AUTO
        seekSensitivity = prefs[keySeekSensitivity]
            ?.let { runCatching { SeekSensitivity.valueOf(it) }.getOrNull() }
            ?: SeekSensitivity.MEDIUM
        _gridColumns.value = prefs[keyGridColumns]?.toIntOrNull()?.coerceIn(2, 4) ?: 2
        _coverMask.value = prefs[keyCoverMask] ?: false
        javSortName = prefs[keyJavSort]
        javSortAsc = prefs[keyJavSortAsc] ?: false
        idolSortName = prefs[keyIdolSort]
        idolSortAsc = prefs[keyIdolSortAsc] ?: false
        favSortName = prefs[keyFavSort]
        favSortAsc = prefs[keyFavSortAsc] ?: true
        favItemSortName = prefs[keyFavItemSort]
        favItemSortAsc = prefs[keyFavItemSortAsc] ?: true
    }

    suspend fun saveEngineMode(mode: EngineMode) {
        engineMode = mode
        context.settingsDataStore.edit { it[keyEngineMode] = mode.name }
    }

    suspend fun saveSeekSensitivity(value: SeekSensitivity) {
        seekSensitivity = value
        context.settingsDataStore.edit { it[keySeekSensitivity] = value.name }
    }

    suspend fun saveJavSort(name: String, asc: Boolean) {
        javSortName = name
        javSortAsc = asc
        context.settingsDataStore.edit {
            it[keyJavSort] = name
            it[keyJavSortAsc] = asc
        }
    }

    suspend fun saveIdolSort(name: String, asc: Boolean) {
        idolSortName = name
        idolSortAsc = asc
        context.settingsDataStore.edit {
            it[keyIdolSort] = name
            it[keyIdolSortAsc] = asc
        }
    }

    suspend fun saveFavSort(name: String, asc: Boolean) {
        favSortName = name
        favSortAsc = asc
        context.settingsDataStore.edit {
            it[keyFavSort] = name
            it[keyFavSortAsc] = asc
        }
    }

    suspend fun saveFavItemSort(name: String, asc: Boolean) {
        favItemSortName = name
        favItemSortAsc = asc
        context.settingsDataStore.edit {
            it[keyFavItemSort] = name
            it[keyFavItemSortAsc] = asc
        }
    }

    suspend fun saveGridColumns(count: Int) {
        val clamped = count.coerceIn(2, 4)
        _gridColumns.value = clamped
        context.settingsDataStore.edit { it[keyGridColumns] = clamped.toString() }
    }

    /** 封面毛玻璃：立即生效 + 持久化 */
    suspend fun saveCoverMask(enabled: Boolean) {
        _coverMask.value = enabled
        context.settingsDataStore.edit { it[keyCoverMask] = enabled }
    }
}
