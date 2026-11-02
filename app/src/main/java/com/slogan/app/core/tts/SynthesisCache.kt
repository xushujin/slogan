package com.slogan.app.core.tts

import android.content.Context
import com.slogan.app.core.audio.WavCodec
import com.slogan.app.data.CacheEntry
import com.slogan.app.data.SynthesisCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 合成结果缓存。
 *
 * 命中时直接返回已有 WAV 文件，**不会**再次调用 TTS；
 * 播放循环只读这份文件，因此“每轮重新合成”在实现上不可能发生。
 */
class SynthesisCache(
    context: Context,
    private val dao: SynthesisCacheDao,
    private val engineSignature: suspend () -> String,
) {

    private val directory = File(context.filesDir, "speech")
    private val mutex = Mutex()

    suspend fun obtain(
        request: SpeechRequest,
        synthesize: suspend (File) -> Unit,
    ): File = mutex.withLock {
        val key = SynthesisKey.of(request, engineSignature())
        val cached = dao.find(key)
        if (cached != null) {
            val file = File(cached.filePath)
            if (file.isFile && file.length() > 0L) {
                dao.touch(key, System.currentTimeMillis())
                return@withLock file
            }
            dao.remove(key)
        }

        withContext(Dispatchers.IO) { directory.mkdirs() }
        val target = File(directory, "$key.wav")
        val temporary = File(directory, "$key.tmp")
        try {
            synthesize(temporary)
            // 真实文件校验：引擎偶尔会写出空文件或非 WAV 内容，绝不能进缓存。
            WavCodec.decode(temporary)
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("无法写入缓存文件：${target.absolutePath}")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            dao.put(
                CacheEntry(
                    cacheKey = key,
                    filePath = target.absolutePath,
                    sizeBytes = target.length(),
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
            prune()
            target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    suspend fun totalSizeBytes(): Long = dao.totalSizeBytes()

    suspend fun clear() {
        mutex.withLock {
            dao.all().forEach { entry ->
                File(entry.filePath).delete()
                dao.remove(entry.cacheKey)
            }
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun prune() {
        val entries = dao.all().toMutableList()
        var total = entries.sumOf { it.sizeBytes }
        while ((entries.size > MAX_ENTRIES || total > MAX_BYTES) && entries.size > 1) {
            val oldest = entries.removeAt(0)
            File(oldest.filePath).delete()
            dao.remove(oldest.cacheKey)
            total -= oldest.sizeBytes
        }
    }

    private companion object {
        const val MAX_ENTRIES = 24
        const val MAX_BYTES = 48L * 1024 * 1024
    }
}
