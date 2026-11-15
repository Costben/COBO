package com.cobo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 首页时间线的续看快照。
 *
 * 首页只在第一次进入时向服务端要一页，往下翻的几页全在内存里：进程一被杀就只剩第一页，
 * 而且列表从头开始。这里把每条流已经拉到的整份列表连游标一起按账号分文件存下来，
 * 下次冷启动直接填回去，滚动位置由 [Settings.feedScroll] 补上。
 *
 * 列表过期不当省钱：超过 [MaxAgeMillis] 的快照直接丢掉，免得打开就看到一周前的微博。
 */
class FeedSnapshotStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "feed-snapshots"))

    init {
        root.mkdirs()
    }

    fun load(owner: String, key: String): FeedSnapshot? {
        val file = file(owner, key)
        if (!file.exists()) return null
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (json.optInt("v") != FormatVersion) return null
        val savedAt = json.optLong("savedAt")
        if (savedAt <= 0 || System.currentTimeMillis() - savedAt > MaxAgeMillis) return null
        val arr = json.optJSONArray("items") ?: return null
        val items = buildList {
            for (i in 0 until arr.length()) {
                statusFromSnapshot(arr.optJSONObject(i))?.let(::add)
            }
        }
        if (items.isEmpty()) return null
        val itemIds = items.mapTo(HashSet()) { it.id }
        val gapsArr = json.optJSONArray("gaps")
        val seenGapTargets = HashSet<String>()
        val gaps = if (gapsArr != null) {
            buildList {
                for (i in 0 until gapsArr.length()) {
                    gapFromSnapshot(gapsArr.optJSONObject(i))
                        ?.takeIf { it.beforeStatusId in itemIds && seenGapTargets.add(it.beforeStatusId) }
                        ?.let(::add)
                }
            }
        } else {
            emptyList()
        }
        return FeedSnapshot(
            items = items,
            sinceId = json.opt("sinceId")?.takeIf { it != JSONObject.NULL }?.toString()?.ifBlank { null },
            end = json.optBoolean("end", false),
            savedAt = savedAt,
            gaps = gaps,
        )
    }

    fun save(
        owner: String,
        key: String,
        items: List<Status>,
        sinceId: String?,
        end: Boolean,
        gaps: List<FeedGap> = emptyList(),
    ) {
        if (items.isEmpty()) return
        val trimmed = items.size > MaxItems
        val keptItems = if (trimmed) items.take(MaxItems) else items
        val keptIds = keptItems.mapTo(HashSet()) { it.id }
        val keptGaps = gaps.filter { it.beforeStatusId in keptIds }
        val json = JSONObject()
            .put("v", FormatVersion)
            .put("savedAt", System.currentTimeMillis())
            .put("sinceId", if (trimmed) JSONObject.NULL else (sinceId ?: JSONObject.NULL))
            .put("end", if (trimmed) false else end)
            .put("items", JSONArray().apply { keptItems.forEach { put(statusToSnapshot(it)) } })
        if (keptGaps.isNotEmpty()) {
            json.put("gaps", JSONArray().apply { keptGaps.forEach { put(gapToSnapshot(it)) } })
        }
        runCatching {
            val file = file(owner, key)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString())
                tmp.delete()
            }
        }
    }

    /** 换账号、退出登录时整表清掉，别让下一个人看到上一个人的时间线。 */
    fun clear() {
        // 快照按账号分在子目录里，delete() 对非空目录直接返回 false；必须递归删。
        runCatching { root.listFiles()?.forEach { it.deleteRecursively() } }
    }

    private fun file(owner: String, key: String): File {
        val dir = File(root, owner.filter { it.isLetterOrDigit() }.ifBlank { GuestOwner })
            .apply { mkdirs() }
        val name = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "feed" }
        return File(dir, "$name.json")
    }

    private companion object {
        const val FormatVersion = 1

        /** 再往下翻也不存了：文件太大、写盘也慢，续看的位置到这儿已经够用。 */
        const val MaxItems = 500

        const val MaxAgeMillis = 7L * 24 * 60 * 60 * 1000

        const val GuestOwner = "guest"
    }
}

data class FeedGap(
    val id: String,
    val cursor: String,
    val beforeStatusId: String,
    val loadedCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

data class FeedSnapshot(
    val items: List<Status>,
    val sinceId: String?,
    val end: Boolean,
    val savedAt: Long,
    val gaps: List<FeedGap> = emptyList(),
)

private fun gapToSnapshot(g: FeedGap): JSONObject = JSONObject().apply {
    put("id", g.id)
    put("cursor", g.cursor)
    put("beforeStatusId", g.beforeStatusId)
    if (g.loadedCount > 0) put("loadedCount", g.loadedCount)
}

private fun gapFromSnapshot(o: JSONObject?): FeedGap? {
    if (o == null) return null
    val id = o.str("id")
    val cursor = o.str("cursor")
    val beforeStatusId = o.str("beforeStatusId")
    if (id.isBlank() || cursor.isBlank() || beforeStatusId.isBlank()) return null
    return FeedGap(
        id = id,
        cursor = cursor,
        beforeStatusId = beforeStatusId,
        loadedCount = o.int("loadedCount").coerceAtLeast(0),
    )
}

private fun userToSnapshot(u: User): JSONObject = JSONObject().apply {
    put("id", u.id)
    put("name", u.name)
    put("avatar", u.avatar)
    put("verified", u.verified)
    put("following", u.following)
    put("description", u.description)
    put("followers", u.followers)
    put("friends", u.friends)
    put("statuses", u.statuses)
    put("specialFollow", u.specialFollow)
    put("mbtype", u.mbtype)
    put("mbrank", u.mbrank)
    put("svip", u.svip)
}

private fun userFromSnapshot(o: JSONObject): User? {
    val id = o.str("id")
    if (id.isBlank()) return null
    return User(
        id = id,
        name = o.str("name"),
        avatar = o.str("avatar"),
        verified = o.optBoolean("verified"),
        following = o.optBoolean("following"),
        description = o.str("description"),
        followers = o.int("followers"),
        friends = o.int("friends"),
        statuses = o.int("statuses"),
        specialFollow = o.optBoolean("specialFollow"),
        mbtype = o.int("mbtype"),
        mbrank = o.int("mbrank"),
        svip = o.optBoolean("svip"),
    )
}

private fun picToSnapshot(p: Pic): JSONObject = JSONObject().apply {
    put("thumb", p.thumb)
    put("large", p.large)
    put("width", p.width)
    put("height", p.height)
    put("live", p.live)
}

private fun picFromSnapshot(o: JSONObject): Pic = Pic(
    thumb = o.str("thumb"),
    large = o.str("large").ifBlank { o.str("thumb") },
    width = o.int("width"),
    height = o.int("height"),
    live = o.str("live"),
)

private fun videoToSnapshot(v: VideoInfo): JSONObject = JSONObject().apply {
    put("title", v.title)
    put("cover", v.cover)
    put("url", v.url)
}

private fun bigPicToSnapshot(p: BigPicInfo): JSONObject = JSONObject().apply {
    put("url", p.url)
    put("scheme", p.scheme)
}

internal fun statusToSnapshot(s: Status): JSONObject = JSONObject().apply {
    put("id", s.id)
    put("mid", s.mid)
    put("text", s.textHtml)
    put("createdAt", s.createdAt)
    put("source", s.source)
    s.user?.let { put("user", userToSnapshot(it)) }
    if (s.pics.isNotEmpty()) {
        put("pics", JSONArray().apply { s.pics.forEach { put(picToSnapshot(it)) } })
    }
    s.video?.let { put("video", videoToSnapshot(it)) }
    s.bigPic?.let { put("bigPic", bigPicToSnapshot(it)) }
    s.retweeted?.let { put("retweeted", statusToSnapshot(it)) }
    put("reposts", s.reposts)
    put("comments", s.comments)
    put("attitudes", s.attitudes)
    put("liked", s.liked)
    put("region", s.region)
    put("editCount", s.editCount)
    put("isVote", s.isVote)
    put("voteId", s.voteId ?: JSONObject.NULL)
}

internal fun statusFromSnapshot(o: JSONObject?): Status? {
    if (o == null) return null
    val id = o.str("id")
    if (id.isBlank()) return null
    val pics = o.optJSONArray("pics")?.let { arr ->
        buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { picFromSnapshot(it) }?.let(::add)
            }
        }
    }.orEmpty()
    return Status(
        id = id,
        mid = o.str("mid").ifBlank { id },
        textHtml = o.str("text"),
        createdAt = o.str("createdAt"),
        source = o.str("source"),
        user = o.optJSONObject("user")?.let { userFromSnapshot(it) },
        pics = pics,
        video = o.optJSONObject("video")?.let {
            VideoInfo(title = it.str("title"), cover = it.str("cover"), url = it.str("url"))
        },
        bigPic = o.optJSONObject("bigPic")?.let {
            val url = it.str("url")
            if (url.isNotBlank()) BigPicInfo(url = url, scheme = it.str("scheme")) else null
        },
        retweeted = statusFromSnapshot(o.optJSONObject("retweeted")),
        reposts = o.int("reposts"),
        comments = o.int("comments"),
        attitudes = o.int("attitudes"),
        liked = o.optBoolean("liked"),
        region = o.str("region"),
        editCount = o.int("editCount"),
        isVote = o.optBoolean("isVote"),
        voteId = o.opt("voteId")?.takeIf { it != JSONObject.NULL }?.toString()?.ifBlank { null },
    )
}
