package com.cobo.app.data

import com.cobo.app.platform.AndroidPlatformHolder
import java.io.File

internal actual fun platformExtractMotionPhoto(bytes: ByteArray): MotionPhotoParts? =
    AndroidMotionPhotoParser.extract(bytes)

/**
 * 将静图 + 实况视频字节流经 [MotionPhotoRemuxer] 规范化后由 [MotionPhotoExporter] 合成
 * 符合 Android Motion Photo 标准的单张 JPEG 字节。
 */
internal fun exportAndroidMotionPhotoBytes(imageBytes: ByteArray, videoBytes: ByteArray): ByteArray? {
    val ctx = AndroidPlatformHolder.appContext ?: return null
    val workDir = File(ctx.cacheDir, "live_export_${System.nanoTime()}").apply { mkdirs() }
    return try {
        val stillIn = File(workDir, "still_in.bin").apply { writeBytes(imageBytes) }
        val videoIn = File(workDir, "video_in.mov").apply { writeBytes(videoBytes) }
        val jpegFile = MotionPhotoRemuxer.ensureJpeg(stillIn, File(workDir, "still.jpg"))
        val mp4File = runCatching {
            MotionPhotoRemuxer.prepareMp4(videoIn, File(workDir, "motion.mp4"))
        }.getOrElse { videoIn }
        val outFile = File(workDir, "out.jpg")
        MotionPhotoExporter.export(
            sourceImage = jpegFile,
            video = mp4File,
            destination = outFile,
            presentationTimestampUs = -1L,
        )
        outFile.takeIf { it.isFile && it.length() > 0L }?.readBytes()
    } catch (t: Throwable) {
        com.cobo.app.platform.logWarn("CoboLiveSave", "合成 Android Motion Photo 失败，回退静图", t)
        null
    } finally {
        workDir.deleteRecursively()
    }
}
