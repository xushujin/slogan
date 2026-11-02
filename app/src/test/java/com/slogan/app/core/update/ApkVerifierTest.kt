package com.slogan.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkVerifierTest {

    @Test
    fun `真实 WAV 文件的 SHA-256 与系统 shasum 一致`() {
        // 期望值由 macOS `shasum -a 256` 对同一文件算出，不是硬编码猜测。
        assertEquals(
            "8176db1c4f404b3e11278975478c985f7e521e7f6da45d3b30a7287e7d370dcb",
            ApkVerifier.sha256(resourceFile("wav/tone_mono_24000.wav")),
        )
    }

    @Test
    fun `立体声夹具的摘要同样可复现`() {
        assertEquals(
            "183eec80fe165e312095be1d7289c348d9f8410dec7db7ce2e25c3949e7129dd",
            ApkVerifier.sha256(resourceFile("wav/tone_stereo_44100.wav")),
        )
    }

    @Test
    fun `空文件的摘要等于 SHA-256 标准空值`() {
        val empty = File.createTempFile("slogan-empty", ".bin")
        try {
            empty.writeBytes(ByteArray(0))
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                ApkVerifier.sha256(empty),
            )
        } finally {
            empty.delete()
        }
    }

    @Test
    fun `校验和比较忽略大小写`() {
        val upper = "E2318E10D4B804A2FF02FF2221E7FA76298089615AEF3AD734E274D7B7287D30"
        val lower = "e2318e10d4b804a2ff02ff2221e7fa76298089615aef3ad734e274d7b7287d30"
        assertTrue(ApkVerifier.matchesChecksum(upper, lower))
        assertTrue(ApkVerifier.matchesChecksum(lower, lower))
    }

    @Test
    fun `校验和不一致时判定失败`() {
        assertFalse(
            ApkVerifier.matchesChecksum(
                "e2318e10d4b804a2ff02ff2221e7fa76298089615aef3ad734e274d7b7287d30",
                "002c26997a53c5be67ef8bcde04ee968e322255c64930c32700f7e9fa734441e",
            ),
        )
    }

    private fun resourceFile(path: String): File {
        val url = checkNotNull(javaClass.classLoader?.getResource(path)) { "缺少测试资源 $path" }
        return File(url.toURI())
    }
}
