package com.cobo.app.data

import com.cobo.app.ui.extractDirectImageUrlsFromHtml
import com.cobo.app.ui.extractResolvablePhotoLinksFromHtml

/**
 * 详情页与楼中楼共用的评论快速筛选条件（页面作用域内存态）。
 */
data class CommentFilterState(
    val authorOnly: Boolean = false,
    val imagesOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = authorOnly || imagesOnly
}

/**
 * 判断单条评论自身是否附带图片（原生 `pics`、直链图或正文内联「查看图片 / 评论配图」跳转链接）。
 */
fun commentHasInlineImage(comment: Comment): Boolean =
    comment.pics.isNotEmpty() ||
        extractDirectImageUrlsFromHtml(comment.textHtml).isNotEmpty() ||
        extractResolvablePhotoLinksFromHtml(comment.textHtml).isNotEmpty()

/**
 * 判断评论及其引用回复或内联子回复中是否包含图片。
 */
fun commentHasAnyImage(
    comment: Comment,
    includeReplies: Boolean = true,
): Boolean {
    if (commentHasInlineImage(comment)) return true
    val quoted = comment.reply
    if (quoted != null && commentHasInlineImage(quoted)) return true
    if (includeReplies && comment.replies.any { commentHasInlineImage(it) }) return true
    return false
}

/**
 * 判断评论是否为博主的发言，或包含博主在内联楼中楼中的回复。
 */
fun commentInvolvesAuthor(
    comment: Comment,
    authorUid: String?,
    includeReplies: Boolean = true,
): Boolean {
    val target = authorUid?.trim().orEmpty()
    if (target.isEmpty()) return false
    if (comment.user?.id == target) return true
    return includeReplies && comment.replies.any { it.user?.id == target }
}

/**
 * 按「仅看博主 / 仅看带图」交集筛选评论列表。
 */
fun filterComments(
    comments: List<Comment>,
    filter: CommentFilterState,
    authorUid: String?,
    includeReplies: Boolean = true,
): List<Comment> {
    if (!filter.isActive) return comments
    val applyAuthor = filter.authorOnly && !authorUid.isNullOrBlank()
    if (!applyAuthor && !filter.imagesOnly) return comments
    return comments.filter { comment ->
        val passAuthor = !applyAuthor || commentInvolvesAuthor(comment, authorUid, includeReplies)
        val passImages = !filter.imagesOnly || commentHasAnyImage(comment, includeReplies)
        passAuthor && passImages
    }
}

fun commentFilterEmptyMessage(filter: CommentFilterState): String = when {
    filter.authorOnly && filter.imagesOnly -> "已加载评论中暂无博主的带图评论"
    filter.authorOnly -> "已加载评论中暂无博主评论或回复"
    filter.imagesOnly -> "已加载评论中暂无带图评论"
    else -> "暂无评论"
}
