package com.cobo.app.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** `/api/config` 下发的 st 会过期，过期后写操作一律 403（errno 100006 请求非法）。 */
private const val ST_TTL_MS = 10 * 60 * 1000L

/** 网页会话的心跳间隔：像浏览器一样隔几小时打开一次页面，让服务端重发 SUB。 */
private const val WEB_HEARTBEAT_MS = 6 * 60 * 60 * 1000L

/**
 * 客户端登录的 cookie 多久换一次。参考 Share：6 小时用 `gsid` 换一次新四域 cookie，
 * 远早于 `SUB` 真正过期的时间；就算临时没换成功也有好几天重试窗口。
 */
private const val GSID_TTL_MS = 6 * 60 * 60 * 1000L

/** 续签失败后的最小重试冷却，避免每次列表请求连续打登录接口。 */
private const val CLIENT_REFRESH_COOLDOWN_MS = 60_000L

/** 同一条微博两次点赞之间的最小间隔，低于这个值基本只会换来 20016。 */
private const val LIKE_COOLDOWN_MS = 1500L

/** 特别关注名单缓存时间：名单变动不频繁，翻页时不必每页重拉。 */
private const val SPECIAL_UIDS_TTL_MS = 5 * 60 * 1000L

/** 关注分组列表缓存：分组只在网页版改，开一次「更多」菜单不必每次打一遍 weibo.com。 */
private const val GROUP_FEED_PAGE_SIZE = 25
private const val FOLLOW_GROUPS_TTL_MS = 10 * 60 * 1000L

/** `setGroup` 里「未分组」的占位 id：清空分组时也得把它提交回去。 */
private const val UNGROUPED_GID = "0"

/** 分组时间线一页拉多少条，和网页版「我的分组」一致。 */

/** 关注 / 粉丝列表接口固定一页 20 人；粉丝列表按已拉取的条数翻页。 */
private const val RELATION_PAGE_SIZE = 20

/** 特别关注流单次最多回扫多少页关注流、凑够多少条就停。 */
private const val SPECIAL_SCAN_PAGES = 4
private const val SPECIAL_PAGE_SIZE = 12

/** 关注流里扫不到时，最多拼几个特别关注用户的主页时间线兜底。 */
private const val SPECIAL_DIRECT_USERS = 5

/** 一轮拼流最多花多久；到点就用已有结果返回，避免下拉头长时间挂着。 */
private const val SPECIAL_REFRESH_BUDGET_MS = 15_000L

/** 访客身份被拒后多久内不再重领：真要登录的接口也回 -100，不能每次都去领一个新的。 */
private const val VISITOR_RETRY_MS = 60_000L

private const val GUEST_BLOCKED = "纯本地模式看不了这里，登录后可用"
private const val LOGIN_REQUIRED = "纯本地模式不能这样操作，登录后可用"

/** 登录渠道。数字与 Share 账号表里的 `loginType` 对齐：1 网页、4 客户端。 */
const val LOGIN_TYPE_WEB = 1
const val LOGIN_TYPE_CLIENT = 4
private const val WEBIM_SOURCE = "209678993"

/** 「本地关注」每轮并发拉几个人的主页时间线：并发再高容易被微博限流。 */
private const val TAG_FEED_CONCURRENCY = 4

/** 「本地关注」一页给多少条：多个人合并时按全局时间切，不按人切。 */
private const val TAG_FEED_PAGE_SIZE = 20

/** 为了让页与页之间保持时间序，翻得慢的人最多再补几轮。 */
private const val TAG_FEED_CATCHUP_ROUNDS = 3

/** 游标里表示「这个人已经翻到底」的记号。 */
private const val TAG_CURSOR_END = ""

/** [uid] 为空或不在 [accounts] 里时返回 [current]，否则返回那个账号的 cookie。 */
internal fun pickLocalFollowCookie(uid: String, accounts: List<Account>, current: String?): String? {
    if (uid.isBlank()) return current
    return accounts.firstOrNull { it.uid == uid }?.cookie?.takeIf { it.isNotBlank() } ?: current
}

/**
 * 「本地关注」的翻页游标：uid -> 这个人翻到的 since_id，空串表示他已经没有更早的微博。
 * 存成 JSON 是为了让一条流里每个人各记各的进度。
 */
internal fun encodeTagCursor(next: Map<String, String>): String {
    val o = JSONObject()
    next.forEach { (uid, since) -> o.put(uid, since) }
    return o.toString()
}

/** 解析失败或没游标时返回空表，等于所有人从头拉一遍。 */
internal fun parseTagCursor(cursor: String?): Map<String, String> {
    if (cursor.isNullOrBlank()) return emptyMap()
    val o = runCatching { JSONObject(cursor) }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    o.keys().forEach { uid -> out[uid] = o.optString(uid) }
    return out
}

/** 合并后的第 [size] 条微博的 id，作为这一页的时间切点；条数不够时没有切点。 */
private fun tagFeedCut(fetched: Map<String, List<Status>>, size: Int): Long? =
    fetched.values.asSequence()
        .flatten()
        .mapNotNull { it.id.toLongOrNull() }
        .sortedDescending()
        .elementAtOrNull(size - 1)

/**
 * 网页版分组表：分类下每一项的 `group` 才是分组，`gid` 是分组 id、`title` 是名字。
 *
 * 只认「我的分组」——用户自己在微博里建的分组都在这里，`groupstimeline` 也只认这批 gid。
 * 「默认分组」里是全部关注 / 最新微博 / 原创这些微博内置的固定组（它们的 gid 传给
 * `groupstimeline` 会直接回「列表不存在」），而且都有各自的入口了，不能混进来。
 * 特别关注 / 互相关注 / 悄悄关注 虽然也在分组表里，但首页已各有一个入口，跳过不重复列。
 */
internal fun parseFollowGroups(root: JSONObject?): List<FollowGroup> {
    val categories = root?.optJSONArray("groups") ?: return emptyList()
    val groups = LinkedHashMap<String, FollowGroup>()
    for (i in 0 until categories.length()) {
        val category = categories.optJSONObject(i) ?: continue
        if (category.str("title") != "我的分组") continue
        val items = category.optJSONArray("group") ?: continue
        for (j in 0 until items.length()) {
            val item = items.optJSONObject(j) ?: continue
            val gid = item.str("gid").ifBlank { item.str("id") }
            val title = item.str("title")
            if (gid.isBlank() || title.isBlank()) continue
            if (title == "特别关注" || title == "互相关注" || title == "悄悄关注") continue
            groups.putIfAbsent(gid, FollowGroup(gid, title))
        }
    }
    return groups.values.toList()
}

/** `setGroup` 的 `list_ids` / `origin_list_ids`：空选择落成 `0`（未分组），别传空串。 */
internal fun groupIdsParam(ids: List<String>): String =
    ids.filter { it.isNotBlank() }.joinToString(",").ifBlank { UNGROUPED_GID }

/**
 * `ajax/profile/getGroupList` 的分组 id 列表。
 *
 * 网页版换过结构：`data` 有时是数组，有时是 `data.lists`；两种都认。
 * `id=0` 是「未分组」占位，不能提交回去。
 */
internal fun parseGroupIds(root: JSONObject?): List<String> {
    val data = root?.opt("data") ?: return emptyList()
    val arr = when (data) {
        is JSONArray -> data
        is JSONObject -> data.optJSONArray("lists") ?: JSONArray()
        else -> JSONArray()
    }
    val ids = LinkedHashSet<String>()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val gid = item.str("idstr").ifBlank { item.str("id") }
        if (gid.isNotBlank() && gid != "0") ids.add(gid)
    }
    return ids.toList()
}

/** 网页版「基本资料」(`ajax/profile/detail`) 的白名单解析：字段名换过几轮，缺了就留空。 */
internal fun parseProfileDetail(data: JSONObject?): ProfileDetail {
    if (data == null) return ProfileDetail()
    fun value(key: String): Any? = data.opt(key)?.takeIf { it != JSONObject.NULL }
    fun text(key: String): String = (value(key) as? String)?.trim().orEmpty()
    fun structured(key: String): String = when (val v = value(key)) {
        is String -> v.trim()
        is JSONObject -> v.str("name").ifBlank { v.str("school") }.ifBlank { v.str("title") }
        else -> ""
    }
    val created = when (val v = value("created_at")) {
        is Number -> runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(v.toLong() * 1000))
        }.getOrDefault("")
        is String -> v.trim()
        else -> ""
    }
    val labels = buildList {
        val arr = data.optJSONArray("labels") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val name = when (item) {
                is JSONObject -> item.str("name").ifBlank { item.str("title") }
                is String -> item
                else -> ""
            }
            if (name.isNotBlank()) add(name)
        }
    }
    val credit = data.optJSONObject("sunshine_credit")?.str("level").orEmpty()
        .ifBlank { data.str("sunshine_credit") }
    return ProfileDetail(
        gender = when (text("gender").lowercase()) {
            "m", "male" -> "男"
            "f", "female" -> "女"
            else -> ""
        },
        birthday = text("birthday"),
        location = text("location"),
        ipLocation = text("ip_location"),
        createdAt = created,
        education = structured("education"),
        company = structured("company"),
        credit = credit,
        labels = labels,
        description = text("description"),
    )
}

/** 一个人这一轮拉到的最旧一条的 id。 */
private fun oldestStatusId(items: List<Status>?): Long? =
    items?.mapNotNull { it.id.toLongOrNull() }?.minOrNull()

/**
 * 微博搜索结果的排序。[type] 是微博搜索频道表里给的值：
 * 综合 `type=1`、实时 `type=61`、热门 `type=60`。
 */
enum class SearchSort(val label: String, val type: Int) {
    Comprehensive("综合", 1),
    Latest("最新", 61),
    Hot("最热", 60),
}

/** 话题详情页顶部分类标签，对应微博搜索频道 `100103type=X`。 */
enum class TopicChannel(val label: String, val type: Int) {
    Comprehensive("综合", 1),
    Latest("实时", 61),
    Pictures("图片", 63),
    Videos("视频", 64),
    Following("关注", 62),
    Hot("热门", 60),
}

class Repo(private val context: Context, private val store: Store) {

    /** 续期要串行：冷启动和并发请求可能同时看到需要续签的 gsid。 */
    private val clientRefreshLock = Mutex()

    @Volatile
    private var lastClientRefreshFailAt: Long = 0L

    @Volatile
    var session: Session? = store.session()
        private set

    @Volatile
    private var cachedConfig: Config? = null

    @Volatile
    private var configCookie: String? = null

    @Volatile
    private var cachedConfigAt: Long = 0L

    /** 访客 cookie：没登录时所有只读请求都带它，见 [get]。 */
    @Volatile
    private var visitorCookie: String? = store.visitorCookie()

    @Volatile
    private var visitorAt: Long = 0L

    @Volatile
    private var clientMessagesNextCursor: String = "0"

    private val visitorLock = Mutex()

    /** 全局本地收藏状态：跨多账号与纯本地模式共用，切号与登出均保留。 */
    val localFavoriteIds = mutableStateMapOf<String, Boolean>().apply {
        store.favorites().forEach { put(it.id, true) }
    }

    var favoritesVersion by mutableIntStateOf(0)
        private set

    val deletedStatusIds = mutableStateMapOf<String, Boolean>()

    var deletedVersion by mutableIntStateOf(0)
        private set

    @Volatile
    private var cachedEmotionSections: List<EmojiSection> = emptyList()

    @Volatile
    private var cachedEmotionCookie: String? = null

    @Volatile
    private var cachedMentionFriends: List<User> = emptyList()

    @Volatile
    private var cachedMentionUid: String? = null

    @Volatile
    private var cachedHotspotTopics: List<TopicSuggestion> = emptyList()

    init {
        // 网页端每次都可能在响应里滚一份新的 SUB / ALF，收到就收进表里，不进缓存也不去重。
        Api.cookieSink = { url, headers -> absorbCookies(url, headers) }
    }

    /** 读数据用的 cookie：登录了就是账号的，纯本地模式下是访客的（可能还没领到）。 */
    fun cookie(): String? = session?.cookie ?: visitorCookie

    /**
     * 把一次响应里的 `Set-Cookie` 合进当前会话。
     *
     * 这是登录态能留住的关键：网页端靠「隔几天来一次、每次都收回新 cookie」续命，
     * 只读不写的话手里那份只会慢慢过期。纯本地模式下落到访客 cookie 上。
     */
    private fun absorbCookies(url: HttpUrl, setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val active = session
        if (active == null) {
            val seed = CookieVault.seed(visitorCookie.orEmpty(), ".${url.host}")
            val merged = CookieVault.absorb(seed, url, setCookies)
            val header = CookieVault.header(merged, "m.weibo.cn")
            if (header.isBlank() || header == visitorCookie) return
            visitorCookie = header
            store.saveVisitorCookie(header)
            return
        }
        val base = if (active.cookies.isEmpty()) CookieVault.seed(active.cookie) else active.cookies
        val merged = CookieVault.absorb(base, url, setCookies)
        if (merged == base) return
        store.updateCookies(merged)
        val header = CookieVault.authHeader(merged, "m.weibo.cn")
        if (!header.contains("SUB=")) {
            if (active.loginType == LOGIN_TYPE_CLIENT && active.gsid.isNotBlank()) {
                // 客户端会话还有 gsid 可以换新 cookie，不能清掉 session。
                session = active.copy(cookies = merged, cookie = header)
            } else {
                // 服务端把 SUB 删了就是这个账号被登出了，内存里也立刻降级，别拿空壳继续请求。
                session = null
            }
            return
        }
        session = active.copy(cookies = merged, cookie = header.ifBlank { active.cookie })
        cachedConfig = null
        configCookie = null
        cachedConfigAt = 0L
        cachedEmotionSections = emptyList()
        cachedEmotionCookie = null
        cachedMentionFriends = emptyList()
        cachedMentionUid = null
        cachedHotspotTopics = emptyList()
    }

    /**
     * 整表灌进 WebView，对应 Share 的 `C0353Gz.O000000o(account, true)`。
     * 这样一来站内 WebView 用的永远是本地这一份，不会和 App 的登录态各走一路。
     */
    suspend fun injectCookies(cookies: Map<String, String> = session?.cookies.orEmpty()) {
        if (cookies.isEmpty()) return
        withContext(Dispatchers.Main) { CookieVault.inject(cookies) }
    }

    fun hasSession(): Boolean =
        session?.let { it.loggedIn || (it.loginType == LOGIN_TYPE_CLIENT && it.gsid.isNotBlank()) } == true

    /** 纯本地模式：没有登录账号，只读浏览，关注全靠本机标记。 */
    val guest: Boolean get() = !hasSession()

    fun isFavorited(id: String): Boolean = id.isNotBlank() && localFavoriteIds[id] == true

    fun isFavorited(status: Status): Boolean = isFavorited(status.id)

    fun isDeleted(id: String): Boolean = id.isNotBlank() && deletedStatusIds[id] == true

    fun toggleLocalFavorite(status: Status): Boolean {
        val nowFavorited = store.toggleFavorite(status)
        if (nowFavorited) localFavoriteIds[status.id] = true else localFavoriteIds.remove(status.id)
        favoritesVersion++
        return nowFavorited
    }

    /** 写操作只认登录账号的 cookie，访客身份发出去只会被拒。 */
    private fun loginCookie(): String =
        session?.takeIf { it.loggedIn }?.cookie ?: throw ApiException(LOGIN_REQUIRED)

    internal fun desktopCookieOrNull(): String? {
        val active = session ?: return visitorCookie
        return CookieVault.desktopAuthHeader(active.cookies, CookieVault.WEIBO_COM)
            .ifBlank { active.cookie }
    }

    @Volatile
    private var clientMobileRejectedCookie: String? = null

    private fun isPrivateMobileEndpoint(url: String): Boolean =
        url.contains("/feed/friends") ||
            url.contains("/feed/circle") ||
            url.contains("/message/") ||
            url.contains("/api/chat/") ||
            url.contains("/api/config")

    /**
     * 只读请求的统一入口。登录态直接走 [Api.get]；客户端登录的会话被拒（`ok:-100` 或 432）时，
     * 先拿 gsid 换一份新 cookie 再试一次；若新下发的 `.weibo.cn` Cookie 仍被 `m.weibo.cn` 拒绝，
     * 对公开可读的容器/详情接口回落到访客身份读取，避免反复刷 `account/login`。
     */
    private suspend fun get(url: String, cookie: String?, force: Boolean = false): JSONObject {
        val active = session
        if (active != null && active.loginType == LOGIN_TYPE_CLIENT && active.gsid.isNotBlank()) {
            ensureClientSession()
            val current = session ?: active
            val reqCookie = if (cookie.isNullOrBlank() || cookie == active.cookie) cookie() else cookie
            val skipClientMobile = reqCookie == current.cookie &&
                clientMobileRejectedCookie == current.cookie &&
                !isPrivateMobileEndpoint(url)
            val root = if (skipClientMobile) {
                JSONObject().put("ok", -100)
            } else {
                try {
                    Api.get(url, reqCookie, force)
                } catch (e: ApiHttpException) {
                    if (e.code != 432) throw e
                    JSONObject().put("ok", -100).put("msg", "HTTP 432")
                }
            }
            if (root.optInt("ok", 1) != -100) return root
            if (!skipClientMobile) {
                if (reqCookie == current.cookie && refreshClientSession(force = true)) {
                    val refreshed = session ?: current
                    val retry = runCatching { Api.get(url, refreshed.cookie, force = true) }.getOrNull()
                    if (retry != null && retry.optInt("ok", 1) != -100) {
                        clientMobileRejectedCookie = null
                        return retry
                    }
                    clientMobileRejectedCookie = refreshed.cookie
                } else if (reqCookie == current.cookie) {
                    clientMobileRejectedCookie = current.cookie
                }
            }
            if (!isPrivateMobileEndpoint(url)) {
                val firstVisitor = visitorFor(stale = null)
                val vRoot = try {
                    Api.get(url, firstVisitor, force)
                } catch (e: ApiHttpException) {
                    if (e.code != 432) throw e
                    null
                }
                if (vRoot != null && vRoot.optInt("ok", 1) != -100) return vRoot
                val freshVisitor = visitorFor(stale = firstVisitor)
                if (freshVisitor != firstVisitor) {
                    val vRetry = runCatching { Api.get(url, freshVisitor, force = true) }.getOrNull()
                    if (vRetry != null && vRetry.optInt("ok", 1) != -100) return vRetry
                }
            }
            return root
        }
        if (active != null || (cookie != null && cookie != visitorCookie)) return Api.get(url, cookie, force)
        val first = cookie ?: visitorFor(stale = null)
        val root = try {
            Api.get(url, first, force)
        } catch (e: ApiHttpException) {
            if (e.code != 432) throw e
            null
        }
        if (root != null && root.optInt("ok", 1) != -100) return root
        val fresh = visitorFor(stale = first)
        if (fresh == first) throw ApiException(GUEST_BLOCKED)
        val retry = Api.get(url, fresh, force = true)
        if (retry.optInt("ok", 1) == -100) throw ApiException(GUEST_BLOCKED)
        return retry
    }

    /** 拿一个可用的访客 cookie；[stale] 是刚被拒的那个，只有它还是当前值时才重领，且一分钟内只领一次。 */
    private suspend fun visitorFor(stale: String?): String = visitorLock.withLock {
        val current = visitorCookie
        if (current != null && current != stale) return@withLock current
        if (current != null && System.currentTimeMillis() - visitorAt < VISITOR_RETRY_MS) return@withLock current
        val fresh = Api.genVisitor()
        store.saveVisitorCookie(fresh)
        visitorCookie = fresh
        visitorAt = System.currentTimeMillis()
        fresh
    }

    fun enterLocalMode() {
        App.settings.applyLocalMode(true)
    }

    fun updateSession(cookie: String, uid: String) {
        resetAccountCaches(clearGroupTabs = true)
        App.settings.applyLocalMode(false)
        store.saveSession(cookie, uid)
        session = store.session()
    }

    /** 切换到已保存账号：客户端登录保留 gsid 以支持无感续签，网页登录保留 cookie 会话。 */
    fun switchAccount(account: Account) {
        resetAccountCaches(clearGroupTabs = true)
        App.settings.applyLocalMode(false)
        if (account.gsid.isNotBlank()) {
            store.saveClientSession(
                uid = account.uid,
                gsid = account.gsid,
                cookie = account.cookie,
                accessToken = "",
                expires = 0L,
                gsidAt = account.gsidAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        } else {
            store.saveSession(account.cookie, account.uid)
        }
        session = store.session()
    }

    fun logout() {
        resetAccountCaches(clearGroupTabs = true, clearSnapshotsAndScrolls = true)
        store.clearSession()
        session = null
    }

    /**
     * 换账号 / 退出登录时把跟「这个人」绑定的短期接口缓存整批清掉。
     *
     * 关注分组与特别关注名单都带有效期，有效期只对生成它的那个账号成立：不清就会在十分钟内
     * 沿用上一个账号的分组和名单。续看快照与滚动位置已经按账号 UID 分开存放，切号时保留
     * 各自上次的列表与位置，只有主动退出登录才全部清掉。
     */
    private fun resetAccountCaches(
        clearGroupTabs: Boolean,
        clearSnapshotsAndScrolls: Boolean = false,
    ) {
        App.cache.clear()
        if (clearSnapshotsAndScrolls) {
            App.snapshots.clear()
            App.settings.clearFeedScrolls()
        } else {
            App.settings.clearLegacyFeedScrolls()
        }
        if (clearGroupTabs) App.settings.clearGroupFeedTabs()
        cachedFollowGroups = emptyList()
        cachedFollowGroupsAt = 0L
        cachedCircleGroupId = null
        specialUids = emptySet()
        specialUidsAt = 0L
        cachedConfig = null
        configCookie = null
        cachedConfigAt = 0L
        cachedEmotionSections = emptyList()
        cachedEmotionCookie = null
        cachedMentionFriends = emptyList()
        cachedMentionUid = null
        cachedHotspotTopics = emptyList()
    }

    private suspend fun configFor(cookie: String, force: Boolean = false): Config {
        val fresh = System.currentTimeMillis() - cachedConfigAt < ST_TTL_MS
        if (!force && fresh && cachedConfig != null && configCookie == cookie) return cachedConfig!!
        val root = Api.get("https://m.weibo.cn/api/config", cookie, force = true)
        val data = root.optJSONObject("data")
        val config = Config(
            uid = data?.str("uid").orEmpty(),
            st = data?.str("st").orEmpty(),
            loggedIn = data?.optBoolean("login", false) == true || !data?.str("uid").isNullOrBlank(),
        )
        cachedConfig = config
        configCookie = cookie
        cachedConfigAt = System.currentTimeMillis()
        return config
    }

    suspend fun config(force: Boolean = false): Config {
        val cookie = session?.cookie ?: return Config("", "", false)
        return configFor(cookie, force)
    }

    suspend fun st(): String = config().st

    suspend fun loginWithCookie(
        cookie: String,
        cookies: Map<String, String> = CookieVault.seed(cookie),
    ): Account? {
        val cfg = configFor(cookie, force = true)
        if (!cfg.loggedIn || cfg.uid.isBlank()) return null
        val user = runCatching { profileFor(cookie, cfg.uid, force = true) }.getOrNull()
        resetAccountCaches(clearGroupTabs = true)
        App.settings.applyLocalMode(false)
        store.saveSession(cookie, cfg.uid, cookies)
        session = store.session()
        cachedConfig = cfg
        configCookie = cookie
        cachedConfigAt = System.currentTimeMillis()
        // 网页登录也把 cookie 灌回 WebView：站内打开的页面跟 App 用同一份登录态。
        injectCookies(session?.cookies.orEmpty())
        val account = Account(
            uid = cfg.uid,
            name = user?.name.orEmpty(),
            avatar = user?.avatar.orEmpty(),
            cookie = cookie,
        )
        store.saveAccount(account)
        return account
    }

    sealed interface ClientLoginStep {
        data class Success(val account: Account) : ClientLoginStep
        data class Captcha(val challenge: ClientLogin.CaptchaChallenge) : ClientLoginStep
        data class SafetyVerify(val challenge: ClientLogin.SafetyChallenge) : ClientLoginStep
        data class WebVerify(val url: String, val message: String) : ClientLoginStep
    }

    suspend fun sendLoginCode(phone: String, area: String = "") {
        ClientLogin.sendCode(phone, area)
    }

    /** 账号 + 密码登录首步（Share 方案），可能直接成功或进入登录保护 / 验证码。 */
    suspend fun loginWithPassword(
        account: String,
        password: String,
        cpt: String? = null,
        cptCode: String? = null,
        alt: String? = null,
    ): ClientLoginStep = mapClientOutcome(
        ClientLogin.loginWithPassword(account, password, cpt, cptCode, alt),
    )

    suspend fun sendSafetyVerifyCode(
        phone: ClientLogin.VerifyPhone,
        retcode: String,
    ): ClientLogin.SafetySmsSent = ClientLogin.sendSafetyVerifyCode(phone, retcode)

    suspend fun submitSafetyVerifyCode(
        account: String,
        password: String,
        phone: ClientLogin.VerifyPhone,
        retcode: String,
        number: String,
        smsCode: String,
    ): ClientLoginStep = mapClientOutcome(
        ClientLogin.submitSafetyVerifyCode(account, password, phone, retcode, number, smsCode),
    )

    /**
     * 短信验证码登录（Share 方案）：拿到长期 `gsid` 与四域 Cookie 表，
     * 并用 `m.weibo.cn` 核验一次确认会话可用。
     */
    suspend fun loginWithClient(phone: String, code: String, area: String = ""): Account =
        finishClientLogin(ClientLogin.verify(phone, area, code))

    private suspend fun mapClientOutcome(outcome: ClientLogin.Outcome): ClientLoginStep = when (outcome) {
        is ClientLogin.Outcome.Success -> ClientLoginStep.Success(finishClientLogin(outcome.result))
        is ClientLogin.Outcome.Captcha -> ClientLoginStep.Captcha(outcome.challenge)
        is ClientLogin.Outcome.SafetyVerify -> ClientLoginStep.SafetyVerify(outcome.challenge)
        is ClientLogin.Outcome.WebVerify -> ClientLoginStep.WebVerify(outcome.url, outcome.message)
    }

    private suspend fun finishClientLogin(result: ClientLogin.Result): Account {
        if (result.cookie.isBlank()) throw ApiException("登录失败：没有拿到 Cookie")
        if (result.uid.isBlank() || result.gsid.isBlank()) throw ApiException("登录失败：缺少客户端会话凭证")
        var effectiveCookies = if (result.cookies.isNotEmpty()) result.cookies else CookieVault.seed(result.cookie)
        var effectiveGsid = result.gsid
        var effectiveCookie = result.cookie
        var cfg = configFor(effectiveCookie, force = true)
        if (!cfg.loggedIn) {
            // 部分通道下发的 m.weibo.cn cookie 需要只保留 SUB/SUBP 或改用桌面域/gsid 换票验证
            val subPairs = CookieVault.parseHeader(effectiveCookie).filter {
                val key = it.substringBefore('=').trim()
                key == "SUB" || key == "SUBP"
            }
            if (subPairs.isNotEmpty()) {
                val subOnly = CookieVault.joinHeader(subPairs)
                val subCfg = runCatching { configFor(subOnly, force = true) }.getOrNull()
                if (subCfg?.loggedIn == true) {
                    cfg = subCfg
                    effectiveCookie = subOnly
                }
            }
        }
        if (!cfg.loggedIn) {
            val refreshed = runCatching { ClientLogin.refresh(result.uid, result.gsid) }.getOrNull()
            if (refreshed != null && refreshed.cookie.isNotBlank()) {
                val refCfg = runCatching { configFor(refreshed.cookie, force = true) }.getOrNull()
                if (refCfg?.loggedIn == true) {
                    cfg = refCfg
                    effectiveCookie = refreshed.cookie
                    if (refreshed.gsid.isNotBlank()) effectiveGsid = refreshed.gsid
                    if (refreshed.cookies.isNotEmpty()) effectiveCookies = refreshed.cookies
                }
            }
        }
        val uid = cfg.uid.ifBlank { result.uid }
        val desktopUser = if (!cfg.loggedIn) {
            runCatching {
                val desktopCookie = CookieVault.desktopAuthHeader(effectiveCookies, "weibo.com")
                val info = Api.getDesktop(
                    "https://weibo.com/ajax/profile/info?uid=$uid",
                    desktopCookie,
                )
                val ok = info.optInt("ok", 0) == 1
                val u = info.optJSONObject("data")?.optJSONObject("user")
                val screen = u?.str("screen_name").orEmpty()
                if (ok && u != null) {
                    User(
                        id = uid,
                        name = screen,
                        avatar = u.str("avatar_large").ifBlank { u.str("profile_image_url") },
                    )
                } else {
                    null
                }
            }.getOrNull()
        } else {
            null
        }
        if (!cfg.loggedIn && desktopUser == null) {
            throw ApiException("登录成功，但网页会话验证未通过")
        }
        val user = desktopUser ?: runCatching { profileFor(effectiveCookie, uid, force = true) }.getOrNull()
        resetAccountCaches(clearGroupTabs = true)
        App.settings.applyLocalMode(false)
        val now = System.currentTimeMillis()
        store.saveClientSession(
            uid = uid,
            gsid = effectiveGsid,
            cookie = effectiveCookie,
            accessToken = result.accessToken,
            expires = result.expires,
            cookies = effectiveCookies,
            gsidAt = now,
        )
        session = store.session()
        cachedConfig = cfg
        configCookie = effectiveCookie
        cachedConfigAt = now
        lastClientRefreshFailAt = 0L
        injectCookies(session?.cookies.orEmpty())
        val account = Account(
            uid = uid,
            name = user?.name.orEmpty(),
            avatar = user?.avatar.orEmpty(),
            cookie = effectiveCookie,
            gsid = effectiveGsid,
            gsidAt = now,
        )
        store.saveAccount(account)
        return account
    }

    /** 用客户端会话前确认 gsid 还没过期；刚换过不久就直接用缓存。 */
    private suspend fun ensureClientSession(): Boolean {
        val active = session ?: return false
        if (active.loginType != LOGIN_TYPE_CLIENT || active.gsid.isBlank()) return false
        if (active.loggedIn && System.currentTimeMillis() - active.gsidAt < GSID_TTL_MS) return true
        return refreshClientSession(force = false)
    }

    /**
     * Share 方案核心：拿 `gsid` 调 `account/login` 换回一整套新四域 Cookie，
     * 原地替换本地 `session.json` / `accounts.json` 并灌回 WebView。
     */
    private suspend fun refreshClientSession(force: Boolean, bypassCooldown: Boolean = false): Boolean =
        clientRefreshLock.withLock {
            val active = session ?: return@withLock false
            if (active.loginType != LOGIN_TYPE_CLIENT || active.gsid.isBlank()) return@withLock false
            if (!force && active.loggedIn && System.currentTimeMillis() - active.gsidAt < GSID_TTL_MS) {
                return@withLock true
            }
            val now = System.currentTimeMillis()
            if (!bypassCooldown && now - lastClientRefreshFailAt < CLIENT_REFRESH_COOLDOWN_MS) {
                return@withLock false
            }
            val attempt = runCatching { ClientLogin.refresh(active.uid, active.gsid) }
            val result = attempt.getOrNull()
            if (result == null || result.cookie.isBlank()) {
                lastClientRefreshFailAt = System.currentTimeMillis()
                val errCode = (attempt.exceptionOrNull() as? ApiException)?.errno
                if (!active.loggedIn && errCode in setOf("50113002", "-100", "21327", "21315")) {
                    session = null
                }
                return@withLock false
            }
            applyRefreshedClient(active, result)
            true
        }

    private suspend fun applyRefreshedClient(active: Session, result: ClientLogin.Result) {
        lastClientRefreshFailAt = 0L
        val gsid = result.gsid.ifBlank { active.gsid }
        val cookies = if (result.cookies.isEmpty()) active.cookies else result.cookies
        store.updateClientSession(gsid, result.cookie, result.accessToken, result.expires, cookies)
        session = store.session()
        App.cache.clear()
        cachedConfig = null
        configCookie = null
        cachedConfigAt = 0L
        injectCookies(cookies)
        val known = store.accounts().firstOrNull { it.uid == active.uid }
        if (known != null) {
            store.saveAccount(
                known.copy(
                    cookie = result.cookie,
                    gsid = gsid,
                    gsidAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** 手动触发一次 Share `gsid` 续签，成功返回 null，失败返回原因。 */
    suspend fun refreshClientSessionNow(): String? {
        val active = session ?: return "当前未登录"
        if (active.loginType != LOGIN_TYPE_CLIENT || active.gsid.isBlank()) {
            return "当前为网页登录，请改用客户端登录以获取长期 gsid"
        }
        return runCatching {
            if (refreshClientSession(force = true, bypassCooldown = true)) null else "续签失败，请稍后重试或重新登录"
        }.getOrElse { it.message ?: "续签失败" }
    }

    /**
     * 冷启动时顺手续一次登录态：
     * - 客户端会话（`gsid`）：距上次换 Cookie 超过 [GSID_TTL_MS] 或缺少 `SUB` 时静默换一套新表；
     * - 网页会话：像浏览器一样打开一次 `m.weibo.cn` 刷新短命 cookie，最后把表灌回 WebView。
     */
    suspend fun refreshSessionIfStale() {
        val active = session ?: return
        if (active.loginType == LOGIN_TYPE_CLIENT && active.gsid.isNotBlank()) {
            if (!active.loggedIn || System.currentTimeMillis() - active.gsidAt >= GSID_TTL_MS) {
                refreshClientSession(force = true, bypassCooldown = true)
            }
        } else if (active.loggedIn) {
            webHeartbeat(active)
        }
        injectCookies(session?.cookies.orEmpty())
    }

    /** 隔几小时才真的打一次，冷启动频繁也不会反复请求。 */
    private suspend fun webHeartbeat(active: Session) {
        if (System.currentTimeMillis() - active.savedAt < WEB_HEARTBEAT_MS) return
        runCatching { Api.getHtml("https://m.weibo.cn/", active.cookie) }
    }

    suspend fun container(
        cid: String,
        sinceId: String? = null,
        extra: Map<String, String> = emptyMap(),
        force: Boolean = false,
        cookie: String? = this.cookie(),
    ): FeedPage {
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = cid
        if (!sinceId.isNullOrBlank()) params["since_id"] = sinceId
        params.putAll(extra)
        val root = get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie, force)
        val page = parseCards(root)
        if (page.items.isEmpty() && root.optInt("ok", 1) != 1) {
            val message = root.str("msg", "加载失败")
            if (message.contains("还没有内容") || message.contains("暂无")) {
                return FeedPage(emptyList(), null)
            }
            throw ApiException(message)
        }
        return page
    }

    /**
     * The "关注" feed. It lives on /feed/friends, not on the container endpoint:
     * containerid=231093 is the follow-group management page and returns an empty
     * card list for accounts without groups. Cursor pagination uses max_id.
     */
    suspend fun friends(maxId: String? = null, force: Boolean = false): FeedPage {
        val active = session
        val skipMobile = active?.loginType == LOGIN_TYPE_CLIENT && active.cookie == clientMobileRejectedCookie
        val mobileErr = if (!skipMobile) {
            val mobile = runCatching { statusFeed("https://m.weibo.cn/feed/friends", maxId, force) }
            mobile.getOrNull()?.let { return it }
            mobile.exceptionOrNull()
        } else null
        if (active == null || active.loginType != LOGIN_TYPE_CLIENT) {
            throw mobileErr ?: ApiException("加载失败")
        }
        val params = LinkedHashMap<String, String?>()
        params["is_all"] = "1"
        params["count"] = GROUP_FEED_PAGE_SIZE.toString()
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        val root = Api.getDesktop(
            Api.url("https://weibo.com/ajax/feed/friendstimeline", params),
            desktopCookieOrNull(),
        )
        return parseDesktopTimeline(root)
    }

    /** The "好友圈" feed: posts from mutual follows, same shape as /feed/friends. */
    suspend fun circle(maxId: String? = null, force: Boolean = false): FeedPage {
        val active = session
        val skipMobile = active?.loginType == LOGIN_TYPE_CLIENT && active.cookie == clientMobileRejectedCookie
        val mobileErr = if (!skipMobile) {
            val mobile = runCatching { statusFeed("https://m.weibo.cn/feed/circle", maxId, force) }
            mobile.getOrNull()?.let { return it }
            mobile.exceptionOrNull()
        } else null
        if (active == null || active.loginType != LOGIN_TYPE_CLIENT) {
            throw mobileErr ?: ApiException("加载失败")
        }
        val gid = circleGroupId(force)
        if (!gid.isNullOrBlank()) return groupFeed(gid, maxId)
        throw mobileErr ?: ApiException("暂未找到「好友圈」分组")
    }

    /**
     * The "特别关注" feed.
     *
     * m.weibo.cn 没有单独的特别关注时间线：H5 的关注页只有 关注 / 好友圈 / 悄悄关注 / 自建分组
     * 四个 tab，特别关注的人发的东西是被塞进 /feed/friends 里的（weibo.com 设特别关注时的提示
     * 原话就是「发布的微博在信息流将置顶显示」）。所以这里扫关注流，只留下特别关注的人：
     * 先用 231093_-_specialfollow 把名单拿全，再退回每条 status 上现成的 special_follow 标记，
     * 两者取并集 —— 名单接口挂了也不至于整页空白。
     */
    suspend fun special(maxId: String? = null, force: Boolean = false): FeedPage =
        byUsers(specialFollowUids(force) + FollowTags.uids(FollowTag.Special), maxId, force)

    /** 「悄悄关注」：服务端没有这个名单，就按本机打的「悄悄」标记拼。 */
    suspend fun secret(maxId: String? = null, force: Boolean = false): FeedPage =
        byUsers(FollowTags.uids(FollowTag.Secret).toSet(), maxId, force)

    @Volatile
    private var cachedFollowGroups: List<FollowGroup> = emptyList()

    @Volatile
    private var cachedFollowGroupsAt: Long = 0L

    @Volatile
    private var cachedCircleGroupId: String? = null

    private suspend fun fetchDesktopAllGroups(): JSONObject = Api.getDesktop(
        Api.url(
            "https://weibo.com/ajax/feed/allGroups",
            mapOf("is_new_segment" to "1", "fetch_hot" to "1"),
        ),
        desktopCookieOrNull(),
    )

    /**
     * 账号里的自建关注分组。
     *
     * 列表在网页版 `ajax/feed/allGroups` 里：「我的分组」是自建分组，「默认分组」放着特别关注、
     * 互相关注这类固定组（这两个已经有单独入口，这里跳过）。纯本地模式拿不到分组，返回空表。
     */
    suspend fun followGroups(force: Boolean = false): List<FollowGroup> {
        if (guest) return emptyList()
        val fresh = System.currentTimeMillis() - cachedFollowGroupsAt < FOLLOW_GROUPS_TTL_MS
        if (!force && fresh) return cachedFollowGroups
        val root = fetchDesktopAllGroups()
        val groups = parseFollowGroups(root)
        cachedFollowGroups = groups
        cachedFollowGroupsAt = System.currentTimeMillis()
        cachedCircleGroupId = findGroupIdByTitle(root, "好友圈", "互相关注")
        return groups
    }

    private fun parseDesktopTimeline(root: JSONObject): FeedPage {
        val statuses = root.optJSONArray("statuses") ?: JSONArray()
        val items = statusesOf(statuses)
        if (items.isEmpty() && root.optInt("ok", 1) != 1) {
            // 网页版两种字段名都出现过：老接口回 msg，新接口回 message。
            val message = root.str("msg").ifBlank { root.str("message") }.ifBlank { "加载失败" }
            if (message.contains("还没有") || message.contains("暂无")) return FeedPage(emptyList(), null)
            throw ApiException(message)
        }
        // 到底时 max_id / since_id 回 0，0 不能再当游标发回去。
        val cursor = root.str("max_id")
            .ifBlank { root.optJSONObject("data")?.str("since_id").orEmpty() }
            .takeIf { it.isNotBlank() && it != "0" }
        return FeedPage(items, cursor)
    }

    /**
     * 某个关注分组的时间线。
     *
     * 走网页版「我的分组」的那条流（`ajax/feed/groupstimeline`）：它是按分组整体排序的，
     * 翻页游标在 `max_id`（部分响应放在 `data.since_id`）。卡片的配图、视频字段
     * [Status.from] 都认，和别处那条流看到的是同一套结构。
     */
    suspend fun groupFeed(gid: String, maxId: String? = null): FeedPage {
        if (gid.isBlank()) return FeedPage(emptyList(), null)
        val params = LinkedHashMap<String, String?>()
        params["list_id"] = gid
        params["refresh"] = "4"
        params["fast_refresh"] = "1"
        params["count"] = GROUP_FEED_PAGE_SIZE.toString()
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        val root = try {
            Api.getDesktop(
                Api.url("https://weibo.com/ajax/feed/groupstimeline", params),
                desktopCookieOrNull(),
            )
        } catch (e: ApiHttpException) {
            // 分组在网页版被删掉之后，这个 gid 会回 400「列表不存在」。
            val message = runCatching { JSONObject(e.body).str("message", "") }.getOrDefault("")
            if (message.contains("列表不存在")) throw ApiException("这个分组已经不存在了，在「更多」里换一个")
            throw e
        }
        return parseDesktopTimeline(root)
    }

    /**
     * 「本地关注」：把本机打了「本地」标记的人的主页时间线合并成一条能一直往下翻的流。
     *
     * 游标是「uid -> 这个人翻到哪了」的 JSON，每个人单独记 since_id，才不会因为某个人更新
     * 得多就把别人的旧微博挤掉。合并按全局时间序切页：页尾定在全局第 N 条，谁这一轮还没
     * 翻到这条线就以他为准再补一页——不然下一页会冒出比本页最后一条更新的微博。
     */
    suspend fun localFollows(maxId: String? = null, force: Boolean = false): FeedPage {
        val uids = FollowTags.uids(FollowTag.Local)
        if (uids.isEmpty()) return FeedPage(emptyList(), null)
        val cookie = localFollowCookie()
        val positions = parseTagCursor(maxId)
        val deadline = System.currentTimeMillis() + SPECIAL_REFRESH_BUDGET_MS
        val lock = Mutex()
        val fetched = HashMap<String, MutableList<Status>>()
        val pending = HashMap<String, String?>()
        val done = HashSet<String>()
        uids.forEach { uid ->
            if (positions[uid] == TAG_CURSOR_END) done.add(uid) else pending[uid] = positions[uid]
        }

        suspend fun pull(targets: List<String>) {
            if (targets.isEmpty()) return
            coroutineScope {
                val permits = Semaphore(TAG_FEED_CONCURRENCY)
                targets.map { uid ->
                    async {
                        if (System.currentTimeMillis() > deadline) return@async
                        permits.withPermit {
                            val since = lock.withLock { pending[uid] }
                            val page = runCatching {
                                container("107603$uid", sinceId = since, force = force, cookie = cookie)
                            }.getOrNull() ?: return@withPermit
                            lock.withLock {
                                // since_id 是闭区间：边界那条会原样再回来一次，只要更旧的。
                                val cutoff = since?.toLongOrNull()
                                val items = if (cutoff == null) page.items else page.items.filter {
                                    (it.id.toLongOrNull() ?: Long.MIN_VALUE) < cutoff
                                }
                                fetched.getOrPut(uid) { ArrayList() }.addAll(items)
                                if (page.sinceId.isNullOrBlank()) {
                                    done.add(uid)
                                    pending.remove(uid)
                                } else {
                                    pending[uid] = page.sinceId
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        val alive = uids.filter { it !in done }
        pull(alive)
        repeat(TAG_FEED_CATCHUP_ROUNDS - 1) {
            val cut = tagFeedCut(fetched, TAG_FEED_PAGE_SIZE) ?: return@repeat
            val behind = alive.filter { uid ->
                uid !in done && (oldestStatusId(fetched[uid]) ?: Long.MAX_VALUE) > cut
            }
            pull(behind)
        }

        val merged = LinkedHashMap<String, Pair<Status, String>>()
        fetched.forEach { (uid, items) -> items.forEach { merged.putIfAbsent(it.id, it to uid) } }
        val ordered = merged.values.sortedByDescending { it.first.id.toLongOrNull() ?: 0L }
        val page = ordered.take(TAG_FEED_PAGE_SIZE)
        if (page.isEmpty()) return FeedPage(emptyList(), null)

        val emittedOldest = HashMap<String, String>()
        val emittedCount = HashMap<String, Int>()
        page.forEach { (status, uid) ->
            emittedOldest[uid] = status.id
            emittedCount[uid] = (emittedCount[uid] ?: 0) + 1
        }
        val cursor = LinkedHashMap<String, String>()
        for (uid in uids) {
            val total = fetched[uid]?.size ?: 0
            val sent = emittedCount[uid] ?: 0
            val oldest = emittedOldest[uid]
            val old = positions[uid]
            if (uid in done && sent == total) {
                cursor[uid] = TAG_CURSOR_END
            } else if (oldest != null) {
                // 只推到真正发出去的最旧一条；被切在页外的下轮会重新拉，不会漏。
                cursor[uid] = oldest
            } else if (old != null) {
                cursor[uid] = old
            }
        }
        return FeedPage(page.map { it.first }, encodeTagCursor(cursor))
    }

    /**
     * 「本地关注」的取数身份：设置里选了哪个已保存账号就用它的 cookie，
     * 没选、或那个账号已经被删掉，就回到当前登录账号。
     */
    private fun localFollowCookie(): String? =
        if (guest) cookie() else pickLocalFollowCookie(App.settings.localFollowUid, store.accounts(), cookie())

    /**
     * 把一组人的微博拼成一条流。
     *
     * 先扫关注流，按原本的时间序把他们挑出来（这样和「全部」看到的是同一批卡片）；本机标记的
     * 人可能压根不在关注流里，所以再补一次各自的主页时间线，按 id 倒序去重。
     */
    private suspend fun byUsers(
        uids: Set<String>,
        maxId: String?,
        force: Boolean,
        cookie: String? = this.cookie(),
    ): FeedPage {
        if (uids.isEmpty()) return FeedPage(emptyList(), null)
        val kept = LinkedHashMap<String, Status>()
        var cursor = maxId
        var scanned = 0
        // 拼流要发好几次请求，网络半死时一路 25s 超时叠起来，下拉头会挂很久。给整轮一个预算，
        // 到点就用手上已有的返回：宁可比平时少几条，也不能让刷新看起来永远不结束。
        val deadline = System.currentTimeMillis() + SPECIAL_REFRESH_BUDGET_MS
        while (scanned < SPECIAL_SCAN_PAGES && kept.size < SPECIAL_PAGE_SIZE) {
            if (scanned > 0 && System.currentTimeMillis() > deadline) break
            val page = if (cookie == this.cookie()) {
                friends(cursor, force)
            } else {
                statusFeed("https://m.weibo.cn/feed/friends", cursor, force, cookie)
            }
            scanned++
            for (status in page.items) {
                val user = status.user ?: continue
                if (user.id in uids) kept[status.id] = status
            }
            cursor = page.sinceId
            if (cursor.isNullOrBlank()) break
        }
        if (kept.size < SPECIAL_PAGE_SIZE) {
            for (uid in uids.take(SPECIAL_DIRECT_USERS)) {
                if (System.currentTimeMillis() > deadline) break
                val page = runCatching {
                    container("107603$uid", force = force, cookie = cookie)
                }.getOrNull() ?: continue
                for (status in page.items) kept.putIfAbsent(status.id, status)
            }
        }
        val items = kept.values
            .sortedByDescending { it.id.toLongOrNull() ?: 0L }
            .take(SPECIAL_PAGE_SIZE * 2)
        return FeedPage(items, null)
    }

    @Volatile
    private var specialUids: Set<String> = emptySet()

    @Volatile
    private var specialUidsAt: Long = 0L

    /**
     * 特别关注名单。
     *
     * m.weibo.cn 没有现成的「特别关注」容器（231093_-_specialfollow 直接返回 ok:0），
     * 真名单在 weibo.com 的分组表里：先按标题找出「特别关注」这一组的 gid，再用
     * 231093_-_selfgroupfollow_-_<gid> 拉成员；这条挂了就退回分组时间线，只从里面挖 uid。
     */
    private suspend fun specialFollowUids(force: Boolean): Set<String> {
        val fresh = System.currentTimeMillis() - specialUidsAt < SPECIAL_UIDS_TTL_MS
        if (!force && fresh) return specialUids
        val ids = HashSet<String>()
        val gid = runCatching { specialGroupId() }.getOrNull()
        if (!gid.isNullOrBlank()) {
            runCatching {
                val url = Api.url(
                    "https://m.weibo.cn/api/container/getIndex",
                    linkedMapOf<String, String?>("containerid" to "231093_-_selfgroupfollow_-_$gid", "page" to "1"),
                )
                collectUserIds(get(url, cookie(), force), ids)
            }
            if (ids.isEmpty()) {
                runCatching {
                    val root = Api.getDesktop("https://weibo.com/ajax/feed/groupstimeline?list_id=$gid", desktopCookieOrNull())
                    val statuses = root.optJSONArray("statuses") ?: JSONArray()
                    for (i in 0 until statuses.length()) {
                        statuses.optJSONObject(i)
                            ?.optJSONObject("user")
                            ?.str("id")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(ids::add)
                    }
                }
            }
        }
        specialUids = ids
        specialUidsAt = System.currentTimeMillis()
        return ids
    }

    /** weibo.com 分组表里标题为「特别关注」那一组的 gid，4040233714204024 是它的历史固定值。 */
    private suspend fun specialGroupId(): String? =
        findGroupIdByTitle(fetchDesktopAllGroups(), "特别关注")

    private suspend fun circleGroupId(force: Boolean): String? {
        if (!force && !cachedCircleGroupId.isNullOrBlank()) return cachedCircleGroupId
        val root = fetchDesktopAllGroups()
        return findGroupIdByTitle(root, "好友圈", "互相关注").also { cachedCircleGroupId = it }
    }

    private fun findGroupIdByTitle(root: JSONObject?, vararg titles: String): String? {
        val groups = root?.optJSONArray("groups") ?: return null
        for (wanted in titles) {
            for (i in 0 until groups.length()) {
                val items = groups.optJSONObject(i)?.optJSONArray("group") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    if (item.str("title") == wanted) {
                        item.str("gid").ifBlank { item.str("id") }.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /** 卡片里挖用户：关注类容器的 user 可能挂在卡片本身，也可能挂在 mblog 里，统一递归取。 */
    private fun collectUserIds(node: Any?, into: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("user")?.str("id")?.takeIf { it.isNotBlank() }?.let(into::add)
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "user") continue
                    collectUserIds(node.opt(key), into)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectUserIds(node.opt(i), into)
        }
    }

    private suspend fun statusFeed(
        base: String,
        maxId: String?,
        force: Boolean,
        cookie: String? = this.cookie(),
        extra: Map<String, String?> = emptyMap(),
    ): FeedPage {
        val params = LinkedHashMap<String, String?>()
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        params.putAll(extra)
        val root = get(Api.url(base, params), cookie, force)
        val data = root.optJSONObject("data")
        if (data == null) {
            if (root.optInt("ok", 1) == 1) return FeedPage(emptyList(), null)
            val message = root.str("msg", "加载失败")
            if (message.contains("还没有内容") || message.contains("暂无")) {
                return FeedPage(emptyList(), null)
            }
            throw ApiException(message)
        }
        if (maxId.isNullOrBlank() && data.opt("statuses") == false) {
            throw ApiException("加载失败", "-100")
        }
        val arr = data.optJSONArray("statuses") ?: JSONArray()
        val items = statusesOf(arr)
        val next = data.str("max_id").ifBlank { data.str("since_id") }
        return FeedPage(items, next.ifBlank { null })
    }

    /** 热搜榜。[board] 换的是容器上的 `filter_type`，几个榜的条目结构完全一样。 */
    suspend fun hot(board: HotBoard = HotBoard.Realtime, force: Boolean = false): List<HotItem> {
        val cookie = cookie()
        val cid = "106003type=25&t=3&disable_hot=1&filter_type=${board.filterType}"
        val root = get(
            Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("containerid" to cid),
            ),
            cookie,
            force,
        )
        return parseHotItems(root)
    }

    /**
     * 搜索联想词。接口要 `X-XSRF-TOKEN` 和 cookie 配套，令牌取自 `/api/config`；
     * 不带令牌只会回「请求失败」。
     */
    suspend fun suggest(keyword: String): List<String> {
        val url = Api.url("https://m.weibo.cn/api/search/suggest", mapOf("keyword" to keyword))
        var cookie = cookie() ?: return emptyList()
        var st = configFor(cookie).st
        var data: JSONObject? = null
        // 这接口会没头没脑地回 ok:0「请求失败」，同一组参数隔一会儿再问一次就出结果，
        // 三试已经够用；访客 cookie 过期时顺手换一个身份再试。
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(150)
            try {
                data = Api.getWithSt(url, cookie, st).optJSONObject("data")
                if (data != null) break
            } catch (e: ApiHttpException) {
                if (e.code != 432 || session != null) return emptyList()
                cookie = visitorFor(stale = cookie)
                st = configFor(cookie, force = true).st
            }
        }
        val arr = data?.optJSONArray("suggestion") ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val text = arr.optJSONObject(i)?.str("suggestion").orEmpty()
            if (text.isNotBlank() && text !in out) out.add(text)
        }
        return out
    }

    suspend fun superTopic(containerId: String, sinceId: String? = null): SuperTopicPage {
        val id = superTopicId(containerId) ?: throw ApiException("无效的超话地址")
        val cursor = sinceId?.takeIf { it.isNotBlank() }
        if (cursor != null && !cursor.matches(Regex("[0-9]+"))) throw ApiException("无效的超话翻页参数")
        return superTopicTab(id, "${id}_-_feed", cursor)
    }

    suspend fun superTopicTab(
        baseContainerId: String,
        tabContainerId: String,
        cursor: String? = null,
    ): SuperTopicPage {
        val id = superTopicId(baseContainerId) ?: throw ApiException("无效的超话地址")
        if (!hasSession()) throw ApiException("超话需要登录后查看", "-100")
        val tabId = tabContainerId.trim().takeIf { it.lowercase().startsWith(id) } ?: "${id}_-_feed"
        val pageCursor = cursor?.takeIf { it.isNotBlank() }
        val pageNumber = if (pageCursor?.startsWith("page:") == true) {
            pageCursor.removePrefix("page:").toIntOrNull()?.coerceAtLeast(2)
                ?: throw ApiException("无效的超话翻页参数")
        } else null
        val active = session
        if (active?.loginType == LOGIN_TYPE_CLIENT) {
            return desktopSuperTopicTab(id, tabId, cursor)
        }
        val params = buildMap {
            put("containerid", tabId)
            if (pageNumber != null) {
                put("page", pageNumber.toString())
            } else {
                put("since_id", pageCursor)
            }
        }
        val root = try {
            get(Api.url("https://m.weibo.cn/api/container/getIndex", params), loginCookie(), force = true)
        } catch (e: ApiHttpException) {
            if (e.code == 432) throw ApiException("超话需要登录后查看", "432")
            throw ApiException("超话加载失败（HTTP ${e.code}）")
        } catch (e: ApiException) {
            if (e.errno == "-100" || e.message == GUEST_BLOCKED) {
                throw ApiException("超话需要登录后查看", "-100")
            }
            throw ApiException("超话加载失败")
        }
        val parsed = if (tabId.equals("${id}_-_feed", ignoreCase = true)) {
            parseSuperTopic(root, id)
        } else {
            parseSuperTopicTab(root, id, tabId, currentPage = pageNumber ?: 1)
        }
        return if (tabId.lowercase().endsWith("_-_video") && parsed.items.isNotEmpty()) {
            hydrateSuperTopicVideoItems(parsed)
        } else {
            parsed
        }
    }

    /**
     * 超话「视频」流（`_-_video`）的 `card_type=52` 仅带精简标题、封面与 `mid`，
     * 缺少博主头像和可播放的视频流地址；按 `mid` 并发通过 `statuses/show` 补全完整微博。
     */
    private suspend fun hydrateSuperTopicVideoItems(page: SuperTopicPage): SuperTopicPage = coroutineScope {
        val gate = Semaphore(6)
        val hydrated = page.items.map { item ->
            if (item.video != null && !item.user?.avatar.isNullOrBlank()) {
                async { item }
            } else {
                async {
                    gate.withPermit {
                        try {
                            detail(item.id, force = false) ?: item
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            item
                        }
                    }
                }
            }
        }.awaitAll()
        page.copy(items = hydrated)
    }

    suspend fun searchSuperTopics(query: String): List<TopicItem> {
        if (!hasSession()) throw ApiException("超话需要登录后查看", "-100")
        return try {
            searchIndex("100103type=1&q=$query", 1, true, ::parseSuperTopicSearch)
                .map { topic ->
                    if (topic.title == "超话") {
                        val id = topic.containerId ?: throw ApiException("超话身份缺失")
                        topic.copy(title = superTopic(id).info.title)
                    } else topic
                }
        } catch (e: ApiHttpException) {
            if (e.code == 432) throw ApiException("超话需要登录后查看", "432")
            throw ApiException("超话搜索失败（HTTP ${e.code}）")
        } catch (e: ApiException) {
            if (e.errno == "432") throw ApiException("超话需要登录后查看", "432")
            if (e.errno == "-100" || e.message == GUEST_BLOCKED) {
                throw ApiException("超话需要登录后查看", "-100")
            }
            throw ApiException("超话搜索失败")
        }
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        force: Boolean = true,
        sort: SearchSort = SearchSort.Comprehensive,
    ): FeedPage {
        val cid = "100103type=${sort.type}&q=$query"
        return container(
            cid,
            extra = if (page > 1) mapOf("page" to page.toString()) else emptyMap(),
            force = force,
        )
    }

    suspend fun topicChannelFeed(
        query: String,
        channel: TopicChannel,
        cursor: String? = null,
        force: Boolean = false,
    ): FeedPage {
        val page = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val cid = "100103type=${channel.type}&q=$query"
        val res = container(
            cid,
            extra = if (page > 1) mapOf("page" to page.toString()) else emptyMap(),
            force = force,
        )
        val next = if (res.items.isNotEmpty()) (page + 1).toString() else null
        return FeedPage(res.items, next)
    }

    /**
     * 搜索的「用户」分类（containerid 的 `type=3`）。和微博搜索一样按 page 翻页，
     * 空页就是到底了。
     */
    suspend fun searchUsers(query: String, page: Int = 1, force: Boolean = true): List<User> =
        searchIndex("100103type=3&q=$query&t=0", page, force, ::parseUserCards)

    /** 搜索的「话题」分类（`type=38`）。 */
    suspend fun searchTopics(query: String, page: Int = 1, force: Boolean = true): List<TopicItem> =
        searchIndex("100103type=38&q=$query&t=0", page, force, ::parseTopicCards)

    private suspend fun <T> searchIndex(
        cid: String,
        page: Int,
        force: Boolean,
        parse: (JSONObject?) -> List<T>,
    ): List<T> {
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = cid
        params["page_type"] = "searchall"
        params["page"] = page.coerceAtLeast(1).toString()
        val root = get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie(), force)
        val items = parse(root)
        if (items.isEmpty() && root.optInt("ok", 1) != 1) {
            val message = root.str("msg", "搜索失败")
            if (message.contains("还没有内容") || message.contains("暂无")) return emptyList()
            throw ApiException(message)
        }
        return items
    }

    suspend fun detail(id: String, force: Boolean = false): Status? {
        val root = get(
            Api.url("https://m.weibo.cn/statuses/show", mapOf("id" to id)),
            cookie(),
            force,
        )
        var parsed = Status.from(root.optJSONObject("data"))
        if (parsed == null && session?.loginType == LOGIN_TYPE_CLIENT) {
            parsed = runCatching {
                val desktopRoot = Api.getDesktop(
                    Api.url("https://weibo.com/ajax/statuses/show", mapOf("id" to id)),
                    desktopCookieOrNull(),
                )
                Status.from(desktopRoot)
            }.getOrNull()
        }
        if (parsed == null) return null
        val favorited = isFavorited(id)
        return if (parsed.favorited == favorited) parsed else parsed.copy(favorited = favorited)
    }

    suspend fun editHistory(mid: String, page: Int = 1): List<EditVersion> {
        if (mid.isBlank()) return emptyList()
        val root = Api.getDesktop(
            Api.url(
                "https://weibo.com/ajax/statuses/editHistory",
                mapOf("mid" to mid, "page" to page.coerceAtLeast(1).toString()),
            ),
            desktopCookieOrNull(),
        )
        val arr = root.optJSONArray("statuses") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                EditVersion.from(arr.optJSONObject(i))?.let(::add)
            }
        }
    }

    private val voteCache = java.util.concurrent.ConcurrentHashMap<String, VoteInfo>()
    private val statusVoteIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun resolveShortUrl(url: String): String =
        runCatching { Api.resolveRedirect(url, cookie()) }.getOrDefault(url)

    suspend fun resolveStatusVoteId(statusId: String, textHtml: String): String? {
        statusVoteIdCache[statusId]?.let { return it }
        val tcnMatch = Regex("""https?://t\.cn/[a-zA-Z0-9]+""").find(textHtml)
        if (tcnMatch != null) {
            val redirected = resolveShortUrl(tcnMatch.value)
            val vid = extractVoteId(redirected)
            if (vid != null) {
                statusVoteIdCache[statusId] = vid
                return vid
            }
        }
        val resolved = runCatching {
            val root = Api.getDesktop("https://weibo.com/ajax/statuses/show?id=$statusId", desktopCookieOrNull())
            val page = root.optJSONObject("page_info")
            val card = page?.optJSONObject("card_info")
            val voteObj = card?.optJSONObject("vote_object")
            voteObj?.str("id")?.takeIf { it.isNotBlank() }
                ?: extractVoteId(page?.str("page_url"))
                ?: extractVoteIdFromUrlStruct(root.optJSONArray("url_struct"))
        }.getOrNull()
        if (resolved != null) {
            statusVoteIdCache[statusId] = resolved
        }
        return resolved
    }

    suspend fun fetchVote(voteId: String, force: Boolean = false): VoteInfo {
        if (!force) {
            voteCache[voteId]?.let { return it }
        }
        val url = "https://vote.weibo.com/h5/index/index?vote_id=$voteId"
        val html = Api.getHtml(url, cookie())
        val regex = Regex("""window\.__DATA__\s*=\s*(\{.*?\})\s*\|\|\s*''\s*;""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html) ?: throw ApiException("无法解析投票页面数据")
        val root = try {
            JSONObject(match.groupValues[1])
        } catch (_: Throwable) {
            throw ApiException("投票数据格式错误")
        }
        val sk = root.str("sk")
        val vi = root.optJSONObject("vote_info") ?: throw ApiException("未找到投票信息")
        val parsed = parseVoteInfoJson(voteId, sk, vi)
        voteCache[voteId] = parsed
        return parsed
    }

    suspend fun castVote(voteId: String, optionIds: List<String>, sk: String): VoteInfo {
        if (optionIds.isEmpty()) throw ApiException("请选择投票选项")
        val cookie = loginCookie()
        val form = mapOf(
            "vote_id" to voteId,
            "vote_optionids" to optionIds.joinToString(","),
            "sk" to sk,
        )
        val referer = "https://vote.weibo.com/h5/index/index?vote_id=$voteId"
        val root = Api.postVote(
            "https://vote.weibo.com/vote/h5/aj/vote/index",
            form,
            cookie,
            referer,
        )
        val code = root.optInt("code", 0)
        if (code != 1) {
            throw ApiException(root.str("msg").ifBlank { "投票失败 ($code)" })
        }
        val data = root.optJSONObject("data")
        val vi = data?.optJSONObject("vote_info")
        if (vi != null) {
            val parsed = parseVoteInfoJson(voteId, sk, vi)
            voteCache[voteId] = parsed
            return parsed
        }
        return fetchVote(voteId, force = true)
    }

    suspend fun uncastVote(voteId: String, sk: String): VoteInfo {
        val cookie = loginCookie()
        val form = mapOf(
            "vote_id" to voteId,
            "sk" to sk,
        )
        val referer = "https://vote.weibo.com/h5/index/index?vote_id=$voteId"
        val root = Api.postVote(
            "https://vote.weibo.com/vote/h5/aj/cancelvote/index",
            form,
            cookie,
            referer,
        )
        val code = root.optInt("code", 0)
        if (code != 1) {
            throw ApiException(root.str("msg").ifBlank { "取消投票失败 ($code)" })
        }
        val data = root.optJSONObject("data")
        val vi = data?.optJSONObject("vote_info")
        if (vi != null) {
            val parsed = parseVoteInfoJson(voteId, sk, vi)
            voteCache[voteId] = parsed
            return parsed
        }
        return fetchVote(voteId, force = true)
    }

    suspend fun comments(
        id: String,
        mid: String,
        sort: CommentSort = CommentSort.Hot,
        maxId: String? = null,
        maxIdType: Int = 0,
        page: Int = 1,
        force: Boolean = false,
    ): CommentPage {
        val params = LinkedHashMap<String, String?>()
        params["id"] = id
        if (sort == CommentSort.Time) {
            // The chronological list is the plain status page's comment API: a page number per
            // request and `data.max` as the last page, where the hot list hands back a cursor.
            params["page"] = page.coerceAtLeast(1).toString()
            val root = get(
                Api.url("https://m.weibo.cn/api/comments/show", params),
                cookie(),
                force,
            )
            val data = root.optJSONObject("data") ?: return CommentPage(emptyList(), "", 0)
            val arr = data.optJSONArray("data") ?: JSONArray()
            val items = ArrayList<Comment>()
            for (i in 0 until arr.length()) {
                Comment.from(arr.optJSONObject(i))?.let(items::add)
            }
            val last = data.optInt("max", 0)
            return CommentPage(
                items = items,
                maxId = "",
                maxIdType = 0,
                nextPage = if (items.isNotEmpty() && page < last) page + 1 else 0,
            )
        }
        params["mid"] = mid.ifBlank { id }
        if (maxId.isNullOrBlank()) {
            params["max_id_type"] = "0"
        } else {
            params["max_id"] = maxId
            params["max_id_type"] = maxIdType.toString()
        }
        val root = get(
            Api.url("https://m.weibo.cn/comments/hotflow", params),
            cookie(),
            force,
        )
        val data = root.optJSONObject("data") ?: return CommentPage(emptyList(), "", 0)
        val arr = data.optJSONArray("data") ?: JSONArray()
        val items = ArrayList<Comment>()
        for (i in 0 until arr.length()) {
            Comment.from(arr.optJSONObject(i))?.let(items::add)
        }
        return CommentPage(
            items = items,
            maxId = data.str("max_id"),
            maxIdType = data.int("max_id_type"),
        )
    }

    /**
     * Replies under one comment ("楼中楼"). The hot list only inlines a preview, so the full
     * thread comes from `comments/hotFlowChild`, paged with the `max_id` cursor it hands back.
     */
    suspend fun commentReplies(
        cid: String,
        maxId: String? = null,
        maxIdType: Int = 0,
        force: Boolean = false,
    ): CommentPage {
        val params = LinkedHashMap<String, String?>()
        params["cid"] = cid
        params["max_id"] = maxId ?: "0"
        params["max_id_type"] = maxIdType.toString()
        val root = get(
            Api.url("https://m.weibo.cn/comments/hotFlowChild", params),
            cookie(),
            force,
        )
        val arr = root.optJSONArray("data") ?: JSONArray()
        val items = ArrayList<Comment>()
        for (i in 0 until arr.length()) {
            Comment.from(arr.optJSONObject(i), depth = 1)?.let(items::add)
        }
        val total = root.optInt("total_number", 0)
        return CommentPage(
            items = items,
            maxId = root.str("max_id"),
            maxIdType = root.int("max_id_type"),
            nextPage = if (total > 0) total else 0,
        )
    }

    /**
     * 写操作统一带 st。st 过期时服务端回的是 `HTTP 403` + `errno:100006 请求非法`，而不是
     * JSON 里的普通失败，所以 403 也要当成「st 失效」处理：强制刷新一次 config 再重试，
     * 否则一段时间之后赞、关注、转发会整片 403。
     */
    /**
     * 正文里的 @ 链接只有昵称（`/n/<昵称>`），没有 uid。用用户搜索接口换回 uid，
     * 点 @ 才能在应用内打开主页而不是掉进 WebView。
     */
    suspend fun uidForName(name: String): String? {
        val q = java.net.URLEncoder.encode(name, "UTF-8")
        val root = get(
            Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("containerid" to "100103type=3&q=$q"),
            ),
            cookie(),
        )
        val cards = root.optJSONObject("data")?.optJSONArray("cards") ?: return null
        var fallback: String? = null
        for (i in 0 until cards.length()) {
            val group = cards.optJSONObject(i)?.optJSONArray("card_group") ?: continue
            for (j in 0 until group.length()) {
                val user = group.optJSONObject(j)?.optJSONObject("user") ?: continue
                val id = user.str("id")
                if (id.isBlank()) continue
                if (user.str("screen_name") == name) return id
                if (fallback == null) fallback = id
            }
        }
        return fallback
    }

    private suspend fun postJson(url: String, form: Map<String, String>): JSONObject {
        val cookie = loginCookie()
        var st = st()
        var root = try {
            Api.post(url, form + ("st" to st), cookie, st)
        } catch (e: ApiHttpException) {
            if (!e.isCsrfRejection()) throw e
            st = config(force = true).st
            Api.post(url, form + ("st" to st), cookie, st)
        }
        if (root.optInt("ok", 0) != 1 && (root.str("msg").contains("st", ignoreCase = true) || isCsrfBody(root))) {
            st = config(force = true).st
            root = Api.post(url, form + ("st" to st), cookie, st)
        }
        return root
    }

    private fun isCsrfBody(root: JSONObject): Boolean =
        root.str("errno") == "100006" || root.str("msg").contains("非法")

    private fun ApiHttpException.isCsrfRejection(): Boolean =
        code == 403 || body.contains("100006") || body.contains("请求非法")

    private fun isClientLogin(): Boolean =
        session?.loginType == LOGIN_TYPE_CLIENT

    private fun checkDesktopOk(root: JSONObject, fallback: String): JSONObject {
        if (root.optInt("ok", 0) != 1) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", fallback) })
        }
        return root
    }

    private suspend fun postOk(url: String, form: Map<String, String>): Boolean {
        val root = postJson(url, form)
        if (root.optInt("ok", 0) != 1) throw ApiException(root.str("msg", "操作失败"))
        return true
    }

    suspend fun likeComment(id: String, liked: Boolean): Boolean {
        if (isClientLogin()) {
            val body = JSONObject().put("object_id", id).put("object_type", "comment").toString()
            checkDesktopOk(
                desktopWrite("操作失败", if (liked) "/ajax/statuses/destroyLike" else "/ajax/statuses/updateLike", body),
                "操作失败",
            )
            return true
        }
        return postOk(
            if (liked) "https://m.weibo.cn/comment/dislike" else "https://m.weibo.cn/comment/like",
            mapOf("id" to id),
        )
    }

    /** 同一条微博连点两下会连着打两次接口，微博随即回 20016「update weibo too fast!」。 */
    private val likeBusy = java.util.Collections.synchronizedMap(HashMap<String, Long>())

    suspend fun like(id: String, liked: Boolean): Boolean {
        val now = System.currentTimeMillis()
        synchronized(likeBusy) {
            val last = likeBusy[id]
            if (last != null && now - last < LIKE_COOLDOWN_MS) {
                throw ApiException("点得太快了，稍等一下")
            }
            likeBusy[id] = now
        }
        val ok = try {
            if (isClientLogin()) {
                val body = JSONObject().put("id", id).toString()
                checkDesktopOk(
                    desktopWrite("操作失败", if (liked) "/ajax/statuses/cancelLike" else "/ajax/statuses/setLike", body),
                    "操作失败",
                )
                true
            } else {
                postOk(
                    if (liked) "https://m.weibo.cn/api/attitudes/destroy"
                    else "https://m.weibo.cn/api/attitudes/create",
                    if (liked) mapOf("id" to id) else mapOf("id" to id, "attitude" to "heart"),
                )
            }
        } catch (e: Throwable) {
            synchronized(likeBusy) { likeBusy.remove(id) }
            throw e
        }
        App.cache.invalidate(
            Api.url("https://m.weibo.cn/statuses/show", mapOf("id" to id)),
            cookie(),
        )
        return ok
    }

    suspend fun favorite(id: String, favorited: Boolean): Boolean {
        if (favorited) {
            store.removeFavorite(id)
            withContext(Dispatchers.Main.immediate) {
                localFavoriteIds.remove(id)
                favoritesVersion++
            }
            return true
        }
        val status = detail(id) ?: throw ApiException("微博不存在")
        store.addFavorite(status)
        withContext(Dispatchers.Main.immediate) {
            localFavoriteIds[id] = true
            favoritesVersion++
        }
        return true
    }

    suspend fun follow(uid: String, follow: Boolean): Boolean {
        if (isClientLogin()) {
            val body = JSONObject().put(if (follow) "friend_uid" else "uid", uid).toString()
            checkDesktopOk(
                desktopWrite(
                    if (follow) "关注失败" else "取关失败",
                    if (follow) "/ajax/friendships/create" else "/ajax/friendships/destory",
                    body,
                ),
                if (follow) "关注失败" else "取关失败",
            )
            return true
        }
        val cookie = loginCookie()
        val url = if (follow) {
            "https://m.weibo.cn/api/friendships/create"
        } else {
            // The mobile web app really uses this spelling for unfollow.
            "https://m.weibo.cn/api/friendships/destory"
        }
        var st = st()
        var root = Api.post(url, mapOf("uid" to uid, "st" to st), cookie, st)
        if (!root.has("data") && root.optInt("ok", 0) != 1 &&
            root.str("msg").contains("st", ignoreCase = true)
        ) {
            st = config(force = true).st
            root = Api.post(url, mapOf("uid" to uid, "st" to st), cookie, st)
        }
        val ok = root.has("data") || root.optInt("ok", 0) == 1
        if (!ok) throw ApiException(root.str("msg", "操作失败"))
        App.cache.invalidate(
            Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("type" to "uid", "value" to uid),
            ),
            cookie,
        )
        return true
    }

    suspend fun comment(id: String, mid: String, text: String): Boolean {
        val ok = if (isClientLogin()) {
            checkDesktopOk(
                desktopPost(
                    "/ajax/comments/create",
                    form = mapOf(
                        "id" to id,
                        "mid" to mid.ifBlank { id },
                        "comment" to text,
                        "is_repost" to "0",
                    ),
                ),
                "评论失败",
            )
            true
        } else {
            // The mobile endpoint takes the body as `content`; `comment` gets rejected as 100013 参数缺失.
            postOk(
                "https://m.weibo.cn/api/comments/create",
                mapOf("id" to id, "mid" to mid.ifBlank { id }, "content" to text),
            )
        }
        App.cache.clear()
        return ok
    }

    /**
     * Answers a comment inside its own thread. `cid` is the comment being answered: a top-level
     * comment id for the first reply, or a reply id to nest under that particular reply. The
     * created reply comes back so the thread can show it without re-reading a lagging list.
     */
    suspend fun replyComment(id: String, mid: String, cid: String, text: String): Comment {
        val root = if (isClientLogin()) {
            desktopPost(
                "/ajax/comments/reply",
                form = mapOf(
                    "id" to id,
                    "mid" to mid.ifBlank { id },
                    "cid" to cid,
                    "comment" to text,
                    "is_repost" to "0",
                ),
            )
        } else {
            postJson(
                "https://m.weibo.cn/api/comments/reply",
                mapOf(
                    "id" to id,
                    "mid" to mid.ifBlank { id },
                    "cid" to cid,
                    "content" to text,
                ),
            )
        }
        if (root.optInt("ok", 0) != 1) throw ApiException(root.str("msg").ifBlank { root.str("message", "回复失败") })
        App.cache.clear()
        return Comment.from(root.optJSONObject("data"), depth = 1)
            ?: throw ApiException("回复失败")
    }

    suspend fun repost(id: String, mid: String, text: String, visible: Int = 0): Boolean {
        val ok = if (isClientLogin()) {
            checkDesktopOk(
                desktopPost(
                    "/ajax/statuses/normal_repost",
                    form = mapOf(
                        "id" to id,
                        "comment" to text,
                        "visible" to visible.toString(),
                        "is_repost" to "0",
                        "comment_ori" to "0",
                        "is_comment" to "0",
                    ),
                ),
                "转发失败",
            )
            true
        } else {
            val form = LinkedHashMap<String, String>()
            form["id"] = id
            form["mid"] = mid.ifBlank { id }
            form["content"] = text
            if (visible != 0) form["visible"] = visible.toString()
            postOk("https://m.weibo.cn/api/statuses/repost", form)
        }
        App.cache.clear()
        return ok
    }

    suspend fun publish(text: String, picIds: List<String>, visible: Int = 0): Boolean {
        val ok = if (isClientLogin()) {
            val form = LinkedHashMap<String, String>()
            form["content"] = text
            form["visible"] = visible.toString()
            if (picIds.isNotEmpty()) form["pic_id"] = picIds.joinToString(",")
            checkDesktopOk(desktopPost("/ajax/statuses/update", form = form), "发布失败")
            true
        } else {
            val form = LinkedHashMap<String, String>()
            form["content"] = text
            form["visible"] = visible.toString()
            if (picIds.isNotEmpty()) form["pic_ids"] = picIds.joinToString(",")
            postOk("https://m.weibo.cn/api/statuses/update", form)
        }
        App.cache.clear()
        return ok
    }

    suspend fun deleteStatus(id: String, mid: String = ""): Boolean {
        val targetMid = mid.ifBlank { id }
        val ok = if (isClientLogin()) {
            checkDesktopOk(
                desktopWrite("删除失败", "/ajax/statuses/destroy", JSONObject().put("id", targetMid).toString()),
                "删除失败",
            )
            true
        } else {
            postOk(
                "https://m.weibo.cn/profile/delMyblog",
                mapOf("mid" to targetMid),
            )
        }
        store.removeFavorite(id)
        if (targetMid != id) store.removeFavorite(targetMid)
        withContext(Dispatchers.Main.immediate) {
            if (localFavoriteIds.remove(id) != null || (targetMid != id && localFavoriteIds.remove(targetMid) != null)) {
                favoritesVersion++
            }
            deletedStatusIds[id] = true
            if (targetMid.isNotBlank()) deletedStatusIds[targetMid] = true
            deletedVersion++
        }
        App.cache.clear()
        return ok
    }

    suspend fun emotions(): List<EmojiSection> {
        val activeCookie = cookie().orEmpty()
        if (cachedEmotionSections.isNotEmpty() && cachedEmotionCookie == activeCookie) {
            return cachedEmotionSections
        }
        val sections = try {
            val root = Api.getDesktop("https://weibo.com/ajax/statuses/config", activeCookie)
            parseWeiboEmotions(root)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            emptyList()
        }
        if (sections.isNotEmpty()) {
            cachedEmotionSections = sections
            cachedEmotionCookie = activeCookie
            return sections
        }
        return FallbackWeiboEmojiSections
    }

    suspend fun mentionCandidates(query: String): List<User> {
        val friends = loadMentionFriends()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return friends.take(20)
        val merged = LinkedHashMap<String, User>()
        friends.filter { it.name.contains(trimmed, ignoreCase = true) }
            .forEach { merged.putIfAbsent(it.id, it) }
        val searched = try {
            searchUsers(trimmed, page = 1, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
        searched.forEach { merged.putIfAbsent(it.id, it) }
        return merged.values.take(20)
    }

    private suspend fun loadMentionFriends(): List<User> {
        val uid = session?.uid.orEmpty()
        if (cachedMentionFriends.isNotEmpty() && cachedMentionUid == uid) {
            return cachedMentionFriends
        }
        val cookie = cookie() ?: return emptyList()
        val fromCompose = try {
            val root = get(
                Api.url("https://m.weibo.cn/api/friendships/friends", mapOf("page" to "1")),
                cookie,
                force = false,
            )
            val arr = root.optJSONObject("data")?.optJSONArray("users")
            buildList<User> {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        User.from(arr.optJSONObject(i))?.let(::add)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
        val fromDesktop = if (fromCompose.isEmpty() && isClientLogin() && uid.isNotBlank()) {
            runCatching {
                val root = Api.getDesktop(
                    Api.url("https://weibo.com/ajax/friendships/friends", mapOf("uid" to uid, "page" to "1")),
                    desktopCookieOrNull(),
                )
                val arr = root.optJSONArray("users")
                buildList<User> {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            User.from(arr.optJSONObject(i))?.let(::add)
                        }
                    }
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val result = if (fromCompose.isNotEmpty() || fromDesktop.isNotEmpty() || uid.isBlank()) {
            fromCompose.ifEmpty { fromDesktop }
        } else {
            try {
                relations(uid, RelationKind.Following, page = 1, force = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }
        if (result.isNotEmpty()) {
            cachedMentionFriends = result
            cachedMentionUid = uid
        }
        return result
    }

    suspend fun topicCandidates(query: String): List<TopicSuggestion> {
        val trimmed = query.trim().trim('#')
        val cookie = cookie()
        if (trimmed.isEmpty()) {
            if (cachedHotspotTopics.isNotEmpty()) return cachedHotspotTopics
            val hotTopics = try {
                val root = get("https://m.weibo.cn/api/suggest/hotspot", cookie, force = false)
                parseHotspotTopics(root)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
            val result = if (hotTopics.isNotEmpty()) {
                hotTopics
            } else {
                try {
                    hot(HotBoard.Realtime, force = false).take(20).map {
                        TopicSuggestion(title = it.title, desc = it.label)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            if (result.isNotEmpty()) cachedHotspotTopics = result
            return result.take(20)
        }
        val out = LinkedHashMap<String, TopicSuggestion>()
        try {
            val root = get(
                Api.url("https://m.weibo.cn/api/suggest/shotspot", mapOf("keyword" to trimmed)),
                cookie,
                force = false,
            )
            parseHotspotTopics(root).forEach { out.putIfAbsent(it.title, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        if (out.isEmpty()) {
            try {
                searchTopics(trimmed, page = 1, force = false).forEach {
                    val clean = it.title.trim('#')
                    if (clean.isNotBlank()) out.putIfAbsent(clean, TopicSuggestion(clean, it.desc1))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        out.putIfAbsent(trimmed, TopicSuggestion(title = trimmed, desc = "新话题"))
        return out.values.take(20)
    }

    suspend fun uploadImage(uri: Uri): String {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val mime = resolver.getType(uri) ?: "image/jpeg"
        if (isClientLogin()) {
            val md5Hex = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
            val root = Api.uploadDesktopPic(bytes, session?.uid.orEmpty(), md5Hex, mime, desktopCookie())
            val pid = root.optJSONObject("pic")?.str("pid").orEmpty()
                .ifBlank { root.optJSONObject("data")?.optJSONObject("pics")?.optJSONObject("pic_1")?.str("pid").orEmpty() }
            if (pid.isBlank()) throw ApiException(root.str("msg").ifBlank { root.str("errno", "图片上传失败") })
            return pid
        }
        val cookie = loginCookie()
        val st = st()
        val name = when {
            mime.contains("png") -> "pic.png"
            mime.contains("gif") -> "pic.gif"
            mime.contains("webp") -> "pic.webp"
            else -> "pic.jpg"
        }
        val root = Api.upload(
            "https://m.weibo.cn/api/statuses/uploadPic",
            bytes,
            name,
            mime,
            cookie,
            st,
        )
        val id = root.str("pic_id")
        if (id.isBlank()) throw ApiException(root.str("msg", "图片上传失败"))
        return id
    }

    suspend fun messages(page: Int, force: Boolean = false): List<MessageItem> {
        val active = session
        if (active?.loginType == LOGIN_TYPE_CLIENT) {
            return clientMessages(page)
        }
        val root = get(
            Api.url("https://m.weibo.cn/message/msglist", mapOf("page" to page.toString())),
            cookie(),
            force,
        )
        val arr = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<MessageItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                MessageItem(
                    id = o.str("id"),
                    text = o.str("text"),
                    createdAt = o.str("created_at"),
                    user = User.from(o.optJSONObject("user")),
                    unread = o.int("unread"),
                    scheme = o.str("scheme"),
                    gid = chatGidOf(o.str("scheme")),
                )
            )
        }
        return out
    }

    /**
     * 聊天记录。接口给的是**最新在前**，[ChatPage.messages] 保持这个顺序，
     * 界面用 `reverseLayout = true` 铺，index 0 落在屏幕最下面。
     *
     * 群聊走 `api/groupchat/list`（参数 `gid`），返回是**旧→新**，这里统一翻成最新在前。
     *
     * @param maxId 取更早的一屏（传当前最旧那条的 id）
     * @param sinceId 取更新的消息（轮询用）
     */
    suspend fun chat(
        uid: String = "",
        gid: String = "",
        maxId: String? = null,
        sinceId: String? = null,
        count: Int = 20,
        force: Boolean = false,
    ): ChatPage {
        val isGroup = gid.isNotBlank()
        val active = session
        if (active?.loginType == LOGIN_TYPE_CLIENT) {
            return clientChat(uid, gid, maxId, sinceId, count)
        }
        val params = LinkedHashMap<String, String?>()
        params[if (isGroup) "gid" else "uid"] = if (isGroup) gid else uid
        params["count"] = count.toString()
        params["max_id"] = maxId
        params["since_id"] = sinceId
        if (sinceId != null) params["is_continuous"] = "1"
        val url = Api.url(
            if (isGroup) "https://m.weibo.cn/api/groupchat/list"
            else "https://m.weibo.cn/api/chat/list",
            params,
        )
        val root = get(url, cookie(), force)
        if (root.optInt("ok", 1) == 0) {
            // errno 要带出去：20003 是对方注销，界面据此不再给「重试」。
            throw ApiException(root.str("msg", "聊天记录加载失败"), root.str("errno"))
        }
        val data = root.optJSONObject("data")
            ?: return ChatPage(emptyList(), emptyMap(), "", 0)
        return parseChatPage(data, isGroup)
    }

    /** 发一条纯文字私信。群聊传 [gid]，一对一传 [uid]。 */
    suspend fun sendChat(uid: String, text: String, gid: String = ""): ChatMessage? {
        if (isClientLogin()) {
            return clientSendChat(uid, gid, text)
        }
        return if (gid.isNotBlank()) {
            sendChatForm(
                "https://m.weibo.cn/api/groupchat/send",
                mapOf("gid" to gid, "content" to text),
            )
        } else {
            sendChatForm(
                "https://m.weibo.cn/api/chat/send",
                mapOf("uid" to uid, "content" to text),
            )
        }
    }

    private suspend fun desktopSuperTopicTab(
        baseId: String,
        tabId: String,
        cursor: String?,
    ): SuperTopicPage {
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = baseId
        params["flowId"] = tabId
        if (cursor?.startsWith("ch:") == true) {
            val extra = runCatching { JSONObject(cursor.removePrefix("ch:")) }.getOrNull()
            extra?.keys()?.forEach { key ->
                val v = extra.opt(key)
                if (v != null && v != JSONObject.NULL) params[key] = v.toString()
            }
        }
        val root = Api.getDesktop(
            Api.url("https://weibo.com/ajax_proxy/chaohua/page", params),
            desktopCookieOrNull(),
        )
        val parsed = parseDesktopSuperTopic(root, baseId, tabId)
        return if (tabId.lowercase().endsWith("_-_video") && parsed.items.isNotEmpty()) {
            hydrateSuperTopicVideoItems(parsed)
        } else {
            parsed
        }
    }

    private suspend fun webImRequest(send: suspend (String) -> JSONObject): JSONObject {
        if (session?.let { !it.loggedIn && it.loginType == LOGIN_TYPE_CLIENT && it.gsid.isNotBlank() } == true) {
            ensureClientSession()
        }
        var root = send(desktopCookie())
        if (root.int("error_code") == 21301 && refreshClientSession(force = true)) {
            root = send(desktopCookie())
        }
        val errCode = root.int("error_code", 0)
        if (errCode != 0) {
            val msg = root.str("error").ifBlank { "私信请求失败（$errCode）" }
            throw ApiException(msg, errCode.toString())
        }
        return root
    }

    private suspend fun clientMessages(page: Int): List<MessageItem> {
        val cursor = if (page <= 1) {
            clientMessagesNextCursor = "0"
            "0"
        } else {
            clientMessagesNextCursor.takeIf { it.isNotBlank() && it != "0" } ?: return emptyList()
        }
        val url = Api.url(
            "https://api.weibo.com/webim/2/direct_messages/contacts.json",
            linkedMapOf(
                "special_source" to "3",
                "source" to WEBIM_SOURCE,
                "cursor" to cursor,
                "count" to "30",
                "is_include_folder" to "0",
            ),
        )
        val root = webImRequest { Api.getWebIm(url, it) }
        val next = root.str("next_cursor")
        clientMessagesNextCursor = if (next == cursor) "0" else next
        val arr = root.optJSONArray("contacts") ?: root.optJSONArray("user_list") ?: return emptyList()
        val out = ArrayList<MessageItem>(arr.length())
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val userObj = entry.optJSONObject("user")
            val dm = entry.optJSONObject("message") ?: entry.optJSONObject("direct_message")
            val user = User.from(userObj) ?: continue
            val numericId = user.id.toLongOrNull()
            if (numericId != null && numericId <= 0L) continue
            val userType = userObj?.int("type", 1) ?: 1
            if (userType !in 1..2) continue
            val folderId = dm?.str("folder_id").orEmpty()
            if (folderId.isNotBlank() && folderId != "0") continue
            val isGroup = userObj?.optJSONArray("members") != null ||
                (userObj?.int("max_member_count") ?: 0) > 0 ||
                userType == 2
            val gid = dm?.str("gid").orEmpty().ifBlank { if (isGroup) user.id else "" }
            val scheme = if (gid.isNotBlank()) {
                "/message/chat?gid=$gid"
            } else {
                "/message/chat?uid=${user.id}"
            }
            val msgId = dm?.str("idstr").orEmpty()
                .ifBlank { dm?.str("id").orEmpty() }
                .ifBlank { user.id }
            val previewText = dm?.str("text").orEmpty().ifBlank {
                if (dm?.int("type") == 499) {
                    runCatching { JSONObject(dm.str("msg")).str("content1") }.getOrNull().orEmpty()
                } else {
                    ""
                }.ifBlank { dm?.str("msg").orEmpty() }
            }
            out.add(
                MessageItem(
                    id = msgId,
                    text = previewText,
                    createdAt = dm?.str("created_at").orEmpty(),
                    user = user,
                    unread = entry.int("unread_count"),
                    scheme = scheme,
                    gid = gid,
                )
            )
        }
        return out
    }

    private suspend fun clientChat(
        uid: String,
        gid: String,
        maxId: String?,
        sinceId: String?,
        count: Int,
    ): ChatPage {
        val isGroup = gid.isNotBlank()
        val params = LinkedHashMap<String, String?>()
        params["source"] = WEBIM_SOURCE
        params["count"] = count.toString()
        if (isGroup) {
            params["convert_emoji"] = "1"
            params["query_sender"] = "1"
            params["id"] = gid
        } else {
            params["uid"] = uid
        }
        if (!maxId.isNullOrBlank()) {
            params["max_id"] = maxId.toLongOrNull()?.minus(1)?.toString() ?: maxId
        } else if (!sinceId.isNullOrBlank()) {
            params["since_id"] = sinceId
        }
        val endpoint = if (isGroup) {
            "https://api.weibo.com/webim/groupchat/query_messages.json"
        } else {
            "https://api.weibo.com/webim/2/direct_messages/conversation.json"
        }
        val root = webImRequest { Api.getWebIm(Api.url(endpoint, params), it) }
        val arr = (if (isGroup) root.optJSONArray("messages") else root.optJSONArray("direct_messages"))
            ?: return ChatPage(emptyList(), emptyMap(), "", 0)
        val messages = ArrayList<ChatMessage>(arr.length())
        val users = HashMap<String, User>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            ChatMessage.fromClient(o)?.let { messages.add(it) }
            User.from(o.optJSONObject("sender"))?.let { u -> users[u.id] = u }
            User.from(o.optJSONObject("from_user"))?.let { u -> users[u.id] = u }
            User.from(o.optJSONObject("recipient"))?.let { u -> users[u.id] = u }
        }
        // 统一保证最新在前（index 0 落在 reverseLayout 底部）。
        if (messages.size >= 2) {
            val firstId = messages.first().id.toLongOrNull() ?: 0L
            val lastId = messages.last().id.toLongOrNull() ?: 0L
            if (firstId in 1 until lastId) messages.reverse()
        }
        return ChatPage(messages, users, "", messages.size)
    }

    private suspend fun clientSendChat(uid: String, gid: String, text: String): ChatMessage? {
        val isGroup = gid.isNotBlank()
        val form = LinkedHashMap<String, String>()
        form["source"] = WEBIM_SOURCE
        if (isGroup) {
            form["id"] = gid
            form["content"] = text
            form["return_detail"] = "1"
        } else {
            form["uid"] = uid
            form["text"] = text
        }
        val endpoint = if (isGroup) {
            "https://api.weibo.com/webim/groupchat/send_message.json"
        } else {
            "https://api.weibo.com/webim/2/direct_messages/new.json"
        }
        val root = webImRequest { Api.postWebIm(endpoint, form, it) }
        return ChatMessage.fromClient(root)
    }

    /**
     * 发图片私信。
     *
     * H5 是两步：先 `api/chat/upload`（multipart `file` + `tuid`）拿 `fids`，
     * 再把 `fids` 交给 `api/chat/send`（`media_type=1`，`content` 留空）。
     * 群聊的发送接口是 `api/groupchat/send`，参数换成 `gid`。
     */
    suspend fun sendChatImage(uid: String, fids: String, gid: String = ""): ChatMessage? =
        if (gid.isNotBlank()) {
            sendChatForm(
                "https://m.weibo.cn/api/groupchat/send",
                mapOf("gid" to gid, "content" to "", "media_type" to "1", "fids" to fids),
            )
        } else {
            sendChatForm(
                "https://m.weibo.cn/api/chat/send",
                mapOf("uid" to uid, "content" to "", "media_type" to "1", "fids" to fids),
            )
        }

    private suspend fun sendChatForm(url: String, form: Map<String, String>): ChatMessage? {
        val root = postJson(url, form)
        if (root.optInt("ok", 0) != 1) {
            // 关注门槛这类业务拦截，人话在 extra 里，msg 只有「出错了，未知错误」。
            throw ApiException(
                root.str("extra").ifBlank { root.str("msg", "发送失败") },
                root.str("errno"),
            )
        }
        return ChatMessage.from(
            root.optJSONObject("data")?.optJSONArray("msgs")?.optJSONObject(0),
        )
    }

    /** 上传私信图片，返回 `fids`。群聊传 [gid]，上传字段换成 `tochatid`。 */
    suspend fun uploadChatImage(uid: String, uri: Uri, gid: String = ""): String {
        val cookie = loginCookie()
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = "chat." + (mime.substringAfterLast('/', "jpg"))
        var st = st()
        var root = uploadChat(uid, gid, bytes, name, mime, cookie, st)
        if (root.optInt("ok", 0) != 1 && isCsrfBody(root)) {
            st = config(force = true).st
            root = uploadChat(uid, gid, bytes, name, mime, cookie, st)
        }
        val fids = root.optJSONObject("data")?.str("fids").orEmpty()
        if (fids.isBlank()) throw ApiException(root.str("msg", "图片上传失败"), root.str("errno"))
        return fids
    }

    private suspend fun uploadChat(
        uid: String,
        gid: String,
        bytes: ByteArray,
        name: String,
        mime: String,
        cookie: String,
        st: String,
    ) = Api.upload(
        "https://m.weibo.cn/api/chat/upload",
        bytes,
        name,
        mime,
        cookie,
        st,
        field = "file",
        extra = if (gid.isNotBlank()) mapOf("tochatid" to gid) else mapOf("tuid" to uid),
    )

    suspend fun notifications(tab: NotificationTab, maxId: String? = null): NotificationPage {
        if (!hasSession()) throw ApiException(LOGIN_REQUIRED, errno = "-100")
        if (isClientLogin()) ensureClientSession()
        val params = LinkedHashMap<String, String?>()
        if (!maxId.isNullOrBlank() && maxId != "0") params["max_id"] = maxId
        val url = Api.url("https://weibo.com${tab.path}", params)
        return try {
            val root = Api.getDesktop(url, desktopCookieOrNull())
            parseDesktopNotifications(root, tab)
        } catch (e: ApiException) {
            if (isClientLogin() && e.errno == "-100" && refreshClientSession(force = true)) {
                parseDesktopNotifications(Api.getDesktop(url, desktopCookieOrNull()), tab)
            } else {
                throw e
            }
        } catch (e: ApiHttpException) {
            if (isClientLogin() && (e.code == 403 || e.code == 418 || e.code == 432) && refreshClientSession(force = true)) {
                parseDesktopNotifications(Api.getDesktop(url, desktopCookieOrNull()), tab)
            } else {
                throw e
            }
        }
    }

    suspend fun profileFor(cookie: String?, uid: String, force: Boolean = false): User? {
        if (session?.loginType == LOGIN_TYPE_CLIENT) {
            desktopProfile(uid)?.let { return it }
        }
        val url = Api.url(
            "https://m.weibo.cn/api/container/getIndex",
            mapOf("type" to "uid", "value" to uid),
        )
        fun pick(root: JSONObject): User? =
            User.from(root.optJSONObject("data")?.optJSONObject("userInfo"))

        pick(get(url, cookie, force))?.let { return it }

        // 缓存里可能躺着早先的失败响应（`ok:0`、登录墙，或者没有 userInfo 的旧结构），
        // 那样主页会一直显示 0 关注 0 粉丝。命中空壳就强制回源一次，再试 containerid 形式。
        if (!force) {
            pick(get(url, cookie, true))?.let { return it }
            val alt = Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("containerid" to "100505$uid"),
            )
            pick(get(alt, cookie, true))?.let { return it }
        }
        return desktopProfile(uid)
    }

    private suspend fun desktopProfile(uid: String): User? {
        val desk = desktopCookieOrNull() ?: return null
        return runCatching {
            val info = Api.getDesktop(
                Api.url("https://weibo.com/ajax/profile/info", mapOf("uid" to uid)),
                desk,
            )
            if (info.optInt("ok", 0) != 1) null
            else User.from(info.optJSONObject("data")?.optJSONObject("user"))
        }.getOrNull()
    }

    suspend fun profile(uid: String, force: Boolean = false): User? = profileFor(cookie(), uid, force)

    // ---- 主页「更多」菜单：关注分组 / 基本资料 / 拉黑 ----

    /** 桌面端 cookie：`.weibo.com` 自己的 XSRF-TOKEN 要留着，身份字段再从别的域补齐。 */
    private fun desktopCookie(): String {
        val active = session?.takeIf { it.loggedIn } ?: throw ApiException(LOGIN_REQUIRED)
        return CookieVault.desktopAuthHeader(active.cookies, CookieVault.WEIBO_COM)
            .ifBlank { active.cookie }
    }

    private fun desktopXsrf(): String? {
        val active = session ?: return null
        return CookieVault.cookieValue(active.cookies, CookieVault.WEIBO_COM, "XSRF-TOKEN")
            ?: CookieVault.cookieValue(active.cookies, CookieVault.WEIBO_CN, "XSRF-TOKEN")
    }

    /**
     * `.weibo.com` 的 XSRF-TOKEN 过期时，读一次主站配置让服务端重发一份。
     * 这条响应只是用来种 cookie，内容本身不用。
     */
    private suspend fun refreshDesktopAuth() {
        runCatching { Api.getDesktop("https://weibo.com/ajax/config/get_config", desktopCookie()) }
    }

    /**
     * 桌面端写操作。`errno:100006 / 请求非法` 说明 XSRF 过期了，刷新一次再重试；
     * 其余错误原样抛出，别把「没有权限」误当成令牌问题。
     */
    private suspend fun desktopPost(
        path: String,
        form: Map<String, String>? = null,
        json: String? = null,
    ): JSONObject {
        if (session?.let { !it.loggedIn && it.loginType == LOGIN_TYPE_CLIENT && it.gsid.isNotBlank() } == true) {
            ensureClientSession()
        }
        val url = "https://weibo.com$path"
        suspend fun send(): JSONObject {
            val cookie = desktopCookie()
            val token = desktopXsrf()
            return if (json != null) {
                Api.postDesktopJson(url, json, cookie, token)
            } else {
                Api.postDesktopForm(url, form.orEmpty(), cookie, token)
            }
        }
        var last: Throwable? = null
        for (attempt in 0..1) {
            if (attempt == 1) refreshDesktopAuth()
            try {
                val root = send()
                if (!isCsrfBody(root) || attempt == 1) return root
            } catch (e: ApiHttpException) {
                if (!e.isCsrfRejection() || attempt == 1) throw e
                last = e
            }
        }
        throw last ?: ApiException("操作失败")
    }

    /**
     * 某个人的关注分组：能勾选的自建分组 + 他当前已在的分组。
     *
     * 分组列表复用首页中间栏那份 `ajax/feed/allGroups`，两处以后看到的是同一批；
     * 已加入的从 `ajax/profile/getGroupList` 取，提交时原样当作 `origin_list_ids`。
     *
     * `getGroupList` 只认「自己关注的人」，没关注时直接 400，网页端也是把这次读取吞掉
     * 当空处理，所以这里同样兜成空列表，别让它把整个菜单点死。
     */
    suspend fun profileGroups(uid: String): ProfileGroupSelection {
        if (guest) throw ApiException(LOGIN_REQUIRED)
        val assigned = runCatching {
            parseGroupIds(
                Api.getDesktop(
                    Api.url("https://weibo.com/ajax/profile/getGroupList", mapOf("uid" to uid)),
                    desktopCookie(),
                ),
            )
        }.getOrDefault(emptyList())
        val groups = followGroups(force = true).map { group ->
            ProfileGroup(
                gid = group.gid,
                name = group.title,
                joined = group.gid in assigned,
            )
        }
        return ProfileGroupSelection(groups, assigned)
    }

    /**
     * 新建关注分组：网页端 `ajax/profile/createGroup`。
     *
     * `isOpen` 是「公开分组」开关，这里固定建私有分组；建完把分组列表缓存清掉，
     * 下次打开对话框会重新打一遍 `ajax/feed/allGroups` 拿到新的那一项。
     */
    suspend fun createProfileGroup(name: String): ProfileGroup {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ApiException("分组名称不能为空")
        val json = JSONObject()
            .put("name", trimmed)
            .put("isOpen", false)
            .toString()
        val root = desktopWrite("新建分组失败", "/ajax/profile/createGroup", json)
        val data = root.optJSONObject("data")
        if (root.optInt("ok", 0) != 1 || data == null) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", "新建分组失败") })
        }
        cachedFollowGroupsAt = 0L
        return ProfileGroup(
            gid = data.str("idstr").ifBlank { data.str("id") },
            name = data.str("name").ifBlank { trimmed },
        )
    }

    /**
     * 重命名分组：网页端 `ajax/profile/updateGroup`。
     *
     * 官方前端还会带 `isOpen`，但那是「公开分组」开关，带上等于把私有组改成公开，
     * 不带也能改名且服务端保持原样，所以这里只发名字。
     */
    suspend fun renameProfileGroup(gid: String, name: String): ProfileGroup {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ApiException("分组名称不能为空")
        val json = JSONObject()
            .put("list_id", gid)
            .put("name", trimmed)
            .toString()
        val root = desktopWrite("重命名分组失败", "/ajax/profile/updateGroup", json)
        if (root.optInt("ok", 0) != 1) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", "重命名分组失败") })
        }
        cachedFollowGroupsAt = 0L
        val saved = root.optJSONObject("data")?.str("name").orEmpty()
        return ProfileGroup(gid = gid, name = saved.ifBlank { trimmed })
    }

    /** 删除分组：网页端 `ajax/profile/destroyGroup`，组里的人会掉回「未分组」。 */
    suspend fun deleteProfileGroup(gid: String) {
        if (gid.isBlank()) throw ApiException("分组不存在")
        val json = JSONObject().put("list_id", gid).toString()
        val root = desktopWrite("删除分组失败", "/ajax/profile/destroyGroup", json)
        if (root.optInt("ok", 0) != 1) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", "删除分组失败") })
        }
        cachedFollowGroupsAt = 0L
    }

    /** 调整分组顺序：网页端 `ajax/profile/updateGroupOrder`，`list_ids` 按新顺序逗号拼接。 */
    suspend fun reorderProfileGroups(gids: List<String>) {
        val ids = gids.filter { it.isNotBlank() }
        if (ids.size < 2) return
        val json = JSONObject().put("list_ids", ids.joinToString(",")).toString()
        val root = desktopWrite("调整分组顺序失败", "/ajax/profile/updateGroupOrder", json)
        val applied = root.optJSONObject("data")?.optBoolean("result") ?: (root.optInt("ok", 0) == 1)
        if (!applied) throw ApiException(root.str("msg").ifBlank { "调整分组顺序失败" })
        cachedFollowGroupsAt = 0L
    }

    /**
     * 写操作的统一入口。
     *
     * 网页端被频率限制或参数不合法时只回一段 HTML 的 400，[ApiHttpException] 的 "HTTP 400"
     * 没法直接给用户看，这里换成一句能看懂的提示；其他错误原样往上抛。
     */
    private suspend fun desktopWrite(fallback: String, path: String, json: String): JSONObject =
        try {
            desktopPost(path, json = json)
        } catch (e: ApiHttpException) {
            if (e.code == 400) throw ApiException("$fallback，可能已达上限或操作太频繁，稍后再试") else throw e
        }

    /**
     * 把这个人整体换到 [selected] 这几个分组；[origin] 是打开菜单时读到的原分组。
     *
     * 网页端是「整组替换」：`list_ids` 给最终结果，`origin_list_ids` 告诉服务端要摘掉哪些，
     * 两个都缺一不可（只给 `list_ids` 时旧分组不会被移除）。空选择用 `0` 表示「未分组」。
     * 官方前端还会带一个 `list_name`，但中文名进 JSON 会被网关直接 400，服务端并不依赖它。
     */
    suspend fun setProfileGroups(uid: String, selected: List<String>, origin: List<String>): Boolean {
        val json = JSONObject()
            .put("uids", uid)
            .put("list_ids", groupIdsParam(selected))
            .put("origin_list_ids", groupIdsParam(origin))
            .toString()
        val root = desktopWrite("设置分组失败", "/ajax/profile/setGroup", json)
        val applied = root.optJSONObject("data")?.optBoolean("result") ?: false
        if (root.optInt("ok", 0) != 1 || !applied) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", "设置分组失败") })
        }
        return true
    }

    /** 网页版「基本资料」：性别、生日、所在地、注册时间这些 m.weibo.cn 的 userInfo 里没有的字段。 */
    suspend fun profileDetail(uid: String): ProfileDetail =
        parseProfileDetail(
            Api.getDesktop(
                Api.url("https://weibo.com/ajax/profile/detail", mapOf("uid" to uid)),
                desktopCookie(),
            ).optJSONObject("data"),
        )

    /**
     * 加入黑名单：网页端 `ajax/statuses/filterUser`。
     * `status/interact/follow` 全填 1，分别对应「不让他看我的微博 / 不让他与我互动 / 不让他关注我」。
     */
    suspend fun blockUser(uid: String): Boolean {
        val numeric = uid.toLongOrNull() ?: throw ApiException("用户不存在")
        val json = JSONObject()
            .put("uid", numeric)
            .put("status", 1)
            .put("interact", 1)
            .put("follow", 1)
            .toString()
        val root = desktopPost("/ajax/statuses/filterUser", json = json)
        if (root.optInt("ok", 0) != 1) {
            throw ApiException(root.str("msg").ifBlank { root.str("message", "拉黑失败") })
        }
        return true
    }

    suspend fun ownUid(): String =
        session?.uid?.takeIf { it.isNotBlank() } ?: config().uid

    suspend fun userStatuses(uid: String, sinceId: String? = null, force: Boolean = false): FeedPage {
        if (session?.loginType == LOGIN_TYPE_CLIENT) {
            val desktop = runCatching { desktopUserStatuses(uid, sinceId) }.getOrNull()
            if (desktop != null) return desktop
        }
        return container("107603$uid", sinceId, force = force)
    }

    private suspend fun desktopUserStatuses(uid: String, cursor: String?): FeedPage? {
        val desk = desktopCookieOrNull() ?: return null
        var page = 1
        var since = ""
        if (!cursor.isNullOrBlank()) {
            if (!cursor.startsWith("wb:")) return null
            val rest = cursor.removePrefix("wb:")
            page = rest.substringBefore(':').toIntOrNull()?.coerceAtLeast(1) ?: return null
            since = rest.substringAfter(':', "")
        }
        val params = LinkedHashMap<String, String?>()
        params["uid"] = uid
        params["page"] = page.toString()
        params["feature"] = "0"
        if (since.isNotBlank()) params["since_id"] = since
        val root = Api.getDesktop(
            Api.url("https://weibo.com/ajax/statuses/mymblog", params),
            desk,
        )
        if (root.optInt("ok", 0) != 1) return null
        val data = root.optJSONObject("data") ?: return null
        val list = data.optJSONArray("list") ?: JSONArray()
        val items = statusesOf(list)
        val nextSince = data.str("since_id")
        val nextCursor = if (items.isNotEmpty() && nextSince.isNotBlank() && nextSince != "0") {
            "wb:${page + 1}:$nextSince"
        } else {
            null
        }
        return FeedPage(items, nextCursor)
    }

    /**
     * 关注 / 粉丝列表。自己的列表走 `selffollowed` / `selffans`（别人那套容器对自己的
     * 账号不完整），别人的走 `231051`；关注按页码翻，粉丝按已拉取的条数偏移翻。
     */
    suspend fun relations(
        uid: String,
        kind: RelationKind,
        page: Int,
        force: Boolean = false,
    ): List<User> {
        val own = uid.isNotBlank() && uid == ownUid()
        val cid = when (kind) {
            RelationKind.Following ->
                if (own) "231093_-_selffollowed" else "231051_-_followers_-_$uid"
            RelationKind.Followers ->
                if (own) "231016_-_selffans" else "231051_-_fans_-_$uid"
        }
        val current = page.coerceAtLeast(1)
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = cid
        when (kind) {
            RelationKind.Following -> params["page"] = current.toString()
            RelationKind.Followers -> params["since_id"] = ((current - 1) * RELATION_PAGE_SIZE).toString()
        }
        val root = get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie(), force)
        return parseRelationUsers(root)
    }

    suspend fun favorites(cursor: String? = null, force: Boolean = false): FeedPage =
        FeedPage(store.favorites(), null)

    /**
     * 附近 / 同城流。
     *
     * `m.weibo.cn/api/feed/trendtop?containerid=102803_2222` 在客户端会话或部分 Cookie 下
     * 会直接返回 `{"ok":1,"data":{"statuses":false}}` 导致整页空白；桌面端同城接口
     * `weibo.com/ajax/feed/hottimeline`（`group_id=1028032222`）同时认 `lat` / `lon` 与
     * `max_id` 翻页，客户端登录态、网页登录态与访客态都能正常出流。
     */
    suspend fun nearby(
        lat: String,
        lon: String,
        maxId: String? = null,
        force: Boolean = false,
    ): FeedPage {
        val useDesktop = isClientLogin() || maxId?.contains("_-_") == true
        if (useDesktop) {
            return desktopNearby(lat, lon, maxId)
        }
        val mobile = runCatching {
            statusFeed(
                "https://m.weibo.cn/api/feed/trendtop?containerid=102803_2222",
                maxId,
                force,
                extra = mapOf("lat" to lat, "lon" to lon),
            )
        }.getOrNull()
        if (mobile != null && (mobile.items.isNotEmpty() || !maxId.isNullOrBlank())) {
            return mobile
        }
        return desktopNearby(lat, lon, maxId)
    }

    private suspend fun desktopNearby(
        lat: String,
        lon: String,
        maxId: String?,
    ): FeedPage {
        if (isClientLogin()) ensureClientSession()
        val params = LinkedHashMap<String, String?>()
        params["group_id"] = "1028032222"
        params["containerid"] = "102803_2222"
        params["extparam"] = "discover|new_feed"
        params["refresh"] = if (maxId.isNullOrBlank()) "0" else "2"
        params["count"] = "15"
        params["lat"] = lat
        params["lon"] = lon
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        val url = Api.url("https://weibo.com/ajax/feed/hottimeline", params)
        val initialCookie = desktopCookieOrNull() ?: visitorFor(null)
        val root = try {
            Api.getDesktop(url, initialCookie)
        } catch (e: ApiHttpException) {
            if (isClientLogin() && (e.code == 403 || e.code == 418 || e.code == 432) && refreshClientSession(force = true)) {
                Api.getDesktop(url, desktopCookieOrNull() ?: visitorFor(null))
            } else {
                Api.getDesktop(url, visitorFor(null))
            }
        }
        val page = runCatching { parseDesktopTimeline(root) }.getOrNull()
        if (page != null && page.items.isNotEmpty()) return page
        if (root.optInt("ok", 1) != 1 || page == null) {
            if (isClientLogin() && refreshClientSession(force = true)) {
                val retried = runCatching {
                    parseDesktopTimeline(Api.getDesktop(url, desktopCookieOrNull() ?: visitorFor(null)))
                }.getOrNull()
                if (retried != null && retried.items.isNotEmpty()) return retried
            }
            val visitorRoot = Api.getDesktop(url, visitorFor(null))
            return parseDesktopTimeline(visitorRoot)
        }
        return page
    }

    /**
     * 「附近」能选的城市。
     *
     * 微博网页版切城市用的就是 `2306860024` 这个容器，`needlocation=1` 时每条城市链接里的
     * `push_mid=qiehuan_<cityId>_<lon>_<lat>` 直接带着坐标，取出来喂给同城流就能换一座城。
     * 接口抽风或返回空时兜底常见城市列表，保证切换城市弹窗可用。
     */
    suspend fun nearbyCities(force: Boolean = false): List<NearbyPlace> {
        val url = Api.url(
            "https://m.weibo.cn/api/container/getIndex",
            linkedMapOf(
                "containerid" to "2306860024_____8008631000000000000",
                "needlocation" to "1",
                "extparam" to "city",
            ),
        )
        val places = runCatching { parseNearbyPlaces(get(url, cookie(), force)) }.getOrDefault(emptyList())
        if (places.isNotEmpty()) return places
        val visitorPlaces = runCatching {
            parseNearbyPlaces(Api.get(url, visitorFor(null), force = true))
        }.getOrDefault(emptyList())
        return visitorPlaces.ifEmpty { DEFAULT_NEARBY_PLACES }
    }

    fun clearLocalHistory() = store.clearHistory()
}

/** 城市链接里的坐标：`qiehuan_8008631000000000000_121.47_31.23` 是 经度_纬度。 */
private val NearbyPlaceScheme = Regex("qiehuan_\\d+_([\\d.]+)_([\\d.]+)")

private fun parseNearbyPlaces(root: JSONObject): List<NearbyPlace> {
    val cards = root.optJSONObject("data")?.optJSONArray("cards") ?: return emptyList()
    val places = LinkedHashMap<String, NearbyPlace>()

    fun add(name: String?, scheme: String?) {
        if (name.isNullOrBlank() || scheme.isNullOrBlank()) return
        val decoded = runCatching { URLDecoder.decode(scheme, "UTF-8") }.getOrDefault(scheme)
        val match = NearbyPlaceScheme.find(decoded) ?: return
        val lon = match.groupValues[1].toDoubleOrNull() ?: return
        val lat = match.groupValues[2].toDoubleOrNull() ?: return
        if (lon == 0.0 && lat == 0.0) return
        val label = name.trim()
        places.putIfAbsent(label, NearbyPlace(label, lat, lon))
    }

    for (i in 0 until cards.length()) {
        val groups = cards.optJSONObject(i)?.optJSONArray("card_group") ?: continue
        for (j in 0 until groups.length()) {
            val group = groups.optJSONObject(j) ?: continue
            // 历史城市卡：名字在同项 tag 的 display_name 上，链接在 tag_scheme 上。
            val tags = group.optJSONObject("profile_tag")?.optJSONArray("tags")
            if (tags != null) {
                for (k in 0 until tags.length()) {
                    val tag = tags.optJSONObject(k) ?: continue
                    add(tag.str("display_name"), tag.str("tag_scheme"))
                }
            }
            // 省市卡：一格里三个城市，名字在 title_sub、链接在 scheme 上。
            val items = group.optJSONArray("group") ?: continue
            for (k in 0 until items.length()) {
                val item = items.optJSONObject(k) ?: continue
                add(item.str("title_sub"), item.str("scheme"))
            }
        }
    }
    return places.values.toList()
}

private val DEFAULT_NEARBY_PLACES = listOf(
    NearbyPlace("北京", 39.9042, 116.4074),
    NearbyPlace("上海", 31.2304, 121.4737),
    NearbyPlace("广州", 23.1291, 113.2644),
    NearbyPlace("深圳", 22.5431, 114.0579),
    NearbyPlace("成都", 30.5728, 104.0668),
    NearbyPlace("杭州", 30.2741, 120.1551),
    NearbyPlace("重庆", 29.5630, 106.5516),
    NearbyPlace("武汉", 30.5928, 114.3055),
    NearbyPlace("西安", 34.3416, 108.9398),
    NearbyPlace("南京", 32.0603, 118.7969),
    NearbyPlace("苏州", 31.2989, 120.5853),
    NearbyPlace("长沙", 28.2282, 112.9388),
    NearbyPlace("天津", 39.0842, 117.2009),
    NearbyPlace("郑州", 34.7466, 113.6254),
    NearbyPlace("青岛", 36.0671, 120.3826),
    NearbyPlace("厦门", 24.4798, 118.0894),
    NearbyPlace("沈阳", 41.8057, 123.4315),
    NearbyPlace("大连", 38.9140, 121.6147),
    NearbyPlace("昆明", 24.8801, 102.8329),
    NearbyPlace("合肥", 31.8206, 117.2272),
    NearbyPlace("哈尔滨", 45.8038, 126.5349),
    NearbyPlace("济南", 36.6512, 117.1201),
    NearbyPlace("福州", 26.0745, 119.2965),
    NearbyPlace("香港", 22.3193, 114.1694),
    NearbyPlace("台北", 25.0330, 121.5654),
    NearbyPlace("东京", 35.6762, 139.6503),
)
