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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.ApiException
import com.cobo.app.data.ChatMessage
import com.cobo.app.data.chatPlainText
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.WeiboRichText
import com.cobo.app.ui.components.navBottomPadding
import androidx.compose.ui.graphics.Color
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
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
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.theme.COUITheme
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

private val WeiboTimeFormat = "EEE MMM dd HH:mm:ss Z yyyy"

@Composable
fun ChatScreen(nav: NavState, uid: String, name: String) {
    val scheme = COUITheme.colorScheme
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        TopAppBarDefaults.SmallTopAppBarCenterHeight

    val me = remember { App.repo.session?.uid.orEmpty() }
    // 最新在前，LazyColumn 用 reverseLayout 铺，index 0 落在最下面。
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val users = remember { mutableStateMapOf<String, User>() }

    var loading by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorErrno by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // 20003：对方账号已注销，聊天记录取不到、消息也发不出去，界面直接收成一句话。
    val dead = errorErrno == "20003"
    val peer = users[uid]
    val peerName = peer?.name?.ifBlank { name } ?: name.ifBlank { "私信" }

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
                App.repo.chat(uid = uid, sinceId = newest, count = ChatPageSize, force = true)
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

    fun send() {
        val content = draft.trim()
        if (content.isEmpty() || sending) return
        sending = true
        draft = ""
        val tempId = "local-${System.currentTimeMillis()}"
        messages.add(
            0,
            ChatMessage(
                id = tempId,
                text = toChatHtml(content),
                senderId = me,
                recipientId = uid,
                createdAt = SimpleDateFormat(WeiboTimeFormat, Locale.US).format(Date()),
                pending = true,
            ),
        )
        scope.launch {
            listState.animateScrollToItem(0)
            try {
                val real = App.repo.sendChat(uid, content)
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
                recipientId = uid,
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
                val fids = App.repo.uploadChatImage(uid, uri)
                val real = App.repo.sendChatImage(uid, fids)
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
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
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
                            modifier = Modifier.fillMaxSize(),
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
                                    ChatBubble(
                                        message = item,
                                        mine = item.senderId == me,
                                        avatar = if (item.senderId == me) null else peer?.avatar,
                                        onRetry = {
                                            if (item.isImage) {
                                                val local = item.imageUrl
                                                messages.remove(item)
                                                if (local.isNotBlank()) sendImage(Uri.parse(local))
                                            } else {
                                                draft = chatPlainText(item.text)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + navPad),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !sending) {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }
                            .testTag("chat_pick"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            COUIIcons.Image,
                            contentDescription = "发送图片",
                            tint = scheme.onSurfaceSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).testTag("chat_input"),
                        label = "说点什么…",
                        backgroundMode = TextFieldMode.Rectangle,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    val canSend = draft.isNotBlank() && !sending
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) scheme.primary else scheme.surfaceContainerHigh,
                            )
                            .clickable(enabled = canSend) { send() }
                            .testTag("chat_send"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            COUIIcons.Send,
                            contentDescription = "发送",
                            tint = if (canSend) scheme.onPrimary else scheme.onSurfaceSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            // 和「消息」页完全同一个材质：AppBarBlurOverlay（COUI 渐变玻璃，材质量到顶栏
            // +24dp 并向下淡出）。SmallTopAppBar 的均匀磨砂会在顶栏下缘留一条硬切线，
            // 这里把它的底色设成透明，只借它的标题 / 图标排版，材质由 overlay 画。
            AppBarBlurOverlay(
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
                        Avatar(
                            url = peer?.avatar,
                            size = 28.dp,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { nav.push(Route.Profile(uid)) }
                                .testTag("chat_peer_avatar"),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    mine: Boolean,
    avatar: String?,
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
