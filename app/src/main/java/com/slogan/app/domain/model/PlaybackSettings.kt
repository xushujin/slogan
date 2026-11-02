package com.slogan.app.domain.model

/**
 * 全局播放设置。唯一事实来源：数据库 settings 表 → [com.slogan.app.data.SettingsStore]。
 * UI 与播放引擎都只读这一份数据，不存在第二套实现。
 */
data class PlaybackSettings(
    val intervalMs: Int = DEFAULT_INTERVAL_MS,
    /** 0 表示无限循环。 */
    val repeatCount: Int = DEFAULT_REPEAT_COUNT,
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 0.8f,
    val boost: Boolean = true,
    val themeId: String = DEFAULT_THEME_ID,
) {
    fun normalized(): PlaybackSettings = copy(
        intervalMs = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
        repeatCount = repeatCount.coerceIn(0, MAX_REPEAT_COUNT),
        speechRate = speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH),
        volume = volume.coerceIn(0f, 1f),
    )

    companion object {
        const val MIN_INTERVAL_MS = 0
        const val MAX_INTERVAL_MS = 10_000
        const val DEFAULT_INTERVAL_MS = 1_500
        const val MAX_REPEAT_COUNT = 999
        const val DEFAULT_REPEAT_COUNT = 3
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
        const val DEFAULT_THEME_ID = "graphite"

        /** 无限循环在 UI 与存储中的统一表示。 */
        const val INFINITE_REPEAT = 0
    }
}
