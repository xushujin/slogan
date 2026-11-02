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

    /** 当前文本是否含中文，用于决定失败提示要不要提「换个中文音色」。 */
    @Volatile
    private var activeTextNeedsChinese: Boolean = false

    /** 最近一次成功应用的具体音色；null 表示当前处于「系统默认」状态。 */
    @Volatile
    private var appliedVoiceName: String? = null

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
            .sortedWith(
                compareByDescending<VoiceOption> { TextScript.isChineseLocaleTag(it.localeTag) }
                    .thenBy { it.label },
            )
    }

    suspend fun synthesize(request: SpeechRequest, target: File) {
        withContext(Dispatchers.Main.immediate) {
            try {
                synthesizeOnce(prepareEngine(request), request, target)
            } catch (error: SpeechSynthesisException) {
                // 「系统默认」在部分厂商引擎上根本用不了（默认音色不可合成）。
                // 与其让用户卡在「默认播不了，又不知道该选哪个」，退到引擎第一个音色再试一次。
                if (request.voiceName != null) throw error
                val fallback = currentFirstVoiceName() ?: throw error
                resetEngine()
                val fresh = obtainEngine() ?: throw error
                applyVoice(fresh, fallback)
                appliedVoiceName = fallback
                synthesizeOnce(fresh, request, target)
            }
        }
    }

    private suspend fun synthesizeOnce(tts: TextToSpeech, request: SpeechRequest, target: File) {
        val voiceLabel = request.voiceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
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
        activeTextNeedsChinese = TextScript.containsChinese(request.text)
        // 传空 Bundle 即可：setVoice 成功后音色已经写进 mParams，
        // 平台会把 mParams 原样带给引擎，不需要（也没有公开常量能）再传一次。
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

    /**
     * 准备引擎并应用音色。
     *
     * 关键点：`mParams` 里的 voice 一旦写入就没有公开 API 能清除，
     * 所以「从具体音色切回系统默认」必须重建引擎，否则会一直念上一个音色。
     */
    private suspend fun prepareEngine(request: SpeechRequest): TextToSpeech {
        val voiceName = request.voiceName
        if (voiceName == null && appliedVoiceName != null) {
            resetEngine()
            appliedVoiceName = null
        }

        val tts = obtainEngine() ?: throw SpeechSynthesisException(
            "系统语音引擎不可用。请先在系统设置中安装并启用「文字转语音」引擎。",
        )

        if (voiceName == null) {
            // 系统默认：不设 voice，只确保引擎切到中文；设不了就直接说清楚。
            if (TextScript.containsChinese(request.text)) {
                val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    throw SpeechSynthesisException(
                        "当前语音引擎没有中文语音数据，请在系统设置中安装中文语音包或更换引擎",
                    )
                }
            }
        } else {
            applyVoice(tts, voiceName)
            appliedVoiceName = voiceName
        }
        return tts
    }

    private fun currentFirstVoiceName(): String? =
        runCatching { engine?.voices }.getOrNull()?.firstOrNull()?.name?.takeIf { it.isNotBlank() }

    private fun resetEngine() {
        pendingUtterances.values.forEach { it.cancel() }
        pendingUtterances.clear()
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    fun shutdown() = resetEngine()

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

    private fun applyVoice(tts: TextToSpeech, voiceName: String) {
        val label = voiceName.substringAfterLast('/')
        val voices = runCatching { tts.voices }.getOrNull()
            ?: throw SpeechSynthesisException("读取不到系统音色列表，请重启应用后再试")

        val target = voices.firstOrNull { it.name == voiceName }
            ?: throw SpeechSynthesisException("系统里已找不到音色「$label」，请重新选择")

        val result = try {
            tts.setVoice(target)
        } catch (error: RuntimeException) {
            throw SpeechSynthesisException("无法切换到音色「$label」", error)
        }
        if (result != TextToSpeech.SUCCESS) {
            throw SpeechSynthesisException("系统拒绝使用音色「$label」（错误码 $result）")
        }

        // setVoice 返回 SUCCESS 不代表真的换掉了。部分引擎会「收下请求但不动」，
        // 结果就是界面显示已选中、听起来却还是同一个声音。这里回读一次戳穿它。
        val applied = runCatching { tts.voice }.getOrNull()
        if (applied != null && applied.name != target.name) {
            throw SpeechSynthesisException(
                "系统没有真正切换到音色「$label」，当前仍是「${applied.name.substringAfterLast('/')}」。" +
                    "这台设备的语音引擎可能不支持多音色，请改用「系统默认」。",
            )
        }
    }

    /** 非默认音色且文本含中文时补一句提示，避免用户对着英文音色反复试中文。 */
    private val voiceHint: String
        get() {
            if (!activeTextNeedsChinese) return ""
            val label = activeVoiceLabel ?: return "。这段文字是中文，请确认所选音色支持中文"
            return "。当前音色「$label」可能不支持中文，请换一个音色"
        }

    private companion object {
        const val INIT_TIMEOUT_MS = 10_000L
        const val SYNTHESIS_TIMEOUT_MS = 30_000L
    }
}
