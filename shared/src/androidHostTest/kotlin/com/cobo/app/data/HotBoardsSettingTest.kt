package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HotBoardsSettingTest {

    @Test
    fun missingKey_meansEveryBoard() {
        assertEquals(HotBoard.entries.toSet(), parseHotBoards(null))
    }

    @Test
    fun emptyString_meansNoBoardAtAll() {
        assertEquals(emptySet<HotBoard>(), parseHotBoards(""))
        assertEquals(emptySet<HotBoard>(), parseHotBoards("   "))
    }

    @Test
    fun storedKeys_keepDeclarationOrderAndIgnoreTheRest() {
        assertEquals(
            listOf(HotBoard.Fun, HotBoard.Mine),
            parseHotBoards("mineband, fun, not-a-board").toList(),
        )
        assertEquals(
            HotBoard.entries.toList(),
            parseHotBoards(HotBoard.entries.joinToString(",") { it.filterType }).toList(),
        )
    }

    @Test
    fun singleTitle_appendsHotSuffixExceptRealtime() {
        assertEquals("全部热搜", HotBoard.Realtime.singleTitle)
        assertEquals("文娱热搜", HotBoard.Fun.singleTitle)
        assertEquals("社会热搜", HotBoard.Social.singleTitle)
        assertEquals("生活热搜", HotBoard.Life.singleTitle)
        assertEquals("个性化热搜", HotBoard.Mine.singleTitle)
    }
}
