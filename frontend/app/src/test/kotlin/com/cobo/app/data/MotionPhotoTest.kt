package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

class MotionPhotoTest {

    @Test
    fun exportsPlainJpegAndMp4AsReadableMotionPhoto() {
        val image = tempFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        val video = tempFile(mp4Bytes("mp42"), ".mp4")
        val output = File.createTempFile("cobo-motion-export-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(image, video, output, 1_295_000L)

            val parsed = MotionPhotoParser.parse(output)
            val raw = output.readBytes()
            val text = raw.toString(Charsets.ISO_8859_1)
            assertTrue(parsed.hasMotionPhoto)
            assertEquals(video.length(), parsed.declaredVideoLength)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertEquals(1_295_000L, parsed.presentationTimestampUs)
            assertTrue(text.contains("GCamera:MotionPhoto=\"1\""))
            assertTrue(text.contains("rdf:about=\"\""))
            assertTrue(text.contains("Item:Length=\"${video.length()}\""))
            assertFalse(text.contains("GCamera:MicroVideo"))
            // XMP APP1 段必须位于 DQT (0xFFDB) 之前的头部区域，确保系统相册扫描器可读。
            val xmpPos = text.indexOf("http://ns.adobe.com/xap/1.0/")
            val dqtPos = (0 until raw.size - 1).first { raw[it] == 0xff.toByte() && raw[it + 1] == 0xdb.toByte() }
            assertTrue("XMP should precede DQT: xmp=$xmpPos dqt=$dqtPos", xmpPos in 1 until dqtPos)
            assertEquals(0, text.split("xmlns:GCamera=").size - 2)
        } finally {
            image.delete()
            video.delete()
            output.delete()
        }
    }

    @Test
    fun preservesUltraHdrGainMapWhenAddingMotionPhotoMetadata() {
        val gainMapTail = Base64.getDecoder().decode(ONE_PIXEL_JPEG)
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description
                    xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
                    xmlns:Container="http://ns.google.com/photos/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
                    hdrgm:Version="1.0">
                  <Container:Directory>
                    <rdf:Seq>
                      <rdf:li rdf:parseType="Resource">
                        <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
                      </rdf:li>
                      <rdf:li rdf:parseType="Resource">
                        <Container:Item Item:Mime="image/jpeg" Item:Semantic="GainMap" Item:Length="${gainMapTail.size}" Item:Padding="0"/>
                      </rdf:li>
                    </rdf:Seq>
                  </Container:Directory>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
        val image = tempFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG).withXmp(xmp) + gainMapTail, ".jpg")
        val video = tempFile(mp4Bytes("isom"), ".mp4")
        val output = File.createTempFile("cobo-motion-hdr-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(image, video, output, 0L)

            val parsed = MotionPhotoParser.parse(output)
            val text = output.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(parsed.hasUltraHdr)
            assertTrue(parsed.hasMotionPhoto)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertTrue(text.contains("Item:Semantic=\"GainMap\""))
            assertTrue(text.contains("Item:Length=\"${gainMapTail.size}\""))
            assertTrue(text.contains("Item:Semantic=\"MotionPhoto\""))
            assertTrue(text.contains("Item:Length=\"${video.length()}\""))
        } finally {
            image.delete()
            video.delete()
            output.delete()
        }
    }

    @Test
    fun alreadyEmbeddedMotionPhotoIsKeptWithoutDuplicatingTail() {
        val baseImage = tempFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        val video = tempFile(mp4Bytes("mp42"), ".mp4")
        val embedded = File.createTempFile("cobo-motion-embedded-", ".jpg").apply { delete() }
        val reexported = File.createTempFile("cobo-motion-reexport-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(baseImage, video, embedded, 500_000L)
            MotionPhotoExporter.export(embedded, null, reexported, null)

            assertEquals(embedded.length(), reexported.length())
            val parsed = MotionPhotoParser.parse(reexported)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertEquals(500_000L, parsed.presentationTimestampUs)
        } finally {
            baseImage.delete()
            video.delete()
            embedded.delete()
            reexported.delete()
        }
    }

    @Test
    fun detectsFtypMajorBrandsCorrectly() {
        val mov = tempFile(mp4Bytes("qt  "), ".mov")
        val mp4 = tempFile(mp4Bytes("mp42"), ".mp4")
        val jpg = tempFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        try {
            assertTrue(MotionPhotoParser.isIsoBmff(mov))
            assertEquals("qt  ", MotionPhotoParser.ftypMajorBrand(mov))
            assertTrue(MotionPhotoParser.isIsoBmff(mp4))
            assertEquals("mp42", MotionPhotoParser.ftypMajorBrand(mp4))
            assertFalse(MotionPhotoParser.isIsoBmff(jpg))
            assertNull(MotionPhotoParser.ftypMajorBrand(jpg))
            assertTrue(MotionPhotoParser.hasJpegHeader(jpg))
            assertFalse(MotionPhotoParser.hasJpegHeader(mov))
        } finally {
            mov.delete()
            mp4.delete()
            jpg.delete()
        }
    }

    @Test
    fun extractsStillJpegAndEmbeddedMp4FromMotionPhotoBytes() {
        val image = tempFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        val videoBytes = mp4Bytes("mp42")
        val video = tempFile(videoBytes, ".mp4")
        val output = File.createTempFile("cobo-motion-split-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(image, video, output, 800_000L)
            val allBytes = output.readBytes()

            val info = MotionPhotoParser.parse(allBytes)
            assertTrue(info.hasMotionPhoto)
            assertEquals(videoBytes.size.toLong(), info.embeddedVideoLength)

            val parts = MotionPhotoParser.extract(allBytes)
            assertTrue("Expected non-null extracted parts", parts != null)
            assertTrue(MotionPhotoParser.hasJpegHeader(parts!!.stillBytes))
            assertTrue(MotionPhotoParser.isIsoBmff(parts.videoBytes))
            assertEquals(allBytes.size - videoBytes.size, parts.stillBytes.size)
            assertTrue(videoBytes.contentEquals(parts.videoBytes))

            val plainJpeg = Base64.getDecoder().decode(ONE_PIXEL_JPEG)
            assertFalse(MotionPhotoParser.parse(plainJpeg).hasMotionPhoto)
            assertNull(MotionPhotoParser.extract(plainJpeg))
        } finally {
            image.delete()
            video.delete()
            output.delete()
        }
    }

    private fun tempFile(bytes: ByteArray, suffix: String): File =
        File.createTempFile("cobo-mp-test-", suffix).apply { writeBytes(bytes) }

    private fun mp4Bytes(brand: String): ByteArray {
        val brandBytes = brand.toByteArray(StandardCharsets.US_ASCII)
        require(brandBytes.size == 4)
        return byteArrayOf(
            0, 0, 0, 20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            brandBytes[0], brandBytes[1], brandBytes[2], brandBytes[3],
            0, 0, 0, 0,
            brandBytes[0], brandBytes[1], brandBytes[2], brandBytes[3],
            0, 0, 0, 12,
            'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte(),
            0, 0, 0, 0,
        )
    }

    private fun ByteArray.withXmp(xmp: String): ByteArray {
        val payload = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) +
            xmp.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.write(this, 0, 2)
            stream.writeByte(0xff)
            stream.writeByte(0xe1)
            stream.writeShort(payload.size + 2)
            stream.write(payload)
            stream.write(this, 2, size - 2)
        }
        return out.toByteArray()
    }

    companion object {
        private const val ONE_PIXEL_JPEG =
            "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q=="
    }
}
