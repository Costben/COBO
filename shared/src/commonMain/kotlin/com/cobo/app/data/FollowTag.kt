package com.cobo.app.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * 主页「更多」菜单里的三种关注标记，互斥，只存本机。
 *
 * 微博网页端只给得出「特别关注」这一个状态（`status.user.special_follow`），悄悄关注与
 * 本地关注在 m.weibo.cn 上没有可写的接口，所以三档统一按本地标记处理；读到服务端
 * `special_follow` 时用它给本地补一次初值，之后以本机记录为准。
 */
enum class FollowTag(val key: String, val label: String) {
    Special("special", "特别"),
    Secret("secret", "悄悄"),
    Local("local", "本地"),
    ;

    /** 菜单里显示的文字：特别关注 / 悄悄关注 / 本地关注。 */
    val menuLabel: String get() = label + "关注"

    companion object {
        fun of(key: String?): FollowTag? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 进程内的标记表：主页改完，信息流里同一张卡片立刻跟着变，不用等重新拉数据。
 * 每次写入都同步落到 `files/follows.json`。
 */
object FollowTags {
    private val tags = mutableStateMapOf<String, FollowTag>()
    private var store: Store? = null

    fun attach(store: Store) {
        this.store = store
        tags.clear()
        tags.putAll(store.followTags())
    }

    operator fun get(uid: String?): FollowTag? = if (uid.isNullOrBlank()) null else tags[uid]

    /** 有没有「记过」这件事：清掉标记也留一个空记录，免得服务端值把它补回来。 */
    fun has(uid: String): Boolean = tags.containsKey(uid) || store?.hasFollowTag(uid) == true

    /** 当前打了某个标记的全部 uid，用来拼「特别关注 / 悄悄关注 / 本地关注」这几条流。 */
    fun uids(tag: FollowTag): List<String> = tags.filterValues { it == tag }.keys.toList()

    fun set(uid: String, tag: FollowTag?) {
        if (uid.isBlank()) return
        if (tag == null) tags.remove(uid) else tags[uid] = tag
        store?.setFollowTag(uid, tag)
    }
}
