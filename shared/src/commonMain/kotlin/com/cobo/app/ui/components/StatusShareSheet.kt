package com.cobo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Membership
import com.cobo.app.data.Pic
import com.cobo.app.data.Status
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.parseWeiboTimeMillis
import com.cobo.app.data.stripHtml
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.encodeImageBitmapToPng
import com.cobo.app.platform.epochYear
import com.cobo.app.platform.formatEpochMillis
import com.cobo.app.platform.generateQrCodeBitmap
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.saveImageBytesToGallery
import com.cobo.app.platform.shareImageBytes
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.isDarkTheme
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 分享海报逻辑画幅宽度（最小宽度 / DPI 等效调节）范围，单位 dp。 */
internal const val ShareWidthMinDp = 300
internal const val ShareWidthMaxDp = 640

/** 会话内暂存上次调节的临时分享画幅宽度，关闭 App 后自动恢复默认。 */
private var lastShareWidthDp: Int? = null

/**
 * 按目标逻辑宽度（dp）为分享海报计算临时 [Density]：
 * 在保持预览框与导出 PNG 物理像素宽不变的前提下，通过重算密度使海报按目标宽度排版，
 * 等效于临时调整最小宽度（DPI），且完全不影响 App 其余界面。
 */
internal fun resolveSharePosterDensity(
    baseDensity: Density,
    containerWidthPx: Int,
    targetWidthDp: Int?,
): Density {
    if (targetWidthDp == null || containerWidthPx <= 0) return baseDensity
    val clampedDp = targetWidthDp.coerceIn(ShareWidthMinDp, ShareWidthMaxDp)
    val effectiveDensity = (containerWidthPx.toFloat() / clampedDp.toFloat()).coerceIn(0.75f, 6.0f)
    return Density(density = effectiveDensity, fontScale = baseDensity.fontScale)
}

/** 分享海报风格：双联票卡（方案 C）与纸墨刊物（方案 B）。 */
internal enum class SharePosterStyle(val label: String, val tag: String) {
    BentoTicket("双联票卡", "share_style_bento"),
    Editorial("纸墨刊物", "share_style_editorial"),
}

/** 方案 C（COUI 17 双联票卡）配色预设。 */
internal enum class BentoShareTheme(
    val label: String,
    val isDark: Boolean,
    val swatchTop: Color,
    val swatchBottom: Color,
) {
    Light("净白", false, Color(0xFFEAEFF6), Color(0xFFFFFFFF)),
    Blue("晴空蓝", false, Color(0xFFD6E7FF), Color(0xFFEEF5FF)),
    Dark("深空黑", true, Color(0xFF1A1E27), Color(0xFF0B0D12)),
    Obsidian("星夜蓝", true, Color(0xFF1D2B45), Color(0xFF090C14)),
}

/** 方案 B（纸墨刊物）纸张色调预设。 */
internal enum class EditorialShareTheme(
    val label: String,
    val isDark: Boolean,
    val matBg: Color,
    val paperBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
) {
    Paper(
        label = "宣纸白",
        isDark = false,
        matBg = Color(0xFFEBE6DD),
        paperBg = Color(0xFFFAF7F1),
        textPrimary = Color(0xFF1D1A16),
        textSecondary = Color(0xFF776F64),
        line = Color(0x1F1D1A16),
        lineStrong = Color(0x381D1A16),
        accent = Color(0xFF9E5A2B),
    ),
    Sand(
        label = "暖米沙",
        isDark = false,
        matBg = Color(0xFFE3DACA),
        paperBg = Color(0xFFF2EBE0),
        textPrimary = Color(0xFF231E18),
        textSecondary = Color(0xFF7B7163),
        line = Color(0x21231E18),
        lineStrong = Color(0x3D231E18),
        accent = Color(0xFF8B4F24),
    ),
    Slate(
        label = "银雾灰",
        isDark = false,
        matBg = Color(0xFFDFE4EA),
        paperBg = Color(0xFFEEF1F4),
        textPrimary = Color(0xFF1A1E26),
        textSecondary = Color(0xFF697384),
        line = Color(0x1F1A1E26),
        lineStrong = Color(0x381A1E26),
        accent = Color(0xFF2E62A6),
    ),
    Ink(
        label = "墨黑",
        isDark = true,
        matBg = Color(0xFF0C0C0B),
        paperBg = Color(0xFF161614),
        textPrimary = Color(0xFFECE7DC),
        textSecondary = Color(0xFF969085),
        line = Color(0x21ECE7DC),
        lineStrong = Color(0x3DECE7DC),
        accent = Color(0xFFD8A86E),
    ),
}

private val RelativeSecondsRegex = Regex("""^(\d+)\s*秒前$""")
private val RelativeMinutesRegex = Regex("""^(\d+)\s*分钟前$""")
private val RelativeHoursRegex = Regex("""^(\d+)\s*小时前$""")
private val RelativeDayTimeRegex = Regex("""^(今天|昨天|前天)\s*(\d{1,2}:\d{2})$""")
private val MonthDayTimeRegex = Regex("""^(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}:\d{2}))?$""")
private val FullDateOnlyRegex = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
private val ShareHighlightRegex = Regex("""#[^#\n]{1,40}#|@[\w\u4E00-\u9FA5\-_·]{1,30}""")

/** 生成用于图文分享海报底部二维码的微博详情链接。 */
internal fun buildWeiboShareUrl(status: Status): String {
    val target = status.mid.ifBlank { status.id }.trim()
    return if (target.isNotEmpty()) "https://m.weibo.cn/detail/$target" else "https://m.weibo.cn/"
}

/** 将微博正文 HTML 规范化为适合分享长图排版的纯文本（保留段落换行，去掉尾部冗余“全文”标签）。 */
internal fun formatShareBodyText(html: String): String {
    if (html.isBlank()) return ""
    val withBreaks = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>\\s*<p[^>]*>"), "\n\n")
        .replace(Regex("(?i)</?p[^>]*>"), "\n")
    return stripHtml(withBreaks)
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun pad2(raw: String): String = raw.padStart(2, '0')

/**
 * 将微博发布时间统一规范为绝对日期时间（如 `2026-11-27 17:42` 或 `2026-11-27`），
 * 避免分享图保存/转发后出现「刚刚」「今天」「昨天」「5分钟前」这类脱离上下文即失效的相对时间。
 */
internal fun formatShareDateTime(raw: String?, nowMillis: Long = currentTimeMillis()): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""

    val parsedMillis = parseWeiboTimeMillis(trimmed)
    if (parsedMillis > 0L) {
        return formatEpochMillis(parsedMillis, "yyyy-MM-dd HH:mm")
    }

    if (trimmed == "刚刚") {
        return formatEpochMillis(nowMillis, "yyyy-MM-dd HH:mm")
    }
    RelativeSecondsRegex.matchEntire(trimmed)?.let { m ->
        val sec = m.groupValues[1].toLongOrNull() ?: 0L
        return formatEpochMillis(nowMillis - sec * 1_000L, "yyyy-MM-dd HH:mm")
    }
    RelativeMinutesRegex.matchEntire(trimmed)?.let { m ->
        val min = m.groupValues[1].toLongOrNull() ?: 0L
        return formatEpochMillis(nowMillis - min * 60_000L, "yyyy-MM-dd HH:mm")
    }
    RelativeHoursRegex.matchEntire(trimmed)?.let { m ->
        val hr = m.groupValues[1].toLongOrNull() ?: 0L
        return formatEpochMillis(nowMillis - hr * 3_600_000L, "yyyy-MM-dd HH:mm")
    }
    RelativeDayTimeRegex.matchEntire(trimmed)?.let { m ->
        val offsetDays = when (m.groupValues[1]) {
            "昨天" -> 1L
            "前天" -> 2L
            else -> 0L
        }
        val datePart = formatEpochMillis(nowMillis - offsetDays * 86_400_000L, "yyyy-MM-dd")
        val timeParts = m.groupValues[2].split(":")
        val hhmm = if (timeParts.size == 2) "${pad2(timeParts[0])}:${pad2(timeParts[1])}" else m.groupValues[2]
        return "$datePart $hhmm"
    }
    if (trimmed == "今天") return formatEpochMillis(nowMillis, "yyyy-MM-dd")
    if (trimmed == "昨天") return formatEpochMillis(nowMillis - 86_400_000L, "yyyy-MM-dd")
    if (trimmed == "前天") return formatEpochMillis(nowMillis - 2L * 86_400_000L, "yyyy-MM-dd")

    FullDateOnlyRegex.matchEntire(trimmed)?.let { m ->
        return "${m.groupValues[1]}-${pad2(m.groupValues[2])}-${pad2(m.groupValues[3])}"
    }
    MonthDayTimeRegex.matchEntire(trimmed)?.let { m ->
        val year = epochYear(nowMillis)
        val datePart = "$year-${pad2(m.groupValues[1])}-${pad2(m.groupValues[2])}"
        val timePart = m.groupValues[3]
        if (timePart.isBlank()) return datePart
        val parts = timePart.split(":")
        val hhmm = if (parts.size == 2) "${pad2(parts[0])}:${pad2(parts[1])}" else timePart
        return "$datePart $hhmm"
    }
    return trimmed
}

/** 纸墨刊物顶部眉栏使用的点分日期（如 `2026.11.27`）。 */
internal fun formatShareMastheadDate(raw: String?, nowMillis: Long = currentTimeMillis()): String {
    val full = formatShareDateTime(raw, nowMillis)
    if (full.isBlank()) return ""
    val dateToken = full.substringBefore(' ')
    return if (FullDateOnlyRegex.matches(dateToken)) {
        dateToken.replace('-', '.')
    } else {
        dateToken
    }
}

/** 为分享图正文中的 `#话题#` 与 `@提及` 附加轻量强调色，避免纯黑白平铺。 */
internal fun buildShareRichText(text: String, accentColor: Color): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(text)
        ShareHighlightRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                ),
                start = match.range.first,
                end = match.range.last + 1,
            )
        }
    }
}

private fun formatShareVideoDuration(durationSec: Int): String {
    if (durationSec <= 0) return ""
    val h = durationSec / 3600
    val m = (durationSec % 3600) / 60
    val s = durationSec % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

/**
 * 微博图文分享底栏面板：
 * - 支持在「双联票卡」（COUI 17 原生分区风）与「纸墨刊物」（杂志剪报风）两种风格之间自由切换；
 * - 每种风格内置 4 套精选明暗色板（纸墨刊物额外支持宋体/黑体切换）；
 * - 支持独立开关底部「二维码」「Cobo 标签」「互动数」「日期来源」，所有时间均格式化为绝对日期；
 * - 经 GraphicsLayer 录制完整长图，导出到相册或调用系统分享面板。
 */
@Composable
internal fun StatusShareSheet(
    status: Status,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme
    val defaultDark = isDarkTheme()

    var show by remember(status.id) { mutableStateOf(false) }
    var settled by remember(status.id) { mutableStateOf(false) }
    var style by remember(status.id) { mutableStateOf(SharePosterStyle.BentoTicket) }
    var bentoTheme by remember(status.id, defaultDark) {
        mutableStateOf(if (defaultDark) BentoShareTheme.Dark else BentoShareTheme.Light)
    }
    var editorialTheme by remember(status.id, defaultDark) {
        mutableStateOf(if (defaultDark) EditorialShareTheme.Ink else EditorialShareTheme.Paper)
    }
    var editorialSerif by remember(status.id) { mutableStateOf(true) }
    var showQr by remember(status.id) { mutableStateOf(true) }
    var showBrand by remember(status.id) { mutableStateOf(true) }
    var showStats by remember(status.id) { mutableStateOf(true) }
    var showMeta by remember(status.id) { mutableStateOf(true) }
    var overrideWidthDp by remember(status.id) { mutableStateOf(lastShareWidthDp) }
    var detectedNativeWidthDp by remember(status.id) { mutableStateOf(360) }
    var busy by remember(status.id) { mutableStateOf(false) }
    val shareLayer = rememberGraphicsLayer()
    val baseDensity = LocalDensity.current

    LaunchedEffect(status.id) { show = true }

    val shareUrl = remember(status.id, status.mid) { buildWeiboShareUrl(status) }
    val qrBitmap = remember(shareUrl, showQr) {
        if (showQr) generateQrCodeBitmap(shareUrl, 256) else null
    }

    suspend fun renderSharePng(): ByteArray? {
        withFrameNanos { }
        val bitmap = runCatching { shareLayer.toImageBitmap() }.getOrNull() ?: return null
        return withContext(ioDispatcher) {
            encodeImageBitmapToPng(bitmap)
        }
    }

    OverlayBottomSheet(
        show = show,
        showDragHandle = true,
        insideMargin = DpSize(12.dp, 0.dp),
        allowDismiss = !busy,
        onDismissRequest = { if (!busy) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 14.dp)
                .testTag("status_share_sheet"),
        ) {
            // 顶栏：标题 + B/C 风格分段切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "图文分享",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ShareStyleSwitcher(
                    selected = style,
                    enabled = !busy,
                    onSelect = { style = it },
                )
            }

            Spacer(Modifier.height(12.dp))

            // 中央预览舞台：按临时画幅宽度重算 Density，GraphicsLayer 挂在可滚动内容本体上确保超长图文完整导出
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 376.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 0.6.dp,
                        color = scheme.dividerLine.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp),
                    ),
            ) {
                val containerWidthPx = constraints.maxWidth
                val nativeWidthDp = remember(containerWidthPx, baseDensity.density) {
                    if (containerWidthPx > 0 && baseDensity.density > 0f) {
                        (containerWidthPx / baseDensity.density).roundToInt()
                    } else {
                        360
                    }
                }
                val posterDensity = remember(baseDensity, containerWidthPx, overrideWidthDp) {
                    resolveSharePosterDensity(baseDensity, containerWidthPx, overrideWidthDp)
                }
                LaunchedEffect(nativeWidthDp) {
                    detectedNativeWidthDp = nativeWidthDp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                shareLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(shareLayer)
                            },
                    ) {
                        CompositionLocalProvider(LocalDensity provides posterDensity) {
                            when (style) {
                                SharePosterStyle.BentoTicket -> BentoTicketSharePoster(
                                    status = status,
                                    theme = bentoTheme,
                                    qrBitmap = qrBitmap,
                                    showQr = showQr,
                                    showBrand = showBrand,
                                    showStats = showStats,
                                    showMeta = showMeta,
                                )
                                SharePosterStyle.Editorial -> EditorialSharePoster(
                                    status = status,
                                    theme = editorialTheme,
                                    serif = editorialSerif,
                                    qrBitmap = qrBitmap,
                                    showQr = showQr,
                                    showBrand = showBrand,
                                    showStats = showStats,
                                    showMeta = showMeta,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 临时画幅宽度（等效最小宽度 / DPI 调节）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "画幅宽度 (DPI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceSecondary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (overrideWidthDp == null) {
                                "默认 · ${detectedNativeWidthDp}dp"
                            } else {
                                "${overrideWidthDp}dp"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (overrideWidthDp != null) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (overrideWidthDp != null) scheme.primary else scheme.onSurfaceSecondary,
                            modifier = Modifier.testTag("share_width_value"),
                        )
                        if (overrideWidthDp != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(scheme.onSurface.copy(alpha = 0.08f))
                                    .clickable(enabled = !busy, role = Role.Button) {
                                        overrideWidthDp = null
                                        lastShareWidthDp = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("share_width_reset"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "恢复默认",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
                Slider(
                    value = (overrideWidthDp ?: detectedNativeWidthDp)
                        .coerceIn(ShareWidthMinDp, ShareWidthMaxDp)
                        .toFloat(),
                    onValueChange = { raw ->
                        if (busy) return@Slider
                        val snapped = ((raw / 10f).roundToInt() * 10)
                            .coerceIn(ShareWidthMinDp, ShareWidthMaxDp)
                        overrideWidthDp = snapped
                        lastShareWidthDp = snapped
                    },
                    valueRange = ShareWidthMinDp.toFloat()..ShareWidthMaxDp.toFloat(),
                    colors = SliderDefaults.sliderColors(foregroundColor = scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_width_slider"),
                )
            }

            Spacer(Modifier.height(8.dp))

            // 配色色板栏（纸墨刊物额外提供 宋体/黑体 切换）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (style == SharePosterStyle.BentoTicket) "卡片色调" else "纸张色调",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceSecondary,
                    )
                    when (style) {
                        SharePosterStyle.BentoTicket -> {
                            BentoShareTheme.entries.forEach { preset ->
                                ShareColorSwatch(
                                    topColor = preset.swatchTop,
                                    bottomColor = preset.swatchBottom,
                                    selected = preset == bentoTheme,
                                    enabled = !busy,
                                    contentDescription = preset.label,
                                    onClick = { bentoTheme = preset },
                                )
                            }
                        }
                        SharePosterStyle.Editorial -> {
                            EditorialShareTheme.entries.forEach { preset ->
                                ShareColorSwatch(
                                    topColor = preset.paperBg,
                                    bottomColor = preset.matBg,
                                    selected = preset == editorialTheme,
                                    enabled = !busy,
                                    contentDescription = preset.label,
                                    onClick = { editorialTheme = preset },
                                )
                            }
                        }
                    }
                }
                if (style == SharePosterStyle.Editorial) {
                    ShareOptionChip(
                        label = if (editorialSerif) "宋体" else "黑体",
                        active = editorialSerif,
                        modifier = Modifier.testTag("share_toggle_serif"),
                        onClick = { if (!busy) editorialSerif = !editorialSerif },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 要素开关行：二维码 / Cobo 标签 / 互动数 / 日期来源
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShareOptionChip(
                    label = "二维码",
                    active = showQr,
                    modifier = Modifier.testTag("share_toggle_qr"),
                    onClick = { if (!busy) showQr = !showQr },
                )
                ShareOptionChip(
                    label = "Cobo 标签",
                    active = showBrand,
                    modifier = Modifier.testTag("share_toggle_brand"),
                    onClick = { if (!busy) showBrand = !showBrand },
                )
                ShareOptionChip(
                    label = "互动数",
                    active = showStats,
                    modifier = Modifier.testTag("share_toggle_stats"),
                    onClick = { if (!busy) showStats = !showStats },
                )
                ShareOptionChip(
                    label = "日期来源",
                    active = showMeta,
                    modifier = Modifier.testTag("share_toggle_meta"),
                    onClick = { if (!busy) showMeta = !showMeta },
                )
            }

            Spacer(Modifier.height(14.dp))

            // 底部主次操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        scope.launch {
                            busy = true
                            try {
                                val bytes = renderSharePng()
                                if (bytes == null) {
                                    App.toast("生成分享图失败")
                                } else {
                                    val fileName = "cobo_weibo_${status.id.ifBlank { "share" }}.png"
                                    saveImageBytesToGallery(bytes, fileName, "image/png")
                                        .onSuccess { App.toast("已保存分享图到相册") }
                                        .onFailure { App.toast(it.message ?: "保存失败") }
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("status_share_save"),
                ) {
                    Icon(
                        imageVector = COUIIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "生成中…" else "保存相册")
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        scope.launch {
                            busy = true
                            try {
                                val bytes = renderSharePng()
                                if (bytes == null) {
                                    App.toast("生成分享图失败")
                                } else {
                                    val fileName = "cobo_weibo_${status.id.ifBlank { "share" }}.png"
                                    shareImageBytes(bytes, fileName, "image/png")
                                    show = false
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1.2f).testTag("status_share_system"),
                ) {
                    Icon(
                        imageVector = COUIIcons.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "生成中…" else "系统分享")
                }
            }
        }
    }
}

@Composable
private fun ShareStyleSwitcher(
    selected: SharePosterStyle,
    enabled: Boolean,
    onSelect: (SharePosterStyle) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SharePosterStyle.entries.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) scheme.surfaceContainer else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(item) }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
                    .testTag(item.tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) scheme.onSurface else scheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun ShareColorSwatch(
    topColor: Color,
    bottomColor: Color,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, scheme.primary, CircleShape)
                } else {
                    Modifier.border(0.8.dp, scheme.dividerLine, CircleShape)
                },
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = contentDescription, onClick = onClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(topColor, bottomColor)))
                .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
        )
    }
}

@Composable
private fun ShareOptionChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val bg = if (active) {
        scheme.primary.copy(alpha = 0.14f)
    } else {
        scheme.onSurface.copy(alpha = 0.06f)
    }
    val border = if (active) {
        scheme.primary.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val fg = if (active) scheme.primary else scheme.onSurfaceSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(0.75.dp, border, RoundedCornerShape(999.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) scheme.primary else scheme.onSurfaceSecondary.copy(alpha = 0.4f)),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

// ============================================================================
// 方案 C：COUI 17 双联票卡（主内容卡 + 底部独立溯源票条）
// ============================================================================

@Composable
private fun BentoTicketSharePoster(
    status: Status,
    theme: BentoShareTheme,
    qrBitmap: ImageBitmap?,
    showQr: Boolean,
    showBrand: Boolean,
    showStats: Boolean,
    showMeta: Boolean,
) {
    val dark = theme.isDark
    val accent = COUITheme.colorScheme.primary
    val linkColor = if (dark) Color(0xFF6BB3FF) else Color(0xFF156CD6)

    val canvasBrush = when (theme) {
        BentoShareTheme.Light -> Brush.verticalGradient(listOf(Color(0xFFEAEFF6), Color(0xFFE6ECF4)))
        BentoShareTheme.Blue -> Brush.linearGradient(listOf(Color(0xFFDCEBFF), Color(0xFFEBF2FC)))
        BentoShareTheme.Dark -> Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF0E1118)))
        BentoShareTheme.Obsidian -> Brush.linearGradient(listOf(Color(0xFF11192A), Color(0xFF090C14)))
    }
    val cardBg = when (theme) {
        BentoShareTheme.Light -> Color(0xFFFFFFFF)
        BentoShareTheme.Blue -> Color(0xF5FFFFFF)
        BentoShareTheme.Dark -> Color(0xFF1A1E27)
        BentoShareTheme.Obsidian -> Color(0xEB1B2231)
    }
    val cardBorder = when (theme) {
        BentoShareTheme.Light -> Color(0x120F172A)
        BentoShareTheme.Blue -> Color(0x180A5AC8)
        BentoShareTheme.Dark -> Color(0x14FFFFFF)
        BentoShareTheme.Obsidian -> Color(0x246EA8FF)
    }
    val quoteBg = if (dark) Color(0xFF232836) else Color(0xFFF3F5F9)
    val textPrimary = if (dark) Color(0xFFF2F4F8) else Color(0xFF151821)
    val textSecondary = if (dark) Color(0xFF96A0B2) else Color(0xFF6B7280)
    val dividerColor = if (dark) Color(0xFF2B3140) else Color(0xFFEAECEF)
    val chipBg = if (dark) Color(0x1AFFFFFF) else Color(0x0D0F172A)

    val user = status.user
    val hasMainBody = remember(status.textHtml) {
        status.textHtml.isNotBlank() && (formatShareBodyText(status.textHtml).isNotBlank() || status.textHtml.contains("<img", ignoreCase = true))
    }
    val absoluteDate = remember(status.createdAt) { formatShareDateTime(status.createdAt) }
    val subMeta = remember(status.region, status.source) {
        buildList {
            status.region.takeIf { it.isNotBlank() }?.let { add(it) }
            status.source.takeIf { it.isNotBlank() }?.let { add("来自 $it") }
        }.joinToString(" · ")
    }
    val hasStats = status.reposts > 0 || status.comments > 0 || status.attitudes > 0
    val showDock = showBrand || (showQr && qrBitmap != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(canvasBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 上半部：主内容卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(cardBg)
                .border(0.6.dp, cardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    url = user?.avatar,
                    size = 40.dp,
                    verifiedKind = user?.verifiedKind ?: VerifiedKind.None,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user?.name?.ifBlank { "微博用户" } ?: "微博用户",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val membership = user?.membership ?: Membership.None
                        if (membership != Membership.None) {
                            Spacer(Modifier.width(5.dp))
                            MemberBadge(membership, compact = true, rank = user?.mbrank ?: 0)
                        }
                    }
                    if (showMeta && (absoluteDate.isNotBlank() || subMeta.isNotBlank())) {
                        Spacer(Modifier.height(2.dp))
                        val line = buildList {
                            if (absoluteDate.isNotBlank()) add(absoluteDate)
                            if (subMeta.isNotBlank()) add(subMeta)
                        }.joinToString(" · ")
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (hasMainBody) {
                Spacer(Modifier.height(13.dp))
                ShareWeiboText(
                    html = status.textHtml,
                    color = textPrimary,
                    linkColor = linkColor,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }

            if (status.pics.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SharePosterImageGrid(
                    pics = status.pics,
                    placeholderColor = quoteBg,
                    outerCorner = 14.dp,
                    innerCorner = 6.dp,
                )
            } else if (status.video != null) {
                Spacer(Modifier.height(12.dp))
                SharePosterVideoCard(
                    video = status.video,
                    placeholderColor = quoteBg,
                    corner = 14.dp,
                )
            }

            status.retweeted?.let { quoted ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(quoteBg)
                        .padding(12.dp),
                ) {
                    val quotedAuthor = quoted.user?.name.orEmpty()
                    val hasQuotedBody = remember(quoted.textHtml, quotedAuthor) {
                        quotedAuthor.isNotBlank() ||
                            formatShareBodyText(quoted.textHtml).isNotBlank() ||
                            quoted.textHtml.contains("<img", ignoreCase = true)
                    }
                    if (hasQuotedBody) {
                        ShareWeiboText(
                            html = quoted.textHtml,
                            prefixAuthor = quotedAuthor.takeIf { it.isNotBlank() },
                            color = textPrimary.copy(alpha = 0.92f),
                            linkColor = linkColor,
                            fontSize = 13.5.sp,
                            lineHeight = 20.5.sp,
                        )
                    }
                    if (quoted.pics.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        SharePosterImageGrid(
                            pics = quoted.pics,
                            placeholderColor = dividerColor,
                            outerCorner = 10.dp,
                            innerCorner = 5.dp,
                        )
                    } else if (quoted.video != null) {
                        Spacer(Modifier.height(9.dp))
                        SharePosterVideoCard(
                            video = quoted.video,
                            placeholderColor = dividerColor,
                            corner = 10.dp,
                        )
                    }
                }
            }

            if (showStats && hasStats) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = dividerColor, thickness = 0.6.dp)
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (status.reposts > 0) {
                            ShareMetricItem(
                                icon = CoboIcons.Repost,
                                value = fmtCount(status.reposts),
                                tint = textSecondary,
                            )
                        }
                        if (status.comments > 0) {
                            ShareMetricItem(
                                icon = CoboIcons.Comment,
                                value = fmtCount(status.comments),
                                tint = textSecondary,
                            )
                        }
                        if (status.attitudes > 0) {
                            ShareMetricItem(
                                icon = CoboIcons.Heart,
                                value = fmtCount(status.attitudes),
                                tint = textSecondary,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "微博正文",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                        )
                    }
                }
            }
        }

        // 下半部：独立溯源票条（仅当启用 Cobo 标签 或 二维码时展示）
        if (showDock) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(0.6.dp, cardBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBrand) {
                    CoboBrandMark(accent = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Cobo",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                            )
                            Text(
                                text = "COUI 17",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (showQr && qrBitmap != null) {
                                "长按识别二维码查看原微博"
                            } else {
                                "分享自 Cobo 微博客户端"
                            },
                            fontSize = 11.sp,
                            color = textSecondary,
                        )
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "原微博链接",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "长按识别右侧二维码查看完整原文",
                            fontSize = 11.sp,
                            color = textSecondary,
                        )
                    }
                }

                if (showQr && qrBitmap != null) {
                    Spacer(Modifier.width(12.dp))
                    ShareQrBox(
                        qrBitmap = qrBitmap,
                        size = 50.dp,
                        corner = 10.dp,
                        borderColor = if (dark) Color.Transparent else Color(0x140F172A),
                    )
                }
            }
        }
    }
}

// ============================================================================
// 方案 B：纸墨刊物（杂志剪报 + 穿孔票根）
// ============================================================================

@Composable
private fun EditorialSharePoster(
    status: Status,
    theme: EditorialShareTheme,
    serif: Boolean,
    qrBitmap: ImageBitmap?,
    showQr: Boolean,
    showBrand: Boolean,
    showStats: Boolean,
    showMeta: Boolean,
) {
    val bodyFont = if (serif) FontFamily.Serif else FontFamily.Default
    val user = status.user
    val hasMainBody = remember(status.textHtml) {
        status.textHtml.isNotBlank() && (formatShareBodyText(status.textHtml).isNotBlank() || status.textHtml.contains("<img", ignoreCase = true))
    }
    val absoluteDate = remember(status.createdAt) { formatShareDateTime(status.createdAt) }
    val mastheadDate = remember(status.createdAt) { formatShareMastheadDate(status.createdAt) }
    val hasTopMedia = status.pics.isNotEmpty() || status.video != null
    val isPureText = !hasTopMedia && status.retweeted == null
    val showFooter = showBrand || (showQr && qrBitmap != null)

    val mastheadRight = remember(showMeta, mastheadDate, status.region) {
        if (!showMeta) {
            "ARCHIVE"
        } else {
            buildList {
                if (mastheadDate.isNotBlank()) add(mastheadDate)
                status.region.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ").ifBlank { "ARCHIVE" }
        }
    }

    val compactStats = remember(status.attitudes, status.comments, status.reposts) {
        buildList {
            if (status.attitudes > 0) add("${fmtCount(status.attitudes)} 赞")
            if (status.comments > 0) add("${fmtCount(status.comments)} 评")
            if (isEmpty() && status.reposts > 0) add("${fmtCount(status.reposts)} 转")
        }.joinToString(" · ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.matBg)
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.paperBg)
                .border(0.6.dp, theme.line, RoundedCornerShape(14.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = if (showFooter) 16.dp else 20.dp),
            ) {
                // 刊物眉栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (showBrand) "COBO · WEIBO CLIPPING" else "WEIBO CLIPPING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = theme.textSecondary,
                    )
                    Text(
                        text = mastheadRight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = theme.lineStrong, thickness = 0.9.dp)

                // 图文优先：首图画报置顶
                if (status.pics.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.6.dp, theme.line, RoundedCornerShape(8.dp)),
                    ) {
                        SharePosterImageGrid(
                            pics = status.pics,
                            placeholderColor = theme.line,
                            outerCorner = 8.dp,
                            innerCorner = 3.dp,
                        )
                    }
                } else if (status.video != null) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.6.dp, theme.line, RoundedCornerShape(8.dp)),
                    ) {
                        SharePosterVideoCard(
                            video = status.video,
                            placeholderColor = theme.line,
                            corner = 8.dp,
                        )
                    }
                }

                // 纯文字视图：优雅书卷引号
                if (isPureText && hasMainBody) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "“",
                        fontSize = 36.sp,
                        lineHeight = 32.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent.copy(alpha = 0.48f),
                    )
                }

                if (hasMainBody) {
                    Spacer(Modifier.height(if (isPureText) 2.dp else 14.dp))
                    ShareWeiboText(
                        html = status.textHtml,
                        color = theme.textPrimary,
                        linkColor = theme.accent,
                        fontSize = if (serif) 15.5.sp else 15.sp,
                        lineHeight = 26.5.sp,
                        fontFamily = bodyFont,
                    )
                }

                // 转发引文区块（左细竖线）
                status.retweeted?.let { quoted ->
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.2.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(99.dp))
                                .background(theme.accent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val quotedAuthor = quoted.user?.name.orEmpty()
                            if (quotedAuthor.isNotBlank()) {
                                Text(
                                    text = "QUOTED · @$quotedAuthor",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                    color = theme.accent,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            val hasQuotedBody = remember(quoted.textHtml) {
                                formatShareBodyText(quoted.textHtml).isNotBlank() ||
                                    quoted.textHtml.contains("<img", ignoreCase = true)
                            }
                            if (hasQuotedBody) {
                                ShareWeiboText(
                                    html = quoted.textHtml,
                                    color = theme.textPrimary.copy(alpha = 0.86f),
                                    linkColor = theme.accent,
                                    fontSize = 13.5.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = bodyFont,
                                )
                            }
                            if (quoted.pics.isNotEmpty()) {
                                Spacer(Modifier.height(9.dp))
                                SharePosterImageGrid(
                                    pics = quoted.pics,
                                    placeholderColor = theme.line,
                                    outerCorner = 6.dp,
                                    innerCorner = 3.dp,
                                )
                            } else if (quoted.video != null) {
                                Spacer(Modifier.height(9.dp))
                                SharePosterVideoCard(
                                    video = quoted.video,
                                    placeholderColor = theme.line,
                                    corner = 6.dp,
                                )
                            }
                        }
                    }
                }

                // 作者署名行
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        url = user?.avatar,
                        size = 34.dp,
                        verifiedKind = user?.verifiedKind ?: VerifiedKind.None,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.name?.ifBlank { "微博用户" } ?: "微博用户",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showMeta && (absoluteDate.isNotBlank() || status.source.isNotBlank())) {
                            Spacer(Modifier.height(1.dp))
                            val authorMeta = buildList {
                                if (absoluteDate.isNotBlank()) add(absoluteDate)
                                status.source.takeIf { it.isNotBlank() }?.let { add("来自 $it") }
                            }.joinToString(" · ")
                            Text(
                                text = authorMeta,
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (showStats && compactStats.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = compactStats,
                            fontSize = 11.sp,
                            color = theme.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 穿孔票根虚线 + 落款页脚（仅当开启 Cobo 标签 或 二维码时展示）
            if (showFooter) {
                EditorialTicketPerforation(
                    dashColor = theme.lineStrong,
                    notchColor = theme.matBg,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (showBrand) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.1.dp, theme.accent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "COBO",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp,
                                        color = theme.accent,
                                    )
                                }
                                Text(
                                    text = "微博图文剪报",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (showQr && qrBitmap != null) {
                                    "长按识别右侧二维码 · 查看完整原博"
                                } else {
                                    "分享自 Cobo 微博客户端"
                                },
                                fontSize = 10.5.sp,
                                color = theme.textSecondary,
                            )
                        } else {
                            Text(
                                text = "原博溯源",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "长按识别右侧二维码 · 查看完整原博",
                                fontSize = 10.5.sp,
                                color = theme.textSecondary,
                            )
                        }
                    }

                    if (showQr && qrBitmap != null) {
                        Spacer(Modifier.width(12.dp))
                        ShareQrBox(
                            qrBitmap = qrBitmap,
                            size = 50.dp,
                            corner = 6.dp,
                            borderColor = theme.lineStrong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialTicketPerforation(
    dashColor: Color,
    notchColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val midY = size.height / 2f
        val notchRadius = 7.dp.toPx()
        drawLine(
            color = dashColor,
            start = Offset(notchRadius + 4.dp.toPx(), midY),
            end = Offset(size.width - notchRadius - 4.dp.toPx(), midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
        )
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(0f, midY))
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(size.width, midY))
    }
}

// ============================================================================
// 共用子组件：Cobo 徽标、互动图标项、二维码框、媒体宫格、视频卡片
// ============================================================================

@Composable
private fun CoboBrandMark(accent: Color) {
    val endColor = Color(
        red = (accent.red * 0.72f).coerceIn(0f, 1f),
        green = (accent.green * 0.72f).coerceIn(0f, 1f),
        blue = (accent.blue * 0.88f).coerceIn(0f, 1f),
        alpha = 1f,
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(accent, endColor))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "C",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}

@Composable
private fun ShareMetricItem(
    icon: ImageVector,
    value: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Composable
private fun ShareQrBox(
    qrBitmap: ImageBitmap,
    size: Dp,
    corner: Dp,
    borderColor: Color,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color.White)
            .border(0.75.dp, borderColor, RoundedCornerShape(corner))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = qrBitmap,
            contentDescription = "原微博二维码",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SharePosterImageGrid(
    pics: List<Pic>,
    placeholderColor: Color,
    outerCorner: Dp = 12.dp,
    innerCorner: Dp = 6.dp,
) {
    val shown = pics.take(9)
    if (shown.size == 1) {
        val pic = shown[0]
        val aspect = if (pic.width > 0 && pic.height > 0) {
            (pic.width.toFloat() / pic.height.toFloat()).coerceIn(0.65f, 1.9f)
        } else {
            1.45f
        }
        AsyncImage(
            model = pic.large.ifBlank { pic.thumb },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(outerCorner))
                .background(placeholderColor),
        )
        return
    }
    val cols = if (shown.size == 2 || shown.size == 4) 2 else 3
    val rows = shown.chunked(cols)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(outerCorner)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until cols) {
                    val pic = row.getOrNull(i)
                    if (pic != null) {
                        AsyncImage(
                            model = pic.large.ifBlank { pic.thumb },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(innerCorner))
                                .background(placeholderColor),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharePosterVideoCard(
    video: VideoInfo,
    placeholderColor: Color,
    corner: Dp = 12.dp,
) {
    val durationLabel = remember(video.durationSec) { formatShareVideoDuration(video.durationSec) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(placeholderColor),
    ) {
        if (video.cover.isNotBlank()) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.74f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .border(0.8.dp, Color.White.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        if (video.title.isNotBlank() || durationLabel.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = video.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (durationLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.56f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = durationLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
