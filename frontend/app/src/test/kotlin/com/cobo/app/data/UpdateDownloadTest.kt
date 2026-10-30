package com.cobo.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真下一个小包，把「探测线路 → 分块并发 → 合并」整条路径跑一遍。
 *
 * 这条用例依赖网络，和 [UpdateVersionTest.fetchesLiveReleases] 一样是有意为之：分块拼接、
 * 续传清理这些地方出错时单元测试看不出来，只有真下一次才知道。
 */
class UpdateDownloadTest {

    @Test
    fun downloadsAndMergesSegments() = runBlocking {
        val info = Updater.latest("0.0.1", includePrerelease = true)!!
        val url = info.apkUrl!!
        val dir = File(System.getProperty("java.io.tmpdir"), "cobo-download-${System.nanoTime()}")
        try {
            val progress = ArrayList<Float>()
            val file = Updater.downloadApk(dir, url, info.version, force = true) { p, _, _ ->
                progress.add(p)
            }
            assertTrue("安装包没落盘", file.exists())
            assertTrue("安装包太小：${file.length()} 字节", file.length() > 1_000_000)
            assertEquals("进度没走到 1", 1f, progress.last())
            // 分块中间文件要清干净，否则下次会当成断点续传接着用。
            assertTrue(
                "分块没清掉：${dir.list()?.toList()}",
                dir.listFiles().orEmpty().none { it.name.contains(".part") },
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
