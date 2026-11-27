package com.cobo.app.screen

import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.copyToClipboard
import com.cobo.app.platform.setStatusBarDarkIcons
import com.cobo.app.platform.urlEncode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.rememberIsTopRoute
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import com.cobo.app.data.MeIconStyle
import com.cobo.app.data.MediaWallFilter
import com.cobo.app.data.Membership
import com.cobo.app.data.MuteUserMode
import com.cobo.app.data.NotificationTab
import com.cobo.app.data.Pic
import com.cobo.app.data.ProfileDetail
import com.cobo.app.data.ProfileGroupSelection
import com.cobo.app.data.ProfileRenameRecord
import com.cobo.app.data.ReadLater
import com.cobo.app.data.RelationKind
import com.cobo.app.data.Status
import com.cobo.app.data.Updater
import com.cobo.app.data.User
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.WEIBO_PROFILE_EDIT_URL
import com.cobo.app.data.computeProfileRenameStatus
import com.cobo.app.data.extractMediaWallItems
import com.cobo.app.ui.MeIconTint
import com.cobo.app.ui.MeIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalBottomBarBounds
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FoldableIntroText
import com.cobo.app.ui.components.FollowTagBadge
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.ProfileEditDialog
import com.cobo.app.ui.components.ProfileGroupDialog
import com.cobo.app.ui.components.ProfileInfoDialog
import com.cobo.app.ui.components.ProfileQrDialog
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.VerifiedBadge
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.mediaAlbumGridItems
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.components.fmtCount
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.FullPageRefreshState
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import androidx.compose.ui.layout.onSizeChanged
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.MaterialShadowEdgeDefaults
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Undo
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import kotlin.math.roundToInt

internal data class CoverContrast(
    val useWhiteContent: Boolean = false,
)

/**
 * 判断封面裁切绘制区中 `[bandTopPx, bandBottomPx]` 这一横条叠完顶部 30% 黑色遮罩后是否偏深：
 * 用于个人主页和“我”页未滚动离开封面时，自适应把顶部文字与状态栏图标反色为白色。
 */
internal fun isCoverCropBandDark(
    width: Int,
    height: Int,
    pixelAt: (x: Int, y: Int) -> Int,
    containerWidthPx: Float,
    containerHeightPx: Float,
    bandTopPx: Float,
    bandBottomPx: Float,
    overlayAlpha: Float = 0.3f,
): Boolean {
    if (width <= 0 || height <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) return false
    val clampedTop = bandTopPx.coerceIn(0f, containerHeightPx)
    val clampedBottom = bandBottomPx.coerceIn(0f, containerHeightPx)
    if (clampedBottom <= clampedTop) return false

    val scale = maxOf(containerWidthPx / width.toFloat(), containerHeightPx / height.toFloat())
    val drawnW = width * scale
    val drawnH = height * scale
    val offsetX = (containerWidthPx - drawnW) / 2f
    val offsetY = (containerHeightPx - drawnH) / 2f
    val factor = (1f - overlayAlpha).coerceIn(0f, 1f).toDouble()

    val cols = 12
    val rows = 6
    var sum = 0.0
    var count = 0
    for (rIdx in 0 until rows) {
        val sy = clampedTop + (clampedBottom - clampedTop) * ((rIdx + 0.5f) / rows)
        val py = ((sy - offsetY) / scale).toInt().coerceIn(0, height - 1)
        for (cIdx in 0 until cols) {
            val sx = containerWidthPx * ((cIdx + 0.5f) / cols)
            val px = ((sx - offsetX) / scale).toInt().coerceIn(0, width - 1)
            val argb = pixelAt(px, py)
            val red = ((argb ushr 16) and 0xFF) * factor
            val green = ((argb ushr 8) and 0xFF) * factor
            val blue = (argb and 0xFF) * factor
            sum += (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
            count++
        }
    }
    return count > 0 && (sum / count) < 0.48
}

@Composable
internal fun rememberCoverContrast(
    coverUrl: String?,
    @Suppress("UNUSED_PARAMETER") topBarHeight: Dp,
    active: Boolean,
    manageStatusBar: Boolean,
): CoverContrast {
    val effective = if (active && !coverUrl.isNullOrBlank()) CoverContrast(useWhiteContent = true) else CoverContrast()
    val darkTheme = isDarkTheme()
    val lightStatusBars = !(darkTheme || effective.useWhiteContent)
    LaunchedEffect(manageStatusBar, lightStatusBars) {
        if (manageStatusBar) {
            setStatusBarDarkIcons(lightStatusBars)
        }
    }
    DisposableEffect(manageStatusBar, darkTheme) {
        onDispose {
            if (manageStatusBar) {
                setStatusBarDarkIcons(!darkTheme)
            }
        }
    }
    return effective
}

@Composable
fun ProfileScreen(nav: NavState, uid: String?) {
    val key = uid.orEmpty()
    val tag = FollowTags[key]
    // 顶栏的「更多」菜单要用到头像和昵称（二维码弹窗），而资料本身在内容区里加载，
    // 这里只收一份镜像，不参与页面布局。
    var profileUser by remember(uid) { mutableStateOf<User?>(null) }
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    val tabState = remember(uid) { ProfileTabState() }
    val density = LocalDensity.current
    var activeCoverUrl by remember(uid) { mutableStateOf<String?>(null) }
    val scrollAlpha = remember(tabState, listState, density) {
        {
            val scrolled = tabState.effectiveScrollOffsetPx(listState)
            AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
        }
    }
    val scrolledPastCover by remember(scrollAlpha) {
        derivedStateOf { scrollAlpha() >= 0.45f }
    }
    val isTopRoute = rememberIsTopRoute(nav)
    val coverContrast = rememberCoverContrast(
        coverUrl = activeCoverUrl,
        topBarHeight = topBarHeight,
        active = !scrolledPastCover && isTopRoute,
        manageStatusBar = isTopRoute,
    )
    val titleColor = if (coverContrast.useWhiteContent) Color.White else COUITheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        // 内容铺到顶栏底下，滚动后顶栏的渐变玻璃才渐入；未滑动时完全透明不模糊封面。
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffectsEnabled(), backdrop)) {
            CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
                ProfileContent(
                    nav = nav,
                    uid = uid,
                    modifier = Modifier.fillMaxSize(),
                    topInset = topBarHeight,
                    listState = listState,
                    state = tabState,
                    onActiveCover = { activeCoverUrl = it },
                    onUser = { profileUser = it },
                )
            }
        }
        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageContainerColor(),
                    scrollFraction = scrollAlpha,
                ) {
                    SmallTopAppBar(
                        title = "主页",
                        titleColor = titleColor,
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            ProfileMoreAction(
                                tag = tag,
                                uid = uid.orEmpty().ifBlank { App.repo.session?.uid.orEmpty() },
                                user = profileUser,
                                nav = nav,
                                onSelect = { picked -> FollowTags.set(key, picked) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 主页右上角的三点菜单：给这个人挂一个关注标记，三种互斥，再点一次已选中的那项就取消。
 * 用 COUI 的 ListPopup 而不是 Material 的 DropdownMenu，和首页筛选菜单同一套观感。
 */
/**
 * 主页右上角的三点菜单。
 *
 * 第一段是三种互斥的关注标记（再点一次取消）；后面依次是关注分组、只读资料、举报与拉黑。
 * 自己的主页上不出现分组 / 举报 / 拉黑这三项，纯本地模式也藏起来（没有账号可写）。
 */
@Composable
private fun ProfileMoreAction(
    tag: FollowTag?,
    uid: String,
    user: User?,
    nav: NavState,
    onSelect: (FollowTag?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var groupSelection by remember(uid) { mutableStateOf<ProfileGroupSelection?>(null) }
    var loadingGroups by remember(uid) { mutableStateOf(false) }
    var infoOpen by remember(uid) { mutableStateOf(false) }
    var info by remember(uid) { mutableStateOf<ProfileDetail?>(null) }
    var infoLoading by remember(uid) { mutableStateOf(false) }
    var qrOpen by remember(uid) { mutableStateOf(false) }
    var blockOpen by remember(uid) { mutableStateOf(false) }
    var editOpen by remember(uid) { mutableStateOf(false) }
    var remarkDialogOpen by remember(uid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = App.context
    val guest = App.repo.guest
    val self = uid.isNotBlank() && uid == App.repo.session?.uid
    val canManage = uid.isNotBlank() && !self && !guest
    val targetUid = user?.id?.takeIf { it.isNotBlank() } ?: uid
    val isSelfTarget = targetUid.isNotBlank() && targetUid == App.repo.session?.uid
    val canLocalMute = targetUid.isNotBlank() && !isSelfTarget
    val canRemark = canLocalMute
    val currentRemark = UserRemarks[targetUid]
    val muteMode = ContentFilters.muteMode(targetUid)
    val profileUrl = "https://weibo.com/u/$uid"

    fun copyLink() {
        copyToClipboard(profileUrl, "url")
        App.toast("已复制链接")
    }

    LaunchedEffect(infoOpen, uid) {
        if (!infoOpen) return@LaunchedEffect
        info = null
        infoLoading = true
        info = runCatching { App.repo.profileDetail(uid) }.getOrNull()
        infoLoading = false
    }

    Box {
        HighlightedIconButton(
            onClick = { expanded = true },
            modifier = Modifier.couiTopAppBarActionItem().testTag("profile_more"),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.semantics { contentDescription = "更多" },
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(LocalContentColor.current)
                    )
                }
            }
        }
        OverlayCascadingListPopup(
            show = expanded,
            entries = buildList {
                add(
                    DropdownEntry(
                        items = buildList {
                            FollowTag.entries.forEach { item ->
                                add(
                                    DropdownItem(
                                        text = item.menuLabel,
                                        selected = item == tag,
                                        onClick = { onSelect(if (item == tag) null else item) },
                                    )
                                )
                            }
                            if (canManage) {
                                add(
                                    DropdownItem(
                                        text = "编辑关注分组",
                                        enabled = !loadingGroups,
                                        onClick = {
                                            if (!loadingGroups) {
                                                val effectiveFollowing = user?.let {
                                                    App.repo.isFollowingUser(uid, it.following)
                                                }
                                                if (effectiveFollowing == false) {
                                                    // 服务端对未关注的人只会静默吞掉，先在本地拦下来。
                                                    App.toast("先关注对方才能设置关注分组")
                                                } else {
                                                    scope.launch {
                                                        loadingGroups = true
                                                        runCatching { App.repo.profileGroups(uid) }
                                                            .onSuccess { groupSelection = it }
                                                            .onFailure { App.toast(it.message ?: "分组加载失败") }
                                                        loadingGroups = false
                                                    }
                                                }
                                            }
                                        },
                                    )
                                )
                            }
                        },
                    )
                )
                add(
                    DropdownEntry(
                        items = buildList {
                            if (self) {
                                add(DropdownItem(text = "设置", onClick = { nav.push(Route.Settings) }))
                                add(DropdownItem(text = "编辑资料", onClick = { editOpen = true }))
                                add(
                                    DropdownItem(
                                        text = "网页端编辑资料",
                                        onClick = { nav.push(Route.Web(WEIBO_PROFILE_EDIT_URL, "编辑资料")) },
                                    ),
                                )
                            }
                            add(DropdownItem(text = "基本资料", onClick = { infoOpen = true }))
                            if (canRemark) {
                                add(
                                    DropdownItem(
                                        text = if (currentRemark != null) "修改本地备注" else "设置本地备注",
                                        summary = currentRemark,
                                        onClick = { remarkDialogOpen = true },
                                    ),
                                )
                            }
                            add(DropdownItem(text = "复制链接", onClick = { copyLink() }))
                            add(DropdownItem(text = "二维码", onClick = { qrOpen = true }))
                        },
                    )
                )
                if (canLocalMute) {
                    add(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "本地静音",
                                    selected = muteMode == MuteUserMode.All,
                                    onClick = {
                                        val next = if (muteMode == MuteUserMode.All) null else MuteUserMode.All
                                        ContentFilters.setMutedUser(targetUid, user?.name.orEmpty(), next)
                                        App.toast(if (next == null) "已取消静音" else "已静音该用户")
                                    },
                                ),
                                DropdownItem(
                                    text = "屏蔽其转发",
                                    selected = muteMode == MuteUserMode.RepostsOnly,
                                    onClick = {
                                        val next = if (muteMode == MuteUserMode.RepostsOnly) null else MuteUserMode.RepostsOnly
                                        ContentFilters.setMutedUser(targetUid, user?.name.orEmpty(), next)
                                        App.toast(if (next == null) "已恢复显示其转发" else "已屏蔽该用户的转发")
                                    },
                                ),
                            ),
                        )
                    )
                }
                if (canManage) {
                    add(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "举报",
                                    onClick = {
                                        nav.push(Route.Web(reportUrl(uid), "举报"))
                                    },
                                ),
                                DropdownItem(
                                    text = "加入黑名单",
                                    alert = true,
                                    onClick = { blockOpen = true },
                                ),
                            ),
                        )
                    )
                }
            },
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        )
    }

    var deletedGroupIds by remember(uid) { mutableStateOf<Set<String>>(emptySet()) }
    groupSelection?.let { selection ->
        ProfileGroupDialog(
            selection = selection,
            onCreate = { App.repo.createProfileGroup(it) },
            onRename = { group, name -> App.repo.renameProfileGroup(group.gid, name) },
            onDelete = { group ->
                App.repo.deleteProfileGroup(group.gid)
                deletedGroupIds = deletedGroupIds + group.gid
            },
            onReorder = { App.repo.reorderProfileGroups(it) },
            onDismiss = {
                groupSelection = null
                deletedGroupIds = emptySet()
            },
            onConfirm = { selected ->
                val deleted = deletedGroupIds
                groupSelection = null
                deletedGroupIds = emptySet()
                val effectiveOrigin = selection.assigned.filterNot { it in deleted }.toSet()
                val effectiveSelected = selected.filterNot { it in deleted }.toSet()
                if (effectiveSelected != effectiveOrigin) {
                    scope.launch {
                        runCatching {
                            App.repo.setProfileGroups(uid, effectiveSelected.toList(), effectiveOrigin.toList(), deleted)
                        }
                            .onSuccess { App.toast("已更新关注分组") }
                            .onFailure { App.toast(it.message ?: "设置分组失败") }
                    }
                }
            },
        )
    }

    if (infoOpen) {
        ProfileInfoDialog(
            user = user,
            detail = info,
            loading = infoLoading,
            onEdit = if (self) ({ editOpen = true }) else null,
            onDismiss = { infoOpen = false },
        )
    }

    if (editOpen) {
        ProfileEditDialog(
            user = user,
            onOpenWeb = { nav.push(Route.Web(WEIBO_PROFILE_EDIT_URL, "编辑资料")) },
            onDismiss = { editOpen = false },
        )
    }

    if (qrOpen) {
        ProfileQrDialog(user = user, url = profileUrl, onDismiss = { qrOpen = false })
    }

    if (blockOpen) {
        ConfirmDialog(
            title = "加入黑名单",
            message = "加入后，对方将无法查看你的微博、与你互动或关注你。",
            confirmText = "加入黑名单",
            onDismiss = { blockOpen = false },
            onConfirm = {
                blockOpen = false
                scope.launch {
                    runCatching { App.repo.blockUser(uid) }
                        .onSuccess {
                            App.toast("已加入黑名单")
                            App.notifyFollowChanged()
                        }
                        .onFailure { App.toast(it.message ?: "操作失败") }
                }
            },
        )
    }

    if (remarkDialogOpen && targetUid.isNotBlank()) {
        UserRemarkDialog(
            originalName = user?.name.orEmpty(),
            initialRemark = currentRemark.orEmpty(),
            onDismiss = { remarkDialogOpen = false },
            onConfirm = { newRemark ->
                remarkDialogOpen = false
                UserRemarks.set(targetUid, newRemark)
                App.toast(if (newRemark.isBlank()) "已清除本地备注" else "已保存本地备注")
            },
        )
    }
}

@Composable
private fun UserRemarkDialog(
    originalName: String,
    initialRemark: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialRemark) }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "设置本地备注",
        summary = originalName.takeIf { it.isNotBlank() }?.let { "原昵称：$it（最多 24 字，留空可清除）" }
            ?: "最多 24 字，留空可清除备注",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val pending = result
                if (pending != null) onConfirm(pending) else onDismiss()
            }
        },
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { if (it.length <= 24) text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                label = "输入本地备注名",
                backgroundMode = TextFieldMode.Rectangle,
                singleLine = true,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = if (text.isBlank() && initialRemark.isNotBlank()) "清除" else "保存",
                    onClick = {
                        result = text.trim()
                        show = false
                    },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 网页版举报入口：`type=3` 是举报用户，`url` 指向他的主页。 */
private fun reportUrl(uid: String): String {
    val target = urlEncode("/u/$uid")
    return "https://service.account.weibo.com/reportspam" +
        "?rid=$uid&from=10106&type=3&url=$target&bottomnav=1&wvr=5"
}

/**
 * 个人主页内容状态，供主壳在切换底部 Tab 时保留已加载的资料、分页与折叠偏移，避免切回时重置。
 */
@Stable
class ProfileTabState {
    var user by mutableStateOf<User?>(null)
    val items = mutableStateListOf<Status>()
    var sinceId by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var end by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resolvedUid by mutableStateOf<String?>(null)
    var loadedUid by mutableStateOf<String?>(null)

    var selectedTabIndex by mutableIntStateOf(0)
    var headerCollapsePx by mutableFloatStateOf(0f)
    var usingTabbedLayout by mutableStateOf(false)
    internal var pagerListStates: Map<Int, LazyListState> = emptyMap()

    fun activeListStateOrNull(): LazyListState? =
        if (usingTabbedLayout) pagerListStates[selectedTabIndex] else null

    fun effectiveScrollOffsetPx(fallbackListState: LazyListState): Float {
        if (!usingTabbedLayout) {
            return if (fallbackListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else fallbackListState.firstVisibleItemScrollOffset.toFloat()
        }
        val activeList = pagerListStates[selectedTabIndex] ?: fallbackListState
        val subOffset = if (activeList.firstVisibleItemIndex > 0) Float.MAX_VALUE
        else activeList.firstVisibleItemScrollOffset.toFloat()
        return if (headerCollapsePx >= 1f) {
            maxOf(headerCollapsePx, headerCollapsePx + subOffset)
        } else {
            subOffset
        }
    }
}

private enum class ProfileContentTab(val label: String) {
    Posts("微博"),
    Album("相册"),
    Videos("视频"),
}

@Composable
fun ProfileContent(
    nav: NavState,
    uid: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    state: ProfileTabState? = null,
    onActiveCover: (String?) -> Unit = {},
    onUser: (User?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val tabState = state ?: remember(uid) { ProfileTabState() }
    val tabs = remember { ProfileContentTab.entries }
    val pagerState = rememberPagerState(initialPage = tabState.selectedTabIndex.coerceIn(0, tabs.lastIndex)) { tabs.size }
    val selectedTab = tabs[pagerState.currentPage.coerceIn(0, tabs.lastIndex)]
    val albumListState = rememberLazyListState()
    val videoListState = rememberLazyListState()
    var autoFilling by remember(uid, selectedTab) { mutableStateOf(false) }
    var autoFillExhausted by remember(uid, selectedTab) { mutableStateOf(false) }
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val topInsetPx = with(density) { topInset.toPx() }
    var fullHeaderHeightPx by remember { mutableIntStateOf(0) }

    val pagerStatesMap = remember(listState, albumListState, videoListState) {
        mapOf(0 to listState, 1 to albumListState, 2 to videoListState)
    }
    DisposableEffect(tabState, pagerStatesMap) {
        tabState.pagerListStates = pagerStatesMap
        tabState.usingTabbedLayout = true
        onDispose {
            if (tabState.pagerListStates === pagerStatesMap) {
                tabState.usingTabbedLayout = false
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        tabState.selectedTabIndex = pagerState.currentPage
    }

    // showRefresh 只给用户主动刷新用：进入页面时的静默加载不驱动下拉动画。
    suspend fun loadStatuses(reset: Boolean, force: Boolean = false, showRefresh: Boolean = false) {
        val target = tabState.resolvedUid ?: return
        if (tabState.loading) return
        if (!reset && tabState.sinceId.isNullOrBlank()) {
            tabState.end = true
            return
        }
        tabState.loading = true
        if (reset && showRefresh) tabState.refreshing = true
        try {
            val page = App.repo.userStatuses(target, if (reset) null else tabState.sinceId, force = force)
            if (reset) {
                tabState.items.clear()
                tabState.end = false
            }
            val known = tabState.items.mapTo(HashSet()) { it.id }
            var added = 0
            page.items.forEach {
                if (known.add(it.id)) {
                    tabState.items.add(it)
                    added++
                }
            }
            tabState.sinceId = page.sinceId
            if (page.items.isEmpty() || page.sinceId.isNullOrBlank() || (!reset && added == 0)) {
                tabState.end = true
            }
            tabState.error = null
            tabState.loadedUid = target
        } catch (t: Throwable) {
            tabState.error = t.message ?: "加载失败"
        } finally {
            tabState.loading = false
            tabState.refreshing = false
        }
    }

    SyncDeletedStatuses(tabState.items)
    LaunchedEffect(uid, tabState) {
        val target = uid ?: runCatching { App.repo.ownUid() }.getOrNull()
        if (target.isNullOrBlank()) {
            tabState.error = "未登录"
            return@LaunchedEffect
        }
        if (tabState.loadedUid != null && tabState.loadedUid != target) {
            tabState.headerCollapsePx = 0f
        }
        tabState.resolvedUid = target
        if (tabState.loadedUid == target && tabState.user != null) {
            return@LaunchedEffect
        }
        tabState.user = runCatching { App.repo.profile(target) }.getOrNull()
        loadStatuses(true, force = false)
    }

    // 顶栏菜单要用头像和昵称，资料加载完就往上同步一份。
    LaunchedEffect(tabState.user) { onUser(tabState.user) }
    LaunchedEffect(tabState) {
        App.profileUpdatedEvents.collect { updated ->
            val target = tabState.resolvedUid ?: tabState.user?.id
            if (!target.isNullOrBlank() && updated.id == target) {
                tabState.user = updated
            }
        }
    }

    // 服务端说这个人被设了特别关注、本机又没记过标记，就补一次初值；之后一律以本机为准。
    LaunchedEffect(tabState.user?.id, tabState.user?.specialFollow) {
        val loaded = tabState.user ?: return@LaunchedEffect
        if (loaded.specialFollow && !FollowTags.has(loaded.id)) {
            FollowTags.set(loaded.id, FollowTag.Special)
        }
    }

    // 相册/视频页签按批连续补拉：整段补拉期间保持单一持续的加载状态，避免每翻一页进度条闪灭重建；
    // 单次自动补拉最多连续翻 4 页，避免视频极少的账号无限循环拉取，未满屏时交由用户点按继续。
    suspend fun loadMediaBurst(targetListState: LazyListState, maxPages: Int = 4) {
        if (autoFilling || tabState.end) return
        autoFilling = true
        autoFillExhausted = false
        try {
            var fetched = 0
            while (fetched < maxPages && !tabState.end && tabState.error == null) {
                val info = targetListState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = info.totalItemsCount
                if (fetched > 0 && total > 0 && last < total - 2) break
                loadStatuses(reset = false)
                fetched++
                withFrameNanos { }
            }
            val info = targetListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (!tabState.end && tabState.error == null && total > 0 && last >= total - 2) {
                autoFillExhausted = true
            }
        } finally {
            autoFilling = false
        }
    }

    val activeListState = when (selectedTab) {
        ProfileContentTab.Posts -> listState
        ProfileContentTab.Album -> albumListState
        ProfileContentTab.Videos -> videoListState
    }

    LaunchedEffect(activeListState, selectedTab, tabState.items.isNotEmpty()) {
        snapshotFlow {
            val info = activeListState.layoutInfo
            Triple(
                info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                info.totalItemsCount,
                tabState.end,
            )
        }.collect { (last, total, ended) ->
            if (total > 0 && last >= total - 2 && tabState.items.isNotEmpty() && !ended) {
                if (selectedTab == ProfileContentTab.Posts) {
                    if (!tabState.loading) loadStatuses(false)
                } else if (!autoFillExhausted && !autoFilling) {
                    loadMediaBurst(activeListState)
                }
            }
        }
    }

    // 下拉拉长封面时的实时高度需要扣掉，避免折叠上限被拉长后的临时高度污染。
    val effectiveBaseHeaderHeightPx = (fullHeaderHeightPx - pullState.dragOffset.roundToInt()).coerceAtLeast(0)
    val maxCollapsePx = (effectiveBaseHeaderHeightPx - topInsetPx).coerceAtLeast(0f)
    LaunchedEffect(maxCollapsePx) {
        if (tabState.headerCollapsePx > maxCollapsePx) {
            tabState.headerCollapsePx = maxCollapsePx
        }
    }

    val headerCollapseConnection = remember(tabState, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || pullState.dragOffset > 0.5f) return Offset.Zero
                val (nextCollapse, consumedY) = ProfileHeaderCollapseMath.collapseDelta(
                    currentCollapsePx = tabState.headerCollapsePx,
                    maxCollapsePx = maxCollapsePx,
                    deltaY = available.y,
                )
                tabState.headerCollapsePx = nextCollapse
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val (nextCollapse, consumedY) = ProfileHeaderCollapseMath.expandDelta(
                    currentCollapsePx = tabState.headerCollapsePx,
                    deltaY = available.y,
                )
                tabState.headerCollapsePx = nextCollapse
                return Offset(0f, consumedY)
            }
        }
    }

    FullPagePullToRefresh(
        isRefreshing = tabState.refreshing,
        onRefresh = {
            scope.launch {
                autoFillExhausted = false
                tabState.resolvedUid?.let { tabState.user = runCatching { App.repo.profile(it, force = true) }.getOrNull() }
                loadStatuses(true, force = true, showRefresh = true)
            }
        },
        modifier = modifier,
        state = pullState,
        contentPadding = PaddingValues(top = topInset),
        // 下拉时不让整页往下走，而是把封面往下拉长；自带的提示会被封面盖住，改由封面自己显示。
        colors = fullPagePullToRefreshColors(
            backgroundColor = pageContainerColor(),
            textColor = Color.Transparent,
            indicatorColor = Color.Transparent,
        ),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 先反向抵消 FullPagePullToRefresh 的下移，再套 overScrollVertical() 的 clipToBounds()，
                // 避免下拉时把封面顶部裁掉只剩固定高度的窄条。
                .offset { IntOffset(0, -pullState.dragOffset.roundToInt()) }
                .nestedScroll(headerCollapseConnection)
                .overScrollVertical(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        fullHeaderHeightPx = placeable.height
                        val dragPx = pullState.dragOffset.roundToInt()
                        val minVisible = (topInsetPx + dragPx).roundToInt().coerceAtLeast(0)
                        val collapsed = tabState.headerCollapsePx.roundToInt()
                        val visibleHeight = (placeable.height - collapsed).coerceIn(minVisible, placeable.height)
                        val placeY = -(placeable.height - visibleHeight)
                        layout(placeable.width, visibleHeight) {
                            placeable.placeRelative(0, placeY)
                        }
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val currentUser = tabState.user
                    val tag = FollowTags[currentUser?.id ?: uid]
                    val targetId = currentUser?.id ?: tabState.resolvedUid
                    val isSelf = !App.repo.guest && !targetId.isNullOrBlank() && targetId == App.repo.session?.uid
                    val currentFollowing = currentUser?.let { App.repo.isFollowingUser(it.id, it.following) } == true
                    ProfileHeader(
                        user = currentUser?.copy(following = currentFollowing),
                        tag = tag,
                        topInset = topInset,
                        pullState = pullState,
                        editableProfile = isSelf,
                        onActiveCover = onActiveCover,
                        onCover = { pics, i -> nav.push(Route.Images(pics, i)) },
                        onAvatar = { avatarUrl ->
                            nav.push(
                                Route.Images(
                                    pics = listOf(Pic(thumb = avatarUrl, large = avatarUrl)),
                                    index = 0,
                                    editableAvatar = isSelf,
                                ),
                            )
                        },
                        onRename = if (isSelf) {
                            { newName ->
                                runCatching {
                                    App.repo.updateProfileBasic(
                                        screenName = newName,
                                        membership = currentUser?.membership ?: Membership.None,
                                    )
                                }
                            }
                        } else null,
                        onStatusesClick = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onRelations = { kind ->
                            val target = currentUser?.id ?: tabState.resolvedUid
                            if (!target.isNullOrBlank()) nav.push(Route.Relations(target, kind))
                        },
                        followLabel = when {
                            App.repo.guest -> if (tag == FollowTag.Local) "已本地关注" else "本地关注"
                            currentFollowing -> "已关注"
                            else -> "关注"
                        },
                        onFollow = currentUser?.let { target ->
                            if (App.repo.guest) {
                                // 纯本地模式没有账号可关注，按钮直接切本机的「本地关注」标记。
                                {
                                    val on = FollowTags[target.id] == FollowTag.Local
                                    FollowTags.set(target.id, if (on) null else FollowTag.Local)
                                    App.notifyFollowChanged()
                                    App.toast(if (on) "已取消本地关注" else "已加入本地关注")
                                }
                            } else if (target.id == App.repo.session?.uid) {
                                null
                            } else {
                                {
                                    if (!App.repo.isFollowInFlight(target.id)) {
                                        val targetFollow = !currentFollowing
                                        scope.launch {
                                            runCatching { App.repo.follow(target.id, targetFollow) }
                                                .onSuccess { applied ->
                                                    if (applied) {
                                                        tabState.user = target.copy(
                                                            following = targetFollow,
                                                            followers = (target.followers +
                                                                if (targetFollow) 1 else -1).coerceAtLeast(0),
                                                        )
                                                        App.notifyFollowChanged()
                                                        App.toast(if (targetFollow) "已关注" else "已取消关注")
                                                    }
                                                }
                                                .onFailure { App.toast(it.message ?: "操作失败") }
                                        }
                                    }
                                }
                            }
                        },
                    )
                    val mutedUid = tabState.user?.id ?: tabState.resolvedUid ?: uid.orEmpty()
                    val isProfileSelf = !App.repo.guest && mutedUid.isNotBlank() && mutedUid == App.repo.session?.uid
                    val activeMuteMode = if (!isProfileSelf) ContentFilters.muteMode(mutedUid) else null
                    if (activeMuteMode != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp),
                            cornerRadius = CardDefaults.GroupCornerRadius,
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            weight = CardDefaults.GroupWeight,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (activeMuteMode == MuteUserMode.All) {
                                        "已本地静音该用户（主页内容仍可见）"
                                    } else {
                                        "已屏蔽该用户的转发（主页内容仍可见）"
                                    },
                                    fontSize = 13.sp,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "解除",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = COUITheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            ContentFilters.setMutedUser(mutedUid, "", null)
                                            App.toast("已取消本地屏蔽")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 胶囊标签栏吸顶保留在头部下缘，点击或左右滑下方列表同步联动切换。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageContainerColor())
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp),
            ) {
                TabRowWithContour(
                    tabs = tabs.map { it.label },
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex),
                    onTabSelected = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_content_tabs"),
                    height = TabRowDefaults.TabRowWithContourTinyHeight,
                    cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
                    contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
                    segmentPadding = 6.dp,
                    backdrop = null,
                )
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val pageTab = tabs[page]
                val pageListState = when (page) {
                    0 -> listState
                    1 -> albumListState
                    else -> videoListState
                }
                BindScrollToTop(pageListState, enabled = pagerState.settledPage == page)
                ProfileMediaPagerTab(
                    nav = nav,
                    tab = pageTab,
                    tabState = tabState,
                    listState = pageListState,
                    extraBottom = extraBottom,
                    isLoadingMore = tabState.loading || autoFilling,
                    autoFillExhausted = autoFillExhausted,
                    onRefreshAll = {
                        scope.launch { loadStatuses(true, force = true, showRefresh = true) }
                    },
                    onLoadMediaBurst = {
                        scope.launch { loadMediaBurst(pageListState) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileMediaPagerTab(
    nav: NavState,
    tab: ProfileContentTab,
    tabState: ProfileTabState,
    listState: LazyListState,
    extraBottom: Dp,
    isLoadingMore: Boolean,
    autoFillExhausted: Boolean,
    onRefreshAll: () -> Unit,
    onLoadMediaBurst: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wallItems = if (tab == ProfileContentTab.Posts) {
        emptyList()
    } else {
        extractMediaWallItems(
            statuses = tabState.items,
            includeRetweeted = false,
            filter = if (tab == ProfileContentTab.Videos) MediaWallFilter.Videos else MediaWallFilter.All,
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            top = if (tab == ProfileContentTab.Posts) 2.dp else 4.dp,
            bottom = 16.dp + navBottomPadding() + extraBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(if (tab == ProfileContentTab.Posts) 8.dp else 3.dp),
    ) {
        if (tabState.items.isEmpty()) {
            item(key = "profile_empty_state_${tab.name}") {
                when {
                    isLoadingMore || tabState.refreshing -> LoadingState()
                    tabState.error != null -> ErrorState(tabState.error!!, onRetry = onRefreshAll)
                    else -> EmptyState("还没有微博")
                }
            }
        } else if (tab != ProfileContentTab.Posts) {
            if (wallItems.isEmpty() && !isLoadingMore) {
                item(key = "profile_wall_empty_${tab.name}") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            if (tab == ProfileContentTab.Videos) "已加载微博中暂无视频"
                            else "已加载微博中暂无图片或视频",
                        )
                        if (!tabState.end) {
                            TextButton(
                                text = "加载更多微博",
                                onClick = onLoadMediaBurst,
                            )
                        }
                    }
                }
            } else if (wallItems.isNotEmpty()) {
                mediaAlbumGridItems(
                    items = wallItems,
                    horizontalPadding = 12.dp,
                    topOuterCornerRadius = CardDefaults.GroupCornerRadius,
                    topOuterWeight = CardDefaults.GroupWeight,
                    onPhotoClick = { pics, idx -> nav.push(Route.Images(pics, idx)) },
                    onVideoClick = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                    onOpenPost = { nav.push(Route.Detail(it.id, it.mid)) },
                )
            }
        } else {
            itemsIndexed(tabState.items, key = { _, s -> s.id }) { _, status ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                val bottomBar = LocalBottomBarBounds.current
                SideEffect {
                    origin.listState = listState
                    origin.rowKey = status.id
                    origin.originKey = "card:${status.id}"
                    origin.topBar = topBar
                    origin.bottomBar = bottomBar
                }
                StatusCard(
                    status = status,
                    modifier = Modifier.padding(horizontal = 12.dp).expandOrigin(origin),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onUser = { nav.push(Route.Profile(it.id)) },
                    onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                    onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                    onLike = {
                        scope.launch {
                            val updated = App.toggleStatusLike(status) ?: return@launch
                            val currentIdx = tabState.items.indexOfFirst { it.id == status.id }
                            if (currentIdx >= 0) tabState.items[currentIdx] = updated
                        }
                    },
                    onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onOpenQuoted = { quoted, quotedOrigin ->
                        quotedOrigin.attachToRow(listState, status.id, topBar, bottomBar)
                        nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                    },
                )
            }
        }
        if (tabState.items.isNotEmpty()) {
            item(key = "profile_footer_${tab.name}") {
                when {
                    isLoadingMore -> LoadingState()
                    tab != ProfileContentTab.Posts && autoFillExhausted && !tabState.end && wallItems.isNotEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                text = "继续加载更多",
                                onClick = onLoadMediaBurst,
                            )
                        }
                    }
                    tabState.end && (tab == ProfileContentTab.Posts || wallItems.isNotEmpty()) -> Text(
                        if (tab == ProfileContentTab.Posts) "没有更多了" else "长按任意缩略图可查看原微博",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * 「我」页面的一级功能与设置中心形态：保留顶部个人资料头，下方聚合我的微博、收藏、
 * 赞过/收到的赞、发出/收到的评论、本机记录以及跳转各个设置分组二级页的入口。
 */
@Composable
fun ProfileHubContent(
    nav: NavState,
    guest: Boolean,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    state: ProfileTabState = remember { ProfileTabState() },
    onActiveCover: (String?) -> Unit = {},
) {
    BindScrollToTop(listState)
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val scheme = COUITheme.colorScheme

    LaunchedEffect(guest, state) {
        if (guest) {
            onActiveCover(null)
            return@LaunchedEffect
        }
        val target = runCatching { App.repo.ownUid() }.getOrNull()
        if (target.isNullOrBlank()) return@LaunchedEffect
        state.resolvedUid = target
        if (state.user == null || state.loadedUid != target) {
            state.user = runCatching { App.repo.profile(target) }.getOrNull()
            if (state.user != null) state.loadedUid = target
        }
    }
    LaunchedEffect(guest, state) {
        if (guest) return@LaunchedEffect
        App.profileUpdatedEvents.collect { updated ->
            val target = state.resolvedUid ?: state.user?.id
            if (target.isNullOrBlank() || updated.id == target) {
                state.user = updated
            }
        }
    }

    val content: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 先反向抵消 FullPagePullToRefresh 的下移，再套 overScrollVertical() 的 clipToBounds()，
                // 避免下拉时把封面顶部裁掉只剩固定高度的窄条。
                .let {
                    if (guest) it else it.offset { IntOffset(0, -pullState.dragOffset.roundToInt()) }
                }
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = if (guest) topInset + 14.dp else 0.dp,
                bottom = 16.dp + navBottomPadding() + extraBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (guest) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        Text("纯本地模式", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "未登录账号，可通过底部入口、草稿、收藏与设置分组管理本机内容。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { nav.push(Route.SmsLogin()) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth().testTag("local_login"),
                        ) {
                            Text("登录微博账号")
                        }
                    }
                }
            } else {
                item {
                    val currentUser = state.user
                    val targetUid = currentUser?.id ?: state.resolvedUid
                    val isSelf = !guest && !targetUid.isNullOrBlank()
                    ProfileHeader(
                        user = currentUser,
                        tag = null,
                        topInset = topInset,
                        pullState = pullState,
                        editableProfile = isSelf,
                        onActiveCover = onActiveCover,
                        onCover = { pics, i -> nav.push(Route.Images(pics, i)) },
                        onAvatar = { avatarUrl ->
                            nav.push(
                                Route.Images(
                                    pics = listOf(Pic(thumb = avatarUrl, large = avatarUrl)),
                                    index = 0,
                                    editableAvatar = isSelf,
                                ),
                            )
                        },
                        onRename = if (isSelf) {
                            { newName ->
                                runCatching {
                                    App.repo.updateProfileBasic(
                                        screenName = newName,
                                        membership = currentUser?.membership ?: Membership.None,
                                    )
                                }
                            }
                        } else null,
                        onStatusesClick = {
                            if (!targetUid.isNullOrBlank()) nav.push(Route.Profile(targetUid))
                        },
                        onRelations = { kind ->
                            if (!targetUid.isNullOrBlank()) nav.push(Route.Relations(targetUid, kind))
                        },
                        followLabel = "",
                        onFollow = null,
                    )
                }

                item {
                    val currentUser = state.user
                    val targetUid = currentUser?.id ?: state.resolvedUid
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        ArrowPreference(
                            title = "我的微博",
                            startAction = { HubRowIcon(MeIcons.Statuses, MeIconTint.Blue) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Head,
                            modifier = Modifier.testTag("hub_my_statuses"),
                            onClick = {
                                if (!targetUid.isNullOrBlank()) nav.push(Route.Profile(targetUid))
                            },
                        )
                        ArrowPreference(
                            title = "我的收藏",
                            startAction = { HubRowIcon(MeIcons.Favorites, MeIconTint.Yellow) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Middle,
                            modifier = Modifier.testTag("hub_favorites"),
                            onClick = { nav.push(Route.Favorites) },
                        )
                        ArrowPreference(
                            title = "我赞过的",
                            startAction = { HubRowIcon(MeIcons.MyLikes, MeIconTint.Red) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Middle,
                            modifier = Modifier.testTag("hub_my_likes"),
                            onClick = { nav.push(Route.MyLikes) },
                        )
                        ArrowPreference(
                            title = "收到的赞",
                            startAction = { HubRowIcon(MeIcons.ReceivedLikes, MeIconTint.Orange) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Middle,
                            modifier = Modifier.testTag("hub_received_likes"),
                            onClick = { nav.push(Route.Notifications(NotificationTab.Like)) },
                        )
                        ArrowPreference(
                            title = "收到的评论",
                            startAction = { HubRowIcon(MeIcons.ReceivedComments, MeIconTint.Green) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Middle,
                            modifier = Modifier.testTag("hub_received_comments"),
                            onClick = { nav.push(Route.Notifications(NotificationTab.Comment)) },
                        )
                        ArrowPreference(
                            title = "发出的评论",
                            startAction = { HubRowIcon(MeIcons.SentComments, MeIconTint.Blue) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Tail,
                            modifier = Modifier.testTag("hub_sent_comments"),
                            onClick = { nav.push(Route.Notifications(NotificationTab.SentComment)) },
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    if (guest) {
                        ArrowPreference(
                            title = "本地收藏",
                            startAction = { HubRowIcon(MeIcons.Favorites, MeIconTint.Yellow) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Head,
                            modifier = Modifier.testTag("local_favorites"),
                            onClick = { nav.push(Route.Favorites) },
                        )
                    }
                    ArrowPreference(
                        title = "草稿箱",
                        startAction = { HubRowIcon(MeIcons.Drafts, MeIconTint.Orange) },
                        centerStartAction = true,
                        cardListPosition = if (guest) CardListPosition.Middle else CardListPosition.Head,
                        modifier = Modifier.testTag("hub_drafts"),
                        onClick = { nav.push(Route.Drafts) },
                    )
                    ArrowPreference(
                        title = "浏览历史",
                        startAction = { HubRowIcon(MeIcons.History, MeIconTint.Blue) },
                        centerStartAction = true,
                        cardListPosition = CardListPosition.Middle,
                        modifier = Modifier.testTag("hub_history"),
                        onClick = { nav.push(Route.History) },
                    )
                    val readLaterCount = ReadLater.all().size
                    val readLaterUnread = ReadLater.unreadCount()
                    ArrowPreference(
                        title = "稍后再看",
                        summary = when {
                            readLaterUnread > 0 -> "$readLaterUnread 条未读"
                            readLaterCount > 0 -> "$readLaterCount 条离线快照"
                            else -> null
                        },
                        startAction = { HubRowIcon(MeIcons.ReadLater, MeIconTint.Blue) },
                        centerStartAction = true,
                        cardListPosition = CardListPosition.Middle,
                        modifier = Modifier.testTag("hub_read_later"),
                        onClick = { nav.push(Route.ReadLater) },
                    )
                    ArrowPreference(
                        title = "话题 / 超话",
                        startAction = { HubRowIcon(MeIcons.Topic, MeIconTint.Yellow) },
                        centerStartAction = true,
                        cardListPosition = if (guest) CardListPosition.Tail else CardListPosition.Middle,
                        modifier = Modifier.testTag("hub_topic"),
                        onClick = { nav.push(Route.Topic()) },
                    )
                    if (!guest) {
                        ArrowPreference(
                            title = "附近",
                            startAction = { HubRowIcon(MeIcons.Nearby, MeIconTint.Green) },
                            centerStartAction = true,
                            cardListPosition = CardListPosition.Tail,
                            modifier = Modifier.testTag("hub_nearby"),
                            onClick = { nav.push(Route.Nearby) },
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    val groups = SettingsGroup.entries
                    groups.forEachIndexed { index, group ->
                        val pos = when (index) {
                            0 -> CardListPosition.Head
                            groups.lastIndex -> CardListPosition.Tail
                            else -> CardListPosition.Middle
                        }
                        ArrowPreference(
                            title = group.title,
                            startAction = { HubRowIcon(group.hubIcon(), group.hubTint()) },
                            centerStartAction = true,
                            cardListPosition = pos,
                            modifier = Modifier.testTag("hub_settings_${group.name.lowercase()}"),
                            onClick = { nav.push(Route.SettingsDetail(group)) },
                        )
                    }
                }
            }
        }
    }

    if (guest) {
        Box(modifier) { content() }
    } else {
        FullPagePullToRefresh(
            isRefreshing = state.refreshing,
            onRefresh = {
                scope.launch {
                    state.refreshing = true
                    try {
                        val target = state.resolvedUid ?: runCatching { App.repo.ownUid() }.getOrNull()
                        if (!target.isNullOrBlank()) {
                            state.resolvedUid = target
                            state.user = runCatching { App.repo.profile(target, force = true) }.getOrNull()
                            state.loadedUid = target
                        }
                    } finally {
                        state.refreshing = false
                    }
                }
            },
            modifier = modifier,
            state = pullState,
            contentPadding = PaddingValues(top = topInset),
            colors = fullPagePullToRefreshColors(
                backgroundColor = pageContainerColor(),
                textColor = Color.Transparent,
                indicatorColor = Color.Transparent,
            ),
            refreshTexts = FullPageRefreshTexts,
            completeHoldDurationMillis = FullPageRefreshHoldMillis,
        ) {
            content()
        }
    }
}

@Composable
private fun HubRowIcon(imageVector: ImageVector, colorfulTint: MeIconTint) {
    val scheme = COUITheme.colorScheme
    val tint = when (App.settings.meIconStyle) {
        MeIconStyle.Monochrome -> scheme.onBackground
        MeIconStyle.Colorful -> colorfulTint.resolve(isDarkTheme())
        MeIconStyle.Accent -> scheme.primary
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = tint,
    )
}

internal fun SettingsGroup.hubIcon(): ImageVector = when (this) {
    SettingsGroup.Display -> MeIcons.DisplaySettings
    SettingsGroup.Feed -> MeIcons.FeedSettings
    SettingsGroup.AccountAndContent -> MeIcons.AccountSettings
    SettingsGroup.Update -> MeIcons.UpdateSettings
    SettingsGroup.Other -> MeIcons.OtherSettings
}

internal fun SettingsGroup.hubTint(): MeIconTint = when (this) {
    SettingsGroup.Display -> MeIconTint.Orange
    SettingsGroup.Feed -> MeIconTint.Green
    SettingsGroup.AccountAndContent -> MeIconTint.Blue
    SettingsGroup.Update -> MeIconTint.Neutral
    SettingsGroup.Other -> MeIconTint.Neutral
}

private val ProfileAvatarRing = 68.dp

/** 封面下沿那道分隔线的粗细。 */
private val CoverDivider = 0.5.dp

/**
 * 关注按钮：顶栏返回键 / 三点键那颗 [HighlightedIconButton] 的材质拉成胶囊 —— 同样的半透明
 * 径向填充、材质描边和按下回弹缩放，只是形状换成能放下文字的胶囊。
 */
@Composable
internal fun FollowCapsule(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = HighlightedIconButtonDefaults.PressSpring,
        label = "followPress",
    )
    val brighten = HighlightedIconButtonDefaults.PressedFillBrighten * press
    val squircleEnabled = isSquircleEnabled()
    val capsuleShape = remember(squircleEnabled) {
        CapsuleOutlineShape(squircleEnabled = squircleEnabled)
    }
    var isWideCapsule by remember { mutableStateOf(true) }
    val capsuleCornerWeight = if (squircleEnabled && isWideCapsule) {
        MaterialShadowEdgeDefaults.CornerWeight
    } else {
        MaterialShadowEdgeDefaults.CapsuleCornerWeight
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .onSizeChanged { isWideCapsule = it.width > it.height }
            .graphicsLayer {
                val scale = if (enabled) 1f - (1f - HighlightedIconButtonDefaults.PressedScale) * press else 1f
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.55f
            }
            .clip(capsuleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to lerp(HighlightedIconButtonDefaults.fillCenter(isDark), Color.White, brighten)
                                .copy(alpha = HighlightedIconButtonDefaults.FillAlpha),
                            HighlightedIconButtonDefaults.FillEdgeStop to lerp(
                                HighlightedIconButtonDefaults.fillEdge(isDark),
                                Color.White,
                                brighten,
                            ).copy(alpha = HighlightedIconButtonDefaults.FillAlpha),
                        ),
                        center = center,
                        radius = size.maxDimension / 2f,
                    ),
                )
            }
            .materialShadowEdge(
                stroke = HighlightedIconButtonDefaults.stroke(isDark),
                cornerWeight = capsuleCornerWeight,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = lerp(scheme.onBackground, Color.White, HighlightedIconButtonDefaults.PressedContentBrighten * press),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileHeader(
    user: User?,
    onFollow: (() -> Unit)?,
    followLabel: String,
    pullState: FullPagePullToRefreshState,
    tag: FollowTag? = null,
    /** 顶栏（含状态栏）的高度：封面顶到屏幕最上沿，多出这一截藏在顶栏玻璃下面。 */
    topInset: Dp = 0.dp,
    editableProfile: Boolean = false,
    onActiveCover: (String?) -> Unit = {},
    onCover: (List<Pic>, Int) -> Unit = { _, _ -> },
    onAvatar: ((String) -> Unit)? = null,
    onRename: (suspend (String) -> Result<User>)? = null,
    onStatusesClick: (() -> Unit)? = null,
    onRelations: (RelationKind) -> Unit = {},
) {
    val scheme = COUITheme.colorScheme
    val covers = user?.covers.orEmpty()
    val hasCover = covers.isNotEmpty()
    // 头像和昵称压在封面上；有封面图时整体压暗一层，文字统一用白色保证看得清。
    val onCoverColor = if (hasCover) Color.White else scheme.onSurface
    val pagerState = rememberPagerState { covers.size }
    LaunchedEffect(covers, pagerState.currentPage) {
        onActiveCover(covers.getOrNull(pagerState.currentPage))
    }
    // 封面在列表里只露中间一条，真实宽高比要等图片解码完才知道。记下来交给看图页，
    // 点开时才能从这一条长出来，而不是从整张图的居中位置凭空出现。
    val coverAspects = remember(covers) { mutableStateMapOf<Int, Float>() }
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 顶栏下面露出的封面只截中间一条，高度取头像的 1.5 倍，点开再看整张；
                // 往上再多出顶栏那一截垫在玻璃底下；下拉多少就往下拉长多少，
                // 封面是居中裁切，拉长就是把上下被裁掉的部分逐渐露出来。高度在布局阶段读，不逐帧重组。
                .layout { measurable, constraints ->
                    val height = (topInset + ProfileAvatarRing * 1.5f).roundToPx() +
                        pullState.dragOffset.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            // 封面下沿是一条直线，再压一道分隔线把边界收实：封面很浅或白底时不至于像没铺满。
            // 上沿在顶栏玻璃底下，由顶栏的渐变接住。
            val dividerColor = scheme.dividerLine
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val stroke = CoverDivider.toPx()
                        drawLine(
                            color = dividerColor,
                            start = Offset(0f, size.height - stroke / 2),
                            end = Offset(size.width, size.height - stroke / 2),
                            strokeWidth = stroke,
                        )
                    }
                    .background(
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer))
                    ),
            ) {
            if (hasCover) {
                val origins = LocalImageOrigins.current
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("profile_cover"),
                    userScrollEnabled = covers.size > 1,
                ) { page ->
                    AsyncImage(
                        model = covers[page],
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            origins.putCacheKey(covers[page], state.result.memoryCacheKey)
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) {
                                coverAspects[page] = size.width / size.height
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .imageOrigin(covers[page], 0.dp, aspect = coverAspects[page], model = covers[page])
                            .clickable {
                                onCover(covers.map { Pic(thumb = it, large = it) }, page)
                            },
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            }
            if (hasCover) {
                if (covers.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(covers.size) { i ->
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color.White.copy(
                                            alpha = if (i == pagerState.currentPage) 0.95f else 0.45f
                                        )
                                    )
                            )
                        }
                    }
                }
            }
            val hint = when (pullState.refreshState) {
                FullPageRefreshState.Pulling -> FullPageRefreshTexts.pulling
                FullPageRefreshState.ThresholdReached -> FullPageRefreshTexts.thresholdReached
                FullPageRefreshState.Refreshing -> FullPageRefreshTexts.refreshing
                FullPageRefreshState.RefreshComplete -> FullPageRefreshTexts.complete
                else -> ""
            }
            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = onCoverColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + 12.dp)
                        .graphicsLayer { alpha = pullState.pullProgress },
                )
            }
            // 头像一行贴着封面底部：不拉时上下各留 17dp 正好居中，拉长时停在原位，上方露出封面。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = (ProfileAvatarRing * 0.5f) / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(ProfileAvatarRing)) {
                    val avatarUrl = user?.avatar.orEmpty()
                    val canOpenAvatar = avatarUrl.isNotBlank() && onAvatar != null
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(2.dp)
                            .then(
                                if (canOpenAvatar) {
                                    Modifier
                                        .imageOrigin(avatarUrl, 32.dp, aspect = 1f, model = avatarUrl)
                                        .clickable(role = Role.Image) { onAvatar.invoke(avatarUrl) }
                                        .testTag("profile_avatar")
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Avatar(user?.avatar, 64.dp)
                    }
                    if (user != null) {
                        VerifiedBadge(
                            kind = user.verifiedKind,
                            size = 20.dp,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                val renameScope = rememberCoroutineScope()
                val keyboardController = LocalSoftwareKeyboardController.current
                var editingName by remember(user?.id) { mutableStateOf(false) }
                var originalName by remember(user?.id) { mutableStateOf("") }
                var nameDraft by remember(user?.id) { mutableStateOf(TextFieldValue("")) }
                var savingName by remember(user?.id) { mutableStateOf(false) }
                var pendingRenameConfirm by remember(user?.id) { mutableStateOf<String?>(null) }
                val nameFocus = remember { FocusRequester() }
                val editProgress by animateFloatAsState(
                    targetValue = if (editingName) 1f else 0f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "profileNameEdit",
                )
                val nameFontSize = (20f - 1.5f * editProgress).sp
                LaunchedEffect(editingName) {
                    if (editingName) {
                        nameFocus.requestFocus()
                        runCatching { App.repo.syncServerRenameHint() }
                    }
                }
                PlatformBackHandler(enabled = editingName && !savingName) {
                    nameDraft = TextFieldValue(originalName, TextRange(originalName.length))
                    editingName = false
                }
                fun commitRename() {
                    val trimmed = nameDraft.text.trim()
                    if (trimmed.isEmpty()) {
                        App.toast("昵称不能为空")
                        return
                    }
                    if (trimmed == originalName) {
                        editingName = false
                        return
                    }
                    if (onRename == null || savingName) return
                    keyboardController?.hide()
                    pendingRenameConfirm = trimmed
                }
                pendingRenameConfirm?.let { targetName ->
                    ConfirmDialog(
                        title = "确认修改昵称",
                        message = "确定将微博昵称修改为「$targetName」吗？",
                        confirmText = "确认修改",
                        onDismiss = { pendingRenameConfirm = null },
                        onConfirm = {
                            pendingRenameConfirm = null
                            val handler = onRename ?: return@ConfirmDialog
                            if (savingName) return@ConfirmDialog
                            savingName = true
                            renameScope.launch {
                                handler(targetName)
                                    .onSuccess {
                                        editingName = false
                                        App.toast("昵称已更新")
                                    }
                                    .onFailure {
                                        App.toast(it.message ?: "修改昵称失败")
                                    }
                                savingName = false
                            }
                        },
                    )
                }
                Column(Modifier.weight(1f)) {
                    val membership = user?.membership ?: Membership.None
                    if (tag != null || membership != Membership.None) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (tag != null) {
                                FollowTagBadge(tag)
                            }
                            if (membership != Membership.None) {
                                MemberBadge(membership, rank = user?.mbrank ?: 0)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (editProgress > 0.01f) {
                                    val stroke = 1.5.dp.toPx()
                                    drawLine(
                                        color = onCoverColor.copy(alpha = 0.85f * editProgress),
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width * editProgress, size.height),
                                        strokeWidth = stroke,
                                    )
                                }
                            }
                            .padding(bottom = (3f * editProgress).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editingName) {
                            BasicTextField(
                                value = nameDraft,
                                onValueChange = { next ->
                                    val clean = next.text.replace("\n", "").take(30)
                                    nameDraft = if (clean == next.text) next else next.copy(text = clean)
                                },
                                singleLine = true,
                                enabled = !savingName,
                                textStyle = TextStyle(
                                    color = onCoverColor,
                                    fontSize = nameFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                cursorBrush = SolidColor(onCoverColor),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitRename() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(nameFocus)
                                    .testTag("profile_my_id_input"),
                            )
                        } else {
                            val canInlineRename = editableProfile && user != null && onRename != null
                            val activeRemark = if (!editableProfile) UserRemarks[user?.id] else null
                            val shownProfileName = activeRemark ?: user?.name ?: "加载中…"
                            Text(
                                text = if (editProgress > 0.01f) nameDraft.text else shownProfileName,
                                color = onCoverColor,
                                fontSize = nameFontSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .then(
                                        if (canInlineRename) {
                                            Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val current = user.name
                                                    originalName = current
                                                    nameDraft = TextFieldValue(current, TextRange(current.length))
                                                    editingName = true
                                                }
                                                .testTag("profile_my_id")
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                        AnimatedVisibility(
                            visible = editingName,
                            enter = fadeIn(tween(220)) +
                                scaleIn(
                                    initialScale = 0.72f,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                ) +
                                expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                ),
                            exit = fadeOut(tween(180)) +
                                scaleOut(
                                    targetScale = 0.72f,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                ) +
                                shrinkHorizontally(
                                    shrinkTowards = Alignment.End,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                ),
                        ) {
                            val trimmedDraft = nameDraft.text.trim()
                            val canSave = !savingName && trimmedDraft.isNotEmpty() && trimmedDraft != originalName
                            val saveAlpha by animateFloatAsState(
                                targetValue = if (canSave) 0.95f else 0.35f,
                                animationSpec = tween(durationMillis = 160),
                                label = "profileNameSaveAlpha",
                            )
                            Row(
                                modifier = Modifier.padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !savingName, role = Role.Button) {
                                            nameDraft = TextFieldValue(originalName, TextRange(originalName.length))
                                            editingName = false
                                        }
                                        .testTag("profile_name_restore"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = COUIIcons.Undo,
                                        contentDescription = "撤回",
                                        modifier = Modifier.size(16.dp),
                                        tint = onCoverColor.copy(alpha = 0.9f),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = canSave, role = Role.Button) {
                                            commitRename()
                                        }
                                        .testTag("profile_name_save"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = COUIIcons.Ok,
                                        contentDescription = if (savingName) "保存中" else "保存",
                                        modifier = Modifier.size(16.dp),
                                        tint = onCoverColor.copy(alpha = saveAlpha),
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = editingName,
                        enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(200)),
                        exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = tween(150)),
                    ) {
                        val renameVersion = App.profileRenameVersion
                        val renameUid = user?.id.orEmpty().ifBlank { runCatching { App.repo.session?.uid.orEmpty() }.getOrDefault("") }
                        val renameRecord = remember(renameUid, renameVersion) {
                            runCatching { App.store.profileRenameRecord(renameUid) }.getOrDefault(ProfileRenameRecord())
                        }
                        val renameStatus = remember(renameRecord, membership) {
                            computeProfileRenameStatus(renameRecord, membership)
                        }
                        Text(
                            text = renameStatus.summary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (renameStatus.limited) {
                                if (hasCover) Color(0xFFFFD180) else scheme.error
                            } else {
                                onCoverColor.copy(alpha = 0.78f)
                            },
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("profile_name_hint"),
                        )
                    }
                    val headerRemark = if (!editableProfile) UserRemarks[user?.id] else null
                    if (headerRemark != null && user != null && headerRemark != user.name && user.name.isNotBlank()) {
                        Text(
                            text = "原昵称：${user.name}",
                            fontSize = 11.sp,
                            color = onCoverColor.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (user?.verified == true) {
                        Text(
                            "已认证",
                            fontSize = 11.sp,
                            color = if (hasCover) Color.White.copy(alpha = 0.85f) else scheme.primary,
                        )
                    }
                }
                if (onFollow != null) {
                    Spacer(Modifier.width(8.dp))
                    FollowCapsule(
                        text = followLabel,
                        onClick = onFollow,
                        modifier = Modifier.testTag("follow_button"),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            if (!user?.description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                FoldableIntroText(
                    text = user.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Stat("微博", user?.statuses ?: 0, testTag = "profile_statuses", onClick = onStatusesClick)
                Stat("关注", user?.friends ?: 0, testTag = "profile_following") {
                    onRelations(RelationKind.Following)
                }
                Stat("粉丝", user?.followers ?: 0, testTag = "profile_followers") {
                    onRelations(RelationKind.Followers)
                }
            }
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: Int,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmtCount(value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
    }
}
