package com.cobo.app.data

internal actual fun platformExtractMotionPhoto(bytes: ByteArray): MotionPhotoParts? {
    if (bytes.size < 64) return null
    val ftypIdx = findFtypBox(bytes)
    if (ftypIdx <= 16 || ftypIdx >= bytes.size - 16) return null
    val start = ftypIdx - 4
    if (start <= 0) return null
    return MotionPhotoParts(
        stillBytes = bytes.copyOfRange(0, start),
        videoBytes = bytes.copyOfRange(start, bytes.size),
        videoMime = "video/mp4",
    )
}

private fun findFtypBox(bytes: ByteArray): Int {
    for (i in 32 until bytes.size - 4) {
        if (bytes[i] == 0x66.toByte() &&
            bytes[i + 1] == 0x74.toByte() &&
            bytes[i + 2] == 0x79.toByte() &&
            bytes[i + 3] == 0x70.toByte()
        ) {
            return i
        }
    }
    return -1
}
