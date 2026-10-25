package org.mutag.core.tagger.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.core.tagger.scanner.TagHealth

/**
 * 音频元数据缓存接口。
 */
interface MetadataCache {
    /**
     * 根据路径与文件快照检索缓存记录。
     *
     * @param filePath 音频文件绝对路径
     * @param fileSize 文件字节大小
     * @param lastModified 文件最后修改时间戳
     * @return 缓存命中返回元数据实例，未命中或快照不匹配返回 null
     */
    fun get(filePath: String, fileSize: Long, lastModified: Long): AudioMetadata?

    /**
     * 写入或覆盖单条音频元数据缓存。
     *
     * @param metadata 待缓存的音频元数据
     */
    fun put(metadata: AudioMetadata)

    /**
     * 失效指定文件路径的元数据缓存。
     *
     * @param filePath 目标音频文件绝对路径
     */
    fun invalidate(filePath: String)

    /**
     * 清空全部音频元数据缓存条目。
     */
    fun clear()
}

/**
 * 基于内存 ConcurrentHashMap 的元数据缓存实现。
 */
class InMemoryMetadataCache : MetadataCache {
    private val cache = ConcurrentHashMap<String, AudioMetadata>()

    override fun get(filePath: String, fileSize: Long, lastModified: Long): AudioMetadata? {
        val item = cache[filePath] ?: return null
        if (item.fileSize == fileSize && item.lastModified == lastModified) {
            return item
        }
        cache.remove(filePath)
        return null
    }

    override fun put(metadata: AudioMetadata) {
        cache[metadata.filePath] = metadata
    }

    override fun invalidate(filePath: String) {
        cache.remove(filePath)
    }

    override fun clear() {
        cache.clear()
    }
}

/**
 * 基于 SQLite 数据库的持久化音频元数据缓存实现。
 *
 * @param context Android 上下文实例
 */
class LocalMetadataCache(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION),
    MetadataCache {

    companion object {
        private const val DATABASE_NAME = "mutag_metadata_cache.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "audio_cache"

        private const val COL_FILE_PATH = "file_path"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_LAST_MODIFIED = "last_modified"
        private const val COL_FORMAT = "format"
        private const val COL_TITLE = "title"
        private const val COL_ARTIST = "artist"
        private const val COL_ALBUM = "album"
        private const val COL_ALBUM_ARTIST = "album_artist"
        private const val COL_TRACK_NUMBER = "track_number"
        private const val COL_TRACK_TOTAL = "track_total"
        private const val COL_DISC_NUMBER = "disc_number"
        private const val COL_DISC_TOTAL = "disc_total"
        private const val COL_PUBLISH_DATE = "publish_date"
        private const val COL_GENRE = "genre"
        private const val COL_LYRICIST = "lyricist"
        private const val COL_COMPOSER = "composer"
        private const val COL_COMPANY = "company"
        private const val COL_COMMENT = "comment"
        private const val COL_DURATION_MS = "duration_ms"
        private const val COL_SPEC_CHIP = "spec_chip"
        private const val COL_HAS_COVER = "has_cover"
        private const val COL_TAG_HEALTH = "tag_health"
        private const val COL_CLOUD_TRACK_ID = "cloud_track_id"
        private const val COL_CLOUD_ALBUM_MID = "cloud_album_mid"
        private const val COL_COVER_SIZE = "cover_size"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_FILE_PATH TEXT PRIMARY KEY,
                $COL_FILE_NAME TEXT,
                $COL_FILE_SIZE INTEGER,
                $COL_LAST_MODIFIED INTEGER,
                $COL_FORMAT TEXT,
                $COL_TITLE TEXT,
                $COL_ARTIST TEXT,
                $COL_ALBUM TEXT,
                $COL_ALBUM_ARTIST TEXT,
                $COL_TRACK_NUMBER INTEGER,
                $COL_TRACK_TOTAL INTEGER,
                $COL_DISC_NUMBER INTEGER,
                $COL_DISC_TOTAL INTEGER,
                $COL_PUBLISH_DATE TEXT,
                $COL_GENRE TEXT,
                $COL_LYRICIST TEXT,
                $COL_COMPOSER TEXT,
                $COL_COMPANY TEXT,
                $COL_COMMENT TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_SPEC_CHIP TEXT,
                $COL_HAS_COVER INTEGER,
                $COL_TAG_HEALTH INTEGER,
                $COL_CLOUD_TRACK_ID TEXT,
                $COL_CLOUD_ALBUM_MID TEXT,
                $COL_COVER_SIZE INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_fp ON $TABLE_NAME($COL_FILE_PATH, $COL_FILE_SIZE, $COL_LAST_MODIFIED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_CLOUD_TRACK_ID TEXT")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_CLOUD_ALBUM_MID TEXT")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_COVER_SIZE INTEGER DEFAULT 0")
        }
    }

    override fun get(filePath: String, fileSize: Long, lastModified: Long): AudioMetadata? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COL_FILE_PATH = ? AND $COL_FILE_SIZE = ? AND $COL_LAST_MODIFIED = ?",
            arrayOf(filePath, fileSize.toString(), lastModified.toString()),
            null,
            null,
            null,
        )

        cursor.use {
            if (it.moveToFirst()) {
                val hasCover = it.getInt(it.getColumnIndexOrThrow(COL_HAS_COVER)) == 1
                val healthOrdinal = it.getInt(it.getColumnIndexOrThrow(COL_TAG_HEALTH))
                val health = TagHealth.entries.getOrNull(healthOrdinal) ?: TagHealth.NEEDS_MATCH
                val cloudTrackId = it.getColumnIndex(COL_CLOUD_TRACK_ID).takeIf { idx -> idx >= 0 }?.let { idx -> it.getString(idx) } ?: ""
                val cloudAlbumMid =
                    it.getColumnIndex(COL_CLOUD_ALBUM_MID).takeIf { idx -> idx >= 0 }?.let { idx -> it.getString(idx) } ?: ""
                val coverSize = it.getColumnIndex(COL_COVER_SIZE).takeIf { idx -> idx >= 0 }?.let { idx -> it.getLong(idx) } ?: 0L

                return AudioMetadata(
                    filePath = filePath,
                    fileName = it.getString(it.getColumnIndexOrThrow(COL_FILE_NAME)) ?: File(filePath).name,
                    fileSize = fileSize,
                    lastModified = lastModified,
                    format = it.getString(it.getColumnIndexOrThrow(COL_FORMAT)) ?: "",
                    title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)) ?: "",
                    artist = it.getString(it.getColumnIndexOrThrow(COL_ARTIST)) ?: "",
                    album = it.getString(it.getColumnIndexOrThrow(COL_ALBUM)) ?: "",
                    albumArtist = it.getString(it.getColumnIndexOrThrow(COL_ALBUM_ARTIST)) ?: "",
                    trackNumber = it.getInt(it.getColumnIndexOrThrow(COL_TRACK_NUMBER)),
                    trackTotal = it.getInt(it.getColumnIndexOrThrow(COL_TRACK_TOTAL)),
                    discNumber = it.getInt(it.getColumnIndexOrThrow(COL_DISC_NUMBER)),
                    discTotal = it.getInt(it.getColumnIndexOrThrow(COL_DISC_TOTAL)),
                    publishDate = it.getString(it.getColumnIndexOrThrow(COL_PUBLISH_DATE)) ?: "",
                    genre = it.getString(it.getColumnIndexOrThrow(COL_GENRE)) ?: "",
                    lyricist = it.getString(it.getColumnIndexOrThrow(COL_LYRICIST)) ?: "",
                    composer = it.getString(it.getColumnIndexOrThrow(COL_COMPOSER)) ?: "",
                    company = it.getString(it.getColumnIndexOrThrow(COL_COMPANY)) ?: "",
                    comment = it.getString(it.getColumnIndexOrThrow(COL_COMMENT)) ?: "",
                    durationMs = it.getLong(it.getColumnIndexOrThrow(COL_DURATION_MS)),
                    specChip = it.getString(it.getColumnIndexOrThrow(COL_SPEC_CHIP)) ?: "",
                    hasCover = hasCover,
                    cloudTrackId = cloudTrackId,
                    cloudAlbumMid = cloudAlbumMid,
                    coverSize = coverSize,
                    tagHealth = health,
                )
            }
        }
        return null
    }

    override fun put(metadata: AudioMetadata) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FILE_PATH, metadata.filePath)
            put(COL_FILE_NAME, metadata.fileName)
            put(COL_FILE_SIZE, metadata.fileSize)
            put(COL_LAST_MODIFIED, metadata.lastModified)
            put(COL_FORMAT, metadata.format)
            put(COL_TITLE, metadata.title)
            put(COL_ARTIST, metadata.artist)
            put(COL_ALBUM, metadata.album)
            put(COL_ALBUM_ARTIST, metadata.albumArtist)
            put(COL_TRACK_NUMBER, metadata.trackNumber)
            put(COL_TRACK_TOTAL, metadata.trackTotal)
            put(COL_DISC_NUMBER, metadata.discNumber)
            put(COL_DISC_TOTAL, metadata.discTotal)
            put(COL_PUBLISH_DATE, metadata.publishDate)
            put(COL_GENRE, metadata.genre)
            put(COL_LYRICIST, metadata.lyricist)
            put(COL_COMPOSER, metadata.composer)
            put(COL_COMPANY, metadata.company)
            put(COL_COMMENT, metadata.comment)
            put(COL_DURATION_MS, metadata.durationMs)
            put(COL_SPEC_CHIP, metadata.specChip)
            put(COL_HAS_COVER, if (metadata.hasCover) 1 else 0)
            put(COL_TAG_HEALTH, metadata.tagHealth.ordinal)
            put(COL_CLOUD_TRACK_ID, metadata.cloudTrackId)
            put(COL_CLOUD_ALBUM_MID, metadata.cloudAlbumMid)
            put(COL_COVER_SIZE, metadata.coverSize)
        }

        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun invalidate(filePath: String) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COL_FILE_PATH = ?", arrayOf(filePath))
    }

    override fun clear() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }
}
