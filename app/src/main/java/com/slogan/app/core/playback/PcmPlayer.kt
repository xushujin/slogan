package com.slogan.app.core.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.slogan.app.core.audio.PcmAudio
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 单实例 AudioTrack 播放器。
 *
 * 设计要点：
 * - 整个会话只创建 **一个** AudioTrack，循环时复用同一个对象，
 *   从结构上杜绝“上一轮还没播完、下一轮又开一个”的重叠。
 * - 每轮开始前 stop + flush，把播放头归零；结束后等待缓冲区真正播完再进入间隔，
 *   否则“间隔”会从写完数据那一刻开始算，实际听感变短。
 * - 音量走 AudioTrack.setVolume，可在播放中实时调整，不需要重新合成。
 */
class PcmPlayer {

    private var track: AudioTrack? = null
    private var volume: Float = 1f

    fun open(audio: PcmAudio) {
        release()

        val channelMask = if (audio.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            throw IllegalStateException("设备不支持 ${audio.sampleRate}Hz / ${audio.channels} 声道的 PCM 播放")
        }
        // 0.5 秒缓冲，兼顾延迟与抗欠载；至少是系统最小缓冲的两倍。
        val bufferSize = max(minBufferSize * 2, audio.sampleRate * audio.channels)

        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw IllegalStateException("AudioTrack 初始化失败，可能是音频输出被占用")
        }

        created.setVolume(volume)
        track = created
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    suspend fun playOnce(
        audio: PcmAudio,
        isActive: () -> Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val player = track ?: throw IllegalStateException("AudioTrack 尚未初始化")
        val totalSamples = audio.samples.size

        player.stop()
        player.flush()
        player.play()
        val baseHead = headPosition(player)

        var offset = 0
        val chunk = CHUNK_FRAMES * audio.channels
        while (offset < totalSamples) {
            if (!isActive()) {
                player.pause()
                player.flush()
                return
            }
            val count = minOf(chunk, totalSamples - offset)
            val written = player.write(audio.samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                throw IllegalStateException("写入音频缓冲失败（错误码 $written）")
            }
            if (written == 0) {
                // 理论上阻塞写不会返回 0；真遇到时让出 CPU，避免忙等。
                delay(WRITE_RETRY_DELAY_MS)
                continue
            }
            offset += written
            onProgress(offset.toFloat() / totalSamples)
        }

        // 等待缓冲区真正播完，避免间隔时间“偷跑”。
        val expectedFrames = audio.frameCount.toLong()
        val deadline = System.currentTimeMillis() + audio.durationMs + DRAIN_TIMEOUT_MS
        while (isActive() && playedFrames(player, baseHead) < expectedFrames) {
            if (System.currentTimeMillis() > deadline) break
            delay(DRAIN_POLL_MS)
        }

        player.stop()
        player.flush()
    }

    fun release() {
        val player = track ?: return
        track = null
        runCatching { player.pause() }
        runCatching { player.flush() }
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun headPosition(player: AudioTrack): Long = player.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun playedFrames(player: AudioTrack, baseHead: Long): Long {
        val current = headPosition(player)
        return (current - baseHead + 0x1_0000_0000L) and 0xFFFF_FFFFL
    }

    private companion object {
        const val CHUNK_FRAMES = 2048
        const val DRAIN_POLL_MS = 10L
        const val DRAIN_TIMEOUT_MS = 2_000L
        const val WRITE_RETRY_DELAY_MS = 5L
    }
}
