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
}
