package com.cobo.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 手机号 + 短信验证码登录。
 *
 * 走客户端接口，服务端会一并下发 gsid 和长期凭证。微博目前对这条通道回
 * `-105 客户端身份校验失败`，所以失败时会自动退到网页登录；通道恢复后不用改代码。
 */
@Composable
fun SmsLoginScreen(nav: NavState, addAccount: Boolean, relogin: String? = null) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var hint by remember {
        mutableStateOf(
            if (relogin != null) "登录「$relogin」，完成后会更新这个账号的登录信息"
            else "输入手机号，收到短信验证码后登录",
        )
    }
    var sending by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown <= 0) return@LaunchedEffect
        delay(1000)
        cooldown -= 1
    }

    val ready = phone.trim().length >= 11 && code.trim().isNotEmpty()

    fun sendCode() {
        val number = phone.trim()
        if (number.length < 11 || sending || cooldown > 0) return
        sending = true
        hint = "正在发送验证码…"
        scope.launch {
            val error = runCatching { App.repo.sendLoginCode(number) }.exceptionOrNull()
            sending = false
            if (error == null) {
                hint = "验证码已发送，请查收短信"
                cooldown = 60
            } else {
                hint = error.message ?: "验证码发送失败"
            }
        }
    }

    fun login() {
        if (!ready || logging) return
        logging = true
        hint = "正在登录…"
        scope.launch {
            val result = runCatching { App.repo.loginWithClient(phone.trim(), code.trim()) }
            logging = false
            val account = result.getOrNull()
            if (account == null) {
                val reason = result.exceptionOrNull()?.message ?: "登录失败"
                // 客户端通道被微博关掉以后（-105 客户端身份校验失败），自动退到网页登录，
                // 和 Share 当年内建登录被封之后的处理一样。
                if (reason.contains("客户端")) {
                    hint = "微博已停用这条客户端通道，正在改用网页登录…"
                    nav.push(Route.Login(addAccount = addAccount, fresh = addAccount, relogin = relogin))
                } else {
                    hint = reason
                }
                return@launch
            }
            hint = "已登录：${account.name.ifBlank { account.uid }}"
            if (addAccount) {
                nav.reset(Route.Main)
                nav.push(Route.Accounts)
            } else {
                nav.reset(Route.Main)
            }
        }
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = if (relogin != null) "重新登录" else if (addAccount) "添加账号" else "登录",
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "短信登录走客户端接口，微博可能拒绝；被拒会自动改用网页登录。",
                fontSize = 13.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(14.dp))
            TextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                modifier = Modifier.fillMaxWidth().testTag("sms_phone"),
                label = "手机号",
                backgroundMode = TextFieldMode.Line,
                singleLine = true,
                showClearButton = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    modifier = Modifier.weight(1f).testTag("sms_code"),
                    label = "短信验证码",
                    backgroundMode = TextFieldMode.Line,
                    singleLine = true,
                )
                TextButton(
                    text = when {
                        sending -> "发送中…"
                        cooldown > 0 -> "${cooldown}s"
                        else -> "获取验证码"
                    },
                    onClick = { sendCode() },
                    enabled = phone.trim().length >= 11 && !sending && cooldown <= 0,
                    modifier = Modifier.testTag("sms_send"),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { login() },
                enabled = ready && !logging,
                modifier = Modifier.fillMaxWidth().testTag("sms_login"),
            ) {
                Text(if (logging) "登录中…" else "登录")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                hint,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                text = "改用网页登录",
                onClick = {
                    nav.push(
                        Route.Login(
                            addAccount = addAccount,
                            fresh = addAccount,
                            relogin = relogin,
                        )
                    )
                },
                modifier = Modifier.testTag("sms_web"),
            )
        }
    }
}
