package com.cobo.app.data

import com.cobo.app.platform.PickedMediaRegistry
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformUri

data class MotionPhotoParts(
    val stillBytes: ByteArray,
    val videoBytes: ByteArray,
    val videoMime: String = "video/mp4",
) {
    val imageBytes: ByteArray get() = stillBytes
}

data class MotionPhotoInspection(val hasMotionPhoto: Boolean)

object MotionPhotoParser {
    fun inspect(context: PlatformContext, uri: PlatformUri): MotionPhotoInspection =
        MotionPhotoInspection(hasMotionPhoto = isMotionPhoto(context, uri))

    fun hasJpegHeader(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF

    fun isMotionPhoto(@Suppress("UNUSED_PARAMETER") context: PlatformContext, uri: PlatformUri): Boolean {
        val picked = PickedMediaRegistry.get(uri)
        if (picked?.liveVideoBytes != null && picked.liveVideoBytes.isNotEmpty()) return true
        val bytes = PickedMediaRegistry.readBytes(uri) ?: return false
        return extract(bytes) != null
    }

    fun extract(@Suppress("UNUSED_PARAMETER") context: PlatformContext, uri: PlatformUri): MotionPhotoParts? {
        val picked = PickedMediaRegistry.get(uri)
        if (picked != null && picked.liveVideoBytes != null && picked.liveVideoBytes.isNotEmpty()) {
            return MotionPhotoParts(
                stillBytes = picked.bytes,
                videoBytes = picked.liveVideoBytes,
                videoMime = picked.liveVideoMime.ifBlank { "video/quicktime" },
            )
        }
        val bytes = PickedMediaRegistry.readBytes(uri) ?: return null
        return extract(bytes)
    }

    fun extract(bytes: ByteArray): MotionPhotoParts? {
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
}
