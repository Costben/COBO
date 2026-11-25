package com.cobo.app.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.imageLoader
import com.cobo.app.data.MotionPhotoExporter
import com.cobo.app.data.MotionPhotoParser
import com.cobo.app.data.MotionPhotoRemuxer
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

private const val LiveSaveTag = "CoboLiveSave"
private val SaveSequence = AtomicInteger(0)

/** 待保存的媒体项；[liveUrl] 非空时按 Android Motion Photo 实况照片合成保存。 */
data class SaveImageItem(
    val url: String,
    val liveUrl: String = "",
    val preserveUrl: Boolean = false,
) {
    val isLive: Boolean get() = liveUrl.isNotBlank()
}

/**
 * Weibo serves one picture at several sizes off the same path segment. Everything the app stores
 * is already `large`, but links copied out of retweets and edit history still carry `orj360`,
 * `mw690` or `thumb150`, so the segment is rewritten before a full-resolution decode.
 */
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

private val ImageClient by lazy { OkHttpClient.Builder().followRedirects(true).build() }

internal fun liveMediaCacheDir(context: Context): File =
    File(context.cacheDir, "live").apply { mkdirs() }

internal fun liveMediaCacheFile(context: Context, url: String, ext: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    val name = digest.joinToString("") { "%02x".format(it) }
    return File(liveMediaCacheDir(context), "$name.$ext")
}

/**
 * 将远程媒体下载到 [dest]；已存在非空文件时直接复用。写入先落 `.part` 临时文件再原子改名，
 * 避免看图页播放或保存链路读到半截文件。
 */
internal suspend fun downloadMediaToFile(url: String, dest: File): File? = withContext(Dispatchers.IO) {
    if (dest.isFile && dest.length() > 0L) return@withContext dest
    runCatching {
        dest.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Referer", "https://m.weibo.cn/")
            .build()
        ImageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val tmp = File(dest.parentFile, "${dest.name}.${System.nanoTime()}.part")
            try {
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (tmp.length() <= 0L) {
                    tmp.delete()
                    return@use null
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                dest
            } catch (error: Throwable) {
                tmp.delete()
                throw error
            }
        }
    }.getOrNull()
}

private suspend fun resolveImageFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return@withContext File(url).takeIf { it.isFile && it.length() > 0L }
    }
    val dest = liveMediaCacheFile(context, url, "img")
    if (dest.isFile && dest.length() > 0L) return@withContext dest
    val fromDiskCache = runCatching {
        context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot ->
            val src = snapshot.data.toFile()
            if (src.isFile && src.length() > 0L) {
                val tmp = File(dest.parentFile, "${dest.name}.${System.nanoTime()}.part")
                try {
                    src.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    dest
                } catch (error: Throwable) {
                    tmp.delete()
                    throw error
                }
            } else {
                null
            }
        }
    }.getOrNull()
    if (fromDiskCache != null) return@withContext fromDiskCache
    downloadMediaToFile(url, dest)
}

private fun extensionFor(url: String): String {
    val ext = url.substringBefore('?').substringAfterLast('.', "")
    return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext.lowercase() else "jpg"
}

private fun mimeFor(ext: String): String = when (ext) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    else -> "image/jpeg"
}

private fun uniqueSaveName(prefix: String, ext: String): String {
    val seq = SaveSequence.incrementAndGet() and 0xfff
    return "${prefix}_${System.currentTimeMillis()}_$seq.$ext"
}

suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .header("Referer", "https://m.weibo.cn/")
            .build()
        ImageClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()
}

/** Writes straight into the shared gallery, so the picture survives an app reinstall. */
suspend fun saveToGallery(context: Context, bytes: ByteArray, url: String): Boolean =
    withContext(Dispatchers.IO) {
        val ext = extensionFor(url)
        val name = uniqueSaveName("cobo", ext)
        writeStreamToGallery(context, name, mimeFor(ext)) { out -> out.write(bytes) }
    }

private suspend fun saveFileToGallery(
    context: Context,
    file: File,
    displayName: String,
    mimeType: String,
): Boolean = withContext(Dispatchers.IO) {
    writeStreamToGallery(context, displayName, mimeType) { out ->
        file.inputStream().use { input -> input.copyTo(out) }
    }
}

private fun writeStreamToGallery(
    context: Context,
    displayName: String,
    mimeType: String,
    write: (java.io.OutputStream) -> Unit,
): Boolean = runCatching {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Cobo",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@runCatching false
    try {
        val out = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法打开相册输出流")
        out.use(write)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}.onFailure { err ->
    Log.w(LiveSaveTag, "写入系统相册失败: $displayName", err)
}.getOrDefault(false)

/**
 * 将微博实况图（静图原图 + 附带 `.mov` 视频）规范化重封装并合成为 Android 通用 Motion Photo JPEG，
 * 保存到系统相册 `Pictures/Cobo`。
 */
suspend fun saveLivePhotoToGallery(context: Context, item: SaveImageItem): Boolean =
    withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "livephoto-export-${System.nanoTime()}")
        workDir.mkdirs()
        try {
            val targetUrl = if (item.preserveUrl) item.url else originalSize(item.url)
            val stillFile = resolveImageFile(context, targetUrl)
                ?: if (targetUrl != item.url) resolveImageFile(context, item.url) else null
            if (stillFile == null) {
                Log.w(LiveSaveTag, "实况静图下载失败: $targetUrl")
                return@withContext false
            }

            val jpegFile = MotionPhotoRemuxer.ensureJpeg(stillFile, File(workDir, "still.jpg"))
            val existingInfo = MotionPhotoParser.parse(jpegFile, expectMotionPhoto = true)

            val mp4File = if (existingInfo.embeddedVideoLength != null) {
                null
            } else {
                val movCache = liveMediaCacheFile(context, item.liveUrl, "mov")
                val movFile = downloadMediaToFile(item.liveUrl, movCache)
                if (movFile == null) {
                    Log.w(LiveSaveTag, "实况视频下载失败: ${item.liveUrl}")
                    return@withContext false
                }
                MotionPhotoRemuxer.prepareMp4(movFile, File(workDir, "motion.mp4"))
            }

            val exported = MotionPhotoExporter.export(
                sourceImage = jpegFile,
                video = mp4File,
                destination = File(workDir, "export.jpg"),
                presentationTimestampUs = existingInfo.presentationTimestampUs,
            )
            saveFileToGallery(
                context = context,
                file = exported,
                displayName = uniqueSaveName("cobo_live", "jpg"),
                mimeType = "image/jpeg",
            )
        } catch (error: Throwable) {
            Log.w(LiveSaveTag, "实况图合成保存失败: ${item.url}", error)
            false
        } finally {
            workDir.deleteRecursively()
        }
    }

/**
 * Save queue shared by the viewer and the feed. Both surfaces show the same actions, so the
 * download / permission / progress plumbing lives here once instead of in every screen.
 */
@Stable
class ImageSaveState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    internal var launchPermission: ((String) -> Unit)? = null
    internal var afterGrant: (() -> Unit)? = null

    var saving by mutableStateOf(false)
        private set

    var hud by mutableStateOf("")
        internal set

    internal var tick by mutableIntStateOf(0)

    /** 统一交由全局 COUI Snackbar 弹出保存进度与结果提示。 */
    fun flash(text: String) {
        com.cobo.app.App.toast(text)
    }

    fun saveOne(item: SaveImageItem) {
        if (item.url.isBlank()) return
        withPermission { scope.launch { performSaveOne(item) } }
    }

    fun saveOne(url: String, preserveUrl: Boolean = false) {
        saveOne(SaveImageItem(url = url, preserveUrl = preserveUrl))
    }

    /** Every original of the post, in the order the gallery shows them. */
    fun saveAll(items: List<SaveImageItem>) {
        if (items.isEmpty()) return
        withPermission { scope.launch { performSaveAll(items) } }
    }

    fun saveAll(urls: List<String>, preserveUrl: Boolean = false) {
        saveAll(urls.map { SaveImageItem(url = it, preserveUrl = preserveUrl) })
    }

    private fun withPermission(action: () -> Unit) {
        val needsLegacyGrant = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyGrant) {
            afterGrant = action
            launchPermission?.invoke(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    private suspend fun performSaveOne(item: SaveImageItem) {
        if (saving) return
        saving = true
        val saved = if (item.isLive) {
            flash("正在保存实况图…")
            saveLivePhotoToGallery(context, item)
        } else {
            flash("正在保存原图…")
            saveStillItem(item)
        }
        saving = false
        flash(
            when {
                saved && item.isLive -> "已保存实况图到相册"
                saved -> "已保存到相册"
                item.isLive -> "实况图保存失败"
                else -> "保存失败"
            },
        )
    }

    private suspend fun performSaveAll(items: List<SaveImageItem>) {
        if (saving) return
        saving = true
        var ok = 0
        var liveOk = 0
        for ((position, item) in items.withIndex()) {
            flash(
                if (item.isLive) {
                    "正在保存实况图 ${position + 1}/${items.size}…"
                } else {
                    "正在保存 ${position + 1}/${items.size}…"
                },
            )
            val saved = if (item.isLive) saveLivePhotoToGallery(context, item) else saveStillItem(item)
            if (saved) {
                ok++
                if (item.isLive) liveOk++
            }
        }
        saving = false
        flash(
            when {
                ok == items.size && liveOk == items.size -> "已保存 $ok 张实况图到相册"
                ok == items.size && liveOk > 0 -> "已保存 $ok 张到相册（含 $liveOk 张实况图）"
                ok == items.size -> "已保存 $ok 张到相册"
                else -> "已保存 $ok/${items.size} 张"
            },
        )
    }

    private suspend fun saveStillItem(item: SaveImageItem): Boolean {
        val target = if (item.preserveUrl) item.url else originalSize(item.url)
        val cachedOrDownloaded = resolveImageFile(context, target)
            ?: if (target != item.url) resolveImageFile(context, item.url) else null
        if (cachedOrDownloaded != null) {
            val ext = extensionFor(target)
            return saveFileToGallery(
                context = context,
                file = cachedOrDownloaded,
                displayName = uniqueSaveName("cobo", ext),
                mimeType = mimeFor(ext),
            )
        }
        val bytes = downloadImage(target) ?: if (target != item.url) downloadImage(item.url) else null
        return bytes != null && saveToGallery(context, bytes, target)
    }
}

@Composable
fun rememberImageSaveState(): ImageSaveState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { ImageSaveState(context, scope) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = state.afterGrant
        state.afterGrant = null
        if (granted) action?.invoke() else state.flash("没有存储权限")
    }
    SideEffect { state.launchPermission = { name -> permission.launch(name) } }
    LaunchedEffect(state.tick) {
        if (state.hud.isNotEmpty()) {
            delay(1600)
            state.hud = ""
        }
    }
    return state
}

/**
 * 任意页面都能呼出保存菜单：菜单本体挂在根节点，长按处只负责把手指位置记进 [PreciseClickState]。
 */
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

/**
 * 长按呼出的保存菜单：使用 COUI 原生标准的 [OverlayListPopup] 悬浮菜单，
 * 挂载于带有 [LocalBackdrop] 的浮层宿主，呈现半透明磨砂玻璃、连续曲率超椭圆圆角、高光边框与聚光灯光效。
 * [items] == null 表示收起；收起动画期间靠上一次的列表把条目留住。
 */
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

/** 保存进度浮字，跟菜单解耦：菜单收起之后它还要自己活 1.6 秒。 */
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
