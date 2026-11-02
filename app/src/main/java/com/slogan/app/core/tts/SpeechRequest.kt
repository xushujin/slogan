package com.slogan.app.core.tts

import com.slogan.app.domain.model.PlaybackSettings

/** 一次合成请求，同时也是缓存键的输入。 */
data class SpeechRequest(
    val text: String,
    val voiceName: String?,
    val speechRate: Float,
    val pitch: Float,
) {
    init {
        require(text.isNotBlank()) { "待合成文本不能为空" }
    }

    companion object {
        fun from(text: String, settings: PlaybackSettings): SpeechRequest = SpeechRequest(
            text = text.trim(),
            voiceName = settings.voiceName,
            speechRate = settings.speechRate,
            pitch = settings.pitch,
        )
    }
}

/** 系统 TTS 引擎里可选的一个音色。 */
data class VoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
)

class SpeechSynthesisException(message: String, cause: Throwable? = null) : Exception(message, cause)
