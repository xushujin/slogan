package com.slogan.app.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextScriptTest {

    @Test
    fun `纯中文文本判定为中文`() {
        assertTrue(TextScript.containsChinese("前方施工，请绕行"))
        assertTrue(TextScript.containsChinese("你好"))
    }

    @Test
    fun `中英混排只要含汉字就判定为中文`() {
        assertTrue(TextScript.containsChinese("请佩戴口罩 Wear a mask"))
    }

    @Test
    fun `纯英文与数字不算中文`() {
        assertFalse(TextScript.containsChinese("Please wear a mask"))
        assertFalse(TextScript.containsChinese("2026-11-02 14:30"))
        assertFalse(TextScript.containsChinese(""))
    }

    @Test
    fun `只有中文标点不算中文文本`() {
        // 标点位于 U+3000 区，不属于汉字；纯标点交给任何音色都不会出问题。
        assertFalse(TextScript.containsChinese("，。！？"))
    }

    @Test
    fun `扩展 A 区汉字也能识别`() {
        assertTrue(TextScript.containsChinese("\u3400"))
    }

    @Test
    fun `语言常量与平台 Locale 一致`() {
        assertEquals(Locale.CHINESE.language, TextScript.CHINESE_LANGUAGE)
    }
}
