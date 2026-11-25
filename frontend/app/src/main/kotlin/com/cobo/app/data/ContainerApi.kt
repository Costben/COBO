package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Share 3.9.6 客户端容器协议通道（`https://api.weibo.cn/2/`）。
 *
 * 所有客户端登录账号（`LOGIN_TYPE_CLIENT`）的业务读/写请求优先走这一层：
 * 用 `gsid` + `s` + `i` + `aid` + `cum` 签名认证，不依赖 `m.weibo.cn` 或 `weibo.com`
 * 的网页 `SUB` Cookie，因此网页侧会话被风控/踢出时仍可稳定使用。
 */
object ContainerApi {

    val AUTH_ERRNOS = setOf("-100", "21327", "21315", "21301", "50113002")

    private const val SHARE_SOURCE = "7501641714"
    private const val PIC_DISCOVERY_TTL_MS = 180_000L
    private const val DEFAULT_CHUNK_BYTES = 4096 * 1024

    data class UploadedMedia(
        val fid: String,
        val md5: String = "",
        val bypass: String = "unistore.image",
        val subtype: String = "",
        val videoFid: String = "",
    ) {
        val isLivePhoto: Boolean
            get() = subtype.isNotBlank() && videoFid.isNotBlank()
    }

    internal data class PicDiscovery(
        val initUrl: String,
        val uploadUrl: String,
        val bypass: String,
    )

    internal data class MediaDiscovery(
        val pic: PicDiscovery,
        val livePhoto: PicDiscovery?,
    )

    internal sealed interface PicUploadInit {
        val chunkBytes: Int
        val auth: String

        data class Mss(
            val uploadId: String,
            val mediaId: String,
            val uploadProtocol: String,
            val uploadUrl: String,
            override val chunkBytes: Int,
            override val auth: String,
        ) : PicUploadInit

        data class Legacy(
            val fileToken: String,
            val urlTag: String,
            val uploadUrl: String,
            override val chunkBytes: Int,
            override val auth: String,
        ) : PicUploadInit
    }

    private data class MediaDiscoveryCache(
        val uid: String,
        val fetchedAtMs: Long,
        val info: MediaDiscovery,
    )

    @Volatile
    private var mediaDiscoveryCache: MediaDiscoveryCache? = null

    private fun buildQuery(
        path: String,
        uid: String,
        gsid: String,
        aid: String,
        extra: Map<String, String?>,
    ): LinkedHashMap<String, String?> {
        val query = linkedMapOf<String, String?>()
        ClientLogin.putCommonParams(query, ClientLogin.FROM)
        query["s"] = ClientLogin.signInfo(uid)
        val iVal = ClientLogin.iValue()
        if (iVal.isNotBlank()) query["i"] = iVal
        query["gsid"] = gsid
        extra.forEach { (k, v) -> if (v != null) query[k] = v }
        ClientLogin.signQuery(path, query, aid)
        return query
    }

    private fun buildBodyParts(
        uid: String,
        gsid: String,
        extra: Map<String, String> = emptyMap(),
    ): LinkedHashMap<String, String> {
        val common = linkedMapOf<String, String?>()
        ClientLogin.putCommonParams(common, ClientLogin.FROM)
        val parts = linkedMapOf<String, String>()
        common.forEach { (k, v) -> if (v != null) parts[k] = v }
        parts["s"] = ClientLogin.signInfo(uid)
        parts["gsid"] = gsid
        extra.forEach { (k, v) -> parts[k] = v }
        return parts
    }

    internal fun checkSuccess(root: JSONObject): JSONObject {
        val errno = ClientLogin.errorCode(root)
        if (errno != null) {
            throw ApiException(ClientLogin.errorMessage(root), errno)
        }
        if (root.has("ok") && root.optInt("ok", 1) != 1) {
            throw ApiException(ClientLogin.errorMessage(root))
        }
        return root
    }

    suspend fun get(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        extra: Map<String, String?> = emptyMap(),
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, extra)
        val resp = Api.containerGetShare(path, query, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    suspend fun getRaw(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        extra: Map<String, String?> = emptyMap(),
    ): String {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, extra)
        val (text, validator) = Api.containerGetRawShare(path, query, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(validator)
        return text
    }

    suspend fun post(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        form: Map<String, String> = emptyMap(),
        queryExtra: Map<String, String?> = emptyMap(),
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, queryExtra)
        val resp = Api.containerPostShare(path, query, form, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    suspend fun postMultipart(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        parts: Map<String, String> = emptyMap(),
        queryExtra: Map<String, String?> = emptyMap(),
        includeAuthInBody: Boolean = true,
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, queryExtra)
        val body = if (includeAuthInBody) buildBodyParts(uid, gsid, parts) else parts
        val resp = Api.containerPostShare(
            path = path,
            query = query,
            form = body,
            headers = ClientLogin.validatorHeaders(uid),
            multipart = true,
        )
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    suspend fun postFileMultipart(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        parts: Map<String, String> = emptyMap(),
        fileField: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
        queryExtra: Map<String, String?> = emptyMap(),
        includeAuthInBody: Boolean = true,
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, queryExtra)
        val body = if (includeAuthInBody) buildBodyParts(uid, gsid, parts) else parts
        val resp = Api.containerPostFileShare(
            path = path,
            query = query,
            form = body,
            fileField = fileField,
            fileName = fileName,
            bytes = bytes,
            mimeType = mimeType,
            headers = ClientLogin.validatorHeaders(uid),
        )
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    /** 上传并更换头像：走 Share 容器通道 `account/avatar_upload`。 */
    suspend fun uploadAvatar(
        uid: String,
        gsid: String,
        aid: String = "",
        bytes: ByteArray,
        fileName: String = "avatar.jpg",
        mimeType: String = "image/jpeg",
        cropWidth: Int = 720,
    ): JSONObject {
        if (bytes.isEmpty()) throw ApiException("图片内容为空")
        val parts = mapOf(
            "trim_x" to "0",
            "trim_y" to "0",
            "trim_width" to cropWidth.coerceAtLeast(1).toString(),
        )
        return try {
            postFileMultipart(
                path = "account/avatar_upload",
                uid = uid,
                gsid = gsid,
                aid = aid,
                parts = parts,
                fileField = "avatar",
                fileName = fileName,
                bytes = bytes,
                mimeType = mimeType,
            )
        } catch (e: ApiException) {
            val msg = e.message.orEmpty()
            val retryWithImageField = e.errno in setOf("10008", "20002", "20003") ||
                msg.contains("image", ignoreCase = true) ||
                msg.contains("参数")
            if (!retryWithImageField) throw e
            postFileMultipart(
                path = "account/avatar_upload",
                uid = uid,
                gsid = gsid,
                aid = aid,
                parts = parts,
                fileField = "image",
                fileName = fileName,
                bytes = bytes,
                mimeType = mimeType,
            )
        }
    }

    /** 更新昵称 / 个人简介：走 Share 容器通道 `users/update`。 */
    suspend fun updateProfileBasic(
        uid: String,
        gsid: String,
        aid: String = "",
        screenName: String? = null,
        description: String? = null,
    ): JSONObject {
        val form = LinkedHashMap<String, String>()
        if (screenName != null) form["screen_name"] = screenName
        if (description != null) form["description"] = description
        if (form.isEmpty()) throw ApiException("没有需要修改的资料")
        return post(
            path = "users/update",
            uid = uid,
            gsid = gsid,
            aid = aid,
            form = form,
        )
    }

    /** 上传并更换主页背景图：先上传图片拿到媒体 ID，再提交 `profile/set/pure/covers`。 */
    suspend fun updateCover(
        uid: String,
        gsid: String,
        aid: String = "",
        bytes: ByteArray,
        fileName: String = "cover.jpg",
    ): JSONObject {
        val uploaded = uploadPic(
            uid = uid,
            gsid = gsid,
            aid = aid,
            bytes = bytes,
            fileName = fileName,
            md5Hex = md5Hex(bytes),
        )
        return post(
            path = "profile/set/pure/covers",
            uid = uid,
            gsid = gsid,
            aid = aid,
            form = mapOf(
                "covers" to uploaded.fid,
                "pic_id" to uploaded.fid,
            ),
        )
    }

    internal fun buildPublishMediaJson(media: List<UploadedMedia>): String {
        val arr = JSONArray()
        for (item in media) {
            val obj = JSONObject()
                .put("createtype", "localfile")
                .put("fid", item.fid)
                .put("type", "pic")
                .put("pic_source", "0")
                .put("picStatus", "0")
            if (item.bypass.isNotBlank()) obj.put("bypass", item.bypass)
            if (item.md5.isNotBlank()) obj.put("pic_raw_md5", item.md5)
            if (item.isLivePhoto) {
                obj.put("subtype", item.subtype)
                obj.put("video", item.videoFid)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    internal fun buildPublishParts(
        text: String,
        visible: Int = 0,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = linkedMapOf<String, String>()
        parts["content"] = text
        parts["visible"] = visible.toString()
        if (media.isNotEmpty()) {
            parts["media"] = buildPublishMediaJson(media)
        }
        return parts
    }

    internal fun buildEditWeiboParts(
        mid: String,
        text: String,
        visible: Int = 0,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = buildPublishParts(text, visible, media)
        parts["mid"] = mid
        return parts
    }

    internal fun buildRepostParts(
        id: String,
        text: String,
        visible: Int = 0,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = linkedMapOf<String, String>()
        parts["id"] = id
        parts["status"] = text
        parts["visible"] = visible.toString()
        if (media.isNotEmpty()) {
            parts["media"] = buildPublishMediaJson(media)
        }
        return parts
    }

    internal fun buildEditRepostParts(
        mid: String,
        originalId: String,
        text: String,
        visible: Int = 0,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = buildRepostParts(originalId, text, visible, media)
        parts["mid"] = mid
        return parts
    }

    internal fun buildCommentParts(
        id: String,
        text: String,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = linkedMapOf<String, String>()
        parts["id"] = id
        parts["comment"] = if (media.isNotEmpty() && text.isBlank()) "图片评论" else text
        if (media.isNotEmpty()) {
            parts["media"] = buildPublishMediaJson(media)
        }
        return parts
    }

    internal fun buildReplyCommentParts(
        id: String,
        cid: String,
        text: String,
        media: List<UploadedMedia> = emptyList(),
    ): LinkedHashMap<String, String> {
        val parts = linkedMapOf<String, String>()
        parts["id"] = id
        parts["cid"] = cid
        parts["comment"] = if (media.isNotEmpty() && text.isBlank()) "图片评论" else text
        if (media.isNotEmpty()) {
            parts["media"] = buildPublishMediaJson(media)
        }
        return parts
    }

    internal fun parseMediaDiscovery(root: JSONObject): MediaDiscovery {
        val pic = root.optJSONObject("pic") ?: root.optJSONObject("image")
        val picInitUrl = pic?.str("init_url").orEmpty()
        val picUploadUrl = pic?.str("upload_url").orEmpty()
        val picBypass = pic?.str("bypass").orEmpty().ifBlank { "unistore.image" }
        if (picInitUrl.isBlank()) throw ApiException("获取图片上传配置失败")
        val live = root.optJSONObject("livephoto")
        val liveInitUrl = live?.str("init_url").orEmpty()
        val liveDiscovery = if (liveInitUrl.isNotBlank()) {
            PicDiscovery(
                initUrl = liveInitUrl,
                uploadUrl = live?.str("upload_url").orEmpty(),
                bypass = live?.str("bypass").orEmpty().ifBlank { "unistore.video" },
            )
        } else {
            null
        }
        return MediaDiscovery(
            pic = PicDiscovery(initUrl = picInitUrl, uploadUrl = picUploadUrl, bypass = picBypass),
            livePhoto = liveDiscovery,
        )
    }

    internal fun parsePicDiscovery(root: JSONObject): PicDiscovery =
        parseMediaDiscovery(root).pic

    internal fun parseUploadInit(root: JSONObject, fallbackUploadUrl: String): PicUploadInit {
        checkSuccess(root)
        val auth = root.str("auth")
        val uploadId = root.str("upload_id")
        if (uploadId.isNotBlank()) {
            val mediaId = root.str("media_id")
            val strategy = root.optJSONObject("strategy")
            val chunkKb = strategy?.int("chunk_size", 4096) ?: 4096
            val chunkBytes = if (chunkKb > 0) chunkKb * 1024 else DEFAULT_CHUNK_BYTES
            val uploadProtocol = strategy?.str("upload_protocol").orEmpty()
            if (mediaId.isBlank() || fallbackUploadUrl.isBlank()) {
                throw ApiException("图片上传初始化失败")
            }
            return PicUploadInit.Mss(
                uploadId = uploadId,
                mediaId = mediaId,
                uploadProtocol = uploadProtocol,
                uploadUrl = fallbackUploadUrl,
                chunkBytes = chunkBytes,
                auth = auth,
            )
        }
        val fileToken = root.str("fileToken")
        val urlTag = root.str("urlTag")
        val chunkKb = root.int("length", 4096)
        val chunkBytes = if (chunkKb > 0) chunkKb * 1024 else DEFAULT_CHUNK_BYTES
        val uploadUrl = root.str("upload_url").ifBlank { fallbackUploadUrl }
        if (fileToken.isBlank() || uploadUrl.isBlank()) {
            throw ApiException("图片上传初始化失败")
        }
        return PicUploadInit.Legacy(
            fileToken = fileToken,
            urlTag = urlTag,
            uploadUrl = uploadUrl,
            chunkBytes = chunkBytes,
            auth = auth,
        )
    }

    private suspend fun discoverUploadEndpoints(uid: String, gsid: String, aid: String): MediaDiscovery {
        val now = System.currentTimeMillis()
        val cached = mediaDiscoveryCache
        if (cached != null && cached.uid == uid && now - cached.fetchedAtMs < PIC_DISCOVERY_TTL_MS) {
            return cached.info
        }
        val root = get(
            path = "multimedia/multidiscovery",
            uid = uid,
            gsid = gsid,
            aid = aid,
            extra = mapOf(
                "source" to SHARE_SOURCE,
                "moduleID" to "700",
                "version" to "3",
                "auth_accept" to "video",
            ),
        )
        val info = parseMediaDiscovery(root)
        mediaDiscoveryCache = MediaDiscoveryCache(uid = uid, fetchedAtMs = now, info = info)
        return info
    }

    private fun uploadNetworkStatus(): String = when (Api.networkType()) {
        "wifi" -> "wifi"
        "4g" -> "4g"
        else -> "N/A"
    }

    internal fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun buildUploadQueryBase(
        uid: String,
        gsid: String,
        aid: String,
        includeGsid: Boolean,
        includeSource: Boolean,
    ): LinkedHashMap<String, String?> {
        val query = linkedMapOf<String, String?>()
        ClientLogin.putCommonParams(query, ClientLogin.FROM)
        query["s"] = ClientLogin.signInfo(uid)
        if (includeGsid) query["gsid"] = gsid
        if (includeSource) {
            query["source"] = SHARE_SOURCE
            query["moduleID"] = "700"
        }
        val iVal = ClientLogin.iValue()
        if (iVal.isNotBlank()) query["i"] = iVal
        query["status"] = uploadNetworkStatus()
        if (aid.isNotBlank()) query["aid"] = aid
        return query
    }

    private suspend fun uploadFile(
        uid: String,
        gsid: String,
        aid: String,
        endpoint: PicDiscovery,
        uploadType: String,
        bytes: ByteArray,
        fileName: String,
        md5Hex: String,
        ori: String = "0",
    ): String {
        val initProps = JSONObject().put("ori", ori).toString()
        val initQuery = buildUploadQueryBase(
            uid = uid,
            gsid = gsid,
            aid = aid,
            includeGsid = true,
            includeSource = false,
        ).apply {
            put("type", uploadType)
            put("check", md5Hex)
            put("name", fileName)
            put("length", bytes.size.toString())
            put("mediaprops", initProps)
        }
        val initResp = Api.containerUrlGetShare(endpoint.initUrl, initQuery, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(initResp.xValidator)
        val plan = parseUploadInit(initResp.json, endpoint.uploadUrl)

        val oriInt = ori.toIntOrNull() ?: 0
        val totalChunks = (bytes.size + plan.chunkBytes - 1) / plan.chunkBytes
        var legacyFid = ""
        for (index in 0 until totalChunks) {
            val start = index * plan.chunkBytes
            val end = minOf(bytes.size, start + plan.chunkBytes)
            val chunk = if (start == 0 && end == bytes.size) bytes else bytes.copyOfRange(start, end)
            val chunkMd5 = if (totalChunks == 1) md5Hex else md5Hex(chunk)
            val headers = ClientLogin.validatorHeaders(uid).apply {
                if (plan.auth.isNotBlank()) put("X-Up-Auth", plan.auth)
            }
            val uploadQuery = buildUploadQueryBase(
                uid = uid,
                gsid = gsid,
                aid = aid,
                includeGsid = plan.auth.isBlank(),
                includeSource = true,
            )
            val targetUrl = when (plan) {
                is PicUploadInit.Mss -> {
                    uploadQuery["uid"] = uid
                    uploadQuery["count"] = totalChunks.toString()
                    uploadQuery["index"] = index.toString()
                    uploadQuery["size"] = chunk.size.toString()
                    uploadQuery["check"] = chunkMd5
                    uploadQuery["startloc"] = start.toString()
                    uploadQuery["upload_id"] = plan.uploadId
                    uploadQuery["media_id"] = plan.mediaId
                    uploadQuery["upload_protocol"] = plan.uploadProtocol
                    plan.uploadUrl
                }
                is PicUploadInit.Legacy -> {
                    val sendProps = JSONObject()
                        .put("ori", oriInt)
                        .put("pri", 0)
                        .put("print_mark", 0)
                        .toString()
                    uploadQuery["act"] = "send"
                    uploadQuery["chunkcount"] = totalChunks.toString()
                    uploadQuery["chunkindex"] = index.toString()
                    uploadQuery["chunksize"] = chunk.size.toString()
                    uploadQuery["urltag"] = plan.urlTag
                    uploadQuery["sectioncheck"] = chunkMd5
                    uploadQuery["startloc"] = start.toString()
                    uploadQuery["filecheck"] = md5Hex
                    uploadQuery["filetoken"] = plan.fileToken
                    uploadQuery["filelength"] = bytes.size.toString()
                    uploadQuery["type"] = uploadType
                    uploadQuery["file_source"] = "9"
                    uploadQuery["mediaprops"] = sendProps
                    plan.uploadUrl
                }
            }
            val resp = Api.containerUrlPostBytesShare(
                fullUrl = targetUrl,
                query = uploadQuery,
                bytes = chunk,
                headers = headers,
            )
            ClientLogin.recordValidator(resp.xValidator)
            checkSuccess(resp.json)
            when (plan) {
                is PicUploadInit.Mss -> {
                    if (!resp.json.optBoolean("result", false)) {
                        throw ApiException(ClientLogin.errorMessage(resp.json))
                    }
                }
                is PicUploadInit.Legacy -> {
                    val id = if (uploadType == "livephoto") {
                        resp.json.str("usid")
                            .ifBlank { resp.json.str("fid") }
                            .ifBlank { resp.json.str("pic_id") }
                    } else {
                        resp.json.str("pic_id")
                            .ifBlank { resp.json.str("fid") }
                            .ifBlank { resp.json.str("usid") }
                    }
                    if (id.isNotBlank()) legacyFid = id
                }
            }
        }
        return when (plan) {
            is PicUploadInit.Mss -> plan.mediaId
            is PicUploadInit.Legacy -> legacyFid.ifBlank {
                throw ApiException(if (uploadType == "livephoto") "实况视频上传失败" else "图片上传失败")
            }
        }
    }

    suspend fun uploadPic(
        uid: String,
        gsid: String,
        aid: String = "",
        bytes: ByteArray,
        fileName: String,
        md5Hex: String,
    ): UploadedMedia {
        if (bytes.isEmpty()) throw ApiException("图片内容为空")
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val discovery = discoverUploadEndpoints(uid, gsid, resolvedAid)
        val fid = uploadFile(
            uid = uid,
            gsid = gsid,
            aid = resolvedAid,
            endpoint = discovery.pic,
            uploadType = "pic",
            bytes = bytes,
            fileName = fileName,
            md5Hex = md5Hex,
            ori = "0",
        )
        return UploadedMedia(fid = fid, md5 = md5Hex, bypass = discovery.pic.bypass)
    }

    suspend fun uploadLivePhoto(
        uid: String,
        gsid: String,
        aid: String = "",
        stillBytes: ByteArray,
        videoBytes: ByteArray,
        stillFileName: String = "pic.jpg",
        videoFileName: String = "live.mov",
        subtype: String = "livephoto",
    ): UploadedMedia {
        if (stillBytes.isEmpty()) throw ApiException("图片内容为空")
        if (videoBytes.isEmpty()) throw ApiException("实况视频内容为空")
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val discovery = discoverUploadEndpoints(uid, gsid, resolvedAid)
        val liveEndpoint = discovery.livePhoto ?: throw ApiException("获取实况图上传配置失败")
        val stillMd5 = md5Hex(stillBytes)
        val videoMd5 = md5Hex(videoBytes)
        val picFid = uploadFile(
            uid = uid,
            gsid = gsid,
            aid = resolvedAid,
            endpoint = discovery.pic,
            uploadType = "pic",
            bytes = stillBytes,
            fileName = stillFileName,
            md5Hex = stillMd5,
            ori = "1",
        )
        val videoUsid = uploadFile(
            uid = uid,
            gsid = gsid,
            aid = resolvedAid,
            endpoint = liveEndpoint,
            uploadType = "livephoto",
            bytes = videoBytes,
            fileName = videoFileName,
            md5Hex = videoMd5,
            ori = "1",
        )
        return UploadedMedia(
            fid = picFid,
            md5 = stillMd5,
            bypass = discovery.pic.bypass,
            subtype = subtype,
            videoFid = videoUsid,
        )
    }

    /**
     * `page/get_search_suggest` 返回顶层数组：`["关键词", [{"card": {...}}, ...]]`。
     * 兼容普通词条（`desc_dark` / `desc`）以及内嵌大V卡片（`card.user` / `card.users`）。
     */
    internal fun parseSearchSuggestResult(rawText: String): SearchSuggestResult {
        val root = runCatching { JSONArray(rawText) }.getOrNull() ?: return SearchSuggestResult(emptyList())
        val arr = root.optJSONArray(1) ?: return SearchSuggestResult(emptyList())
        val keywords = ArrayList<String>(arr.length())
        val directUsers = LinkedHashMap<String, User>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val card = item.optJSONObject("card") ?: item
            val embeddedUser = User.from(card.optJSONObject("user") ?: item.optJSONObject("user"))
            if (embeddedUser != null) {
                directUsers.putIfAbsent(embeddedUser.id, embeddedUser)
            }
            card.optJSONArray("users")?.let { arrUsers ->
                for (j in 0 until arrUsers.length()) {
                    User.from(arrUsers.optJSONObject(j))?.let { directUsers.putIfAbsent(it.id, it) }
                }
            }
            if (embeddedUser == null) {
                val word = card.str("desc_dark").ifBlank { card.str("desc") }.trim()
                if (word.isNotBlank() && word !in keywords) keywords.add(word)
            }
        }
        return SearchSuggestResult(keywords = keywords, users = directUsers.values.toList())
    }

    /** `page/get_search_suggest` 词条快捷解析（保留供现有调用/测试复用）。 */
    fun parseSearchSuggest(rawText: String): List<String> =
        parseSearchSuggestResult(rawText).keywords
}
