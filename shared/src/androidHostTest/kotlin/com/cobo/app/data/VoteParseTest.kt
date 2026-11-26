package com.cobo.app.data

import com.cobo.app.ui.isWeiboShortLink
import com.cobo.app.ui.weiboVoteId
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteParseTest {

    @Test
    fun extractVoteIdFromVariousFormats() {
        assertEquals(
            "2026_10794768_-_8ecd76",
            extractVoteId("https://vote.weibo.com/h5/index/index?vote_id=2026_10794768_-_8ecd76"),
        )
        assertEquals(
            "2026_10794768_-_8ecd76",
            extractVoteId("https://vote.weibo.com/h5/index/index?vote_id=2026_10794768_-_8ecd76&from=1FFFF96039&weiboauthoruid=6048569942"),
        )
        assertEquals(
            "2026_10794768_-_8ecd76",
            extractVoteId("2317162026_10794768_-_8ecd76"),
        )
        assertEquals(
            "2026_10794768_-_8ecd76",
            weiboVoteId("https://vote.weibo.com/h5/index/index?vote_id=2026_10794768_-_8ecd76"),
        )
        assertNull(extractVoteId("https://m.weibo.cn/status/5360117360888504"))
        assertNull(extractVoteId(null))
    }

    @Test
    fun shortLinkDetection() {
        assertTrue(isWeiboShortLink("http://t.cn/AXjmOWXn"))
        assertTrue(isWeiboShortLink("https://t.cn/AXjmOWXn"))
        assertTrue(isWeiboShortLink("  http://t.cn/abc123  "))
        assertFalse(isWeiboShortLink("https://m.weibo.cn/status/123"))
        assertFalse(isWeiboShortLink("https://vote.weibo.com/h5/index"))
    }

    @Test
    fun parseVoteInfoJsonMatchesFields() {
        val viJson = JSONObject(
            """
            {
                "id": "2026_10794768_-_8ecd76",
                "title": "极限二选一",
                "limited": "1",
                "status": "1",
                "end_time_str": "截止时间 2026年11月07日 16:23",
                "part_num": "3970",
                "parted": "0",
                "launch_userinfo": {
                    "uid": "6048569942",
                    "name": "数码闲聊站",
                    "avatar_large": "https://wx1.sinaimg.cn/orj480/006BlblIly8gdim8sx8poj30u00u0adb.jpg",
                    "logo": "gold"
                },
                "option_list": [
                    {
                        "id": "28284703",
                        "title": "7英寸2K 19.5:9大直屏",
                        "part_num": "1917票",
                        "part_ratio": "48.29",
                        "selected": "0"
                    },
                    {
                        "id": "28284704",
                        "title": "6.5英寸1.5K 16:9阔直屏",
                        "part_num": "2053票",
                        "part_ratio": "51.71",
                        "selected": "0"
                    }
                ]
            }
            """.trimIndent()
        )

        val vote = parseVoteInfoJson("2026_10794768_-_8ecd76", "sk_test_123", viJson)
        assertEquals("2026_10794768_-_8ecd76", vote.id)
        assertEquals("极限二选一", vote.title)
        assertEquals("sk_test_123", vote.sk)
        assertEquals(1, vote.status)
        assertFalse(vote.isMultiSelect)
        assertEquals(1, vote.maxSelect)
        assertEquals("截止时间 2026年11月07日 16:23", vote.endTimeStr)
        assertEquals("2026年11月07日 16:23 截止", vote.deadlineLabel)
        assertEquals(3970, vote.partNum)
        assertFalse(vote.parted)
        assertFalse(vote.isExpired)

        val author = vote.author
        assertNotNull(author)
        assertEquals("6048569942", author!!.id)
        assertEquals("数码闲聊站", author.name)
        assertTrue(author.svip)

        assertEquals(2, vote.options.size)
        val opt1 = vote.options[0]
        assertEquals("28284703", opt1.id)
        assertEquals("7英寸2K 19.5:9大直屏", opt1.title)
        assertEquals("1917票", opt1.partNum)
        assertEquals(48.29f, opt1.partRatio, 0.01f)
        assertEquals("48.3%", opt1.ratioLabel)
        assertFalse(opt1.selected)

        val opt2 = vote.options[1]
        assertEquals("28284704", opt2.id)
        assertEquals(51.71f, opt2.partRatio, 0.01f)
    }

    @Test
    fun statusParsingDetectsVote() {
        val statusJson = JSONObject(
            """
            {
                "id": "5360117360888504",
                "mid": "5360117360888504",
                "text": "子系下一代旗舰的屏幕选型，你们喜欢哪个？",
                "created_at": "Sat Oct 31 16:23:48 +0800 2026",
                "is_vote": 1,
                "url_struct": [
                    {
                        "ori_url": "https://vote.weibo.com/h5/index/index?vote_id=2026_10794768_-_8ecd76"
                    }
                ]
            }
            """.trimIndent()
        )

        val status = Status.from(statusJson)
        assertNotNull(status)
        assertTrue(status!!.isVote)
        assertEquals("2026_10794768_-_8ecd76", status.voteId)
    }

    @Test
    fun pageInfoType23SuperTopicIsNotMisparsedAsVote() {
        val statusJson = JSONObject(
            """
            {
                "id": "5360117360888599",
                "mid": "5360117360888599",
                "text": "💎Bin哥厉害1109",
                "created_at": "Sun Nov 09 12:00:00 +0800 2026",
                "page_info": {
                    "type": "23",
                    "object_type": "webpage",
                    "page_id": "100808abcdef123456",
                    "page_url": "https://m.weibo.cn/p/index?containerid=100808abcdef123456"
                }
            }
            """.trimIndent()
        )

        val status = Status.from(statusJson)
        assertNotNull(status)
        assertFalse(status!!.isVote)
        assertNull(status.voteId)
    }

    @Test
    fun voteRepostDeduplicatesSharedVoteIntoRetweetedStatus() {
        val statusJson = JSONObject(
            """
            {
                "id": "5360117360889001",
                "mid": "5360117360889001",
                "text": "我投给了“A.布伦特福德主场取胜”~",
                "created_at": "Mon Nov 23 16:47:00 +0800 2026",
                "is_vote": 1,
                "page_info": {
                    "object_type": "hudongvote",
                    "card_info": {
                        "vote_object": { "id": "2026_10794768_-_8ecd76" }
                    }
                },
                "retweeted_status": {
                    "id": "5360117360888900",
                    "mid": "5360117360888900",
                    "text": "你看好本场比赛是什么结果？",
                    "created_at": "Mon Nov 23 16:13:00 +0800 2026",
                    "is_vote": 1,
                    "page_info": {
                        "object_type": "hudongvote",
                        "card_info": {
                            "vote_object": { "id": "2026_10794768_-_8ecd76" }
                        }
                    }
                }
            }
            """.trimIndent()
        )

        val status = Status.from(statusJson)!!
        assertFalse(status.isVote)
        assertNull(status.voteId)
        assertNotNull(status.retweeted)
        assertTrue(status.retweeted!!.isVote)
        assertEquals("2026_10794768_-_8ecd76", status.retweeted!!.voteId)
    }

    @Test
    fun hoistedVoteOnRepostMovesIntoRetweetedStatus() {
        val statusJson = JSONObject(
            """
            {
                "id": "5360117360889002",
                "mid": "5360117360889002",
                "text": "转发参与投票",
                "created_at": "Mon Nov 23 16:50:00 +0800 2026",
                "is_vote": 1,
                "page_info": {
                    "object_type": "hudongvote",
                    "card_info": {
                        "vote_object": { "id": "2026_99999999_-_abcdef" }
                    }
                },
                "retweeted_status": {
                    "id": "5360117360888901",
                    "mid": "5360117360888901",
                    "text": "原博投票",
                    "created_at": "Mon Nov 23 16:10:00 +0800 2026"
                }
            }
            """.trimIndent()
        )

        val status = Status.from(statusJson)!!
        assertFalse(status.isVote)
        assertNull(status.voteId)
        assertNotNull(status.retweeted)
        assertTrue(status.retweeted!!.isVote)
        assertEquals("2026_99999999_-_abcdef", status.retweeted!!.voteId)
    }
}
