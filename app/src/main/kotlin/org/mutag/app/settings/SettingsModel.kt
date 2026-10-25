package org.mutag.app.settings

/**
 * 界面显示主题模式枚举。
 *
 * @param title 界面显示的模式标题文本
 */
enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/**
 * 在线元数据提供商数据源枚举。
 *
 * @param id 数据源唯一英文标识
 * @param title 数据源名称文本
 */
enum class MetadataSource(val id: String, val title: String) {
    QQ_MUSIC("qqmusic", "QQ 音乐 (默认)"),
    NETEASE("netease", "网易云音乐"),
}

/**
 * 歌词保存策略枚举。
 *
 * @param title 策略选项显示文本
 */
enum class LyricsSavePolicy(val title: String) {
    EMBED_ONLY("内嵌歌词到文件"),
    LRC_ONLY("生成独立lrc文件"),
    EMBED_AND_LRC("同时内嵌与生成"),
}

/**
 * 主题预设颜色数据模型。
 *
 * @param name 预设配色展示名称
 * @param color 种子颜色 ARGB 纯整型值
 */
data class ThemeColorPreset(val name: String, val color: Long)

/**
 * Google 官方推荐标准基线预设配色列表。
 */
val DEFAULT_THEME_PRESETS = listOf(
    ThemeColorPreset("极光紫", 0xFF6750A4L),
    ThemeColorPreset("海洋蓝", 0xFF0061A4L),
    ThemeColorPreset("森林绿", 0xFF386A20L),
    ThemeColorPreset("暖阳金", 0xFF825500L),
    ThemeColorPreset("玫瑰红", 0xFF984061L),
)

/**
 * 应用程序设置状态数据容器。
 *
 * @param themeMode 主题模式
 * @param dynamicColor 是否启用动态取色
 * @param amoledBlack 是否启用纯黑背景
 * @param themeColor 当前选定主题种子色彩值
 * @param isCustomThemeColor 是否为用户自定义输入色彩
 * @param preferredSource 首选在线数据源
 * @param dualSourceFallback 是否启用双源 fallback 重试机制
 * @param artistSeparator 艺术家分割符
 * @param fetchOriginalMasterCover 是否优先获取母带级原图封面
 * @param lyricsSavePolicy 歌词持久化策略
 * @param autoMergeTranslation 是否自动合并歌词翻译
 * @param ignoreShortAudio 是否过滤短音频文件
 * @param ignoreVideoFiles 是否过滤视频文件
 * @param excludedDirectories 排除扫描的文件夹路径列表
 */
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amoledBlack: Boolean = false,
    val themeColor: Long = 0xFF6750A4L,
    val isCustomThemeColor: Boolean = false,
    val preferredSource: MetadataSource = MetadataSource.QQ_MUSIC,
    val dualSourceFallback: Boolean = true,
    val artistSeparator: String = " / ",
    val fetchOriginalMasterCover: Boolean = true,
    val lyricsSavePolicy: LyricsSavePolicy = LyricsSavePolicy.EMBED_ONLY,
    val autoMergeTranslation: Boolean = true,
    val ignoreShortAudio: Boolean = true,
    val ignoreVideoFiles: Boolean = true,
    val excludedDirectories: List<String> = emptyList(),
)
