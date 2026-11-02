package com.slogan.app.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 用真实文件验证 WAV 解析，而不是只验证“能编译”。
 *
 * tone_stereo_44100.wav / tone_mono_24000.wav 由 ffmpeg 生成，含 LIST/INFO 元数据块，
 * 其中 IART 块长度为 7（奇数），可覆盖真实文件里的对齐填充逻辑。
 * 其余文件用于覆盖 WAVE_FORMAT_EXTENSIBLE、8 bit、float、data 长度非法等边界。
 */
class WavCodecTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/wav/$name")) { "缺少测试资源: $name" }
            .use { it.readBytes() }

    @Test
    fun decodesRealFfmpegStereoFileWithListChunk() {
        val audio = WavCodec.decode(fixture("tone_stereo_44100.wav"))

        assertEquals(44100, audio.sampleRate)
        assertEquals(2, audio.channels)
        assertEquals(44100, audio.frameCount)
        assertEquals(1000L, audio.durationMs)
        assertEquals(88200, audio.samples.size)

        // ffmpeg 的 sine 源左右声道完全一致，可以同时验证声道交织顺序。
        for (frame in 0 until audio.frameCount step 97) {
            assertEquals(audio.samples[frame * 2], audio.samples[frame * 2 + 1])
        }

        // ffmpeg 的 sine 源默认电平是 -21.1 dBFS（volumedetect 实测），
        // 解码峰值必须与之吻合，才能证明样本没有被错位或缩放。
        val peak = audio.samples.maxOf { abs(it.toInt()) }
        assertTrue("峰值应与 ffmpeg volumedetect 的 -21.1 dBFS 一致，实际 $peak", peak in 2_800..3_000)

        // 440 Hz 正弦在 1 秒内应有约 880 次过零，可反证字节序与采样顺序正确。
        var crossings = 0
        for (frame in 1 until audio.frameCount) {
            val previous = audio.samples[(frame - 1) * 2].toInt()
            val current = audio.samples[frame * 2].toInt()
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) crossings++
        }
        assertTrue("过零次数异常: $crossings", crossings in 860..900)
    }

    @Test
    fun decodesRealFfmpegMonoFile() {
        val audio = WavCodec.decode(fixture("tone_mono_24000.wav"))

        assertEquals(24000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(12000, audio.frameCount)
        assertEquals(500L, audio.durationMs)
    }

    @Test
    fun decodesWaveFormatExtensible() {
        val audio = WavCodec.decode(fixture("tone_extensible_mono_16000.wav"))

        assertEquals(16000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(4000, audio.frameCount)
        assertEquals(250L, audio.durationMs)
    }

    @Test
    fun rejectsEightBitPcm() {
        val error = assertThrows(WavFormatException::class.java) {
            WavCodec.decode(fixture("tone_8bit_mono.wav"))
        }
        assertTrue(error.message.orEmpty().contains("16 bit"))
    }

    @Test
    fun rejectsIeeeFloatPcm() {
        val error = assertThrows(WavFormatException::class.java) {
            WavCodec.decode(fixture("tone_float32_mono.wav"))
        }
        assertTrue(error.message.orEmpty().contains("线性 PCM"))
    }

    @Test
    fun clampsIllegalDataChunkSizeToRealBytes() {
        // data 块声明 0xFFFFFFFF，真实文件只有 8000 字节音频数据。
        val audio = WavCodec.decode(fixture("tone_truncated_data.wav"))

        assertEquals(16000, audio.sampleRate)
        assertEquals(4000, audio.frameCount)
    }

    @Test
    fun rejectsNonRiffFile() {
        assertThrows(WavFormatException::class.java) {
            WavCodec.decode(fixture("not_a_wav.bin"))
        }
    }

    @Test
    fun rejectsEmptyDataChunk() {
        val header = ByteArray(44)
        header.writeAscii(0, "RIFF")
        header.writeUInt32Le(4, 36)
        header.writeAscii(8, "WAVE")
        header.writeAscii(12, "fmt ")
        header.writeUInt32Le(16, 16)
        header.writeUInt16Le(20, 1)
        header.writeUInt16Le(22, 1)
        header.writeUInt32Le(24, 16000)
        header.writeUInt32Le(28, 32000)
        header.writeUInt16Le(32, 2)
        header.writeUInt16Le(34, 16)
        header.writeAscii(36, "data")
        header.writeUInt32Le(40, 0)

        assertThrows(WavFormatException::class.java) { WavCodec.decode(header) }
    }

    @Test
    fun encodeDecodeRoundTripKeepsSamples() {
        val samples = ShortArray(4000) { index ->
            ((index * 37) % 60_000 - 30_000).toShort()
        }
        val original = PcmAudio(sampleRate = 32000, channels = 2, samples = samples)

        val encoded = WavCodec.encode(original)

        assertEquals(44 + samples.size * 2, encoded.size)
        assertEquals("RIFF", encoded.asciiAt(0, 4))
        assertEquals("WAVE", encoded.asciiAt(8, 4))
        assertEquals((encoded.size - 8).toLong(), encoded.readUInt32Le(4))
        assertEquals(2L, encoded.readUInt16Le(22))
        assertEquals(32000L, encoded.readUInt32Le(24))
        assertEquals(samples.size.toLong() * 2, encoded.readUInt32Le(40))

        val decoded = WavCodec.decode(encoded)
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertEquals(original.channels, decoded.channels)
        assertArrayEquals(samples, decoded.samples)
    }
}
