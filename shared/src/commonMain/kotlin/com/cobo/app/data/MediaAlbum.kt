package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject

/**
 * 个人主页独立「相册 / 视频」接口的分页结果。
 */
data class ProfileMediaPage(
    val items: List<MediaWallItem>,
    val nextCursor: String?,
)

/**
 * 相册墙筛选维度：全部媒体、仅图片（含实况图）或仅视频。
 */
enum class MediaWallFilter(val label: String) {
    All("全部"),
    Images("图片"),
    Videos("视频"),
    ;
}

/**
 * 相册墙网格中的单个媒体条目（单图、实况图或视频封面），保留所属微博以支持长按直达详情页。
 */
sealed interface MediaWallItem {
    val key: String
    val status: Status
    val thumbUrl: String
    val isVideo: Boolean
    val isLivePhoto: Boolean
    val durationSec: Int

    data class Photo(
        override val key: String,
        override val status: Status,
        val pic: Pic,
    ) : MediaWallItem {
        override val thumbUrl: String get() = pic.thumb.ifBlank { pic.large }
        override val isVideo: Boolean get() = false
        override val isLivePhoto: Boolean get() = pic.isLive
        override val durationSec: Int get() = 0
    }

    data class Video(
        override val key: String,
        override val status: Status,
        val video: VideoInfo,
    ) : MediaWallItem {
        override val thumbUrl: String get() = video.cover
        override val isVideo: Boolean get() = true
        override val isLivePhoto: Boolean get() = false
        override val durationSec: Int get() = video.durationSec
    }
}

/**
 * 规范化媒体地址用于跨分页去重：剥离随请求变化的 `Expires` / `ssig` 签名参数，
 * 对带 `fid` 的播放页地址保留视频 ID，对微博视频 CDN 直链提取稳定文件名。
 */
internal fun canonicalMediaUrl(url: String): String {
    val trimmed = url.trim().substringBefore('#')
    if (trimmed.isBlank()) return ""
    val fid = com.cobo.app.screen.extractWeiboVideoFid(trimmed)
    if (!fid.isNullOrBlank()) return "fid:$fid"
    val base = trimmed.substringBefore('?')
    val fileName = base.substringAfterLast('/')
    if (fileName.isNotBlank() && (
            base.contains("weibocdn.com", ignoreCase = true) ||
                base.contains("sinaimg.cn", ignoreCase = true)
            )
    ) {
        return "file:$fileName"
    }
    return base
}

internal fun canonicalVideoToken(fid: String, url: String, cover: String = "", fallback: String = ""): String {
    val cleanFid = fid.trim()
    if (cleanFid.isNotBlank()) return "v:$cleanFid"
    val urlKey = canonicalMediaUrl(url)
    if (urlKey.isNotBlank()) return "v:$urlKey"
    val coverKey = canonicalMediaUrl(cover)
    if (coverKey.isNotBlank()) return "vc:$coverKey"
    return "vk:$fallback"
}

internal fun mediaWallDedupToken(item: MediaWallItem): String = when (item) {
    is MediaWallItem.Photo -> {
        val pid = item.pic.resolvedPicId()
        if (pid.isNotBlank()) "p:$pid"
        else "p:" + canonicalMediaUrl(item.pic.large.ifBlank { item.thumbUrl })
    }
    is MediaWallItem.Video -> canonicalVideoToken(
        fid = item.video.fid,
        url = item.video.url,
        cover = item.video.cover,
        fallback = "${item.status.id}:${item.key}",
    )
}

/**
 * 将视频秒数格式化为 `m:ss` 或 `h:mm:ss`，<= 0 时返回空串。
 */
fun formatAlbumDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

/**
 * 从微博列表中提取相册墙媒体条目：
 * - 个人主页传 `includeRetweeted = false`（仅展示本人发布的图文与视频）；
 * - 收藏夹传 `includeRetweeted = true`（保留原博图片与视频）。
 */
fun extractMediaWallItems(
    statuses: List<Status>,
    includeRetweeted: Boolean,
    filter: MediaWallFilter = MediaWallFilter.All,
): List<MediaWallItem> {
    if (statuses.isEmpty()) return emptyList()
    val result = ArrayList<MediaWallItem>()
    val seenKeys = HashSet<String>()

    fun addUnique(item: MediaWallItem) {
        val uniqueKey = if (seenKeys.add(item.key)) {
            item.key
        } else {
            "${item.key}#${result.size}"
        }
        val finalItem = if (uniqueKey == item.key) item else when (item) {
            is MediaWallItem.Photo -> item.copy(key = uniqueKey)
            is MediaWallItem.Video -> item.copy(key = uniqueKey)
        }
        val matches = when (filter) {
            MediaWallFilter.All -> true
            MediaWallFilter.Images -> !finalItem.isVideo
            MediaWallFilter.Videos -> finalItem.isVideo
        }
        if (matches) result.add(finalItem)
    }

    statuses.forEachIndexed { statusIdx, status ->
        val ownerKey = status.id.ifBlank { "idx_$statusIdx" }
        val sources = buildList {
            add(status)
            if (includeRetweeted) {
                status.retweeted?.let(::add)
            }
        }
        val seenMediaInPost = HashSet<String>()
        sources.forEachIndexed { sourceIdx, src ->
            val srcId = src.id.ifBlank { "${ownerKey}_s$sourceIdx" }
            val statusVideo = src.video?.takeIf { it.url.isNotBlank() }
            if (statusVideo != null) {
                val resolvedVideo = if (statusVideo.statusId.isBlank() && src.id.isNotBlank()) {
                    statusVideo.copy(statusId = src.id)
                } else {
                    statusVideo
                }
                val dedupToken = canonicalVideoToken(resolvedVideo.fid, resolvedVideo.url, resolvedVideo.cover, srcId)
                if (seenMediaInPost.add(dedupToken)) {
                    addUnique(
                        MediaWallItem.Video(
                            key = "v_${ownerKey}_${sourceIdx}_$srcId",
                            status = status,
                            video = resolvedVideo,
                        ),
                    )
                }
            }

            src.pics.forEachIndexed { picIdx, pic ->
                if (pic.isVideo) {
                    val cover = pic.thumb.ifBlank { pic.large }
                    val dedupToken = canonicalVideoToken(pic.videoFid, pic.videoUrl, cover, "${srcId}_$picIdx")
                    if (seenMediaInPost.add(dedupToken)) {
                        addUnique(
                            MediaWallItem.Video(
                                key = "mv_${ownerKey}_${sourceIdx}_$picIdx",
                                status = status,
                                video = VideoInfo(
                                    title = pic.videoTitle,
                                    cover = cover,
                                    url = pic.videoUrl,
                                    durationSec = pic.durationSec,
                                    fid = pic.videoFid,
                                    statusId = pic.videoStatusId.ifBlank { src.id },
                                ),
                            ),
                        )
                    }
                } else {
                    val thumb = pic.thumb.ifBlank { pic.large }
                    val dedupToken = "p:" + (pic.resolvedPicId().ifBlank { canonicalMediaUrl(pic.large.ifBlank { thumb }) })
                    if (thumb.isNotBlank() && seenMediaInPost.add(dedupToken)) {
                        addUnique(
                            MediaWallItem.Photo(
                                key = "p_${ownerKey}_${sourceIdx}_$picIdx",
                                status = status,
                                pic = pic,
                            ),
                        )
                    }
                }
            }

            if (statusVideo == null && src.pics.isEmpty()) {
                val bigUrl = src.bigPic?.url.orEmpty().trim()
                if (bigUrl.isNotBlank() && seenMediaInPost.add("p:$bigUrl")) {
                    addUnique(
                        MediaWallItem.Photo(
                            key = "bp_${ownerKey}_${sourceIdx}_$srcId",
                            status = status,
                            pic = Pic(thumb = bigUrl, large = bigUrl),
                        ),
                    )
                }
            }
        }
    }
    return result
}

/**
 * 提取相册墙中所有图片用于看图页连续左右翻页，同时返回 `itemKey -> photoIndex` 映射。
 */
fun mediaWallPhotoGallery(items: List<MediaWallItem>): Pair<List<Pic>, Map<String, Int>> {
    val pics = ArrayList<Pic>()
    val indexByKey = HashMap<String, Int>()
    for (item in items) {
        if (item is MediaWallItem.Photo) {
            indexByKey[item.key] = pics.size
            pics.add(item.pic)
        }
    }
    return pics to indexByKey
}

private fun stubStatusForMid(mid: String): Status = Status(
    id = mid,
    mid = mid,
    textHtml = "",
    createdAt = "",
    source = "",
    user = null,
)

/**
 * 解析个人主页官方相册容器（`107803<uid>_-_photoall`）单页响应：
 * 同时兼容客户端 `api.weibo.cn/2/cardlist`（顶层 `cards`/`cardlistInfo`）与
 * 移动端 `m.weibo.cn/api/container/getSecond`（包裹在 `data` 内）。
 *
 * 每个 `card_type=47` 卡片带有一组 `pics[]`，其中内嵌了对应微博的 `mblog`（含 `pic_infos`
 * 实况图元信息），长按任意格即可直达原微博详情。
 */
internal fun parseProfilePhotoAllPage(
    root: JSONObject?,
    currentPage: Int,
    cursorPrefix: String = "pa",
): ProfileMediaPage {
    val container = root?.optJSONObject("data") ?: root ?: return ProfileMediaPage(emptyList(), null)
    val cards = container.optJSONArray("cards") ?: return ProfileMediaPage(emptyList(), null)
    val items = ArrayList<MediaWallItem>()
    val seen = HashSet<String>()

    fun addPicEntry(picObj: JSONObject) {
        val mblogObj = picObj.optJSONObject("mblog")
        val parsedStatus = mblogObj?.let { Status.from(it) }
        val mid = parsedStatus?.id?.ifBlank { parsedStatus.mid }
            ?: mblogObj?.str("id")?.ifBlank { mblogObj.str("mid") }
            ?: picObj.str("mid").ifBlank { picObj.str("mblogid") }
        val ownerStatus = parsedStatus ?: stubStatusForMid(mid)

        val picId = picObj.str("pic_id").ifBlank { picObj.str("pid") }
        val small = picObj.str("pic_small").ifBlank { picObj.str("pic_middle") }
        val middle = picObj.str("pic_middle").ifBlank { small }
        val big = picObj.str("pic_big")
            .ifBlank { picObj.str("pic_mw2000") }
            .ifBlank { picObj.str("pic_ori") }
            .ifBlank { middle }
            .ifBlank { small }
        val thumb = small.ifBlank { middle }.ifBlank { big }

        val matchedRich = if (picId.isNotBlank() && parsedStatus != null) {
            parsedStatus.pics.firstOrNull { it.resolvedPicId() == picId || it.pid == picId }
        } else {
            null
        }

        if (matchedRich != null && matchedRich.isVideo) {
            val dedup = "v:" + (matchedRich.videoFid.ifBlank { matchedRich.videoUrl })
            if (seen.add(dedup)) {
                items.add(
                    MediaWallItem.Video(
                        key = "pa_v_${mid}_${picId.ifBlank { items.size.toString() }}",
                        status = ownerStatus,
                        video = VideoInfo(
                            title = matchedRich.videoTitle,
                            cover = matchedRich.thumb.ifBlank { thumb },
                            url = matchedRich.videoUrl,
                            durationSec = matchedRich.durationSec,
                            fid = matchedRich.videoFid,
                            statusId = matchedRich.videoStatusId.ifBlank { ownerStatus.id },
                        ),
                    ),
                )
            }
            return
        }

        val resolvedThumb = matchedRich?.thumb?.ifBlank { thumb } ?: thumb
        val resolvedLarge = matchedRich?.large?.ifBlank { big } ?: big
        if (resolvedThumb.isBlank() && resolvedLarge.isBlank()) return
        val resolvedPic = matchedRich?.copy(
            thumb = resolvedThumb.ifBlank { resolvedLarge },
            large = resolvedLarge.ifBlank { resolvedThumb },
            pid = matchedRich.pid.ifBlank { picId },
        ) ?: Pic(
            thumb = resolvedThumb.ifBlank { resolvedLarge },
            large = resolvedLarge.ifBlank { resolvedThumb },
            live = picObj.str("video"),
            pid = picId,
        )
        val dedup = "p:" + (picId.ifBlank { resolvedPic.large.ifBlank { resolvedPic.thumb } })
        if (!seen.add(dedup)) return
        items.add(
            MediaWallItem.Photo(
                key = "pa_${mid}_${picId.ifBlank { items.size.toString() }}",
                status = ownerStatus,
                pic = resolvedPic,
            ),
        )
    }

    fun inspectCard(card: JSONObject) {
        val pics = card.optJSONArray("pics") ?: card.optJSONArray("pic_items")
        if (pics != null && pics.length() > 0) {
            for (i in 0 until pics.length()) {
                pics.optJSONObject(i)?.let(::addPicEntry)
            }
            return
        }
        val mblog = card.optJSONObject("mblog")?.takeUnless { it.isPromotedStatus() }
        if (mblog != null) {
            Status.from(mblog)?.let { st ->
                extractMediaWallItems(listOf(st), includeRetweeted = false, filter = MediaWallFilter.Images)
                    .forEach { entry ->
                        val dedup = when (entry) {
                            is MediaWallItem.Photo -> "p:" + (entry.pic.resolvedPicId().ifBlank { entry.pic.large.ifBlank { entry.thumbUrl } })
                            is MediaWallItem.Video -> "v:" + (entry.video.fid.ifBlank { entry.video.url })
                        }
                        if (seen.add(dedup)) items.add(entry)
                    }
            }
        }
    }

    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        inspectCard(card)
        val group: JSONArray? = card.optJSONArray("card_group")
        if (group != null) {
            for (j in 0 until group.length()) {
                group.optJSONObject(j)?.let(::inspectCard)
            }
        }
    }

    val info = container.optJSONObject("cardlistInfo")
    val nextSince = info?.str("since_id")?.trim()?.takeIf { it.isNotBlank() && it != "0" }
    val nextPage = info?.optInt("page", 0) ?: 0
    val nextCursor = when {
        items.isEmpty() -> null
        nextSince != null -> "${cursorPrefix}_s:$nextSince"
        nextPage > currentPage -> "${cursorPrefix}_p:$nextPage"
        else -> null
    }
    return ProfileMediaPage(items = items, nextCursor = nextCursor)
}

/**
 * 解析网页端相册墙接口（`weibo.com/ajax/profile/getImageWall`）单页响应。
 */
internal fun parseProfileImageWallPage(root: JSONObject?): ProfileMediaPage {
    val data = root?.optJSONObject("data") ?: return ProfileMediaPage(emptyList(), null)
    val list = data.optJSONArray("list") ?: return ProfileMediaPage(emptyList(), null)
    val items = ArrayList<MediaWallItem>(list.length())
    val seen = HashSet<String>()
    for (i in 0 until list.length()) {
        val entry = list.optJSONObject(i) ?: continue
        val pid = entry.str("pid")
        if (pid.isBlank() || !seen.add(pid)) continue
        val mid = entry.str("mid")
        val live = entry.str("video")
        val pic = Pic(
            thumb = "https://wx3.sinaimg.cn/orj480/$pid.jpg",
            large = "https://wx3.sinaimg.cn/large/$pid.jpg",
            live = live,
            pid = pid,
        )
        items.add(
            MediaWallItem.Photo(
                key = "iw_${mid}_$pid",
                status = stubStatusForMid(mid),
                pic = pic,
            ),
        )
    }
    val nextSince = data.str("since_id")
    val nextCursor = if (items.isNotEmpty() && nextSince.isNotBlank() && nextSince != "0") {
        "iw:$nextSince"
    } else {
        null
    }
    return ProfileMediaPage(items = items, nextCursor = nextCursor)
}

/**
 * 从个人主页专用视频分页结果中提取视频墙条目，自动过滤尾部的非本人推荐视频并去重。
 */
internal fun extractProfileVideoWallItems(uid: String, page: FeedPage): Pair<List<MediaWallItem>, String?> {
    val filtered = filterUserStatusesPage(uid, page)
    if (filtered.items.isEmpty()) return emptyList<MediaWallItem>() to null
    val out = ArrayList<MediaWallItem>()
    val seen = HashSet<String>()
    for (status in filtered.items) {
        val own = extractMediaWallItems(listOf(status), includeRetweeted = false, filter = MediaWallFilter.Videos)
        val picked = own.ifEmpty {
            extractMediaWallItems(listOf(status), includeRetweeted = true, filter = MediaWallFilter.Videos)
        }
        for (item in picked) {
            if (seen.add(mediaWallDedupToken(item))) out.add(item)
        }
    }
    return out to filtered.sinceId
}
