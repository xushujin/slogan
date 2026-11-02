package com.slogan.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNameTest {

    @Test
    fun `解析带 v 前缀的发布标签`() {
        assertEquals(VersionName(1, 0, 0), VersionName.parse("v1.0.0"))
        assertEquals(VersionName(2, 13, 7), VersionName.parse("V2.13.7"))
        assertEquals(VersionName(1, 2, 3), VersionName.parse("  1.2.3  "))
    }

    @Test
    fun `解析预发布标识`() {
        assertEquals(VersionName(1, 2, 0, "rc1"), VersionName.parse("v1.2.0-rc1"))
        assertEquals(VersionName(1, 2, 0, "beta.2"), VersionName.parse("1.2.0-beta.2"))
    }

    @Test
    fun `构建元数据不参与解析结果`() {
        assertEquals(VersionName(1, 2, 3), VersionName.parse("1.2.3+build.9"))
    }

    @Test
    fun `无法识别的版本号返回 null`() {
        assertNull(VersionName.parse(null))
        assertNull(VersionName.parse(""))
        assertNull(VersionName.parse("   "))
        assertNull(VersionName.parse("1.0"))
        assertNull(VersionName.parse("latest"))
        assertNull(VersionName.parse("1.0.0.0"))
        assertNull(VersionName.parse("v1.0.0-"))
    }

    @Test
    fun `主次修订依次比较`() {
        assertTrue("2.0.0 应高于 1.9.9", VersionName.parse("2.0.0")!! > VersionName.parse("1.9.9")!!)
        assertTrue("1.10.0 应高于 1.9.0", VersionName.parse("1.10.0")!! > VersionName.parse("1.9.0")!!)
        assertTrue("1.0.1 应高于 1.0.0", VersionName.parse("1.0.1")!! > VersionName.parse("1.0.0")!!)
    }

    @Test
    fun `同号正式版高于预发布版`() {
        assertTrue(VersionName.parse("1.0.0")!! > VersionName.parse("1.0.0-rc1")!!)
        assertTrue(VersionName.parse("1.0.0-rc1")!! < VersionName.parse("1.0.0")!!)
    }

    @Test
    fun `预发布标识按段比较`() {
        assertTrue(VersionName.parse("1.0.0-rc2")!! > VersionName.parse("1.0.0-rc1")!!)
        // 纯数字段按数值比较
        assertTrue(VersionName.parse("1.0.0-10")!! > VersionName.parse("1.0.0-9")!!)
        assertTrue(VersionName.parse("1.0.0-2")!! > VersionName.parse("1.0.0-1")!!)
        // 含字母的段按字典序，这是 semver 的规定：rc10 排在 rc9 之前
        assertTrue(VersionName.parse("1.0.0-rc10")!! < VersionName.parse("1.0.0-rc9")!!)
        assertTrue(VersionName.parse("1.0.0-beta")!! < VersionName.parse("1.0.0-rc")!!)
    }

    @Test
    fun `数字段优先级低于字母段`() {
        assertTrue(VersionName.parse("1.0.0-alpha")!! > VersionName.parse("1.0.0-1")!!)
    }

    @Test
    fun `相同版本比较为零`() {
        assertEquals(0, VersionName.parse("v1.2.3")!!.compareTo(VersionName.parse("1.2.3")!!))
        assertEquals(0, VersionName.parse("1.2.3-rc1")!!.compareTo(VersionName.parse("1.2.3-rc1")!!))
    }

    @Test
    fun `归一化后不保留 v 前缀`() {
        assertEquals("1.0.0", VersionName.parse("v1.0.0").toString())
        assertEquals("1.2.0-rc1", VersionName.parse("v1.2.0-rc1").toString())
    }
}
