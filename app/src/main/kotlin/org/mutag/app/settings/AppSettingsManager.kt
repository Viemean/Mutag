package org.mutag.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用配置持久化管理工具，基于 SharedPreferences 维护设置项响应式数据流。
 */
object AppSettingsManager {

    private const val PREFS_NAME = "mutag_settings"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_AMOLED_BLACK = "amoled_black"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_IS_CUSTOM_THEME_COLOR = "is_custom_theme_color"
    private const val KEY_PREFERRED_SOURCE = "preferred_source"
    private const val KEY_DUAL_SOURCE_FALLBACK = "dual_source_fallback"
    private const val KEY_ARTIST_SEPARATOR = "artist_separator"
    private const val KEY_FETCH_ORIGINAL_COVER = "fetch_original_cover"
    private const val KEY_LYRICS_SAVE_POLICY = "lyrics_save_policy"
    private const val KEY_AUTO_MERGE_TRANSLATION = "auto_merge_translation"
    private const val KEY_IGNORE_SHORT_AUDIO = "ignore_short_audio"
    private const val KEY_IGNORE_VIDEO_FILES = "ignore_video_files"

    private var prefs: SharedPreferences? = null

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    /**
     * 初始化配置管理器并从 SharedPreferences 读取持久化状态。
     *
     * @param context Android 上下文实例
     */
    fun initialize(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val themeModeName = sp.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)
        val dynamicColor = sp.getBoolean(KEY_DYNAMIC_COLOR, true)
        val amoledBlack = sp.getBoolean(KEY_AMOLED_BLACK, false)
        val themeColor = sp.getLong(KEY_THEME_COLOR, 0xFF6750A4L)
        val isCustomThemeColor = sp.getBoolean(KEY_IS_CUSTOM_THEME_COLOR, false)

        val sourceName = sp.getString(KEY_PREFERRED_SOURCE, MetadataSource.QQ_MUSIC.name) ?: MetadataSource.QQ_MUSIC.name
        val preferredSource = runCatching { MetadataSource.valueOf(sourceName) }.getOrDefault(MetadataSource.QQ_MUSIC)
        val dualSourceFallback = sp.getBoolean(KEY_DUAL_SOURCE_FALLBACK, true)

        val artistSeparator = sp.getString(KEY_ARTIST_SEPARATOR, " / ") ?: " / "
        val fetchOriginalCover = sp.getBoolean(KEY_FETCH_ORIGINAL_COVER, true)

        val policyName = sp.getString(KEY_LYRICS_SAVE_POLICY, LyricsSavePolicy.EMBED_ONLY.name) ?: LyricsSavePolicy.EMBED_ONLY.name
        val lyricsSavePolicy = runCatching { LyricsSavePolicy.valueOf(policyName) }.getOrDefault(LyricsSavePolicy.EMBED_ONLY)

        val autoMergeTranslation = sp.getBoolean(KEY_AUTO_MERGE_TRANSLATION, true)
        val ignoreShortAudio = sp.getBoolean(KEY_IGNORE_SHORT_AUDIO, true)
        val ignoreVideoFiles = sp.getBoolean(KEY_IGNORE_VIDEO_FILES, true)

        _settingsState.value = SettingsState(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            amoledBlack = amoledBlack,
            themeColor = themeColor,
            isCustomThemeColor = isCustomThemeColor,
            preferredSource = preferredSource,
            dualSourceFallback = dualSourceFallback,
            artistSeparator = artistSeparator,
            fetchOriginalMasterCover = fetchOriginalCover,
            lyricsSavePolicy = lyricsSavePolicy,
            autoMergeTranslation = autoMergeTranslation,
            ignoreShortAudio = ignoreShortAudio,
            ignoreVideoFiles = ignoreVideoFiles,
        )
    }

    /**
     * 设置界面主题显示模式。
     *
     * @param mode 目标主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        _settingsState.update { it.copy(themeMode = mode) }
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    /**
     * 设置是否启用动态取色。
     *
     * @param enabled 启用状态
     */
    fun setDynamicColor(enabled: Boolean) {
        _settingsState.update { it.copy(dynamicColor = enabled) }
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    /**
     * 设置是否启用纯黑背景。
     *
     * @param enabled 启用状态
     */
    fun setAmoledBlack(enabled: Boolean) {
        _settingsState.update { it.copy(amoledBlack = enabled) }
        prefs?.edit()?.putBoolean(KEY_AMOLED_BLACK, enabled)?.apply()
    }

    /**
     * 设置主题种子颜色。
     *
     * @param color 种子色彩 ARGB 整型
     * @param isCustom 是否为自定义输入颜色
     */
    fun setThemeColor(color: Long, isCustom: Boolean = false) {
        _settingsState.update { it.copy(themeColor = color, isCustomThemeColor = isCustom) }
        prefs?.edit()
            ?.putLong(KEY_THEME_COLOR, color)
            ?.putBoolean(KEY_IS_CUSTOM_THEME_COLOR, isCustom)
            ?.apply()
    }

    /**
     * 设置首选在线元数据提供商。
     *
     * @param source 目标数据源
     */
    fun setPreferredSource(source: MetadataSource) {
        _settingsState.update { it.copy(preferredSource = source) }
        prefs?.edit()?.putString(KEY_PREFERRED_SOURCE, source.name)?.apply()
    }

    /**
     * 设置是否启用双源 fallback 重试机制。
     *
     * @param enabled 启用状态
     */
    fun setDualSourceFallback(enabled: Boolean) {
        _settingsState.update { it.copy(dualSourceFallback = enabled) }
        prefs?.edit()?.putBoolean(KEY_DUAL_SOURCE_FALLBACK, enabled)?.apply()
    }

    /**
     * 设置多艺术家合并展示分割符。
     *
     * @param separator 分割符文本
     */
    fun setArtistSeparator(separator: String) {
        _settingsState.update { it.copy(artistSeparator = separator) }
        prefs?.edit()?.putString(KEY_ARTIST_SEPARATOR, separator)?.apply()
    }

    /**
     * 设置是否优先拉取母带级原图封面。
     *
     * @param enabled 启用状态
     */
    fun setFetchOriginalMasterCover(enabled: Boolean) {
        _settingsState.update { it.copy(fetchOriginalMasterCover = enabled) }
        prefs?.edit()?.putBoolean(KEY_FETCH_ORIGINAL_COVER, enabled)?.apply()
    }

    /**
     * 设置歌词保存策略。
     *
     * @param policy 目标保存策略
     */
    fun setLyricsSavePolicy(policy: LyricsSavePolicy) {
        _settingsState.update { it.copy(lyricsSavePolicy = policy) }
        prefs?.edit()?.putString(KEY_LYRICS_SAVE_POLICY, policy.name)?.apply()
    }

    /**
     * 设置是否自动合并翻译歌词。
     *
     * @param enabled 启用状态
     */
    fun setAutoMergeTranslation(enabled: Boolean) {
        _settingsState.update { it.copy(autoMergeTranslation = enabled) }
        prefs?.edit()?.putBoolean(KEY_AUTO_MERGE_TRANSLATION, enabled)?.apply()
    }

    /**
     * 设置曲库扫描是否过滤短音频。
     *
     * @param enabled 启用状态
     */
    fun setIgnoreShortAudio(enabled: Boolean) {
        _settingsState.update { it.copy(ignoreShortAudio = enabled) }
        prefs?.edit()?.putBoolean(KEY_IGNORE_SHORT_AUDIO, enabled)?.apply()
    }

    /**
     * 设置曲库扫描是否过滤视频文件。
     *
     * @param enabled 启用状态
     */
    fun setIgnoreVideoFiles(enabled: Boolean) {
        _settingsState.update { it.copy(ignoreVideoFiles = enabled) }
        prefs?.edit()?.putBoolean(KEY_IGNORE_VIDEO_FILES, enabled)?.apply()
    }
}
