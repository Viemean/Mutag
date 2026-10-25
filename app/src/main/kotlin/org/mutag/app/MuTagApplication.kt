package org.mutag.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.mutag.app.ui.cover.EmbeddedCoverFetcher
import org.mutag.core.tagger.cache.LocalMetadataCache
import org.mutag.core.tagger.cache.MetadataCache
import org.mutag.provider.api.MusicMetadataProvider
import org.mutag.provider.netease.NeteaseMetadataProvider
import org.mutag.provider.qqmusic.QqMusicMetadataProvider

/**
 * MuTag 应用程序主入口，维护全局单例元数据缓存与提供商实例。
 */
class MuTagApplication :
    Application(),
    SingletonImageLoader.Factory {

    lateinit var cache: MetadataCache
        private set

    lateinit var qqMusicProvider: MusicMetadataProvider
        private set

    lateinit var neteaseProvider: MusicMetadataProvider
        private set

    /**
     * 获取当前设置选中的在线元数据提供商实例。
     *
     * @return 激活的在线元数据提供商
     */
    fun getActiveProvider(): MusicMetadataProvider {
        val preferred = org.mutag.app.settings.AppSettingsManager.settingsState.value.preferredSource
        return if (preferred == org.mutag.app.settings.MetadataSource.NETEASE) neteaseProvider else qqMusicProvider
    }

    /**
     * 获取当前可用的备用在线元数据提供商实例。
     * 若未开启双源 fallback 重试机制则返回 null。
     *
     * @return 备选在线元数据提供商或 null
     */
    fun getFallbackProvider(): MusicMetadataProvider? {
        val settings = org.mutag.app.settings.AppSettingsManager.settingsState.value
        if (!settings.dualSourceFallback) return null
        return if (settings.preferredSource == org.mutag.app.settings.MetadataSource.NETEASE) {
            qqMusicProvider
        } else {
            neteaseProvider
        }
    }

    fun getProviderForSource(sourceId: String): MusicMetadataProvider =
        if (sourceId == neteaseProvider.sourceId) neteaseProvider else qqMusicProvider

    /**
     * 根据候选曲目来源标识获取其对应的在线元数据提供商。
     *
     * @param track 目标曲目元数据
     * @return 对应的提供商实例
     */
    fun getProviderForTrack(track: org.mutag.model.TrackMetadata): MusicMetadataProvider {
        if (track.sourceId.isNotBlank()) {
            return getProviderForSource(track.sourceId)
        }
        val albumMid = track.album.id
        val isNetease = track.coverUrl.contains("126.net") ||
            (albumMid.isNotBlank() && albumMid.all { it.isDigit() }) ||
            (track.trackId.isNotBlank() && track.trackId.all { it.isDigit() })
        return if (isNetease) neteaseProvider else qqMusicProvider
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.mutag.app.settings.AppSettingsManager.initialize(this)

        cache = try {
            LocalMetadataCache(this)
        } catch (_: Exception) {
            org.mutag.core.tagger.cache.InMemoryMetadataCache()
        }

        qqMusicProvider = QqMusicMetadataProvider()
        neteaseProvider = NeteaseMetadataProvider()
    }

    /**
     * 构建 Coil 图片加载器单例，注册音频内嵌封面提取器组件。
     *
     * @param context 平台上下文实例
     * @return 配置完成的 ImageLoader 实例
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(EmbeddedCoverFetcher.Factory())
        }
        .build()

    companion object {
        lateinit var instance: MuTagApplication
            private set
    }
}
