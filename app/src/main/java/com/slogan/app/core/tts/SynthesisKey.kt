package com.slogan.app.core.tts

import java.security.MessageDigest

/**
 * 缓存键：同样的文本 + 音色 + 语速 + 音调 + TTS 引擎，才允许复用同一份音频。
 * 纯函数，方便单测覆盖“参数变化必须换键”的行为。
 */
object SynthesisKey {

    fun of(request: SpeechRequest, engineSignature: String): String {
        val payload = buildString {
            append("engine=").append(engineSignature).append('\n')
            append("voice=").append(request.voiceName.orEmpty()).append('\n')
            append("rate=").append(format(request.speechRate)).append('\n')
            append("pitch=").append(format(request.pitch)).append('\n')
            append("text=").append(request.text)
        }
        return sha256(payload)
    }

    private fun format(value: Float): String = String.format(java.util.Locale.US, "%.3f", value)

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val intValue = byte.toInt() and 0xFF
            if (intValue < 0x10) builder.append('0')
            builder.append(Integer.toHexString(intValue))
        }
        return builder.toString()
    }
}
