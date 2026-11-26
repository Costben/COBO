package com.cobo.app.data

import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.appFilesDir
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject


/**
 * 首页时间线的续看快照。
 *
 * 首页只在第一次进入时向服务端要一页，往下翻的几页全在内存里：进程一被杀就只剩第一页，
 * 而且列表从头开始。这里把每条流已经拉到的整份列表连游标一起按账号分文件存下来，
 * 下次冷启动直接填回去，滚动位置由 [Settings.feedScroll] 补上。
 *
 * 列表过期不当省钱：超过 [MaxAgeMillis] 的快照直接丢掉，免得打开就看到一周前的微博。
 */
class FeedSnapshotStore(private val root: PlatformFile) {
    constructor(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()) : this(appFilesDir().child("feed-snapshots"))

    init {
        root.mkdirs()
    }

    fun load(owner: String, key: String): FeedSnapshot? {
        val file = file(owner, key)
        if (!file.exists()) return null
        val json = runCatching { JSONObject(file.readTextOrNull().orEmpty()) }.getOrNull() ?: return null
        if (json.optInt("v") != FormatVersion) return null
        val savedAt = json.optLong("savedAt")
        if (savedAt <= 0 || currentTimeMillis() - savedAt > MaxAgeMillis) return null
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
        val lastReadBeforeStatusId = json.opt("lastReadBeforeStatusId")
            ?.takeIf { it != JSONObject.NULL }
            ?.toString()
            ?.takeIf { it in itemIds }
        return FeedSnapshot(
            items = items,
            sinceId = json.opt("sinceId")?.takeIf { it != JSONObject.NULL }?.toString()?.ifBlank { null },
            end = json.optBoolean("end", false),
            savedAt = savedAt,
            gaps = gaps,
            lastReadBeforeStatusId = lastReadBeforeStatusId,
        )
    }

    fun save(
        owner: String,
        key: String,
        items: List<Status>,
        sinceId: String?,
        end: Boolean,
        gaps: List<FeedGap> = emptyList(),
        lastReadBeforeStatusId: String? = null,
    ) {
        if (items.isEmpty()) return
        val trimmed = items.size > MaxItems
        val keptItems = if (trimmed) items.take(MaxItems) else items
        val keptIds = keptItems.mapTo(HashSet()) { it.id }
        val keptGaps = gaps.filter { it.beforeStatusId in keptIds }
        val keptMarker = lastReadBeforeStatusId?.takeIf { it in keptIds }
        val json = JSONObject()
            .put("v", FormatVersion)
            .put("savedAt", currentTimeMillis())
            .put("sinceId", if (trimmed) JSONObject.NULL else (sinceId ?: JSONObject.NULL))
            .put("end", if (trimmed) false else end)
            .put("items", JSONArray().apply { keptItems.forEach { put(statusToSnapshot(it)) } })
        if (keptGaps.isNotEmpty()) {
            json.put("gaps", JSONArray().apply { keptGaps.forEach { put(gapToSnapshot(it)) } })
        }
        if (keptMarker != null) {
            json.put("lastReadBeforeStatusId", keptMarker)
        }
        runCatching {
            val file = file(owner, key)
            file.writeTextAtomic(json.toString())
        }
    }

    /** 换账号、退出登录时整表清掉，别让下一个人看到上一个人的时间线。 */
    fun clear() {
        // 快照按账号分在子目录里，delete() 对非空目录直接返回 false；必须递归删。
        runCatching { root.listFiles().forEach { it.deleteRecursively() } }
    }

    private fun file(owner: String, key: String): PlatformFile {
        val dir = root.child(snapshotOwnerKey(owner))
            .apply { mkdirs() }
        val name = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "feed" }
        return dir.child("$name.json")
    }

    private companion object {
        const val FormatVersion = 1

        /** 再往下翻也不存了：文件太大、写盘也慢，续看的位置到这儿已经够用。 */
        const val MaxItems = 500

        const val MaxAgeMillis = 7L * 24 * 60 * 60 * 1000
    }
}

internal fun snapshotOwnerKey(owner: String): String =
    owner.filter { it.isLetterOrDigit() }.ifBlank { "guest" }

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
    val lastReadBeforeStatusId: String? = null,
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
    put("verifiedType", u.verifiedType)
    put("following", u.following)
    put("description", u.description)
    put("followers", u.followers)
    put("friends", u.friends)
    put("statuses", u.statuses)
    put("specialFollow", u.specialFollow)
    put("mbtype", u.mbtype)
    put("mbrank", u.mbrank)
    put("svip", u.svip)
    put("vvip", u.vvip)
}

private fun userFromSnapshot(o: JSONObject): User? {
    val id = o.str("id")
    if (id.isBlank()) return null
    return User(
        id = id,
        name = o.str("name"),
        avatar = o.str("avatar"),
        verified = o.optBoolean("verified"),
        verifiedType = o.optInt("verifiedType", -1),
        following = o.optBoolean("following"),
        description = o.str("description"),
        followers = o.int("followers"),
        friends = o.int("friends"),
        statuses = o.int("statuses"),
        specialFollow = o.optBoolean("specialFollow"),
        mbtype = o.int("mbtype"),
        mbrank = o.int("mbrank"),
        svip = o.optBoolean("svip"),
        vvip = o.optBoolean("vvip"),
    )
}

private fun picToSnapshot(p: Pic): JSONObject = JSONObject().apply {
    put("thumb", p.thumb)
    put("large", p.large)
    put("width", p.width)
    put("height", p.height)
    put("live", p.live)
    if (p.videoUrl.isNotBlank()) put("videoUrl", p.videoUrl)
    if (p.videoTitle.isNotBlank()) put("videoTitle", p.videoTitle)
    if (p.durationSec > 0) put("durationSec", p.durationSec)
    if (p.videoFid.isNotBlank()) put("videoFid", p.videoFid)
    if (p.videoStatusId.isNotBlank()) put("videoStatusId", p.videoStatusId)
}

private fun picFromSnapshot(o: JSONObject): Pic = Pic(
    thumb = o.str("thumb"),
    large = o.str("large").ifBlank { o.str("thumb") },
    width = o.int("width"),
    height = o.int("height"),
    live = o.str("live"),
    videoUrl = o.str("videoUrl"),
    videoTitle = o.str("videoTitle"),
    durationSec = o.int("durationSec"),
    videoFid = o.str("videoFid"),
    videoStatusId = o.str("videoStatusId"),
)

private fun videoToSnapshot(v: VideoInfo): JSONObject = JSONObject().apply {
    put("title", v.title)
    put("cover", v.cover)
    put("url", v.url)
    if (v.durationSec > 0) put("durationSec", v.durationSec)
    if (v.isLive) put("isLive", true)
    if (v.fid.isNotBlank()) put("fid", v.fid)
    if (v.statusId.isNotBlank()) put("statusId", v.statusId)
}

private fun bigPicToSnapshot(p: BigPicInfo): JSONObject = JSONObject().apply {
    put("url", p.url)
    put("scheme", p.scheme)
    if (p.title.isNotBlank()) put("title", p.title)
    if (p.subtitle.isNotBlank()) put("subtitle", p.subtitle)
    if (!p.isLarge) put("isLarge", false)
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
    put("favorited", s.favorited)
    put("region", s.region)
    put("editCount", s.editCount)
    put("isVote", s.isVote)
    put("voteId", s.voteId ?: JSONObject.NULL)
    if (s.isLongText) put("isLongText", true)
    s.foldedTextHtml?.let { put("foldedText", it) }
    s.visibility?.let { vis ->
        put("visibleType", vis.type)
        put("visibleLabel", vis.label)
        if (vis.listId.isNotBlank()) put("visibleListId", vis.listId)
    }
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
            VideoInfo(
                title = it.str("title"),
                cover = it.str("cover"),
                url = it.str("url"),
                durationSec = it.int("durationSec"),
                isLive = it.optBoolean("isLive", false),
                fid = it.str("fid"),
                statusId = it.str("statusId"),
            )
        },
        bigPic = o.optJSONObject("bigPic")?.let {
            val url = it.str("url")
            if (url.isNotBlank()) {
                BigPicInfo(
                    url = url,
                    scheme = it.str("scheme"),
                    title = it.str("title"),
                    subtitle = it.str("subtitle"),
                    isLarge = it.optBoolean("isLarge", true),
                )
            } else {
                null
            }
        },
        retweeted = statusFromSnapshot(o.optJSONObject("retweeted")),
        reposts = o.int("reposts"),
        comments = o.int("comments"),
        attitudes = o.int("attitudes"),
        liked = o.optBoolean("liked"),
        favorited = o.optBoolean("favorited"),
        region = o.str("region"),
        editCount = o.int("editCount"),
        isVote = o.optBoolean("isVote"),
        voteId = o.opt("voteId")?.takeIf { it != JSONObject.NULL }?.toString()?.ifBlank { null },
        isLongText = o.optBoolean("isLongText", false),
        foldedTextHtml = o.str("foldedText").ifBlank { null },
        visibility = if (o.has("visibleType")) {
            val type = o.int("visibleType")
            StatusVisibility(
                type = type,
                label = o.str("visibleLabel").ifBlank { defaultVisibilityLabel(type) },
                listId = o.str("visibleListId"),
            )
        } else {
            null
        },
    )
}
