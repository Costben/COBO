@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalNativeApi::class)

package com.cobo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cobo.app.data.Api
import com.cobo.app.platform.platformNetworkType
import com.cobo.app.screen.defaultRefererFor
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addOutput
import platform.AVFoundation.allowsExternalPlayback
import platform.AVFoundation.asset
import platform.AVFoundation.automaticallyWaitsToMinimizeStalling
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.playImmediatelyAtRate
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.preventsDisplaySleepDuringVideoPlayback
import platform.AVFoundation.removeOutput
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.tracksWithMediaType
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.numberWithUnsignedInt
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.native.ref.WeakReference

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

/**
 * 信息流卡片内的轻量 iOS 视频帧解码器：
 * 通过 [AVPlayerItemVideoOutput] 直接提取 BGRA 帧并渲染到 Compose Skia 画布，
 * 避免 [UIKitView] 在外层 COUI 磨砂玻璃（`layerBackdrop`）与转场快照（`ExitSnapshotHost`）下
 * 挖空失效导致白屏或返回黑屏，同时在静音预览时不抢占系统 `AVAudioSession`。
 */
private class IosInlineFeedVideoPlayer {
    private val avPlayer = AVPlayer().apply {
        muted = true
        allowsExternalPlayback = false
        preventsDisplaySleepDuringVideoPlayback = false
        automaticallyWaitsToMinimizeStalling = true
    }

    private val videoOutput: AVPlayerItemVideoOutput = run {
        val pixelFormatKey = kCVPixelBufferPixelFormatTypeKey?.let {
            interpretObjCPointer<Any>(it.rawValue)
        } ?: "PixelFormatType"
        AVPlayerItemVideoOutput(
            pixelBufferAttributes = mapOf<Any?, Any?>(
                pixelFormatKey to NSNumber.numberWithUnsignedInt(kCVPixelFormatType_32BGRA),
            ),
        )
    }

    private var currentItem: AVPlayerItem? = null
    private var endObserver: Any? = null
    private var failedObserver: Any? = null
    private var reusablePixels = ByteArray(0)
    private var rotationResolved = false
    private var released = false

    var frameBitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    var rotationDegrees by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(false)
        private set

    fun start(url: String, headers: Map<String, String>, muted: Boolean) {
        if (released) return
        val nsUrl = NSURL.URLWithString(url) ?: run {
            failed = true
            return
        }
        detachCurrentItem()
        failed = false
        rotationResolved = false
        rotationDegrees = 0

        val options: Map<Any?, Any?>? = if (headers.isNotEmpty()) {
            mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
        } else {
            null
        }
        val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = options)
        val item = AVPlayerItem.playerItemWithAsset(asset)
        item.addOutput(videoOutput)
        currentItem = item

        val weakSelf = WeakReference(this)
        val nc = NSNotificationCenter.defaultCenter
        endObserver = nc.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val self = weakSelf.get() ?: return@addObserverForName
            if (self.released) return@addObserverForName
            self.avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
            self.avPlayer.playImmediatelyAtRate(1f)
        }
        failedObserver = nc.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            weakSelf.get()?.let { if (!it.released) it.failed = true }
        }

        avPlayer.muted = muted
        if (!muted) ensurePlaybackAudioSession()
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        avPlayer.play()
    }

    fun setMuted(muted: Boolean) {
        if (released) return
        avPlayer.muted = muted
        if (!muted) {
            ensurePlaybackAudioSession()
        }
    }

    fun pollFrame(): Boolean {
        if (released) return false
        val item = avPlayer.currentItem ?: return false
        when (item.status) {
            AVPlayerItemStatusFailed -> {
                failed = true
                return false
            }
            AVPlayerItemStatusReadyToPlay -> Unit
            else -> return false
        }

        if (!rotationResolved) {
            val videoTrack = item.asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            if (videoTrack != null) {
                val deg = videoTrack.preferredTransform.useContents {
                    ((atan2(b, a) * 180.0 / PI).roundToInt() % 360 + 360) % 360
                }
                rotationDegrees = when (deg) {
                    90, 180, 270 -> deg
                    else -> 0
                }
                rotationResolved = true
            }
        }

        val itemTime = videoOutput.itemTimeForHostTime(CACurrentMediaTime())
        if (!videoOutput.hasNewPixelBufferForItemTime(itemTime)) return false
        val pixelBuffer = videoOutput.copyPixelBufferForItemTime(itemTime, itemTimeForDisplay = null) ?: return false
        try {
            CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
            try {
                val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
                val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
                val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
                val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return false
                if (width <= 0 || height <= 0 || bytesPerRow < width * 4) return false
                val totalBytes = bytesPerRow * height
                val bytes = if (reusablePixels.size == totalBytes) {
                    reusablePixels
                } else {
                    ByteArray(totalBytes).also { reusablePixels = it }
                }
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), baseAddress, totalBytes.convert())
                }
                val skiaImage = SkiaImage.makeRaster(
                    imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                    bytes = bytes,
                    rowBytes = bytesPerRow,
                )
                frameBitmap = skiaImage.toComposeImageBitmap()
                return true
            } finally {
                CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
            }
        } finally {
            CFRelease(pixelBuffer)
        }
    }

    private fun ensurePlaybackAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeMoviePlayback,
                options = 0uL,
                error = null,
            )
            session.setActive(true, error = null)
        }
    }

    private fun detachCurrentItem() {
        val nc = NSNotificationCenter.defaultCenter
        endObserver?.let { nc.removeObserver(it) }
        failedObserver?.let { nc.removeObserver(it) }
        endObserver = null
        failedObserver = null
        currentItem?.removeOutput(videoOutput)
        currentItem = null
    }

    fun release() {
        if (released) return
        released = true
        detachCurrentItem()
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        frameBitmap = null
        reusablePixels = ByteArray(0)
    }
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
    val player = remember(activeUrl, referer) { IosInlineFeedVideoPlayer() }
    var reportedFirstFrame by remember(player) { mutableStateOf(false) }

    LaunchedEffect(player, activeUrl, referer) {
        val effectiveReferer = referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(activeUrl)
        val headers = buildMap {
            put("User-Agent", Api.WEB_UA)
            if (!effectiveReferer.isNullOrBlank()) put("Referer", effectiveReferer)
        }
        player.start(url = activeUrl, headers = headers, muted = muted)
    }

    LaunchedEffect(player, muted) {
        player.setMuted(muted)
    }

    LaunchedEffect(player) {
        while (true) {
            withFrameNanos { }
            if (player.pollFrame() && !reportedFirstFrame) {
                reportedFirstFrame = true
                onFirstFrame()
            }
        }
    }

    LaunchedEffect(player.failed, fallbackUrl) {
        if (player.failed) {
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

    val bitmap = player.frameBitmap ?: return
    val rotation = player.rotationDegrees
    if (rotation == 0) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Canvas(modifier = modifier.clipToBounds()) {
            val bw = bitmap.width.toFloat().coerceAtLeast(1f)
            val bh = bitmap.height.toFloat().coerceAtLeast(1f)
            val swapped = rotation == 90 || rotation == 270
            val displayW = if (swapped) bh else bw
            val displayH = if (swapped) bw else bh
            val scale = max(size.width / displayW, size.height / displayH)
            val drawW = (bw * scale).roundToInt().coerceAtLeast(1)
            val drawH = (bh * scale).roundToInt().coerceAtLeast(1)
            val offsetX = ((size.width - drawW) / 2f).roundToInt()
            val offsetY = ((size.height - drawH) / 2f).roundToInt()
            withTransform({
                rotate(degrees = rotation.toFloat(), pivot = center)
            }) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(offsetX, offsetY),
                    dstSize = IntSize(drawW, drawH),
                )
            }
        }
    }
}
