package com.slogan.app.domain.model

/** 一条可复用的播报记录。 */
data class Preset(
    val id: Long = 0L,
    val title: String,
    val body: String,
    val voiceName: String?,
    val speechRate: Float,
    val pitch: Float,
    val intervalMs: Int,
    val repeatCount: Int,
    val volume: Float,
    val boost: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val MAX_TITLE_LENGTH = 40
        const val MAX_BODY_LENGTH = 500
    }
}
