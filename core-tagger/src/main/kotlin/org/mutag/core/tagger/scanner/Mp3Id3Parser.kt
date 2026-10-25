package org.mutag.core.tagger.scanner

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MP3 格式 ID3v2 标签与 MPEG 音频帧探测解析器。
 */
internal object Mp3Id3Parser {

    /**
     * 校验文件头部是否具备 ID3v2 标识或 MPEG 同步字头。
     *
     * @param file 目标文件
     * @return 符合 MP3 流特征时返回 true
     */
    fun isMp3Stream(file: File): Boolean = try {
        FileInputStream(file).use { fis ->
            val header = ByteArray(3)
            fis.read(header) == 3 &&
                (
                    (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) ||
                        (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
                    )
        }
    } catch (_: Exception) {
        false
    }

    /**
     * 解析 MP3 ID3v2 标签帧与第一帧音频头。
     *
     * @param file 目标音频文件
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param lastModified 修改时间戳
     * @return 填充完成的音频元数据实体
     */
    fun parse(file: File, fileName: String, fileSize: Long, lastModified: Long): AudioMetadata {
        val frames = mutableMapOf<String, String>()
        val txxxFrames = mutableMapOf<String, String>()
        var comment = ""
        var hasCover = false
        var coverSize = 0L
        var lyrics: String? = null
        var id3TagSize = 0

        FileInputStream(file).use { fis ->
            val head = ByteArray(10)
            if (fis.read(head) == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                // 解算 ID3v2 头部 7 位同步安全大小 (Syncsafe integer)
                val s0 = head[6].toInt() and 0x7F
                val s1 = head[7].toInt() and 0x7F
                val s2 = head[8].toInt() and 0x7F
                val s3 = head[9].toInt() and 0x7F
                val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                id3TagSize = tagSize

                val tagBuffer = AudioParserUtils.readFully(fis, tagSize)
                val bb = ByteBuffer.wrap(tagBuffer).order(ByteOrder.BIG_ENDIAN)

                while (bb.remaining() >= 10) {
                    val frameIdBytes = ByteArray(4)
                    bb.get(frameIdBytes)
                    if (frameIdBytes[0] == 0.toByte()) break // Padding reached

                    val frameId = String(frameIdBytes, Charsets.US_ASCII)
                    val frameLen = bb.int
                    bb.short // skip 2 bytes flags

                    if (frameLen <= 0 || frameLen > bb.remaining()) break

                    val frameData = ByteArray(frameLen)
                    bb.get(frameData)

                    when (frameId) {
                        "APIC" -> {
                            hasCover = true
                            if (frameData.size > 5) {
                                var mimeEnd = -1
                                for (pos in 1 until frameData.size) {
                                    if (frameData[pos] == 0.toByte()) {
                                        mimeEnd = pos
                                        break
                                    }
                                }
                                if (mimeEnd > 0) {
                                    var descEnd = -1
                                    for (pos in mimeEnd + 2 until frameData.size) {
                                        if (frameData[pos] == 0.toByte()) {
                                            descEnd = pos
                                            break
                                        }
                                    }
                                    if (descEnd > 0 && descEnd + 1 < frameData.size) {
                                        coverSize = (frameData.size - (descEnd + 1)).toLong()
                                    }
                                }
                            }
                        }

                        "USLT" -> {
                            if (frameLen > 4) {
                                val encodingByte = frameData[0].toInt() and 0xFF
                                val charset = AudioParserUtils.charsetForEncoding(encodingByte)
                                var descEnd = -1
                                if (encodingByte == 1 || encodingByte == 2) {
                                    for (i in 4 until frameData.size - 1 step 2) {
                                        if (frameData[i] == 0.toByte() && frameData[i + 1] == 0.toByte()) {
                                            descEnd = i
                                            break
                                        }
                                    }
                                } else {
                                    for (i in 4 until frameData.size) {
                                        if (frameData[i] == 0.toByte()) {
                                            descEnd = i
                                            break
                                        }
                                    }
                                }
                                val valOffset = if (descEnd >= 4) {
                                    if (encodingByte == 1 || encodingByte == 2) descEnd + 2 else descEnd + 1
                                } else {
                                    4
                                }
                                val valLen = frameData.size - valOffset
                                if (valLen > 0) {
                                    lyrics = String(frameData, valOffset, valLen, charset).trim('\u0000', ' ')
                                }
                            }
                        }

                        "COMM" -> {
                            if (frameLen > 4) {
                                val encodingByte = frameData[0].toInt() and 0xFF
                                val charset = AudioParserUtils.charsetForEncoding(encodingByte)
                                var descEnd = -1
                                if (encodingByte == 1 || encodingByte == 2) {
                                    for (i in 4 until frameData.size - 1 step 2) {
                                        if (frameData[i] == 0.toByte() && frameData[i + 1] == 0.toByte()) {
                                            descEnd = i
                                            break
                                        }
                                    }
                                } else {
                                    for (i in 4 until frameData.size) {
                                        if (frameData[i] == 0.toByte()) {
                                            descEnd = i
                                            break
                                        }
                                    }
                                }
                                val valOffset = if (descEnd >= 4) {
                                    if (encodingByte == 1 || encodingByte == 2) descEnd + 2 else descEnd + 1
                                } else {
                                    4
                                }
                                val valLen = frameData.size - valOffset
                                if (valLen > 0) {
                                    comment = String(frameData, valOffset, valLen, charset).trim('\u0000', ' ')
                                }
                            }
                        }

                        "TXXX" -> {
                            if (frameLen > 1) {
                                val encodingByte = frameData[0].toInt() and 0xFF
                                val charset = AudioParserUtils.charsetForEncoding(encodingByte)
                                var delimiterIndex = -1
                                if (encodingByte == 1 || encodingByte == 2) {
                                    for (i in 1 until frameData.size - 1 step 2) {
                                        if (frameData[i] == 0.toByte() && frameData[i + 1] == 0.toByte()) {
                                            delimiterIndex = i
                                            break
                                        }
                                    }
                                } else {
                                    for (i in 1 until frameData.size) {
                                        if (frameData[i] == 0.toByte()) {
                                            delimiterIndex = i
                                            break
                                        }
                                    }
                                }
                                if (delimiterIndex >= 1) {
                                    val desc = String(frameData, 1, delimiterIndex - 1, charset).trim('\u0000', ' ')
                                    val valOffset = if (encodingByte == 1 || encodingByte == 2) delimiterIndex + 2 else delimiterIndex + 1
                                    val valLen = frameData.size - valOffset
                                    val value = if (valLen > 0) String(frameData, valOffset, valLen, charset).trim('\u0000', ' ') else ""
                                    txxxFrames[desc.uppercase()] = value
                                }
                            }
                        }

                        else -> {
                            if (frameId.startsWith("T") && frameLen > 1) {
                                val text = decodeId3Text(frameData, 0, frameLen)
                                frames[frameId] = text
                            }
                        }
                    }
                }
            }
        }

        val title = frames["TIT2"] ?: file.nameWithoutExtension
        val artist = frames["TPE1"] ?: ""
        val album = frames["TALB"] ?: ""
        val albumArtist = frames["TPE2"] ?: ""
        val trackStr = frames["TRCK"] ?: ""
        val trackParts = trackStr.split("/")
        val trackNum = trackParts.firstOrNull()?.toIntOrNull() ?: 0
        val trackTot = trackParts.getOrNull(1)?.toIntOrNull() ?: 0
        val discStr = frames["TPOS"] ?: ""
        val discParts = discStr.split("/")
        val discNum = discParts.firstOrNull()?.toIntOrNull() ?: 1
        val discTot = discParts.getOrNull(1)?.toIntOrNull() ?: 1
        val date = frames["TDRC"] ?: frames["TYER"] ?: ""
        val genre = frames["TCON"] ?: ""
        val lyricist = frames["TEXT"] ?: ""
        val composer = frames["TCOM"] ?: ""
        val company = frames["TPUB"] ?: ""
        val arranger = txxxFrames["ARRANGER"] ?: ""
        val producer = txxxFrames["PRODUCER"] ?: ""
        val cloudId = txxxFrames["QQMUSIC_SONGMID"] ?: txxxFrames["NETEASE_ID"] ?: ""
        val cloudAlbumMid = txxxFrames["QQMUSIC_ALBUMMID"] ?: ""
        val desc = txxxFrames["DESCRIPTION"] ?: ""
        val gain = txxxFrames["REPLAYGAIN_TRACK_GAIN"]?.substringBefore(" ")?.toDoubleOrNull()
        val peak = txxxFrames["REPLAYGAIN_TRACK_PEAK"]?.toDoubleOrNull()
        val language = frames["TLAN"] ?: ""
        val chorusStart = txxxFrames["CHORUS_START"]?.toLongOrNull() ?: 0L
        val durationMs = frames["TLEN"]?.toLongOrNull() ?: 0L

        val frameInfo = probeMp3AudioFrame(file, id3TagSize)
        val sampleRate = frameInfo.sampleRate
        val bitrateKbps = if (frameInfo.bitrateKbps >
            0
        ) {
            frameInfo.bitrateKbps
        } else if (durationMs > 0) {
            ((fileSize * 8) / durationMs).toInt()
        } else {
            320
        }
        val qualityLevel = when {
            bitrateKbps >= 320 -> "HQ"
            bitrateKbps >= 192 -> "标准"
            else -> "流畅"
        }
        val durationDisplay = AudioParserUtils.formatDuration(durationMs)
        val fileSizeDisplay = AudioParserUtils.formatFileSize(fileSize)

        val techSpec = AudioTechSpec(
            format = "MP3",
            sampleRateHz = sampleRate,
            sampleRateDisplay = if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz" else "%.1f kHz".format(sampleRate / 1000.0),
            bitDepth = 16,
            bitDepthDisplay = "16-bit",
            channels = frameInfo.channels,
            channelsDisplay = frameInfo.channelMode,
            bitrateKbps = bitrateKbps,
            bitrateDisplay = "$bitrateKbps kbps",
            fileSize = fileSize,
            fileSizeDisplay = fileSizeDisplay,
            qualityLevel = qualityLevel,
            durationMs = durationMs,
            durationDisplay = durationDisplay,
            tagVersion = "ID3v2.3",
            filePath = file.absolutePath,
        )

        val specChip = "MP3"

        return AudioMetadata(
            filePath = file.absolutePath,
            fileName = fileName,
            fileSize = fileSize,
            lastModified = lastModified,
            format = "MP3",
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
            replayGainTrackGain = gain,
            replayGainTrackPeak = peak,
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

    private fun decodeId3Text(data: ByteArray, offset: Int, length: Int): String {
        if (length <= 1 || offset >= data.size) return ""
        val encodingByte = data[offset].toInt() and 0xFF
        val textOffset = offset + 1
        val textLen = length - 1
        val charset = AudioParserUtils.charsetForEncoding(encodingByte)
        return try {
            String(data, textOffset, textLen, charset).trim('\u0000', ' ')
        } catch (_: Exception) {
            String(data, textOffset, textLen, Charsets.UTF_8).trim('\u0000', ' ')
        }
    }

    private data class Mp3FrameInfo(
        val sampleRate: Int = 44100,
        val bitrateKbps: Int = 320,
        val channels: Int = 2,
        val channelMode: String = "立体声 (2.0 ch)",
    )

    private fun probeMp3AudioFrame(file: File, tagSize: Int): Mp3FrameInfo {
        return try {
            FileInputStream(file).use { fis ->
                val startOffset = if (tagSize > 0) tagSize + 10L else 0L
                AudioParserUtils.seekSkip(fis, startOffset)
                val buffer = ByteArray(4096)
                val read = fis.read(buffer)
                if (read >= 4) {
                    var foundOffset = -1
                    for (i in 0 until read - 3) {
                        val b0 = buffer[i].toInt() and 0xFF
                        val b1 = buffer[i + 1].toInt() and 0xFF
                        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                            foundOffset = i
                            break
                        }
                    }
                    if (foundOffset >= 0) {
                        val b1 = buffer[foundOffset + 1].toInt() and 0xFF
                        val b2 = buffer[foundOffset + 2].toInt() and 0xFF
                        val b3 = buffer[foundOffset + 3].toInt() and 0xFF

                        // 解析 MPEG 帧头：版本、比特率索引、采样率索引与声道模式
                        val mpegVer = (b1 ushr 3) and 0x03
                        val bitrateIdx = (b2 ushr 4) and 0x0F
                        val sampleRateIdx = (b2 ushr 2) and 0x03
                        val channelModeIdx = (b3 ushr 6) and 0x03

                        val sampleRate = if (mpegVer == 3) {
                            when (sampleRateIdx) {
                                0 -> 44100
                                1 -> 48000
                                2 -> 32000
                                else -> 44100
                            }
                        } else if (mpegVer == 2) {
                            when (sampleRateIdx) {
                                0 -> 22050
                                1 -> 24000
                                2 -> 16000
                                else -> 22050
                            }
                        } else {
                            44100
                        }

                        val bitrateTable = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
                        val bitrate = bitrateTable.getOrElse(bitrateIdx) { 320 }.let { if (it == 0) 320 else it }

                        val (channels, channelMode) = when (channelModeIdx) {
                            3 -> 1 to "单声道 (1.0 ch)"
                            else -> 2 to "立体声 (2.0 ch)"
                        }
                        return Mp3FrameInfo(sampleRate, bitrate, channels, channelMode)
                    }
                }
                Mp3FrameInfo()
            }
        } catch (_: Exception) {
            Mp3FrameInfo()
        }
    }
}
