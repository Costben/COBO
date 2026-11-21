package com.cobo.app.data

/**
 * 最近在列表里画过的微博。
 *
 * 列表里的正文、图片、视频封面都已经在屏幕上了，点进详情不该再空转一个圈从零拉一遍：
 * 详情页第一帧先用这里顶住，接口回来再无声替换。只留最近几十条，id 与 mid 都能查到。
 */
object StatusMemory {
    private const val Limit = 80

    private val byId = object : LinkedHashMap<String, Status>(Limit, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Status>?) = size > Limit
    }
    private val idByMid = HashMap<String, String>()

    /** 列表每次画一条就登记一次；同一条重复画只是刷新最近使用顺序。 */
    fun remember(status: Status) {
        if (status.id.isBlank()) return
        byId[status.id] = status
        if (status.mid.isNotBlank()) idByMid[status.mid] = status.id
    }

    /** 按路由里给的 id / mid 取一条。取不到就是没在列表里见过，详情页照旧走加载态。 */
    fun find(idOrMid: String): Status? {
        if (idOrMid.isBlank()) return null
        byId[idOrMid]?.let { return it }
        val id = idByMid[idOrMid] ?: return null
        return byId[id]
    }
}
