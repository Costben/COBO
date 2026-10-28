package com.cobo.app.data

import android.content.Context
import android.net.Uri
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/** `/api/config` 下发的 st 会过期，过期后写操作一律 403（errno 100006 请求非法）。 */
private const val ST_TTL_MS = 10 * 60 * 1000L

/** 同一条微博两次点赞之间的最小间隔，低于这个值基本只会换来 20016。 */
private const val LIKE_COOLDOWN_MS = 1500L

/** 特别关注名单缓存时间：名单变动不频繁，翻页时不必每页重拉。 */
private const val SPECIAL_UIDS_TTL_MS = 5 * 60 * 1000L

/** 特别关注流单次最多回扫多少页关注流、凑够多少条就停。 */
private const val SPECIAL_SCAN_PAGES = 4
private const val SPECIAL_PAGE_SIZE = 12

/** 关注流里扫不到时，最多拼几个特别关注用户的主页时间线兜底。 */
private const val SPECIAL_DIRECT_USERS = 5

/** 一轮拼流最多花多久；到点就用已有结果返回，避免下拉头长时间挂着。 */
private const val SPECIAL_REFRESH_BUDGET_MS = 15_000L

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

/** 一个人这一轮拉到的最旧一条的 id。 */
private fun oldestStatusId(items: List<Status>?): Long? =
    items?.mapNotNull { it.id.toLongOrNull() }?.minOrNull()

class Repo(private val context: Context, private val store: Store) {

    @Volatile
    var session: Session? = store.session()
        private set

    @Volatile
    private var cachedConfig: Config? = null

    @Volatile
    private var configCookie: String? = null

    @Volatile
    private var cachedConfigAt: Long = 0L

    fun cookie(): String? = session?.cookie

    fun hasSession(): Boolean = !session?.cookie.isNullOrBlank()

    fun updateSession(cookie: String, uid: String) {
        App.cache.clear()
        store.saveSession(cookie, uid)
        session = store.session()
        cachedConfig = null
        configCookie = null
        cachedConfigAt = 0L
    }

    fun logout() {
        App.cache.clear()
        store.clearSession()
        session = null
        cachedConfig = null
        configCookie = null
        cachedConfigAt = 0L
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
        val cookie = cookie() ?: return Config("", "", false)
        return configFor(cookie, force)
    }

    suspend fun st(): String = config().st

    suspend fun loginWithCookie(cookie: String): Account? {
        val cfg = configFor(cookie, force = true)
        if (!cfg.loggedIn || cfg.uid.isBlank()) return null
        val user = runCatching { profileFor(cookie, cfg.uid, force = true) }.getOrNull()
        App.cache.clear()
        store.saveSession(cookie, cfg.uid)
        session = store.session()
        cachedConfig = cfg
        configCookie = cookie
        val account = Account(
            uid = cfg.uid,
            name = user?.name.orEmpty(),
            avatar = user?.avatar.orEmpty(),
            cookie = cookie,
        )
        store.saveAccount(account)
        return account
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
        val root = Api.get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie, force)
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
    suspend fun friends(maxId: String? = null, force: Boolean = false): FeedPage =
        statusFeed("https://m.weibo.cn/feed/friends", maxId, force)

    /** The "好友圈" feed: posts from mutual follows, same shape as /feed/friends. */
    suspend fun circle(maxId: String? = null, force: Boolean = false): FeedPage =
        statusFeed("https://m.weibo.cn/feed/circle", maxId, force)

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
        pickLocalFollowCookie(App.settings.localFollowUid, store.accounts(), cookie())

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
            val page = statusFeed("https://m.weibo.cn/feed/friends", cursor, force, cookie)
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
                collectUserIds(Api.get(url, cookie(), force), ids)
            }
            if (ids.isEmpty()) {
                runCatching {
                    val root = Api.getDesktop("https://weibo.com/ajax/feed/groupstimeline?list_id=$gid", cookie())
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
    private suspend fun specialGroupId(): String? {
        val root = Api.getDesktop("https://weibo.com/ajax/feed/allGroups", cookie())
        val groups = root.optJSONArray("groups") ?: return null
        for (i in 0 until groups.length()) {
            val items = groups.optJSONObject(i)?.optJSONArray("group") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                if (item.str("title") == "特别关注") {
                    item.str("gid").takeIf { it.isNotBlank() }?.let { return it }
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
    ): FeedPage {
        val params = LinkedHashMap<String, String?>()
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        val root = Api.get(Api.url(base, params), cookie, force)
        val data = root.optJSONObject("data")
        if (data == null) {
            if (root.optInt("ok", 1) == 1) return FeedPage(emptyList(), null)
            val message = root.str("msg", "加载失败")
            if (message.contains("还没有内容") || message.contains("暂无")) {
                return FeedPage(emptyList(), null)
            }
            throw ApiException(message)
        }
        val arr = data.optJSONArray("statuses") ?: JSONArray()
        val items = ArrayList<Status>(arr.length())
        for (i in 0 until arr.length()) {
            Status.from(arr.optJSONObject(i))?.let(items::add)
        }
        val next = data.str("max_id").ifBlank { data.str("since_id") }
        return FeedPage(items, next.ifBlank { null })
    }

    suspend fun hot(force: Boolean = false): List<HotItem> {
        val cookie = cookie()
        val cid = "106003type=25&t=3&disable_hot=1&filter_type=realtimehot"
        val root = Api.get(
            Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("containerid" to cid),
            ),
            cookie,
            force,
        )
        val cards = root.optJSONObject("data")?.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<HotItem>()
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i) ?: continue
            val group = card.optJSONArray("card_group") ?: continue
            for (j in 0 until group.length()) {
                val g = group.optJSONObject(j) ?: continue
                val title = g.str("desc").ifBlank { g.str("title_sub") }
                if (title.isBlank()) continue
                out.add(
                    HotItem(
                        title = title,
                        scheme = g.str("scheme"),
                        label = g.str("card_type_name"),
                        rank = out.size + 1,
                    )
                )
            }
        }
        return out
    }

    suspend fun search(query: String, page: Int = 1, force: Boolean = true): FeedPage {
        val cid = "100103type=1&q=$query"
        return container(
            cid,
            extra = if (page > 1) mapOf("page" to page.toString()) else emptyMap(),
            force = force,
        )
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
        val root = Api.get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie(), force)
        val items = parse(root)
        if (items.isEmpty() && root.optInt("ok", 1) != 1) {
            val message = root.str("msg", "搜索失败")
            if (message.contains("还没有内容") || message.contains("暂无")) return emptyList()
            throw ApiException(message)
        }
        return items
    }

    suspend fun detail(id: String, force: Boolean = false): Status? {
        val root = Api.get(
            Api.url("https://m.weibo.cn/statuses/show", mapOf("id" to id)),
            cookie(),
            force,
        )
        return Status.from(root.optJSONObject("data"))
    }

    suspend fun editHistory(mid: String, page: Int = 1): List<EditVersion> {
        if (mid.isBlank()) return emptyList()
        val root = Api.getDesktop(
            Api.url(
                "https://weibo.com/ajax/statuses/editHistory",
                mapOf("mid" to mid, "page" to page.coerceAtLeast(1).toString()),
            ),
            cookie(),
        )
        val arr = root.optJSONArray("statuses") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                EditVersion.from(arr.optJSONObject(i))?.let(::add)
            }
        }
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
            val root = Api.get(
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
        val root = Api.get(
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
        val root = Api.get(
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
        val root = Api.get(
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
        val cookie = cookie() ?: throw ApiException("未登录")
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

    private suspend fun postOk(url: String, form: Map<String, String>): Boolean {
        val root = postJson(url, form)
        if (root.optInt("ok", 0) != 1) throw ApiException(root.str("msg", "操作失败"))
        return true
    }

    suspend fun likeComment(id: String, liked: Boolean): Boolean = postOk(
        if (liked) "https://m.weibo.cn/comment/dislike" else "https://m.weibo.cn/comment/like",
        mapOf("id" to id),
    )

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
            postOk(
                if (liked) "https://m.weibo.cn/api/attitudes/destroy"
                else "https://m.weibo.cn/api/attitudes/create",
                if (liked) mapOf("id" to id) else mapOf("id" to id, "attitude" to "heart"),
            )
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

    suspend fun follow(uid: String, follow: Boolean): Boolean {
        val cookie = cookie() ?: throw ApiException("未登录")
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
        // The mobile endpoint takes the body as `content`; `comment` gets rejected as 100013 参数缺失.
        val ok = postOk(
            "https://m.weibo.cn/api/comments/create",
            mapOf("id" to id, "mid" to mid.ifBlank { id }, "content" to text),
        )
        App.cache.clear()
        return ok
    }

    /**
     * Answers a comment inside its own thread. `cid` is the comment being answered: a top-level
     * comment id for the first reply, or a reply id to nest under that particular reply. The
     * created reply comes back so the thread can show it without re-reading a lagging list.
     */
    suspend fun replyComment(id: String, mid: String, cid: String, text: String): Comment {
        val root = postJson(
            "https://m.weibo.cn/api/comments/reply",
            mapOf(
                "id" to id,
                "mid" to mid.ifBlank { id },
                "cid" to cid,
                "content" to text,
            ),
        )
        if (root.optInt("ok", 0) != 1) throw ApiException(root.str("msg", "回复失败"))
        App.cache.clear()
        return Comment.from(root.optJSONObject("data"), depth = 1)
            ?: throw ApiException("回复失败")
    }

    suspend fun repost(id: String, mid: String, text: String): Boolean {
        val ok = postOk(
            "https://m.weibo.cn/api/statuses/repost",
            mapOf("id" to id, "mid" to mid.ifBlank { id }, "content" to text),
        )
        App.cache.clear()
        return ok
    }

    suspend fun publish(text: String, picIds: List<String>): Boolean {
        val form = LinkedHashMap<String, String>()
        form["content"] = text
        form["visible"] = "0"
        if (picIds.isNotEmpty()) form["pic_ids"] = picIds.joinToString(",")
        val ok = postOk("https://m.weibo.cn/api/statuses/update", form)
        App.cache.clear()
        return ok
    }

    suspend fun uploadImage(uri: Uri): String {
        val cookie = cookie() ?: throw ApiException("未登录")
        val st = st()
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val mime = resolver.getType(uri) ?: "image/jpeg"
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
        val root = Api.get(
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
                )
            )
        }
        return out
    }

    /**
     * 聊天记录。接口给的是**最新在前**，[ChatPage.messages] 保持这个顺序，
     * 界面用 `reverseLayout = true` 铺，index 0 落在屏幕最下面。
     *
     * @param maxId 取更早的一屏（传当前最旧那条的 id）
     * @param sinceId 取更新的消息（轮询用）
     */
    suspend fun chat(
        uid: String,
        maxId: String? = null,
        sinceId: String? = null,
        count: Int = 20,
        force: Boolean = false,
    ): ChatPage {
        val url = Api.url(
            "https://m.weibo.cn/api/chat/list",
            mapOf(
                "uid" to uid,
                "count" to count.toString(),
                "max_id" to maxId,
                "since_id" to sinceId,
                "is_continuous" to if (sinceId != null) "1" else null,
            ),
        )
        val root = Api.get(url, cookie(), force)
        if (root.optInt("ok", 1) == 0) {
            // errno 要带出去：20003 是对方注销，界面据此不再给「重试」。
            throw ApiException(root.str("msg", "聊天记录加载失败"), root.str("errno"))
        }
        val data = root.optJSONObject("data")
            ?: return ChatPage(emptyList(), emptyMap(), "", 0)
        val arr = data.optJSONArray("msgs")
        val messages = ArrayList<ChatMessage>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                ChatMessage.from(arr.optJSONObject(i))?.let { messages.add(it) }
            }
        }
        val users = LinkedHashMap<String, User>()
        data.optJSONObject("users")?.let { o ->
            o.keys().forEach { key -> User.from(o.optJSONObject(key))?.let { users[key] = it } }
        }
        return ChatPage(
            messages = messages,
            users = users,
            title = data.str("title"),
            total = data.int("total_number"),
        )
    }

    /** 发一条纯文字私信。成功返回服务端那条消息，失败抛 [ApiException]。 */
    suspend fun sendChat(uid: String, text: String): ChatMessage? =
        sendChatForm(mapOf("uid" to uid, "content" to text))

    /**
     * 发图片私信。
     *
     * H5 是两步：先 `api/chat/upload`（multipart `file` + `tuid`）拿 `fids`，
     * 再把 `fids` 交给 `api/chat/send`（`media_type=1`，`content` 留空）。
     */
    suspend fun sendChatImage(uid: String, fids: String): ChatMessage? =
        sendChatForm(
            mapOf("uid" to uid, "content" to "", "media_type" to "1", "fids" to fids),
        )

    private suspend fun sendChatForm(form: Map<String, String>): ChatMessage? {
        val root = postJson("https://m.weibo.cn/api/chat/send", form)
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

    /** 上传私信图片，返回 `fids`。 */
    suspend fun uploadChatImage(uid: String, uri: Uri): String {
        val cookie = cookie() ?: throw ApiException("未登录")
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = "chat." + (mime.substringAfterLast('/', "jpg"))
        var st = st()
        var root = uploadChat(uid, bytes, name, mime, cookie, st)
        if (root.optInt("ok", 0) != 1 && isCsrfBody(root)) {
            st = config(force = true).st
            root = uploadChat(uid, bytes, name, mime, cookie, st)
        }
        val fids = root.optJSONObject("data")?.str("fids").orEmpty()
        if (fids.isBlank()) throw ApiException(root.str("msg", "图片上传失败"), root.str("errno"))
        return fids
    }

    private suspend fun uploadChat(
        uid: String,
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
        extra = mapOf("tuid" to uid),
    )

    suspend fun profileFor(cookie: String, uid: String, force: Boolean = false): User? {
        val url = Api.url(
            "https://m.weibo.cn/api/container/getIndex",
            mapOf("type" to "uid", "value" to uid),
        )
        fun pick(root: JSONObject): User? =
            User.from(root.optJSONObject("data")?.optJSONObject("userInfo"))

        pick(Api.get(url, cookie, force))?.let { return it }

        // 缓存里可能躺着早先的失败响应（`ok:0`、登录墙，或者没有 userInfo 的旧结构），
        // 那样主页会一直显示 0 关注 0 粉丝。命中空壳就强制回源一次，再试 containerid 形式。
        if (!force) {
            pick(Api.get(url, cookie, true))?.let { return it }
            val alt = Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                mapOf("containerid" to "100505$uid"),
            )
            return pick(Api.get(alt, cookie, true))
        }
        return null
    }

    suspend fun profile(uid: String, force: Boolean = false): User? {
        val cookie = cookie() ?: return null
        return profileFor(cookie, uid, force)
    }

    suspend fun ownUid(): String =
        session?.uid?.takeIf { it.isNotBlank() } ?: config().uid

    suspend fun userStatuses(uid: String, sinceId: String? = null, force: Boolean = false): FeedPage {
        return container("107603$uid", sinceId, force = force)
    }

    suspend fun favorites(force: Boolean = false): FeedPage = container("230267", force = force)

    suspend fun nearby(lat: String, lon: String, force: Boolean = false): FeedPage {
        return container(
            "231776",
            extra = mapOf("lat" to lat, "lon" to lon),
            force = force,
        )
    }

    fun clearLocalHistory() = store.clearHistory()
}
