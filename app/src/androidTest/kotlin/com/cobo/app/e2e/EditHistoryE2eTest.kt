package com.cobo.app.e2e

import com.cobo.app.App
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Read-only checks for the edit-history endpoint that backs the "查看编辑记录" entry.
 *
 * The sample status is a public post that has been edited several times; if it is gone the
 * whole class is skipped instead of failing the suite.
 */
class EditHistoryE2eTest : DataE2eTest() {

    private val editedMid = "5356400671726471"

    private fun timeOf(raw: String): Long? = runCatching {
        SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US).parse(raw)?.time
    }.getOrNull()

    @Test
    fun detail_reportsEditCount() {
        val status = net { App.repo.detail(editedMid, force = true) }
        assumeTrue("样例微博已不可用", status != null)
        assertTrue("未识别到 edit_count", status!!.editCount > 0)
    }

    @Test
    fun editHistory_returnsVersionsNewestFirst() {
        val versions = net { App.repo.editHistory(editedMid) }
        assumeTrue("样例微博没有编辑记录", versions.isNotEmpty())

        assertTrue(
            "每条记录都缺少正文或可见性说明",
            versions.all { it.textHtml.isNotBlank() || it.note.isNotBlank() },
        )

        val times = versions.mapNotNull { timeOf(it.createdAt) }
        assertTrue("时间戳解析失败", times.size == versions.size)
        times.zipWithNext().forEach { (newer, older) ->
            assertTrue("记录不是按时间倒序返回", newer >= older)
        }
    }

    @Test
    fun editHistory_buildsImageUrls() {
        val versions = net { App.repo.editHistory(editedMid) }
        assumeTrue("样例微博没有编辑记录", versions.isNotEmpty())
        val pics = versions.flatMap { it.pics }
        assumeTrue("样例微博没有配图", pics.isNotEmpty())
        pics.forEach { url ->
            assertTrue("图片地址不合法：$url", url.startsWith("https://wx1.sinaimg.cn/large/"))
        }
    }
}
