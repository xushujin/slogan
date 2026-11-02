package com.slogan.app.data

import android.content.ContentValues
import com.slogan.app.domain.model.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PresetDao(private val database: SloganDatabase) {

    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _presets.value = queryAll()
    }

    suspend fun upsert(preset: Preset): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COLUMN_TITLE, preset.title)
            put(COLUMN_BODY, preset.body)
            put(COLUMN_VOICE, preset.voiceName)
            put(COLUMN_INTERVAL, preset.intervalMs)
            put(COLUMN_REPEAT, preset.repeatCount)
            put(COLUMN_VOLUME, preset.volume)
            put(COLUMN_SPEECH_RATE, preset.speechRate)
            put(COLUMN_PITCH, preset.pitch)
            put(COLUMN_BOOST, if (preset.boost) 1 else 0)
            put(COLUMN_UPDATED_AT, preset.updatedAt)
        }
        val id = if (preset.id == 0L) {
            values.put(COLUMN_CREATED_AT, preset.createdAt)
            database.writableDatabase.insertOrThrow(TABLE, null, values)
        } else {
            database.writableDatabase.update(
                TABLE,
                values,
                "$COLUMN_ID = ?",
                arrayOf(preset.id.toString()),
            )
            preset.id
        }
        _presets.value = queryAll()
        id
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val removed = database.writableDatabase.delete(TABLE, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
        if (removed) _presets.value = queryAll()
        removed
    }

    suspend fun findById(id: Long): Preset? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            TABLE,
            PROJECTION,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toPreset() else null
        }
    }

    private fun queryAll(): List<Preset> = database.readableDatabase.query(
        TABLE,
        PROJECTION,
        null,
        null,
        null,
        null,
        "$COLUMN_UPDATED_AT DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toPreset())
        }
    }

    private fun android.database.Cursor.toPreset(): Preset = Preset(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
        body = getString(getColumnIndexOrThrow(COLUMN_BODY)),
        voiceName = getString(getColumnIndexOrThrow(COLUMN_VOICE)),
        speechRate = getFloat(getColumnIndexOrThrow(COLUMN_SPEECH_RATE)),
        pitch = getFloat(getColumnIndexOrThrow(COLUMN_PITCH)),
        intervalMs = getInt(getColumnIndexOrThrow(COLUMN_INTERVAL)),
        repeatCount = getInt(getColumnIndexOrThrow(COLUMN_REPEAT)),
        volume = getFloat(getColumnIndexOrThrow(COLUMN_VOLUME)),
        boost = getInt(getColumnIndexOrThrow(COLUMN_BOOST)) != 0,
        createdAt = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
    )

    private companion object {
        const val TABLE = "presets"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_BODY = "body"
        const val COLUMN_VOICE = "voice_name"
        const val COLUMN_INTERVAL = "interval_ms"
        const val COLUMN_REPEAT = "repeat_count"
        const val COLUMN_VOLUME = "volume"
        const val COLUMN_SPEECH_RATE = "speech_rate"
        const val COLUMN_PITCH = "pitch"
        const val COLUMN_BOOST = "boost"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"

        val PROJECTION = arrayOf(
            COLUMN_ID,
            COLUMN_TITLE,
            COLUMN_BODY,
            COLUMN_VOICE,
            COLUMN_INTERVAL,
            COLUMN_REPEAT,
            COLUMN_VOLUME,
            COLUMN_SPEECH_RATE,
            COLUMN_PITCH,
            COLUMN_BOOST,
            COLUMN_CREATED_AT,
            COLUMN_UPDATED_AT,
        )
    }
}
