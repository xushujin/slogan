package com.slogan.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class LoudnessProcessorTest {

    private fun sine(
        amplitude: Float,
        frames: Int = 44100,
        sampleRate: Int = 44100,
        channels: Int = 1,
    ): PcmAudio {
        val samples = ShortArray(frames * channels) { index ->
            val frame = index / channels
            (amplitude * 32767f * sin(2.0 * PI * 220.0 * frame / sampleRate)).roundToInt().toShort()
        }
        return PcmAudio(sampleRate, channels, samples)
    }

    @Test
    fun normalizesQuietSpeechToTargetPeak() {
        val input = sine(amplitude = 0.2f)
        val output = LoudnessProcessor.process(input, LoudnessProcessor.Config.Transparent)

        val peak = output.samples.maxOf { abs(it.toInt()) }
        val ratio = peak / 32767.0
        assertTrue("归一化后峰值应接近 0.89，实际 $ratio", ratio in 0.85..0.91)
        assertTrue("归一化后应显著大于原始峰值", peak > 25_000)
    }

    @Test
    fun limiterPreventsHardClipping() {
        val input = sine(amplitude = 0.99f)
        val output = LoudnessProcessor.process(input, LoudnessProcessor.Config.Megaphone)

        val peak = output.samples.maxOf { abs(it.toInt()) }
        assertTrue("软限幅后不应出现满刻度硬削顶", peak < 32767)
        assertTrue("扩音模式仍应保持足够响度，实际 $peak", peak > 27_000)
        assertEquals(0, output.samples.count { it == Short.MAX_VALUE || it == Short.MIN_VALUE })
    }

    @Test
    fun fadesEdgesToAvoidLoopClicks() {
        val input = sine(amplitude = 0.8f)
        val output = LoudnessProcessor.process(input, LoudnessProcessor.Config.Transparent)

        assertEquals(0, output.samples.first().toInt())
        assertEquals(0, output.samples.last().toInt())
        val firstTenMs = 441
        val middlePeak = output.samples
            .copyOfRange(output.samples.size / 3, output.samples.size * 2 / 3)
            .maxOf { abs(it.toInt()) }
        assertTrue("中段不应被淡入淡出影响，实际峰值 $middlePeak", middlePeak > 25_000)
        assertTrue("淡入区间内应存在非零采样", output.samples[firstTenMs].toInt() != 0)
    }

    @Test
    fun keepsSilenceSilent() {
        val silence = PcmAudio(16000, 1, ShortArray(1600))
        val output = LoudnessProcessor.process(silence)

        assertTrue(output.samples.all { it.toInt() == 0 })
    }

    @Test
    fun preservesStereoLayout() {
        val input = sine(amplitude = 0.4f, frames = 8000, channels = 2)
        val output = LoudnessProcessor.process(input)

        assertEquals(2, output.channels)
        assertEquals(input.samples.size, output.samples.size)
        assertEquals(input.sampleRate, output.sampleRate)
    }

    @Test
    fun rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            LoudnessProcessor.Config(targetPeak = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LoudnessProcessor.Config(limiterThreshold = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LoudnessProcessor.Config(fadeMs = -1)
        }
    }
}
