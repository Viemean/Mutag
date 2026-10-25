package org.mutag.app.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置界面 ViewModel，向 UI 层暴露设置状态流并分发修改指令。
 */
class SettingsViewModel : ViewModel() {

    val state: StateFlow<SettingsState> = AppSettingsManager.settingsState

    /**
     * 更新主题显示模式。
     *
     * @param mode 目标主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        AppSettingsManager.setThemeMode(mode)
    }

    /**
     * 更新动态取色开关状态。
     *
     * @param enabled 是否启用动态取色
     */
    fun setDynamicColor(enabled: Boolean) {
        AppSettingsManager.setDynamicColor(enabled)
    }

    /**
     * 更新纯黑背景开关状态。
     *
     * @param enabled 是否启用纯黑背景
     */
    fun setAmoledBlack(enabled: Boolean) {
        AppSettingsManager.setAmoledBlack(enabled)
    }

    /**
     * 更新主题色彩与自定义标记。
     *
     * @param color 种子色彩 ARGB 整型
     * @param isCustom 是否为自定义输入颜色
     */
    fun setThemeColor(color: Long, isCustom: Boolean = false) {
        AppSettingsManager.setThemeColor(color, isCustom)
    }

    /**
     * 更新首选在线元数据数据源。
     *
     * @param source 目标元数据源
     */
    fun setPreferredSource(source: MetadataSource) {
        AppSettingsManager.setPreferredSource(source)
    }

    /**
     * 更新双源 fallback 重试机制开关状态。
     *
     * @param enabled 是否启用双源重试
     */
    fun setDualSourceFallback(enabled: Boolean) {
        AppSettingsManager.setDualSourceFallback(enabled)
    }

    /**
     * 更新多艺术家展示分割符。
     *
     * @param separator 分割符文本
     */
    fun setArtistSeparator(separator: String) {
        AppSettingsManager.setArtistSeparator(separator)
    }

    /**
     * 更新是否优先下载母带原图封面配置。
     *
     * @param enabled 是否优先下载母带原图
     */
    fun setFetchOriginalMasterCover(enabled: Boolean) {
        AppSettingsManager.setFetchOriginalMasterCover(enabled)
    }

    /**
     * 更新歌词持久化策略。
     *
     * @param policy 目标歌词保存策略
     */
    fun setLyricsSavePolicy(policy: LyricsSavePolicy) {
        AppSettingsManager.setLyricsSavePolicy(policy)
    }

    /**
     * 更新翻译歌词自动合并开关。
     *
     * @param enabled 是否自动合并翻译歌词
     */
    fun setAutoMergeTranslation(enabled: Boolean) {
        AppSettingsManager.setAutoMergeTranslation(enabled)
    }

    /**
     * 更新短音频过滤配置。
     *
     * @param enabled 是否过滤短音频
     */
    fun setIgnoreShortAudio(enabled: Boolean) {
        AppSettingsManager.setIgnoreShortAudio(enabled)
    }

    /**
     * 更新视频文件过滤配置。
     *
     * @param enabled 是否过滤视频文件
     */
    fun setIgnoreVideoFiles(enabled: Boolean) {
        AppSettingsManager.setIgnoreVideoFiles(enabled)
    }
}
