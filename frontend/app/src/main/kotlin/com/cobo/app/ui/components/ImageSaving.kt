package com.cobo.app.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.window.WindowListPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Weibo serves one picture at several sizes off the same path segment. Everything the app stores
 * is already `large`, but links copied out of retweets and edit history still carry `orj360`,
 * `mw690` or `thumb150`, so the segment is rewritten before a full-resolution decode.
 */
val SinaSizeSegment = Regex(
    """^(https?://[^/]+)/(?:large|original|woriginal|bmiddle|small|square|thumbnail|thumb\d+|orj\d+|mw\d+|crop\.[0-9.]+)(/.*)$"""
)

fun originalSize(url: String): String =
    SinaSizeSegment.replace(url) { "${it.groupValues[1]}/large${it.groupValues[2]}" }

private val ImageClient by lazy { OkHttpClient.Builder().build() }

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
        runCatching {
            val name = "cobo_${System.currentTimeMillis()}.${extensionFor(url)}"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(extensionFor(url)))
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
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

/**
 * Save queue shared by the viewer and the feed. Both surfaces show the same two actions, so the
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

    /** Shows a short status line; the caller only has to render [hud]. */
    fun flash(text: String) {
        hud = text
        tick++
    }

    fun saveOne(url: String) {
        if (url.isBlank()) return
        withPermission { scope.launch { performSaveOne(url) } }
    }

    /** Every original of the post, in the order the gallery shows them. */
    fun saveAll(urls: List<String>) {
        if (urls.isEmpty()) return
        withPermission { scope.launch { performSaveAll(urls) } }
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

    private suspend fun performSaveOne(url: String) {
        if (saving) return
        saving = true
        flash("正在保存原图…")
        val target = originalSize(url)
        val bytes = downloadImage(target) ?: downloadImage(url)
        val saved = bytes != null && saveToGallery(context, bytes, target)
        saving = false
        flash(if (saved) "已保存到相册" else "保存失败")
    }

    private suspend fun performSaveAll(urls: List<String>) {
        if (saving) return
        saving = true
        var ok = 0
        for ((position, url) in urls.withIndex()) {
            flash("正在保存 ${position + 1}/${urls.size}…")
            val target = originalSize(url)
            val bytes = downloadImage(target) ?: downloadImage(url)
            if (bytes != null && saveToGallery(context, bytes, target)) ok++
        }
        saving = false
        flash(if (ok == urls.size) "已保存 $ok 张到相册" else "已保存 $ok/${urls.size} 张")
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
    private val open: (List<String>, Int, PreciseClickState?) -> Unit,
) {
    fun show(urls: List<String>, index: Int = 0, precise: PreciseClickState? = null) {
        if (urls.isNotEmpty()) open(urls, index.coerceIn(0, urls.lastIndex), precise)
    }
}

val LocalImageSaver = staticCompositionLocalOf<ImageSaver?> { null }

/**
 * 长按呼出的保存菜单：直接用 COUI 的浮层下拉菜单（demo 右上角三点那个），按手指位置弹出。
 * [urls] == null 表示收起；收起动画期间靠上一次的列表把条目留住。
 *
 * 用 overlay 宿主而不是 Dialog 宿主：Dialog 会开独立窗口，收起动画没播完之前整块窗口都在吞触摸，
 * 手指怎么划都没反应；overlay 宿主一旦开始收起就把触摸交还给页面，页面能立刻接着滚。
 *
 * 入场/收起的缩放由 COUI 的玻璃菜单自己给（0→1 从锚点放大、缩回锚点），本文件只负责把按下点
 * 摆成一颗 1dp 的锚点，别在调用处再套一层缩放。
 */
@Composable
fun SaveImagesMenu(
    urls: List<String>?,
    index: Int,
    precise: PreciseClickState?,
    onDismiss: () -> Unit,
    state: ImageSaveState = rememberImageSaveState(),
) {
    var last by remember { mutableStateOf(urls.orEmpty()) }
    var lastIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(urls, index) {
        if (urls != null) {
            last = urls
            lastIndex = index
        }
    }
    val targets = urls ?: last
    if (targets.isEmpty()) return

    val actions = remember(targets, lastIndex, state) {
        buildList {
            add(
                "保存原图" to {
                    state.saveOne(targets.getOrNull(lastIndex) ?: targets.first())
                },
            )
            if (targets.size > 1) {
                add("一键保存原图（${targets.size} 张）" to { state.saveAll(targets) })
            }
        }
    }

    val point = precise?.touchPoint
    val popup: @Composable () -> Unit = {
        // 独立窗口而不是主窗口里的浮层：底栏的玻璃含一块平台 View 遮罩，主窗口的视图树一变
        // （浮层卸载），它作为独立 RenderNode 会丢一帧，整块玻璃跟着消失。菜单挪进自己的窗口
        // 后主窗口结构不动。WindowListPopup 在收起开始就把窗口让出来，页面照样能继续滑。
        WindowListPopup(
            show = urls != null,
            onDismissRequest = onDismiss,
            onDismissFinished = {},
            maxHeight = null,
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                actions.forEachIndexed { index, (label, action) ->
                    DropdownImpl(
                        item = DropdownItem(text = label),
                        optionSize = actions.size,
                        isSelected = false,
                        index = index,
                        groupKey = "save",
                        onSelectedIndexChange = {
                            action()
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }

    if (point == null || !point.isSpecified) {
        // 无障碍点击不产生按下点，退回 COUI 的居中定位。
        popup()
    } else {
        // 和 c001apk 三点菜单同一套锚定：把按下点当成一颗 1dp 的按钮，菜单右边缘对齐它、挂在它
        // 下方，展开从这颗按钮长出来、收起缩回这颗按钮。走 preciseClickState 那条路是把菜单居中
        // 压在手指上，缩放中心落在菜单自己的中线上，看着就是整块往上飘。
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }
                    .size(1.dp)
            ) { popup() }
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
