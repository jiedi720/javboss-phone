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

/**
 * 底部导航的 4 个 tab，index 与 [com.javboss.phone.ui.main.MainScreen] 的 mainTabs 顺序一致。
 * 冷启动入口只允许视频 / JAV（收藏夹、设置不作为启动页），见 [startupOptions]。
 */
enum class LaunchTab(val index: Int, val label: String) {
    VIDEOS(0, "视频"),
    JAV(1, "JAV"),
    FAVORITES(2, "收藏夹"),
    SETTINGS(3, "设置"),
    ;

    companion object {
        fun fromIndex(index: Int): LaunchTab = entries.firstOrNull { it.index == index } ?: JAV

        /** 设置页「启动显示界面」只给这两个选项 */
        val startupOptions = listOf(VIDEOS, JAV)
    }
}

/** 应用设置持久化（播放引擎偏好、拖动 seek 灵敏度、列数、封面打码、自动切换登录等） */
class SettingsManager(private val context: Context) {

    private val keyEngineMode = stringPreferencesKey("engine_mode")
    private val keySeekSensitivity = stringPreferencesKey("seek_sensitivity")
    // 列数按窗口方向分开设定：0 = 自动，竖屏 2/3，横屏 5/6
    private val keyPortraitColumns = stringPreferencesKey("portrait_columns")
    private val keyLandscapeColumns = stringPreferencesKey("landscape_columns")
    // 旧版单个 grid_columns（0 = 自动 / 2..8 = 固定列数）：只保留读取用于一次性迁移，不再写入
    private val keyGridColumns = stringPreferencesKey("grid_columns")
    private val keyJavSort = stringPreferencesKey("jav_sort")
    private val keyJavSortAsc = booleanPreferencesKey("jav_sort_asc")
    private val keyIdolSort = stringPreferencesKey("idol_sort")
    private val keyIdolSortAsc = booleanPreferencesKey("idol_sort_asc")
    private val keyCoverMask = booleanPreferencesKey("cover_mask")
    // 锁屏后自动给封面打码的总开关（默认开，保持旧行为）
    private val keyAutoMaskOnLock = booleanPreferencesKey("auto_mask_on_lock")
    // v0.3.51 左侧栏紧凑模式：只显示图标、不显示文字（默认关）
    private val keyRailCompactMode = booleanPreferencesKey("rail_compact_mode")
    private val keyFavSort = stringPreferencesKey("fav_sort")
    private val keyFavSortAsc = booleanPreferencesKey("fav_sort_asc")
    private val keyFavItemSort = stringPreferencesKey("fav_item_sort")
    private val keyFavItemSortAsc = booleanPreferencesKey("fav_item_sort_asc")
    private val keyLaunchTab = stringPreferencesKey("launch_tab")
    // 按所在网络自动切换登录：总开关 + 两条预设角色 + 可选探测地址覆盖
    private val keyAutoSwitch = booleanPreferencesKey("auto_switch_login")
    private val keyLanPreset = stringPreferencesKey("auto_switch_lan_preset")
    private val keyWanPreset = stringPreferencesKey("auto_switch_wan_preset")
    private val keyProbeOverride = stringPreferencesKey("auto_switch_probe_override")

    @Volatile
    var engineMode: EngineMode = EngineMode.AUTO
        private set

    @Volatile
    var seekSensitivity: SeekSensitivity = SeekSensitivity.MEDIUM
        private set

    /**
     * 卡片墙列数按「竖屏 / 横屏」分开存：0 = 自动（竖屏 3 / 横屏 6），
     * 竖屏可选 2/3、横屏可选 5/6。用 StateFlow 暴露，设置页改完已打开的页面立刻跟着变；
     * 真正画网格时由 `rememberGridColumns()` 结合当前窗口宽高判断方向并取对应值。
     */
    private val _portraitColumnsChoice = MutableStateFlow(0)
    val portraitColumnsChoiceFlow: StateFlow<Int> = _portraitColumnsChoice
    val portraitColumnsChoice: Int get() = _portraitColumnsChoice.value

    private val _landscapeColumnsChoice = MutableStateFlow(0)
    val landscapeColumnsChoiceFlow: StateFlow<Int> = _landscapeColumnsChoice
    val landscapeColumnsChoice: Int get() = _landscapeColumnsChoice.value

    /**
     * 封面毛玻璃（常开）。同样是 StateFlow：设置页一开，
     * 已经在显示的作品/女优墙立刻糊掉，不用退出重进。
     */
    private val _coverMask = MutableStateFlow(false)
    val coverMaskFlow: StateFlow<Boolean> = _coverMask
    val coverMask: Boolean get() = _coverMask.value

    /**
     * 锁屏后自动给封面打码的总开关。默认开（保持旧行为）；
     * 关掉后 [PrivacyMask] 的锁屏状态不再参与打码，但「封面毛玻璃」常开仍照常生效。
     */
    private val _autoMaskOnLock = MutableStateFlow(true)
    val autoMaskOnLockFlow: StateFlow<Boolean> = _autoMaskOnLock
    val autoMaskOnLock: Boolean get() = _autoMaskOnLock.value

    /**
     * 左侧栏紧凑模式：开启后原 NavigationRail 的主 tab 与页面工具项都只画图标。
     * StateFlow 让设置页一改，已打开的左侧栏立刻跟着变，不用重启 App。
     */
    private val _railCompactMode = MutableStateFlow(false)
    val railCompactModeFlow: StateFlow<Boolean> = _railCompactMode
    val railCompactMode: Boolean get() = _railCompactMode.value

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

    /** 冷启动默认 tab：默认 JAV，设置页可改 */
    @Volatile
    var launchTab: LaunchTab = LaunchTab.JAV
        private set

    /** 是否按当前网络在「家里 / 外面」两条登录预设间自动切换 */
    @Volatile
    var autoSwitchEnabled: Boolean = false
        private set

    /** 「家里（局域网）」用哪条预设，存归一化 url */
    @Volatile
    var lanPresetUrl: String = ""
        private set

    /** 「外面（外网）」用哪条预设，存归一化 url */
    @Volatile
    var wanPresetUrl: String = ""
        private set

    /** 探测地址覆盖值；留空 = 直接跟随「家里」预设的地址 */
    @Volatile
    var probeUrlOverride: String = ""
        private set

    suspend fun restore() {
        val prefs = context.settingsDataStore.data.first()
        engineMode = prefs[keyEngineMode]
            ?.let { runCatching { EngineMode.valueOf(it) }.getOrNull() }
            ?: EngineMode.AUTO
        seekSensitivity = prefs[keySeekSensitivity]
            ?.let { runCatching { SeekSensitivity.valueOf(it) }.getOrNull() }
            ?: SeekSensitivity.MEDIUM
        // 新设置项不存在时，从旧版单个 grid_columns 迁移到两个方向；
        // 旧值超出方向范围会被钳到边界，不再出现「竖屏选 6 列却只显示 3 列」
        val oldChoice = prefs[keyGridColumns]?.toIntOrNull()
        _portraitColumnsChoice.value = prefs[keyPortraitColumns]?.toIntOrNull()
            ?.takeIf { WindowSizeRules.isValidColumnChoice(it, false) }
            ?: oldChoice?.let { WindowSizeRules.migrateColumnChoice(it, false) }
            ?: 0
        _landscapeColumnsChoice.value = prefs[keyLandscapeColumns]?.toIntOrNull()
            ?.takeIf { WindowSizeRules.isValidColumnChoice(it, true) }
            ?: oldChoice?.let { WindowSizeRules.migrateColumnChoice(it, true) }
            ?: 0
        _coverMask.value = prefs[keyCoverMask] ?: false
        _autoMaskOnLock.value = prefs[keyAutoMaskOnLock] ?: true
        _railCompactMode.value = prefs[keyRailCompactMode] ?: false
        javSortName = prefs[keyJavSort]
        javSortAsc = prefs[keyJavSortAsc] ?: false
        idolSortName = prefs[keyIdolSort]
        idolSortAsc = prefs[keyIdolSortAsc] ?: false
        favSortName = prefs[keyFavSort]
        favSortAsc = prefs[keyFavSortAsc] ?: true
        favItemSortName = prefs[keyFavItemSort]
        favItemSortAsc = prefs[keyFavItemSortAsc] ?: true
        launchTab = prefs[keyLaunchTab]
            ?.let { runCatching { LaunchTab.valueOf(it) }.getOrNull() }
            // 旧版可能存过「收藏夹 / 设置」作为启动页，现统一回落到 JAV
            ?.takeIf { it in LaunchTab.startupOptions }
            ?: LaunchTab.JAV
        autoSwitchEnabled = prefs[keyAutoSwitch] ?: false
        lanPresetUrl = prefs[keyLanPreset].orEmpty()
        wanPresetUrl = prefs[keyWanPreset].orEmpty()
        probeUrlOverride = prefs[keyProbeOverride].orEmpty()
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

    /** 竖屏列数：0 = 自动（3 列），可选 2 / 3；立即生效 + 持久化 */
    suspend fun savePortraitColumns(choice: Int) {
        val valid = if (WindowSizeRules.isValidColumnChoice(choice, false)) choice else 0
        _portraitColumnsChoice.value = valid
        context.settingsDataStore.edit { it[keyPortraitColumns] = valid.toString() }
    }

    /** 横屏列数：0 = 自动（6 列），可选 5 / 6；立即生效 + 持久化 */
    suspend fun saveLandscapeColumns(choice: Int) {
        val valid = if (WindowSizeRules.isValidColumnChoice(choice, true)) choice else 0
        _landscapeColumnsChoice.value = valid
        context.settingsDataStore.edit { it[keyLandscapeColumns] = valid.toString() }
    }

    /** 封面毛玻璃：立即生效 + 持久化 */
    suspend fun saveCoverMask(enabled: Boolean) {
        _coverMask.value = enabled
        context.settingsDataStore.edit { it[keyCoverMask] = enabled }
    }

    /** 锁屏自动打码：立即生效 + 持久化；关掉后锁屏不再触发打码 */
    suspend fun saveAutoMaskOnLock(enabled: Boolean) {
        _autoMaskOnLock.value = enabled
        context.settingsDataStore.edit { it[keyAutoMaskOnLock] = enabled }
    }

    /** 左侧栏紧凑模式：立即生效 + 持久化 */
    suspend fun saveRailCompactMode(enabled: Boolean) {
        _railCompactMode.value = enabled
        context.settingsDataStore.edit { it[keyRailCompactMode] = enabled }
    }

    /** 启动显示界面：只在下次冷启动生效，当前会话不跳转，避免把用户从设置页弹走 */
    suspend fun saveLaunchTab(tab: LaunchTab) {
        launchTab = tab
        context.settingsDataStore.edit { it[keyLaunchTab] = tab.name }
    }

    suspend fun saveAutoSwitchEnabled(enabled: Boolean) {
        autoSwitchEnabled = enabled
        context.settingsDataStore.edit { it[keyAutoSwitch] = enabled }
    }

    /** 一次性写入「家里 / 外面」两条预设角色 */
    suspend fun saveAutoSwitchRoles(lanUrl: String, wanUrl: String) {
        lanPresetUrl = lanUrl
        wanPresetUrl = wanUrl
        context.settingsDataStore.edit {
            it[keyLanPreset] = lanUrl
            it[keyWanPreset] = wanUrl
        }
    }

    /** 探测地址覆盖；留空表示跟随「家里」预设的地址 */
    suspend fun saveProbeUrlOverride(value: String) {
        probeUrlOverride = value
        context.settingsDataStore.edit { it[keyProbeOverride] = value }
    }
}
