package com.slogan.app.core.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

enum class InstallOutcome {
    /** 已拉起系统安装界面。 */
    Started,

    /** 缺少「安装未知应用」授权，已跳转设置页。 */
    NeedPermission,

    /** 系统没有可处理 APK 的安装器，或文件已失效。 */
    Failed,
}

object ApkInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** minSdk 为 26，`canRequestPackageInstalls` 始终可用。 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, file: File): InstallOutcome {
        if (!file.isFile || file.length() == 0L) return InstallOutcome.Failed
        if (!canInstall(context)) {
            openUnknownSourcesSettings(context)
            return InstallOutcome.NeedPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            InstallOutcome.Started
        } else {
            InstallOutcome.Failed
        }
    }

    private fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
