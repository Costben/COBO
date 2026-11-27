package com.cobo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Membership
import com.cobo.app.data.Pic
import com.cobo.app.data.Status
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.data.stripHtml
import com.cobo.app.platform.encodeImageBitmapToPng
import com.cobo.app.platform.generateQrCodeBitmap
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.saveImageBytesToGallery
import com.cobo.app.platform.shareImageBytes
import com.cobo.app.ui.isDarkTheme
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * 微博图文分享底栏面板：将当前微博（作者信息、正文、配图/视频封面、转发原博、互动统计及二维码）
 * 拼合成一张完整分享长图卡片，支持切换浅/深色卡片样式、开关二维码，并经系统分享面板导出或保存到相册。
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
    var darkCard by remember(status.id, defaultDark) { mutableStateOf(defaultDark) }
    var showQr by remember(status.id) { mutableStateOf(true) }
    var busy by remember(status.id) { mutableStateOf(false) }
    val shareLayer = rememberGraphicsLayer()

    LaunchedEffect(status.id) { show = true }

    val shareUrl = remember(status.id, status.mid) { buildWeiboShareUrl(status) }
    val qrBitmap = remember(shareUrl, showQr) {
        if (showQr) generateQrCodeBitmap(shareUrl, 192) else null
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
                ShareOptionChip(
                    label = if (darkCard) "深色卡片" else "浅色卡片",
                    active = darkCard,
                    onClick = { if (!busy) darkCard = !darkCard },
                )
                Spacer(Modifier.width(8.dp))
                ShareOptionChip(
                    label = "二维码",
                    active = showQr,
                    onClick = { if (!busy) showQr = !showQr },
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 0.5.dp,
                        color = scheme.dividerLine.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .verticalScroll(rememberScrollState()),
            ) {
                // GraphicsLayer 挂在可滚动内容本体上而非外层视口上，确保长图完整录制全部文字、配图与二维码。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            shareLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(shareLayer)
                        },
                ) {
                    StatusSharePoster(
                        status = status,
                        dark = darkCard,
                        qrBitmap = qrBitmap,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

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
                    Text(if (busy) "生成中…" else "保存图片")
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
                    modifier = Modifier.weight(1f).testTag("status_share_system"),
                ) {
                    Text(if (busy) "生成中…" else "系统分享")
                }
            }
        }
    }
}

@Composable
private fun ShareOptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val bg = if (active) {
        scheme.primary.copy(alpha = 0.14f)
    } else {
        scheme.onSurface.copy(alpha = 0.07f)
    }
    val fg = if (active) scheme.primary else scheme.onSurfaceSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

@Composable
private fun StatusSharePoster(
    status: Status,
    dark: Boolean,
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
) {
    val canvasBg = if (dark) Color(0xFF101216) else Color(0xFFF1F3F7)
    val cardBg = if (dark) Color(0xFF1C1F26) else Color(0xFFFFFFFF)
    val quoteBg = if (dark) Color(0xFF252933) else Color(0xFFF6F7FA)
    val textPrimary = if (dark) Color(0xFFF2F4F8) else Color(0xFF181B22)
    val textSecondary = if (dark) Color(0xFF9AA2B1) else Color(0xFF6B7280)
    val dividerColor = if (dark) Color(0xFF2E3340) else Color(0xFFE7EAF0)
    val accent = COUITheme.colorScheme.primary

    val user = status.user
    val mainText = remember(status.textHtml) { formatShareBodyText(status.textHtml) }
    val timeAndMeta = remember(status.createdAt, status.source, status.region) {
        buildList {
            formatWeiboTime(status.createdAt).takeIf { it.isNotBlank() }?.let { add(it) }
            status.source.takeIf { it.isNotBlank() }?.let { add("来自 $it") }
            status.region.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(canvasBg)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(0.5.dp, dividerColor, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            // 作者栏（分享图统一展示公开微博昵称，避免带出私密本地备注）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    url = user?.avatar,
                    size = 42.dp,
                    verifiedKind = user?.verifiedKind ?: com.cobo.app.data.VerifiedKind.None,
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
                    if (timeAndMeta.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = timeAndMeta,
                            fontSize = 11.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (mainText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = mainText,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = textPrimary,
                )
            }

            if (status.pics.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SharePosterImageGrid(pics = status.pics, placeholderColor = quoteBg)
            } else if (status.video != null) {
                Spacer(Modifier.height(12.dp))
                SharePosterVideoCard(video = status.video, placeholderColor = quoteBg)
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
                    val quotedBody = remember(quoted.textHtml) { formatShareBodyText(quoted.textHtml) }
                    val quotedCombined = buildString {
                        if (quotedAuthor.isNotBlank()) {
                            append("@")
                            append(quotedAuthor)
                            if (quotedBody.isNotBlank()) append("：")
                        }
                        append(quotedBody)
                    }
                    if (quotedCombined.isNotBlank()) {
                        Text(
                            text = quotedCombined,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = textPrimary.copy(alpha = 0.92f),
                        )
                    }
                    if (quoted.pics.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SharePosterImageGrid(pics = quoted.pics, placeholderColor = dividerColor)
                    } else if (quoted.video != null) {
                        Spacer(Modifier.height(8.dp))
                        SharePosterVideoCard(video = quoted.video, placeholderColor = dividerColor)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.6.dp)
            Spacer(Modifier.height(12.dp))

            val stats = remember(status.reposts, status.comments, status.attitudes) {
                buildList {
                    if (status.reposts > 0) add("转发 ${fmtCount(status.reposts)}")
                    if (status.comments > 0) add("评论 ${fmtCount(status.comments)}")
                    if (status.attitudes > 0) add("赞 ${fmtCount(status.attitudes)}")
                }.joinToString(" · ")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.14f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Cobo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )
                        }
                        if (stats.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stats,
                                fontSize = 11.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = if (qrBitmap != null) "长按识别二维码查看原微博" else "分享自 Cobo 微博客户端",
                        fontSize = 11.sp,
                        color = textSecondary,
                    )
                }

                if (qrBitmap != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
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
            }
        }
    }
}

@Composable
private fun SharePosterImageGrid(
    pics: List<Pic>,
    placeholderColor: Color,
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
                .clip(RoundedCornerShape(12.dp))
                .background(placeholderColor),
        )
        return
    }
    val cols = if (shown.size == 2 || shown.size == 4) 2 else 3
    val rows = shown.chunked(cols)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                                .clip(RoundedCornerShape(8.dp))
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
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
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        if (video.title.isNotBlank()) {
            Text(
                text = video.title,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
