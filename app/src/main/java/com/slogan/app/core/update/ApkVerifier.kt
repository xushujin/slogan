package com.slogan.app.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 安装包校验。
 *
 * 校验和只能发现下载损坏；真正拦住「别人塞一个同包名 APK」的是签名比对——
 * 用待安装包的签名与当前已安装应用的签名做交集，为空就拒绝。
 */
object ApkVerifier {

    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

    fun matchesChecksum(expected: String, actual: String): Boolean =
        expected.equals(actual, ignoreCase = true)

    /** 返回 null 表示可以安装，否则返回展示给用户的原因。 */
    @Suppress("DEPRECATION")
    fun verifyInstallable(context: Context, apkFile: File): String? {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return "安装包无法解析，可能下载已损坏"
        if (archive.packageName != context.packageName) {
            return "安装包包名不一致（${archive.packageName}），无法覆盖当前应用"
        }
        val archiveDigests = signerDigests(archive)
        val currentDigests = signerDigests(packageManager.getPackageInfo(context.packageName, flags))
        if (archiveDigests.isEmpty() || currentDigests.isEmpty()) {
            return "无法读取安装包签名"
        }
        // 密钥轮换后 signingCertificateHistory 会同时包含新旧证书，用交集判断。
        if (archiveDigests.intersect(currentDigests).isEmpty()) {
            return "安装包签名与当前应用不一致，已拒绝安装"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures ?: return emptySet()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }
}
