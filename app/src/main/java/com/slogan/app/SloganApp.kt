package com.slogan.app

import android.app.Application
import android.content.Context
import com.slogan.app.core.playback.AudioFocusManager
import com.slogan.app.core.playback.PcmPlayer
import com.slogan.app.core.playback.PlaybackEngine
import com.slogan.app.core.playback.PlaybackState
import com.slogan.app.core.tts.SpeechSynthesizer
import com.slogan.app.core.tts.SynthesisCache
import com.slogan.app.core.update.UpdateManager
import com.slogan.app.data.PresetDao
import com.slogan.app.data.SettingsStore
import com.slogan.app.data.SloganDatabase
import com.slogan.app.data.SynthesisCacheDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 手写依赖容器。项目不引入 DI 框架，所有依赖在这里一次性组装，
 * 保证“同一个能力只有一个实例”（尤其是 TTS 与 AudioTrack）。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val context: Context
        get() = appContext

    val database: SloganDatabase = SloganDatabase(appContext)
    val presetDao: PresetDao = PresetDao(database)
    val settingsStore: SettingsStore = SettingsStore(database)
    val cacheDao: SynthesisCacheDao = SynthesisCacheDao(database)
    val synthesizer: SpeechSynthesizer = SpeechSynthesizer(appContext)
    val synthesisCache: SynthesisCache = SynthesisCache(appContext, cacheDao) { synthesizer.engineSignature() }

    private val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)

    /**
     * 更新比较用的基准版本。
     * debug 构建的 versionName 带 `-debug` 后缀（见 build.gradle.kts），
     * 去掉后才能与 GitHub 标签（`v1.0.0`）正确比较。
     */
    private val updateBaseVersion: String = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    val updateManager: UpdateManager = UpdateManager(
        context = appContext,
        currentVersion = updateBaseVersion,
    )

    val playbackEngine: PlaybackEngine by lazy {
        PlaybackEngine(
            scope = applicationScope,
            synthesizer = synthesizer,
            cache = synthesisCache,
            player = PcmPlayer(),
            focusManager = AudioFocusManager(
                context = appContext,
                onFocusLost = { playbackEngine.stop() },
                onDuckChanged = { ducked -> playbackEngine.setDucked(ducked) },
            ),
            mutableState = playbackState,
        )
    }

    fun warmUp() {
        applicationScope.launch {
            settingsStore.load()
            presetDao.refresh()
        }
    }
}

class SloganApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.warmUp()
    }
}
