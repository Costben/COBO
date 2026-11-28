package com.cobo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.appCacheDir
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.saveImageBytesToGallery
import com.cobo.app.platform.saveLivePhotoToGallery as platformSaveLivePhotoToGallery
import com.cobo.app.platform.sha256Hex
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ImageSaveAppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

data class SaveImageItem(
    val url: String,
    val liveUrl: String = "",
    val preserveUrl: Boolean = false,
) {
    val isLive: Boolean get() = liveUrl.isNotBlank()
}

val SinaSizeSegment = Regex(
    """^(https?://[^/]+)/(?:large|original|woriginal|bmiddle|small|square|thumbnail|thumb\d+|orj\d+|mw\d+|crop\.[0-9.]+)(/.*)$"""
)

fun originalSize(url: String): String {
    if (url.contains("ssig=", ignoreCase = true) ||
        url.contains("KID=", ignoreCase = true) ||
        url.contains("Expires=", ignoreCase = true) ||
        url.contains("Signature=", ignoreCase = true)
    ) {
        return url
    }
    return SinaSizeSegment.replace(url) { "${it.groupValues[1]}/large${it.groupValues[2]}" }
}

internal fun liveMediaCacheDir(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()): PlatformFile =
    appCacheDir().child("live").apply { mkdirs() }

internal fun liveMediaCacheFile(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext(),
    url: String,
    ext: String,
): PlatformFile {
    val name = sha256Hex(url)
    return liveMediaCacheDir().child("$name.$ext")
}

internal suspend fun downloadMediaToFile(
    url: String,
    target: PlatformFile,
): PlatformFile? = withContext(ioDispatcher) {
    if (target.exists() && target.length() > 0L) return@withContext target
    val bytes = downloadImage(url) ?: return@withContext null
    if (target.writeBytes(bytes)) target else null
}

suspend fun downloadImage(url: String): ByteArray? = withContext(ioDispatcher) {
    val trimmed = url.trim()
    if (trimmed.startsWith("/") || trimmed.startsWith("file://", ignoreCase = true)) {
        val localPath = trimmed.removePrefix("file://")
        return@withContext PlatformFile(localPath).readBytesOrNull()
    }
    val host = parseUrlHost(trimmed)
    val headers = App.imageHeadersForHost(host)
    PlatformHttp.downloadBytes(trimmed, headers)
}

private fun extensionFor(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".png") -> "png"
        path.endsWith(".gif") -> "gif"
        path.endsWith(".webp") -> "webp"
        path.endsWith(".heic") -> "heic"
        else -> "jpg"
    }
}

private fun mimeFor(ext: String): String = when (ext.lowercase()) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    else -> "image/jpeg"
}

suspend fun saveToGalleryResult(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
    bytes: ByteArray,
    url: String,
): Result<Unit> {
    val ext = extensionFor(url)
    val name = "cobo_${currentTimeMillis()}.$ext"
    return saveImageBytesToGallery(bytes, name, mimeFor(ext))
}

suspend fun saveToGallery(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
    bytes: ByteArray,
    url: String,
): Boolean = saveToGalleryResult(context, bytes, url).isSuccess

suspend fun saveLivePhotoToGalleryResult(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
    item: SaveImageItem,
): Result<Unit> {
    val targetUrl = if (item.preserveUrl) item.url else originalSize(item.url)
    val imageBytes = downloadImage(targetUrl) ?: if (targetUrl != item.url) downloadImage(item.url) else null
    if (imageBytes == null) return Result.failure(IllegalStateException("图片下载失败"))
    val videoBytes = if (item.liveUrl.isNotBlank()) downloadImage(item.liveUrl) else null
    if (videoBytes != null) {
        val liveResult = platformSaveLivePhotoToGallery(imageBytes, videoBytes)
        if (liveResult.isSuccess) return liveResult
    }
    return saveToGalleryResult(PlatformContext(), imageBytes, targetUrl)
}

suspend fun saveLivePhotoToGallery(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
    item: SaveImageItem,
): Boolean = saveLivePhotoToGalleryResult(context, item).isSuccess

@Stable
class ImageSaveState(
    private val context: PlatformContext,
    private val scope: CoroutineScope = ImageSaveAppScope,
    private val onNotify: (String) -> Unit = App::toast,
    private val saveStill: suspend (SaveImageItem) -> Result<Unit> = { item ->
        saveStillItemResult(context, item)
    },
    private val saveLive: suspend (SaveImageItem) -> Result<Unit> = { item ->
        saveLivePhotoToGalleryResult(context, item)
    },
) {
    var hud by mutableStateOf("")
        internal set
    var tick by mutableIntStateOf(0)
        internal set
    var saving by mutableStateOf(false)
        private set

    fun flash(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        hud = text
        tick++
        onNotify(text)
    }

    fun saveOne(item: SaveImageItem) {
        scope.launch { performSaveOne(item) }
    }

    fun saveOne(url: String, preserveUrl: Boolean = false) {
        saveOne(SaveImageItem(url = url, preserveUrl = preserveUrl))
    }

    fun saveAll(items: List<SaveImageItem>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            saveOne(items.first())
            return
        }
        scope.launch { performSaveAll(items) }
    }

    private suspend fun performSaveOne(item: SaveImageItem) {
        if (saving) {
            flash("正在保存，请稍候…")
            return
        }
        saving = true
        val result = try {
            if (item.isLive) {
                saveLive(item)
            } else {
                saveStill(item)
            }
        } finally {
            saving = false
        }
        val errMsg = result.exceptionOrNull()?.message?.trim().orEmpty()
        flash(
            when {
                result.isSuccess && item.isLive -> "已保存实况图到相册"
                result.isSuccess -> "已保存到相册"
                errMsg.isNotBlank() -> errMsg
                item.isLive -> "实况图保存失败"
                else -> "保存失败"
            },
        )
    }

    private suspend fun performSaveAll(items: List<SaveImageItem>) {
        if (saving) {
            flash("正在保存，请稍候…")
            return
        }
        saving = true
        var ok = 0
        var liveOk = 0
        var lastErr = ""
        try {
            for (item in items) {
                val res = if (item.isLive) saveLive(item) else saveStill(item)
                if (res.isSuccess) {
                    ok++
                    if (item.isLive) liveOk++
                } else {
                    res.exceptionOrNull()?.message?.trim()?.takeIf { it.isNotEmpty() }?.let { lastErr = it }
                }
            }
        } finally {
            saving = false
        }
        flash(
            when {
                ok == 0 && lastErr.isNotBlank() -> lastErr
                ok == 0 -> "保存失败"
                ok == items.size && liveOk == items.size -> "已保存 $ok 张实况图到相册"
                ok == items.size && liveOk > 0 -> "已保存 $ok 张到相册（含 $liveOk 张实况图）"
                ok == items.size -> "已保存 $ok 张到相册"
                else -> "已保存 $ok/${items.size} 张"
            },
        )
    }
}

private suspend fun saveStillItemResult(context: PlatformContext, item: SaveImageItem): Result<Unit> {
    val target = if (item.preserveUrl) item.url else originalSize(item.url)
    val bytes = downloadImage(target)
        ?: (if (target != item.url) downloadImage(item.url) else null)
        ?: return Result.failure(IllegalStateException("图片下载失败"))
    return saveToGalleryResult(context, bytes, target)
}

@Composable
fun rememberImageSaveState(): ImageSaveState {
    // 使用应用级作用域执行保存任务，避免看图页或菜单关闭后中断下载与完成提示。
    val state = remember { ImageSaveState(App.context, ImageSaveAppScope) }
    LaunchedEffect(state.tick) {
        if (state.hud.isNotEmpty()) {
            delay(1600)
            state.hud = ""
        }
    }
    return state
}

@Stable
class ImageSaver internal constructor(
    private val open: (List<SaveImageItem>, Int, PreciseClickState?) -> Unit,
) {
    fun showItems(
        items: List<SaveImageItem>,
        index: Int = 0,
        precise: PreciseClickState? = null,
    ) {
        if (items.isNotEmpty()) open(items, index.coerceIn(0, items.lastIndex), precise)
    }

    fun show(
        urls: List<String>,
        index: Int = 0,
        precise: PreciseClickState? = null,
        preserveUrls: Boolean = false,
    ) {
        showItems(
            items = urls.map { SaveImageItem(url = it, preserveUrl = preserveUrls) },
            index = index,
            precise = precise,
        )
    }
}

val LocalImageSaver = staticCompositionLocalOf<ImageSaver?> { null }

@Composable
fun SaveImagesMenu(
    items: List<SaveImageItem>?,
    index: Int,
    precise: PreciseClickState?,
    onDismiss: () -> Unit,
    state: ImageSaveState = rememberImageSaveState(),
) {
    var last by remember { mutableStateOf(items.orEmpty()) }
    var lastIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(items, index) {
        if (items != null) {
            last = items
            lastIndex = index
        }
    }
    val targets = items ?: last
    if (targets.isEmpty()) return

    val actions = remember(targets, lastIndex, state) {
        val current = targets.getOrNull(lastIndex) ?: targets.first()
        val hasLive = targets.any { it.isLive }
        buildList {
            add(
                (if (current.isLive) "保存实况图" else "保存原图") to {
                    state.saveOne(current)
                },
            )
            if (targets.size > 1) {
                val batchLabel = if (hasLive) {
                    "一键保存全部（${targets.size} 张，含实况）"
                } else {
                    "一键保存原图（${targets.size} 张）"
                }
                add(batchLabel to { state.saveAll(targets) })
            }
        }
    }

    OverlayListPopup(
        show = items != null,
        onDismissRequest = onDismiss,
        preciseClickState = precise,
    ) {
        val dismiss = LocalDismissState.current
        ListPopupColumn {
            actions.forEachIndexed { actionIndex, (label, action) ->
                DropdownImpl(
                    text = label,
                    optionSize = actions.size,
                    isSelected = false,
                    index = actionIndex,
                    onSelectedIndexChange = {
                        action()
                        dismiss?.invoke()
                    },
                )
            }
        }
    }
}

@Composable
fun SaveHud(state: ImageSaveState) {
    if (state.hud.isEmpty()) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = state.hud,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
