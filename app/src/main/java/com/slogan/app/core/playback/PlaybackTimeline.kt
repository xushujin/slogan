package com.slogan.app.core.playback

/**
 * 循环时间轴：把“播放多久、间隔多久、重复几次”变成可单测的纯计算。
 *
 * repeatCount <= 0 表示无限循环；此时总时长不可预知，用 [Long.MAX_VALUE] 表示。
 */
data class PlaybackTimeline(
    val clipDurationMs: Long,
    val intervalMs: Long,
    val repeatCount: Int,
) {
    init {
        require(clipDurationMs >= 0) { "clipDurationMs 不能为负" }
        require(intervalMs >= 0) { "intervalMs 不能为负" }
    }

    val isInfinite: Boolean
        get() = repeatCount <= 0

    /** 计划播放轮数；无限循环时返回 [Int.MAX_VALUE]。 */
    val plannedIterations: Int
        get() = if (isInfinite) Int.MAX_VALUE else repeatCount

    fun isLastIteration(index: Int): Boolean = !isInfinite && index >= repeatCount - 1

    /** 第 [index] 轮播放结束后的等待时间，最后一轮不等待。 */
    fun gapAfter(index: Int): Long = if (isLastIteration(index)) 0L else intervalMs

    /** 估算总时长；无限循环返回 [Long.MAX_VALUE]。 */
    fun totalDurationMs(): Long {
        if (isInfinite) return Long.MAX_VALUE
        if (repeatCount <= 0) return 0L
        val plays = clipDurationMs * repeatCount
        val gaps = intervalMs * (repeatCount - 1).coerceAtLeast(0)
        return plays + gaps
    }
}
