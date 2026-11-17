package com.cobo.app.screen

import android.net.Uri
import android.util.Log
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
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
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.WeiboEmojiPanel
import com.cobo.app.ui.components.WeiboRichText
import com.cobo.app.ui.components.navBottomPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.theme.COUITheme
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

/** 底栏单行高度：输入胶囊、表情/图片按钮、发送按钮共用，保证三者底边齐平。 */
private val ComposerHeight = 44.dp

/** 输入换行后每多一行长高多少。发送按钮不跟着长，仍按 [ComposerHeight] 下对齐。 */
private val ComposerLineStep = 22.dp

/** 输入胶囊和发送按钮的圆角，正好是单行高度的一半。 */
private val ComposerShape = RoundedCornerShape(22.dp)

private val WeiboTimeFormat = "EEE MMM dd HH:mm:ss Z yyyy"

@Composable
fun ChatScreen(nav: NavState, uid: String, name: String, gid: String = "") {
    val isGroup = gid.isNotBlank()
    val scheme = COUITheme.colorScheme
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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
    // 输入框当前排了几行：单行 44dp，之后每多一行长高一点，最多 4 行。
    var inputLines by remember { mutableStateOf(1) }
    val keyboard = LocalSoftwareKeyboardController.current

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

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffectsEnabled(), backdrop)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                            reverseLayout = true,
                            contentPadding = PaddingValues(
                                top = topBarHeight + 10.dp,
                                bottom = 10.dp,
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

                if (dead) return@Column
                val imeBottom = WindowInsets.ime.getBottom(density)
                val navPad = if (imeBottom > 0) 0.dp else navBottomPadding()
                val canSend = draft.text.isNotBlank() && !sending
                // 单行时和发送按钮一样高；换行后胶囊长高，发送按钮原地不动，整体仍然下对齐。
                val inputHeight = ComposerHeight + ComposerLineStep * (inputLines - 1).coerceIn(0, 3)
                // 一整条胶囊：输入框占左边，表情 / 图片按钮贴在输入框右边，发送按钮单独一颗。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + navPad),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(inputHeight)
                            .clip(ComposerShape)
                            .background(scheme.surfaceContainer),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            // 显式高度顶掉 COUI 的 62dp 最小高度，单行压成搜索框那种胶囊；
                            // 换行后按行数长高，超长内容在框里自己滚。
                            modifier = Modifier.weight(1f).height(inputHeight).testTag("chat_input"),
                            label = if (isGroup) "发送消息…" else "发送私信…",
                            backgroundMode = TextFieldMode.None,
                            insideMargin = DpSize(14.dp, 4.dp),
                            maxLines = 4,
                            onTextLayout = { inputLines = it.lineCount },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                        )
                        ComposerIconButton(
                            icon = CoboIcons.Emoji,
                            contentDescription = "表情",
                            tint = scheme.onSurfaceSecondary,
                            modifier = Modifier.testTag("chat_emoji"),
                            onClick = {
                                keyboard?.hide()
                                emojiOpen = true
                            },
                        )
                        ComposerIconButton(
                            icon = COUIIcons.Image,
                            contentDescription = "发送图片",
                            tint = scheme.onSurfaceSecondary,
                            modifier = Modifier.testTag("chat_pick"),
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(ComposerHeight)
                            .clip(ComposerShape)
                            .background(if (canSend) scheme.primary else scheme.surfaceContainerHigh)
                            .clickable(enabled = canSend) { send() }
                            .padding(horizontal = 16.dp)
                            .testTag("chat_send"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "发送",
                            fontSize = 14.sp,
                            color = if (canSend) scheme.onPrimary else scheme.onSurfaceSecondary,
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
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = { 1f },
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
        title = "表情",
        onDismissRequest = { emojiOpen = false },
    ) {
        WeiboEmojiPanel(
            modifier = Modifier.height(260.dp),
            // 私信里表情就是微博那套 [微笑] 标记，和发博输入框一致，由服务端渲染成图。
            onPick = { emoji ->
                insertAtSelection(emoji.phrase)
                emojiOpen = false
            },
        )
    }
}

/** 胶囊里的方形点击区：表情、图片共用，保证两个图标的触摸范围和基线一致。 */
@Composable
private fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ComposerHeight)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp),
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
