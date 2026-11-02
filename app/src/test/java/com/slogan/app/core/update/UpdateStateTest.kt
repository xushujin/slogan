package com.slogan.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateStateTest {

    private val release = ReleaseInfo(
        tagName = "v1.1.0",
        version = VersionName(1, 1, 0),
        publishedAt = "2026-11-02T00:00:00Z",
        notes = "",
        pageUrl = "https://example.com/r",
        apk = UpdateAsset("Slogan-1.1.0.apk", "https://example.com/a.apk", 1_000L),
    )

    @Test
    fun `下载进度按字节比例计算`() {
        assertEquals(0.25f, UpdateState.Downloading(release, 250L, 1_000L).progress, 0.0001f)
        assertEquals(0.5f, UpdateState.Downloading(release, 500L, 1_000L).progress, 0.0001f)
    }

    @Test
    fun `总字节未知时进度为零`() {
        assertEquals(0f, UpdateState.Downloading(release, 250L, 0L).progress, 0.0001f)
    }

    @Test
    fun `进度超出范围时钳制到零到一`() {
        assertEquals(1f, UpdateState.Downloading(release, 2_000L, 1_000L).progress, 0.0001f)
        assertEquals(0f, UpdateState.Downloading(release, 0L, 1_000L).progress, 0.0001f)
    }
}
