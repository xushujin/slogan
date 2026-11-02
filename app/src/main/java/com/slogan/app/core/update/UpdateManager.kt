package com.slogan.app.core.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 应用内更新的完整状态机。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState

    data class Downloading(
        val release: ReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes <= 0L) {
                0f
            } else {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            }
    }

    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState

    /** [release] 不为空表示可以原地重试下载，否则应重新检查。 */
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * 更新流程编排：检查 → 下载 → 校验 → 交给安装器。
 *
 * 同一时刻只允许一个任务；取消下载会退回 [UpdateState.Available]，
 * 用户可以再次点击下载，而不是回到需要重新检查的状态。
 */
class UpdateManager(
    private val context: Context,
    private val currentVersion: String,
    private val client: UpdateClient = UpdateClient(),
    private val downloader: ApkDownloader = ApkDownloader(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val release = client.fetchLatestRelease()
                val current = VersionName.parse(currentVersion)
                when {
                    current == null -> UpdateState.Failed("当前版本号无法解析：$currentVersion")
                    release.version > current -> UpdateState.Available(release)
                    else -> UpdateState.UpToDate(currentVersion)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateException) {
                UpdateState.Failed(error.message ?: "检查更新失败")
            } catch (error: Exception) {
                UpdateState.Failed("检查更新失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0L, release.apk.sizeBytes)
            try {
                val file = downloader.download(release.apk, destinationFor(release)) { downloaded, total ->
                    // 取消之后仍可能收到最后一帧回调，用 isActive 挡住，避免覆盖新会话的状态。
                    if (isActive) {
                        _state.value = UpdateState.Downloading(release, downloaded, total)
                    }
                }
                val problem = withContext(Dispatchers.IO) { verify(release, file) }
                if (problem == null) {
                    _state.value = UpdateState.Ready(release, file)
                } else {
                    file.delete()
                    _state.value = UpdateState.Failed(problem, release)
                }
            } catch (error: CancellationException) {
                // 状态已在 cancel() 中置为 Available，这里只负责退出。
                throw error
            } catch (error: UpdateException) {
                _state.value = UpdateState.Failed(error.message ?: "下载失败", release)
            } catch (error: Exception) {
                _state.value = UpdateState.Failed("下载失败：${error.message ?: "未知错误"}", release)
            }
        }
    }

    fun cancel() {
        val current = _state.value
        if (current is UpdateState.Downloading) {
            _state.value = UpdateState.Available(current.release)
        }
        job?.cancel()
        job = null
    }

    private suspend fun verify(release: ReleaseInfo, file: File): String? {
        // 优先用资产自带的 digest，省掉一次 .sha256 下载；没有才回退。
        val expected = release.apk.digest
            ?: release.apk.checksumUrl?.let { client.fetchChecksum(it) }
        if (expected != null && !ApkVerifier.matchesChecksum(expected, ApkVerifier.sha256(file))) {
            return "安装包校验失败，可能下载已损坏"
        }
        return ApkVerifier.verifyInstallable(context, file)
    }

    private fun destinationFor(release: ReleaseInfo): File {
        val directory = File(context.cacheDir, DIRECTORY)
        directory.mkdirs()
        val fileName = release.apk.name.ifBlank { "Slogan-${release.version}.apk" }
        // 只保留当前要安装的包，避免历次下载在缓存里堆积。
        directory.listFiles()?.forEach { existing ->
            if (existing.name != fileName) existing.delete()
        }
        val target = File(directory, fileName)
        if (target.exists()) target.delete()
        return target
    }

    private companion object {
        const val DIRECTORY = "updates"
    }
}
