package com.cobo.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.data.Membership
import com.cobo.app.data.VerifiedKind
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshTexts
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun Avatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    verifiedKind: VerifiedKind = VerifiedKind.None,
) {
    val scheme = COUITheme.colorScheme
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (verifiedKind != VerifiedKind.None) {
            val badgeSize = (size.value * 0.36f).coerceIn(10f, 20f).dp
            VerifiedBadge(
                kind = verifiedKind,
                size = badgeSize,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

private val VerifiedGoldBg = Color(0xFFFFA000)
private val VerifiedBlueBg = Color(0xFF2B90FF)

/** 微博蓝V / 金V 圆形认证角标：外圈留一圈浅色描边与头像隔开，内绘白色居中 V 字。 */
@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    kind: VerifiedKind = VerifiedKind.Gold,
) {
    val showVerified = runCatching { com.cobo.app.App.settings.showVerifiedBadge }.getOrDefault(true)
    if (kind == VerifiedKind.None || !showVerified) return
    val scheme = COUITheme.colorScheme
    val fill = if (kind == VerifiedKind.Blue) VerifiedBlueBg else VerifiedGoldBg
    val ringColor = scheme.surfaceContainer
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(color = ringColor, radius = radius)
        val strokeRing = (this.size.minDimension * 0.08f).coerceAtLeast(1.dp.toPx())
        drawCircle(color = fill, radius = (radius - strokeRing).coerceAtLeast(1f))
        val w = this.size.width
        val h = this.size.height
        val vPath = Path().apply {
            moveTo(w * 0.31f, h * 0.34f)
            lineTo(w * 0.50f, h * 0.70f)
            lineTo(w * 0.69f, h * 0.34f)
        }
        drawPath(
            path = vPath,
            color = Color.White,
            style = Stroke(
                width = (this.size.minDimension * 0.145f).coerceAtLeast(1.3.dp.toPx()),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

internal val CompactBadgeHeight = 14.dp
internal val RegularBadgeHeight = 16.dp

/** VIP / 关注标记 / 粉丝兜底徽标共用的连续曲率圆角形状。 */
@Composable
internal fun badgeShape(compact: Boolean): Shape {
    val squircleEnabled = isSquircleEnabled()
    val radius = if (compact) 4.dp else 5.dp
    return remember(squircleEnabled, radius) {
        SquircleShape(
            cornerRadius = radius,
            squircleEnabled = squircleEnabled,
            capsuleCircleBlend = false,
        )
    }
}

private data class MemberBadgeStyle(
    val background: Color,
    val content: Color,
    val tag: String,
    val prefix: String,
)

/** 微博会员标识：VIP / SVIP / VVIP，并附上实际等级数字（如 SVIP10、VVIP2、VIP3）。 */
@Composable
fun MemberBadge(
    membership: Membership,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    rank: Int = 0,
    height: Dp = if (compact) CompactBadgeHeight else RegularBadgeHeight,
) {
    val showVip = runCatching { com.cobo.app.App.settings.showVipLevel }.getOrDefault(true)
    if (!showVip) return
    val style = when (membership) {
        Membership.None -> return
        Membership.Vip -> MemberBadgeStyle(MemberVipRed, Color.White, "vip_badge_vip", "VIP")
        Membership.Svip -> MemberBadgeStyle(MemberSvipRed, Color.White, "vip_badge_svip", "SVIP")
        Membership.Vvip -> MemberBadgeStyle(MemberVvipBg, MemberVvipGold, "vip_badge_vvip", "VVIP")
    }
    val text = if (rank > 0) "${style.prefix}$rank" else style.prefix
    val shape = badgeShape(compact)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(style.background)
            .padding(horizontal = if (compact) 4.dp else 5.dp)
            .testTag(style.tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (compact) 8.sp else 9.sp,
            lineHeight = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            color = style.content,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 微博会员的品牌红；超级会员用更深的一档，VVIP 用黑金，三档并排一眼分开。 */
private val MemberVipRed = Color(0xFFE6162D)
private val MemberSvipRed = Color(0xFFC4000B)
private val MemberVvipBg = Color(0xFF231F20)
private val MemberVvipGold = Color(0xFFF5D17A)

/**
 * 复用 ColorOS 下拉刷新（`FullPagePullToRefresh`）的转圈进度条样式：
 * 无底环、270° 单段圆弧、两端圆角半球帽（[StrokeCap.Round]），按 1000ms 线性旋转一周。
 */
@Composable
fun CoboRefreshSpinner(
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.primary,
    size: Dp = 26.dp,
    strokeWidth: Dp = Dp.Unspecified,
) {
    val transition = rememberInfiniteTransition(label = "cobo_refresh_spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cobo_refresh_spinner_angle",
    )
    Canvas(modifier = modifier.size(size)) {
        val widthPx = if (strokeWidth != Dp.Unspecified) {
            strokeWidth.toPx()
        } else {
            this.size.minDimension / 10f
        }
        drawArc(
            color = color,
            startAngle = -90f + rotation,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(widthPx / 2f, widthPx / 2f),
            size = ComposeSize(this.size.width - widthPx, this.size.height - widthPx),
            style = Stroke(width = widthPx, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CoboRefreshSpinner()
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(44.dp), tint = scheme.onSurfaceSecondary)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            color = scheme.onSurfaceSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = scheme.onSurfaceSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

/**
 * 分组小标题，直接用 COUI [SmallTitle]。
 *
 * 它贴着下面那张卡片走，不站在两张卡片中间：在 8dp 项间距的列表里默认上留白 16dp、下留白 0dp，
 * 与上一组卡片的视觉距离是 24dp，与自己这一组只有 8dp。页面最顶上第一个分组传 `topPadding = 4.dp`，
 * 紧跟在页头卡片后面的传 `8.dp`。
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 0.dp,
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin = PaddingValues(
            start = horizontalPadding,
            top = topPadding,
            end = horizontalPadding,
            bottom = bottomPadding,
        ),
    )
}

@Composable
fun ActionIcon(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = COUITheme.colorScheme.onSurfaceSecondary,
    /** 只读流里按钮留着给人看计数，但不响应点击。 */
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription ?: text.ifBlank { null }, Modifier.size(17.dp), tint = color)
        if (text.isNotBlank()) {
            Text(text, fontSize = 12.sp, color = color)
        }
    }
}

/**
 * 顶栏文字按钮：高度与 [BackIconButton] 的正圆一致（40dp）。
 *
 * 复用搜索栏「取消」那个 [SearchBarActionButton] 的形状与光效——同一份胶囊、同一圈焦散外光
 * （裁在胶囊外侧，不会照亮内部）、同一套按下动效。底色换成 [HighlightedIconButton] 圆形材质的
 * 那份取样色：两者在顶栏左右并排时深度一致，不会一边深一边浅。
 */
@Composable
fun TopBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SearchBarActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = toolbarMaterialFill(),
    )
}

/** 顶栏材质底色：和返回键的圆形底同一份材质，取它中心那一档。 */
@Composable
private fun toolbarMaterialFill(): Color =
    // 圆形底是中心深、边沿浅的径向渐变，眼睛读的是它中间那一片；胶囊铺平取中心色，并排时看着一样深。
    HighlightedIconButtonDefaults.fillCenter(COUITheme.colorScheme.background.luminance() < 0.5f)
        .copy(alpha = HighlightedIconButtonDefaults.FillAlpha)

@Composable
fun BackIconButton(onClick: () -> Unit) {
    HighlightedIconButton(onClick = onClick) {
        Icon(COUIIcons.Back, "返回", Modifier.size(24.dp))
    }
}

/**
 * 设置卡片里分隔两行的细线。
 *
 * COUI 的 [HorizontalDivider] 是整宽画到卡片边缘的，而 ColorOS 17 的设置卡片里分割线只从
 * 行标题的左缘起画、右侧留同样的内缩。标题的左缘就是 [BasicComponentDefaults.InsideMargin]
 * 的横向值，这里直接取它，将来 COUI 改了这个常量分割线会自己跟上。
 */
@Composable
fun PreferenceDivider() {
    val inset = BasicComponentDefaults.InsideMargin.calculateLeftPadding(LayoutDirection.Ltr)
    HorizontalDivider(Modifier.padding(horizontal = inset))
}

@Composable
fun ClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        content()
    }
}

fun fmtCount(value: Int): String {
    if (value <= 0) return "0"
    if (value < 10000) return value.toString()
    val wan = value / 10000.0
    return if (wan >= 100) "${wan.toInt()}万" else String.format("%.1f万", wan)
}

val FullPageRefreshTexts = FullPagePullToRefreshTexts(
    pulling = "下拉刷新",
    thresholdReached = "松开刷新",
    refreshing = "刷新中…",
    complete = "刷新完成",
)

/** How long the success hint stays up before the page rebounds; COUI's 800ms default drags. */
const val FullPageRefreshHoldMillis = 200L

@Composable
fun navBottomPadding(): Dp =
    androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

@Composable
fun systemNavigationBarHeight(): Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = view.rootWindowInsets
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        ?.bottom ?: 0
    val fallback = navBottomPadding()
    return if (px > 0) with(density) { px.toDp() } else maxOf(fallback, 20.dp)
}
