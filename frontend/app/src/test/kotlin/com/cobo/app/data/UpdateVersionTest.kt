package com.cobo.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本号比较是更新提示的唯一判据，错一次就会天天弹窗或者永远不弹。 */
class UpdateVersionTest {

    @Test
    fun comparesNumericSegments() {
        assertTrue(Updater.compareVersion("1.0.10", "1.0.9") > 0)
        assertTrue(Updater.compareVersion("1.1.0", "1.0.99") > 0)
        assertTrue(Updater.compareVersion("2", "1.9.9") > 0)
        assertTrue(Updater.compareVersion("1.0.9", "1.0.10") < 0)
    }

    @Test
    fun releaseBeatsPrerelease() {
        assertTrue(Updater.compareVersion("1.0.0", "1.0.0-pre") > 0)
        assertTrue(Updater.compareVersion("1.0.0-pre", "1.0.0") < 0)
        assertTrue(Updater.compareVersion("1.1.0-pre", "1.0.9") > 0)
    }

    @Test
    fun ignoresTagPrefixAndPadding() {
        assertEquals(0, Updater.compareVersion("v1.0.0", "1.0.0"))
        assertEquals(0, Updater.compareVersion("1.0", "1.0.0"))
        assertEquals(0, Updater.compareVersion("1.0.0-pre", "v1.0.0-pre"))
    }

    /** 打真接口确认解析路径通（本地已是最高版本时应返回 null）。 */
    @Test
    fun fetchesLiveReleases() = runBlocking {
        val info = Updater.latest("0.0.1", includePrerelease = true)
        assertTrue("线上没有可用的 release", info != null)
        info!!
        assertTrue("版本号为空", info.version.isNotBlank())
        assertTrue("缺少 APK 下载地址", !info.apkUrl.isNullOrBlank())
        assertTrue("缺少 release 页面", info.pageUrl.startsWith("https://github.com/"))
        assertNull("本地比线上还新时不该提示更新", Updater.latest("99.0.0"))

        val stable = Updater.latest("0.0.1", includePrerelease = false)
        assertTrue("线上没有正式版", stable != null)
        assertTrue("正式版通道返回了预发布", !stable!!.prerelease)
    }

    @Test
    fun stableChannelSkipsPrereleases() {
        val body = """
            [
              {"tag_name":"v1.0.7-pre","prerelease":true,"html_url":"https://example.com/pre","assets":[]},
              {"tag_name":"v1.0.6","prerelease":false,"html_url":"https://example.com/stable","assets":[]}
            ]
        """.trimIndent()

        assertEquals("1.0.7-pre", Updater.parseLatest(body, "1.0.5", includePrerelease = true)?.version)
        assertEquals("1.0.6", Updater.parseLatest(body, "1.0.5", includePrerelease = false)?.version)
        assertNull(Updater.parseLatest(body, "1.0.6", includePrerelease = false))
    }

    /** 有些 release 忘了勾 pre-release，靠 `-pre` 后缀也要能识别出来。 */
    @Test
    fun suffixMarksPrereleaseWithoutFlag() {
        val body = """[{"tag_name":"v1.0.7-pre","prerelease":false,"html_url":"x","assets":[]}]"""
        assertNull(Updater.parseLatest(body, "1.0.5", includePrerelease = false))
        assertEquals("1.0.7-pre", Updater.parseLatest(body, "1.0.5", includePrerelease = true)?.version)
    }

    @Test
    fun parseApkUrlFromReleaseAssets() {
        val body = """
            [
              {
                "tag_name": "v1.0.12-pre",
                "prerelease": true,
                "html_url": "https://github.com/Costben/COBO/releases/tag/v1.0.12-pre",
                "assets": [
                  {
                    "name": "source.zip",
                    "browser_download_url": "https://github.com/.../source.zip"
                  },
                  {
                    "name": "Cobo-1.0.12-pre.apk",
                    "browser_download_url": "https://github.com/Costben/COBO/releases/download/v1.0.12-pre/Cobo-1.0.12-pre.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
        val update = Updater.parseLatest(body, "1.0.11", includePrerelease = true)
        assertEquals("1.0.12-pre", update?.version)
        assertEquals(
            "https://github.com/Costben/COBO/releases/download/v1.0.12-pre/Cobo-1.0.12-pre.apk",
            update?.apkUrl,
        )
    }
}
