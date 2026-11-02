package com.slogan.app.core.playback

import com.slogan.app.domain.model.PlaybackSettings

/** 一次播报请求：文本 + 当时的全局设置快照。 */
data class PlaybackRequest(
    val title: String,
    val text: String,
    val settings: PlaybackSettings,
)

sealed interface PlaybackState {

    data object Idle : PlaybackState

    data class Preparing(val title: String) : PlaybackState

    /** [totalIterations] 为 -1 表示无限循环。 */
    data class Playing(
        val iteration: Int,
        val totalIterations: Int,
        val progress: Float,
    ) : PlaybackState

    data class Waiting(
        val iteration: Int,
        val totalIterations: Int,
        val gapMs: Long,
    ) : PlaybackState

    data class Failed(val message: String) : PlaybackState

    val isBusy: Boolean
        get() = this is Preparing || this is Playing || this is Waiting
}
