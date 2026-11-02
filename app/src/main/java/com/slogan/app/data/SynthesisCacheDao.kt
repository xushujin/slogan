package com.slogan.app.data

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CacheEntry(
    val cacheKey: String,
    val filePath: String,
    val sizeBytes: Long,
    val lastUsedAt: Long,
)

/**
 * 合成结果索引。真正省时间的是“命中文件就不再调用 TTS”，
 * 这张表只负责记录文件位置与 LRU 时间。
 */
class SynthesisCacheDao(private val database: SloganDatabase) {

    suspend fun find(cacheKey: String): CacheEntry? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            TABLE,
            PROJECTION,
            "$COLUMN_KEY = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    }

    suspend fun put(entry: CacheEntry) = withContext(Dispatchers.IO) {
        database.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put(COLUMN_KEY, entry.cacheKey)
                put(COLUMN_FILE, entry.filePath)
                put(COLUMN_SIZE, entry.sizeBytes)
                put(COLUMN_USED, entry.lastUsedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    suspend fun touch(cacheKey: String, timestamp: Long) = withContext(Dispatchers.IO) {
        database.writableDatabase.execSQL(
            "UPDATE $TABLE SET $COLUMN_USED = ? WHERE $COLUMN_KEY = ?",
            arrayOf<Any>(timestamp, cacheKey),
        )
    }

    suspend fun remove(cacheKey: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(TABLE, "$COLUMN_KEY = ?", arrayOf(cacheKey))
        Unit
    }

    suspend fun all(): List<CacheEntry> = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            TABLE,
            PROJECTION,
            null,
            null,
            null,
            null,
            "$COLUMN_USED ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }
    }

    suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("SELECT COALESCE(SUM($COLUMN_SIZE), 0) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun android.database.Cursor.toEntry(): CacheEntry = CacheEntry(
        cacheKey = getString(getColumnIndexOrThrow(COLUMN_KEY)),
        filePath = getString(getColumnIndexOrThrow(COLUMN_FILE)),
        sizeBytes = getLong(getColumnIndexOrThrow(COLUMN_SIZE)),
        lastUsedAt = getLong(getColumnIndexOrThrow(COLUMN_USED)),
    )

    private companion object {
        const val TABLE = "synthesis_cache"
        const val COLUMN_KEY = "cache_key"
        const val COLUMN_FILE = "file_path"
        const val COLUMN_SIZE = "size_bytes"
        const val COLUMN_USED = "last_used_at"

        val PROJECTION = arrayOf(COLUMN_KEY, COLUMN_FILE, COLUMN_SIZE, COLUMN_USED)
    }
}
