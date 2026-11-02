package com.cobo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.data.Membership
import com.cobo.app.data.User
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 用户列表里的一行：搜索结果和关注 / 粉丝列表共用。 */
@Composable
fun UserRow(user: User, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatar, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.membership != Membership.None) {
                    Spacer(Modifier.width(4.dp))
                    MemberBadge(user.membership, compact = true)
                }
                if (user.verified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge()
                }
            }
            if (user.description.isNotBlank()) {
                Text(
                    user.description,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "${fmtCount(user.followers)} 粉丝",
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
