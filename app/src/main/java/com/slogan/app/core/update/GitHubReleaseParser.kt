package com.slogan.app.core.update

import org.json.JSONException
import org.json.JSONObject

/**
 * 解析 GitHub `GET /repos/{owner}/{repo}/releases/latest` 的响应。
 *
 * 只取更新需要的字段，其余忽略。字段缺失时抛 [UpdateException] 给出可读原因，
 * 而不是让界面显示一个空版本号。
 */
object GitHubReleaseParser {

    fun parse(json: String): ReleaseInfo {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw UpdateException("更新信息格式不正确", error)
        }

        val tagName = root.optString("tag_name").takeIf { it.isNotBlank() }
            ?: throw UpdateException("更新信息里没有版本号")
        val version = VersionName.parse(tagName)
            ?: throw UpdateException("无法识别的版本号：$tagName")

        val assets = root.optJSONArray("assets")
        if (assets == null || assets.length() == 0) {
            throw UpdateException("该版本没有可下载的文件")
        }

        val candidates = ArrayList<UpdateAsset>(assets.length())
        for (index in 0 until assets.length()) {
            val item = assets.optJSONObject(index) ?: continue
            val name = item.optString("name")
            val url = item.optString("browser_download_url")
            if (name.isBlank() || url.isBlank()) continue
            candidates += UpdateAsset(
                name = name,
                downloadUrl = url,
                sizeBytes = item.optLong("size", 0L),
                digest = item.optString("digest").toSha256Digest(),
            )
        }

        val apk = selectApk(candidates, version)
            ?: throw UpdateException("该版本没有找到 APK 安装包")
        val checksum = candidates.firstOrNull { it.name.equals("${apk.name}.sha256", ignoreCase = true) }

        return ReleaseInfo(
            tagName = tagName,
            version = version,
            publishedAt = root.optString("published_at"),
            notes = root.optString("body").trim(),
            pageUrl = root.optString("html_url"),
            apk = apk.copy(checksumUrl = checksum?.downloadUrl),
        )
    }

    /**
     * 优先选与版本号同名的安装包（`Slogan-1.2.3.apk`），
     * 命名规则变了也能退回「第一个 .apk」。
     */
    private fun selectApk(assets: List<UpdateAsset>, version: VersionName): UpdateAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val expected = "Slogan-$version.apk"
        return apks.firstOrNull { it.name.equals(expected, ignoreCase = true) } ?: apks.first()
    }

    /** GitHub 的格式是 `sha256:abcdef...`；不是这个格式就当作没有摘要。 */
    private fun String.toSha256Digest(): String? {
        val value = substringAfter("sha256:", missingDelimiterValue = "").lowercase()
        return value.takeIf { it.length == SHA256_HEX_LENGTH && it.all(::isHexDigit) }
    }

    private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f'

    private const val SHA256_HEX_LENGTH = 64
}
