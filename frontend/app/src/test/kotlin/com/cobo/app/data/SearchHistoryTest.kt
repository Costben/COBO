package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun parseAndEncode_roundTripsValidDistinctEntries() {
        val encoded = encodeSearchHistoryJson(listOf("微博", "热搜", "数码"))
        assertEquals(listOf("微博", "热搜", "数码"), parseSearchHistoryJson(encoded))
        assertEquals(listOf("甲", "乙"), parseSearchHistoryJson("""[" 甲 ", "", "甲", "乙"]"""))
        assertEquals(emptyList<String>(), parseSearchHistoryJson(null))
        assertEquals(emptyList<String>(), parseSearchHistoryJson("not-json"))
    }

    @Test
    fun pushSearchHistoryItem_deduplicatesMovesToFrontAndCapsLimit() {
        val list = mutableListOf("b", "a")
        assertFalse(pushSearchHistoryItem(list, "   ", limit = 3))
        assertTrue(pushSearchHistoryItem(list, " a ", limit = 3))
        assertEquals(listOf("a", "b"), list)

        assertTrue(pushSearchHistoryItem(list, "c", limit = 3))
        assertTrue(pushSearchHistoryItem(list, "d", limit = 3))
        assertEquals(listOf("d", "c", "a"), list)
    }

    @Test
    fun removeSearchHistoryItem_removesOnlyTargetEntry() {
        val list = mutableListOf("微博", "华为", "小米")
        assertTrue(removeSearchHistoryItem(list, "华为"))
        assertEquals(listOf("微博", "小米"), list)

        assertFalse(removeSearchHistoryItem(list, "不存在"))
        assertEquals(listOf("微博", "小米"), list)
    }
}
