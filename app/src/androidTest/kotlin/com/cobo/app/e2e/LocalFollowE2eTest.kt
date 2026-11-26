package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「本地关注」是几条主页时间线合并出来的流，最容易出的两个问题：只给一页翻不动、
 * 以及页与页之间时间序断掉（下一页冒出比本页更新的微博）。这里拿真实接口逐页验。
 */
@RunWith(AndroidJUnit4::class)
class LocalFollowE2eTest : DataE2eTest() {

    @Test
    fun localFollows_paginatesInTimeOrder() {
        assumeTrue("本机没有打「本地」标记", FollowTags.uids(FollowTag.Local).isNotEmpty())

        var page = net { App.repo.localFollows(force = true) }
        assertTrue("本地关注第一页为空", page.items.isNotEmpty())
        assertNotNull("第一页没给游标，这条流翻不动", page.sinceId)

        val seen = page.items.mapTo(HashSet()) { it.id }
        var loaded = 1
        repeat(2) {
            val cursor = page.sinceId ?: return@repeat
            val next = net { App.repo.localFollows(cursor, force = false) }
            if (next.items.isEmpty()) return@repeat

            val dup = next.items.filter { it.id in seen }.map { it.id }
            assertTrue("第 ${loaded + 1} 页重复了前面的微博：$dup", dup.isEmpty())
            assertTrue(
                "第 ${loaded + 1} 页冒出了比上一页更新的微博，页与页之间时间序断了" +
                    "（上一页尾 ${page.items.last().id}，这一页首 ${next.items.first().id}）",
                next.items.maxOf { it.id.toLong() } <= page.items.minOf { it.id.toLong() },
            )
            seen.addAll(next.items.map { it.id })
            page = next
            loaded++
        }
        assertTrue("只翻到 $loaded 页，这条流还是翻不下去", loaded >= 2)
    }
}
