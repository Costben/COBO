package com.cobo.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 将微博实况图附带的 QuickTime `.mov` 规范化重封装为标准 MP4，供 Motion Photo 合成使用。
 *
 * 微博实况视频多来自 iPhone 原生 `.mov`（`ftypqt  `），常见三种不直接兼容 Android MP4 封装器的情况：
 * 1. 多条视频轨（1 条主视频 + 多条小分辨率 `Core Media Auxiliary Picture` 辅助图轨）；
 * 2. Apple QuickTime 私有 `mebx` Metadata 轨；
 * 3. 音频轨可能是线性 PCM（`audio/raw` / `pcm_s16le`），`MediaMuxer(MPEG_4)` 需先经 `MediaCodec` 编码为 AAC。
 */
internal object MotionPhotoRemuxer {

    private const val TAG = "CoboLiveSave"
    private const val DEFAULT_SAMPLE_BUFFER_BYTES = 2 * 1024 * 1024
    private const val MAX_SAMPLE_BUFFER_BYTES = 16 * 1024 * 1024
    private const val AAC_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val AAC_BITRATE = 128_000
    private const val CODEC_TIMEOUT_US = 10_000L

    private val cleanMp4Brands = setOf("isom", "iso2", "mp41", "mp42", "avc1", "mp4v", "M4V ")
    private val mp4VideoMimes = setOf(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_VIDEO_MPEG4,
        "video/av01",
        MediaFormat.MIMETYPE_VIDEO_VP9,
    )
    private val mp4PassthroughAudioMimes = setOf(
        MediaFormat.MIMETYPE_AUDIO_AAC,
        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
        MediaFormat.MIMETYPE_AUDIO_MPEG,
    )

    private data class TrackChoice(
        val videoIndex: Int,
        val videoFormat: MediaFormat,
        val audioIndex: Int?,
        val audioFormat: MediaFormat?,
        val totalTracks: Int,
    )

    private data class EncodedSample(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
    )

    private data class EncodedAudioTrack(
        val format: MediaFormat,
        val samples: List<EncodedSample>,
    )

    /**
     * 确保静图为 JPEG：若原文件已是 JPEG 则直接返回（保留 EXIF / XMP / GainMap），否则转码为高质量 JPEG。
     */
    fun ensureJpeg(source: File, tempJpeg: File): File {
        if (MotionPhotoParser.hasJpegHeader(source)) return source
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: throw IllegalStateException("无法解码静图用于实况合成")
        try {
            tempJpeg.parentFile?.mkdirs()
            tempJpeg.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    "静图转码 JPEG 失败"
                }
            }
            return tempJpeg
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 若 [input] 已是单主视频轨 + 兼容音频轨的纯净 MP4，则直接复用；否则无损重封装视频轨并规范化音频轨到 [output]。
     */
    fun prepareMp4(input: File, output: File): File {
        require(input.isFile && input.length() > 0L) { "实况视频文件不可用" }
        val choice = inspectTracks(input)
        val audioMime = choice.audioFormat?.getString(MediaFormat.KEY_MIME)
        val expectedTracks = 1 + if (choice.audioIndex != null) 1 else 0
        val brand = MotionPhotoParser.ftypMajorBrand(input)
        val alreadyCleanMp4 = brand in cleanMp4Brands &&
            choice.totalTracks == expectedTracks &&
            (audioMime == null || audioMime in mp4PassthroughAudioMimes)
        if (alreadyCleanMp4) return input

        remuxToMp4(input, output, choice)
        check(output.isFile && output.length() > 0L && MotionPhotoParser.isIsoBmff(output)) {
            "重封装后的实况 MP4 不可用"
        }
        return output
    }

    private fun inspectTracks(input: File): TrackChoice {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            var bestVideoIndex = -1
            var bestVideoFormat: MediaFormat? = null
            var bestVideoArea = -1L
            var bestVideoSupported = false
            var audioIndex: Int? = null
            var audioFormat: MediaFormat? = null

            val total = extractor.trackCount
            for (index in 0 until total) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> {
                        val supported = mime in mp4VideoMimes
                        val width = format.intOrZero(MediaFormat.KEY_WIDTH).toLong()
                        val height = format.intOrZero(MediaFormat.KEY_HEIGHT).toLong()
                        val area = width * height
                        val better = bestVideoIndex < 0 ||
                            (supported && !bestVideoSupported) ||
                            (supported == bestVideoSupported && area > bestVideoArea)
                        if (better) {
                            bestVideoIndex = index
                            bestVideoFormat = format
                            bestVideoArea = area
                            bestVideoSupported = supported
                        }
                    }
                    mime.startsWith("audio/") && audioIndex == null -> {
                        audioIndex = index
                        audioFormat = format
                    }
                }
            }
            val selectedVideoFormat = requireNotNull(bestVideoFormat) { "实况视频中未找到可用视频轨" }
            return TrackChoice(
                videoIndex = bestVideoIndex,
                videoFormat = selectedVideoFormat,
                audioIndex = audioIndex,
                audioFormat = audioFormat,
                totalTracks = total,
            )
        } finally {
            extractor.release()
        }
    }

    private fun remuxToMp4(input: File, output: File, choice: TrackChoice) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val audioMime = choice.audioFormat?.getString(MediaFormat.KEY_MIME)
        val encodedPcmAudio = if (choice.audioIndex != null && audioMime == MediaFormat.MIMETYPE_AUDIO_RAW) {
            runCatching {
                encodePcmToAac(input, choice.audioIndex, choice.audioFormat)
            }.onFailure { err ->
                Log.w(TAG, "PCM 音频转 AAC 失败，回退为无声实况重封装", err)
            }.getOrNull()
        } else {
            null
        }

        val passthroughAudioIndex = if (
            choice.audioIndex != null &&
            audioMime != null &&
            audioMime in mp4PassthroughAudioMimes
        ) {
            choice.audioIndex
        } else {
            null
        }

        val rotation = resolveRotation(input, choice.videoFormat)
        val minPtsUs = findMinPresentationTimeUs(
            input = input,
            trackIndices = listOfNotNull(choice.videoIndex, passthroughAudioIndex),
            extraAudioMinPtsUs = encodedPcmAudio?.samples?.minOfOrNull { it.presentationTimeUs },
        )
        val ptsShiftUs = if (minPtsUs < 0L) -minPtsUs else 0L

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(input.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation in setOf(0, 90, 180, 270)) {
                muxer.setOrientationHint(rotation)
            }

            val muxVideoTrack = muxer.addTrack(choice.videoFormat)
            var muxPassthroughAudioTrack: Int? = null
            if (passthroughAudioIndex != null && choice.audioFormat != null) {
                muxPassthroughAudioTrack = runCatching {
                    muxer.addTrack(choice.audioFormat)
                }.onFailure { err ->
                    Log.w(TAG, "跳过不兼容音频轨 $audioMime", err)
                }.getOrNull()
            }
            var muxEncodedAudioTrack: Int? = null
            if (encodedPcmAudio != null && encodedPcmAudio.samples.isNotEmpty()) {
                muxEncodedAudioTrack = runCatching {
                    muxer.addTrack(encodedPcmAudio.format)
                }.onFailure { err ->
                    Log.w(TAG, "添加转码后的 AAC 音轨失败", err)
                }.getOrNull()
            }

            muxer.start()
            started = true

            extractor.selectTrack(choice.videoIndex)
            if (passthroughAudioIndex != null && muxPassthroughAudioTrack != null) {
                extractor.selectTrack(passthroughAudioIndex)
            }

            val initialSize = maxOf(
                DEFAULT_SAMPLE_BUFFER_BYTES,
                choice.videoFormat.intOrZero(MediaFormat.KEY_MAX_INPUT_SIZE),
                choice.audioFormat?.intOrZero(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
            ).coerceAtMost(MAX_SAMPLE_BUFFER_BYTES)
            var buffer = ByteBuffer.allocateDirect(initialSize)
            val info = MediaCodec.BufferInfo()
            var lastAudioPtsUs = Long.MIN_VALUE

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val targetTrack = when (trackIndex) {
                    choice.videoIndex -> muxVideoTrack
                    passthroughAudioIndex -> muxPassthroughAudioTrack
                    else -> null
                }
                if (targetTrack == null) {
                    extractor.advance()
                    continue
                }
                val (sampleSize, usedBuffer) = readSampleWithGrow(extractor, buffer)
                buffer = usedBuffer
                if (sampleSize < 0) break

                val rawPts = extractor.sampleTime
                var pts = (rawPts + ptsShiftUs).coerceAtLeast(0L)
                if (targetTrack == muxPassthroughAudioTrack) {
                    if (pts < lastAudioPtsUs) pts = lastAudioPtsUs
                    lastAudioPtsUs = pts
                }

                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = pts
                info.flags = mapExtractorFlags(extractor.sampleFlags)
                muxer.writeSampleData(targetTrack, buffer, info)
                extractor.advance()
            }

            if (encodedPcmAudio != null && muxEncodedAudioTrack != null) {
                var lastPts = Long.MIN_VALUE
                for (sample in encodedPcmAudio.samples) {
                    var pts = (sample.presentationTimeUs + ptsShiftUs).coerceAtLeast(0L)
                    if (pts < lastPts) pts = lastPts
                    lastPts = pts
                    info.offset = 0
                    info.size = sample.data.size
                    info.presentationTimeUs = pts
                    info.flags = sample.flags
                    muxer.writeSampleData(muxEncodedAudioTrack, ByteBuffer.wrap(sample.data), info)
                }
            }
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            extractor.release()
            if (muxer != null) {
                if (started) {
                    runCatching { muxer.stop() }.onFailure { err ->
                        output.delete()
                        throw err
                    }
                }
                runCatching { muxer.release() }
            }
        }
    }

    /**
     * 将 Apple QuickTime 实况视频里的线性 PCM 音频轨编码为标准 AAC。
     * 实况仅 2~3 秒，128kbps AAC 总量约 40KB，先编码入内存再与视频一起 mux 可避免 start() 顺序竞争。
     */
    private fun encodePcmToAac(
        input: File,
        audioTrackIndex: Int,
        sourceFormat: MediaFormat,
    ): EncodedAudioTrack? {
        val sampleRate = sourceFormat.intOrZero(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: 44_100
        val channelCount = sourceFormat.intOrZero(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 } ?: 1
        val pcmEncoding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            sourceFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
        ) {
            sourceFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val sourceBytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> 2
        }

        val encoderFormat = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
        }

        val extractor = MediaExtractor()
        val encoder = MediaCodec.createEncoderByType(AAC_MIME)
        var encoderStarted = false
        try {
            extractor.setDataSource(input.absolutePath)
            extractor.selectTrack(audioTrackIndex)

            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true

            var readBuf = ByteBuffer.allocateDirect(512 * 1024)
            var pendingPcm: ByteArray? = null
            var pendingOffset = 0
            var pendingPtsUs = 0L
            var inputDone = false
            var outputDone = false
            var outputFormat: MediaFormat? = null
            val samples = mutableListOf<EncodedSample>()
            val outInfo = MediaCodec.BufferInfo()
            val bytesPerSecond16Bit = sampleRate.toLong() * channelCount.toLong() * 2L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = encoder.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        if (pendingPcm == null || pendingOffset >= pendingPcm.size) {
                            val (size, usedBuf) = readSampleWithGrow(extractor, readBuf)
                            readBuf = usedBuf
                            if (size < 0) {
                                encoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val raw = ByteArray(size)
                                readBuf.position(0)
                                readBuf.get(raw)
                                pendingPcm = convertToPcm16Le(raw, pcmEncoding, sourceBytesPerSample)
                                pendingOffset = 0
                                pendingPtsUs = extractor.sampleTime.coerceAtLeast(0L)
                                extractor.advance()
                            }
                        }

                        val chunkSource = pendingPcm
                        if (!inputDone && chunkSource != null && pendingOffset < chunkSource.size) {
                            val remaining = chunkSource.size - pendingOffset
                            val maxChunk = (inBuf.capacity() / (channelCount * 2)) * (channelCount * 2)
                            val toWrite = minOf(remaining, maxChunk.coerceAtLeast(channelCount * 2))
                            val chunkPts = pendingPtsUs +
                                (pendingOffset.toLong() * 1_000_000L / bytesPerSecond16Bit)
                            inBuf.put(chunkSource, pendingOffset, toWrite)
                            pendingOffset += toWrite
                            encoder.queueInputBuffer(inIndex, 0, toWrite, chunkPts, 0)
                        }
                    }
                }

                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(outInfo, CODEC_TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = encoder.outputFormat
                        }
                        outIndex >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIndex)
                            val isConfig = (outInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && outBuf != null && outInfo.size > 0) {
                                outBuf.position(outInfo.offset)
                                outBuf.limit(outInfo.offset + outInfo.size)
                                val data = ByteArray(outInfo.size)
                                outBuf.get(data)
                                val cleanFlags = outInfo.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                                samples.add(EncodedSample(data, outInfo.presentationTimeUs, cleanFlags))
                            }
                            val eos = (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            encoder.releaseOutputBuffer(outIndex, false)
                            if (eos) {
                                outputDone = true
                                break
                            }
                        }
                    }
                }
            }

            val finalFormat = outputFormat ?: return null
            if (samples.isEmpty()) return null
            return EncodedAudioTrack(finalFormat, samples)
        } finally {
            extractor.release()
            if (encoderStarted) runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }
    }

    private fun convertToPcm16Le(raw: ByteArray, pcmEncoding: Int, bytesPerSample: Int): ByteArray {
        if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) return raw
        val sampleCount = raw.size / bytesPerSample
        val out = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        val input = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> {
                for (i in 0 until sampleCount) {
                    val v = ((raw[i].toInt() and 0xff) - 128) shl 8
                    out.putShort(v.toShort())
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                for (i in 0 until sampleCount) {
                    val f = input.float.coerceIn(-1f, 1f)
                    out.putShort((f * 32767f).toInt().toShort())
                }
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                for (i in 0 until sampleCount) {
                    val base = i * 3
                    val s = ((raw[base + 2].toInt() shl 8) or (raw[base + 1].toInt() and 0xff))
                    out.putShort(s.toShort())
                }
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                for (i in 0 until sampleCount) {
                    val s = input.int shr 16
                    out.putShort(s.toShort())
                }
            }
            else -> return raw
        }
        return out.array()
    }

    private fun resolveRotation(input: File, videoFormat: MediaFormat): Int {
        val fromFormat = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            videoFormat.getInteger(MediaFormat.KEY_ROTATION)
        } else {
            null
        }
        val raw = fromFormat ?: runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(input.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull() ?: 0
        return ((raw % 360) + 360) % 360
    }

    private fun findMinPresentationTimeUs(
        input: File,
        trackIndices: List<Int>,
        extraAudioMinPtsUs: Long?,
    ): Long {
        var minPts = extraAudioMinPtsUs ?: Long.MAX_VALUE
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            trackIndices.forEach(extractor::selectTrack)
            var checked = 0
            while (checked < 32) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val pts = extractor.sampleTime
                if (pts < minPts) minPts = pts
                extractor.advance()
                checked++
            }
        } finally {
            extractor.release()
        }
        return if (minPts == Long.MAX_VALUE) 0L else minPts
    }

    private fun readSampleWithGrow(
        extractor: MediaExtractor,
        initial: ByteBuffer,
    ): Pair<Int, ByteBuffer> {
        var current = initial
        while (true) {
            current.clear()
            try {
                val size = extractor.readSampleData(current, 0)
                return size to current
            } catch (err: IllegalArgumentException) {
                val nextCapacity = current.capacity() * 2
                if (nextCapacity > MAX_SAMPLE_BUFFER_BYTES) throw err
                current = ByteBuffer.allocateDirect(nextCapacity)
            }
        }
    }

    private fun mapExtractorFlags(extractorFlags: Int): Int {
        var flags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            (extractorFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0
        ) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0
}
