package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformUsesCustomEdgeSwipeBack: Boolean = true

internal actual val platformSupportsPredictiveBackBlur: Boolean = false

internal actual fun Modifier.platformRootSemantics(): Modifier = this

@Composable
internal actual fun rememberScreenCornerRadius(): Dp = 52.dp

@Composable
actual fun CoboPredictiveBackHandler(nav: NavState) {
    // iOS 路由栈返回由根节点双侧 EdgeSwipeBackOverlay 交互式驱动。
    @Suppress("UNUSED_EXPRESSION")
    nav
}
