package com.cobo.app.screen

/**
 * 主页头部折叠偏移的纯数学计算（像素单位）。
 */
object ProfileHeaderCollapseMath {
    /** 上滑（[deltaY] < 0）时消耗可折叠剩余量，返回 `(新折叠高度, 消耗的 Y 位移)`。 */
    fun collapseDelta(
        currentCollapsePx: Float,
        maxCollapsePx: Float,
        deltaY: Float,
    ): Pair<Float, Float> {
        val clampedCurrent = currentCollapsePx.coerceIn(0f, maxCollapsePx.coerceAtLeast(0f))
        if (maxCollapsePx <= 0f || deltaY >= 0f) return clampedCurrent to 0f
        val remaining = (maxCollapsePx - clampedCurrent).coerceAtLeast(0f)
        if (remaining <= 0f) return clampedCurrent to 0f
        val consume = minOf(-deltaY, remaining)
        return (clampedCurrent + consume) to -consume
    }

    /** 下滑未消耗完的剩余量（[deltaY] > 0）时展开资料头，返回 `(新折叠高度, 消耗的 Y 位移)`。 */
    fun expandDelta(
        currentCollapsePx: Float,
        deltaY: Float,
    ): Pair<Float, Float> {
        val clampedCurrent = currentCollapsePx.coerceAtLeast(0f)
        if (clampedCurrent <= 0f || deltaY <= 0f) return clampedCurrent to 0f
        val consume = minOf(deltaY, clampedCurrent)
        return (clampedCurrent - consume) to consume
    }
}
