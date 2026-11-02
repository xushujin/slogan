package com.slogan.app.ui

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slogan.app.AppContainer
import com.slogan.app.BuildConfig
import com.slogan.app.core.playback.PlaybackRequest
import com.slogan.app.core.playback.PlaybackState
import com.slogan.app.core.tts.VoiceOption
import com.slogan.app.core.update.ApkInstaller
import com.slogan.app.core.update.InstallOutcome
import com.slogan.app.core.update.UpdateState
import com.slogan.app.domain.model.PlaybackSettings
import com.slogan.app.domain.model.Preset
import com.slogan.app.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SloganUiState(
    val draft: String = "",
    val settings: PlaybackSettings = PlaybackSettings(),
    val presets: List<Preset> = emptyList(),
    val playback: PlaybackState = PlaybackState.Idle,
    val voices: List<VoiceOption> = emptyList(),
    val message: String? = null,
    val selectedPresetId: Long? = null,
    val ttsReady: Boolean = false,
    val cacheBytes: Long = 0L,
    val update: UpdateState = UpdateState.Idle,
    val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    val isBusy: Boolean
        get() = playback.isBusy

    val canSave: Boolean
        get() = draft.isNotBlank()
}

class SloganViewModel(private val container: AppContainer) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val selectedPresetId = MutableStateFlow<Long?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    private val ttsReady = MutableStateFlow(false)
    private val cacheBytes = MutableStateFlow(0L)

    private data class Base(
        val draft: String,
        val settings: PlaybackSettings,
        val presets: List<Preset>,
        val playback: PlaybackState,
        val voices: List<VoiceOption>,
    )

    private val base = combine(
        draft,
        container.settingsStore.settings,
        container.presetDao.presets,
        container.playbackEngine.state,
        voices,
    ) { text, settings, presets, playback, voiceList ->
        Base(text, settings, presets, playback, voiceList)
    }

    /** 把「跨页面共享的一次性状态」收在一起，避免 combine 参数超过五个。 */
    private data class Status(
        val message: String?,
        val selectedPresetId: Long?,
        val ttsReady: Boolean,
        val cacheBytes: Long,
        val update: UpdateState,
    )

    private val status = combine(
        message,
        selectedPresetId,
        ttsReady,
        cacheBytes,
        container.updateManager.state,
    ) { currentMessage, presetId, ready, cache, updateState ->
        Status(currentMessage, presetId, ready, cache, updateState)
    }

    val uiState: StateFlow<SloganUiState> = combine(base, status) { current, extra ->
        SloganUiState(
            draft = current.draft,
            settings = current.settings,
            presets = current.presets,
            playback = current.playback,
            voices = current.voices,
            message = extra.message,
            selectedPresetId = extra.selectedPresetId,
            ttsReady = extra.ttsReady,
            cacheBytes = extra.cacheBytes,
            update = extra.update,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SloganUiState(),
    )

    init {
        viewModelScope.launch {
            ttsReady.value = container.synthesizer.isReady()
            voices.value = container.synthesizer.availableVoices()
            cacheBytes.value = container.synthesisCache.totalSizeBytes()
        }
    }

    fun onDraftChange(text: String) {
        draft.value = text.take(Preset.MAX_BODY_LENGTH)
        if (text.isBlank()) selectedPresetId.value = null
    }

    fun setInterval(milliseconds: Int) = updateSettings { it.copy(intervalMs = milliseconds) }

    fun setRepeatCount(count: Int) = updateSettings { it.copy(repeatCount = count) }

    fun setVoice(voiceName: String?) = updateSettings { it.copy(voiceName = voiceName) }

    fun setSpeechRate(rate: Float) = updateSettings { it.copy(speechRate = rate) }

    fun setPitch(pitch: Float) = updateSettings { it.copy(pitch = pitch) }

    fun setBoost(enabled: Boolean) = updateSettings { it.copy(boost = enabled) }

    fun setTheme(themeId: String) = updateSettings { it.copy(themeId = themeId) }

    fun setVolume(volume: Float) {
        container.playbackEngine.updateVolume(volume)
        updateSettings { it.copy(volume = volume) }
    }

    fun startPlayback() {
        val text = draft.value.trim()
        if (text.isEmpty()) {
            showMessage("先输入要播报的文字")
            return
        }
        val request = PlaybackRequest(
            title = selectedTitle(text),
            text = text,
            settings = uiState.value.settings,
        )
        startService(PlaybackService.startIntent(container.context, request))
    }

    fun playPreset(preset: Preset) {
        val request = PlaybackRequest(
            title = preset.title,
            text = preset.body,
            settings = uiState.value.settings.copy(
                voiceName = preset.voiceName,
                speechRate = preset.speechRate,
                pitch = preset.pitch,
                intervalMs = preset.intervalMs,
                repeatCount = preset.repeatCount,
                volume = preset.volume,
                boost = preset.boost,
            ),
        )
        startService(PlaybackService.startIntent(container.context, request))
    }

    fun stopPlayback() {
        val intent = PlaybackService.stopIntent(container.context)
        runCatching { container.context.startService(intent) }
            .onFailure { container.playbackEngine.stop() }
    }

    fun savePreset(title: String) {
        val text = draft.value.trim()
        if (text.isEmpty()) {
            showMessage("没有可保存的内容")
            return
        }
        val settings = uiState.value.settings
        val currentId = selectedPresetId.value
        val existing = uiState.value.presets.firstOrNull { it.id == currentId }
        val now = System.currentTimeMillis()
        val preset = Preset(
            id = existing?.id ?: 0L,
            title = title.trim().ifBlank { text.take(12) },
            body = text,
            voiceName = settings.voiceName,
            speechRate = settings.speechRate,
            pitch = settings.pitch,
            intervalMs = settings.intervalMs,
            repeatCount = settings.repeatCount,
            volume = settings.volume,
            boost = settings.boost,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            val id = container.presetDao.upsert(preset)
            selectedPresetId.value = id
            showMessage(if (existing == null) "已保存到记录" else "已更新记录")
        }
    }

    fun loadPreset(preset: Preset) {
        draft.value = preset.body
        selectedPresetId.value = preset.id
        viewModelScope.launch {
            container.settingsStore.update {
                it.copy(
                    voiceName = preset.voiceName,
                    speechRate = preset.speechRate,
                    pitch = preset.pitch,
                    intervalMs = preset.intervalMs,
                    repeatCount = preset.repeatCount,
                    volume = preset.volume,
                    boost = preset.boost,
                )
            }
        }
        showMessage("已载入「${preset.title}」")
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            container.presetDao.delete(preset.id)
            if (selectedPresetId.value == preset.id) selectedPresetId.value = null
            showMessage("已删除")
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            container.synthesisCache.clear()
            cacheBytes.value = container.synthesisCache.totalSizeBytes()
            showMessage("缓存已清除")
        }
    }

    fun refreshVoices() {
        viewModelScope.launch {
            ttsReady.value = container.synthesizer.isReady()
            voices.value = container.synthesizer.availableVoices()
            cacheBytes.value = container.synthesisCache.totalSizeBytes()
        }
    }

    fun checkForUpdate() = container.updateManager.check()

    fun downloadUpdate() {
        val release = when (val current = container.updateManager.state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        }
        if (release == null) {
            showMessage("请先检查更新")
            return
        }
        container.updateManager.download(release)
    }

    fun cancelUpdateDownload() = container.updateManager.cancel()

    fun installUpdate() {
        val ready = container.updateManager.state.value as? UpdateState.Ready ?: return
        when (ApkInstaller.install(container.context, ready.file)) {
            InstallOutcome.Started -> Unit
            InstallOutcome.NeedPermission -> showMessage("请允许 Slogan 安装未知来源应用")
            InstallOutcome.Failed -> showMessage("无法启动系统安装程序")
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun selectedTitle(text: String): String =
        uiState.value.presets.firstOrNull { it.id == selectedPresetId.value }?.title ?: text.take(12)

    private fun updateSettings(transform: (PlaybackSettings) -> PlaybackSettings) {
        viewModelScope.launch { container.settingsStore.update(transform) }
    }

    private fun startService(intent: Intent) {
        runCatching {
            ContextCompat.startForegroundService(container.context, intent)
        }.onFailure {
            showMessage("无法启动后台播放：${it.message ?: "未知错误"}")
        }
    }

    private fun showMessage(text: String) {
        message.value = text
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SloganViewModel(container) }
        }
    }
}
