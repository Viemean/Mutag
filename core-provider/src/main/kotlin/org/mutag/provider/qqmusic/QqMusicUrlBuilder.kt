package org.mutag.provider.qqmusic

/**
 * QQ 音乐专辑封面图片分辨率品质枚举。
 */
enum class CoverQuality {
    ORIGINAL_MASTER,
    HD_1200,
    MEDIUM_800,
    SMALL_500,
}

/**
 * QQ 音乐 CDN 静态资源图片（专辑封面、歌手头像）URL 构建器。
 */
object QqMusicUrlBuilder {
    /**
     * 构建专辑封面图片 CDN 直链。
     *
     * @param albumMid 专辑 MID 标识
     * @param quality 目标分辨率品质
     * @return 完整的图片 HTTP 下载直链
     */
    fun buildAlbumCoverUrl(albumMid: String, quality: CoverQuality = CoverQuality.HD_1200): String {
        if (albumMid.isBlank()) return ""
        return when (quality) {
            CoverQuality.ORIGINAL_MASTER -> "https://y.qq.com/music/photo_new/T002M000$albumMid.jpg?max_age=2592000"
            CoverQuality.HD_1200 -> "https://y.qq.com/music/photo_new/T002R1200x1200M000$albumMid.jpg?max_age=2592000"
            CoverQuality.MEDIUM_800 -> "https://y.qq.com/music/photo_new/T002R800x800M000$albumMid.jpg?max_age=2592000"
            CoverQuality.SMALL_500 -> "https://y.qq.com/music/photo_new/T002R500x500M000$albumMid.jpg?max_age=2592000"
        }
    }

    /**
     * 构建视觉专栏封面图片 CDN 直链。
     *
     * @param visualMid 视觉 MID 标识
     * @param quality 目标分辨率品质
     * @return 完整的图片 HTTP 下载直链
     */
    fun buildVisualCoverUrl(visualMid: String, quality: CoverQuality = CoverQuality.HD_1200): String {
        if (visualMid.isBlank() || visualMid == "00000000000000") return ""
        return when (quality) {
            CoverQuality.ORIGINAL_MASTER,
            CoverQuality.HD_1200,
            -> "https://y.qq.com/music/photo_new/T062R1200x1200M000$visualMid.jpg?max_age=2592000"

            CoverQuality.MEDIUM_800 -> "https://y.qq.com/music/photo_new/T062R800x800M000$visualMid.jpg?max_age=2592000"

            CoverQuality.SMALL_500 -> "https://y.qq.com/music/photo_new/T062R500x500M000$visualMid.jpg?max_age=2592000"
        }
    }

    /**
     * 构建歌手高清头像图片 CDN 直链。
     *
     * @param singerMid 歌手 MID 标识
     * @param size 头像宽高像素尺寸
     * @return 完整的头像图片 HTTP 直链
     */
    fun buildSingerAvatarUrl(singerMid: String, size: Int = 800): String {
        if (singerMid.isBlank()) return ""
        return "https://y.gtimg.cn/music/photo_new/T001R${size}x${size}M000$singerMid.jpg?max_age=2592000"
    }
}
