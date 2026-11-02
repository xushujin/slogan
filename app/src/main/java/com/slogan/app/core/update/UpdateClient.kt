package com.slogan.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection

/** 只负责「拿到最新版本」与「拿到校验和文本」，下载与安装由别的类负责。 */
class UpdateClient(
    private val releaseApiUrl: String = DEFAULT_RELEASE_API_URL,
) {

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val body = readText(releaseApiUrl, HttpSupport.ACCEPT_GITHUB_JSON, "检查更新")
        GitHubReleaseParser.parse(body)
    }

    /**
     * 读取 `<apk>.sha256` 附件，返回其中的 64 位十六进制摘要。
     * 校验和缺失或格式不对时返回 null——它只用来发现下载损坏，不阻断安装。
     */
    suspend fun fetchChecksum(url: String): String? = withContext(Dispatchers.IO) {
        val text = runCatching {
            readText(url, HttpSupport.ACCEPT_BINARY, "下载校验和")
        }.getOrNull() ?: return@withContext null
        text.lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?.substringBefore(' ')
            ?.lowercase()
            ?.takeIf(::isSha256Hex)
    }

    private fun readText(url: String, accept: String, action: String): String {
        val connection = HttpSupport.open(url, accept)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpSupport.failure(code, action)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: IOException) {
            throw UpdateException("网络不可用，请检查连接后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun isSha256Hex(value: String): Boolean =
        value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

    private companion object {
        const val DEFAULT_RELEASE_API_URL =
            "https://api.github.com/repos/xushujin/slogan/releases/latest"
        const val SHA256_HEX_LENGTH = 64
    }
}
