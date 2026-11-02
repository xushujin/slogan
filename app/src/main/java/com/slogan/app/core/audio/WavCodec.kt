package com.slogan.app.core.audio

import java.io.File

/** WAV 文件不符合“16 bit 线性 PCM”约定时抛出。 */
class WavFormatException(message: String) : Exception(message)

/**
 * 自研的最小 WAV 编解码器。
 *
 * 只支持 Android TTS `synthesizeToFile` 实际产出的格式族：RIFF/WAVE、
 * 16 bit 线性 PCM、单/双声道；同时容忍真实文件里常见的附加块
 * （LIST/fact/JUNK）、奇数长度块的对齐字节，以及个别编码器写出的
 * 非法/溢出的 data 块长度。
 *
 * 不依赖任何第三方音频库。
 */
object WavCodec {

    private const val HEADER_SIZE = 44
    private const val FORMAT_PCM = 1
    private const val FORMAT_EXTENSIBLE = 0xFFFE
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024

    fun decode(file: File): PcmAudio {
        require(file.isFile) { "WAV 文件不存在: ${file.absolutePath}" }
        require(file.length() <= MAX_FILE_BYTES) { "WAV 文件过大: ${file.length()} 字节" }
        return decode(file.readBytes())
    }

    fun decode(bytes: ByteArray): PcmAudio {
        if (bytes.size < 12) throw WavFormatException("文件过短，不是合法的 RIFF 容器")

        if (!bytes.matchesAscii(0, "RIFF")) throw WavFormatException("缺少 RIFF 标识")
        if (!bytes.matchesAscii(8, "WAVE")) throw WavFormatException("不是 WAVE 容器")

        var format: FormatChunk? = null
        var dataOffset = -1
        var dataSize = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.asciiAt(offset, 4)
            val declaredSize = bytes.readUInt32Le(offset + 4)
            val bodyOffset = offset + 8
            val remaining = bytes.size - bodyOffset
            // 部分编码器会写出 0xFFFFFFFF 或超出文件长度的 data 长度，这里按实际剩余字节收敛。
            val bodySize = declaredSize.coerceAtMost(remaining.toLong()).toInt()

            when (chunkId) {
                "fmt " -> format = parseFormat(bytes, bodyOffset, bodySize)
                "data" -> {
                    dataOffset = bodyOffset
                    dataSize = bodySize
                }
            }

            // 块长度为奇数时存在 1 字节对齐填充。
            val advance = 8L + declaredSize + (declaredSize and 1L)
            val next = offset + advance
            offset = if (next <= offset || next > Int.MAX_VALUE) bytes.size else next.toInt()
        }

        val parsedFormat = format ?: throw WavFormatException("缺少 fmt 块")
        if (dataOffset < 0) throw WavFormatException("缺少 data 块")
        if (parsedFormat.audioFormat != FORMAT_PCM) {
            throw WavFormatException("只支持线性 PCM，当前格式: ${parsedFormat.audioFormat}")
        }
        if (parsedFormat.bitsPerSample != 16) {
            throw WavFormatException("只支持 16 bit 采样，当前: ${parsedFormat.bitsPerSample} bit")
        }

        val frameBytes = parsedFormat.channels * 2
        val usableBytes = dataSize - (dataSize % frameBytes)
        if (usableBytes <= 0) throw WavFormatException("data 块为空")

        val samples = ShortArray(usableBytes / 2)
        var sampleIndex = 0
        var cursor = dataOffset
        val end = dataOffset + usableBytes
        while (cursor + 1 < end) {
            samples[sampleIndex++] = bytes.readInt16Le(cursor)
            cursor += 2
        }

        return PcmAudio(
            sampleRate = parsedFormat.sampleRate,
            channels = parsedFormat.channels,
            samples = samples,
        )
    }

    fun encode(audio: PcmAudio): ByteArray {
        val dataBytes = audio.samples.size * 2
        val out = ByteArray(HEADER_SIZE + dataBytes)

        out.writeAscii(0, "RIFF")
        out.writeUInt32Le(4, (HEADER_SIZE - 8 + dataBytes).toLong())
        out.writeAscii(8, "WAVE")
        out.writeAscii(12, "fmt ")
        out.writeUInt32Le(16, 16L)
        out.writeUInt16Le(20, FORMAT_PCM)
        out.writeUInt16Le(22, audio.channels)
        out.writeUInt32Le(24, audio.sampleRate.toLong())
        out.writeUInt32Le(28, (audio.sampleRate * audio.channels * 2).toLong())
        out.writeUInt16Le(32, audio.channels * 2)
        out.writeUInt16Le(34, 16)
        out.writeAscii(36, "data")
        out.writeUInt32Le(40, dataBytes.toLong())

        var cursor = HEADER_SIZE
        for (sample in audio.samples) {
            val value = sample.toInt()
            out[cursor] = (value and 0xFF).toByte()
            out[cursor + 1] = ((value shr 8) and 0xFF).toByte()
            cursor += 2
        }
        return out
    }

    private fun parseFormat(bytes: ByteArray, offset: Int, size: Int): FormatChunk {
        if (size < 16) throw WavFormatException("fmt 块长度不足: $size")
        var audioFormat = bytes.readUInt16Le(offset).toInt()
        val channels = bytes.readUInt16Le(offset + 2).toInt()
        val sampleRate = bytes.readUInt32Le(offset + 4)
        val bitsPerSample = bytes.readUInt16Le(offset + 14)

        if (audioFormat == FORMAT_EXTENSIBLE) {
            if (size < 40) throw WavFormatException("WAVE_FORMAT_EXTENSIBLE 的 fmt 块长度不足: $size")
            // SubFormat GUID 的前两个字节即真实格式码。
            audioFormat = bytes.readUInt16Le(offset + 24).toInt()
        }

        if (channels !in 1..2) throw WavFormatException("只支持 1~2 声道，当前: $channels")
        if (sampleRate < PcmAudio.MIN_SAMPLE_RATE || sampleRate > PcmAudio.MAX_SAMPLE_RATE) {
            throw WavFormatException("采样率超出范围: $sampleRate")
        }

        return FormatChunk(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate.toInt(),
            bitsPerSample = bitsPerSample.toInt(),
        )
    }

    private data class FormatChunk(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
    )
}

internal fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
    if (offset + text.length > size) return false
    for (index in text.indices) {
        if (this[offset + index].toInt() != text[index].code) return false
    }
    return true
}

internal fun ByteArray.asciiAt(offset: Int, length: Int): String {
    val chars = CharArray(length)
    for (index in 0 until length) {
        chars[index] = (this[offset + index].toInt() and 0xFF).toChar()
    }
    return String(chars)
}

internal fun ByteArray.readUInt16Le(offset: Int): Long {
    val low = this[offset].toInt() and 0xFF
    val high = this[offset + 1].toInt() and 0xFF
    return (low or (high shl 8)).toLong()
}

internal fun ByteArray.readInt16Le(offset: Int): Short {
    val low = this[offset].toInt() and 0xFF
    val high = this[offset + 1].toInt()
    return ((high shl 8) or low).toShort()
}

internal fun ByteArray.readUInt32Le(offset: Int): Long {
    var result = 0L
    for (index in 0 until 4) {
        result = result or ((this[offset + index].toLong() and 0xFF) shl (8 * index))
    }
    return result
}

internal fun ByteArray.writeAscii(offset: Int, text: String) {
    for (index in text.indices) {
        this[offset + index] = text[index].code.toByte()
    }
}

internal fun ByteArray.writeUInt16Le(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

internal fun ByteArray.writeUInt32Le(offset: Int, value: Long) {
    for (index in 0 until 4) {
        this[offset + index] = ((value shr (8 * index)) and 0xFF).toByte()
    }
}
