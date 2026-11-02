package com.slogan.app.core.playback

import com.slogan.app.core.audio.LoudnessProcessor
import com.slogan.app.core.audio.WavCodec
import com.slogan.app.core.tts.SpeechRequest
import com.slogan.app.core.tts.SpeechSynthesizer
import com.slogan.app.core.tts.SynthesisCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 播放引擎：进程内唯一，UI 与前台服务共用同一个实例。
 *
 * 并发模型：
 * - 所有 start/stop 串行通过 [controlMutex]，新会话启动前会 cancelAndJoin 旧会话，
 *   保证任意时刻最多只有一个会话、一个 AudioTrack。
 * - 合成只发生一次（缓存未命中时），循环播放复用内存中的 PCM。
 */
class PlaybackEngine(
    private val scope: CoroutineScope,
    private val synthesizer: SpeechSynthesizer,
    private val cache: SynthesisCache,
    private val player: PcmPlayer,
    private val focusManager: AudioFocusManager,
    private val mutableState: MutableStateFlow<PlaybackState>,
) {

    val state: StateFlow<PlaybackState> = mutableState

    private val controlMutex = Mutex()
    private var session: Job? = null

    @Volatile
    private var currentVolume: Float = 0.8f

    @Volatile
    private var ducked: Boolean = false

    fun start(request: PlaybackRequest) {
        scope.launch {
            controlMutex.withLock {
                session?.cancelAndJoin()
                session = null
                player.release()

                val settings = request.settings.normalized()
                currentVolume = settings.volume
                ducked = false
                mutableState.value = PlaybackState.Preparing(request.title)
                focusManager.request()
                session = scope.launch { runSession(request.copy(settings = settings)) }
            }
        }
    }

    fun stop() {
        scope.launch {
            controlMutex.withLock {
                session?.cancelAndJoin()
                session = null
                player.release()
                focusManager.abandon()
                if (mutableState.value !is PlaybackState.Failed) {
                    mutableState.value = PlaybackState.Idle
                }
            }
        }
    }

    /** 播放中实时调整音量，不重新合成、不重建 AudioTrack。 */
    fun updateVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        player.setVolume(if (ducked) currentVolume * DUCK_FACTOR else currentVolume)
    }

    fun setDucked(value: Boolean) {
        ducked = value
        player.setVolume(if (value) currentVolume * DUCK_FACTOR else currentVolume)
    }

    private suspend fun runSession(request: PlaybackRequest) {
        val settings = request.settings
        try {
            val speechRequest = SpeechRequest.from(request.text, settings)
            val file = cache.obtain(speechRequest) { temporary ->
                synthesizer.synthesize(speechRequest, temporary)
            }

            val raw = withContext(Dispatchers.IO) { WavCodec.decode(file) }
            val processed = withContext(Dispatchers.Default) {
                LoudnessProcessor.process(
                    input = raw,
                    config = if (settings.boost) {
                        LoudnessProcessor.Config.Megaphone
                    } else {
                        LoudnessProcessor.Config.Transparent
                    },
                )
            }

            val timeline = PlaybackTimeline(
                clipDurationMs = processed.durationMs,
                intervalMs = settings.intervalMs.toLong(),
                repeatCount = settings.repeatCount,
            )
            val total = if (timeline.isInfinite) INFINITE_LABEL else timeline.plannedIterations

            player.open(processed)
            player.setVolume(currentVolume)

            val context = currentCoroutineContext()
            var iteration = 0
            var lastReported = 0f

            while (context.isActive) {
                mutableState.value = PlaybackState.Playing(iteration, total, 0f)
                lastReported = 0f
                player.playOnce(
                    audio = processed,
                    isActive = { context.isActive },
                    onProgress = { progress ->
                        if (progress - lastReported >= PROGRESS_STEP || progress >= 1f) {
                            lastReported = progress
                            mutableState.value = PlaybackState.Playing(iteration, total, progress)
                        }
                    },
                )

                if (timeline.isLastIteration(iteration)) break

                val gap = timeline.gapAfter(iteration)
                if (gap > 0L) {
                    mutableState.value = PlaybackState.Waiting(iteration, total, gap)
                    delay(gap)
                }
                iteration += 1
            }

            if (context.isActive) {
                mutableState.value = PlaybackState.Idle
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = PlaybackState.Failed(error.message ?: "播放失败，请重试")
        } finally {
            player.release()
            focusManager.abandon()
        }
    }

    private companion object {
        const val DUCK_FACTOR = 0.2f
        const val PROGRESS_STEP = 0.05f
        const val INFINITE_LABEL = -1
    }
}
