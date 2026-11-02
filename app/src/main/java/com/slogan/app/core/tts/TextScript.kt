package com.slogan.app.core.tts

/**
 * 极简的字符集判定：只回答「这段文本是否需要中文音色」。
 *
 * 不引入语言识别库——扩音器场景下文本几乎都是中文，
 * 这里只做一次保守判断，用来在合成前拦住「英文音色读中文」这种必然失败或崩溃的组合。
 */
object TextScript {

    fun containsChinese(text: String): Boolean = text.any { char ->
        char in CJK_UNIFIED_START..CJK_UNIFIED_END || char in CJK_EXT_A_START..CJK_EXT_A_END
    }

    /**
     * 判断语言标签是否指向中文。
     *
     * 除了标准的 `zh`，还要接受 `chn` / `chi` / `zho` / `cmn`：部分厂商引擎用
     * ISO 639-2/3 码上报，甚至把英文报成 `usa`。按标准 `chn` 是奇努克混合语，
     * 但实际遇到的引擎用它表示中文，这里按「更可能对用户有用」的方向取舍。
     */
    fun isChineseLocaleTag(tag: String): Boolean {
        val normalized = tag.lowercase()
        return CHINESE_TAG_PREFIXES.any(normalized::startsWith)
    }

    /** 与 `Locale.CHINESE.language` 一致，避免调用处再拼字符串。 */
    const val CHINESE_LANGUAGE: String = "zh"

    private val CHINESE_TAG_PREFIXES = listOf("zh", "chn", "chi", "zho", "cmn")

    private const val CJK_UNIFIED_START = '\u4E00'
    private const val CJK_UNIFIED_END = '\u9FFF'
    private const val CJK_EXT_A_START = '\u3400'
    private const val CJK_EXT_A_END = '\u4DBF'
}
