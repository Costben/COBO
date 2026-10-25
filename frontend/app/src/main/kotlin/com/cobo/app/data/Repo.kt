package com.cobo.app.data

import android.content.Context
import android.net.Uri
import com.cobo.app.App
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
    ): FeedPage {
        val cookie = cookie()
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
    suspend fun special(maxId: String? = null, force: Boolean = false): FeedPage {
        val uids = specialFollowUids(force)
        val kept = ArrayList<Status>()
        var cursor = maxId
        var next: String? = null
        var scanned = 0
        while (scanned < SPECIAL_SCAN_PAGES) {
            val page = statusFeed("https://m.weibo.cn/feed/friends", cursor, force)
            scanned++
            for (status in page.items) {
                val user = status.user ?: continue
                if (user.specialFollow || user.id in uids) kept.add(status)
            }
            next = page.sinceId
            if (next.isNullOrBlank() || kept.size >= SPECIAL_PAGE_SIZE) break
            cursor = next
        }
        if (kept.isEmpty() && uids.isNotEmpty()) {
            // 关注流里一条都没撞上：要么特别关注的人这几天没发博，要么服务端没给 special_follow
            // 标记。这时直接把这几个人的主页时间线拼起来，保证这一页不是空的。
            for (uid in uids.take(SPECIAL_DIRECT_USERS)) {
                val page = runCatching { container("107603$uid", force = force) }.getOrNull() ?: continue
                kept += page.items
            }
            kept.sortByDescending { it.id.toLongOrNull() ?: 0L }
            next = null
        }
        return FeedPage(kept, next)
    }

    @Volatile
    private var specialUids: Set<String> = emptySet()

    @Volatile
    private var specialUidsAt: Long = 0L

    /** 特别关注名单：containerid=231093_-_specialfollow 就是关注页那个「特别关注」入口。 */
    private suspend fun specialFollowUids(force: Boolean): Set<String> {
        val fresh = System.currentTimeMillis() - specialUidsAt < SPECIAL_UIDS_TTL_MS
        if (!force && fresh) return specialUids
        val ids = HashSet<String>()
        runCatching {
            val url = Api.url(
                "https://m.weibo.cn/api/container/getIndex",
                linkedMapOf<String, String?>("containerid" to "231093_-_specialfollow", "page" to "1"),
            )
            collectUserIds(Api.get(url, cookie(), force), ids)
        }
        specialUids = ids
        specialUidsAt = System.currentTimeMillis()
        return ids
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

    private suspend fun statusFeed(base: String, maxId: String?, force: Boolean): FeedPage {
        val params = LinkedHashMap<String, String?>()
        if (!maxId.isNullOrBlank()) params["max_id"] = maxId
        val root = Api.get(Api.url(base, params), cookie(), force)
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
