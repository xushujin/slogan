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

    /** 当前正在合成的音色（用于错误提示），合成结束后清空。 */
    @Volatile
    private var activeVoiceLabel: String? = null

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
                    label = buildString {
                        append(voice.locale.displayName)
                        append(" · ")
                        append(voice.name.substringAfterLast('/'))
                        if (voice.isNetworkConnectionRequired) append("（需联网）")
                    },
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
        val voiceLabel = request.voiceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        withContext(Dispatchers.Main.immediate) {
            val applied = applyVoice(tts, request.voiceName)
            // 中文文本配非中文音色，很多引擎会直接报错，个别厂商引擎甚至会在原生层崩掉。
            // 与其把中文丢给英文音色，不如在合成前就给出可执行的提示。
            if (applied != null &&
                applied.locale.language != TextScript.CHINESE_LANGUAGE &&
                TextScript.containsChinese(request.text)
            ) {
                throw SpeechSynthesisException(
                    "音色「${applied.name.substringAfterLast('/')}」不是中文音色，无法朗读中文。" +
                        "请改用中文音色或「系统默认」。",
                )
            }
            tts.setSpeechRate(request.speechRate.coerceIn(0.5f, 2.0f))
            tts.setPitch(request.pitch.coerceIn(0.5f, 2.0f))

            target.parentFile?.mkdirs()
            if (target.exists() && !target.delete()) {
                throw SpeechSynthesisException("无法覆盖旧的合成文件：${target.absolutePath}")
            }

            val utteranceId = "slogan-${utteranceCounter.incrementAndGet()}"
            val signal = CompletableDeferred<Unit>()
            pendingUtterances[utteranceId] = signal
            activeVoiceLabel = voiceLabel
            val started = tts.synthesizeToFile(request.text, Bundle.EMPTY, target, utteranceId)
            if (started != TextToSpeech.SUCCESS) {
                pendingUtterances.remove(utteranceId)
                activeVoiceLabel = null
                throw SpeechSynthesisException("语音引擎拒绝了合成请求（错误码 $started）$voiceHint")
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
                    finished == null -> throw SpeechSynthesisException("语音合成超时$voiceHint")
                    finished.isFailure -> throw finished.exceptionOrNull()
                        ?: SpeechSynthesisException("语音合成失败$voiceHint")
                }
            } finally {
                pendingUtterances.remove(utteranceId)
                activeVoiceLabel = null
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
                                SpeechSynthesisException("语音合成失败$voiceHint"),
                            )
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let {
                            pendingUtterances.remove(it)?.completeExceptionally(
                                SpeechSynthesisException("语音合成失败（错误码 $errorCode）$voiceHint"),
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

    /**
     * 应用音色，返回实际生效的音色（系统默认时返回引擎默认音色）。
     *
     * **不能**用 `tts.voice = null` 表示「系统默认」：平台实现里 `setVoice` 会直接
     * 对传入对象调用 `voice.getName()`，传 null 必然抛 NPE。要回到默认音色，
     * 必须显式设置引擎给出的 `defaultVoice`。
     *
     * 设置结果也不再静默吞掉——`setVoice` 返回非 SUCCESS 时抛错，
     * 否则会出现「选了音色但听起来没变」却毫无提示的情况。
     */
    private fun applyVoice(tts: TextToSpeech, voiceName: String?): Voice? {
        val voices = runCatching { tts.voices }.getOrNull()

        if (voiceName.isNullOrBlank()) {
            val fallback = runCatching { tts.defaultVoice }.getOrNull() ?: return null
            runCatching { tts.setVoice(fallback) }
            return fallback
        }

        // 引擎尚未给出音色列表时不做判断，交给后续合成流程报错。
        if (voices == null) return null

        val target = voices.firstOrNull { it.name == voiceName }
            ?: throw SpeechSynthesisException(
                "系统里已找不到音色「${voiceName.substringAfterLast('/')}」，请重新选择",
            )
        val result = try {
            tts.setVoice(target)
        } catch (error: RuntimeException) {
            throw SpeechSynthesisException(
                "无法切换到音色「${target.name.substringAfterLast('/')}」",
                error,
            )
        }
        if (result != TextToSpeech.SUCCESS) {
            throw SpeechSynthesisException(
                "系统拒绝使用音色「${target.name.substringAfterLast('/')}」（错误码 $result）",
            )
        }
        return target
    }

    /** 非默认音色失败时补一句提示，避免用户对着英文音色反复试中文。 */
    private val voiceHint: String
        get() = activeVoiceLabel?.let { "。当前音色「$it」可能与文本语言不匹配，请换一个音色" }.orEmpty()

    private companion object {
        const val INIT_TIMEOUT_MS = 10_000L
        const val SYNTHESIS_TIMEOUT_MS = 30_000L
    }
}
