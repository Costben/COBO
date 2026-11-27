package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentFilterTest {

    private fun user(id: String) = User(id = id, name = "用户$id", avatar = "")

    private fun comment(
        id: String,
        uid: String,
        html: String = "普通评论 $id",
        pics: List<Pic> = emptyList(),
        reply: Comment? = null,
        replies: List<Comment> = emptyList(),
    ) = Comment(
        id = id,
        textHtml = html,
        user = user(uid),
        createdAt = "Mon Nov 17 10:05:00 +0800 2026",
        likes = 0,
        pics = pics,
        reply = reply,
        replies = replies,
    )

    @Test
    fun inactiveFilterReturnsOriginalListDirectly() {
        val list = listOf(comment("1", "u1"), comment("2", "u2"))
        val filtered = filterComments(list, CommentFilterState(), authorUid = "op")
        assertSame(list, filtered)
    }

    @Test
    fun authorOnlyMatchesDirectAuthorCommentsAndNestedAuthorReplies() {
        val opDirect = comment("1", "op")
        val viewerWithOpReply = comment(
            id = "2",
            uid = "viewer",
            replies = listOf(comment("2_1", "op", html = "博主回复")),
        )
        val viewerOnly = comment(
            id = "3",
            uid = "viewer",
            replies = listOf(comment("3_1", "other")),
        )

        val detailFiltered = filterComments(
            listOf(opDirect, viewerWithOpReply, viewerOnly),
            CommentFilterState(authorOnly = true),
            authorUid = "op",
            includeReplies = true,
        )
        assertEquals(listOf("1", "2"), detailFiltered.map { it.id })

        // 楼中楼扁平回复列表只判断回复作者本人
        val flatFiltered = filterComments(
            listOf(opDirect, viewerWithOpReply),
            CommentFilterState(authorOnly = true),
            authorUid = "op",
            includeReplies = false,
        )
        assertEquals(listOf("1"), flatFiltered.map { it.id })
    }

    @Test
    fun imagesOnlyDetectsPicsInlinePhotoLinksQuotedAndNestedImages() {
        val nativePic = comment("1", "u1", pics = listOf(Pic("t", "l")))
        val inlineDirectLink = comment(
            "2",
            "u2",
            html = "看这里 <a href=\"https://wx1.sinaimg.cn/large/abc123def4567890abcd.jpg\">查看图片</a>",
        )
        val inlineResolvableShort = comment(
            "2b",
            "u2b",
            html = "附图 <a href=\"https://t.cn/A6x12345\">评论配图</a>",
        )
        val quotedPic = comment(
            "3",
            "u3",
            reply = comment("3_q", "u4", pics = listOf(Pic("qt", "ql"))),
        )
        val nestedReplyPic = comment(
            "4",
            "u5",
            replies = listOf(comment("4_r", "u6", pics = listOf(Pic("rt", "rl")))),
        )
        val textOnly = comment("5", "u7", html = "纯文字讨论")

        val detailImages = filterComments(
            listOf(nativePic, inlineDirectLink, inlineResolvableShort, quotedPic, nestedReplyPic, textOnly),
            CommentFilterState(imagesOnly = true),
            authorUid = "op",
            includeReplies = true,
        )
        assertEquals(listOf("1", "2", "2b", "3", "4"), detailImages.map { it.id })
        assertFalse(detailImages.any { it.id == "5" })

        val flatImages = filterComments(
            listOf(nativePic, inlineDirectLink, inlineResolvableShort, quotedPic, nestedReplyPic, textOnly),
            CommentFilterState(imagesOnly = true),
            authorUid = "op",
            includeReplies = false,
        )
        assertEquals(listOf("1", "2", "2b", "3"), flatImages.map { it.id })
    }

    @Test
    fun combinedAuthorAndImagesRequiresIntersection() {
        val opTextOnly = comment("1", "op", html = "文字")
        val opWithImage = comment("2", "op", pics = listOf(Pic("t", "l")))
        val viewerWithImage = comment("3", "viewer", pics = listOf(Pic("t2", "l2")))
        val viewerTextOpReplyImage = comment(
            "4",
            "viewer",
            replies = listOf(comment("4_r", "op", pics = listOf(Pic("t3", "l3")))),
        )

        val result = filterComments(
            listOf(opTextOnly, opWithImage, viewerWithImage, viewerTextOpReplyImage),
            CommentFilterState(authorOnly = true, imagesOnly = true),
            authorUid = "op",
            includeReplies = true,
        )
        assertEquals(listOf("2", "4"), result.map { it.id })
        assertTrue(commentFilterEmptyMessage(CommentFilterState(authorOnly = true, imagesOnly = true)).contains("博主"))
        assertTrue(commentFilterEmptyMessage(CommentFilterState(authorOnly = true)).contains("博主"))
        assertTrue(commentFilterEmptyMessage(CommentFilterState(imagesOnly = true)).contains("带图"))
    }

    @Test
    fun triggerLabelReflectsActiveFilterCombination() {
        assertEquals("全部评论", commentFilterTriggerLabel(CommentFilterState(), "全部评论"))
        assertEquals("仅看博主", commentFilterTriggerLabel(CommentFilterState(authorOnly = true), "全部评论"))
        assertEquals("仅看带图", commentFilterTriggerLabel(CommentFilterState(imagesOnly = true), "全部回复 · 2"))
        assertEquals("博主 · 带图", commentFilterTriggerLabel(CommentFilterState(authorOnly = true, imagesOnly = true), "全部回复 · 2"))
    }
}
