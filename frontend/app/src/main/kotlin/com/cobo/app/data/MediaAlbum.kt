package com.cobo.app.data

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
 * 将视频秒数格式化为 `m:ss` 或 `h:mm:ss`，<= 0 时返回空串。
 */
fun formatAlbumDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
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
                val dedupToken = "v:" + (resolvedVideo.fid.ifBlank { resolvedVideo.url })
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
                    val dedupToken = "v:" + (pic.videoFid.ifBlank { pic.videoUrl })
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
                    val dedupToken = "p:" + (pic.resolvedPicId().ifBlank { pic.large.ifBlank { thumb } })
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
