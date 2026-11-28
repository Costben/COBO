@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.cobo.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.*
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.NSItemProviderReadingProtocol
import platform.Foundation.appendData
import platform.Foundation.setValue
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieSecure
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.MediaPlayer.MPVolumeView
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypeFullSizePairedVideo
import platform.Photos.PHAssetResourceTypeFullSizePhoto
import platform.Photos.PHAssetResourceTypePairedVideo
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHLivePhoto
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.*
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

actual fun copyToClipboard(text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

internal fun findKeyWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.windows
    return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        ?: UIApplication.sharedApplication.keyWindow
        ?: windows.firstOrNull() as? UIWindow
}

internal fun topViewController(): UIViewController? {
    var top = findKeyWindow()?.rootViewController ?: return null
    while (true) {
        val presented = top.presentedViewController ?: break
        top = presented
    }
    return top
}

private fun presentShareItems(items: List<Any>) {
    dispatch_async(dispatch_get_main_queue()) {
        val presenter = topViewController() ?: return@dispatch_async
        val controller = UIActivityViewController(
            activityItems = items,
            applicationActivities = null,
        )
        controller.popoverPresentationController?.let { popover ->
            val anchorView = presenter.view
            popover.sourceView = anchorView
            val (w, h) = anchorView.bounds.useContents { size.width to size.height }
            popover.sourceRect = CGRectMake(w / 2.0, h / 2.0, 1.0, 1.0)
            popover.permittedArrowDirections = 0uL
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}

actual fun shareText(text: String, title: String) {
    presentShareItems(listOf(text))
}

actual fun shareImageBytes(bytes: ByteArray, fileName: String, mime: String) {
    val image = UIImage.imageWithData(bytes.toNSData())
    if (image != null) {
        presentShareItems(listOf(image))
    } else {
        val tmp = appCacheDir().child("share_$fileName")
        if (tmp.writeBytes(bytes)) {
            presentShareItems(listOf(NSURL.fileURLWithPath(tmp.path)))
        }
    }
}

actual fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    val nsString = NSString.create(string = content)
    val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return null
    val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
    filter.setValue(data, forKey = "inputMessage")
    filter.setValue("M", forKey = "inputCorrectionLevel")
    val output = filter.outputImage ?: return null
    val extentWidth = output.extent.useContents { size.width }.coerceAtLeast(1.0)
    val scale = sizePx.toDouble() / extentWidth
    val scaled = output.imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
    val ciContext = CIContext.context()
    val cgImage = ciContext.createCGImage(scaled, fromRect = scaled.extent) ?: return null
    return try {
        val uiImage = UIImage.imageWithCGImage(cgImage)
        val pngData = UIImagePNGRepresentation(uiImage) ?: return null
        Image.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
    } finally {
        CGImageRelease(cgImage)
    }
}

actual fun loadAppIconBitmap(darkTheme: Boolean): ImageBitmap? = runCatching {
    val bundle = NSBundle.mainBundle
    val trait = UITraitCollection.traitCollectionWithUserInterfaceStyle(
        if (darkTheme) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight,
    )
    val infoIcons = (bundle.infoDictionary?.get("CFBundleIcons") as? Map<*, *>)
        ?.get("CFBundlePrimaryIcon") as? Map<*, *>
    val iconFiles = (infoIcons?.get("CFBundleIconFiles") as? List<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()
    val candidates = buildList {
        addAll(iconFiles.asReversed())
        add("AppIcon")
        add("AppIcon60x60")
    }.distinct()
    val uiImage = candidates.firstNotNullOfOrNull { name ->
        UIImage.imageNamed(name, inBundle = bundle, compatibleWithTraitCollection = trait)
            ?: UIImage.imageNamed(name)
    } ?: return null
    val pngData = UIImagePNGRepresentation(uiImage) ?: return null
    Image.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
}.getOrNull()

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    if (bytes.isEmpty()) return null
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray? = runCatching {
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
}.getOrNull()

actual fun decodeImageDimensions(bytes: ByteArray): Pair<Int, Int>? = runCatching {
    if (bytes.isEmpty()) return null
    val img = Image.makeFromEncoded(bytes)
    img.width to img.height
}.getOrNull()

actual fun cropAndEncodeJpeg(
    bytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    sizeFrac: Float,
    outputPx: Int,
    quality: Int,
): ByteArray? = runCatching {
    val srcImage = UIImage.imageWithData(bytes.toNSData()) ?: return null
    val cgSrc = srcImage.CGImage ?: return null
    val srcW = CGImageGetWidth(cgSrc).toDouble()
    val srcH = CGImageGetHeight(cgSrc).toDouble()
    if (srcW <= 0.0 || srcH <= 0.0) return null
    val cropX = (leftFrac.toDouble() * srcW).coerceIn(0.0, srcW - 1.0)
    val cropY = (topFrac.toDouble() * srcH).coerceIn(0.0, srcH - 1.0)
    val cropSide = (sizeFrac.toDouble() * minOf(srcW, srcH)).coerceIn(1.0, minOf(srcW - cropX, srcH - cropY))
    val croppedCg = CGImageCreateWithImageInRect(cgSrc, CGRectMake(cropX, cropY, cropSide, cropSide)) ?: return null
    try {
        val croppedUi = UIImage.imageWithCGImage(croppedCg)
        val targetSide = outputPx.coerceAtLeast(64).toDouble()
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetSide, targetSide), true, 1.0)
        try {
            croppedUi.drawInRect(CGRectMake(0.0, 0.0, targetSide, targetSide))
            val resized = UIGraphicsGetImageFromCurrentImageContext() ?: return null
            val jpg = UIImageJPEGRepresentation(resized, (quality.coerceIn(10, 100)) / 100.0) ?: return null
            jpg.toByteArray()
        } finally {
            UIGraphicsEndImageContext()
        }
    } finally {
        CGImageRelease(croppedCg)
    }
}.getOrNull()

private suspend fun ensurePhotoAddPermission(): Unit {
    val authStatus = suspendCancellableCoroutine<Long> { cont ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (cont.isActive) cont.resume(status)
        }
    }
    if (authStatus != PHAuthorizationStatusAuthorized && authStatus != PHAuthorizationStatusLimited) {
        error("未授予相册写入权限")
    }
}

actual suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String, mime: String): Result<Unit> =
    runCatching {
        ensurePhotoAddPermission()
        val data = bytes.toNSData()
        suspendCancellableCoroutine<Unit> { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    val req = PHAssetCreationRequest.creationRequestForAsset()
                    val opts = PHAssetResourceCreationOptions().apply {
                        originalFilename = fileName
                    }
                    req.addResourceWithType(PHAssetResourceTypePhoto, data = data, options = opts)
                },
                completionHandler = { success, error ->
                    if (!cont.isActive) return@performChanges
                    if (success) cont.resume(Unit)
                    else cont.resumeWithException(
                        IllegalStateException(error?.localizedDescription?.takeIf { it.isNotBlank() } ?: "保存到相册失败"),
                    )
                },
            )
        }
    }

actual suspend fun saveVideoBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit> =
    runCatching {
        ensurePhotoAddPermission()
        val ext = fileName.substringAfterLast('.', "mp4").ifBlank { "mp4" }
        val tmpFile = appCacheDir().child("save_video_${currentTimeMillis()}.$ext")
        require(tmpFile.writeBytes(bytes)) { "写入临时视频失败" }
        val fileUrl = NSURL.fileURLWithPath(tmpFile.path)
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    changeBlock = {
                        PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(fileUrl)
                    },
                    completionHandler = { success, error ->
                        if (!cont.isActive) return@performChanges
                        if (success) cont.resume(Unit)
                        else cont.resumeWithException(
                            IllegalStateException(error?.localizedDescription?.takeIf { it.isNotBlank() } ?: "保存视频失败"),
                        )
                    },
                )
            }
        } finally {
            tmpFile.delete()
        }
    }

actual suspend fun saveLivePhotoToGallery(imageBytes: ByteArray, videoBytes: ByteArray): Result<Unit> =
    runCatching {
        ensurePhotoAddPermission()
        val ts = currentTimeMillis()
        val tmpImg = appCacheDir().child("live_$ts.jpg")
        val tmpMov = appCacheDir().child("live_$ts.mov")
        require(tmpImg.writeBytes(imageBytes)) { "写入实况图片失败" }
        require(tmpMov.writeBytes(videoBytes)) { "写入实况视频失败" }
        val imgUrl = NSURL.fileURLWithPath(tmpImg.path)
        val movUrl = NSURL.fileURLWithPath(tmpMov.path)
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    changeBlock = {
                        val req = PHAssetCreationRequest.creationRequestForAsset()
                        req.addResourceWithType(PHAssetResourceTypePhoto, fileURL = imgUrl, options = null)
                        req.addResourceWithType(PHAssetResourceTypePairedVideo, fileURL = movUrl, options = null)
                    },
                    completionHandler = { success, error ->
                        if (!cont.isActive) return@performChanges
                        if (success) cont.resume(Unit)
                        else cont.resumeWithException(
                            IllegalStateException(error?.localizedDescription?.takeIf { it.isNotBlank() } ?: "保存实况照片失败"),
                        )
                    },
                )
            }
        } finally {
            tmpImg.delete()
            tmpMov.delete()
        }
    }

private fun ByteArray.matchesBytes(offset: Int, vararg expected: Int): Boolean =
    size >= offset + expected.size && expected.indices.all { i -> (this[offset + i].toInt() and 0xFF) == expected[i] }

private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif")

private fun detectImageMimeAndExt(bytes: ByteArray, typeIds: List<String>): Pair<String, String> {
    when {
        bytes.matchesBytes(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> return "image/png" to "png"
        bytes.matchesBytes(0, 0x47, 0x49, 0x46, 0x38) -> return "image/gif" to "gif"
        bytes.matchesBytes(0, 0x52, 0x49, 0x46, 0x46) && bytes.matchesBytes(8, 0x57, 0x45, 0x42, 0x50) ->
            return "image/webp" to "webp"
        bytes.size >= 12 && bytes.matchesBytes(4, 0x66, 0x74, 0x79, 0x70) &&
            bytes.copyOfRange(8, 12).decodeToString().lowercase() in HEIC_BRANDS ->
            return "image/heic" to "heic"
        bytes.matchesBytes(0, 0xFF, 0xD8, 0xFF) -> return "image/jpeg" to "jpg"
    }
    val lowered = typeIds.map { it.lowercase() }
    return when {
        lowered.any { "png" in it } -> "image/png" to "png"
        lowered.any { "webp" in it } -> "image/webp" to "webp"
        lowered.any { "gif" in it } -> "image/gif" to "gif"
        lowered.any { "heic" in it || "heif" in it } -> "image/heic" to "heic"
        else -> "image/jpeg" to "jpg"
    }
}

private fun normalizePickedStillImage(
    bytes: ByteArray,
    typeIds: List<String>,
    suggestedName: String?,
    idx: Int,
): Triple<ByteArray, String, String> {
    var finalBytes = bytes
    var (mime, ext) = detectImageMimeAndExt(bytes, typeIds)
    if (mime == "image/heic" || mime == "image/heif" || ext == "heic" || ext == "heif") {
        val converted = runCatching {
            val img = UIImage.imageWithData(bytes.toNSData()) ?: return@runCatching null
            UIImageJPEGRepresentation(img, 0.92)?.toByteArray()
        }.getOrNull()
        if (converted != null && converted.isNotEmpty()) {
            finalBytes = converted
            mime = "image/jpeg"
            ext = "jpg"
        }
    }
    val fileName = when {
        suggestedName == null -> "image_$idx.$ext"
        suggestedName.contains('.') -> {
            if (ext == "jpg" && (suggestedName.endsWith(".heic", ignoreCase = true) || suggestedName.endsWith(".heif", ignoreCase = true))) {
                suggestedName.substringBeforeLast('.') + ".jpg"
            } else {
                suggestedName
            }
        }
        else -> "$suggestedName.$ext"
    }
    return Triple(finalBytes, mime, fileName)
}

private fun requestAssetResourceBytes(
    resource: PHAssetResource,
    onDone: (ByteArray?) -> Unit,
) {
    val buffer = NSMutableData()
    val options = PHAssetResourceRequestOptions().apply {
        networkAccessAllowed = true
    }
    PHAssetResourceManager.defaultManager().requestDataForAssetResource(
        resource = resource,
        options = options,
        dataReceivedHandler = { chunk ->
            if (chunk != null) buffer.appendData(chunk)
        },
        completionHandler = { error ->
            if (error == null && buffer.length > 0uL) {
                onDone(buffer.toByteArray())
            } else {
                onDone(null)
            }
        },
    )
}

private fun loadStillImageFromProvider(
    idx: Int,
    provider: platform.Foundation.NSItemProvider,
    suggestedName: String?,
    typeIds: List<String>,
    onDone: (PickedMedia?) -> Unit,
) {
    provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
        val item = if (data != null && data.length > 0uL) {
            val (finalBytes, mime, fileName) = normalizePickedStillImage(
                bytes = data.toByteArray(),
                typeIds = typeIds,
                suggestedName = suggestedName,
                idx = idx,
            )
            PickedMedia(
                id = "${randomUuid()}-$idx",
                fileName = fileName,
                mime = mime,
                bytes = finalBytes,
            )
        } else {
            null
        }
        onDone(item)
    }
}

private fun phLivePhotoReadingClass(): NSItemProviderReadingProtocol =
    interpretObjCPointer<NSItemProviderReadingProtocol>(PHLivePhoto.objcPtr())

private fun loadPickedMediaFromProvider(
    idx: Int,
    provider: platform.Foundation.NSItemProvider,
    onDone: (PickedMedia?) -> Unit,
) {
    val suggestedName = provider.suggestedName?.trim()?.takeIf { it.isNotEmpty() }
    val typeIds = provider.registeredTypeIdentifiers.filterIsInstance<String>()
    val livePhotoClass = phLivePhotoReadingClass()
    val canLoadLivePhoto = runCatching { provider.canLoadObjectOfClass(livePhotoClass) }.getOrDefault(false)
    if (!canLoadLivePhoto) {
        loadStillImageFromProvider(idx, provider, suggestedName, typeIds, onDone)
        return
    }
    provider.loadObjectOfClass(livePhotoClass) { obj, _ ->
        val livePhoto = obj as? PHLivePhoto
        if (livePhoto == null) {
            loadStillImageFromProvider(idx, provider, suggestedName, typeIds, onDone)
            return@loadObjectOfClass
        }
        val resources = PHAssetResource.assetResourcesForLivePhoto(livePhoto).filterIsInstance<PHAssetResource>()
        val photoRes = resources.firstOrNull { it.type == PHAssetResourceTypeFullSizePhoto }
            ?: resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
        val videoRes = resources.firstOrNull { it.type == PHAssetResourceTypeFullSizePairedVideo }
            ?: resources.firstOrNull { it.type == PHAssetResourceTypePairedVideo }
        if (photoRes == null || videoRes == null) {
            loadStillImageFromProvider(idx, provider, suggestedName, typeIds, onDone)
            return@loadObjectOfClass
        }
        requestAssetResourceBytes(photoRes) { photoBytes ->
            if (photoBytes == null || photoBytes.isEmpty()) {
                loadStillImageFromProvider(idx, provider, suggestedName, typeIds, onDone)
                return@requestAssetResourceBytes
            }
            requestAssetResourceBytes(videoRes) { videoBytes ->
                val (finalStill, mime, fileName) = normalizePickedStillImage(
                    bytes = photoBytes,
                    typeIds = typeIds,
                    suggestedName = suggestedName,
                    idx = idx,
                )
                onDone(
                    PickedMedia(
                        id = "${randomUuid()}-$idx",
                        fileName = fileName,
                        mime = mime,
                        bytes = finalStill,
                        liveVideoBytes = videoBytes?.takeIf { it.isNotEmpty() },
                        liveVideoMime = "video/quicktime",
                        liveVideoFileName = "live_$idx.mov",
                    ),
                )
            }
        }
    }
}

@Composable
actual fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedMedia>) -> Unit,
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                if (results.isEmpty()) return
                val collected = arrayOfNulls<PickedMedia>(results.size)
                var remaining = results.size
                results.forEachIndexed { idx, res ->
                    loadPickedMediaFromProvider(idx, res.itemProvider) { item ->
                        dispatch_async(dispatch_get_main_queue()) {
                            if (item != null) collected[idx] = item
                            remaining--
                            if (remaining == 0) {
                                val out = collected.filterNotNull()
                                if (out.isNotEmpty()) currentOnPicked.value(out)
                            }
                        }
                    }
                }
            }
        }
    }
    return {
        dispatch_async(dispatch_get_main_queue()) {
            val config = PHPickerConfiguration().apply {
                selectionLimit = maxSelection.toLong()
                filter = PHPickerFilter.imagesFilter()
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private fun postNotification(name: String, key: String, value: NSNumber) {
    dispatch_async(dispatch_get_main_queue()) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = name,
            `object` = null,
            userInfo = mapOf(key to value),
        )
    }
}

actual fun setScreenOrientation(orientation: PlatformOrientation) {
    val mask = when (orientation) {
        PlatformOrientation.Portrait -> UIInterfaceOrientationMaskPortrait
        PlatformOrientation.Landscape -> UIInterfaceOrientationMaskLandscape
        PlatformOrientation.LandscapeLeft -> UIInterfaceOrientationMaskLandscapeLeft
        PlatformOrientation.LandscapeRight -> UIInterfaceOrientationMaskLandscapeRight
        PlatformOrientation.Unspecified -> UIInterfaceOrientationMaskAllButUpsideDown
    }
    postNotification("CoboOrientationChanged", "mask", NSNumber(unsignedLong = mask))
}

actual fun setSystemBarsVisible(visible: Boolean) {
    postNotification("CoboSystemBarsChanged", "visible", NSNumber(bool = visible))
}

actual fun setStatusBarDarkIcons(darkIcons: Boolean?) {
    val styleValue = when (darkIcons) {
        true -> 1
        false -> 0
        null -> -1
    }
    postNotification("CoboStatusBarStyleChanged", "darkIcons", NSNumber(int = styleValue))
}

internal actual fun platformSystemBarsApplier(): io.github.suqi8.coui.kmp.utils.CouiSystemBarsApplier? =
    io.github.suqi8.coui.kmp.utils.CouiSystemBarsApplier { style ->
        setStatusBarDarkIcons(style.darkStatusBarIcons)
        setSystemBarsVisible(style.statusBarVisible && style.navigationBarVisible)
    }

actual fun keepScreenOn(enabled: Boolean) {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

private var savedIosScreenBrightness: Double? = null

actual fun readScreenBrightness(): Float = UIScreen.mainScreen.brightness.toFloat().coerceIn(0.02f, 1f)

actual fun writeScreenBrightness(value: Float) {
    dispatch_async(dispatch_get_main_queue()) {
        if (value < 0f) {
            savedIosScreenBrightness?.let { UIScreen.mainScreen.brightness = it }
            savedIosScreenBrightness = null
        } else {
            if (savedIosScreenBrightness == null) savedIosScreenBrightness = UIScreen.mainScreen.brightness
            UIScreen.mainScreen.brightness = value.coerceIn(0.02f, 1f).toDouble()
        }
    }
}

private object IosVolumeBridge {
    private var volumeView: MPVolumeView? = null
    private var detachTimer: NSTimer? = null
    private var latestTargetVolume: Float? = null

    private fun ensureAttached(): UISlider? {
        val view = volumeView ?: MPVolumeView(
            frame = CGRectMake(-2000.0, -2000.0, 1.0, 1.0),
        ).apply {
            alpha = 0.01
            clipsToBounds = true
            userInteractionEnabled = false
            volumeView = this
        }
        val window = findKeyWindow()
        if (window != null && view.superview?.objcPtr() != window.objcPtr()) {
            view.removeFromSuperview()
            window.addSubview(view)
            view.layoutIfNeeded()
        }
        return findSlider(view)
    }

    private fun findSlider(view: UIView): UISlider? {
        view.subviews.forEach { raw ->
            if (raw is UISlider) return raw
            if (raw is UIView) findSlider(raw)?.let { return it }
        }
        return null
    }

    private fun scheduleDetach() {
        detachTimer?.invalidate()
        detachTimer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 1.6,
            repeats = false,
            block = { _ ->
                detachTimer = null
                latestTargetVolume = null
                volumeView?.removeFromSuperview()
            },
        )
    }

    fun read(): Float {
        dispatch_async(dispatch_get_main_queue()) {
            ensureAttached()
            scheduleDetach()
        }
        return AVAudioSession.sharedInstance().outputVolume.coerceIn(0f, 1f)
    }

    fun write(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        latestTargetVolume = clamped
        dispatch_async(dispatch_get_main_queue()) {
            val target = latestTargetVolume ?: clamped
            val slider = ensureAttached()
            if (slider != null) {
                slider.setValue(target, animated = false)
                slider.sendActionsForControlEvents(UIControlEventValueChanged)
            }
            dispatch_async(dispatch_get_main_queue()) {
                val pending = latestTargetVolume ?: return@dispatch_async
                val s = ensureAttached() ?: return@dispatch_async
                if (abs(s.value - pending) > 0.005f) {
                    s.setValue(pending, animated = false)
                    s.sendActionsForControlEvents(UIControlEventValueChanged)
                }
            }
            scheduleDetach()
        }
    }
}

actual fun readSystemVolume(): Float = IosVolumeBridge.read()

actual fun writeSystemVolume(value: Float) = IosVolumeBridge.write(value)

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val dispatcher = LocalBackDispatcher.current ?: return
    val routeIsTop = LocalBackRouteIsTop.current
    val currentEnabled = rememberUpdatedState(enabled && routeIsTop)
    val currentOnBack = rememberUpdatedState(onBack)
    DisposableEffect(dispatcher) {
        val unregister = dispatcher.register(
            isEnabled = { currentEnabled.value },
            onBack = { currentOnBack.value() },
        )
        onDispose { unregister() }
    }
}

private class IosLocationDelegate(
    private val onAuthChanged: () -> Unit,
    private val onLocation: (Pair<Double, Double>?) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthChanged()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        val coords = loc?.coordinate?.useContents { latitude to longitude }
        onLocation(coords)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onLocation(null)
    }
}

private object IosLocationHelper {
    private val manager = CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyHundredMeters
    }
    private var pendingPermCallback: ((Boolean) -> Unit)? = null
    private var pendingLocCallback: ((Pair<Double, Double>?) -> Unit)? = null

    private val delegate = IosLocationDelegate(
        onAuthChanged = {
            val cb = pendingPermCallback
            if (cb != null) {
                pendingPermCallback = null
                cb(hasPermission())
            }
        },
        onLocation = { coords ->
            val cb = pendingLocCallback
            pendingLocCallback = null
            cb?.invoke(coords)
        },
    )

    init {
        manager.delegate = delegate
    }

    fun hasPermission(): Boolean {
        val status: CLAuthorizationStatus = manager.authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            if (hasPermission()) {
                onResult(true)
            } else {
                pendingPermCallback = onResult
                manager.requestWhenInUseAuthorization()
            }
        }
    }

    suspend fun currentCoords(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val cached = manager.location
        if (cached != null) {
            return cached.coordinate.useContents { latitude to longitude }
        }
        return suspendCancellableCoroutine { cont ->
            dispatch_async(dispatch_get_main_queue()) {
                pendingLocCallback = { coords ->
                    if (cont.isActive) cont.resume(coords)
                }
                manager.requestLocation()
            }
        }
    }
}

actual suspend fun platformDeviceCoords(): Pair<Double, Double>? = IosLocationHelper.currentCoords()

actual fun hasLocationPermission(): Boolean = IosLocationHelper.hasPermission()

@Composable
actual fun rememberLocationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return { IosLocationHelper.requestPermission { granted -> currentOnResult.value(granted) } }
}

actual fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private val CausticDarkColor = Color(0x33FFFFFF)
private val CausticBottomOffset = 4.3.dp
private val CausticSdfWidth = 9.23.dp

private const val CAUSTIC_SKSL = """
uniform float2 in_half;
uniform float in_corner;
uniform float in_bottom;
uniform float in_sdf;
uniform float4 in_color;
uniform float3 in_background;
float RBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}
half4 main(float2 pos) {
    float2 pos_optics = pos - in_half - float2(0.0, in_bottom * 0.5);
    float2 halfSizeOptics = in_half + float2(0.0, in_bottom * 0.5);
    float sdf = min(in_sdf, min(in_half.x, in_half.y));
    if (in_color.a <= 0.001 || sdf <= 0.0 || any(greaterThan(abs(pos_optics), halfSizeOptics))) {
        return half4(0.0);
    }
    halfSizeOptics = max(halfSizeOptics - sdf, float2(0.0));
    float cornerOptics = min(in_corner, min(halfSizeOptics.x, halfSizeOptics.y));
    float opticsSdf = RBox(pos_optics, halfSizeOptics, cornerOptics);
    float value = smoothstep(sdf, 0.0, opticsSdf);
    float fade = smoothstep(halfSizeOptics.y + sdf, -halfSizeOptics.y - sdf, -pos_optics.y);
    value *= fade > 0.0 ? pow(fade, 18.0) : 0.0;
    value = (value > 0.0 ? pow(value, 0.6) : 0.0) * 0.6 + 0.4 * value;
    float gray = clamp((dot(float3(0.299, 0.587, 0.114), in_background) - 0.45) * 0.6, 0.0, 1.0);
    float alpha = in_color.a * value * (1.0 - gray) * (1.0 - gray);
    return half4(in_color.rgb * alpha, alpha);
}
"""

@Composable
actual fun Modifier.platformCausticLight(): Modifier {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(CAUSTIC_SKSL) }.getOrNull() }
        ?: return this
    val background = COUITheme.colorScheme.background
    return drawBehind {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawBehind
        val bottom = CausticBottomOffset.toPx().toInt().toFloat()
        val sdf = CausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("in_half", halfW, halfH)
            uniform("in_corner", cornerPx)
            uniform("in_bottom", bottom)
            uniform("in_sdf", sdf)
            uniform("in_color", CausticDarkColor.red, CausticDarkColor.green, CausticDarkColor.blue, CausticDarkColor.alpha)
            uniform("in_background", background.red, background.green, background.blue)
        }
        drawRect(
            brush = ShaderBrush(builder.makeShader().asComposeShader()),
            size = Size(size.width, size.height + bottom),
            blendMode = BlendMode.Plus,
        )
    }
}

actual fun defaultWebUserAgent(): String =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

actual object WebCookieBridge {
    private val lock = NSLock()
    private val cached = LinkedHashMap<String, String>()

    actual fun updateCachedDomains(domains: Map<String, String>) {
        lock.lock()
        try {
            cached.clear()
            cached.putAll(domains)
        } finally {
            lock.unlock()
        }
    }

    actual fun readDomains(): Map<String, String> {
        lock.lock()
        return try {
            LinkedHashMap(cached)
        } finally {
            lock.unlock()
        }
    }

    actual fun inject(cookies: Map<String, String>) {
        updateCachedDomains(cookies)
        dispatch_async(dispatch_get_main_queue()) {
            val dataStore = WKWebsiteDataStore.defaultDataStore()
            val cookieStore = dataStore.httpCookieStore
            cookieStore.getAllCookies { existing ->
                val list = existing?.filterIsInstance<NSHTTPCookie>().orEmpty()
                if (list.isEmpty()) {
                    writeCookiesToStore(cookieStore, cookies)
                } else {
                    var remaining = list.size
                    list.forEach { old ->
                        cookieStore.deleteCookie(old) {
                            remaining--
                            if (remaining == 0) writeCookiesToStore(cookieStore, cookies)
                        }
                    }
                }
            }
        }
    }

    actual fun plant(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        lock.lock()
        try {
            cookies.forEach { (k, v) -> cached[k] = v }
        } finally {
            lock.unlock()
        }
        dispatch_async(dispatch_get_main_queue()) {
            writeCookiesToStore(WKWebsiteDataStore.defaultDataStore().httpCookieStore, cookies)
        }
    }

    actual fun clearAll(onDone: (() -> Unit)?) {
        updateCachedDomains(emptyMap())
        dispatch_async(dispatch_get_main_queue()) {
            val store = WKWebsiteDataStore.defaultDataStore()
            store.fetchDataRecordsOfTypes(WKWebsiteDataStore.allWebsiteDataTypes()) { records ->
                @Suppress("UNCHECKED_CAST")
                val typed = records as? List<Any> ?: emptyList()
                if (typed.isEmpty()) {
                    onDone?.invoke()
                } else {
                    store.removeDataOfTypes(
                        WKWebsiteDataStore.allWebsiteDataTypes(),
                        forDataRecords = typed,
                        completionHandler = { onDone?.invoke() },
                    )
                }
            }
        }
    }

    internal fun writeCookiesToStore(
        cookieStore: platform.WebKit.WKHTTPCookieStore,
        cookies: Map<String, String>,
        onAllWritten: (() -> Unit)? = null,
    ) {
        val toSet = ArrayList<NSHTTPCookie>()
        cookies.forEach { (domain, rawValue) ->
            val cleanHost = domain.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .trimStart('.')
            if (cleanHost.isBlank()) return@forEach
            val attrDomain = ".$cleanHost"
            val lines = rawValue.split('\n').filter { it.isNotBlank() }
            for (line in lines) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val name = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).substringBefore(';').trim()
                if (name.isBlank()) continue
                val props: Map<Any?, *> = mapOf(
                    NSHTTPCookieName to name,
                    NSHTTPCookieValue to value,
                    NSHTTPCookieDomain to attrDomain,
                    NSHTTPCookiePath to "/",
                    NSHTTPCookieSecure to "TRUE",
                )
                NSHTTPCookie.cookieWithProperties(props)?.let { toSet.add(it) }
            }
        }
        if (toSet.isEmpty()) {
            onAllWritten?.invoke()
            return
        }
        var remaining = toSet.size
        toSet.forEach { cookie ->
            cookieStore.setCookie(cookie) {
                remaining--
                if (remaining == 0) onAllWritten?.invoke()
            }
        }
    }
}

@Composable
actual fun rememberJsonExportLauncher(
    onResult: (Result<String>?) -> Unit,
): (suggestedFileName: String, content: String, summaryLabel: String) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return { suggestedFileName, content, summaryLabel ->
        dispatch_async(dispatch_get_main_queue()) {
            val result = runCatching {
                val safeName = suggestedFileName.substringAfterLast('/').ifBlank { "cobo-backup.json" }
                val outFile = appCacheDir().child("backup_export").apply { mkdirs() }.child(safeName)
                if (!outFile.writeTextAtomic(content)) error("无法写入备份文件")
                val fileUrl = platform.Foundation.NSURL.fileURLWithPath(outFile.path)
                val vc = platform.UIKit.UIActivityViewController(
                    activityItems = listOf(fileUrl),
                    applicationActivities = null,
                )
                topViewController()?.presentViewController(vc, animated = true, completion = null)
                    ?: error("无法打开系统分享面板")
                summaryLabel
            }
            currentOnResult.value(result)
        }
    }
}

@Composable
actual fun rememberJsonImportLauncher(
    maxBytes: Long,
    onResult: (Result<String>?) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val delegate = remember {
        object : platform.darwin.NSObject(), platform.UIKit.UIDocumentPickerDelegateProtocol {
            override fun documentPickerWasCancelled(
                controller: platform.UIKit.UIDocumentPickerViewController,
            ) {
                currentOnResult.value(null)
            }

            override fun documentPicker(
                controller: platform.UIKit.UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? platform.Foundation.NSURL ?: run {
                    currentOnResult.value(null)
                    return
                }
                val result = runCatching {
                    val accessed = url.startAccessingSecurityScopedResource()
                    try {
                        val filePath = url.path ?: error("无法读取所选文件路径")
                        val file = PlatformFile(filePath)
                        if (file.length() > maxBytes) {
                            throw IllegalArgumentException("备份文件过大，已取消读取")
                        }
                        val bytes = file.readBytesOrNull() ?: error("无法读取所选文件")
                        if (bytes.size.toLong() > maxBytes) {
                            throw IllegalArgumentException("备份文件过大，已取消读取")
                        }
                        bytes.decodeToString()
                    } finally {
                        if (accessed) url.stopAccessingSecurityScopedResource()
                    }
                }
                currentOnResult.value(result)
            }
        }
    }
    return {
        dispatch_async(dispatch_get_main_queue()) {
            val picker = platform.UIKit.UIDocumentPickerViewController(
                documentTypes = listOf("public.json", "public.plain-text", "public.item"),
                inMode = platform.UIKit.UIDocumentPickerMode.UIDocumentPickerModeImport,
            )
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
