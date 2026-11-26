package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cobo.app.data.Api
import com.cobo.app.platform.PlatformVideoPlayer
import com.cobo.app.platform.PlatformVideoSurface
import com.cobo.app.platform.platformNetworkType
import com.cobo.app.screen.defaultRefererFor
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification

@Composable
internal actual fun rememberPlatformAutoPlayEnvironmentState(): Pair<Boolean, Boolean> {
    var foreground by remember {
        mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
    }
    var isWifi by remember {
        mutableStateOf(platformNetworkType().equals("wifi", ignoreCase = true))
    }

    DisposableEffect(Unit) {
        val nc = NSNotificationCenter.defaultCenter
        val becomeActive = nc.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) {
            foreground = true
            isWifi = platformNetworkType().equals("wifi", ignoreCase = true)
        }
        val resignActive = nc.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
        ) {
            foreground = false
        }
        onDispose {
            nc.removeObserver(becomeActive)
            nc.removeObserver(resignActive)
        }
    }

    return isWifi to foreground
}

@Composable
internal actual fun PlatformInlineVideoPreview(
    initialUrl: String,
    referer: String?,
    fallbackUrl: String?,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    var activeUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    val player = remember(activeUrl, referer) { PlatformVideoPlayer() }

    LaunchedEffect(player, activeUrl, referer) {
        val effectiveReferer = referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(activeUrl)
        val headers = buildMap {
            put("User-Agent", Api.WEB_UA)
            if (!effectiveReferer.isNullOrBlank()) put("Referer", effectiveReferer)
        }
        player.setMedia(
            url = activeUrl,
            headers = headers,
            looping = true,
            muted = muted,
            autoPlay = true,
        )
    }

    LaunchedEffect(player, muted) {
        player.setMuted(muted)
    }

    LaunchedEffect(player.hasRenderedFirstFrame) {
        if (player.hasRenderedFirstFrame) {
            onFirstFrame()
        }
    }

    LaunchedEffect(player.errorMessage, fallbackUrl) {
        if (player.errorMessage != null) {
            val backup = fallbackUrl?.takeIf { it.isNotBlank() && it != activeUrl }
            if (backup != null) {
                activeUrl = backup
            } else {
                onFailed()
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    PlatformVideoSurface(
        player = player,
        fillCrop = true,
        modifier = modifier,
        transparentBackground = true,
    )
}
