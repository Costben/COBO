package com.cobo.app.data

import com.cobo.app.screen.SubReplySort
import com.cobo.app.screen.orderSubReplies
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAndSubReplyTest {
    @Test
    fun filterRecommendedSearchUsersCapsAtThreeVerifiedMatches() {
        val candidates = listOf(
            User(id = "1", name = "小米手机", avatar = "", verified = true, followers = 8_000_000),
            User(id = "2", name = "普通路人小米", avatar = "", verified = false, followers = 120),
            User(id = "3", name = "小米公司", avatar = "", verified = true, followers = 5_000_000),
            User(id = "4", name = "小米汽车", avatar = "", verified = true, followers = 2_000_000),
            User(id = "5", name = "小米之家", avatar = "", verified = true, followers = 1_000_000),
        )
        val result = filterRecommendedSearchUsers(query = "小米", users = candidates)
        assertEquals(listOf("1", "3", "4"), result.map { it.id })
    }

    @Test
    fun filterRecommendedSearchUsersReturnsEmptyWhenNoMatch() {
        val candidates = listOf(
            User(id = "10", name = "张三", avatar = "", verified = false, followers = 50),
            User(id = "11", name = "李四", avatar = "", verified = true, followers = 200),
        )
        val result = filterRecommendedSearchUsers(query = "今天天气", users = candidates)
        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun buildSearchSuggestEntriesPlacesUpToThreeUsersAtSlotsFiveToSeven() {
        val keywords = listOf("鞠婧祎", "鞠婧祎工作室official", "鞠婧祎超话", "鞠婧祎618", "鞠婧祎_Official", "鞠婧祎热搜")
        val users = listOf(
            User(id = "u1", name = "鞠婧祎", avatar = "", verified = true, followers = 34_759_000),
            User(id = "u2", name = "鞠婧祎_Official", avatar = "", verified = true, followers = 1_569_000),
            User(id = "u3", name = "鞠婧祎应援会官微", avatar = "", verified = true, followers = 1_705_000),
        )
        val entries = buildSearchSuggestEntries(keywords, users)
        assertEquals(
            listOf(
                SearchSuggestEntry.Keyword("鞠婧祎"),
                SearchSuggestEntry.Keyword("鞠婧祎工作室official"),
                SearchSuggestEntry.Keyword("鞠婧祎超话"),
                SearchSuggestEntry.Keyword("鞠婧祎618"),
                SearchSuggestEntry.Account(users[0]),
                SearchSuggestEntry.Account(users[1]),
                SearchSuggestEntry.Account(users[2]),
                SearchSuggestEntry.Keyword("鞠婧祎热搜"),
            ),
            entries,
        )
    }

    @Test
    fun parseSearchSuggestResultExtractsEmbeddedUsersAndKeywords() {
        val raw = """
            ["鞠婧祎", [
              {"card": {"desc_dark": "鞠婧祎"}},
              {"card": {"desc_dark": "鞠婧祎超话"}},
              {"card": {"user": {"id": "100", "screen_name": "鞠婧祎", "verified": true, "followers_count": 34759000}}}
            ]]
        """.trimIndent()
        val parsed = ContainerApi.parseSearchSuggestResult(raw)
        assertEquals(listOf("鞠婧祎", "鞠婧祎超话"), parsed.keywords)
        assertEquals(listOf("100"), parsed.users.map { it.id })
    }

    @Test
    fun parsesBlueAndGoldVerifiedKindFromWeiboVerifiedType() {
        val goldUser = User.from(
            org.json.JSONObject("""{"id":"1","screen_name":"大V","verified":true,"verified_type":0}"""),
        )!!
        val blueUser = User.from(
            org.json.JSONObject("""{"id":"2","screen_name":"官方号","verified":true,"verified_type":2}"""),
        )!!
        val plainUser = User.from(
            org.json.JSONObject("""{"id":"3","screen_name":"路人","verified":false,"verified_type":-1}"""),
        )!!
        assertEquals(VerifiedKind.Gold, goldUser.verifiedKind)
        assertEquals(VerifiedKind.Blue, blueUser.verifiedKind)
        assertEquals(VerifiedKind.None, plainUser.verifiedKind)
    }

    @Test
    fun orderSubRepliesSupportsHotOldAndNew() {
        val cOld = Comment(
            id = "1",
            textHtml = "old",
            createdAt = "Sun Nov 22 10:00:00 +0800 2026",
            user = null,
            likes = 5,
        )
        val cMidHot = Comment(
            id = "2",
            textHtml = "hot",
            createdAt = "Sun Nov 22 11:00:00 +0800 2026",
            user = null,
            likes = 99,
        )
        val cNew = Comment(
            id = "3",
            textHtml = "new",
            createdAt = "Sun Nov 22 12:00:00 +0800 2026",
            user = null,
            likes = 12,
        )
        val items = listOf(cMidHot, cNew, cOld)
        assertEquals(listOf("2", "3", "1"), orderSubReplies(items, SubReplySort.Hot).map { it.id })
        assertEquals(listOf("1", "2", "3"), orderSubReplies(items, SubReplySort.Old).map { it.id })
        assertEquals(listOf("3", "2", "1"), orderSubReplies(items, SubReplySort.New).map { it.id })
    }
}
