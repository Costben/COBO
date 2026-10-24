package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.data.Api
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.weiboStatusId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 用线上返回的真实正文 HTML 检查链接落地：微博给的是 `/status/<id>`（「全文」）和
 * `/n/<name>`（@用户）这类站内相对路径，任何一条都不能再被当成 `file:///` 打开。
 */
@RunWith(AndroidJUnit4::class)
class LinkApiE2eTest : DataE2eTest() {

    @Test
    fun liveHtml_linksAllResolveToInAppTargets() {
        val hrefs = LinkedHashSet<String>()
        collectHrefs(feedRoot(), hrefs)
        collectHrefs(searchRoot(), hrefs)

        assertTrue("线上正文里没有取到任何链接", hrefs.isNotEmpty())
        val statusLinks = hrefs.count { weiboStatusId(it) != null }
        assertTrue("没有取到「全文」这类微博链接，用例失去意义：$hrefs", statusLinks > 0)
        hrefs.forEach { href ->
            val target = resolveWeiboLink(href)
            assertTrue("链接既不是微博详情也无法解析：$href", weiboStatusId(href) != null || target != null)
            target?.let {
                assertTrue("链接没有补成绝对地址：$href -> $it", it.startsWith("http://") || it.startsWith("https://"))
                assertFalse("链接落到了本地路径：$href -> $it", it.startsWith("file:"))
            }
        }
        android.util.Log.i("E2E_LINK", "checked ${hrefs.size} hrefs, $statusLinks status links")
    }

    private fun feedRoot(): JSONObject {
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = "102803"
        params["page_type"] = "03"
        return net { Api.get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie, true) }
    }

    /** 搜索结果里长文更多，「全文」链接基本每次都能取到。 */
    private fun searchRoot(): JSONObject {
        val params = LinkedHashMap<String, String?>()
        params["containerid"] = "100103type=1&q=新闻"
        params["page_type"] = "searchall"
        return net { Api.get(Api.url("https://m.weibo.cn/api/container/getIndex", params), cookie, true) }
    }

    private fun collectHrefs(value: Any?, out: MutableSet<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { collectHrefs(value.opt(it), out) }
            is JSONArray -> for (i in 0 until value.length()) collectHrefs(value.opt(i), out)
            is String -> HREF.findAll(value).forEach { out.add(it.groupValues[1]) }
        }
    }

    private companion object {
        val HREF = Regex("""href=['"]([^'"]+)['"]""")
    }
}
