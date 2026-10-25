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
        val info = Updater.latest("0.0.1")
        assertTrue("线上没有可用的 release", info != null)
        info!!
        assertTrue("版本号为空", info.version.isNotBlank())
        assertTrue("缺少 APK 下载地址", !info.apkUrl.isNullOrBlank())
        assertTrue("缺少 release 页面", info.pageUrl.startsWith("https://github.com/"))
        assertNull("本地比线上还新时不该提示更新", Updater.latest("99.0.0"))
    }
}
