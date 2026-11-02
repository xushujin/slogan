package com.slogan.app.core.update

import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新链路共用的 HTTP 细节。
 *
 * 两个刻意的选择：
 * 1. `User-Agent` 必须显式设置，GitHub API 对空 UA 直接返回 403；
 * 2. 关掉 gzip（`Accept-Encoding: identity`），否则 `Content-Length` 是压缩后的长度，
 *    而 `InputStream` 读到的是解压后的字节，进度条会直接超过 100%。
 */
internal object HttpSupport {

    const val USER_AGENT = "Slogan-Android"
    const val ACCEPT_GITHUB_JSON = "application/vnd.github+json"
    const val ACCEPT_BINARY = "application/octet-stream"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429

    fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            // GitHub 的下载地址会 302 到 objects.githubusercontent.com，必须跟随。
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Accept-Encoding", "identity")
        }

    fun failure(code: Int, action: String): UpdateException = when (code) {
        HttpURLConnection.HTTP_NOT_FOUND -> UpdateException("仓库还没有发布任何版本")
        HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
            UpdateException("GitHub 接口访问过于频繁，请稍后再试")
        else -> UpdateException("$action 失败（HTTP $code）")
    }
}
