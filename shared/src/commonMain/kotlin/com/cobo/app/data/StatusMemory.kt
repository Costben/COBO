package com.cobo.app.data

object StatusMemory {
    private const val Limit = 80
    private val byId = LinkedHashMap<String, Status>()
    private val idByMid = HashMap<String, String>()

    fun remember(status: Status) {
        if (status.id.isBlank()) return
        byId.remove(status.id)
        byId[status.id] = status
        if (status.mid.isNotBlank()) idByMid[status.mid] = status.id
        while (byId.size > Limit) {
            val eldest = byId.keys.firstOrNull() ?: break
            byId.remove(eldest)
        }
    }

    fun find(idOrMid: String): Status? {
        if (idOrMid.isBlank()) return null
        byId[idOrMid]?.let { return it }
        val id = idByMid[idOrMid] ?: return null
        return byId[id]
    }
}
