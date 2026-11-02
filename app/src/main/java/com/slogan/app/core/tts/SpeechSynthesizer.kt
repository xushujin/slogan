package com.slogan.app.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 系统 TextToSpeech 的唯一封装。
 *
 * 关键点：
 * - 初始化是异步的，用 [CompletableDeferred] 等待，避免“第一次点播放没声音”。
 * - 合成走 `synthesizeToFile`，只落盘一次；播放侧读文件，循环时不再触发合成。
 * - 所有 TTS 调用都在主线程，回调也回到主线程，避免引擎回调线程不确定的问题。
 */
class SpeechSynthesizer(context: Context) {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val utteranceCounter = AtomicInteger(0)

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready: Boolean = false

    @Volatile
    private var initSignal: CompletableDeferred<Boolean>? = null

    /** 默认引擎包名，用于让缓存随引擎切换而失效。 */
    suspend fun engineSignature(): String = obtainEngine()?.defaultEngine ?: "unknown"

    suspend fun isReady(): Boolean = withContext(Dispatchers.Main.immediate) {
        obtainEngine() != null
    }

    suspend fun availableVoices(): List<VoiceOption> = withContext(Dispatchers.Main.immediate) {
        val tts = obtainEngine() ?: return@withContext emptyList()
        val voices: Set<Voice> = tts.voices ?: return@withContext emptyList()
        voices
            .map { voice ->
                VoiceOption(
                    name = voice.name,
                    label = "${voice.locale.displayName} · ${voice.name.substringAfterLast('/')}",
                    localeTag = voice.locale.toLanguageTag(),
                    requiresNetwork = voice.isNetworkConnectionRequired,
                )
            }
            .sortedWith(compareByDescending<VoiceOption> { it.localeTag.startsWith("zh") }.thenBy { it.label })
    }

    suspend fun synthesize(request: SpeechRequest, target: File) {
        val tts = obtainEngine() ?: throw SpeechSynthesisException(
            "系统语音引擎不可用。请先在系统设置中安装并启用「文字转语音」引擎。",
        )
        withContext(Dispatchers.Main.immediate) {
            applyVoice(tts, request.voiceName)
            tts.setSpeechRate(request.speechRate.coerceIn(0.5f, 2.0f))
            tts.setPitch(request.pitch.coerceIn(0.5f, 2.0f))

            target.parentFile?.mkdirs()
            if (target.exists() && !target.delete()) {
                throw SpeechSynthesisException("无法覆盖旧的合成文件：${target.absolutePath}")
            }

            val utteranceId = "slogan-${utteranceCounter.incrementAndGet()}"
            val signal = CompletableDeferred<Unit>()
            pendingUtterances[utteranceId] = signal
            val started = tts.synthesizeToFile(request.text, Bundle.EMPTY, target, utteranceId)
            if (started != TextToSpeech.SUCCESS) {
                pendingUtterances.remove(utteranceId)
                throw SpeechSynthesisException("语音引擎拒绝了合成请求（错误码 $started）")
            }

            try {
                val finished = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
                    try {
                        signal.await()
                        Result.success(Unit)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                }
                when {
                    finished == null -> throw SpeechSynthesisException("语音合成超时，请检查系统语音引擎")
                    finished.isFailure -> throw finished.exceptionOrNull()
                        ?: SpeechSynthesisException("语音合成失败")
                }
            } finally {
                pendingUtterances.remove(utteranceId)
            }
        }
    }

    fun shutdown() {
        pendingUtterances.values.forEach { it.cancel() }
        pendingUtterances.clear()
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private suspend fun obtainEngine(): TextToSpeech? {
        engine?.let { if (ready) return it }
        return withContext(Dispatchers.Main.immediate) {
            initMutex.withLock {
                val current = engine
                if (current != null && ready) return@withLock current

                val signal = CompletableDeferred<Boolean>()
                initSignal = signal
                ready = false

                val created = TextToSpeech(appContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    initSignal?.complete(ready)
                }
                engine = created

                val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) { signal.await() } ?: false
                if (!initialized) {
                    runCatching { created.shutdown() }
                    engine = null
                    ready = false
                    return@withLock null
                }

                created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { pendingUtterances.remove(it)?.complete(Unit) }
                    }

                    // 平台要求实现这个已废弃的抽象方法，运行时优先走带 errorCode 的重载。
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let {
                            pendingUtterances.remove(it)?.completeExceptionally(
                                SpeechSynthesisException("语音合成失败，请更换音色后重试"),
                            )
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let {
                            pendingUtterances.remove(it)?.completeExceptionally(
                                SpeechSynthesisException("语音合成失败（错误码 $errorCode）"),
                            )
                        }
                    }
                })

                applyLanguage(created)
                created
            }
        }
    }

    private fun applyLanguage(tts: TextToSpeech) {
        val preferred = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (preferred == TextToSpeech.LANG_MISSING_DATA || preferred == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        }
    }

    private fun applyVoice(tts: TextToSpeech, voiceName: String?) {
        if (voiceName.isNullOrBlank()) {
            tts.voice = null
            return
        }
        val target = tts.voices?.firstOrNull { it.name == voiceName } ?: return
        runCatching { tts.voice = target }
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 10_000L
        const val SYNTHESIS_TIMEOUT_MS = 30_000L
    }
}
