package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 游标是这条流唯一的进度来源：解析不出来就得从头拉，不能把整条流卡死。 */
class LocalFollowCursorTest {

    @Test
    fun roundTripsPerUserProgress() {
        val next = linkedMapOf("1" to "4870000000000000", "2" to "")
        assertEquals(next, parseTagCursor(encodeTagCursor(next)))
    }

    @Test
    fun missingCursorStartsFromNewest() {
        assertTrue(parseTagCursor(null).isEmpty())
        assertTrue(parseTagCursor("").isEmpty())
        assertTrue(parseTagCursor("   ").isEmpty())
    }

    @Test
    fun brokenCursorFallsBackToStart() {
        assertTrue(parseTagCursor("{not json").isEmpty())
        assertTrue(parseTagCursor("[]").isEmpty())
    }
}
