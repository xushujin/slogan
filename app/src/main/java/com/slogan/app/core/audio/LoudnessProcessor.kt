package com.slogan.app.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * 扩音用的响度整形：去直流 → 峰值归一化 → 软限幅 → 首尾淡入淡出。
 *
 * 为什么不是简单“乘一个音量系数”：
 * 1. TTS 引擎输出的电平差异很大，有的只有 -18 dBFS，直接播放会被环境噪声盖住；
 *    归一化把每一段语音都推到接近满刻度，这是扩音器“喊得响”的前提。
 * 2. 单纯放大必然削顶，削顶产生方波谐波，听感更吵但响度反而上不去；
 *    软限幅在阈值以上用 tanh 压缩，把峰值压在 1.0 以内。
 * 3. 循环播放时首尾如果有非零采样，每轮都会“咔哒”一声；淡入淡出消除接缝。
 *
 * 实现上分两遍直接处理 ShortArray，不额外分配等长的 Float 缓冲，
 * 长文本（500 字，可能对应数分钟音频）不会因为中间缓冲把内存翻倍。
 */
object LoudnessProcessor {

    private const val SILENCE_PEAK = 3f
    private const val FULL_SCALE = 32767f

    data class Config(
        /** 归一化目标峰值，0~1。0.89 约等于 -1 dBFS。 */
        val targetPeak: Float = 0.89f,
        /** 最大允许增益，避免把底噪一起放大。 */
        val maxGainDb: Float = 18f,
        /** 软限幅起始阈值，越小压缩越明显、平均响度越高。 */
        val limiterThreshold: Float = 0.85f,
        /** 首尾淡入淡出时长。 */
        val fadeMs: Int = 12,
    ) {
        init {
            require(targetPeak > 0f && targetPeak <= 1f) { "targetPeak 必须位于 (0, 1]" }
            require(limiterThreshold > 0f && limiterThreshold < 1f) { "limiterThreshold 必须位于 (0, 1)" }
            require(fadeMs >= 0) { "fadeMs 不能为负" }
        }

        companion object {
            /** 保真优先：只做轻度归一化。 */
            val Transparent = Config(targetPeak = 0.89f, maxGainDb = 24f, limiterThreshold = 0.85f, fadeMs = 12)

            /** 扩音优先：更高增益、更低阈值，平均响度更大。 */
            val Megaphone = Config(targetPeak = 0.99f, maxGainDb = 24f, limiterThreshold = 0.6f, fadeMs = 8)
        }
    }

    fun process(input: PcmAudio, config: Config = Config.Transparent): PcmAudio {
        val samples = input.samples
        val sampleCount = samples.size
        if (sampleCount == 0) return input

        // 第一遍：直流偏置与峰值。部分 TTS 引擎输出存在直流偏置，会白白吃掉动态余量。
        var sum = 0.0
        for (sample in samples) sum += sample.toInt()
        val dcOffset = (sum / sampleCount).toFloat()

        var peak = 0f
        for (sample in samples) {
            val magnitude = abs(sample - dcOffset)
            if (magnitude > peak) peak = magnitude
        }
        if (peak <= SILENCE_PEAK) {
            return PcmAudio(input.sampleRate, input.channels, ShortArray(sampleCount))
        }

        val maxGain = 10f.pow(config.maxGainDb / 20f)
        val gain = ((config.targetPeak * FULL_SCALE) / peak).coerceAtMost(maxGain)

        // 第二遍：逐样本归一化 + 限幅 + 淡入淡出，直接写目标数组。
        val output = ShortArray(sampleCount)
        val totalFrames = sampleCount / input.channels
        val fadeFrames = ((input.sampleRate.toLong() * config.fadeMs) / 1000L)
            .toInt()
            .coerceAtMost(totalFrames / 2)
        val fadeCurve = FloatArray(fadeFrames.coerceAtLeast(0)) { index ->
            (0.5 - 0.5 * cos(PI * index / fadeFrames.coerceAtLeast(1))).toFloat()
        }

        for (index in 0 until sampleCount) {
            val frame = index / input.channels
            val fadeGain = when {
                fadeFrames <= 0 -> 1f
                frame < fadeFrames -> fadeCurve[frame]
                frame >= totalFrames - fadeFrames -> fadeCurve[totalFrames - 1 - frame]
                else -> 1f
            }

            val normalized = ((samples[index] - dcOffset) * gain / 32768f) * fadeGain
            val limited = softLimit(normalized, config.limiterThreshold)
            output[index] = (limited * FULL_SCALE).roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return PcmAudio(input.sampleRate, input.channels, output)
    }

    private fun softLimit(value: Float, threshold: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= threshold) return value
        val headroom = 1f - threshold
        val limited = threshold + headroom * tanh((magnitude - threshold) / headroom)
        return if (value < 0f) -limited else limited
    }
}
