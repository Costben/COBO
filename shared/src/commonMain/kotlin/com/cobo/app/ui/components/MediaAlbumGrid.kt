@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cobo.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.data.MediaWallItem
import com.cobo.app.data.Pic
import com.cobo.app.data.Status
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.formatAlbumDuration
import com.cobo.app.data.mediaWallPhotoGallery
import com.cobo.app.ui.imageOrigin
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme

private val InnerTileCornerRadius = 8.dp

/**
 * 3 列方形缩略图相册墙：点击图片进连续看图，点击视频直接播放，长按任意格跳转到原微博详情。
 *
 * [topOuterCornerRadius] 控制首行左上角与右上角的外侧圆角（默认与内侧拼接圆角一致；
 * 个人主页等紧贴卡片布局的场景可传入 [CardDefaults.GroupCornerRadius] 与微博卡片顶角保持统一）。
 */
fun LazyListScope.mediaAlbumGridItems(
    items: List<MediaWallItem>,
    horizontalPadding: Dp = 12.dp,
    topOuterCornerRadius: Dp = InnerTileCornerRadius,
    topOuterWeight: Float = CardDefaults.GroupWeight,
    rowModifier: Modifier = Modifier,
    onPhotoClick: (pics: List<Pic>, index: Int) -> Unit,
    onVideoClick: (VideoInfo) -> Unit,
    onOpenPost: (Status) -> Unit,
) {
    val (galleryPics, photoIndexByKey) = mediaWallPhotoGallery(items)
    val rows = items.chunked(3)
    itemsIndexed(
        items = rows,
        key = { _, row -> "album_row_${row.first().key}" },
        contentType = { _, _ -> "album_row" },
    ) { rowIndex, row ->
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (col in 0 until 3) {
                val item = row.getOrNull(col)
                if (item != null) {
                    val isTopStartOuter = rowIndex == 0 && col == 0
                    val isTopEndOuter = rowIndex == 0 && col == 2
                    MediaAlbumTile(
                        item = item,
                        topStartRadius = if (isTopStartOuter) topOuterCornerRadius else InnerTileCornerRadius,
                        topEndRadius = if (isTopEndOuter) topOuterCornerRadius else InnerTileCornerRadius,
                        cornerWeight = if ((isTopStartOuter || isTopEndOuter) && topOuterCornerRadius != InnerTileCornerRadius) {
                            topOuterWeight
                        } else {
                            SquircleDefaults.Weight
                        },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (item) {
                                is MediaWallItem.Photo -> {
                                    val idx = photoIndexByKey[item.key] ?: 0
                                    onPhotoClick(galleryPics, idx)
                                }
                                is MediaWallItem.Video -> onVideoClick(item.video)
                            }
                        },
                        onLongClick = { onOpenPost(item.status) },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaAlbumTile(
    item: MediaWallItem,
    topStartRadius: Dp,
    topEndRadius: Dp,
    cornerWeight: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val squircleEnabled = isSquircleEnabled()
    val shape = remember(topStartRadius, topEndRadius, cornerWeight, squircleEnabled) {
        SquircleShape(
            topStart = topStartRadius,
            topEnd = topEndRadius,
            bottomEnd = InnerTileCornerRadius,
            bottomStart = InnerTileCornerRadius,
            squircleEnabled = squircleEnabled,
            weight = cornerWeight,
        )
    }
    val originUrl = when (item) {
        is MediaWallItem.Photo -> item.pic.large.ifBlank { item.pic.thumb }
        is MediaWallItem.Video -> ""
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .let { base ->
                if (item is MediaWallItem.Photo && originUrl.isNotBlank()) {
                    base.imageOrigin(originUrl, InnerTileCornerRadius, aspect = 1f, model = item.thumbUrl)
                } else {
                    base
                }
            }
            .clip(shape)
            .background(scheme.surfaceContainer)
            .combinedClickable(
                role = Role.Image,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        if (item.thumbUrl.isNotBlank()) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (item is MediaWallItem.Video) {
            Icon(
                imageVector = COUIIcons.Play,
                contentDescription = null,
                tint = scheme.onSurfaceSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
            )
        }

        if (item is MediaWallItem.Video) {
            val badgeText = if (item.video.isLive) {
                "直播"
            } else {
                formatAlbumDuration(item.durationSec).ifEmpty { "视频" }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = COUIIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        } else if (item.isLivePhoto) {
            Text(
                text = "实况",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

internal enum class MediaWallFooterState {
    Hidden,
    Loading,
    ManualLoadMore,
    ErrorRetry,
    End,
}

/**
 * 个人主页与相册/视频墙底部的统一状态栏：固定最小高度并通过柔和交叉淡入淡出切换，
 * 消除分页加载中、手动继续与底部提示交替时的高度跳动与闪烁感。
 */
@Composable
internal fun MediaWallFooter(
    state: MediaWallFooterState,
    loadingText: String = "正在加载更多…",
    loadMoreText: String = "轻点继续加载更多",
    errorText: String = "加载失败，轻点重试",
    endText: String = "长按任意缩略图可查看原微博",
    onLoadMore: () -> Unit,
    onRetry: () -> Unit = onLoadMore,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = tween(durationMillis = 180),
            label = "media_wall_footer",
        ) { target ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (target) {
                    MediaWallFooterState.Hidden -> Unit
                    MediaWallFooterState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CoboRefreshSpinner(
                                size = 16.dp,
                                strokeWidth = 2.dp,
                                color = scheme.onSurfaceSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = loadingText,
                                fontSize = 13.sp,
                                color = scheme.onSurfaceSecondary,
                            )
                        }
                    }
                    MediaWallFooterState.ManualLoadMore -> {
                        Text(
                            text = loadMoreText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onLoadMore)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    MediaWallFooterState.ErrorRetry -> {
                        Text(
                            text = errorText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    MediaWallFooterState.End -> {
                        Text(
                            text = endText,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }
}
