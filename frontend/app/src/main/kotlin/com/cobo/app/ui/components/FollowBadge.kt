package com.cobo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.data.FollowTag
import io.github.suqi8.coui.kmp.basic.Text

private class BadgeColors(val background: Color, val border: Color, val content: Color)

/** 关注标记的三档配色：特别橙、悄悄紫、本地青。底色用实色，不用 alpha 叠，白卡和灰底上才不变色。 */
private fun FollowTag.badgeColors(): BadgeColors = when (this) {
    FollowTag.Special -> BadgeColors(Color(0xFFFFE9D2), Color(0xFFF0A85C), Color(0xFF9A3D00))
    FollowTag.Secret -> BadgeColors(Color(0xFFEFE6FF), Color(0xFFB39BF0), Color(0xFF5B21B6))
    FollowTag.Local -> BadgeColors(Color(0xFFD9F2EC), Color(0xFF6FC3B6), Color(0xFF0B5F58))
}

/**
 * 关注标记的小徽标：主页贴在昵称上一行，信息流里跟在昵称后面、认证勾的左边。
 * compact 是信息流用的尺寸。高度写死、行高收紧，文字才真正落在正中。
 */
@Composable
fun FollowTagBadge(tag: FollowTag, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = tag.badgeColors()
    val shape = RoundedCornerShape(if (compact) 4.dp else 5.dp)
    Box(
        modifier = modifier
            .height(if (compact) 15.dp else 16.dp)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = if (compact) 4.dp else 5.dp)
            .testTag("follow_badge_${tag.key}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tag.label,
            fontSize = if (compact) 9.sp else 10.sp,
            lineHeight = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.content,
        )
    }
}
