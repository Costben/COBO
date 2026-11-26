package com.cobo.app.screen

import android.net.Uri
import android.util.Log
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.ApiException
import com.cobo.app.data.ChatMessage
import com.cobo.app.data.chatPlainText
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.CompactSendButton
import com.cobo.app.ui.components.ComposerEmoteTextField
import com.cobo.app.ui.components.ComposerToolButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.WeiboEmojiPanel
import com.cobo.app.ui.components.WeiboRichText
import com.cobo.app.ui.components.inputCapsuleGlass
import com.cobo.app.ui.components.navBottomPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一屏取多少条。和 H5 保持一致，翻页手感接近。 */
private const val ChatPageSize = 20

/** 聊天页停留期间的增量轮询间隔。 */
private const val ChatPollMillis = 15_000L

/** 两条消息间隔超过这个值就在上面插一条时间。H5 里也是 5 分钟。 */
private const val TimeGapMillis = 5 * 60 * 1000L

/** 底栏胶囊高度：输入框与发送按钮共用，保证两者单行时严格等高。 */
private val ChatComposerHeight = 40.dp

/** 输入框最多长到多高，再多就在框里自己滚。 */
private val ChatComposerMaxHeight = 108.dp

private val WeiboTimeFormat = "EEE MMM dd HH:mm:ss Z yyyy"

@Composable
fun ChatScreen(nav: NavState, uid: String, name: String, gid: String = "") {
    val isGroup = gid.isNotBlank()
    val scheme = COUITheme.colorScheme
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val density = LocalDensity.current

    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    val me = remember { App.repo.session?.uid.orEmpty() }
    // 最新在前，LazyColumn 用 reverseLayout 铺，index 0 落在最下面。
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val users = remember { mutableStateMapOf<String, User>() }

    var loading by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorErrno by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var sending by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    // 底栏实测高度（px）：输入框换行会整体往上长，列表底部要按它让位。
    var composerHeightPx by remember { mutableIntStateOf(0) }
    // 输入框要把 [表情] 就地画成图，先备好「标记 -> 图片地址」。
    var emotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val composerFocus = remember { FocusRequester() }
    val composerInteraction = remember { MutableInteractionSource() }
    val composerFocused by composerInteraction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        emotes = runCatching {
            App.repo.emotions()
                .flatMap { section -> section.items }
                .mapNotNull { emoji -> emoji.url.takeIf { it.isNotBlank() }?.let { emoji.phrase to it } }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    // 点回输入框就收起表情面板，光标和面板不同时抢焦点。
    LaunchedEffect(composerFocused) {
        if (composerFocused) emojiOpen = false
    }

    // 20003：对方账号已注销，聊天记录取不到、消息也发不出去，界面直接收成一句话。
    // 群聊没有这个状态，群里某个成员注销不该把整间群锁死。
    val dead = errorErrno == "20003" && !isGroup
    val peer = users[uid]
    val peerName = if (isGroup) {
        name.ifBlank { "群聊" }
    } else {
        peer?.name?.ifBlank { name } ?: name.ifBlank { "私信" }
    }

    suspend fun load(reset: Boolean, force: Boolean) {
        if (reset) {
            if (loading) return
            loading = true
        } else {
            if (loading || loadingOlder || end || messages.isEmpty()) return
            loadingOlder = true
        }
        error = null
        try {
            val page = App.repo.chat(
                uid = uid,
                gid = gid,
                maxId = if (reset) null else messages.last().id,
                count = ChatPageSize,
                force = force,
            )
            users.putAll(page.users)
            val known = messages.mapTo(HashSet()) { it.id }
            val fresh = page.messages.filter { known.add(it.id) }
            if (reset) messages.clear()
            // 服务端最新在前：首屏直接替换；翻页的整页都比现有更旧，接在尾部。
            messages.addAll(fresh)
            if (page.messages.isEmpty()) end = true
            if (reset && messages.isNotEmpty()) listState.scrollToItem(0)
        } catch (t: Throwable) {
            if (reset) {
                error = t.message ?: "加载失败"
                errorErrno = (t as? ApiException)?.errno
            } else {
                App.toast(t.message ?: "加载失败")
            }
        } finally {
            loading = false
            loadingOlder = false
        }
    }

    LaunchedEffect(uid) { load(reset = true, force = true) }

    // 停在聊天页时增量取新消息；滚动在最底部才自动跟到底。
    LaunchedEffect(uid) {
        while (true) {
            delay(ChatPollMillis)
            val newest = messages.firstOrNull()?.id ?: continue
            // force 必须开：GET 缓存没有 TTL，since_id 不变时 URL 不变，走缓存就永远收不到新消息。
            val page = runCatching {
                App.repo.chat(uid = uid, gid = gid, sinceId = newest, count = ChatPageSize, force = true)
            }.getOrNull() ?: continue
            if (page.users.isNotEmpty()) users.putAll(page.users)
            val known = messages.mapTo(HashSet()) { it.id }
            val fresh = page.messages.filter { known.add(it.id) }
            if (fresh.isEmpty()) continue
            val atBottom = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 8
            messages.addAll(0, fresh)
            if (atBottom) listState.animateScrollToItem(0)
        }
    }

    // 滑到顶部（reverseLayout 里就是 index 最大的一端）继续往前翻。
    LaunchedEffect(listState, messages.size, end) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= messages.size - 2) load(reset = false, force = false)
            }
    }

    /** 在光标处插入一段文本（表情走这里），光标停在插入内容末尾。 */
    fun insertAtSelection(snippet: String) {
        val current = draft.text
        val start = draft.selection.min.coerceIn(0, current.length)
        val end = draft.selection.max.coerceIn(start, current.length)
        val next = current.substring(0, start) + snippet + current.substring(end)
        draft = TextFieldValue(next, TextRange((start + snippet.length).coerceIn(0, next.length)))
    }

    fun send() {
        val content = draft.text.trim()
        if (content.isEmpty() || sending) return
        sending = true
        draft = TextFieldValue("")
        val tempId = "local-${System.currentTimeMillis()}"
        messages.add(
            0,
            ChatMessage(
                id = tempId,
                text = toChatHtml(content),
                senderId = me,
                recipientId = if (isGroup) gid else uid,
                createdAt = SimpleDateFormat(WeiboTimeFormat, Locale.US).format(Date()),
                pending = true,
            ),
        )
        scope.launch {
            listState.animateScrollToItem(0)
            try {
                val real = App.repo.sendChat(uid, content, gid)
                val index = messages.indexOfFirst { it.id == tempId }
                if (index >= 0) {
                    messages[index] = real ?: messages[index].copy(pending = false)
                }
            } catch (t: Throwable) {
                val index = messages.indexOfFirst { it.id == tempId }
                if (index >= 0) messages[index] = messages[index].copy(pending = false, failed = true)
                App.toast(t.message ?: "发送失败")
            } finally {
                sending = false
            }
        }
    }

    /**
     * 发图：先插一条本地预览气泡（Coil 直接吃 content://），
     * 上传拿到 fids 再调发送接口，成功就把预览换成服务端那条。
     */
    fun sendImage(uri: Uri) {
        if (sending) return
        sending = true
        val tempId = "local-${System.currentTimeMillis()}"
        messages.add(
            0,
            ChatMessage(
                id = tempId,
                text = "",
                senderId = me,
                recipientId = if (isGroup) gid else uid,
                createdAt = SimpleDateFormat(WeiboTimeFormat, Locale.US).format(Date()),
                mediaType = 1,
                imageUrl = uri.toString(),
                thumbUrl = uri.toString(),
                pending = true,
            ),
        )
        scope.launch {
            listState.animateScrollToItem(0)
            try {
                val fids = App.repo.uploadChatImage(uid, uri, gid)
                val real = App.repo.sendChatImage(uid, fids, gid)
                val index = messages.indexOfFirst { it.id == tempId }
                if (index >= 0) {
                    messages[index] = real ?: messages[index].copy(pending = false)
                }
            } catch (t: Throwable) {
                Log.w("chat", "图片发送失败", t)
                val index = messages.indexOfFirst { it.id == tempId }
                if (index >= 0) messages[index] = messages[index].copy(pending = false, failed = true)
                App.toast(t.message ?: "图片发送失败")
            } finally {
                sending = false
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) sendImage(uri) }

    val blur = blurEffectsEnabled()
    val imeBottom = WindowInsets.ime.getBottom(density)
    // 键盘弹起时输入栏贴着键盘，不用再补导航条高度；收起时才补。
    val navPad = if (imeBottom > 0) 0.dp else navBottomPadding()

    // 底栏浮在列表上面：列表底部空出含底部安全区在内的底栏实测总高度，无论键盘弹起或收起、
    // 长草稿换行顶高时消息都能完整露在底栏上方，不会被盖住。
    val composerHeight = with(density) { composerHeightPx.toDp() }
    val listBottomPadding = if (dead) {
        12.dp + navPad
    } else {
        (composerHeight.takeIf { it > 0.dp } ?: (ChatComposerHeight + 24.dp + navPad)) + 8.dp
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // 只把消息列表录进 backdrop：输入胶囊浮在它上面，磨到的就是消息本身。
                // 录制层必须自带不透明底色——layerBackdrop 不包含父级背景，透明像素模糊后会泛白，
                // 胶囊就会糊出一圈白边。
                Box(Modifier.fillMaxSize().layerBackdropIf(blur, backdrop).background(pageColor)) {
                    when {
                        // 状态页都从顶栏下面开始，否则会被顶栏的毛玻璃糊成一团黑影。
                        messages.isEmpty() && loading -> Box(
                            Modifier.fillMaxSize().padding(top = topBarHeight),
                            contentAlignment = Alignment.Center,
                        ) { LoadingState() }
                        messages.isEmpty() && error != null -> Box(
                            Modifier.fillMaxSize().padding(top = topBarHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 20003 = 对方注销，重试多少次都是同一句「用户不存在」。
                            if (dead) {
                                EmptyState("对方账号已注销，聊天记录和私信都不可用")
                            } else {
                                ErrorState(error!!) { scope.launch { load(reset = true, force = true) } }
                            }
                        }
                        messages.isEmpty() -> Box(
                            Modifier.fillMaxSize().padding(top = topBarHeight),
                            contentAlignment = Alignment.Center,
                        ) { EmptyState("还没有聊过，打个招呼吧") }
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                            reverseLayout = true,
                            contentPadding = PaddingValues(
                                top = topBarHeight + 10.dp,
                                bottom = listBottomPadding,
                            ),
                        ) {
                            itemsIndexed(
                                items = messages,
                                key = { index, item -> "${item.id}#$index" },
                            ) { index, item ->
                                val older = messages.getOrNull(index + 1)
                                Column {
                                    if (showTime(item, older)) {
                                        Text(
                                            chatTime(item.createdAt),
                                            fontSize = 11.sp,
                                            color = scheme.onSurfaceSecondary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                    val sender = users[item.senderId]
                                    ChatBubble(
                                        message = item,
                                        mine = item.senderId == me,
                                        avatar = when {
                                            item.senderId == me -> null
                                            isGroup -> sender?.avatar
                                            else -> peer?.avatar
                                        },
                                        senderName = if (isGroup && item.senderId != me) {
                                            sender?.name.orEmpty()
                                        } else {
                                            null
                                        },
                                        onRetry = {
                                            if (item.isImage) {
                                                val local = item.imageUrl
                                                messages.remove(item)
                                                if (local.isNotBlank()) sendImage(Uri.parse(local))
                                            } else {
                                                draft = TextFieldValue(chatPlainText(item.text))
                                                messages.remove(item)
                                            }
                                        },
                                    )
                                }
                            }
                            if (loadingOlder) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { LoadingState() }
                                }
                            }
                        }
                    }
                }

                if (!dead) {
                    // 底栏照搬 BiliCO 私信页：整条不铺底色，输入胶囊和发送按钮各自悬浮在页面上，
                    // 两者共用同一个高度，单行时严格等高。
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { composerHeightPx = it.height }
                            .padding(bottom = navPad),
                    ) {
                        ChatBottomBar(
                            value = draft,
                            onValueChange = { draft = it },
                            emotes = emotes,
                            label = if (isGroup) "发送消息…" else "发送私信…",
                            onSend = { send() },
                            isSending = sending,
                            emotePanelOpen = emojiOpen,
                            onToggleEmote = {
                                if (emojiOpen) {
                                    emojiOpen = false
                                } else {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    emojiOpen = true
                                }
                            },
                            onPickImage = {
                                if (emojiOpen) emojiOpen = false
                                keyboard?.hide()
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            focusRequester = composerFocus,
                            interactionSource = composerInteraction,
                            backdrop = if (blur) backdrop else null,
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            // 和「消息」页完全同一个材质层：模糊开着是 COUI 渐变玻璃（材质量到顶栏 +24dp 并向下淡出），
            // 关掉是整块实色。SmallTopAppBar 的均匀磨砂会在顶栏下缘留一条硬切线，
            // 这里把它的底色设成透明，只借它的标题 / 图标排版，材质由 AppBarSurface 画。
            PinnedBarDensity {
                val barDensity = LocalDensity.current
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = {
                        if (messages.isEmpty()) {
                            0f
                        } else if (listState.canScrollForward) {
                            1f
                        } else {
                            val info = listState.layoutInfo
                            val topItem = info.visibleItemsInfo.lastOrNull()
                            if (topItem == null) {
                                0f
                            } else {
                                val contentTop = info.viewportEndOffset - info.afterContentPadding
                                val overlap = (topItem.offset + topItem.size - contentTop).coerceAtLeast(0)
                                AppBarBlurOverlayDefaults.scrollAlpha(overlap.toFloat(), barDensity)
                            }
                        }
                    },
                ) {
                    SmallTopAppBar(
                        title = peerName,
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            if (!isGroup) {
                                Avatar(
                                    url = peer?.avatar,
                                    size = 28.dp,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { nav.push(Route.Profile(uid)) }
                                        .testTag("chat_peer_avatar"),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    OverlayBottomSheet(
        show = emojiOpen,
        // 「表情」从大标题挪到顶栏「关闭」右边，省下的 42dp 全给表情网格，
        // 同样的面板高度能多显示一排。
        startAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetCloseCapsule(onClick = { emojiOpen = false })
                Text(
                    "表情",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        },
        onDismissRequest = { emojiOpen = false },
    ) {
        WeiboEmojiPanel(
            // 原本 260dp，加上被大标题占掉的 3 + 27 + 12 = 42dp。
            modifier = Modifier.height(302.dp),
            // 私信里表情就是微博那套 [微笑] 标记，和发博输入框一致，由服务端渲染成图。
            onPick = { emoji ->
                insertAtSelection(emoji.phrase)
                emojiOpen = false
            },
        )
    }
}

/** 私信底栏：一个输入胶囊 + 一个发送胶囊，两者共用 [ChatComposerHeight]，单行时严格等高。 */
@Composable
private fun ChatBottomBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    emotes: Map<String, String>,
    label: String,
    onSend: () -> Unit,
    isSending: Boolean,
    emotePanelOpen: Boolean,
    onToggleEmote: () -> Unit,
    onPickImage: () -> Unit,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    backdrop: Backdrop?,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    var singleLineHeightPx by remember { mutableIntStateOf(0) }
    val singleLineHeightDp = with(density) { singleLineHeightPx.toDp() }
    val syncedButtonHeight = if (singleLineHeightDp > ChatComposerHeight) singleLineHeightDp else ChatComposerHeight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val squircleEnabled = isSquircleEnabled()
        val composerShape = remember(squircleEnabled, syncedButtonHeight) {
            CapsuleOutlineShape(cornerRadius = syncedButtonHeight / 2, squircleEnabled = squircleEnabled)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ChatComposerHeight)
                .onSizeChanged { size ->
                    if (!value.text.contains('\n') && size.height > 0) {
                        val dp = with(density) { size.height.toDp() }
                        if (dp <= ChatComposerHeight + 14.dp) {
                            singleLineHeightPx = size.height
                        }
                    }
                }
                // 和发送按钮同一块材质：有 backdrop 就走 COUI 磨砂（背后消息被高斯磨平再叠混合色），
                // 没有就退回平面胶囊。
                .inputCapsuleGlass(
                    backdrop = backdrop,
                    isDark = isDark,
                    cornerRadius = syncedButtonHeight / 2,
                )
                .clip(composerShape),
        ) {
            ComposerEmoteTextField(
                value = value,
                onValueChange = onValueChange,
                emotes = emotes,
                maxHeight = ChatComposerMaxHeight,
                focusRequester = focusRequester,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .testTag("chat_input"),
                label = label,
                minLines = 1,
                insideMargin = DpSize(14.dp, 8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                compact = true,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerToolButton(
                    active = emotePanelOpen,
                    size = 32.dp,
                    cornerRadius = 16.dp,
                    onClick = onToggleEmote,
                    modifier = Modifier.testTag("chat_emoji"),
                ) {
                    Icon(
                        imageVector = CoboIcons.Emoji,
                        contentDescription = "表情",
                        modifier = Modifier.size(20.dp),
                        tint = if (emotePanelOpen) scheme.primary else scheme.onSurface,
                    )
                }

                Spacer(Modifier.width(2.dp))

                ComposerToolButton(
                    active = false,
                    size = 32.dp,
                    cornerRadius = 16.dp,
                    onClick = onPickImage,
                    modifier = Modifier.testTag("chat_pick"),
                ) {
                    Icon(
                        imageVector = COUIIcons.Photos,
                        contentDescription = "图片",
                        modifier = Modifier.size(20.dp),
                        tint = scheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        CompactSendButton(
            text = if (isSending) "发送中" else "发送",
            onClick = onSend,
            enabled = value.text.isNotBlank() && !isSending,
            height = syncedButtonHeight,
            containerColor = scheme.surfaceContainer,
            isDark = isDark,
            backdrop = backdrop,
            modifier = Modifier.testTag("chat_send"),
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    mine: Boolean,
    avatar: String?,
    senderName: String? = null,
    onRetry: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    if (message.isNotice) {
        Text(
            text = chatPlainText(message.text),
            fontSize = 12.sp,
            color = scheme.onSurfaceSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    val shape = if (mine) {
        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            Avatar(avatar, 34.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && !senderName.isNullOrBlank()) {
                Text(
                    senderName,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 262.dp)
                    .clip(shape)
                    .background(if (mine) scheme.primary else scheme.surfaceContainerHigh)
                    .then(
                        if (message.failed) Modifier.clickable(onClick = onRetry) else Modifier,
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                when {
                    message.isImage -> {
                        // 服务端给了原始宽高，先按比例占好位；加载失败也不会塌成一条细边。
                        val ratio = if (message.width > 0 && message.height > 0) {
                            message.width.toFloat() / message.height
                        } else {
                            4f / 3f
                        }
                        val boxWidth = 180.dp
                        val boxHeight = (boxWidth / ratio).coerceAtMost(220.dp)
                        Box(
                            Modifier
                                .size(boxWidth, boxHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceContainerHigh),
                        ) {
                            AsyncImage(
                                model = message.thumbUrl.ifBlank { message.imageUrl },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    else -> WeiboRichText(
                        html = message.text,
                        color = if (mine) scheme.onPrimary else scheme.onSurface,
                        // 自己的气泡是主色底，链接/表情文字再用主色就看不清了，跟着正文走。
                        linkColor = if (mine) scheme.onPrimary else scheme.primary,
                    )
                }
            }
            if (message.pending) {
                Text(
                    "发送中…",
                    fontSize = 10.sp,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (message.failed) {
                Text(
                    "发送失败，点气泡重发",
                    fontSize = 10.sp,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 纯文本落库前转成微博那套 HTML：`<` 转义，换行变 `<br/>`。 */
private fun toChatHtml(plain: String): String =
    TextUtils.htmlEncode(plain).replace("\n", "<br/>")

private fun parseTime(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        SimpleDateFormat(WeiboTimeFormat, Locale.US).parse(raw)?.time
    }.getOrNull() ?: raw.toLongOrNull()
}

private fun showTime(message: ChatMessage, older: ChatMessage?): Boolean {
    val current = parseTime(message.createdAt) ?: return false
    val previous = older?.let { parseTime(it.createdAt) } ?: return true
    return current - previous > TimeGapMillis
}

private fun chatTime(raw: String?): String {
    val millis = parseTime(raw) ?: return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
