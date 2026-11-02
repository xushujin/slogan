package com.slogan.app.data

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.slogan.app.domain.model.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局设置唯一入口。内存里只有一份 [StateFlow]。
 *
 * 拖动滑杆会以每秒几十次的频率触发更新，因此内存状态立即生效，
 * 落库做 250ms 防抖，避免把 UI 手感变成数据库写入压力。
 */
class SettingsStore(private val database: SloganDatabase) {

    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        val stored = mutableMapOf<String, String>()
        database.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_KEY, COLUMN_VALUE),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                stored[cursor.getString(0)] = cursor.getString(1)
            }
        }
        _settings.value = PlaybackSettings(
            intervalMs = stored[KEY_INTERVAL]?.toIntOrNull() ?: PlaybackSettings.DEFAULT_INTERVAL_MS,
            repeatCount = stored[KEY_REPEAT]?.toIntOrNull() ?: PlaybackSettings.DEFAULT_REPEAT_COUNT,
            voiceName = stored[KEY_VOICE]?.takeIf { it.isNotBlank() },
            speechRate = stored[KEY_SPEECH_RATE]?.toFloatOrNull() ?: 1.0f,
            pitch = stored[KEY_PITCH]?.toFloatOrNull() ?: 1.0f,
            volume = stored[KEY_VOLUME]?.toFloatOrNull() ?: 0.8f,
            boost = stored[KEY_BOOST]?.toBooleanStrictOrNull() ?: true,
            themeId = stored[KEY_THEME]?.takeIf { it.isNotBlank() } ?: PlaybackSettings.DEFAULT_THEME_ID,
        ).normalized()
    }

    suspend fun update(transform: (PlaybackSettings) -> PlaybackSettings): PlaybackSettings {
        val updated = transform(_settings.value).normalized()
        _settings.value = updated
        persistJob?.cancel()
        persistJob = persistScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persist(updated)
        }
        return updated
    }

    private fun persist(settings: PlaybackSettings) {
        val values = listOf(
            KEY_INTERVAL to settings.intervalMs.toString(),
            KEY_REPEAT to settings.repeatCount.toString(),
            KEY_VOICE to settings.voiceName.orEmpty(),
            KEY_SPEECH_RATE to settings.speechRate.toString(),
            KEY_PITCH to settings.pitch.toString(),
            KEY_VOLUME to settings.volume.toString(),
            KEY_BOOST to settings.boost.toString(),
            KEY_THEME to settings.themeId,
        )
        database.writableDatabase.transaction {
            for ((key, value) in values) {
                insertWithOnConflict(
                    TABLE,
                    null,
                    ContentValues().apply {
                        put(COLUMN_KEY, key)
                        put(COLUMN_VALUE, value)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    private companion object {
        const val TABLE = "settings"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"

        const val KEY_INTERVAL = "interval_ms"
        const val KEY_REPEAT = "repeat_count"
        const val KEY_VOICE = "voice_name"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_PITCH = "pitch"
        const val KEY_VOLUME = "volume"
        const val KEY_BOOST = "boost"
        const val KEY_THEME = "theme_id"

        const val PERSIST_DEBOUNCE_MS = 250L
    }
}
