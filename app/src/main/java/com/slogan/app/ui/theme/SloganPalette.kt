package com.slogan.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 一套拟态配色。拟态风格依赖“同色系的高光 + 阴影”，
 * 所以每套主题都必须显式给出 background / surface / 双向阴影色。
 */
@Immutable
data class SloganPalette(
    val id: String,
    val displayName: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color,
)

object SloganPalettes {

    val Graphite = SloganPalette(
        id = "graphite",
        displayName = "石墨",
        dark = true,
        background = Color(0xFF1E2126),
        surface = Color(0xFF23272E),
        shadowLight = Color(0xFF30353E),
        shadowDark = Color(0xFF121418),
        accent = Color(0xFF7C9CFF),
        onAccent = Color(0xFF10141F),
        textPrimary = Color(0xFFE9ECF2),
        textSecondary = Color(0xFF98A1B2),
        danger = Color(0xFFFF7A7A),
    )

    val Midnight = SloganPalette(
        id = "midnight",
        displayName = "靛夜",
        dark = true,
        background = Color(0xFF191A2B),
        surface = Color(0xFF1F2136),
        shadowLight = Color(0xFF2C2F4A),
        shadowDark = Color(0xFF0D0E19),
        accent = Color(0xFFA78BFA),
        onAccent = Color(0xFF150F2A),
        textPrimary = Color(0xFFEDEAF7),
        textSecondary = Color(0xFF9C97B8),
        danger = Color(0xFFFF7A9A),
    )

    val Ocean = SloganPalette(
        id = "ocean",
        displayName = "深海",
        dark = true,
        background = Color(0xFF10202A),
        surface = Color(0xFF152731),
        shadowLight = Color(0xFF1E3A47),
        shadowDark = Color(0xFF08141B),
        accent = Color(0xFF4FD1C5),
        onAccent = Color(0xFF04211F),
        textPrimary = Color(0xFFE3F2F1),
        textSecondary = Color(0xFF8FA9AC),
        danger = Color(0xFFFF8A80),
    )

    val Moss = SloganPalette(
        id = "moss",
        displayName = "苔绿",
        dark = false,
        background = Color(0xFFE6EAE3),
        surface = Color(0xFFEAEEE7),
        shadowLight = Color(0xFFFBFDF8),
        shadowDark = Color(0xFFC0C7BC),
        accent = Color(0xFF4F7A5B),
        onAccent = Color(0xFFF3FBF5),
        textPrimary = Color(0xFF2A322C),
        textSecondary = Color(0xFF6E7A71),
        danger = Color(0xFFB3452F),
    )

    val Sand = SloganPalette(
        id = "sand",
        displayName = "暖砂",
        dark = false,
        background = Color(0xFFEDE6DC),
        surface = Color(0xFFF1EBE1),
        shadowLight = Color(0xFFFFFCF6),
        shadowDark = Color(0xFFC9BFB1),
        accent = Color(0xFFB96F43),
        onAccent = Color(0xFFFFF6EF),
        textPrimary = Color(0xFF332C25),
        textSecondary = Color(0xFF7A6E62),
        danger = Color(0xFFA63F2A),
    )

    val Sakura = SloganPalette(
        id = "sakura",
        displayName = "樱粉",
        dark = false,
        background = Color(0xFFF2E9EC),
        surface = Color(0xFFF6EEF1),
        shadowLight = Color(0xFFFFFBFD),
        shadowDark = Color(0xFFD2C2C8),
        accent = Color(0xFFB95C7C),
        onAccent = Color(0xFFFFF5F8),
        textPrimary = Color(0xFF352A2F),
        textSecondary = Color(0xFF7C6A72),
        danger = Color(0xFFA63A52),
    )

    val All: List<SloganPalette> = listOf(Graphite, Midnight, Ocean, Moss, Sand, Sakura)

    val Default: SloganPalette = Graphite

    fun byId(id: String): SloganPalette = All.firstOrNull { it.id == id } ?: Default
}
