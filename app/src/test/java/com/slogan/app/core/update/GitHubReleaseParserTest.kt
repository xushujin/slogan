package com.slogan.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    /*
     * 夹具说明：结构照 GitHub REST API `GET /releases/latest` 的真实响应；
     * 资产名、字节数、digest、下载地址均取自线上 v1.0.0 发布（已与 release 页面逐项核对）。
     * 抓取夹具时 GitHub API 对本机出口 IP 限流，故 id / 正文 / 时间戳为等价填充。
     */

    @Test
    fun `解析真实发布响应`() {
        val release = GitHubReleaseParser.parse(fixture("release_v1_0_0.json"))

        assertEquals("v1.0.0", release.tagName)
        assertEquals(VersionName(1, 0, 0), release.version)
        assertEquals("https://github.com/xushujin/slogan/releases/tag/v1.0.0", release.pageUrl)
        assertEquals("2026-11-02T03:59:47Z", release.publishedAt)

        assertEquals("Slogan-1.0.0.apk", release.apk.name)
        assertEquals(1_185_106L, release.apk.sizeBytes)
        assertEquals(
            "https://github.com/xushujin/slogan/releases/download/v1.0.0/Slogan-1.0.0.apk",
            release.apk.downloadUrl,
        )
        assertEquals(
            "e2318e10d4b804a2ff02ff2221e7fa76298089615aef3ad734e274d7b7287d30",
            release.apk.digest,
        )
        assertEquals(
            "https://github.com/xushujin/slogan/releases/download/v1.0.0/Slogan-1.0.0.apk.sha256",
            release.apk.checksumUrl,
        )
    }

    @Test
    fun `发布说明保留原始正文`() {
        val release = GitHubReleaseParser.parse(fixture("release_v1_0_0.json"))
        assertTrue("正文应保留 Markdown 标题", release.notes.startsWith("## What's Changed"))
        assertTrue("正文应保留完整变更链接", release.notes.contains("/commits/v1.0.0"))
        assertTrue("正文首尾空白应被裁掉", release.notes == release.notes.trim())
    }

    @Test
    fun `没有附件时报错`() {
        val message = expectUpdateException {
            GitHubReleaseParser.parse(
                """{"tag_name":"v1.0.0","html_url":"https://example.com","assets":[]}""",
            )
        }
        assertTrue("错误信息应说明没有可下载文件，实际：$message", message.contains("可下载"))
    }

    @Test
    fun `附件里没有 APK 时报错`() {
        val json = releaseJson("v1.0.0", asset("notes.txt", 10L))
        val message = expectUpdateException { GitHubReleaseParser.parse(json) }
        assertTrue("错误信息应说明缺少 APK，实际：$message", message.contains("APK"))
    }

    @Test
    fun `非法 JSON 报错而不是崩溃`() {
        val message = expectUpdateException { GitHubReleaseParser.parse("not a json at all") }
        assertTrue("错误信息应说明格式问题，实际：$message", message.contains("格式"))
    }

    @Test
    fun `版本号无法解析时报错`() {
        val json = releaseJson("latest", asset("Slogan-latest.apk", 100L))
        val message = expectUpdateException { GitHubReleaseParser.parse(json) }
        assertTrue("错误信息应包含原始标签，实际：$message", message.contains("latest"))
    }

    @Test
    fun `资产没有 digest 时该字段为空`() {
        val json = releaseJson("v1.1.0", asset("Slogan-1.1.0.apk", 2048L))
        val release = GitHubReleaseParser.parse(json)
        assertNull(release.apk.digest)
        assertNull(release.apk.checksumUrl)
    }

    @Test
    fun `优先选择与版本号同名的安装包`() {
        val json = releaseJson(
            "v1.1.0",
            asset("Slogan-debug.apk", 100L) + "," + asset("Slogan-1.1.0.apk", 200L),
        )
        val release = GitHubReleaseParser.parse(json)
        assertEquals("Slogan-1.1.0.apk", release.apk.name)
        assertEquals(200L, release.apk.sizeBytes)
    }

    @Test
    fun `命名规则不匹配时退回第一个 APK`() {
        val json = releaseJson("v1.1.0", asset("app-release.apk", 300L))
        val release = GitHubReleaseParser.parse(json)
        assertEquals("app-release.apk", release.apk.name)
    }

    @Test
    fun `digest 前缀不合法时忽略该字段`() {
        val json = releaseJson("v1.1.0", asset("Slogan-1.1.0.apk", 100L, digest = "md5:abc"))
        val release = GitHubReleaseParser.parse(json)
        assertNull(release.apk.digest)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("github/$name")) {
            "缺少测试资源 github/$name"
        }.bufferedReader().use { it.readText() }

    private fun releaseJson(tag: String, assets: String): String =
        "{\"tag_name\":\"" + tag + "\",\"html_url\":\"https://example.com/r\",\"body\":\"\"," +
            "\"published_at\":\"2026-01-01T00:00:00Z\",\"assets\":[" + assets + "]}"

    private fun asset(name: String, size: Long, digest: String? = null): String {
        val digestField = if (digest == null) "" else ",\"digest\":\"" + digest + "\""
        return "{\"name\":\"" + name + "\",\"browser_download_url\":\"https://example.com/" + name + "\"," +
            "\"size\":" + size + digestField + "}"
    }

    private fun expectUpdateException(block: () -> Unit): String {
        val thrown = runCatching(block).exceptionOrNull()
        assertTrue("应当抛出 UpdateException，实际是 $thrown", thrown is UpdateException)
        return thrown?.message.orEmpty()
    }
}
