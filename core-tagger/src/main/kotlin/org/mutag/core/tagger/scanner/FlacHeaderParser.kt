package org.mutag.core.tagger.scanner

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FLAC 格式音频流头部 STREAMINFO 与 Vorbis Comments 物理协议解析器。
 */
internal object FlacHeaderParser {

    /**
     * 校验文件头部是否具备 fLaC 魔数标识。
     *
     * @param file 目标文件
     * @return 具备有效 FLAC 头部魔数时返回 true
     */
    fun isFlacStream(file: File): Boolean = try {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            fis.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "fLaC"
        }
    } catch (_: Exception) {
        false
    }

    /**
     * 解析 FLAC 音频元数据块。
     *
     * @param file 物理音频文件
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param lastModified 修改时间戳
     * @return 填充完成的音频元数据实体
     */
    fun parse(file: File, fileName: String, fileSize: Long, lastModified: Long): AudioMetadata {
        var sampleRate = 44100
        var bitDepth = 16
        var channels = 2
        var totalSamples = 0L
        var hasCover = false
        var coverSize = 0L
        val comments = mutableMapOf<String, String>()

        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            if (fis.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "fLaC") {
                return AudioMetadata(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    fileSize = fileSize,
                    lastModified = lastModified,
                    format = "FLAC",
                    title = file.nameWithoutExtension,
                )
            }

            var isLast = false
            while (!isLast) {
                val header = ByteArray(4)
                if (fis.read(header) != 4) break
                isLast = (header[0].toInt() and 0x80) != 0
                val blockType = header[0].toInt() and 0x7F
                val length =
                    ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                when (blockType) {
                    0 -> {
                        // 解析 STREAMINFO 元数据块（采样率、声道、位深及总采样点数）
                        val data = AudioParserUtils.readFully(fis, length)
                        if (data.size >= 18) {
                            // 解析 20 位采样率、3 位声道数与 5 位位深
                            val b10 = data[10].toInt() and 0xFF
                            val b11 = data[11].toInt() and 0xFF
                            val b12 = data[12].toInt() and 0xFF
                            val b13 = data[13].toInt() and 0xFF
                            sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                            channels = ((b12 ushr 1) and 0x07) + 1
                            bitDepth = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1

                            val b14 = data[14].toInt() and 0xFF
                            val b15 = data[15].toInt() and 0xFF
                            val b16 = data[16].toInt() and 0xFF
                            val b17 = data[17].toInt() and 0xFF
                            // 解析 36 位总采样点数
                            totalSamples = (
                                ((b13 and 0x0F).toLong() shl 32) or
                                    ((b14.toLong() and 0xFF) shl 24) or
                                    ((b15.toLong() and 0xFF) shl 16) or
                                    ((b16.toLong() and 0xFF) shl 8) or
                                    (b17.toLong() and 0xFF)
                                )
                        }
                    }

                    4 -> {
                        // 解析 VORBIS_COMMENT 注释键值对
                        val data = AudioParserUtils.readFully(fis, length)
                        parseVorbisComments(data, comments)
                    }

                    6 -> {
                        hasCover = true
                        if (length >= 32) {
                            val probeLen = minOf(length, 1024)
                            val picHeader = AudioParserUtils.readFully(fis, probeLen)
                            try {
                                val bb = java.nio.ByteBuffer.wrap(picHeader).order(java.nio.ByteOrder.BIG_ENDIAN)
                                bb.int // type
                                val mimeLen = bb.int
                                if (mimeLen >= 0 && bb.remaining() >= mimeLen + 4) {
                                    bb.position(bb.position() + mimeLen)
                                    val descLen = bb.int
                                    if (descLen >= 0 && bb.remaining() >= descLen + 16 + 4) {
                                        bb.position(bb.position() + descLen + 16)
                                        val picLen = bb.int
                                        if (picLen > 0) {
                                            coverSize = picLen.toLong()
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                            AudioParserUtils.seekSkip(fis, (length - picHeader.size).toLong())
                        } else {
                            AudioParserUtils.seekSkip(fis, length.toLong())
                        }
                    }

                    else -> {
                        AudioParserUtils.seekSkip(fis, length.toLong())
                    }
                }
            }
        }

        val durationMs = if (sampleRate > 0) (totalSamples * 1000L) / sampleRate else 0L
        val sampleRateKhz = if (sampleRate % 1000 == 0) "${sampleRate / 1000}k" else "%.1fk".format(sampleRate / 1000.0)
        val specChip = "FLAC ${bitDepth}bit/$sampleRateKhz"

        val title = comments["TITLE"] ?: file.nameWithoutExtension
        val artist = comments["ARTIST"] ?: ""
        val album = comments["ALBUM"] ?: ""
        val albumArtist = comments["ALBUMARTIST"] ?: ""
        val trackNum = comments["TRACKNUMBER"]?.toIntOrNull() ?: 0
        val trackTot = comments["TRACKTOTAL"]?.toIntOrNull() ?: 0
        val discNum = comments["DISCNUMBER"]?.toIntOrNull() ?: 1
        val discTot = comments["DISCTOTAL"]?.toIntOrNull() ?: 1
        val date = comments["DATE"] ?: ""
        val genre = comments["GENRE"] ?: ""
        val lyricist = comments["LYRICIST"] ?: ""
        val composer = comments["COMPOSER"] ?: ""
        val arranger = comments["ARRANGER"] ?: ""
        val producer = comments["PRODUCER"] ?: ""
        val company = comments["ORGANIZATION"] ?: ""
        val comment = comments["COMMENT"] ?: ""
        val lyrics = comments["LYRICS"]
        val cloudId = comments["QQMUSIC_SONGMID"] ?: ""
        val cloudAlbumMid = comments["QQMUSIC_ALBUMMID"] ?: ""
        val desc = comments["DESCRIPTION"] ?: ""
        val language = comments["LANGUAGE"] ?: ""
        val chorusStart = comments["CHORUS_START"]?.toLongOrNull() ?: 0L

        val durationDisplay = AudioParserUtils.formatDuration(durationMs)
        val fileSizeDisplay = AudioParserUtils.formatFileSize(fileSize)
        val bitrateKbps = if (durationMs > 0) ((fileSize * 8) / durationMs).toInt() else 0
        val qualityLevel = if (bitDepth >= 24 || sampleRate >= 88200) "Hi-Res" else "SQ 无损"
        val channelsDisplay = when (channels) {
            1 -> "单声道 (1.0 ch)"
            2 -> "立体声 (2.0 ch)"
            else -> "$channels 声道"
        }

        val techSpec = AudioTechSpec(
            format = "FLAC",
            sampleRateHz = sampleRate,
            sampleRateDisplay = if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz" else "%.1f kHz".format(sampleRate / 1000.0),
            bitDepth = bitDepth,
            bitDepthDisplay = "$bitDepth-bit",
            channels = channels,
            channelsDisplay = channelsDisplay,
            bitrateKbps = bitrateKbps,
            bitrateDisplay = if (bitrateKbps > 0) "$bitrateKbps kbps" else "无损动态",
            fileSize = fileSize,
            fileSizeDisplay = fileSizeDisplay,
            qualityLevel = qualityLevel,
            durationMs = durationMs,
            durationDisplay = durationDisplay,
            tagVersion = "Vorbis Comment",
            filePath = file.absolutePath,
        )

        return AudioMetadata(
            filePath = file.absolutePath,
            fileName = fileName,
            fileSize = fileSize,
            lastModified = lastModified,
            format = "FLAC",
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = trackNum,
            trackTotal = trackTot,
            discNumber = discNum,
            discTotal = discTot,
            publishDate = date,
            genre = genre,
            lyricist = lyricist,
            composer = composer,
            arranger = arranger,
            producer = producer,
            company = company,
            comment = comment,
            cloudTrackId = cloudId,
            cloudAlbumMid = cloudAlbumMid,
            description = desc,
            language = language,
            chorusStartMs = chorusStart,
            durationMs = durationMs,
            specChip = specChip,
            hasCover = hasCover,
            coverSize = coverSize,
            lyrics = lyrics,
            techSpec = techSpec,
            tagHealth = AudioMetadata.evaluateHealth(title, artist, album, hasCover),
        )
    }

    private fun parseVorbisComments(data: ByteArray, out: MutableMap<String, String>) {
        if (data.size < 8) return
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = bb.int
        if (vendorLen < 0 || vendorLen > data.size - 8) return
        bb.position(bb.position() + vendorLen)

        if (bb.remaining() < 4) return
        val userCommentCount = bb.int

        for (i in 0 until userCommentCount) {
            if (bb.remaining() < 4) break
            val commentLen = bb.int
            if (commentLen < 0 || commentLen > bb.remaining()) break
            val commentBytes = ByteArray(commentLen)
            bb.get(commentBytes)
            val entry = String(commentBytes, Charsets.UTF_8)
            val eqIdx = entry.indexOf('=')
            if (eqIdx > 0) {
                val key = entry.substring(0, eqIdx).uppercase()
                val value = entry.substring(eqIdx + 1)
                out[key] = value
            }
        }
    }
}
