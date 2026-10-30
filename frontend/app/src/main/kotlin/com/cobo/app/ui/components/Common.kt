package com.cobo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshTexts
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
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
}

/** 昵称后面的认证勾。搜索结果的用户行和信息流共用同一个尺寸比例。 */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", fontSize = (size.value * 0.64f).sp, color = scheme.onPrimary)
    }
}

/** 微博会员的红标：VIP / SVIP 两种，和微博客户端的两种标识一一对应。 */
@Composable
fun MemberBadge(
    membership: Membership,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (membership == Membership.None) return
    val svip = membership == Membership.Svip
    val shape = RoundedCornerShape(if (compact) 4.dp else 5.dp)
    Box(
        modifier = modifier
            .height(if (compact) 15.dp else 16.dp)
            .clip(shape)
            .background(if (svip) MemberSvipRed else MemberVipRed)
            .padding(horizontal = if (compact) 4.dp else 5.dp)
            .testTag(if (svip) "vip_badge_svip" else "vip_badge_vip"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (svip) "SVIP" else "VIP",
            fontSize = if (compact) 8.sp else 9.sp,
            lineHeight = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 微博会员的品牌红；超级会员用更深的一档，两者并排也能一眼分开。 */
private val MemberVipRed = Color(0xFFE6162D)
private val MemberSvipRed = Color(0xFFC4000B)

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = COUITheme.colorScheme.onSurfaceSecondary,
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
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, text.ifBlank { null }, Modifier.size(17.dp), tint = color)
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
