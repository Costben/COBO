package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 服务端插进时间线、热搜的推广内容要在解析层就丢掉。
 *
 * 只认 `mark` 里的 `WeiboAD*`：真实样本里明星、品牌自己发的商业博也带 `ad_state: 1`，
 * 那些不能用同一条规则删掉，否则关注流会少正常内容。
 */
class PromotionFilterTest {

    private fun status(id: String, mark: String? = null, adState: Int? = null) = JSONObject()
        .put("id", id)
        .put("mid", id)
        .put("text", "正文 $id")
        .put("created_at", "Mon Nov 17 10:00:00 +0800 2026")
        .put("user", JSONObject().put("id", "42").put("screen_name", "测试用户"))
        .apply {
            if (mark != null) put("mark", mark)
            if (adState != null) put("ad_state", adState)
        }

    @Test
    fun parseCardsDropsPromotedPosts() {
        val cards = JSONArray()
            .put(JSONObject().put("card_type", 9).put("mblog", status("1")))
            .put(
                JSONObject().put("card_type", 9)
                    .put("mblog", status("2", mark = "999_reallog_mark_ad:999|WeiboADNatural"))
            )
            .put(JSONObject().put("card_type", 9).put("mblog", status("3", adState = 1)))
            .put(
                JSONObject().put("card_type", 11).put(
                    "card_group",
                    JSONArray()
                        .put(
                            JSONObject().put("card_type", 9)
                                .put("mblog", status("4", mark = "999_reallog_mark_ad:999|WeiboAD"))
                        )
                        .put(JSONObject().put("card_type", 9).put("mblog", status("5"))),
                )
            )
        val root = JSONObject().put("ok", 1).put("data", JSONObject().put("cards", cards))

        assertEquals(listOf("1", "3", "5"), parseCards(root).items.map { it.id })
    }

    @Test
    fun statusesOfDropsPromotedPosts() {
        val arr = JSONArray()
            .put(status("1"))
            .put(status("2", mark = "999_reallog_mark_ad:999|WeiboADNatural"))

        assertEquals(listOf("1"), statusesOf(arr).map { it.id })
    }

    @Test
    fun parseHotItemsDropsAdSlotsAndRanksStayContiguous() {
        val group = JSONArray()
            .put(JSONObject().put("card_type", 4).put("desc", "普通热搜一").put("itemid", "seqid:1|cate:5001|t:31|c_type:31"))
            .put(JSONObject().put("card_type", 4).put("desc", "减重多样选择").put("itemid", "seqid:1|adid:366933|is_ad_pos:1|cate:5001"))
            .put(JSONObject().put("card_type", 4).put("desc", "普通热搜二").put("itemid", "seqid:1|qtime:1|cate:5001|t:31"))
        val root = JSONObject().put("ok", 1).put(
            "data",
            JSONObject().put(
                "cards",
                JSONArray().put(JSONObject().put("card_type", 11).put("card_group", group)),
            ),
        )

        val items = parseHotItems(root)
        assertEquals(listOf("普通热搜一", "普通热搜二"), items.map { it.title })
        assertEquals(listOf(1, 2), items.map { it.rank })
    }

    @Test
    fun promotedPostStillParsesForDetail() {
        val promoted = status("2", mark = "999_reallog_mark_ad:999|WeiboADNatural")
        assertNotNull(Status.from(promoted))
    }

    @Test
    fun isPromotedStatusCatchesClientPromotionPayloadsWithoutBreakingOrganicAdState() {
        val arr = JSONArray()
            .put(status("organic_ad_state", adState = 1))
            .put(status("with_promotion_obj").put("promotion", JSONObject().put("type", "ad")))
            .put(status("mblogtype_ad").put("mblogtype", 1))
            .put(status("ad_marked").put("ad_marked", true))
            .put(status("is_ad_flag").put("is_ad", 1))
            .put(status("ad_title").put("title", JSONObject().put("text", "品牌推广")))
            .put(status("readtimetype_ad").put("readtimetype", "adMblog"))

        assertEquals(listOf("organic_ad_state"), statusesOf(arr).map { it.id })
    }

    @Test
    fun followFeedDropsUnfollowedInjectedPostsWhileKeepingFollowedAndSelfAndRetweets() {
        fun statusWithUser(id: String, uid: String, following: Boolean?, adState: Int? = null): JSONObject {
            val u = JSONObject().put("id", uid).put("screen_name", "用户$uid")
            if (following != null) u.put("following", following)
            return status(id, adState = adState).put("user", u)
        }

        val retweetOfUnfollowed = statusWithUser("retweet_ok", uid = "100", following = true).put(
            "retweeted_status",
            statusWithUser("inner_unfollowed", uid = "999", following = false),
        )
        val arr = JSONArray()
            .put(statusWithUser("followed_organic", uid = "100", following = true))
            .put(statusWithUser("followed_brand_ad_state", uid = "101", following = true, adState = 1))
            .put(statusWithUser("self_post", uid = "777", following = false))
            .put(statusWithUser("no_following_field", uid = "102", following = null))
            .put(retweetOfUnfollowed)
            .put(statusWithUser("huawei_unfollowed_ad", uid = "888", following = false, adState = 1))
            .put(statusWithUser("stealth_unfollowed_inject", uid = "889", following = false))

        assertEquals(
            listOf("followed_organic", "followed_brand_ad_state", "self_post", "no_following_field", "retweet_ok"),
            statusesOf(arr, requireFollowedAuthor = true, selfUid = "777").map { it.id },
        )
    }
}
