package com.cobo.app.data

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun JSONObject.str(key: String, def: String = ""): String {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return v.toString()
}

fun JSONObject.int(key: String, def: Int = 0): Int {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return when (v) {
        is Number -> v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> v.toString().toIntOrNull() ?: def
    }
}

fun JSONObject.bool(key: String, def: Boolean = false): Boolean {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> def
    }
}

/**
 * m.weibo.cn 把「粉丝」这类可能上亿的计数按中文习惯压缩成展示字符串下发，
 * 例如 `"followers_count": "72.3万"` / `"1.2亿"` / `"26"`，直接走 [int] 会得到 0
 * （主页就变成「0 粉丝」）。这里按 万/亿/千 还原成数值。
 */
fun parseCount(raw: String?): Int? {
    val text = raw?.trim()?.replace(",", "")?.replace("+", "") ?: return null
    if (text.isEmpty()) return null
    val unit = when {
        text.endsWith("亿") -> 100_000_000.0
        text.endsWith("万") -> 10_000.0
        text.endsWith("千") -> 1_000.0
        text.endsWith("w", true) -> 10_000.0
        text.endsWith("k", true) -> 1_000.0
        else -> 1.0
    }
    val digits = text.dropLastWhile { it == '亿' || it == '万' || it == '千' || it == 'w' || it == 'W' || it == 'k' || it == 'K' }
    val value = digits.trim().toDoubleOrNull() ?: return null
    return (value * unit).toInt()
}

fun JSONObject.count(key: String, def: Int = 0): Int {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return when (v) {
        is Number -> v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> parseCount(v.toString()) ?: def
    }
}

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return try {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (_: Throwable) {
        html.replace(Regex("<[^>]+>"), "").trim()
    }
}

/** 私信正文里的 emoji 是 `<img alt="[可爱]">`，[stripHtml] 会把它变成 U+FFFC 方块，这里换成 alt 文本。 */
fun chatPlainText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val withAlt = html.replace(Regex("<img[^>]*alt=\"([^\"]*)\"[^>]*>")) { it.groupValues[1] }
    return stripHtml(withAlt).replace("\uFFFC", "").trim()
}

/**
 * 微博会员。接口给的四个字段：`mbtype`（0 从未开通，2 开通过但已过期，大于 2 为在期（实测 11 / 12，12 是年费））、
 * `mbrank`（会员等级，1 起步，常见 1–10+）、`svip`（1 = 超级会员）、`vvip`（1 = 黑金/VVIP，优先级高于 svip）。
 * 判定：`mbtype > 2 && mbrank > 0` 才算在期；不在期时 `svip` / `vvip` 一律不看。
 */
enum class Membership { None, Vip, Svip, Vvip }

/** 微博账号认证类型：未认证、金V（个人大V，verified_type 0 等）、蓝V（官方/机构，verified_type 1..8）。 */
enum class VerifiedKind { None, Gold, Blue }

/** 主页上的两种关系列表：关注了谁、谁在关注。 */
enum class RelationKind { Following, Followers }

data class User(
    val id: String,
    val name: String,
    val avatar: String,
    val verified: Boolean = false,
    val verifiedType: Int = -1,
    val following: Boolean = false,
    val description: String = "",
    val followers: Int = 0,
    val friends: Int = 0,
    val statuses: Int = 0,
    /** 主页封面原图；官方号常配好几张轮播，接口里用 `;` 拼在一起。 */
    val covers: List<String> = emptyList(),
    /** 当前登录者是否把这个用户设成了「特别关注」；m.weibo.cn 每条 status 的 user 都带这个字段。 */
    val specialFollow: Boolean = false,
    val mbtype: Int = 0,
    val mbrank: Int = 0,
    val svip: Boolean = false,
    val vvip: Boolean = false,
) {
    val membership: Membership
        get() = when {
            mbtype <= 2 || mbrank <= 0 -> Membership.None
            vvip -> Membership.Vvip
            svip -> Membership.Svip
            else -> Membership.Vip
        }

    val verifiedKind: VerifiedKind
        get() = when {
            !verified -> VerifiedKind.None
            verifiedType in 1..8 -> VerifiedKind.Blue
            else -> VerifiedKind.Gold
        }

    companion object {
        fun from(o: JSONObject?): User? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null
            val vipData = vipIconDataOf(o)
            val mbtype = o.int("mbtype").let { if (it > 0) it else vipData?.int("mbtype") ?: 0 }
            val mbrank = o.int("mbrank").let { if (it > 0) it else vipData?.int("mbrank") ?: 0 }
            val svip = o.int("svip") > 0 || (vipData?.int("svip") ?: 0) > 0
            val vvip = o.int("vvip") > 0 || (vipData?.int("vvip") ?: 0) > 0
            return User(
                id = id,
                name = o.str("screen_name").ifBlank { o.str("name") },
                // 没换过头像的账号，`avatar_hd` 会给 `.../default_avatar_male_180&690` 这种占位地址（404），
                // 直接取会一直显示灰圈；跳过占位项落到 `profile_image_url` 的默认头像。
                avatar = listOf(
                    o.str("avatar_hd"),
                    o.str("avatar_large"),
                    o.str("profile_image_url"),
                    o.str("avatar"),
                    o.str("round_avatar_large"),
                    o.str("round_profile_image_url"),
                ).firstOrNull { !isPlaceholderWeiboMediaUrl(it) }.orEmpty(),
                verified = o.bool("verified", false),
                verifiedType = o.optInt("verified_type", -1),
                following = o.bool("following", false),
                description = o.str("description"),
                followers = o.count("followers_count", o.count("followers_count_str")),
                friends = o.int("follow_count", o.int("friends_count", o.count("friends_count_str"))),
                statuses = o.count("statuses_count"),
                covers = coverUrls(o.str("cover_image_phone")),
                specialFollow = o.optBoolean("special_follow", false),
                mbtype = mbtype,
                mbrank = mbrank,
                svip = svip,
                vvip = vvip,
            )
        }
    }
}

private fun vipIconDataOf(o: JSONObject): JSONObject? {
    val arr = o.optJSONArray("icon_list") ?: return null
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        if (item.str("type") == "vip") return item.optJSONObject("data")
    }
    return null
}

private val CoverCrop = Regex("""^(https?://[^/]+)/crop\.[^/]+/(.+)$""")

/**
 * `cover_image_phone` 给的是 `crop.0.0.640.640.640/<pid>.jpg` 这种 640 方图，可能多张用 `;` 拼接。
 * 拆开后把裁剪段换成 `large`，拿到同一张的原图。
 */
internal fun coverUrls(raw: String): List<String> =
    raw.split(';')
        .map { it.trim() }
        .filter { it.startsWith("http") }
        .map { url -> CoverCrop.replace(url) { "${it.groupValues[1]}/large/${it.groupValues[2]}" } }
        .distinct()

data class Pic(
    val thumb: String,
    val large: String,
    val width: Int = 0,
    val height: Int = 0,
    /**
     * 实况图（`type=livephoto`）附带的短视频。静图、GIF 都是空串：GIF 在看图页仍按静图打开。
     * 地址可能是 `video.weibo.com/media/play?livephoto=` 这种播放页，真正的 mov 在参数里。
     */
    val live: String = "",
    /** 混排图集（`mix_media_info`）里的内嵌视频地址；非空时缩略图按九宫格占位，点开按视频播放。 */
    val videoUrl: String = "",
    val videoTitle: String = "",
    /** 视频时长（秒），> 0 时在混排缩略图右下角显示时长角标。 */
    val durationSec: Int = 0,
    /** 为 true 时看图页与保存直接沿用原始卡片/签名地址，不再把路径段改写成 `/large/`。 */
    val preserveUrl: Boolean = false,
) {
    val isLive: Boolean get() = live.isNotBlank()
    val isVideo: Boolean get() = videoUrl.isNotBlank()
}

// 实况视频地址：m.weibo.cn 放在 videoSrc，weibo.com 的 pic_infos 放在 video。
internal fun liveVideoUrl(pic: JSONObject): String {
    if (!pic.str("type").equals("livephoto", ignoreCase = true)) return ""
    val raw = pic.str("videoSrc").ifBlank { pic.str("video") }.ifBlank { pic.str("video_hd") }
    return unwrapLivePhoto(raw)
}

// 裸 mov 直接请求 403，要先走 video.weibo.com 的播放页拿签名地址。已签名的原样返回。
internal fun livePlayUrl(raw: String): String {
    if (raw.isBlank()) return ""
    if (raw.contains("livephoto=") || raw.contains("Expires=") || raw.contains("ssig=")) return raw
    val absolute = if (raw.startsWith("//")) "https:$raw" else raw
    if (!absolute.contains("sinaimg.cn") || !absolute.substringBefore('?').endsWith(".mov")) return absolute
    return "https://video.weibo.com/media/play?livephoto=" +
        java.net.URLEncoder.encode(absolute, "UTF-8")
}

private fun unwrapLivePhoto(raw: String): String = livePlayUrl(raw)

/**
 * 一条微博的配图。优先走 `pics[]`（m.weibo.cn 的卡片和详情都有），没有时退回
 * `pic_ids` + `pic_infos`（weibo.com 的 ajax 接口）。实况视频跟在对应那张静图上。
 */
internal fun picsOf(o: JSONObject): List<Pic> {
    val arr = o.optJSONArray("pics")
    if (arr != null && arr.length() > 0) {
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                picOf(p)?.let(::add)
            }
        }
        // `pics` 可能整组都是占位项（图片评论的客户端通道就是这样），此时继续看 `pic_ids` / `pic_infos`。
        if (parsed.isNotEmpty()) return parsed
    }
    val ids = o.optJSONArray("pic_ids")
    val infos = o.optJSONObject("pic_infos")
    if (ids != null && ids.length() > 0) {
        return buildList {
            for (i in 0 until ids.length()) {
                val id = ids.optString(i).orEmpty()
                if (id.isBlank()) continue
                val parsed = infos?.optJSONObject(id)?.let { picOf(it) }
                if (parsed != null) {
                    add(parsed)
                } else {
                    add(
                        Pic(
                            thumb = "https://wx1.sinaimg.cn/orj360/$id.jpg",
                            large = "https://wx1.sinaimg.cn/large/$id.jpg",
                        ),
                    )
                }
            }
        }
    }
    if (infos == null || infos.length() == 0) return emptyList()
    return buildList {
        val keys = infos.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            infos.optJSONObject(id)?.let { picOf(it) }?.let(::add)
        }
    }
}

private fun picOf(p: JSONObject): Pic? {
    val large = p.optJSONObject("large") ?: p.optJSONObject("largest") ?: p.optJSONObject("original") ?: p.optJSONObject("mw2000")
    val thumbObj = p.optJSONObject("bmiddle") ?: p.optJSONObject("thumbnail") ?: p.optJSONObject("orj360")
    val directUrl = (p.opt("url") as? String)?.trim().orEmpty()
    val rawPid = (p.opt("pid") as? String)?.trim().orEmpty()
    val pid = if (rawPid.equals("null", ignoreCase = true) || rawPid.equals("undefined", ignoreCase = true)) "" else rawPid
    val url = directUrl
        .ifBlank { (thumbObj?.opt("url") as? String)?.trim().orEmpty() }
        .ifBlank { (large?.opt("url") as? String)?.trim().orEmpty() }
        .ifBlank { if (pid.isNotBlank()) "https://wx1.sinaimg.cn/orj360/$pid.jpg" else "" }
    val videoInfo = if (p.str("type").equals("video", ignoreCase = true)) {
        p.optJSONObject("video_info")?.let { extractVideoInfo(it) } ?: run {
            val rawVideo = p.str("videoSrc").ifBlank { p.str("video") }.ifBlank { p.str("video_hd") }
            val normalized = if (rawVideo.startsWith("//")) "https:$rawVideo" else rawVideo
            if (normalized.isNotBlank()) {
                VideoInfo(
                    title = p.str("title"),
                    cover = url,
                    url = normalized,
                    durationSec = videoDurationSec(p, p.optJSONObject("media_info")),
                )
            } else {
                null
            }
        }
    } else {
        null
    }
    val thumbUrl = normalizeWeiboMediaUrl(url.ifBlank { videoInfo?.cover.orEmpty() })
    val largeCandidate = (large?.opt("url") as? String)?.trim()?.takeIf { it.isNotBlank() }
        ?: if (pid.isNotBlank() && directUrl.isBlank()) "https://wx1.sinaimg.cn/large/$pid.jpg" else com.cobo.app.ui.toLargeSinaImageUrl(thumbUrl)
    val largeUrl = normalizeWeiboMediaUrl(largeCandidate).ifBlank { thumbUrl }
    if (thumbUrl.isBlank() && large == null && videoInfo == null) return null
    val liveUrl = if (videoInfo == null) liveVideoUrl(p) else ""
    // 客户端通道的图片评论会在 `pics` 里塞一条占位项（`{"pid": null, "url": ".../orj360/&690"}`），
    // 真图在旁边的 `pic` 字段。占位地址没有真实文件名，当成配图只会渲染出一个空灰块。
    if (videoInfo == null && liveUrl.isBlank() &&
        isPlaceholderWeiboMediaUrl(thumbUrl) && isPlaceholderWeiboMediaUrl(largeUrl)
    ) {
        return null
    }
    val geo = large?.optJSONObject("geo") ?: p.optJSONObject("geo")
    return Pic(
        thumb = thumbUrl,
        large = largeUrl,
        width = geo?.int("width") ?: large?.int("width") ?: p.int("width"),
        height = geo?.int("height") ?: large?.int("height") ?: p.int("height"),
        live = liveUrl,
        videoUrl = videoInfo?.url.orEmpty(),
        videoTitle = videoInfo?.title.orEmpty(),
        durationSec = videoInfo?.durationSec ?: 0,
    )
}

/**
 * 微博图床的占位地址（如 `https://ss1.sinaimg.cn/orj360/&690`、`.../default_avatar_male_180&690`）
 * 没有真实文件名，请求一律 404：图片评论的 `pics` 用它凑数（真图在旁边的 `pic` 字段），
 * 没换过头像的账号用它冒充 `avatar_hd`。这类地址不能当成可加载的媒体。
 */
private fun isPlaceholderWeiboMediaUrl(url: String): Boolean {
    if (url.isBlank()) return true
    val name = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    return name.isBlank() || name.contains('&') || name.equals("null", ignoreCase = true)
}

/**
 * 解析混排图集 `mix_media_info.items`（同时包含图片与视频的九宫格/多图微博）。
 * 返回 `null` 表示该条微博未携带 `mix_media_info`，交由传统 `pics` + `page_info` 流程处理。
 */
internal fun mixMediaPicsOf(o: JSONObject): List<Pic>? {
    val items = o.optJSONObject("mix_media_info")?.optJSONArray("items") ?: return null
    if (items.length() == 0) return null
    val out = ArrayList<Pic>(items.length())
    for (i in 0 until items.length()) {
        val entry = items.optJSONObject(i) ?: continue
        val type = entry.str("type")
        val data = entry.optJSONObject("data") ?: continue
        when {
            type.equals("video", ignoreCase = true) -> videoGridPicOf(data)?.let(out::add)
            else -> picOf(data)?.let(out::add)
        }
    }
    return out.takeIf { it.isNotEmpty() }
}

private fun videoGridPicOf(data: JSONObject): Pic? {
    val info = extractVideoInfo(data) ?: return null
    val media = data.optJSONObject("media_info")
    val pagePic = data.optJSONObject("page_pic")
    val picBig = media?.optJSONObject("pic_info")?.optJSONObject("pic_big")
    val w = pagePic?.int("width")?.takeIf { it > 0 }
        ?: picBig?.int("width")?.takeIf { it > 0 }
        ?: media?.int("video_width") ?: 0
    val h = pagePic?.int("height")?.takeIf { it > 0 }
        ?: picBig?.int("height")?.takeIf { it > 0 }
        ?: media?.int("video_height") ?: 0
    return Pic(
        thumb = info.cover,
        large = info.cover,
        width = w,
        height = h,
        videoUrl = info.url,
        videoTitle = info.title,
        durationSec = info.durationSec,
    )
}

internal fun isLivePageInfo(page: JSONObject): Boolean {
    val type = page.str("type").lowercase()
    val objType = page.str("object_type").lowercase()
    if (type == "live" || objType == "live" || objType == "wblive" || objType == "chatroom") return true
    val media = page.optJSONObject("media_info")
    if (media != null && (
            media.str("live_id").isNotBlank() ||
                media.has("live_status") ||
                media.optInt("is_live", 0) == 1 ||
                media.optBoolean("is_live", false)
            )
    ) {
        return true
    }
    val urlCandidates = listOf(
        page.str("page_url"),
        page.str("scheme"),
        page.optJSONObject("page_pic")?.str("scheme").orEmpty(),
        media?.str("open_scheme").orEmpty(),
        media?.str("stream_url").orEmpty(),
        media?.str("h5_url").orEmpty(),
    )
    return urlCandidates.any { it.isNotBlank() && com.cobo.app.ui.isWeiboLiveLink(it) }
}

internal fun extractVideoInfo(page: JSONObject, fallbackPics: List<Pic> = emptyList()): VideoInfo? {
    val media = page.optJSONObject("media_info")
    val urlsObj = page.optJSONObject("urls")
    val livePage = isLivePageInfo(page)
    val liveStatus = media?.optInt("live_status", -1)?.takeIf { it >= 0 }
        ?: media?.optInt("real_status", -1)?.takeIf { it >= 0 }
        ?: page.optInt("live_status", -1).takeIf { it >= 0 }
        ?: media?.optInt("status", -1)?.takeIf { it >= 0 }
        ?: -1
    val liveId = if (livePage) {
        media?.str("live_id")?.takeIf { it.isNotBlank() }
            ?: page.str("object_id").takeIf { it.contains(":") && !it.startsWith("1034:") }
            ?: listOf(
                page.str("page_url"),
                page.str("scheme"),
                page.optJSONObject("page_pic")?.str("scheme").orEmpty(),
                media?.str("open_scheme").orEmpty(),
                media?.str("stream_url").orEmpty(),
                media?.str("h5_url").orEmpty(),
            ).firstNotNullOfOrNull { candidate ->
                candidate.takeIf { it.isNotBlank() }?.let { com.cobo.app.ui.extractWeiboLiveId(it) }
            }
    } else {
        null
    }
    val liveIsReplay = livePage && liveStatus == 3
    val directUrl = if (livePage) {
        if (liveIsReplay) {
            media?.str("replay_hd").orEmpty()
                .ifBlank { media?.str("replay_ld").orEmpty() }
                .ifBlank { firstPlaybackUrl(media?.optJSONArray("playback_list")) }
                .ifBlank { media?.str("stream_url_hd").orEmpty() }
                .ifBlank { media?.str("stream_url").orEmpty() }
        } else {
            ""
        }
    } else {
        media?.str("stream_url_hd")
            ?.ifBlank { media.str("stream_url") }
            ?.ifBlank { media.str("mp4_720p_mp4") }
            ?.ifBlank { media.str("mp4_hd_url") }
            ?.ifBlank { media.str("mp4_sd_url") }
            .orEmpty()
            .ifBlank { firstPlaybackUrl(media?.optJSONArray("playback_list")) }
            .ifBlank { urlsObj?.str("mp4_720p_mp4").orEmpty() }
            .ifBlank { urlsObj?.str("mp4_hd_mp4").orEmpty() }
            .ifBlank { urlsObj?.str("mp4_ld_mp4").orEmpty() }
            .ifBlank { urlsObj?.str("hevc_mp4_hd").orEmpty() }
            .ifBlank { urlsObj?.str("stream_url_hd").orEmpty() }
            .ifBlank { urlsObj?.str("stream_url").orEmpty() }
    }
    val fallbackLiveStream = if (livePage && !liveIsReplay) {
        media?.str("live_hd").orEmpty()
            .ifBlank { media?.str("live_ld").orEmpty() }
            .ifBlank { media?.str("stream_url_hd").orEmpty() }
            .ifBlank { media?.str("stream_url").orEmpty() }
    } else {
        ""
    }
    val fallbackPageUrl = if (livePage && liveId != null) {
        "https://weibo.com/l/wblive/m/show/$liveId"
    } else {
        media?.str("h5_url").orEmpty()
            .ifBlank { page.str("page_url") }
            .ifBlank { page.optJSONObject("page_pic")?.str("scheme").orEmpty() }
            .ifBlank { media?.str("open_scheme").orEmpty() }
            .ifBlank {
                page.str("object_id").takeIf { it.isNotBlank() }
                    ?.let { "https://video.weibo.com/show?fid=$it" }
                    .orEmpty()
            }
    }
    val resolvedUrl = directUrl.ifBlank {
        if (livePage && liveId != null) {
            "https://weibo.com/l/wblive/m/show/$liveId"
        } else {
            val unwrapped = com.cobo.app.ui.unwrapWeiboLink(fallbackPageUrl)
            com.cobo.app.ui.resolveWeiboLink(unwrapped)
                ?: unwrapped.ifBlank { fallbackLiveStream }
        }
    }
    if (resolvedUrl.isBlank()) return null
    val title = page.str("page_title")
        .ifBlank { page.str("title") }
        .ifBlank { page.str("content1") }
        .ifBlank { media?.str("kol_title").orEmpty() }
        .ifBlank { media?.str("next_title").orEmpty() }
    return VideoInfo(
        title = title,
        cover = videoCoverUrl(page, media, fallbackPics),
        url = resolvedUrl,
        durationSec = if (livePage && !liveIsReplay) 0 else videoDurationSec(page, media),
        isLive = livePage && !liveIsReplay,
    )
}

private fun videoDurationSec(page: JSONObject, media: JSONObject?): Int {
    val candidates = listOf(
        media?.optDouble("duration", 0.0) ?: 0.0,
        media?.optDouble("video_duration", 0.0) ?: 0.0,
        page.optDouble("duration", 0.0),
        page.optDouble("video_duration", 0.0),
    )
    val raw = candidates.firstOrNull { !it.isNaN() && it > 0.0 } ?: return 0
    return raw.roundToInt().coerceAtLeast(1)
}

data class VideoInfo(
    val title: String,
    val cover: String,
    val url: String,
    val durationSec: Int = 0,
    val isLive: Boolean = false,
)

data class BigPicInfo(
    val url: String,
    val scheme: String = "",
    val title: String = "",
    val subtitle: String = "",
    /** `true` 表示需要撑满宽度的预览大图卡片（`bigPic`/`wbox`/`card_type=107`）；`false` 表示普通链接/微博小图卡片。 */
    val isLarge: Boolean = true,
)

data class VoteOption(
    val id: String,
    val title: String,
    val pic: String = "",
    val partNum: String = "",
    val partRatio: Float = 0f,
    val selected: Boolean = false,
) {
    /** 保留一位小数，整数比例不带 `.0`。 */
    val ratioLabel: String
        get() {
            val tenths = (partRatio * 10).roundToInt()
            return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
        }
}

data class VoteInfo(
    val id: String,
    val title: String,
    val sk: String = "",
    val status: Int = 1,
    val isMultiSelect: Boolean = false,
    val maxSelect: Int = 1,
    val endTimeStr: String = "",
    val partNum: Int = 0,
    val parted: Boolean = false,
    val options: List<VoteOption> = emptyList(),
    val author: User? = null,
) {
    val isExpired: Boolean get() = status == 0

    /** 接口的截止文案自带「截止时间」前缀；其它形式（如剩余时间）原样展示。 */
    val deadlineLabel: String
        get() = endTimeStr.trim().let {
            if (it.startsWith("截止时间")) "${it.removePrefix("截止时间").trim()} 截止" else it
        }
}

fun extractVoteId(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val regex = Regex("""vote_id=([0-9a-zA-Z_\-]+)""")
    val match = regex.find(text)
    if (match != null) return match.groupValues[1]
    val pageIdRegex = Regex("""231716([0-9a-zA-Z_\-]+)""")
    val match2 = pageIdRegex.find(text)
    if (match2 != null) return match2.groupValues[1]
    return null
}

fun extractVoteIdFromUrlStruct(arr: JSONArray?): String? {
    if (arr == null) return null
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        for (field in listOf("ori_url", "long_url", "h5_target_url", "page_id")) {
            val v = item.str(field)
            val vid = extractVoteId(v)
            if (vid != null) return vid
        }
    }
    return null
}

fun parseVoteInfoJson(voteId: String, sk: String, vi: JSONObject, fallbackAuthor: User? = null): VoteInfo {
    val authorObj = vi.optJSONObject("launch_userinfo")
    val author = if (authorObj != null) {
        User(
            id = authorObj.str("uid"),
            name = authorObj.str("name"),
            avatar = authorObj.str("avatar_large").ifBlank { authorObj.str("avatar") },
            svip = authorObj.str("logo").contains("gold") || authorObj.str("logo").contains("vip"),
        )
    } else fallbackAuthor

    val options = ArrayList<VoteOption>()
    val optArr = vi.optJSONArray("option_list") ?: vi.optJSONArray("vote_list")
    if (optArr != null) {
        for (i in 0 until optArr.length()) {
            val opt = optArr.optJSONObject(i) ?: continue
            val ratio = opt.optDouble("part_ratio", 0.0).toFloat()
            val normalizedRatio = if (ratio in 0.0f..1.0f) ratio * 100f else ratio
            options.add(
                VoteOption(
                    id = opt.str("id"),
                    title = opt.str("title").ifBlank { opt.str("content") },
                    pic = opt.str("pic"),
                    partNum = opt.str("part_num"),
                    partRatio = normalizedRatio,
                    selected = opt.int("selected") > 0,
                )
            )
        }
    }

    val limited = vi.int("limited", vi.int("maximum", 1))
    return VoteInfo(
        id = vi.str("id", voteId),
        title = vi.str("title").ifBlank { vi.str("content") },
        sk = sk,
        status = vi.int("status", vi.int("state", 1)),
        isMultiSelect = limited > 1 || vi.int("is_multi_select") > 0,
        maxSelect = limited,
        endTimeStr = vi.str("end_time_str").ifBlank { vi.str("show_str") },
        partNum = vi.int("part_num", vi.int("part_info")),
        parted = vi.int("parted") > 0,
        options = options,
        author = author,
    )
}

/**
 * 微博发布/可见范围。
 * 对应接口 `visible: { type, list_id }` 与非公开条目的 `title.text`：
 * `0` 公开、`1` 仅自己可见、`2` 密友可见、`3` 指定分组可见、`6` 仅好友可见、`10` 仅粉丝可见。
 */
data class StatusVisibility(
    val type: Int,
    val label: String,
    val listId: String = "",
) {
    val isPublic: Boolean get() = type == 0
}

private fun isVisibilityTitle(title: String): Boolean {
    return title == "公开" ||
        title == "好友圈" ||
        title == "私密" ||
        title.contains("可见") ||
        title.contains("好友") ||
        title.contains("自己") ||
        title.contains("粉丝") ||
        title.contains("密友") ||
        title.contains("分组")
}

internal fun defaultVisibilityLabel(type: Int, hint: String = ""): String = when (type) {
    0 -> "公开"
    1 -> hint.ifBlank { "仅自己可见" }
    2 -> hint.ifBlank { "密友可见" }
    3 -> hint.ifBlank { "指定分组可见" }
    6 -> hint.ifBlank { "仅好友可见" }
    10 -> hint.ifBlank { "仅粉丝可见" }
    else -> hint.ifBlank { "部分可见" }
}

internal fun parseStatusVisibility(o: JSONObject): StatusVisibility? {
    val visObj = o.optJSONObject("visible")
    val rawTitle = o.optJSONObject("title")?.str("text")?.trim().orEmpty()
    val visibilityTitle = rawTitle.takeIf { it.isNotBlank() && isVisibilityTitle(it) }.orEmpty()

    if (visObj == null) {
        return when (visibilityTitle) {
            "公开" -> StatusVisibility(type = 0, label = "公开")
            "仅自己可见", "私密" -> StatusVisibility(type = 1, label = "仅自己可见")
            "仅好友可见", "好友圈" -> StatusVisibility(type = 6, label = visibilityTitle)
            else -> if (visibilityTitle.isNotBlank()) StatusVisibility(type = -1, label = visibilityTitle) else null
        }
    }

    val type = visObj.int("type", 0)
    val listId = visObj.str("list_idstr").ifBlank { visObj.str("list_id") }
        .takeIf { it.isNotBlank() && it != "0" }
        .orEmpty()
    return StatusVisibility(
        type = type,
        label = defaultVisibilityLabel(type, visibilityTitle),
        listId = listId,
    )
}

data class Status(
    val id: String,
    val mid: String,
    val textHtml: String,
    val createdAt: String,
    val source: String,
    val user: User?,
    val pics: List<Pic> = emptyList(),
    val video: VideoInfo? = null,
    val bigPic: BigPicInfo? = null,
    val retweeted: Status? = null,
    val reposts: Int = 0,
    val comments: Int = 0,
    val attitudes: Int = 0,
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val region: String = "",
    val editCount: Int = 0,
    val isVote: Boolean = false,
    val voteId: String? = null,
    val visibility: StatusVisibility? = null,
    /** 服务端标记的长微博。信息流按官方 140 字规则折叠时以它为准。 */
    val isLongText: Boolean = false,
    /**
     * 长微博在信息流里的折叠版正文（官方在 280 个半角宽度处截断）。
     * 只有拿得到全文时才有值；拿不到时 [textHtml] 本身就是服务端截断过的版本。
     */
    val foldedTextHtml: String? = null,
) {
    val plainText: String get() = stripHtml(textHtml)

    companion object {
        fun from(o: JSONObject?): Status? = parse(o, 0)

        private fun parse(o: JSONObject?, depth: Int): Status? {
            if (o == null) return null
            val id = o.str("idstr").ifBlank { o.str("id") }.ifBlank { o.str("mid") }
            if (id.isBlank()) return null

            val mixPics = mixMediaPicsOf(o)
            var pics = mixPics ?: picsOf(o)
            if (pics.isEmpty() && mixPics == null) {
                val thumbSingle = normalizeWeiboMediaUrl(
                    (o.opt("bmiddle_pic") as? String)
                        ?: (o.opt("thumbnail_pic") as? String)
                        ?: (o.opt("original_pic") as? String)
                        ?: "",
                )
                val largeSingle = normalizeWeiboMediaUrl((o.opt("original_pic") as? String).orEmpty())
                    .ifBlank { thumbSingle }
                if (thumbSingle.isNotBlank()) {
                    pics = listOf(Pic(thumb = thumbSingle, large = largeSingle))
                }
            }

            var video: VideoInfo? = null
            val page = o.optJSONObject("page_info")
            if (mixPics != null) {
                // 混排仅含一条视频时仍按普通单视频大卡呈现；多条（含图片+视频混排）全部进缩略图网格。
                if (mixPics.size == 1 && mixPics[0].isVideo) {
                    val only = mixPics[0]
                    video = VideoInfo(
                        title = only.videoTitle,
                        cover = only.large.ifBlank { only.thumb },
                        url = only.videoUrl,
                        durationSec = only.durationSec,
                    )
                    pics = emptyList()
                }
            } else if (page != null && (page.str("type") == "video" || page.str("object_type") == "video" || isLivePageInfo(page))) {
                // m.weibo.cn 把视频标成 type=video；weibo.com 网页版（关注分组那条流）给的是
                // type=11 + object_type=video，只认 type 会把视频当成纯文字。
                video = extractVideoInfo(page, pics)
            } else if (page != null && pics.isEmpty()) {
                val pageUrlRaw = page.str("page_url")
                    .ifBlank { page.optJSONObject("page_pic")?.str("scheme").orEmpty() }
                    .ifBlank { page.str("scheme") }
                val pageUrl = com.cobo.app.ui.unwrapWeiboLink(pageUrlRaw)
                val resolvedTarget = com.cobo.app.ui.resolveWeiboLink(pageUrl) ?: pageUrl
                val isLiveTarget = com.cobo.app.ui.isWeiboLiveLink(resolvedTarget)
                if (isLiveTarget || com.cobo.app.ui.isVideoLink(resolvedTarget)) {
                    video = VideoInfo(
                        title = page.str("page_title").ifBlank { page.str("content1") },
                        cover = videoCoverUrl(page, page.optJSONObject("media_info"), emptyList()),
                        url = resolvedTarget,
                        isLive = isLiveTarget,
                    )
                }
            }

            val retweetedObj = o.optJSONObject("retweeted_status")
            var retweeted = if (depth < 2) parse(retweetedObj, depth + 1) else null

            val longTextObj = o.optJSONObject("longText")
            val longContent = longTextObj?.str("longTextContent")?.takeIf { it.isNotBlank() }
            val longTextFlag = o.optBoolean("isLongText") || o.optBoolean("truncated")
            val serverText = o.str("text")
            // 服务端自己截断的长文尾部带「…展开」标记；正文一旦补上自己的入口，这个尾巴要先去干净。
            val rawText = longContent
                ?: if (longTextFlag) stripServerTruncationMarker(serverText) else serverText
            val mergedUrlStruct = mergeJsonArrays(
                o.optJSONArray("url_struct"),
                longTextObj?.optJSONArray("url_struct"),
            )
            val mergedTopicStruct = mergeJsonArrays(
                o.optJSONArray("topic_struct"),
                longTextObj?.optJSONArray("topic_struct"),
            )

            var voteId: String? = null
            if (page != null) {
                val cardInfo = page.optJSONObject("card_info")
                val voteObj = cardInfo?.optJSONObject("vote_object")
                voteId = voteObj?.str("id")?.takeIf { it.isNotBlank() }
                    ?: extractVoteId(page.str("page_url"))
                    ?: extractVoteId(page.str("page_id"))
                    ?: extractVoteId(page.str("object_id"))
                    ?: extractVoteId(page.optJSONObject("page_pic")?.str("scheme"))
            }
            if (voteId == null) {
                voteId = extractVoteIdFromUrlStruct(mergedUrlStruct)
            }
            if (voteId == null) {
                voteId = extractVoteId(o.str("text"))
            }

            // 注意：page_info.type == "23" 在微博里也是超话/网页等通用 H5 卡片类型，不能单独当作投票，
            // 否则所有带超话卡片的微博都会误显示「暂未获取到投票详情」。
            var isVote = o.int("is_vote") == 1 ||
                page?.str("object_type") == "hudongvote" ||
                page?.optJSONObject("card_info")?.optJSONObject("vote_object") != null ||
                voteId != null

            var bigPic: BigPicInfo? = extractBigPicInfo(
                o = o,
                page = page,
                pics = pics,
                hasVideo = video != null,
                isVote = isVote,
                urlStructOverride = mergedUrlStruct,
            )

            // 微博转发带视频的原博时，接口常把原博的 page_info 挂在外层转发体上而 retweeted_status 里没有；
            // 转发本身不能附带视频，把被拉出来的外层视频（及大图卡片）回嵌进被转发正文，避免浮在转发框外面。
            if (retweeted != null && (video != null || bigPic != null)) {
                val outerOwnsMedia = outerOwnsPageMedia(rawText, page, retweetedObj)
                if (!outerOwnsMedia) {
                    if (video != null) {
                        if (retweeted.video == null && retweeted.pics.none { it.isVideo }) {
                            retweeted = retweeted.copy(video = video)
                        }
                        video = null
                    }
                    if (bigPic != null) {
                        if (retweeted.bigPic == null) {
                            retweeted = retweeted.copy(bigPic = bigPic)
                        }
                        bigPic = null
                    }
                }
            }

            // 投票后自动转发（「我投给了“...”」）或转发带投票的原博时，接口常在外层转发体与
            // retweeted_status 上同时挂出同一个投票（或只把投票卡片挂在外层），统一归并到被转发原博，
            // 避免外层与转发框内重复渲染两张投票卡片。
            if (retweeted != null && isVote) {
                val sameOrSharedVote = retweeted.isVote &&
                    (voteId == null || retweeted.voteId == null || voteId == retweeted.voteId)
                val hoistedVote = !retweeted.isVote && !outerOwnsPageMedia(rawText, page, retweetedObj)
                if (sameOrSharedVote || hoistedVote) {
                    retweeted = retweeted.copy(
                        isVote = true,
                        voteId = retweeted.voteId ?: voteId,
                    )
                    isVote = false
                    voteId = null
                }
            }

            // 兼容同时返回 pics 与单条 page_info 视频的接口形态：把视频作为封面缩略图拼进网格。
            if (mixPics == null && pics.isNotEmpty() && video != null && video.url.isNotBlank() && pics.none { it.isVideo }) {
                val cover = video.cover.ifBlank { pics.first().thumb }
                pics = pics + Pic(
                    thumb = cover,
                    large = cover,
                    videoUrl = video.url,
                    videoTitle = video.title,
                    durationSec = video.durationSec,
                )
                video = null
            }

            val htmlTextBase = formatContainerTextToHtml(
                rawText,
                mergedUrlStruct,
                mergedTopicStruct,
            )
            val truncatedWithoutLink = longTextFlag &&
                longContent == null &&
                !htmlTextBase.contains("全文")
            val htmlText = if (truncatedWithoutLink) {
                "$htmlTextBase...<a href=\"/status/${escapeHtmlAttr(id)}\">全文</a>"
            } else {
                htmlTextBase
            }
            val finalHtml = enrichSuperTopicLinks(htmlText, mergedUrlStruct)
            // 官方信息流在 280 个半角宽度（约 140 个汉字）处截断长正文；有全文时另存一份折叠版，
            // 列表按开关决定显示哪一份，详情页始终用 [finalHtml]。网页版通道拿到的已经是服务端截断过的
            // HTML，宽度本来就在预算内，跳过即可。
            val foldedHtml = rawText
                .takeUnless { WeiboHtmlTagRegex.containsMatchIn(it) }
                ?.let { foldWeiboText(it) }
                ?.let { formatContainerTextToHtml(it, mergedUrlStruct, mergedTopicStruct) }
                ?.let { enrichSuperTopicLinks(it, mergedUrlStruct) }
            if (pics.isEmpty() && mixPics == null) {
                val inlinePics = picsFromUrlStruct(mergedUrlStruct).ifEmpty { inlinePicsFromHtml(finalHtml) }
                if (inlinePics.isNotEmpty()) pics = inlinePics
            }

            return Status(
                id = id,
                mid = o.str("mid").ifBlank { id },
                textHtml = finalHtml,
                createdAt = o.str("created_at"),
                source = stripHtml(o.str("source")),
                user = User.from(o.optJSONObject("user")),
                pics = pics,
                video = video,
                bigPic = bigPic,
                retweeted = retweeted,
                reposts = o.int("reposts_count"),
                comments = o.int("comments_count"),
                attitudes = o.int("attitudes_count"),
                liked = parseStatusLiked(o),
                favorited = o.optBoolean("favorited", false),
                region = o.str("region_name"),
                editCount = o.int("edit_count"),
                isVote = isVote,
                voteId = voteId,
                visibility = parseStatusVisibility(o),
                isLongText = longTextFlag || foldedHtml != null,
                foldedTextHtml = foldedHtml,
            )
        }
    }
}

private val WeiboHtmlTagRegex = Regex(
    """</?(?:a|br|span|img|p|div|b|i|em|strong)\b""",
    RegexOption.IGNORE_CASE,
)

private val ContainerRichTokenRegex = Regex(
    """(https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)|(#[^#\n]+?#)|(@[\p{L}\p{N}_\-\u4e00-\u9fa5·]+)"""
)

private val HtmlSegmentRegex = Regex(
    """<a\b([^>]*)>(.*?)</a>|<[^>]+>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val HtmlHrefAttrRegex = Regex(
    """\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')""",
    RegexOption.IGNORE_CASE,
)

private val EmailDomainSuffixRegex = Regex("""^\.[A-Za-z]{2,}""")

private fun mergeJsonArrays(primary: JSONArray?, secondary: JSONArray?): JSONArray? {
    if (primary == null || primary.length() == 0) return secondary
    if (secondary == null || secondary.length() == 0) return primary
    val merged = JSONArray()
    for (i in 0 until primary.length()) merged.put(primary.opt(i))
    for (i in 0 until secondary.length()) merged.put(secondary.opt(i))
    return merged
}

private fun canonicalShortUrlKey(url: String): String {
    val trimmed = url.trim()
    val schemeSep = trimmed.indexOf("://")
    if (schemeSep <= 0) return trimmed
    val rest = trimmed.substring(schemeSep + 3)
    val slash = rest.indexOf('/')
    return if (slash < 0) {
        rest.lowercase()
    } else {
        rest.substring(0, slash).lowercase() + rest.substring(slash)
    }
}

private fun lookupShortUrl(
    shortUrls: Map<String, Pair<String, String>>,
    url: String,
): Pair<String, String>? {
    val trimmed = url.trim()
    return shortUrls[trimmed] ?: shortUrls[canonicalShortUrlKey(trimmed)]
}

private fun splitUrlAndTrailingPunctuation(
    rawUrl: String,
    shortUrls: Map<String, Pair<String, String>>,
): Pair<String, String> {
    if (lookupShortUrl(shortUrls, rawUrl) != null) return rawUrl to ""
    var end = rawUrl.length
    while (end > 0) {
        val last = rawUrl[end - 1]
        val shouldStrip = when (last) {
            '.', ',', ';', ':', '!', '?', ']', '}' -> true
            ')' -> {
                val sub = rawUrl.substring(0, end)
                sub.count { it == '(' } < sub.count { it == ')' }
            }
            else -> false
        }
        if (!shouldStrip) break
        end--
        val candidate = rawUrl.substring(0, end)
        if (lookupShortUrl(shortUrls, candidate) != null) {
            return candidate to rawUrl.substring(end)
        }
    }
    return rawUrl.substring(0, end) to rawUrl.substring(end)
}

private fun resolveShortUrlLabel(
    rawTitle: String,
    picHint: String,
    short: String,
    target: String,
): String {
    val lowerPic = picHint.lowercase()
    val isWeiboTarget = com.cobo.app.ui.weiboStatusId(target) != null ||
        lowerPic.contains("timeline_card_small_weibo")
    val isLiveTarget = com.cobo.app.ui.isWeiboLiveLink(target) ||
        lowerPic.contains("timeline_card_small_live")
    val isVideoTarget = !isLiveTarget && (
        com.cobo.app.ui.isVideoLink(target) ||
            lowerPic.contains("timeline_card_small_video")
        )
    val isPhotoTarget = lowerPic.contains("timeline_card_small_photo") ||
        lowerPic.contains("card_small_photo") ||
        com.cobo.app.ui.isWeiboPhotoPageLink(target) ||
        com.cobo.app.ui.isImageLink(target)
    return when {
        isWeiboTarget && (rawTitle.isBlank() || rawTitle == "网页链接" || com.cobo.app.ui.isWeiboShortLink(rawTitle)) ->
            "微博正文"
        isLiveTarget && (rawTitle.isBlank() || rawTitle == "网页链接" || com.cobo.app.ui.isWeiboShortLink(rawTitle)) ->
            "微博直播"
        isPhotoTarget && (rawTitle.isBlank() || rawTitle == "网页链接" || com.cobo.app.ui.isWeiboShortLink(rawTitle)) ->
            "查看图片"
        rawTitle.isNotBlank() &&
            !com.cobo.app.ui.isWeiboShortLink(rawTitle) &&
            rawTitle != short &&
            rawTitle != target -> rawTitle
        isVideoTarget -> "微博视频"
        lowerPic.contains("timeline_card_small_web") -> "网页链接"
        rawTitle.isNotBlank() && !com.cobo.app.ui.isWeiboShortLink(rawTitle) -> rawTitle
        else -> ""
    }
}

private fun enrichHtmlShortUrls(
    raw: String,
    shortUrls: Map<String, Pair<String, String>>,
): String {
    val out = StringBuilder(raw.length + 32)
    var cursor = 0
    for (match in HtmlSegmentRegex.findAll(raw)) {
        if (match.range.first > cursor) {
            out.append(replaceBareUrlsInPlainHtmlSegment(raw.substring(cursor, match.range.first), shortUrls))
        }
        val attrs = match.groups[1]?.value
        val inner = match.groups[2]?.value
        if (attrs != null && inner != null) {
            val innerPlain = stripHtml(inner).trim()
            val hrefMatch = HtmlHrefAttrRegex.find(attrs)
            val href = hrefMatch?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }.orEmpty()
            val mapped = if (com.cobo.app.ui.isWeiboShortLink(innerPlain)) {
                lookupShortUrl(shortUrls, innerPlain)
            } else if (com.cobo.app.ui.isWeiboShortLink(href)) {
                lookupShortUrl(shortUrls, href)
            } else {
                null
            }
            if (mapped != null && (com.cobo.app.ui.isWeiboShortLink(innerPlain) || innerPlain.isBlank())) {
                val target = mapped.second.ifBlank { href }.ifBlank { innerPlain }
                val label = mapped.first.takeIf { it.isNotBlank() } ?: run {
                    val unwrapped = com.cobo.app.ui.unwrapSinaUrl(target) ?: target
                    if (com.cobo.app.ui.weiboStatusId(unwrapped) != null) "微博正文" else innerPlain
                }
                val newAttrs = if (hrefMatch != null && target != href) {
                    attrs.replaceRange(hrefMatch.range, "href=\"${escapeHtmlAttr(target)}\"")
                } else {
                    attrs
                }
                out.append("<a").append(newAttrs).append(">").append(escapeHtmlText(label)).append("</a>")
            } else if (com.cobo.app.ui.isWeiboShortLink(innerPlain)) {
                val unwrapped = com.cobo.app.ui.unwrapSinaUrl(href) ?: href
                val replacementLabel = if (com.cobo.app.ui.weiboStatusId(unwrapped) != null) "微博正文" else null
                if (replacementLabel != null) {
                    out.append("<a").append(attrs).append(">").append(replacementLabel).append("</a>")
                } else {
                    out.append(match.value)
                }
            } else {
                out.append(match.value)
            }
        } else {
            out.append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        out.append(replaceBareUrlsInPlainHtmlSegment(raw.substring(cursor), shortUrls))
    }
    return out.toString()
}

private fun replaceBareUrlsInPlainHtmlSegment(
    segment: String,
    shortUrls: Map<String, Pair<String, String>>,
): String {
    if (!segment.contains("http://", ignoreCase = true) && !segment.contains("https://", ignoreCase = true)) {
        return segment
    }
    val urlOnlyRegex = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")
    val out = StringBuilder(segment.length + 16)
    var cursor = 0
    for (match in urlOnlyRegex.findAll(segment)) {
        if (match.range.first > cursor) out.append(segment.substring(cursor, match.range.first))
        val (urlToken, trailing) = splitUrlAndTrailingPunctuation(match.value, shortUrls)
        val mapped = lookupShortUrl(shortUrls, urlToken)
        if (mapped != null) {
            val target = mapped.second
            val label = mapped.first.takeIf { it.isNotBlank() } ?: urlToken
            out.append("<a href=\"")
                .append(escapeHtmlAttr(target))
                .append("\">")
                .append(escapeHtmlText(label))
                .append("</a>")
                .append(trailing)
        } else {
            out.append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < segment.length) out.append(segment.substring(cursor))
    return out.toString()
}

private fun isAsciiEmailLocalChar(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '_' || c == '+' || c == '-'

private fun escapeHtmlText(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\n", "<br/>")

private fun escapeHtmlAttr(attr: String): String = attr
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/**
 * 容器端（`api.weibo.cn/2/`）下发的正文与评论是纯文本 + `url_struct` / `topic_struct`，
 * 没有网页端的 `<a href>` 标签和 `<br/>`。这里在保留已有 HTML 不动的前提下，
 * 把纯文本里的超话、话题、`@用户`、`http://t.cn/...` 与换行转换成统一 HTML，
 * 让现有的富文本渲染、链接跳转与历史记录直接复用。
 */
internal fun formatContainerTextToHtml(
    raw: String,
    urlStruct: JSONArray? = null,
    topicStruct: JSONArray? = null,
): String {
    if (raw.isBlank()) return raw
    val shortUrls = HashMap<String, Pair<String, String>>()
    val superTopicUrls = HashMap<String, String>()
    if (urlStruct != null) {
        for (i in 0 until urlStruct.length()) {
            val entry = urlStruct.optJSONObject(i) ?: continue
            val short = entry.str("short_url").trim()
            val ori = entry.str("ori_url").trim()
            val long = entry.str("long_url").trim()
            val pageId = entry.str("page_id").trim()
            val rawTitle = entry.str("url_title").trim()
            val rawTarget = when {
                pageId.startsWith("231601") && entry.str("h5_target_url").isNotBlank() ->
                    entry.str("h5_target_url").trim()
                pageId.startsWith("100808", ignoreCase = true) ->
                    "https://weibo.com/p/${pageId.lowercase()}"
                ori.isNotBlank() -> ori
                long.isNotBlank() -> long
                else -> short
            }
            // 客户端通道的「查看图片 / 评论配图」有时把 ori_url 写成无法解析的 sinaweibo://commentimage?...，
            // 若继续覆盖 short_url 会丢失可解析的 t.cn 短链；此时保留 short_url 或提取其中携带的 pid。
            val target = if (rawTarget.startsWith("sinaweibo://", ignoreCase = true) &&
                com.cobo.app.ui.resolveWeiboLink(rawTarget) == null
            ) {
                val embeddedPid = com.cobo.app.ui.extractWeiboPhotoPid(rawTarget)
                when {
                    !embeddedPid.isNullOrBlank() -> "https://wx1.sinaimg.cn/large/$embeddedPid.jpg"
                    short.startsWith("http", ignoreCase = true) -> short
                    else -> rawTarget
                }
            } else {
                rawTarget
            }
            val label = resolveShortUrlLabel(
                rawTitle = rawTitle,
                picHint = entry.str("url_type_pic"),
                short = short,
                target = target,
            )
            if (short.isNotBlank() && target.isNotBlank()) {
                val pair = label to target
                shortUrls[short] = pair
                shortUrls[canonicalShortUrlKey(short)] = pair
            }
            val isSuper = pageId.startsWith("100808", ignoreCase = true) ||
                ori.contains("100808") ||
                entry.str("url_type_pic").contains("super", ignoreCase = true)
            if (isSuper && target.isNotBlank() && rawTitle.isNotBlank()) {
                superTopicUrls[rawTitle] = target
                superTopicUrls[rawTitle.removeSuffix("超话").trim()] = target
            }
        }
    }

    if (WeiboHtmlTagRegex.containsMatchIn(raw)) {
        return if (shortUrls.isNotEmpty() || raw.contains("t.cn/", ignoreCase = true)) {
            enrichHtmlShortUrls(raw, shortUrls)
        } else {
            raw
        }
    }
    val hasRichHints = urlStruct != null || topicStruct != null ||
        raw.contains('\n') || raw.contains('@') || raw.contains('#') ||
        raw.contains("http://", ignoreCase = true) || raw.contains("https://", ignoreCase = true)
    if (!hasRichHints) return raw

    val topicUrls = HashMap<String, String>()
    if (topicStruct != null) {
        for (i in 0 until topicStruct.length()) {
            val entry = topicStruct.optJSONObject(i) ?: continue
            val title = entry.str("topic_title").trim('#', ' ')
            val url = entry.str("topic_url").trim()
            if (title.isNotBlank() && url.isNotBlank()) {
                topicUrls[title] = url
            }
        }
    }

    val out = StringBuilder(raw.length + 64)
    var cursor = 0
    for (match in ContainerRichTokenRegex.findAll(raw)) {
        if (match.range.first < cursor) continue
        if (match.range.first > cursor) {
            out.append(escapeHtmlText(raw.substring(cursor, match.range.first)))
        }
        val token = match.value
        when {
            match.groups[1] != null -> {
                val (urlToken, trailing) = splitUrlAndTrailingPunctuation(token, shortUrls)
                val mapped = lookupShortUrl(shortUrls, urlToken)
                val target = mapped?.second ?: urlToken
                val label = mapped?.first?.takeIf { it.isNotBlank() } ?: urlToken
                out.append("<a href=\"")
                    .append(escapeHtmlAttr(target))
                    .append("\">")
                    .append(escapeHtmlText(label))
                    .append("</a>")
                if (trailing.isNotEmpty()) out.append(escapeHtmlText(trailing))
            }

            match.groups[2] != null -> {
                val inner = token.substring(1, token.length - 1)
                if (inner.endsWith("[超话]")) {
                    val name = inner.removeSuffix("[超话]").trim()
                    val encoded = runCatching { java.net.URLEncoder.encode(name, "UTF-8") }.getOrDefault(name)
                    val target = superTopicUrls[name]
                        ?: superTopicUrls["${name}超话"]
                        ?: "https://m.weibo.cn/k/$encoded"
                    out.append("<a href=\"")
                        .append(escapeHtmlAttr(target))
                        .append("\">")
                        .append(escapeHtmlText(name))
                        .append("</a>")
                } else {
                    val encoded = runCatching { java.net.URLEncoder.encode(inner, "UTF-8") }.getOrDefault(inner)
                    val target = topicUrls[inner] ?: "https://m.weibo.cn/k/$encoded"
                    out.append("<a href=\"")
                        .append(escapeHtmlAttr(target))
                        .append("\">#")
                        .append(escapeHtmlText(inner))
                        .append("#</a>")
                }
            }

            match.groups[3] != null -> {
                val prevChar = raw.getOrNull(match.range.first - 1)
                val looksLikeEmail = prevChar != null &&
                    isAsciiEmailLocalChar(prevChar) &&
                    EmailDomainSuffixRegex.containsMatchIn(raw.substring(match.range.last + 1))
                if (looksLikeEmail) {
                    out.append(escapeHtmlText(token))
                } else {
                    val name = token.substring(1)
                    val encoded = runCatching { java.net.URLEncoder.encode(name, "UTF-8") }.getOrDefault(name)
                    out.append("<a href=\"/n/")
                        .append(escapeHtmlAttr(encoded))
                        .append("\">@")
                        .append(escapeHtmlText(name))
                        .append("</a>")
                }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        out.append(escapeHtmlText(raw.substring(cursor)))
    }
    return out.toString()
}

/**
 * 官方信息流把长正文压在 280 个半角宽度（约 140 个汉字）以内，再补「…展开」。
 * 这里按同一条规则把全文截短，供列表折叠显示。
 */
internal const val WeiboFeedFoldWidth = 280

private val FoldUrlRegex = Regex("""https?://[^\s\u4e00-\u9fff]+""")
private val FoldEmojiRegex = Regex("""\[[^\[\]\s]{1,8}\]""")
private const val FoldUrlTrailing = ".,;:!?)]}）】》〉」』\"'"

/**
 * 把微博正文截到 [budget] 个半角宽度：汉字 / 全角标点 / emoji 记 2，ASCII 记 1，
 * 零宽字符不计；链接按 URL 原长计入（官方就是这么数的，只是显示成「网页链接」）。
 * 表情 `[微笑]` 按一个宽字符处理。原文本来就够短时返回 null。
 */
internal fun foldWeiboText(raw: String, budget: Int = WeiboFeedFoldWidth): String? {
    if (raw.isEmpty()) return null
    var index = 0
    var width = 0
    while (index < raw.length) {
        val url = FoldUrlRegex.matchAt(raw, index)
        if (url != null) {
            val token = url.value.trimEnd { it in FoldUrlTrailing }
            val w = displayWidthOf(token)
            if (width + w > budget) break
            width += w
            index += token.length
            continue
        }
        val emoji = FoldEmojiRegex.matchAt(raw, index)
        if (emoji != null) {
            if (width + 2 > budget) break
            width += 2
            index += emoji.value.length
            continue
        }
        val codePoint = raw.codePointAt(index)
        val w = displayWidthOf(codePoint)
        if (width + w > budget) break
        width += w
        index += Character.charCount(codePoint)
    }
    if (index >= raw.length) return null
    val folded = raw.substring(0, index).trimEnd { it == '\u200b' || it.isWhitespace() }
    return folded.ifEmpty { null }
}

private fun displayWidthOf(text: String): Int {
    var width = 0
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        width += displayWidthOf(codePoint)
        i += Character.charCount(codePoint)
    }
    return width
}

private fun displayWidthOf(codePoint: Int): Int {
    if (codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0xFEFF) return 0
    return when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        -> 0
        else -> if (isWideCodePoint(codePoint)) 2 else 1
    }
}

/** 东亚宽字符 + 中文语境下按全角算的歧义字符（引号、破折号、省略号等）。 */
private fun isWideCodePoint(codePoint: Int): Boolean = when (codePoint) {
    in 0x1100..0x115F -> true
    in 0x2010..0x201F -> true
    in 0x2026..0x2027, 0x2030, 0x2032, 0x2033 -> true
    in 0x2E80..0x303E -> true
    in 0x3041..0x33FF -> true
    in 0x3400..0x4DBF -> true
    in 0x4E00..0x9FFF -> true
    in 0xA000..0xA4CF -> true
    in 0xAC00..0xD7A3 -> true
    in 0xF900..0xFAFF -> true
    in 0xFE30..0xFE4F -> true
    in 0xFF00..0xFF60 -> true
    in 0xFFE0..0xFFE6 -> true
    in 0x1F000..0x1FAFF -> true
    in 0x20000..0x3FFFD -> true
    else -> false
}

/**
 * 服务端截断的长文尾部是 ` \u200b ...<span class="expand">展开</span>`，把这段标记去掉，
 * 免得后面再补「全文」入口时出现「…展开…全文」。
 */
private fun stripServerTruncationMarker(html: String): String {
    val withoutExpand = html.replace(
        Regex("""<span[^>]*class="expand"[^>]*>.*?</span>""", RegexOption.IGNORE_CASE),
        "",
    )
    return withoutExpand.trimEnd { it == '\u200b' || it == '.' || it == '…' || it.isWhitespace() }
}

private fun enrichSuperTopicLinks(textHtml: String, urlStruct: JSONArray?): String {
    if (textHtml.isBlank() || urlStruct == null || urlStruct.length() == 0) return textHtml
    var out = textHtml
    for (i in 0 until urlStruct.length()) {
        val entry = urlStruct.optJSONObject(i) ?: continue
        val pageId = entry.str("page_id")
        if (!pageId.startsWith("100808", ignoreCase = true)) continue
        val target = "https://weibo.com/p/${pageId.lowercase()}"
        val shortUrl = entry.str("short_url")
        if (shortUrl.isNotBlank() && out.contains(shortUrl)) {
            out = out.replace("href=\"$shortUrl\"", "href=\"$target\"")
                .replace("href='$shortUrl'", "href='$target'")
        }
    }
    return out
}

internal fun normalizeWeiboMediaUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val absolute = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    return if (absolute.startsWith("http://") &&
        (absolute.contains("sinaimg.") || absolute.contains("weibocdn.com"))
    ) {
        "https://" + absolute.removePrefix("http://")
    } else {
        absolute
    }
}

internal fun isGenericCardIcon(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("timeline_card_small_") ||
        (lower.contains("h5.sinaimg.cn/upload/") &&
            (lower.contains("_default.") || lower.contains("card_small")))
}

private fun firstWboxUrlFromStruct(urlStruct: JSONArray?): String {
    if (urlStruct == null) return ""
    for (i in 0 until urlStruct.length()) {
        val entry = urlStruct.optJSONObject(i) ?: continue
        val candidates = listOf(
            entry.str("h5_target_url"),
            entry.str("ori_url"),
            entry.str("long_url"),
            entry.str("short_url"),
        )
        val match = candidates.firstOrNull { com.cobo.app.ui.isWeiboWboxLink(it) }
        if (!match.isNullOrBlank()) return match
    }
    return ""
}

private fun pageCardImageUrl(page: JSONObject): String {
    val pagePicObj = page.optJSONObject("page_pic")
    val picInfo = page.optJSONObject("pic_info")
    val cardInfo = page.optJSONObject("card_info")
    val firstSubCard = cardInfo?.optJSONArray("cards")?.optJSONObject(0)
        ?: page.optJSONArray("cards")?.optJSONObject(0)
    // 优先取卡片大图字段（card_info.pic_url / media_pic_url / page_pic），跳过微博小链接占位灰图标（timeline_card_small_*）。
    val candidates = listOf(
        cardInfo?.str("pic_url").orEmpty(),
        cardInfo?.optJSONObject("page_pic")?.str("url").orEmpty(),
        page.str("media_pic_url"),
        pagePicObj?.str("url").orEmpty(),
        (page.opt("page_pic") as? String).orEmpty(),
        picInfo?.optJSONObject("pic_big")?.str("url").orEmpty(),
        picInfo?.optJSONObject("pic_middle")?.str("url").orEmpty(),
        picInfo?.optJSONObject("pic_small")?.str("url").orEmpty(),
        cardInfo?.str("pic").orEmpty(),
        firstSubCard?.optJSONObject("page_pic")?.str("url").orEmpty(),
        firstSubCard?.str("pic_url").orEmpty(),
        firstSubCard?.str("pic").orEmpty(),
        page.str("pic"),
        page.str("thumb_pic"),
    )
    return candidates
        .map { normalizeWeiboMediaUrl(it) }
        .firstOrNull { it.isNotBlank() && !isGenericCardIcon(it) }
        .orEmpty()
}

private fun isPlainSuperTopicOrUserPageInfo(page: JSONObject, schemeUrl: String): Boolean {
    val objType = page.str("object_type").lowercase()
    if (objType == "user" || objType == "super_topic" || objType == "topic") return true
    val pageId = page.str("page_id").trim().lowercase()
    if (pageId.startsWith("100808")) return true
    if (superTopicId(schemeUrl) != null) return true
    return false
}

/**
 * 提取微博 `page_info` 附带的大图/卡片预览图（兼容网页端 `type=bigPic` 与客户端容器端 `wbox`/`card_type=107` 卡片）。
 */
internal fun extractBigPicInfo(
    o: JSONObject,
    page: JSONObject?,
    pics: List<Pic>,
    hasVideo: Boolean,
    isVote: Boolean,
    urlStructOverride: JSONArray? = null,
): BigPicInfo? {
    if (page == null || hasVideo || isVote) return null
    val pageType = page.str("type").lowercase()
    val objType = page.str("object_type").lowercase()
    if (pageType == "video" || objType == "video" || isLivePageInfo(page) || objType == "hudongvote") return null

    val picUrl = pageCardImageUrl(page)
    if (picUrl.isBlank()) return null

    val pagePic = page.optJSONObject("page_pic")
    val cardInfo = page.optJSONObject("card_info")
    val rawScheme = pagePic?.str("scheme").orEmpty()
        .ifBlank { page.str("page_url") }
        .ifBlank { page.str("scheme") }
        .ifBlank { cardInfo?.str("scheme").orEmpty() }
    val wboxFromStruct = firstWboxUrlFromStruct(urlStructOverride ?: o.optJSONArray("url_struct"))
    val schemeUrl = if (com.cobo.app.ui.isWeiboWboxLink(rawScheme)) {
        rawScheme
    } else if (wboxFromStruct.isNotBlank() && (rawScheme.isBlank() || com.cobo.app.ui.isWeiboShortLink(rawScheme))) {
        wboxFromStruct
    } else {
        rawScheme
    }

    val pageId = page.str("page_id").trim()
    val objectId = page.str("object_id").trim()
    val cardType = cardInfo?.str("card_type").orEmpty().trim()
    val isBigPicCard = cardType == "107" ||
        pageId.startsWith("231601") ||
        objectId.contains(":231601_")

    val isExplicitBigOrWbox = isBigPicCard ||
        pageType == "photo" || objType == "photo" || objType == "picture" ||
        com.cobo.app.ui.isWeiboPhotoPageLink(schemeUrl) ||
        pageType == "bigpic" || pageType == "big_pic" ||
        objType == "bigpic" || objType == "big_pic" ||
        pageType == "wbox" || objType == "wbox" || objType == "mini_program" ||
        com.cobo.app.ui.isWeiboWboxLink(schemeUrl) ||
        objectId.contains("wbox", ignoreCase = true)

    if (!isExplicitBigOrWbox) {
        if (pics.isNotEmpty() || isPlainSuperTopicOrUserPageInfo(page, schemeUrl)) {
            return null
        }
    }

    val title = page.str("page_title")
        .ifBlank { page.str("title") }
        .ifBlank { cardInfo?.str("title").orEmpty() }
        .trim()
    val rawSubtitle = page.str("content1")
        .ifBlank { page.str("tips") }
        .trim()
    val subtitle = if (rawSubtitle == title) {
        page.str("content2")
            .ifBlank { cardInfo?.str("content1").orEmpty() }
            .trim()
            .takeIf { it != title }
            .orEmpty()
    } else {
        rawSubtitle
    }
    return BigPicInfo(
        url = picUrl,
        scheme = schemeUrl,
        title = title,
        subtitle = subtitle,
        isLarge = isExplicitBigOrWbox,
    )
}

private fun videoCoverUrl(page: JSONObject, media: JSONObject?, pics: List<Pic>): String {
    val pagePicObj = page.optJSONObject("page_pic")
    val picInfo = media?.optJSONObject("pic_info")
    val raw = pagePicObj?.str("url").orEmpty()
        .ifBlank { (page.opt("page_pic") as? String).orEmpty() }
        .ifBlank { picInfo?.optJSONObject("pic_big")?.str("url").orEmpty() }
        .ifBlank { picInfo?.optJSONObject("pic_middle")?.str("url").orEmpty() }
        .ifBlank { picInfo?.optJSONObject("pic_small")?.str("url").orEmpty() }
        .ifBlank { page.str("pic") }
        .ifBlank { page.str("thumb_pic") }
        .ifBlank { pics.firstOrNull()?.thumb.orEmpty() }
        .trim()
    return normalizeWeiboMediaUrl(raw)
}

private fun firstPlaybackUrl(arr: JSONArray?): String {
    if (arr == null) return ""
    var fallback = ""
    for (i in 0 until arr.length()) {
        val play = arr.optJSONObject(i)?.optJSONObject("play_info") ?: continue
        val url = play.str("url")
        if (url.isBlank()) continue
        if (fallback.isBlank()) fallback = url
        val mime = play.str("mime")
        if (mime.contains("mp4", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
            return url
        }
    }
    return fallback
}

data class EditVersion(
    val createdAt: String,
    val textHtml: String,
    val pics: List<String> = emptyList(),
    /** Visibility-only history rows have no body; this holds their one-line description. */
    val note: String = "",
) {
    companion object {
        fun from(o: JSONObject?): EditVersion? {
            if (o == null) return null
            val note = if (o.has("history_type")) stripHtml(o.str("text")) else ""
            val text = if (note.isBlank()) {
                formatContainerTextToHtml(
                    o.str("text"),
                    o.optJSONArray("url_struct"),
                    o.optJSONArray("topic_struct"),
                )
            } else {
                ""
            }
            val ids = o.optJSONArray("pic_ids")
            val pics = if (ids == null) {
                emptyList()
            } else {
                buildList {
                    for (i in 0 until ids.length()) {
                        val id = ids.optString(i).orEmpty()
                        if (id.isNotBlank()) add("https://wx1.sinaimg.cn/large/$id.jpg")
                    }
                }
            }
            val created = o.str("created_at")
            if (note.isBlank() && text.isBlank() && pics.isEmpty()) return null
            return EditVersion(created, text, pics, note)
        }
    }
}

data class Comment(
    val id: String,
    val textHtml: String,
    val user: User?,
    val createdAt: String,
    val likes: Int,
    val liked: Boolean = false,
    /** 评论自带的配图（图片评论），按上传顺序。 */
    val pics: List<Pic> = emptyList(),
    /** ID 后面那串粉丝标识（铁粉 / 钻粉…）。接口给的是现成的小图，直接按图渲染。 */
    val badges: List<CommentBadge> = emptyList(),
    /** 接口没给 `comment_badge` 小图但给了 `comment_bubble` / `comment_dynamic_message` 时的粉丝等级兜底（如「铁粉」「金粉」「钻粉」）。 */
    val fanTier: String = "",
    val reply: Comment? = null,
    /** The replies Weibo inlines under a comment, oldest first. */
    val replies: List<Comment> = emptyList(),
    /** How many replies the comment has in total; [replies] is only a preview. */
    val replyCount: Int = 0,
) {
    companion object {
        fun from(o: JSONObject?, depth: Int = 0): Comment? {
            if (o == null) return null
            val id = o.str("id").ifBlank { o.str("idstr") }
            if (id.isBlank()) return null
            val children = if (depth < 1) o.optJSONArray("comments") else null
            val replies = if (children == null) {
                emptyList()
            } else {
                buildList {
                    for (i in 0 until children.length()) {
                        from(children.optJSONObject(i), depth + 1)?.let(::add)
                    }
                }
            }
            val textHtml = formatContainerTextToHtml(
                o.str("text"),
                o.optJSONArray("url_struct"),
                o.optJSONArray("topic_struct"),
            )
            val parsedPics = commentPicsOf(o)
                .ifEmpty { picsFromUrlStruct(o.optJSONArray("url_struct")) }
                .ifEmpty { inlinePicsFromHtml(textHtml) }
            return Comment(
                id = id,
                textHtml = textHtml,
                user = User.from(o.optJSONObject("user")),
                createdAt = o.str("created_at"),
                // The hot list spells it `like_count`, the chronological one `like_counts`.
                likes = if (o.has("like_count")) o.int("like_count") else o.int("like_counts"),
                liked = parseCommentLiked(o),
                pics = parsedPics,
                badges = commentBadgesOf(o),
                fanTier = commentFanTierOf(o),
                reply = if (depth < 1) from(o.optJSONObject("reply") ?: o.optJSONObject("reply_comment"), depth + 1) else null,
                replies = replies,
                replyCount = o.optInt("total_number", replies.size),
            )
        }
    }
}

internal fun parseStatusLiked(o: JSONObject): Boolean =
    o.optBoolean("liked", false) ||
        o.int("liked") > 0 ||
        o.int("attitudes_status") > 0

internal fun parseCommentLiked(o: JSONObject): Boolean =
    o.optBoolean("liked", false) ||
        o.int("liked") > 0 ||
        o.int("like_state") > 0 ||
        o.optBoolean("isLiked", false)

private fun picsFromUrlStruct(urlStruct: JSONArray?): List<Pic> {
    if (urlStruct == null || urlStruct.length() == 0) return emptyList()
    val out = LinkedHashMap<String, Pic>()
    fun addPic(pic: Pic) {
        val key = pic.large.ifBlank { pic.thumb }
        if (key.isNotBlank()) out.putIfAbsent(key, pic)
    }
    for (i in 0 until urlStruct.length()) {
        val entry = urlStruct.optJSONObject(i) ?: continue
        picsOf(entry).forEach(::addPic)
        entry.optJSONObject("pic")?.let(::picOf)?.let(::addPic)
        entry.optJSONObject("pic_info")?.let(::picOf)?.let(::addPic)
        for (field in listOf("ori_url", "long_url", "h5_target_url")) {
            val rawUrl = entry.str(field).trim()
            if (rawUrl.isBlank()) continue
            val unwrapped = com.cobo.app.ui.unwrapWeiboLink(rawUrl)
            when {
                com.cobo.app.ui.isImageLink(unwrapped) -> {
                    val thumb = normalizeWeiboMediaUrl(unwrapped)
                    addPic(Pic(thumb = thumb, large = com.cobo.app.ui.toLargeSinaImageUrl(thumb)))
                }
                com.cobo.app.ui.isWeiboPhotoPageLink(unwrapped) -> {
                    com.cobo.app.ui.extractWeiboPhotoPid(unwrapped)?.let { pid ->
                        addPic(
                            Pic(
                                thumb = "https://wx1.sinaimg.cn/orj360/$pid.jpg",
                                large = "https://wx1.sinaimg.cn/large/$pid.jpg",
                            ),
                        )
                    }
                }
            }
        }
    }
    return out.values.toList()
}

private fun inlinePicsFromHtml(html: String): List<Pic> =
    com.cobo.app.ui.extractDirectImageUrlsFromHtml(html)
        .map { (thumb, large) -> Pic(thumb = thumb, large = large) }

/**
 * 评论配图。H5 的评论带 `pic_ids` + `pic_infos`（也偶尔直接给 `pics`），客户端通道只回一个
 * `pic` 字段，字符串或对象两种写法都有；都收，没图就是空表。
 */
private fun commentPicsOf(o: JSONObject): List<Pic> {
    val pics = picsOf(o)
    if (pics.isNotEmpty()) return pics
    // 优先按 JSONObject/JSONArray 解析 `pic`，不能直接调 `o.str("pic")`——JSONObject.toString() 会把对象拼成 `{"pid":...}` 当作图片地址。
    o.optJSONObject("pic")?.let { obj ->
        picOf(obj)?.let { return listOf(it) }
    }
    o.optJSONArray("pic")?.let { arr ->
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let(::picOf)?.let(::add)
            }
        }
        if (parsed.isNotEmpty()) return parsed
    }
    o.optJSONObject("pic_info")?.let { info ->
        picOf(info)?.let { return listOf(it) }
    }
    val singlePid = (o.opt("pic_id") as? String)?.trim().orEmpty()
    if (singlePid.isNotBlank() && !singlePid.equals("null", ignoreCase = true)) {
        return listOf(
            Pic(
                thumb = "https://wx1.sinaimg.cn/orj360/$singlePid.jpg",
                large = "https://wx1.sinaimg.cn/large/$singlePid.jpg",
            ),
        )
    }
    val single = (o.opt("pic") as? String)?.trim().orEmpty()
    if (single.isNotBlank() && !single.equals("null", ignoreCase = true)) {
        val url = normalizeWeiboMediaUrl(single)
        if (url.isNotBlank()) {
            return listOf(Pic(thumb = url, large = com.cobo.app.ui.toLargeSinaImageUrl(url)))
        }
    }
    return emptyList()
}

/**
 * 评论 ID 后面的粉丝标识。`comment_badge`（H5 / 网页）与 `user.icons`（客户端通道）里放的都是现成小图：
 * `{"pic_url": ".../member_7_bigfan2_4.png", "name": "loyal_fans", "type_and_level": "2_4"}`。
 * 兼容放在 `comment` 或 `user` 下、单对象或数组、`icons` 里的粉丝勋章，以及 `//` 协议相对地址。
 */
private fun commentBadgesOf(o: JSONObject): List<CommentBadge> {
    val userObj = o.optJSONObject("user")
    val badgeArrays = listOfNotNull(
        o.optJSONArray("comment_badge"),
        userObj?.optJSONArray("comment_badge"),
    )
    val singleObjects = listOfNotNull(
        o.optJSONObject("comment_badge"),
        userObj?.optJSONObject("comment_badge"),
    )
    val iconArrays = listOfNotNull(
        o.optJSONArray("icons"),
        userObj?.optJSONArray("icons"),
    )
    return buildList {
        val seen = HashSet<String>()
        fun addBadge(b: JSONObject?, requireFansIcon: Boolean) {
            if (b == null) return
            if (b.int("hide") == 1) return
            val rawPic = b.str("pic_url")
                .ifBlank { b.str("url") }
                .ifBlank { b.str("pic") }
                .ifBlank { b.str("icon_url") }
                .ifBlank { b.str("icon") }
                .trim()
            if (rawPic.isBlank()) return
            val pic = if (rawPic.startsWith("//")) "https:$rawPic" else rawPic
            if (requireFansIcon && !isFansIconObject(b, pic)) return
            val name = b.str("type").ifBlank { b.str("name") }
            if (seen.add(pic)) add(CommentBadge(pic = pic, name = name))
        }
        for (arr in badgeArrays) {
            for (i in 0 until arr.length()) addBadge(arr.optJSONObject(i), requireFansIcon = false)
        }
        for (obj in singleObjects) addBadge(obj, requireFansIcon = false)
        for (arr in iconArrays) {
            for (i in 0 until arr.length()) addBadge(arr.optJSONObject(i), requireFansIcon = true)
        }
    }
}

private fun isFansIconObject(b: JSONObject, picUrl: String): Boolean {
    val type = b.str("type").lowercase()
    val name = b.str("name").lowercase()
    val ext = b.optJSONObject("actionlog")?.str("ext").orEmpty().lowercase()
    val scheme = b.str("scheme").lowercase()
    val lowerUrl = picUrl.lowercase()
    return type in FANS_ICON_NAMES ||
        name in FANS_ICON_NAMES ||
        ext == "status:fans" ||
        scheme.contains("bigfans") ||
        lowerUrl.contains("bigfan") ||
        lowerUrl.contains("loyal_fans")
}

private val FANS_ICON_NAMES = setOf(
    "loyal_fans",
    "diamond_fans",
    "gold_fans",
    "iron_fans",
    "super_fans",
    "fans",
)

private fun commentFanTierOf(o: JSONObject): String {
    fun extract(obj: JSONObject?): String {
        if (obj == null) return ""
        val obtainType = obj.optInt("obtain_type", -1)
        val id = obj.str("id").trim()
        val rawName = (obj.optJSONObject("name")?.str("zh_CN")?.takeIf { it.isNotBlank() }
            ?: obj.str("name"))
            .trim()
        val idLower = id.lowercase()
        val isFanBubble = obtainType == 3 ||
            rawName.endsWith("专属") ||
            rawName.contains("粉") ||
            idLower.startsWith("tie") ||
            idLower.startsWith("jin") ||
            idLower.startsWith("zuan") ||
            idLower.startsWith("zhiai") ||
            idLower.startsWith("fan")
        if (!isFanBubble) return ""
        val cleaned = rawName.removeSuffix("专属").trim()
        if (cleaned.isNotBlank() && cleaned != "会员") return cleaned
        return when {
            idLower.startsWith("zuan") -> "钻粉"
            idLower.startsWith("jin") -> "金粉"
            idLower.startsWith("tie") -> "铁粉"
            idLower.startsWith("zhiai") -> "挚爱粉"
            else -> ""
        }
    }
    return extract(o.optJSONObject("comment_bubble"))
        .ifBlank { extract(o.optJSONObject("comment_dynamic_message")) }
}

private fun outerOwnsPageMedia(
    outerRawText: String,
    page: JSONObject?,
    retweetedObj: JSONObject?,
): Boolean {
    if (retweetedObj == null) return true
    val retweetText = retweetedObj.optJSONObject("longText")?.str("longTextContent")?.takeIf { it.isNotBlank() }
        ?: retweetedObj.str("text")
    val outerHasUrl = outerRawText.contains("http://") || outerRawText.contains("https://")
    val retweetHasUrl = retweetText.contains("http://") || retweetText.contains("https://")
    if (!outerHasUrl || retweetHasUrl) return false
    val pageUrl = page?.str("page_url").orEmpty()
    return pageUrl.isBlank() || outerRawText.contains(pageUrl)
}

/** 评论 ID 后面的一枚粉丝标识，比如「铁粉」「钻粉」。 */
data class CommentBadge(val pic: String, val name: String = "")

data class SearchFeaturedUser(
    val user: User,
    val desc1: String = "",
    val desc2: String = "",
    val desc2Scheme: String = "",
)

data class FeedPage(
    val items: List<Status>,
    val sinceId: String?,
    val recommendedUsers: List<User> = emptyList(),
    val featuredUsers: List<SearchFeaturedUser> = emptyList(),
)

/** How the detail page orders its comments: the hot list, or every comment in time order. */
enum class CommentSort { Hot, Time }

/**
 * One page of comments. [maxId]/[maxIdType] carry the hot list's cursor, [nextPage] the
 * chronological list's next page number (0 when there is none left).
 */
data class CommentPage(
    val items: List<Comment>,
    val maxId: String,
    val maxIdType: Int,
    val nextPage: Int = 0,
)

data class HotItem(val title: String, val scheme: String, val label: String = "", val rank: Int = 0)

/**
 * 热搜的榜单。[filterType] 就是 `106003type=25` 容器认的 `filter_type`。
 *
 * 官方 H5 只提供这几个榜，`entertainment` / `news` / `topic` 这类看着合理的值一律回
 * 「这里还没有内容」；`region`（同城）要配合城市参数，`personal` / `hotregion` / `friends`
 * 是会回空的子列表，都不适合当固定分类，所以没放进标签栏。
 */
enum class HotBoard(val label: String, val filterType: String) {
    Realtime("全部热搜", "realtimehot"),
    Fun("文娱", "fun"),
    Social("社会", "social"),
    Life("生活", "lifenav"),
    Mine("个性化", "mineband"),
    ;

    /** 只勾选这一个榜时在标签栏位置显示的小标题。 */
    val singleTitle: String
        get() = if (label.endsWith("热搜")) label else "${label}热搜"
}

/** 搜索「话题 / 超话 / 地点」分类的一条结果。标题去掉了两侧的 #，[desc1]/[desc2] 是阅读、讨论或打卡数。 */
data class TopicItem(
    val title: String,
    val desc1: String = "",
    val desc2: String = "",
    val scheme: String = "",
    val containerId: String? = null,
    val pic: String = "",
)

data class MessageItem(
    val id: String,
    val text: String,
    val createdAt: String,
    val user: User?,
    val unread: Int,
    val scheme: String,
    /** 群聊会话的 gid；一对一私信为空串。 */
    val gid: String = "",
)

/**
 * 会话 scheme 里的群聊 id。
 *
 * 一对一私信是 `/message/chat?uid=<uid>`，群聊是 `/message/chat?gid=<gid>`；
 * 列表接口不给单独字段，只能从 scheme 里拆。群聊的 `user` 是群主或系统号，
 * 拿它当 uid 去调私信接口会得到 20003「用户不存在」。
 */
internal fun chatGidOf(scheme: String): String =
    scheme.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.startsWith("gid=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }
        .orEmpty()

/**
 * 一条私信。
 *
 * - [mediaType] 1 是图片，正文在 [imageUrl] / [thumbUrl]，[text] 只是占位；
 * - [dmType] 9 是系统提示（居中的灰条，不是气泡）；
 * - [pending] / [failed] 只用于本地乐观发送的那条，服务端回来的消息两者都是 false。
 */
data class ChatMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val recipientId: String,
    val createdAt: String,
    val mediaType: Int = 0,
    val dmType: Int = 0,
    val imageUrl: String = "",
    val thumbUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val pending: Boolean = false,
    val failed: Boolean = false,
) {
    val isImage: Boolean get() = mediaType == 1 && (imageUrl.isNotBlank() || thumbUrl.isNotBlank())
    val isNotice: Boolean get() = dmType == 9

    companion object {
        fun from(o: JSONObject?): ChatMessage? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null
            val attachment = o.optJSONObject("attachment")
            val original = attachment?.optJSONObject("original_image")
            // 群聊消息的发送者有时不出 sender_id，只给一个 from_user 对象。
            val fromUser = o.optJSONObject("from_user")
            return ChatMessage(
                id = id,
                text = o.str("text"),
                senderId = o.str("sender_id").ifBlank { fromUser?.str("id").orEmpty() },
                recipientId = o.str("recipient_id"),
                createdAt = o.str("created_at"),
                mediaType = o.int("media_type"),
                dmType = o.int("dm_type"),
                imageUrl = original?.str("url").orEmpty(),
                thumbUrl = attachment?.optJSONObject("thumbnail")?.str("url").orEmpty(),
                width = original?.int("width") ?: 0,
                height = original?.int("height") ?: 0,
            )
        }

        /** 解析开放/客户端接口（`api.weibo.cn/2/direct_messages/...`）的单条私信。 */
        fun fromClient(o: JSONObject?, gsid: String = ""): ChatMessage? {
            if (o == null) return null
            val id = o.str("idstr").ifBlank { o.str("id") }
            if (id.isBlank()) return null
            val sender = o.optJSONObject("sender")
            val recipient = o.optJSONObject("recipient")
            val senderId = o.str("sender_id").ifBlank { sender?.str("idstr").orEmpty() }.ifBlank { sender?.str("id").orEmpty() }
            val recipientId = o.str("recipient_id").ifBlank { recipient?.str("idstr").orEmpty() }.ifBlank { recipient?.str("id").orEmpty() }
            val attachArr = o.optJSONArray("attachinfo")
            val firstAttach = attachArr?.optJSONObject(0)
            val fid = firstAttach?.str("fid").orEmpty()
            val s3Url = firstAttach?.str("s3_url").orEmpty()
            val rawThumb = firstAttach?.str("thumbnail_240").orEmpty()
                .ifBlank { firstAttach?.str("thumbnail").orEmpty() }
            val imageUrl = when {
                s3Url.isNotBlank() -> s3Url
                fid.isNotBlank() -> "https://upload.api.weibo.com/2/mss/msget?source=209678993&fid=$fid"
                else -> ""
            }
            val thumbUrl = when {
                rawThumb.isNotBlank() -> rawThumb
                fid.isNotBlank() -> "https://upload.api.weibo.com/2/mss/msget_thumbnail?source=209678993&fid=$fid&high=240&width=240"
                else -> imageUrl
            }
            val mediaType = o.int("media_type").let { if (it == 0 && (imageUrl.isNotBlank() || thumbUrl.isNotBlank())) 1 else it }
            return ChatMessage(
                id = id,
                text = o.str("text"),
                senderId = senderId,
                recipientId = recipientId,
                createdAt = o.str("created_at"),
                mediaType = mediaType,
                dmType = o.int("dm_type"),
                imageUrl = imageUrl,
                thumbUrl = thumbUrl,
            )
        }
    }
}

/** 一次 `api/chat/list` 的结果。列表本身是最新在前，界面按 reverseLayout 铺。 */
data class ChatPage(
    val messages: List<ChatMessage>,
    val users: Map<String, User>,
    val title: String,
    val total: Int,
)

/**
 * 聊天记录响应 → [ChatPage]。
 *
 * 私信接口返回最新在前，群聊接口（`api/groupchat/list`）返回旧→新，这里统一翻成最新在前，
 * 界面只认一种顺序。
 */
internal fun parseChatPage(data: JSONObject, isGroup: Boolean): ChatPage {
    val messages = ArrayList<ChatMessage>()
    val arr = data.optJSONArray("msgs")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            ChatMessage.from(arr.optJSONObject(i))?.let { messages.add(it) }
        }
    }
    if (isGroup) messages.reverse()
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

/** 微博「关注分组」：gid 是分组 id，title 是分组名。分组属于账号，换账号就是另一套。 */
data class FollowGroup(val gid: String, val title: String)

/** 主页「添加到关注分组」里的一行：分组本身 + 当前用户是否已经在里面。 */
data class ProfileGroup(
    val gid: String,
    val name: String,
    val memberCount: Int = 0,
    val joined: Boolean = false,
)

/**
 * `getGroupList` 和 `allGroups` 一起给出来的结果：
 * [groups] 是能勾选的自建分组，[assigned] 是他当前已在的分组，提交时原样当 `origin_list_ids`。
 */
data class ProfileGroupSelection(
    val groups: List<ProfileGroup>,
    val assigned: List<String>,
)

/** 主页「基本资料」从网页版 `ajax/profile/detail` 里抽出来的几项，缺什么就留空。 */
data class ProfileDetail(
    val gender: String = "",
    val birthday: String = "",
    val location: String = "",
    val ipLocation: String = "",
    val createdAt: String = "",
    val education: String = "",
    val company: String = "",
    val credit: String = "",
    val labels: List<String> = emptyList(),
    val description: String = "",
)

data class Account(
    val uid: String,
    val name: String,
    val avatar: String,
    val cookie: String,
    /** 客户端登录拿到的会话 id；网页登录的账号为空，不能静默续期。 */
    val gsid: String = "",
    val gsidAt: Long = 0L,
)

data class Draft(val text: String, val time: Long)

data class HistoryItem(
    val id: String,
    val mid: String,
    val text: String,
    val user: String,
    val time: Long,
    /** 详情页正文的原始 HTML：历史列表靠它还原 emoji 与链接；旧记录没有这一项。 */
    val html: String = "",
)

/**
 * 登录态。[gsid] 非空表示这个会话是客户端登录来的，可以拿它走 `account/getoauth`
 * 换一份新 cookie；网页登录的会话只有 cookie，靠响应里的 `Set-Cookie` 滚动续命。
 */
data class Session(
    val cookie: String,
    val uid: String,
    val savedAt: Long,
    val gsid: String = "",
    val gsidAt: Long = 0L,
    val accessToken: String = "",
    val expires: Long = 0L,
    val source: String = "web",
    /** 登录渠道，对应 Share 的 `loginType`：1 网页、4 客户端。0 是早于这个字段的旧会话。 */
    val loginType: Int = 0,
    /** 域名 → 该域名下的 cookie（`\n` 分隔），见 [CookieVault]。 */
    val cookies: Map<String, String> = emptyMap(),
) {
    /** 有没有 `SUB` 才是真的登录了；只剩 `XSRF-TOKEN` 这类 cookie 的会话是过期的空壳。 */
    val loggedIn: Boolean get() = cookie.contains("SUB=")
}

data class Config(val uid: String, val st: String, val loggedIn: Boolean)

fun parseWeiboTimeMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    return runCatching {
        SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US).parse(raw)?.time
    }.getOrNull() ?: raw.toLongOrNull() ?: 0L
}

fun formatWeiboTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = parseWeiboTimeMillis(raw).takeIf { it > 0L } ?: return raw

    val diff = System.currentTimeMillis() - millis
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L}分钟前"
    if (diff < 86_400_000L) return "${diff / 3_600_000L}小时前"
    val date = Date(millis)
    val now = Date()
    val pattern = if (SimpleDateFormat("yyyy", Locale.US).format(date) ==
        SimpleDateFormat("yyyy", Locale.US).format(now)
    ) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.US).format(date)
}

/**
 * 服务端塞进时间线的推广博。
 *
 * 信息流广告的 `mark` 形如 `999_reallog_mark_ad:999|WeiboAD`（广告位）或
 * `...|WeiboADNatural`（自然流投放）。只认这个标记：`ad_state` 同样打在明星、
 * 品牌自己发的商业博上，那些在关注流和主页里是正常内容，不能一起删掉。
 */
internal fun JSONObject.isPromotedStatus(): Boolean {
    val mark = optString("mark")
    return mark.contains("reallog_mark_ad") || mark.contains("WeiboAD")
}

/** `statuses` 数组 → 微博列表，顺手丢掉推广博。时间线和分组流共用。 */
internal fun statusesOf(arr: JSONArray): List<Status> {
    val items = ArrayList<Status>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        if (o.isPromotedStatus()) continue
        Status.from(o)?.let(items::add)
    }
    return items
}

/**
 * 热搜榜条目。
 *
 * 广告位的 `itemid` 带 `is_ad_pos:1|adid:<id>`，普通词条只有 `seqid` / `cate` / `t`，
 * 按这个字段就能整条跳过，不必看标题。跳过后 [HotItem.rank] 自然顺延，榜单不留空号。
 */
internal fun parseHotItems(root: JSONObject?): List<HotItem> =
    parseHotItemsFromCards(root?.optJSONObject("data")?.optJSONArray("cards"))

internal fun parseContainerHotItems(root: JSONObject?): List<HotItem> =
    parseHotItemsFromCards(root?.optJSONArray("cards"))

private fun parseHotItemsFromCards(cards: JSONArray?): List<HotItem> {
    if (cards == null) return emptyList()
    val out = ArrayList<HotItem>()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val g = group.optJSONObject(j) ?: continue
            if (g.str("itemid").contains("is_ad_pos")) continue
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

fun parseCards(root: JSONObject?): FeedPage =
    parseCardsFromData(root?.optJSONObject("data"))

fun parseContainerCards(root: JSONObject?): FeedPage =
    parseCardsFromData(root)

private fun parseCardsFromData(data: JSONObject?): FeedPage {
    val items = ArrayList<Status>()
    val recommended = LinkedHashMap<String, User>()
    val featured = LinkedHashMap<String, SearchFeaturedUser>()

    fun collectCardUsers(obj: JSONObject) {
        val searchUser = searchUserOf(obj)
        if (searchUser != null) {
            if (searchUser.id !in recommended) recommended[searchUser.id] = searchUser
            if (obj.optInt("card_type") == 10 && searchUser.id !in featured) {
                val desc2Scheme = obj.optJSONArray("desc2_struct")
                    ?.optJSONObject(0)
                    ?.str("scheme")
                    .orEmpty()
                featured[searchUser.id] = SearchFeaturedUser(
                    user = searchUser,
                    desc1 = obj.str("desc1"),
                    desc2 = obj.str("desc2"),
                    desc2Scheme = desc2Scheme,
                )
            }
        }
        val userArr = obj.optJSONArray("users") ?: return
        for (k in 0 until userArr.length()) {
            val uObj = userArr.optJSONObject(k) ?: continue
            val u = User.from(uObj) ?: continue
            if (u.id !in recommended) recommended[u.id] = u
        }
    }

    fun collectSubCardsMblogs(obj: JSONObject) {
        val subs = obj.optJSONArray("sub_cards") ?: return
        for (k in 0 until subs.length()) {
            val sub = subs.optJSONObject(k) ?: continue
            sub.optJSONObject("mblog")?.takeUnless { it.isPromotedStatus() }
                ?.let { Status.from(it)?.let(items::add) }
        }
    }

    val cards: JSONArray? = data?.optJSONArray("cards")
    if (cards != null) {
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i) ?: continue
            card.optJSONObject("mblog")?.takeUnless { it.isPromotedStatus() }
                ?.let { Status.from(it)?.let(items::add) }
            collectCardUsers(card)
            collectSubCardsMblogs(card)
            val group = card.optJSONArray("card_group")
            if (group != null) {
                for (j in 0 until group.length()) {
                    val g = group.optJSONObject(j) ?: continue
                    g.optJSONObject("mblog")
                        ?.takeUnless { it.isPromotedStatus() }
                        ?.let { Status.from(it)?.let(items::add) }
                    collectCardUsers(g)
                    collectSubCardsMblogs(g)
                }
            }
        }
    }
    val since = data?.optJSONObject("cardlistInfo")?.str("since_id")?.ifBlank { null }
    return FeedPage(items, since, recommended.values.toList(), featured.values.toList())
}

/**
 * 过滤个人主页时间线分页结果，只保留目标用户 [uid] 本人发布的微博。
 *
 * 微博 `profile/statuses` 与 `107603<uid>` 在本人可见微博展示完毕后，会继续下发其他用户的
 * 推荐微博并附带推荐流游标；这里统一剔除非该用户的条目，并在整页无本人微博或尾部已进入
 * 推荐流（连续 2 条及以上非本人微博）时截断后续游标（`sinceId = null`），让主页自然停在底端。
 */
internal fun filterUserStatusesPage(uid: String, page: FeedPage): FeedPage {
    val targetUid = uid.trim()
    if (targetUid.isBlank()) return page
    val raw = page.items
    val own = raw.filter { it.user?.id?.trim() == targetUid }
    val trailingForeignCount = raw.asReversed().takeWhile { it.user?.id?.trim() != targetUid }.size
    val nextSince = if (own.isEmpty() || trailingForeignCount >= 2) null else page.sinceId
    return page.copy(items = own, sinceId = nextSince)
}

/**
 * 搜索联想列表里的单项：普通关键词建议，或第 5–7 位的大V/官方号直达项。
 */
sealed interface SearchSuggestEntry {
    data class Keyword(val term: String) : SearchSuggestEntry
    data class Account(val user: User) : SearchSuggestEntry
}

data class SearchSuggestResult(
    val keywords: List<String>,
    val users: List<User> = emptyList(),
)

/**
 * 若命中推荐大V/官方号，将其放在第 5、6、7 位（1-based，最多 3 个），其余关键词顺延且去重；
 * 未命中或不足时有几个显示几个，无命中则全部正常显示关键词。
 */
fun buildSearchSuggestEntries(
    keywords: List<String>,
    users: List<User>,
): List<SearchSuggestEntry> {
    val recs = users.distinctBy { it.id }.take(3)
    if (recs.isEmpty()) return keywords.map { SearchSuggestEntry.Keyword(it) }
    val head = keywords.take(4).map { SearchSuggestEntry.Keyword(it) }
    val recNames = recs.mapTo(HashSet()) { it.name.lowercase() }
    val tail = keywords.drop(4)
        .filterNot { it.lowercase() in recNames }
        .map { SearchSuggestEntry.Keyword(it) }
    return head + recs.map { SearchSuggestEntry.Account(it) } + tail
}

/**
 * 为搜索联想第 5、6、7 位筛选命中的大V/官方推荐账号（最多 3 个）：
 * - 优先采纳联想接口直接带回的认证/高粉账号；
 * - 再用 `searchUsers` 顶部搜索结果补充：要求是认证账号（官方蓝V / 个人大V）或 10 万+ 粉丝大V，
 *   且昵称/简介与关键词相关（或首位 5 万+ 粉丝认证号，兼容拼音/英文检索如 xiaomi -> 小米公司）。
 */
internal fun filterRecommendedSearchUsers(
    query: String,
    users: List<User>,
    directRecommendations: List<User> = emptyList(),
    limit: Int = 3,
): List<User> {
    val q = query.trim().removePrefix("@").trim()
    if (q.isEmpty() || limit <= 0) return emptyList()
    val picked = LinkedHashMap<String, User>()
    for (u in directRecommendations) {
        if (picked.size >= limit) break
        if (u.verified || u.followers >= 10_000) {
            picked[u.id] = u
        }
    }
    users.forEachIndexed { index, u ->
        if (picked.size >= limit) return@forEachIndexed
        val isBigVOrOfficial = u.verified || u.followers >= 100_000
        if (!isBigVOrOfficial) return@forEachIndexed
        val nameMatches = u.name.contains(q, ignoreCase = true) || q.contains(u.name, ignoreCase = true)
        val descMatches = u.verified && u.description.contains(q, ignoreCase = true)
        val topVerifiedLead = index == 0 && u.verified && u.followers >= 50_000
        if (nameMatches || descMatches || topVerifiedLead) {
            picked.putIfAbsent(u.id, u)
        }
    }
    return picked.values.take(limit)
}

/**
 * 用户搜索（`100103type=3`）的卡片。用户挂在 `cards[].card_group[].user` 上，
 * 首页还可能多一张 `card_type` 不是 11 的筛选卡，所以逐张扫、只认带 user 的项。
 *
 * 搜索结果里的简介和粉丝数不总在 user 对象里：卡片项的 `desc1` 是简介、`desc2` 是
 * 「粉丝：58.8万」这种展示串，user 上没有对应字段时用它们补。
 */
fun parseUserCards(root: JSONObject?): List<User> {
    val data = root?.optJSONObject("data") ?: return emptyList()
    val users = LinkedHashMap<String, User>()
    val cards = data.optJSONArray("cards") ?: return emptyList()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        searchUserOf(card)?.let { if (it.id !in users) users[it.id] = it }
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val item = group.optJSONObject(j) ?: continue
            searchUserOf(item)?.let { if (it.id !in users) users[it.id] = it }
        }
    }
    return users.values.toList()
}

private fun searchUserOf(item: JSONObject): User? {
    val user = User.from(item.optJSONObject("user")) ?: return null
    val description = item.str("desc1").ifBlank { user.description }
    val followers = if (user.followers > 0) {
        user.followers
    } else {
        val raw = item.str("desc2").substringAfter("粉丝", "").trim('：', ':', ' ')
        parseCount(raw) ?: 0
    }
    return user.copy(description = description, followers = followers)
}

/**
 * 关注 / 粉丝列表的卡片。只认 `card_type` 11 且没有 `card_style` 的那几张：
 * 同一页里还夹着「相关用户」「推荐关注」这类卡，它们的用户挂在 `users` 数组上，
 * 混进来会把关系列表变成推荐页。列表项里的 `desc1` 才是简介。
 */
fun parseRelationUsers(root: JSONObject?): List<User> {
    val data = root?.optJSONObject("data") ?: return emptyList()
    val cards = data.optJSONArray("cards") ?: return emptyList()
    val users = LinkedHashMap<String, User>()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        if (card.optInt("card_type") != 11 || !card.isNull("card_style")) continue
        User.from(card.optJSONObject("user"))?.let { if (it.id !in users) users[it.id] = it }
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val item = group.optJSONObject(j) ?: continue
            val user = User.from(item.optJSONObject("user")) ?: continue
            val description = item.str("desc1").ifBlank { user.description }
            if (user.id !in users) users[user.id] = user.copy(description = description)
        }
    }
    return users.values.toList()
}

/**
 * 话题搜索（`100103type=38`）的卡片：`card_type` 11 的卡里，`card_type` 8 的项才是话题，
 * 名字在 `title_sub`（形如 `#话题#`），阅读/讨论数在 `desc1` / `desc2`。
 */
fun parseTopicCards(root: JSONObject?): List<TopicItem> {
    val data = root?.optJSONObject("data") ?: return emptyList()
    val topics = LinkedHashMap<String, TopicItem>()
    fun collectTopic(item: JSONObject) {
        val title = item.str("title_sub").trim('#', ' ', '\u00a0')
        if (title.isBlank()) return
        val scheme = item.str("scheme")
        val containerId = superTopicId(item.str("containerid")) ?: superTopicId(scheme)
        val key = containerId ?: scheme.ifBlank { title }
        if (key in topics) return
        val pic = normalizeWeiboMediaUrl(item.str("pic")).takeUnless { isGenericCardIcon(it) }.orEmpty()
        topics[key] = TopicItem(
            title = title,
            desc1 = item.str("desc1"),
            desc2 = item.str("desc2"),
            scheme = scheme,
            containerId = containerId,
            pic = pic,
        )
    }
    val cards = data.optJSONArray("cards") ?: return emptyList()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        collectTopic(card)
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val item = group.optJSONObject(j) ?: continue
            collectTopic(item)
        }
    }
    return topics.values.toList()
}
