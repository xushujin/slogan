package com.slogan.app.core.update

/**
 * 语义化版本号。
 *
 * 只关心 `主.次.修订` 与可选的预发布标识，构建元数据（`+` 之后）在比较中被忽略。
 * 项目里版本号有两个来源：`BuildConfig.VERSION_NAME` 与 GitHub Release 的标签名，
 * 两者格式不完全一致（标签带 `v` 前缀），统一在这里归一化。
 */
data class VersionName(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<VersionName> {

    override fun compareTo(other: VersionName): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return comparePreRelease(preRelease, other.preRelease)
    }

    /** 归一化后的展示形态，例如 `1.0.0` 或 `1.2.0-rc1`。 */
    override fun toString(): String =
        if (preRelease == null) "$major.$minor.$patch" else "$major.$minor.$patch-$preRelease"

    companion object {
        private val PATTERN = Regex(
            "^[vV]?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-([0-9A-Za-z.\\-]{1,32}))?(?:\\+[0-9A-Za-z.\\-]{1,32})?$",
        )

        /** 解析失败返回 null，调用方负责给出可读的错误提示。 */
        fun parse(raw: String?): VersionName? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val match = PATTERN.matchEntire(text) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            return VersionName(major, minor, patch, match.groupValues[4].takeIf { it.isNotEmpty() })
        }

        /**
         * 按 semver 规则比较预发布标识：
         * 没有预发布标识的版本高于同号预发布版；都有时逐段比较，
         * 数字段按数值比、字母段按字典序，数字段优先级低于字母段。
         */
        private fun comparePreRelease(left: String?, right: String?): Int {
            if (left == null && right == null) return 0
            if (left == null) return 1
            if (right == null) return -1
            val a = left.split('.')
            val b = right.split('.')
            for (index in 0 until minOf(a.size, b.size)) {
                val x = a[index]
                val y = b[index]
                val xn = x.toIntOrNull()
                val yn = y.toIntOrNull()
                val result = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> x.compareTo(y)
                }
                if (result != 0) return result
            }
            return a.size.compareTo(b.size)
        }
    }
}
