package com.cobo.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.net.URLEncoder

@Composable
fun TopicScreen(nav: NavState, initial: String = "") {
    var query by remember { mutableStateOf(initial) }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "话题 / 超话",
                navigationIcon = { BackIconButton { nav.pop() } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "输入话题名，例如「摄影」或「超话」名称，打开微博的话题页。",
                fontSize = 13.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(14.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = "话题名",
                backgroundMode = TextFieldMode.Line,
                singleLine = true,
                showClearButton = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val name = query.trim()
                    if (name.isBlank()) return@Button
                    val cid = "100103type=1&q=$name"
                    val url = "https://m.weibo.cn/search?containerid=" +
                        URLEncoder.encode(cid, "UTF-8")
                    nav.push(Route.Web(url, name))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开话题")
            }
        }
    }
}
