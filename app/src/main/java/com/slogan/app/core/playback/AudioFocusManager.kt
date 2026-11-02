package com.slogan.app.core.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * 音频焦点：来电/其它播放器抢占时正确处理，避免“扩音器一直喊”。
 */
class AudioFocusManager(
    context: Context,
    private val onFocusLost: () -> Unit,
    private val onDuckChanged: (Boolean) -> Unit,
) {

    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    fun request(): Boolean {
        val manager = audioManager ?: return false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        onDuckChanged(false)
                        onFocusLost()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> onDuckChanged(true)

                    AudioManager.AUDIOFOCUS_GAIN -> onDuckChanged(false)
                }
            }
            .build()

        focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        val manager = audioManager ?: return
        focusRequest?.let(manager::abandonAudioFocusRequest)
        focusRequest = null
    }
}
