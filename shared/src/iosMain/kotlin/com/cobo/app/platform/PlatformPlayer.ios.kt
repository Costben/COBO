@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.cobo.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import coil3.BitmapImage
import coil3.Image as CoilImage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNull
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.native.ref.WeakReference

/** 保持系统画中画运行期间的强引用，防止页面退出重组后被 GC 导致画中画意外中断。 */
private object ActivePipRetainer {
    private val active = LinkedHashSet<PlatformVideoPlayer>()

    fun retain(player: PlatformVideoPlayer) {
        active.add(player)
    }

    fun release(player: PlatformVideoPlayer) {
        active.remove(player)
    }
}

@OptIn(ExperimentalNativeApi::class)
private class IosPipDelegate(
    private val playerRef: WeakReference<PlatformVideoPlayer>,
) : NSObject(), AVPictureInPictureControllerDelegateProtocol {

    override fun pictureInPictureControllerWillStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController,
    ) {
        playerRef.get()?.onSystemPipWillStart()
    }

    override fun pictureInPictureControllerDidStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController,
    ) {
        playerRef.get()?.onSystemPipDidStart()
    }

    override fun pictureInPictureControllerDidStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController,
    ) {
        playerRef.get()?.onSystemPipDidStop()
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        failedToStartPictureInPictureWithError: NSError,
    ) {
        logWarn("CoboPiP", "failed to start PiP: ${failedToStartPictureInPictureWithError.localizedDescription}")
        playerRef.get()?.onSystemPipDidStop()
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
    ) {
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
    }
}

@Stable
@OptIn(ExperimentalNativeApi::class)
actual class PlatformVideoPlayer actual constructor() {
    internal val avPlayer: AVPlayer = AVPlayer().apply {
        muted = false
        volume = 1f
        allowsExternalPlayback = true
        preventsDisplaySleepDuringVideoPlayback = true
        audiovisualBackgroundPlaybackPolicy = AVPlayerAudiovisualBackgroundPlaybackPolicyContinuesIfPossible
    }

    /** 与播放器同生命周期的唯一 AVPlayerLayer，保证切小窗与系统画中画时画面不闪烁、控制器不丢失。 */
    internal val playerLayer: AVPlayerLayer = AVPlayerLayer.playerLayerWithPlayer(avPlayer).apply {
        contentsScale = UIScreen.mainScreen.scale
        masksToBounds = true
        val noAction = NSNull.`null`()
        actions = mapOf(
            "bounds" to noAction,
            "position" to noAction,
            "frame" to noAction,
            "opacity" to noAction,
            "hidden" to noAction,
        )
    }

    private var _isPlaying by mutableStateOf(false)
    actual val isPlaying: Boolean get() = _isPlaying
    private var _isBuffering by mutableStateOf(false)
    actual val isBuffering: Boolean get() = _isBuffering
    private var _isEnded by mutableStateOf(false)
    actual val isEnded: Boolean get() = _isEnded
    private var _hasRenderedFirstFrame by mutableStateOf(false)
    actual val hasRenderedFirstFrame: Boolean get() = _hasRenderedFirstFrame
    private var _isPosterReady by mutableStateOf(false)
    actual val isPosterReady: Boolean get() = _isPosterReady
    private var _isLayerReady by mutableStateOf(false)
    actual val isLayerReady: Boolean get() = _isLayerReady
    internal var stillImage by mutableStateOf<UIImage?>(null)
    internal var posterImage by mutableStateOf<UIImage?>(null)
    private var stillImageVersion: Int = 0
    private var stillImagePending: Boolean = false
    private var posterDecoded: Boolean = false
    private var _currentPositionMs by mutableLongStateOf(0L)
    actual val currentPositionMs: Long get() = _currentPositionMs
    private var _durationMs by mutableLongStateOf(0L)
    actual val durationMs: Long get() = _durationMs
    private var _bufferedPositionMs by mutableLongStateOf(0L)
    actual val bufferedPositionMs: Long get() = _bufferedPositionMs
    private var _videoWidth by mutableIntStateOf(0)
    actual val videoWidth: Int get() = _videoWidth
    private var _videoHeight by mutableIntStateOf(0)
    actual val videoHeight: Int get() = _videoHeight
    private var _errorMessage by mutableStateOf<String?>(null)
    actual val errorMessage: String? get() = _errorMessage
    private var _isSystemPipActive by mutableStateOf(false)
    actual val isSystemPipActive: Boolean get() = _isSystemPipActive

    private var desiredSpeed: Float = 1f
    private var desiredLooping: Boolean = false
    private var released: Boolean = false
    private var releasePendingAfterPip: Boolean = false
    private var systemMediaEnabled: Boolean = false
    private var mediaIsLive: Boolean = false

    private var pipController: AVPictureInPictureController? = null
    private var pipDelegate: IosPipDelegate? = null
    private var pipStateListener: ((Boolean) -> Unit)? = null

    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var failedObserver: Any? = null

    init {
        val weakSelf = WeakReference(this)
        dispatch_async(dispatch_get_main_queue()) {
            val self = weakSelf.get() ?: return@dispatch_async
            if (self.released) return@dispatch_async
            val interval = CMTimeMake(value = 1, timescale = 30)
            self.timeObserver = self.avPlayer.addPeriodicTimeObserverForInterval(interval, queue = dispatch_get_main_queue()) { _ ->
                weakSelf.get()?.pollState()
            }
        }
    }

    internal fun resetLayerReady() {
        _isLayerReady = false
    }

    private fun updatePosterReady() {
        _isPosterReady = stillImage != null || (posterDecoded && !stillImagePending)
    }

    actual fun setStillImage(image: CoilImage?) {
        val bitmap = (image as? BitmapImage)?.bitmap ?: return
        val skiaImage = runCatching { SkiaImage.makeFromBitmap(bitmap) }.getOrNull() ?: return
        val version = ++stillImageVersion
        stillImagePending = true
        updatePosterReady()
        val weakSelf = WeakReference(this)
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0uL)) {
            val bytes = runCatching {
                skiaImage.encodeToData(EncodedImageFormat.JPEG, 92)?.bytes
            }.getOrNull()
            val decoded = bytes?.let { UIImage.imageWithData(it.toNSData()) }
            dispatch_async(dispatch_get_main_queue()) {
                val self = weakSelf.get() ?: return@dispatch_async
                if (self.released || self.stillImageVersion != version) return@dispatch_async
                self.stillImagePending = false
                if (decoded != null) {
                    self.stillImage = decoded
                }
                self.updatePosterReady()
            }
        }
    }

    internal fun onSystemPipWillStart() {
        _isSystemPipActive = true
        pipStateListener?.invoke(true)
    }

    internal fun onSystemPipDidStart() {
        _isSystemPipActive = true
        ActivePipRetainer.retain(this)
        pipStateListener?.invoke(true)
    }

    internal fun onSystemPipDidStop() {
        _isSystemPipActive = false
        ActivePipRetainer.release(this)
        pipStateListener?.invoke(false)
        if (releasePendingAfterPip) {
            releasePendingAfterPip = false
            performFinalRelease()
        }
    }

    private fun ensurePipControllerOnMain() {
        if (!systemMediaEnabled || released) return
        if (!AVPictureInPictureController.isPictureInPictureSupported()) return
        val controller = pipController ?: AVPictureInPictureController(playerLayer = playerLayer).also { created ->
            val delegate = IosPipDelegate(WeakReference(this))
            created.delegate = delegate
            pipDelegate = delegate
            pipController = created
        }
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        controller.requiresLinearPlayback = mediaIsLive
    }

    actual fun configureSystemMedia(
        title: String,
        isLive: Boolean,
        enabled: Boolean,
    ) {
        mediaIsLive = isLive
        systemMediaEnabled = enabled
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            if (enabled) {
                ensureAudioSession()
                ensurePipControllerOnMain()
            } else {
                pipController?.canStartPictureInPictureAutomaticallyFromInline = false
            }
        }
    }

    actual fun setSystemPipStateListener(listener: ((Boolean) -> Unit)?) {
        pipStateListener = listener
    }

    actual fun startSystemPictureInPicture(): Boolean {
        if (released || !AVPictureInPictureController.isPictureInPictureSupported()) return false
        systemMediaEnabled = true
        ensureAudioSession()
        ensurePipControllerOnMain()
        val controller = pipController ?: return false
        if (controller.pictureInPictureActive) return true
        if (!controller.pictureInPicturePossible) return false
        controller.startPictureInPicture()
        return true
    }

    actual fun stopSystemPictureInPicture() {
        dispatch_async(dispatch_get_main_queue()) {
            val controller = pipController ?: return@dispatch_async
            if (controller.pictureInPictureActive) {
                controller.stopPictureInPicture()
            }
        }
    }

    private fun ensureAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeMoviePlayback, options = 0uL, error = null)
            session.setActive(true, error = null)
        }
    }

    private fun clearItemObservers() {
        val nc = NSNotificationCenter.defaultCenter
        endObserver?.let { nc.removeObserver(it) }
        failedObserver?.let { nc.removeObserver(it) }
        endObserver = null
        failedObserver = null
    }

    private fun attachItem(item: AVPlayerItem, looping: Boolean, muted: Boolean, autoPlay: Boolean) {
        clearItemObservers()
        desiredLooping = looping
        _isEnded = false
        _errorMessage = null
        _hasRenderedFirstFrame = false
        _isLayerReady = false
        _currentPositionMs = 0L
        _durationMs = 0L
        _bufferedPositionMs = 0L
        _videoWidth = 0
        _videoHeight = 0
        _isBuffering = true

        val weakSelf = WeakReference(this)
        val nc = NSNotificationCenter.defaultCenter
        endObserver = nc.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val self = weakSelf.get() ?: return@addObserverForName
            if (self.desiredLooping) {
                self.avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
                self.avPlayer.playImmediatelyAtRate(self.desiredSpeed)
                self._isEnded = false
            } else {
                self._isEnded = true
                self._isPlaying = false
            }
        }
        failedObserver = nc.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val self = weakSelf.get() ?: return@addObserverForName
            self._errorMessage = item.error?.localizedDescription ?: "视频播放失败"
            self._isBuffering = false
            self._isPlaying = false
        }

        avPlayer.muted = muted
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        if (systemMediaEnabled) {
            ensurePipControllerOnMain()
        }
        if (autoPlay) {
            if (!muted) ensureAudioSession()
            avPlayer.play()
            if (desiredSpeed != 1f) avPlayer.rate = desiredSpeed
            _isPlaying = true
        } else {
            avPlayer.pause()
            _isPlaying = false
        }
    }

    actual fun setMedia(
        url: String,
        headers: Map<String, String>,
        looping: Boolean,
        muted: Boolean,
        autoPlay: Boolean,
    ) {
        val nsUrl = NSURL.URLWithString(url) ?: run {
            _errorMessage = "无效视频地址"
            return
        }
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            posterImage = null
            _isPosterReady = true
            val options: Map<Any?, Any?>? = if (headers.isNotEmpty()) {
                mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
            } else {
                null
            }
            val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = options)
            val item = AVPlayerItem.playerItemWithAsset(asset)
            attachItem(item, looping = looping, muted = muted, autoPlay = autoPlay)
        }
    }

    actual fun setLocalMedia(
        filePath: String,
        looping: Boolean,
        muted: Boolean,
        autoPlay: Boolean,
    ) {
        val nsUrl = NSURL.fileURLWithPath(filePath)
        val weakSelf = WeakReference(this)
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            posterImage = null
            posterDecoded = false
            updatePosterReady()
            val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = null)
            val item = AVPlayerItem.playerItemWithAsset(asset)
            attachItem(item, looping = looping, muted = muted, autoPlay = autoPlay)

            // 异步解码本地实况视频首帧作为垫图，原生视图挂载瞬间直接显示首帧，零黑闪。
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0uL)) {
                val generator = AVAssetImageGenerator.assetImageGeneratorWithAsset(asset).apply {
                    appliesPreferredTrackTransform = true
                    requestedTimeToleranceBefore = CMTimeMake(value = 0, timescale = 600)
                    requestedTimeToleranceAfter = CMTimeMake(value = 0, timescale = 600)
                }
                val cg = runCatching {
                    generator.copyCGImageAtTime(
                        requestedTime = CMTimeMake(value = 0, timescale = 600),
                        actualTime = null,
                        error = null,
                    )
                }.getOrNull()
                val decoded = cg?.let {
                    try {
                        UIImage.imageWithCGImage(it)
                    } finally {
                        CGImageRelease(it)
                    }
                }
                dispatch_async(dispatch_get_main_queue()) {
                    val self = weakSelf.get() ?: return@dispatch_async
                    if (self.released) return@dispatch_async
                    self.posterImage = decoded
                    self.posterDecoded = true
                    self.updatePosterReady()
                }
            }
        }
    }

    actual fun play() {
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            if (!avPlayer.muted) ensureAudioSession()
            if (isEnded) {
                avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
                _isEnded = false
            }
            avPlayer.play()
            if (desiredSpeed != 1f) avPlayer.rate = desiredSpeed
            _isPlaying = true
        }
    }

    actual fun pause() {
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            avPlayer.pause()
            _isPlaying = false
        }
    }

    actual fun seekTo(positionMs: Long) {
        val sec = (positionMs.coerceAtLeast(0L)) / 1000.0
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            avPlayer.seekToTime(CMTimeMakeWithSeconds(sec, 600))
            _currentPositionMs = positionMs.coerceAtLeast(0L)
            _isEnded = false
        }
    }

    actual fun setSpeed(speed: Float) {
        desiredSpeed = speed.coerceIn(0.25f, 4f)
        dispatch_async(dispatch_get_main_queue()) {
            if (!released && isPlaying) {
                avPlayer.rate = desiredSpeed
            }
        }
    }

    actual fun setMuted(muted: Boolean) {
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            avPlayer.muted = muted
            if (!muted && _isPlaying) {
                ensureAudioSession()
            }
        }
    }

    actual fun setVolume(volume: Float) {
        dispatch_async(dispatch_get_main_queue()) {
            if (!released) avPlayer.volume = volume.coerceIn(0f, 1f)
        }
    }

    actual fun setLooping(looping: Boolean) {
        desiredLooping = looping
    }

    actual fun stop() {
        dispatch_async(dispatch_get_main_queue()) {
            if (released) return@dispatch_async
            clearItemObservers()
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
            _isPlaying = false
            _isBuffering = false
            _isEnded = false
            _hasRenderedFirstFrame = false
            _isLayerReady = false
        }
    }

    private fun performFinalRelease() {
        if (released) return
        released = true
        dispatch_async(dispatch_get_main_queue()) {
            clearItemObservers()
            timeObserver?.let { avPlayer.removeTimeObserver(it) }
            timeObserver = null
            pipController?.delegate = null
            pipController = null
            pipDelegate = null
            pipStateListener = null
            ActivePipRetainer.release(this)
            stillImage = null
            posterImage = null
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
            playerLayer.removeFromSuperlayer()
        }
    }

    actual fun release() {
        if (released) return
        if (_isSystemPipActive || pipController?.pictureInPictureActive == true) {
            // 系统画中画仍在桌面运行时，延迟到画中画关闭后再释放，避免切走页面杀掉画中画。
            releasePendingAfterPip = true
            return
        }
        performFinalRelease()
    }

    private fun pollState() {
        if (released) return
        val item = avPlayer.currentItem ?: return
        val curSec = CMTimeGetSeconds(avPlayer.currentTime())
        if (curSec.isFinite() && curSec >= 0.0) {
            _currentPositionMs = (curSec * 1000.0).toLong()
        }
        val durSec = CMTimeGetSeconds(item.duration)
        if (durSec.isFinite() && durSec > 0.0) {
            _durationMs = (durSec * 1000.0).toLong()
        }
        var maxBuf = currentPositionMs
        item.loadedTimeRanges.forEach { raw ->
            val v = raw as? NSValue ?: return@forEach
            val endSec = v.CMTimeRangeValue.useContents {
                CMTimeGetSeconds(this.start.readValue()) + CMTimeGetSeconds(this.duration.readValue())
            }
            if (endSec.isFinite() && endSec > 0.0) {
                maxBuf = max(maxBuf, (endSec * 1000.0).toLong())
            }
        }
        _bufferedPositionMs = maxBuf

        when (item.status) {
            AVPlayerItemStatusReadyToPlay -> {
                if (playerLayer.readyForDisplay) {
                    _isLayerReady = true
                    _hasRenderedFirstFrame = true
                } else if (avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying && _currentPositionMs > 0L) {
                    _hasRenderedFirstFrame = true
                }
                item.presentationSize.useContents {
                    if (width > 0.0 && height > 0.0) {
                        _videoWidth = width.toInt()
                        _videoHeight = height.toInt()
                    }
                }
            }
            AVPlayerItemStatusFailed -> {
                _errorMessage = item.error?.localizedDescription ?: "视频加载失败"
                _isBuffering = false
                _isPlaying = false
            }
        }

        val status = avPlayer.timeControlStatus
        _isBuffering = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
        _isPlaying = status == AVPlayerTimeControlStatusPlaying || avPlayer.rate > 0f
    }
}

private class CoboPlayerContainerView(
    private val playerLayer: AVPlayerLayer,
    transparentBackground: Boolean,
) : UIView(frame = CGRectZero.readValue()) {
    private val posterImageView = UIImageView(frame = CGRectZero.readValue()).apply {
        clipsToBounds = true
        userInteractionEnabled = false
        hidden = true
    }
    private var lastWidth = -1.0
    private var lastHeight = -1.0

    init {
        opaque = !transparentBackground
        backgroundColor = if (transparentBackground) UIColor.clearColor else UIColor.blackColor
        playerLayer.backgroundColor = null
        clipsToBounds = true
        userInteractionEnabled = false
        addSubview(posterImageView)
        attachLayer()
    }

    fun applyAppearance(
        transparentBackground: Boolean,
        targetAlpha: Float,
        backdropImage: UIImage?,
        fillCrop: Boolean,
        layerReady: Boolean,
    ) {
        val wantOpaque = !transparentBackground
        if (opaque != wantOpaque) {
            opaque = wantOpaque
            backgroundColor = if (transparentBackground) UIColor.clearColor else UIColor.blackColor
        }
        val clamped = targetAlpha.coerceIn(0f, 1f)
        if (backdropImage != null) {
            // 实况图淡入淡出模式：容器保持不透明，底部垫原生静图，仅对 AVPlayerLayer 做 0→1 / 1→0 透明度渐变。
            if (abs(alpha - 1.0) > 0.002) {
                alpha = 1.0
            }
            val targetMode = if (fillCrop) {
                UIViewContentMode.UIViewContentModeScaleAspectFill
            } else {
                UIViewContentMode.UIViewContentModeScaleAspectFit
            }
            if (posterImageView.contentMode != targetMode) {
                posterImageView.contentMode = targetMode
            }
            if (posterImageView.image?.objcPtr() != backdropImage.objcPtr()) {
                posterImageView.image = backdropImage
            }
            if (posterImageView.hidden) {
                posterImageView.hidden = false
            }
            val layerOpacity = if (!layerReady && clamped <= 0.002f) 0.01f else clamped
            if (abs(playerLayer.opacity - layerOpacity) > 0.002f) {
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                playerLayer.opacity = layerOpacity
                CATransaction.commit()
            }
        } else {
            if (!posterImageView.hidden) {
                posterImageView.hidden = true
            }
            if (posterImageView.image != null) {
                posterImageView.image = null
            }
            if (abs(playerLayer.opacity - 1f) > 0.002f) {
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                playerLayer.opacity = 1f
                CATransaction.commit()
            }
            val targetViewAlpha = clamped.toDouble()
            if (abs(alpha - targetViewAlpha) > 0.002) {
                alpha = targetViewAlpha
            }
        }
    }

    fun attachLayer(requestLayout: Boolean = true) {
        if (playerLayer.superlayer?.objcPtr() != layer.objcPtr()) {
            playerLayer.removeFromSuperlayer()
            layer.addSublayer(playerLayer)
            lastWidth = -1.0
            lastHeight = -1.0
            if (requestLayout) {
                setNeedsLayout()
            }
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        attachLayer(requestLayout = false)
        val w = CGRectGetWidth(bounds)
        val h = CGRectGetHeight(bounds)
        if (w == lastWidth && h == lastHeight) return
        lastWidth = w
        lastHeight = h
        val animDuration = UIView.inheritedAnimationDuration
        CATransaction.begin()
        if (animDuration > 0.0) {
            CATransaction.setAnimationDuration(animDuration)
        } else {
            CATransaction.setDisableActions(true)
        }
        posterImageView.setFrame(bounds)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformVideoSurface(
    player: PlatformVideoPlayer,
    fillCrop: Boolean,
    modifier: Modifier,
    transparentBackground: Boolean,
    alpha: Float,
) {
    val playerLayer = player.playerLayer
    val backdropImage = player.stillImage ?: player.posterImage
    val layerReady = player.isLayerReady
    DisposableEffect(player) {
        onDispose {
            player.resetLayerReady()
        }
    }
    UIKitView(
        factory = {
            CoboPlayerContainerView(playerLayer, transparentBackground).apply {
                applyAppearance(transparentBackground, alpha, backdropImage, fillCrop, layerReady)
                playerLayer.videoGravity = if (fillCrop) {
                    AVLayerVideoGravityResizeAspectFill
                } else {
                    AVLayerVideoGravityResizeAspect
                }
            }
        },
        update = { view ->
            view.attachLayer()
            view.applyAppearance(transparentBackground, alpha, backdropImage, fillCrop, layerReady)
            val targetGravity = if (fillCrop) {
                AVLayerVideoGravityResizeAspectFill
            } else {
                AVLayerVideoGravityResizeAspect
            }
            if (playerLayer.videoGravity != targetGravity) {
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                playerLayer.videoGravity = targetGravity
                CATransaction.commit()
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false,
        ),
    )
}
