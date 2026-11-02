package com.slogan.app.ui

import java.util.Locale

fun formatInterval(milliseconds: Int): String = when {
    milliseconds <= 0 -> "无缝衔接"
    milliseconds % 1000 == 0 -> "${milliseconds / 1000} 秒"
    else -> String.format(Locale.US, "%.1f 秒", milliseconds / 1000.0)
}

fun formatGap(milliseconds: Int): String = when {
    milliseconds <= 0 -> "立即继续"
    milliseconds % 1000 == 0 -> "${milliseconds / 1000} 秒"
    else -> String.format(Locale.US, "%.1f 秒", milliseconds / 1000.0)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun formatMultiplier(value: Float): String = String.format(Locale.US, "%.2f×", value)
