package com.slogan.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.coroutineContext

/**
 * 把 APK 下载到应用私有目录。
 *
 * 先写 `.part` 再改名，中途失败或取消都不会留下一个“看起来完整”的文件；
 * 进度回调按 80ms 节流，避免每读 64KB 就往界面推一次状态。
 */
class ApkDownloader {

    suspend fun download(
        asset: UpdateAsset,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()

        val connection = HttpSupport.open(asset.downloadUrl, HttpSupport.ACCEPT_BINARY)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpSupport.failure(code, "下载更新")

            // Content-Length 可能缺失，退回 Release 里声明的字节数。
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: asset.sizeBytes
            var downloaded = 0L
            var lastReportAt = 0L

            connection.inputStream.use { input ->
                partial.outputStream().buffered(BUFFER_BYTES).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= PROGRESS_INTERVAL_MS) {
                            lastReportAt = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)

            if (total > 0L && downloaded != total) {
                throw UpdateException("下载不完整（$downloaded/$total 字节），请重试")
            }
            if (!partial.renameTo(destination)) {
                throw UpdateException("无法保存安装包，请检查存储空间")
            }
            destination
        } catch (error: IOException) {
            throw UpdateException("下载中断，请检查网络后重试", error)
        } finally {
            connection.disconnect()
            // 成功时 partial 已被 renameTo 移走；失败或取消时在这里统一清理。
            if (partial.exists()) partial.delete()
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 80L
    }
}
