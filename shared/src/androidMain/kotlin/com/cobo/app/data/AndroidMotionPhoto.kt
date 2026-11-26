package com.cobo.app.data

import android.content.Context
import android.net.Uri
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class MotionPhotoInfo(
    val hasUltraHdr: Boolean,
    val declaresMotionPhoto: Boolean,
    val embeddedVideoLength: Long?,
    val declaredVideoLength: Long?,
    val presentationTimestampUs: Long?,
) {
    val hasMotionPhoto: Boolean
        get() = declaresMotionPhoto || embeddedVideoLength != null
}

/**
 * 解析 JPEG 中的 XMP 与尾部内嵌视频，校验生成的实况图是否符合 Android Motion Photo 结构。
 */
internal object AndroidMotionPhotoParser {

    private const val MAX_XMP_SCAN_BYTES = 512 * 1024
    private const val MAX_TAIL_SCAN_BYTES = 32L * 1024 * 1024
    private const val MIN_STILL_JPEG_BYTES = 16

    private val itemTagRegex = Regex("""<[^>]+Item:Mime="video/(?:mp4|quicktime)"[^>]*>""")
    private val itemElementRegex =
        Regex("""<[^/>\s]*:Item\b[^>]*>.*?</[^/>\s]*:Item\s*>""", RegexOption.DOT_MATCHES_ALL)
    private val lengthRegex = Regex("""Item:Length="(\d+)"""")
    private val elementLengthRegex =
        Regex("""<[^/>\s]*:Length\s*>\s*(\d+)\s*</[^/>\s]*:Length\s*>""")
    private val microVideoOffsetRegex = Regex("""GCamera:MicroVideoOffset="(\d+)"""")
    private val onePlusVideoLengthRegex = Regex("""OpCamera:VideoLength="(\d+)"""")
    private val presentationTimestampRegex =
        Regex("""(?:GCamera:)?MotionPhotoPresentationTimestampUs="(-?\d+)"""")

    private val mp4TopLevelBoxes = setOf("moov", "mdat", "free", "wide", "skip", "uuid", "meta")

    fun hasJpegHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            (bytes[0].toInt() and 0xff) == 0xff &&
            (bytes[1].toInt() and 0xff) == 0xd8

    fun hasJpegHeader(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 4L) return false
        file.inputStream().use { input ->
            input.read() == 0xff && input.read() == 0xd8
        }
    }.getOrDefault(false)

    fun isIsoBmff(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()

    fun isIsoBmff(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 12L) return false
        file.inputStream().use { input ->
            val header = ByteArray(8)
            input.read(header) == header.size &&
                header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))
        }
    }.getOrDefault(false)

    fun ftypMajorBrand(file: File): String? = runCatching {
        if (!file.isFile || file.length() < 12L) return null
        file.inputStream().use { input ->
            val header = ByteArray(12)
            if (input.read(header) != header.size) return null
            if (!header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))) {
                return null
            }
            String(header, 8, 4, StandardCharsets.US_ASCII)
        }
    }.getOrNull()

    fun parse(
        bytes: ByteArray,
        expectMotionPhoto: Boolean = false,
    ): MotionPhotoInfo {
        if (bytes.size < 16) {
            return MotionPhotoInfo(false, false, null, null, null)
        }
        val prefixLen = minOf(bytes.size, MAX_XMP_SCAN_BYTES)
        val xmp = String(bytes, 0, prefixLen, StandardCharsets.ISO_8859_1)
        val hasUltraHdr = xmp.contains("hdrgm:Version", ignoreCase = true)
        val declaresMotionPhoto = expectMotionPhoto || declaresMotionPhotoXmp(xmp)
        val declaredVideoLength = findDeclaredVideoLength(xmp)
        val embeddedVideoLength = declaredVideoLength
            ?.takeIf {
                (declaresMotionPhoto || expectMotionPhoto) &&
                    it in 12 until bytes.size.toLong() && hasFtypAtTail(bytes, it.toInt())
            }
            ?: if (declaresMotionPhoto || expectMotionPhoto) {
                findMp4LengthInBytes(bytes, requireFollowingBox = false)
            } else if (hasJpegHeader(bytes)) {
                findMp4LengthInBytes(bytes, requireFollowingBox = true)
            } else {
                null
            }
        val presentationTimestampUs = presentationTimestampRegex.find(xmp)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

        return MotionPhotoInfo(
            hasUltraHdr = hasUltraHdr,
            declaresMotionPhoto = declaresMotionPhoto || embeddedVideoLength != null,
            embeddedVideoLength = embeddedVideoLength,
            declaredVideoLength = declaredVideoLength,
            presentationTimestampUs = presentationTimestampUs,
        )
    }

    /**
     * 将 Android Motion Photo 拆分为静图 JPEG 与内嵌 MP4 视频字节数组；非实况图返回 `null`。
     */
    fun extract(bytes: ByteArray): MotionPhotoParts? {
        if (!hasJpegHeader(bytes)) return null
        val info = parse(bytes)
        val videoLenLong = info.embeddedVideoLength ?: return null
        if (videoLenLong < 12L || videoLenLong >= bytes.size.toLong()) return null
        val videoLen = videoLenLong.toInt()
        val stillLen = bytes.size - videoLen
        if (stillLen < MIN_STILL_JPEG_BYTES) return null
        if (!hasFtypAtTail(bytes, videoLen)) return null
        return MotionPhotoParts(
            stillBytes = bytes.copyOfRange(0, stillLen),
            videoBytes = bytes.copyOfRange(stillLen, bytes.size),
        )
    }

    /**
     * 低内存探测相册 Uri 是否为 Android 实况图（优先只读头部 512KB XMP 与尾部 `ftyp` 偏移）。
     */
    fun inspect(context: Context, uri: Uri): MotionPhotoInfo = runCatching {
        val resolver = context.contentResolver
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val fileSize = channel.size()
                if (fileSize >= 16L) {
                    val prefixLen = minOf(fileSize, MAX_XMP_SCAN_BYTES.toLong()).toInt()
                    val prefixBytes = readChannelRange(channel, 0L, prefixLen)
                    if (!hasJpegHeader(prefixBytes)) {
                        return@runCatching MotionPhotoInfo(false, false, null, null, null)
                    }
                    val xmp = String(prefixBytes, StandardCharsets.ISO_8859_1)
                    val hasUltraHdr = xmp.contains("hdrgm:Version", ignoreCase = true)
                    val declaresMotionPhoto = declaresMotionPhotoXmp(xmp)
                    val declaredVideoLength = findDeclaredVideoLength(xmp)
                    val presentationTimestampUs = presentationTimestampRegex.find(xmp)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()

                    val verifiedDeclared = declaredVideoLength?.takeIf {
                        declaresMotionPhoto &&
                            it in 12 until fileSize &&
                            hasFtypAtChannelOffset(channel, fileSize - it)
                    }
                    if (verifiedDeclared != null) {
                        return@runCatching MotionPhotoInfo(
                            hasUltraHdr = hasUltraHdr,
                            declaresMotionPhoto = true,
                            embeddedVideoLength = verifiedDeclared,
                            declaredVideoLength = declaredVideoLength,
                            presentationTimestampUs = presentationTimestampUs,
                        )
                    }

                    // 未声明准确偏移时（如三星 MotionPhoto_Data 或部分机型），扫描尾部验证 MP4 盒子
                    val embedded = if (fileSize <= MAX_TAIL_SCAN_BYTES) {
                        val all = readChannelRange(channel, 0L, fileSize.toInt())
                        findMp4LengthInBytes(all, requireFollowingBox = !declaresMotionPhoto)
                    } else if (declaresMotionPhoto) {
                        val scanLen = minOf(fileSize, MAX_TAIL_SCAN_BYTES).toInt()
                        val tail = readChannelRange(channel, fileSize - scanLen, scanLen)
                        findMp4LengthInBytes(tail, requireFollowingBox = false)
                    } else {
                        null
                    }
                    return@runCatching MotionPhotoInfo(
                        hasUltraHdr = hasUltraHdr,
                        declaresMotionPhoto = embedded != null,
                        embeddedVideoLength = embedded,
                        declaredVideoLength = declaredVideoLength,
                        presentationTimestampUs = presentationTimestampUs,
                    )
                }
            }
        }
        resolver.openInputStream(uri)?.use { input ->
            parse(input.readBytes())
        } ?: MotionPhotoInfo(false, false, null, null, null)
    }.getOrDefault(MotionPhotoInfo(false, false, null, null, null))

    fun parse(
        file: File,
        expectMotionPhoto: Boolean = false,
    ): MotionPhotoInfo {
        if (!file.isFile || file.length() < 16L) {
            return MotionPhotoInfo(false, false, null, null, null)
        }

        val xmp = readPrefix(file)
        val hasUltraHdr = xmp.contains("hdrgm:Version", ignoreCase = true)
        val declaresMotionPhoto = expectMotionPhoto || declaresMotionPhotoXmp(xmp)
        val declaredVideoLength = findDeclaredVideoLength(xmp)
        val embeddedVideoLength = declaredVideoLength
            ?.takeIf {
                (declaresMotionPhoto || expectMotionPhoto) &&
                    it in 12 until file.length() && hasFtypAtTail(file, it)
            }
            ?: if (declaresMotionPhoto || expectMotionPhoto) {
                findMp4LengthFromTail(file)
            } else if (hasJpegHeader(file)) {
                findMp4LengthFromTail(file, requireFollowingBox = true)
            } else {
                null
            }
        val presentationTimestampUs = presentationTimestampRegex.find(xmp)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

        return MotionPhotoInfo(
            hasUltraHdr = hasUltraHdr,
            declaresMotionPhoto = declaresMotionPhoto || embeddedVideoLength != null,
            embeddedVideoLength = embeddedVideoLength,
            declaredVideoLength = declaredVideoLength,
            presentationTimestampUs = presentationTimestampUs,
        )
    }

    private fun declaresMotionPhotoXmp(xmp: String): Boolean =
        xmp.contains("GCamera:MotionPhoto=\"1\"") ||
            xmp.contains("GCamera:MicroVideo=\"1\"") ||
            xmp.contains("OpCamera:OLivePhotoVersion=\"")

    private fun findDeclaredVideoLength(xmp: String): Long? =
        findContainerVideoLength(xmp)
            ?: microVideoOffsetRegex.find(xmp)?.groupValues?.get(1)?.toLongOrNull()
            ?: onePlusVideoLengthRegex.find(xmp)?.groupValues?.get(1)?.toLongOrNull()

    private fun readChannelRange(channel: java.nio.channels.FileChannel, offset: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        val buf = ByteBuffer.wrap(out)
        channel.position(offset)
        while (buf.hasRemaining()) {
            val read = channel.read(buf)
            if (read <= 0) break
        }
        return if (buf.position() == length) out else out.copyOf(buf.position())
    }

    private fun hasFtypAtChannelOffset(channel: java.nio.channels.FileChannel, offset: Long): Boolean = runCatching {
        val type = readChannelRange(channel, offset + 4L, 4)
        type.size == 4 && String(type, StandardCharsets.US_ASCII) == "ftyp"
    }.getOrDefault(false)

    private fun hasFtypAtTail(bytes: ByteArray, videoLength: Int): Boolean {
        val offset = bytes.size - videoLength
        if (offset < 0 || offset + 8 > bytes.size) return false
        return bytes[offset + 4] == 'f'.code.toByte() &&
            bytes[offset + 5] == 't'.code.toByte() &&
            bytes[offset + 6] == 'y'.code.toByte() &&
            bytes[offset + 7] == 'p'.code.toByte()
    }

    private fun findMp4LengthInBytes(
        bytes: ByteArray,
        requireFollowingBox: Boolean = false,
    ): Long? {
        for (index in 4 until bytes.size - 4) {
            if (bytes[index] == 'f'.code.toByte() &&
                bytes[index + 1] == 't'.code.toByte() &&
                bytes[index + 2] == 'y'.code.toByte() &&
                bytes[index + 3] == 'p'.code.toByte()
            ) {
                val boxSize = ((bytes[index - 4].toInt() and 0xff) shl 24) or
                    ((bytes[index - 3].toInt() and 0xff) shl 16) or
                    ((bytes[index - 2].toInt() and 0xff) shl 8) or
                    (bytes[index - 1].toInt() and 0xff)
                if (boxSize in 12..4096) {
                    val ftypStart = index - 4
                    if (requireFollowingBox) {
                        val nextBoxOffset = ftypStart + boxSize
                        if (nextBoxOffset + 8 > bytes.size) continue
                        val nextType = String(bytes, nextBoxOffset + 4, 4, StandardCharsets.US_ASCII)
                        if (nextType !in mp4TopLevelBoxes) continue
                    }
                    return (bytes.size - ftypStart).toLong()
                }
            }
        }
        return null
    }

    private fun readPrefix(file: File): String {
        val length = minOf(file.length(), MAX_XMP_SCAN_BYTES.toLong()).toInt()
        val bytes = ByteArray(length)
        RandomAccessFile(file, "r").use { input ->
            input.readFully(bytes)
        }
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun findContainerVideoLength(xmp: String): Long? {
        itemTagRegex.find(xmp)?.value?.let { tag ->
            lengthRegex.find(tag)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        }
        return itemElementRegex.findAll(xmp)
            .firstOrNull { it.value.contains("video/mp4") || it.value.contains("video/quicktime") }
            ?.let { match ->
                lengthRegex.find(match.value)?.groupValues?.get(1)?.toLongOrNull()
                    ?: elementLengthRegex.find(match.value)?.groupValues?.get(1)?.toLongOrNull()
            }
    }

    private fun hasFtypAtTail(file: File, videoLength: Long): Boolean {
        val offset = file.length() - videoLength
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset + 4L)
                val type = ByteArray(4)
                input.readFully(type)
                String(type, StandardCharsets.US_ASCII) == "ftyp"
            }
        }.getOrDefault(false)
    }

    private fun findMp4LengthFromTail(
        file: File,
        requireFollowingBox: Boolean = false,
    ): Long? = runCatching {
        val scanLength = minOf(file.length(), 64L * 1024 * 1024).toInt()
        val scanOffset = file.length() - scanLength
        val bytes = ByteArray(scanLength)
        RandomAccessFile(file, "r").use { input ->
            input.seek(scanOffset)
            input.readFully(bytes)
        }
        for (index in 4 until bytes.size - 4) {
            if (bytes[index] == 'f'.code.toByte() &&
                bytes[index + 1] == 't'.code.toByte() &&
                bytes[index + 2] == 'y'.code.toByte() &&
                bytes[index + 3] == 'p'.code.toByte()
            ) {
                val boxSize = ((bytes[index - 4].toInt() and 0xff) shl 24) or
                    ((bytes[index - 3].toInt() and 0xff) shl 16) or
                    ((bytes[index - 2].toInt() and 0xff) shl 8) or
                    (bytes[index - 1].toInt() and 0xff)
                if (boxSize in 12..4096) {
                    val ftypStart = index - 4
                    if (requireFollowingBox) {
                        val nextBoxOffset = ftypStart + boxSize
                        if (nextBoxOffset + 8 > bytes.size) continue
                        val nextType = String(bytes, nextBoxOffset + 4, 4, StandardCharsets.US_ASCII)
                        if (nextType !in mp4TopLevelBoxes) continue
                    }
                    val mp4Offset = scanOffset + ftypStart.toLong()
                    return@runCatching file.length() - mp4Offset
                }
            }
        }
        null
    }.getOrNull()
}

/**
 * 将静图 JPEG 与标准 MP4 合成 Android 通用 Motion Photo（Google Motion Photo v1 + MicroVideo v1），
 * 兼容 ColorOS / Google Photos / HyperOS / OriginOS / OneUI 等系统相册识别播放。
 * 若原 JPEG 已带有 Ultra HDR GainMap 等 Container:Directory 项，则完整保留它们并在末尾追加 MotionPhoto 项。
 */
internal object MotionPhotoExporter {

    fun export(
        sourceImage: File,
        video: File?,
        destination: File,
        presentationTimestampUs: Long? = null,
    ): File {
        require(sourceImage.isFile && AndroidMotionPhotoParser.hasJpegHeader(sourceImage)) {
            "Motion photo source image is not a readable JPEG"
        }
        destination.parentFile?.mkdirs()
        val staging = File(destination.parentFile, "${destination.name}.part")
        if (staging.exists()) staging.delete()

        try {
            val sourceInfo = AndroidMotionPhotoParser.parse(sourceImage, expectMotionPhoto = true)
            sourceImage.copyTo(staging, overwrite = true)
            if (sourceInfo.embeddedVideoLength == null) {
                val motionVideo = requireNotNull(video?.takeIf { it.isFile && AndroidMotionPhotoParser.isIsoBmff(it) }) {
                    "Motion photo video is unavailable or not an MP4/ISO-BMFF stream"
                }
                val timestampUs = presentationTimestampUs
                    ?: sourceInfo.presentationTimestampUs
                    ?: 0L
                updateMotionPhotoXmp(
                    image = staging,
                    videoLength = motionVideo.length(),
                    presentationTimestampUs = timestampUs,
                )
                FileOutputStream(staging, true).use { output ->
                    motionVideo.inputStream().use { input -> input.copyTo(output) }
                }
            }

            val exportedInfo = AndroidMotionPhotoParser.parse(staging, expectMotionPhoto = true)
            check(exportedInfo.embeddedVideoLength != null) {
                "Exported file does not contain a readable motion photo video"
            }
            if (destination.exists()) check(destination.delete())
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
                check(staging.delete())
            }
            return destination
        } catch (error: Throwable) {
            staging.delete()
            throw error
        }
    }

    private fun updateMotionPhotoXmp(
        image: File,
        videoLength: Long,
        presentationTimestampUs: Long,
    ) {
        val document = readStandardXmp(image)
            ?.takeIf(String::isNotBlank)
            ?.let(::parseDocument)
            ?: newDocument()
        val rdf = document.findOrCreateRdf()
        val description = rdf.findMotionDescription() ?: document.createElementNS(
            RDF_NAMESPACE,
            "rdf:Description",
        ).also(rdf::appendChild)

        description.declareNamespace("GCamera", GCAMERA_NAMESPACE)
        description.declareNamespace("Container", CONTAINER_NAMESPACE)
        description.declareNamespace("Item", ITEM_NAMESPACE)
        if (!description.hasAttributeNS(RDF_NAMESPACE, "about")) {
            description.setAttributeNS(RDF_NAMESPACE, "rdf:about", "")
        }
        description.setAttributeNS(GCAMERA_NAMESPACE, "GCamera:MotionPhoto", "1")
        description.setAttributeNS(GCAMERA_NAMESPACE, "GCamera:MotionPhotoVersion", "1")
        description.setAttributeNS(
            GCAMERA_NAMESPACE,
            "GCamera:MotionPhotoPresentationTimestampUs",
            presentationTimestampUs.toString(),
        )
        // Google Motion Photo v1 与旧版 MicroVideo 互斥；ColorOS 相册扫描器看到同时存在
        // GCamera:MicroVideo 会拒绝识别为实况照片，因此需移除残留的 MicroVideo 属性。
        description.removeAttributeNS(GCAMERA_NAMESPACE, "MicroVideo")
        description.removeAttributeNS(GCAMERA_NAMESPACE, "MicroVideoVersion")
        description.removeAttributeNS(GCAMERA_NAMESPACE, "MicroVideoOffset")
        description.removeAttribute("GCamera:MicroVideo")
        description.removeAttribute("GCamera:MicroVideoVersion")
        description.removeAttribute("GCamera:MicroVideoOffset")

        val preservedItems = extractPreservedContainerItems(document)
        val oldDirectories = document.getElementsByTagNameNS(CONTAINER_NAMESPACE, "Directory")
        for (index in oldDirectories.length - 1 downTo 0) {
            oldDirectories.item(index).let { it.parentNode?.removeChild(it) }
        }
        description.appendChild(document.createMotionPhotoDirectory(preservedItems, videoLength))
        writeStandardXmp(image, serialize(document))
    }

    /**
     * 保留原图已有容器条目（例如 Ultra HDR 的 Primary + GainMap），仅剥离旧的 MotionPhoto 项。
     */
    private fun extractPreservedContainerItems(document: Document): List<Element> {
        val result = mutableListOf<Element>()
        val directories = document.getElementsByTagNameNS(CONTAINER_NAMESPACE, "Directory")
        if (directories.length == 0) return result
        val firstDir = directories.item(0) as? Element ?: return result
        val listItems = firstDir.getElementsByTagNameNS(RDF_NAMESPACE, "li")
        for (i in 0 until listItems.length) {
            val li = listItems.item(i) as? Element ?: continue
            val item = li.getElementsByTagNameNS(CONTAINER_NAMESPACE, "Item").item(0) as? Element
            val semantic = item?.let(::readItemSemantic)
            if (semantic.equals("MotionPhoto", ignoreCase = true)) continue
            result.add(li.cloneNode(true) as Element)
        }
        return result
    }

    private fun readItemSemantic(item: Element): String? {
        val attr = item.getAttributeNS(ITEM_NAMESPACE, "Semantic")
        if (attr.isNotBlank()) return attr
        val child = item.getElementsByTagNameNS(ITEM_NAMESPACE, "Semantic").item(0)
        return child?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun readStandardXmp(image: File): String? =
        DataInputStream(BufferedInputStream(image.inputStream())).use { input ->
            require(input.readUnsignedShort() == JPEG_SOI) { "Source is not a JPEG image" }
            while (true) {
                val marker = readMarker(input)
                if (marker == JPEG_SOS || marker == JPEG_EOI) return@use null
                if (marker.isStandaloneJpegMarker()) continue
                val data = readSegment(input)
                if (marker == JPEG_APP1 && data.startsWith(XMP_HEADER)) {
                    return@use String(
                        data,
                        XMP_HEADER.size,
                        data.size - XMP_HEADER.size,
                        Charsets.UTF_8,
                    ).trimEnd('\u0000')
                }
            }
            null
        }

    private fun writeStandardXmp(image: File, xmp: String) {
        val xmpData = XMP_HEADER + xmp.toByteArray(Charsets.UTF_8)
        require(xmpData.size + 2 <= 0xffff) { "Motion photo XMP packet is too large" }
        val rewritten = File(image.parentFile, "${image.name}.xmp")
        if (rewritten.exists()) rewritten.delete()

        try {
            DataInputStream(BufferedInputStream(image.inputStream())).use { input ->
                DataOutputStream(BufferedOutputStream(rewritten.outputStream())).use { output ->
                    require(input.readUnsignedShort() == JPEG_SOI) { "Source is not a JPEG image" }
                    output.writeShort(JPEG_SOI)
                    var written = false
                    while (true) {
                        val marker = readMarker(input)
                        if (marker == JPEG_SOS) {
                            if (!written) {
                                writeXmpSegment(output, xmpData)
                                written = true
                            }
                            output.writeByte(JPEG_MARKER_PREFIX)
                            output.writeByte(marker)
                            val scanHeader = readSegment(input)
                            output.writeShort(scanHeader.size + 2)
                            output.write(scanHeader)
                            input.copyTo(output)
                            break
                        }
                        if (marker == JPEG_EOI) {
                            if (!written) {
                                writeXmpSegment(output, xmpData)
                                written = true
                            }
                            output.writeByte(JPEG_MARKER_PREFIX)
                            output.writeByte(marker)
                            input.copyTo(output)
                            break
                        }

                        if (marker.isStandaloneJpegMarker()) {
                            output.writeByte(JPEG_MARKER_PREFIX)
                            output.writeByte(marker)
                            continue
                        }
                        val data = readSegment(input)
                        if (marker == JPEG_APP1 && data.startsWith(XMP_HEADER)) {
                            if (!written) {
                                writeXmpSegment(output, xmpData)
                                written = true
                            }
                            continue
                        }
                        // 新建 XMP 段放在头部 APP0/EXIF 之后、第一个非 APP 段（如 DQT/SOF）之前，
                        // 避免只扫头部 APP 段的相册扫描器错过 XMP。
                        if (!written && marker !in 0xe0..0xef) {
                            writeXmpSegment(output, xmpData)
                            written = true
                        }
                        output.writeByte(JPEG_MARKER_PREFIX)
                        output.writeByte(marker)
                        output.writeShort(data.size + 2)
                        output.write(data)
                    }
                }
            }
            check(image.delete())
            check(rewritten.renameTo(image))
        } catch (error: Throwable) {
            rewritten.delete()
            throw error
        }
    }

    private fun readMarker(input: DataInputStream): Int {
        require(input.readUnsignedByte() == JPEG_MARKER_PREFIX) { "Invalid JPEG marker" }
        var marker = input.readUnsignedByte()
        while (marker == JPEG_MARKER_PREFIX) marker = input.readUnsignedByte()
        require(marker != 0) { "Unexpected stuffed JPEG marker before image data" }
        return marker
    }

    private fun readSegment(input: DataInputStream): ByteArray {
        val length = input.readUnsignedShort()
        require(length >= 2) { "Invalid JPEG segment length" }
        return ByteArray(length - 2).also(input::readFully)
    }

    private fun writeXmpSegment(output: DataOutputStream, xmpData: ByteArray) {
        output.writeByte(JPEG_MARKER_PREFIX)
        output.writeByte(JPEG_APP1)
        output.writeShort(xmpData.size + 2)
        output.write(xmpData)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun Int.isStandaloneJpegMarker(): Boolean =
        this == 0x01 || this in 0xd0..0xd8

    private fun parseDocument(xmp: String): Document {
        require(!xmp.contains("<!DOCTYPE", ignoreCase = true)) {
            "Motion photo XMP must not contain a document type declaration"
        }
        return documentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xmp)))
    }

    private fun newDocument(): Document = documentBuilderFactory()
        .newDocumentBuilder()
        .newDocument()
        .apply {
            val xmpMeta = createElementNS(XMP_META_NAMESPACE, "x:xmpmeta").apply {
                declareNamespace("x", XMP_META_NAMESPACE)
            }
            appendChild(xmpMeta)
            xmpMeta.appendChild(createElementNS(RDF_NAMESPACE, "rdf:RDF").apply {
                declareNamespace("rdf", RDF_NAMESPACE)
            })
        }

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }

    private fun Document.findOrCreateRdf(): Element {
        val existing = getElementsByTagNameNS(RDF_NAMESPACE, "RDF")
            .item(0) as? Element
        if (existing != null) return existing
        val root = documentElement ?: createElementNS(XMP_META_NAMESPACE, "x:xmpmeta").also {
            it.declareNamespace("x", XMP_META_NAMESPACE)
            appendChild(it)
        }
        return createElementNS(RDF_NAMESPACE, "rdf:RDF").also {
            it.declareNamespace("rdf", RDF_NAMESPACE)
            root.appendChild(it)
        }
    }

    private fun Element.findMotionDescription(): Element? {
        val descriptions = getElementsByTagNameNS(RDF_NAMESPACE, "Description")
        for (index in 0 until descriptions.length) {
            val description = descriptions.item(index) as? Element ?: continue
            if (description.hasAttributeNS(GCAMERA_NAMESPACE, "MotionPhoto") ||
                description.hasAttributeNS(GCAMERA_NAMESPACE, "MicroVideo")
            ) {
                return description
            }
        }
        return descriptions.item(0) as? Element
    }

    private fun Document.createMotionPhotoDirectory(
        preservedItems: List<Element>,
        videoLength: Long,
    ): Element {
        val directory = createElementNS(CONTAINER_NAMESPACE, "Container:Directory")
        val sequence = createElementNS(RDF_NAMESPACE, "rdf:Seq")
        directory.appendChild(sequence)
        val hasPrimary = preservedItems.any { li ->
            val item = li.getElementsByTagNameNS(CONTAINER_NAMESPACE, "Item").item(0) as? Element
            item?.let(::readItemSemantic).equals("Primary", ignoreCase = true)
        }
        if (!hasPrimary) {
            sequence.appendChild(createMotionPhotoItem("image/jpeg", "Primary", 0L))
        }
        preservedItems.forEach { sequence.appendChild(it) }
        sequence.appendChild(createMotionPhotoItem("video/mp4", "MotionPhoto", videoLength))
        return directory
    }

    private fun Document.createMotionPhotoItem(
        mime: String,
        semantic: String,
        length: Long,
    ): Element = createElementNS(RDF_NAMESPACE, "rdf:li").apply {
        setAttributeNS(RDF_NAMESPACE, "rdf:parseType", "Resource")
        appendChild(createElementNS(CONTAINER_NAMESPACE, "Container:Item").apply {
            setAttributeNS(ITEM_NAMESPACE, "Item:Mime", mime)
            setAttributeNS(ITEM_NAMESPACE, "Item:Semantic", semantic)
            setAttributeNS(ITEM_NAMESPACE, "Item:Length", length.toString())
            setAttributeNS(ITEM_NAMESPACE, "Item:Padding", "0")
        })
    }

    private fun Element.declareNamespace(prefix: String, namespace: String) {
        if (!hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix)) {
            setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:$prefix", namespace)
        }
    }

    private fun serialize(document: Document): String {
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    private const val XMP_META_NAMESPACE = "adobe:ns:meta/"
    private const val RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val GCAMERA_NAMESPACE = "http://ns.google.com/photos/1.0/camera/"
    private const val CONTAINER_NAMESPACE = "http://ns.google.com/photos/1.0/container/"
    private const val ITEM_NAMESPACE = "http://ns.google.com/photos/1.0/container/item/"
    private const val JPEG_MARKER_PREFIX = 0xff
    private const val JPEG_SOI = 0xffd8
    private const val JPEG_APP1 = 0xe1
    private const val JPEG_SOS = 0xda
    private const val JPEG_EOI = 0xd9
    private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
}
