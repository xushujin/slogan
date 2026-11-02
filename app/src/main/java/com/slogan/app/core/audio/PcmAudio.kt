package com.slogan.app.core.audio

/**
 * 内存中的一段 16 bit 单声道/立体声 PCM。
 *
 * 应用只在“合成一次”之后持有 PCM，循环播放直接复用这块数据，
 * 因此不会出现“每轮循环重新合成”的问题。
 */
class PcmAudio(
    val sampleRate: Int,
    val channels: Int,
    val samples: ShortArray,
) {
    init {
        require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) { "不支持的采样率: $sampleRate" }
        require(channels in 1..2) { "不支持的声道数: $channels" }
        require(samples.size % channels == 0) { "PCM 数据长度必须是声道数的整数倍" }
    }

    val frameCount: Int
        get() = samples.size / channels

    val durationMs: Long
        get() = frameCount * 1000L / sampleRate

    fun copy(): PcmAudio = PcmAudio(sampleRate, channels, samples.copyOf())

    companion object {
        const val MIN_SAMPLE_RATE = 8_000
        const val MAX_SAMPLE_RATE = 192_000
    }
}
