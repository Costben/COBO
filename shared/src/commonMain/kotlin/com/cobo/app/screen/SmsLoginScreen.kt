package com.cobo.app.screen

import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.PlatformWebViewHandle
import com.cobo.app.platform.base64Decode
import com.cobo.app.platform.defaultWebUserAgent
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.parseUrlPath
import com.cobo.app.platform.readBundledAssetText
import com.cobo.app.platform.urlDecode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.Api
import com.cobo.app.data.ClientLogin
import com.cobo.app.data.CookieVault
import com.cobo.app.data.Repo
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.WeiboWebView
import com.cobo.app.ui.components.extractWebViewFallbackUrl
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject

private sealed interface ClientAuthStage {
    data object Credentials : ClientAuthStage
    data class Captcha(val challenge: ClientLogin.CaptchaChallenge) : ClientAuthStage
    data class SafetyVerify(val challenge: ClientLogin.SafetyChallenge) : ClientAuthStage
    data class WebVerify(val url: String) : ClientAuthStage
}

/**
 * Share 3.9.6 客户端登录页（长期续签）：
 * - 默认账号（手机号/邮箱）+ 密码首步登录；
 * - 若账号开启登录保护（`-1007`），切入单次短信验证（`login_smsverify` -> `account/login`），只需收一次短信；
 * - 也兼容图形验证码（`-1005`）、H5 验证回传 `alt`，以及直接手机号短信登录兜底；
 * - 全程不在本地保存密码，只持久化长期 `gsid` 与四域 Cookie 表。
 */
@Composable
fun SmsLoginScreen(nav: NavState, addAccount: Boolean, relogin: String? = null) {
    val scope = rememberCoroutineScope()
    var useDirectSms by remember { mutableStateOf(true) }
    var stage by remember { mutableStateOf<ClientAuthStage>(ClientAuthStage.Credentials) }

    // 账号密码表单态
    var accountInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 图形验证码（-1005）
    var captchaCode by remember { mutableStateOf("") }

    // 登录保护短信验证（-1007）
    var selectedPhoneIdx by remember { mutableIntStateOf(0) }
    var safetyNumber by remember { mutableStateOf("") }
    var safetySmsCode by remember { mutableStateOf("") }

    // 直接手机号短信登录
    var directPhone by remember { mutableStateOf("") }
    var directCode by remember { mutableStateOf("") }

    var sending by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }
    var hint by remember {
        mutableStateOf(
            if (relogin != null) "请重新登录「$relogin」" else "",
        )
    }

    LaunchedEffect(cooldown) {
        if (cooldown <= 0) return@LaunchedEffect
        delay(1000)
        cooldown -= 1
    }

    fun onSignedIn(account: Account) {
        password = ""
        hint = "已登录：${account.name.ifBlank { account.uid }}"
        if (addAccount) {
            nav.reset(Route.Main)
            nav.push(Route.Accounts)
        } else {
            nav.reset(Route.Main)
        }
    }

    fun handleStep(step: Repo.ClientLoginStep) {
        when (step) {
            is Repo.ClientLoginStep.Success -> onSignedIn(step.account)
            is Repo.ClientLoginStep.Captcha -> {
                captchaCode = ""
                stage = ClientAuthStage.Captcha(step.challenge)
                hint = step.challenge.message.ifBlank { "请输入图形验证码" }
            }
            is Repo.ClientLoginStep.SafetyVerify -> {
                selectedPhoneIdx = 0
                safetyNumber = ""
                safetySmsCode = ""
                cooldown = 0
                stage = ClientAuthStage.SafetyVerify(step.challenge)
                hint = step.challenge.message.ifBlank { "需要登录保护短信验证" }
            }
            is Repo.ClientLoginStep.WebVerify -> {
                stage = ClientAuthStage.WebVerify(step.url)
                hint = step.message.ifBlank { "请在页面中完成安全验证" }
            }
        }
    }

    fun submitPassword(cpt: String? = null, cptCode: String? = null, alt: String? = null) {
        val u = accountInput.trim()
        if (alt == null && (u.isBlank() || password.isBlank())) return
        if (logging) return
        logging = true
        hint = "正在登录…"
        scope.launch {
            val result = runCatching { App.repo.loginWithPassword(u, password, cpt, cptCode, alt) }
            logging = false
            result.fold(
                onSuccess = { handleStep(it) },
                onFailure = { err -> hint = err.message ?: "登录失败" },
            )
        }
    }

    fun sendSafetySms(challenge: ClientLogin.SafetyChallenge) {
        val target = challenge.phones.getOrNull(selectedPhoneIdx) ?: challenge.phones.firstOrNull() ?: return
        if (sending || cooldown > 0) return
        sending = true
        hint = "正在向 ${ClientLogin.maskPhone(target.phone)} 发送验证码…"
        scope.launch {
            val result = runCatching { App.repo.sendSafetyVerifyCode(target, challenge.retcode) }
            sending = false
            result.fold(
                onSuccess = { sent ->
                    safetyNumber = sent.number
                    cooldown = 60
                    hint = sent.message.ifBlank { "验证码已发送，请查收短信" }
                },
                onFailure = { err -> hint = err.message ?: "验证码发送失败" },
            )
        }
    }

    fun submitSafetySms(challenge: ClientLogin.SafetyChallenge) {
        val target = challenge.phones.getOrNull(selectedPhoneIdx) ?: challenge.phones.firstOrNull() ?: return
        val sms = safetySmsCode.trim()
        if (sms.isBlank() || logging) return
        logging = true
        hint = "正在验证并登录…"
        scope.launch {
            val number = safetyNumber.ifBlank { target.phone }
            val result = runCatching {
                App.repo.submitSafetyVerifyCode(
                    account = accountInput.trim(),
                    password = password,
                    phone = target,
                    retcode = challenge.retcode,
                    number = number,
                    smsCode = sms,
                )
            }
            logging = false
            result.fold(
                onSuccess = { handleStep(it) },
                onFailure = { err -> hint = err.message ?: "验证失败" },
            )
        }
    }

    fun sendDirectSms() {
        val number = directPhone.trim()
        if (number.length < 11 || sending || cooldown > 0) return
        sending = true
        hint = "正在发送验证码…"
        scope.launch {
            val error = runCatching { App.repo.sendLoginCode(number) }.exceptionOrNull()
            sending = false
            if (error == null) {
                cooldown = 60
                hint = "验证码已发送，请查收短信"
            } else {
                hint = error.message ?: "验证码发送失败"
            }
        }
    }

    fun submitDirectSms() {
        val number = directPhone.trim()
        val code = directCode.trim()
        if (number.length < 11 || code.isBlank() || logging) return
        logging = true
        hint = "正在登录…"
        scope.launch {
            val result = runCatching { App.repo.loginWithClient(number, code) }
            logging = false
            result.fold(
                onSuccess = { onSignedIn(it) },
                onFailure = { err ->
                    val reason = err.message ?: "登录失败"
                    hint = if (reason.contains("客户端")) "当前通道被服务端拒绝，可改用账号密码或网页登录" else reason
                },
            )
        }
    }

    var webVerifyReload by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun finishFromWebVerifyCookie(header: String, cookies: Map<String, String>) {
        if (logging) return
        logging = true
        hint = "验证完成，正在登录…"
        scope.launch {
            val clientResult = if (accountInput.isNotBlank() && password.isNotBlank()) {
                runCatching { App.repo.loginWithPassword(accountInput.trim(), password) }.getOrNull()
            } else {
                null
            }
            if (clientResult is Repo.ClientLoginStep.Success) {
                logging = false
                onSignedIn(clientResult.account)
                return@launch
            }
            val cookieAccount = runCatching { App.repo.loginWithCookie(header, cookies) }.getOrNull()
            logging = false
            if (cookieAccount != null) {
                onSignedIn(cookieAccount)
            } else if (clientResult != null) {
                handleStep(clientResult)
            } else {
                hint = "读取登录信息失败，请重试"
            }
        }
    }

    PlatformBackHandler(enabled = stage != ClientAuthStage.Credentials) {
        stage = ClientAuthStage.Credentials
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = when {
                        stage is ClientAuthStage.SafetyVerify -> "登录保护验证"
                        stage is ClientAuthStage.WebVerify -> "安全验证"
                        relogin != null -> "重新登录"
                        addAccount -> "添加账号"
                        else -> "登录"
                    },
                    navigationIcon = {
                        if (stage != ClientAuthStage.Credentials || nav.stack.size > 1) {
                            BackIconButton {
                                if (stage != ClientAuthStage.Credentials) stage = ClientAuthStage.Credentials
                                else nav.pop()
                            }
                        }
                    },
                    actions = {
                        if (stage is ClientAuthStage.WebVerify) {
                            HighlightedIconButton(
                                onClick = { webVerifyReload?.invoke() },
                                modifier = Modifier.couiTopAppBarActionItem().testTag("web_verify_reload"),
                            ) {
                                Icon(COUIIcons.Refresh, "刷新验证页", Modifier.size(22.dp))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        when (val current = stage) {
            is ClientAuthStage.WebVerify -> {
                key(current.url) {
                    WebAltVerifyView(
                        url = current.url,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        onBindReload = { webVerifyReload = it },
                        onAlt = { alt ->
                            stage = ClientAuthStage.Credentials
                            submitPassword(alt = alt)
                        },
                        onBrowserClose = {
                            stage = ClientAuthStage.Credentials
                            submitPassword()
                        },
                        onWebCookie = { header, cookies ->
                            stage = ClientAuthStage.Credentials
                            finishFromWebVerifyCookie(header, cookies)
                        },
                    )
                }
            }

            is ClientAuthStage.SafetyVerify -> {
                val challenge = current.challenge
                // 单号码触发登录保护时自动下发唯一一次短信验证码，无需多点一步。
                LaunchedEffect(challenge.retcode) {
                    if (challenge.phones.size == 1 && safetyNumber.isBlank() && cooldown == 0 && !sending) {
                        sendSafetySms(challenge)
                    }
                }
                SafetyVerifyContent(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
                    challenge = challenge,
                    selectedIdx = selectedPhoneIdx,
                    onSelectPhone = { selectedPhoneIdx = it },
                    smsCode = safetySmsCode,
                    onSmsCodeChange = { safetySmsCode = it.filter { c -> c.isDigit() }.take(6) },
                    sending = sending,
                    logging = logging,
                    cooldown = cooldown,
                    hint = hint,
                    onSend = { sendSafetySms(challenge) },
                    onSubmit = { submitSafetySms(challenge) },
                    onBack = { stage = ClientAuthStage.Credentials },
                )
            }

            is ClientAuthStage.Captcha -> {
                CaptchaVerifyContent(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
                    challenge = current.challenge,
                    code = captchaCode,
                    onCodeChange = { captchaCode = it.take(8) },
                    logging = logging,
                    hint = hint,
                    onRefresh = { submitPassword() },
                    onSubmit = { submitPassword(cpt = current.challenge.cpt, cptCode = captchaCode.trim()) },
                    onBack = { stage = ClientAuthStage.Credentials },
                )
            }

            ClientAuthStage.Credentials -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (!useDirectSms) {
                        PasswordCredentialsSection(
                            account = accountInput,
                            onAccountChange = { accountInput = it },
                            password = password,
                            onPasswordChange = { password = it },
                            logging = logging,
                            onSubmit = { submitPassword() },
                        )
                    } else {
                        DirectSmsSection(
                            phone = directPhone,
                            onPhoneChange = { directPhone = it.filter { c -> c.isDigit() }.take(11) },
                            code = directCode,
                            onCodeChange = { directCode = it.filter { c -> c.isDigit() }.take(6) },
                            sending = sending,
                            logging = logging,
                            cooldown = cooldown,
                            onSend = { sendDirectSms() },
                            onSubmit = { submitDirectSms() },
                        )
                    }
                    if (hint.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = hint,
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = if (useDirectSms) "账号密码登录" else "验证码登录",
                            onClick = {
                                useDirectSms = !useDirectSms
                                hint = if (relogin != null) "请重新登录「$relogin」" else ""
                            },
                            modifier = Modifier.weight(1f).testTag("sms_toggle_mode"),
                        )
                        TextButton(
                            text = "网页登录",
                            onClick = {
                                val prev = nav.stack.dropLast(1).lastOrNull()?.route
                                if (prev is Route.Login) nav.pop()
                                else nav.push(Route.Login(addAccount = addAccount, fresh = true, relogin = relogin))
                            },
                            modifier = Modifier.weight(1f).testTag("sms_to_web"),
                        )
                    }
                    if (!addAccount && nav.stack.size == 1) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            text = "不登录，纯本地浏览",
                            onClick = {
                                App.repo.enterLocalMode()
                                nav.reset(Route.Main)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("login_local_mode"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordCredentialsSection(
    account: String,
    onAccountChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    logging: Boolean,
    onSubmit: () -> Unit,
) {
    TextField(
        value = account,
        onValueChange = onAccountChange,
        modifier = Modifier.fillMaxWidth().testTag("login_account"),
        label = "手机号或邮箱",
        backgroundMode = TextFieldMode.Line,
        singleLine = true,
        showClearButton = true,
    )
    Spacer(Modifier.height(12.dp))
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth().testTag("login_password"),
        label = "登录密码",
        backgroundMode = TextFieldMode.Line,
        singleLine = true,
        showPasswordToggle = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        enabled = account.isNotBlank() && password.isNotBlank() && !logging,
        modifier = Modifier.fillMaxWidth().testTag("sms_submit"),
    ) {
        Text(if (logging) "登录中…" else "登录")
    }
}

@Composable
private fun DirectSmsSection(
    phone: String,
    onPhoneChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    sending: Boolean,
    logging: Boolean,
    cooldown: Int,
    onSend: () -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = phone,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth().testTag("sms_phone"),
        label = "手机号",
        backgroundMode = TextFieldMode.Line,
        singleLine = true,
        showClearButton = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.weight(1f).testTag("sms_code"),
            label = "短信验证码",
            backgroundMode = TextFieldMode.Line,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextButton(
            text = when {
                sending -> "发送中…"
                cooldown > 0 -> "${cooldown}s"
                else -> "获取验证码"
            },
            onClick = onSend,
            enabled = phone.length >= 11 && !sending && cooldown <= 0,
            modifier = Modifier.testTag("sms_send"),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        enabled = phone.length >= 11 && code.isNotEmpty() && !logging,
        modifier = Modifier.fillMaxWidth().testTag("sms_submit"),
    ) {
        Text(if (logging) "登录中…" else "登录")
    }
}

@Composable
private fun SafetyVerifyContent(
    modifier: Modifier,
    challenge: ClientLogin.SafetyChallenge,
    selectedIdx: Int,
    onSelectPhone: (Int) -> Unit,
    smsCode: String,
    onSmsCodeChange: (String) -> Unit,
    sending: Boolean,
    logging: Boolean,
    cooldown: Int,
    hint: String,
    onSend: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            "检测到当前账号开启了登录保护，需要用绑定手机号收一次短信验证码。",
            fontSize = 13.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(12.dp))
        challenge.phones.forEachIndexed { idx, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = challenge.phones.size > 1) { onSelectPhone(idx) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (challenge.phones.size > 1) {
                    RadioButton(
                        selected = idx == selectedIdx,
                        onClick = { onSelectPhone(idx) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "验证手机：${ClientLogin.maskPhone(item.phone)}",
                    fontSize = 14.sp,
                    color = COUITheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = smsCode,
                onValueChange = onSmsCodeChange,
                modifier = Modifier.weight(1f).testTag("sms_code"),
                label = "短信验证码",
                backgroundMode = TextFieldMode.Line,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            TextButton(
                text = when {
                    sending -> "发送中…"
                    cooldown > 0 -> "${cooldown}s"
                    else -> "发送验证码"
                },
                onClick = onSend,
                enabled = !sending && cooldown <= 0,
                modifier = Modifier.testTag("sms_send"),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = smsCode.isNotEmpty() && !logging,
            modifier = Modifier.fillMaxWidth().testTag("sms_submit"),
        ) {
            Text(if (logging) "验证中…" else "验证并登录")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = hint,
            fontSize = 12.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(text = "返回账号密码", onClick = onBack)
    }
}

@Composable
private fun CaptchaVerifyContent(
    modifier: Modifier,
    challenge: ClientLogin.CaptchaChallenge,
    code: String,
    onCodeChange: (String) -> Unit,
    logging: Boolean,
    hint: String,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            "请输入下图验证码后继续登录。",
            fontSize = 13.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (challenge.picUrl.isNotBlank()) {
                AsyncImage(
                    model = challenge.picUrl,
                    contentDescription = "验证码",
                    modifier = Modifier.width(136.dp).height(48.dp).clickable { onRefresh() },
                )
            }
            TextButton(text = "换一张", onClick = onRefresh)
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().testTag("login_captcha"),
            label = "图形验证码",
            backgroundMode = TextFieldMode.Line,
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = code.isNotBlank() && !logging,
            modifier = Modifier.fillMaxWidth().testTag("sms_submit"),
        ) {
            Text(if (logging) "提交中…" else "继续登录")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = hint,
            fontSize = 12.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(text = "返回账号密码", onClick = onBack)
    }
}

@Composable
private fun WebAltVerifyView(
    url: String,
    modifier: Modifier,
    onBindReload: ((() -> Unit)?) -> Unit,
    onAlt: (String) -> Unit,
    onBrowserClose: () -> Unit,
    onWebCookie: (String, Map<String, String>) -> Unit,
) {
    val context = App.context
    val scope = rememberCoroutineScope()
    
    val handled = remember(url) { BooleanBox(false) }
    var webViewRef by remember { mutableStateOf<PlatformWebViewHandle?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val shareWebUa = remember(context) {
        val base = runCatching { defaultWebUserAgent() }
            .getOrDefault(Api.WEB_UA)
        "$base Weibo (${Api.clientUa()})"
    }
    val wbjsBody = remember {
        runCatching {
            (readBundledAssetText("wbjs.js") ?: "")
                .trim().removePrefix("!")
        }.getOrDefault("")
    }

    fun injectWbjs(wv: PlatformWebViewHandle) {
        if (wbjsBody.isBlank()) return
        val env = JSONObject()
            .put("aid", App.store.clientAid())
            .put("initTextPercent", 100)
        val initJs = "if(!window.WeiboJSBridge){($wbjsBody)($env);}"
        wv.evaluateJavaScript(initJs)
    }

    fun submitAltOnce(alt: String) {
        if (alt.isBlank() || !handled.compareAndSet(false, true)) return
        scope.launch { onAlt(alt) }
    }

    fun closeAndRetryOnce() {
        if (!handled.compareAndSet(false, true)) return
        scope.launch { onBrowserClose() }
    }

    fun submitWebCookieOnce(header: String, cookies: Map<String, String>) {
        if (header.isBlank() || !handled.compareAndSet(false, true)) return
        scope.launch { onWebCookie(header, cookies) }
    }

    fun replyBridge(
        wv: PlatformWebViewHandle,
        cbId: String,
        eventName: String = "",
        params: JSONObject = JSONObject(),
        ok: Boolean = true,
        status: Int = if (ok) 200 else 500,
    ) {
        if (cbId.isBlank()) return
        val msg = JSONObject()
            .put("__msg_type", "callback")
            .put("__callback_id", cbId)
            .put("__event_name", eventName)
            .put("status", status)
            .put("failed", !ok)
            .put("params", params)
        val script = "window.__WeiboJSBridge && window.__WeiboJSBridge._handleMessage($msg);"
        run { wv.evaluateJavaScript(script) }
    }

    lateinit var dispatchBridgeTransfer: (PlatformWebViewHandle, String) -> Unit

    fun handleVerifyRedirect(wv: PlatformWebViewHandle?, target: String): Boolean {
        return when (val action = parseVerifyRedirectAction(target)) {
            is VerifyRedirectAction.SubmitAlt -> {
                submitAltOnce(action.alt)
                true
            }
            is VerifyRedirectAction.CloseAndRetry -> {
                closeAndRetryOnce()
                true
            }
            is VerifyRedirectAction.LoadUrl -> {
                if (wv != null) run { wv.loadUrl(action.url) }
                true
            }
            is VerifyRedirectAction.BridgeInvoke -> {
                if (wv != null) {
                    run {
                        wv.evaluateJavaScript("window.__WeiboJSBridge && window.__WeiboJSBridge._messageQueue();")
                    }
                }
                true
            }
            is VerifyRedirectAction.BridgeTransfer -> {
                if (wv != null) {
                    run {
                        val wrapped = JSONObject()
                            .put("scene", action.scene)
                            .put("data", action.data)
                            .toString()
                        dispatchBridgeTransfer(wv, wrapped)
                    }
                }
                true
            }
            VerifyRedirectAction.Ignore -> false
        }
    }

    fun handleBridgeAction(wv: PlatformWebViewHandle, item: JSONObject) {
        val action = item.optString("action")
        val cbId = item.optString("__callback_id")
        when (action) {
            "getAid" -> replyBridge(wv, cbId, action, JSONObject().put("aid", App.store.clientAid()))
            "getWM" -> replyBridge(wv, cbId, action, JSONObject().put("wm", "2468_1001"))
            "getNetworkType" -> {
                val netType = when (Api.networkType()) {
                    "wifi" -> "wifi"
                    "none" -> "fail"
                    else -> "wwan"
                }
                replyBridge(wv, cbId, action, JSONObject().put("network_type", netType))
            }
            "CheckCanOpenUrl" -> replyBridge(wv, cbId, action, JSONObject().put("canOpenUrl", false))
            "getBrowserInfo" -> replyBridge(
                wv,
                cbId,
                action,
                JSONObject()
                    .put("clientVersion", "11.6.3")
                    .put("browserType", "normal")
                    .put("from", ClientLogin.FROM)
                    .put("wm", "2468_1001")
                    .put("oldwm", Api.oldWm())
                    .put("ua", Api.clientUa())
                    .put("c", "android")
                    .put("uid", "")
                    .put("lang", "zh_CN")
                    .put("networktype", Api.networkType()),
            )
            "loginWithALT" -> {
                val alt = extractAltFromBridgeItem(item).orEmpty()
                replyBridge(wv, cbId, action, ok = alt.isNotBlank(), status = if (alt.isNotBlank()) 200 else 400)
                if (alt.isNotBlank()) submitAltOnce(alt)
            }
            // 未登录态执行安全验证时，passport.weibo.cn 会探测 loginWeiboAccount；
            // 必须返回失败让页面回退到 &nojsbridge=1，否则页面会误以为已同步 Cookie 而停在空白页。
            "loginWeiboAccount" -> replyBridge(wv, cbId, action, ok = false, status = 500)
            "closeBrowser", "close", "closePlatformWebViewHandle", "dismiss" -> {
                val alt = extractAltFromBridgeItem(item).orEmpty()
                replyBridge(wv, cbId, action)
                if (alt.isNotBlank()) submitAltOnce(alt) else closeAndRetryOnce()
            }
            "openURL", "openBrowser", "openScheme", "openWindow" -> {
                val targetUrl = extractBridgeTargetUrl(item).orEmpty()
                replyBridge(wv, cbId, action)
                if (targetUrl.isNotBlank() && !handleVerifyRedirect(wv, targetUrl)) {
                    val resolved = extractWebViewFallbackUrl(targetUrl) ?: targetUrl
                    if (resolved.startsWith("http://", true) || resolved.startsWith("https://", true)) {
                        run { wv.loadUrl(resolved) }
                    }
                }
            }
            "setBrowserTitle",
            "setImmersiveStyle",
            "setBrowserGestureBackEnable",
            "setTopNavigationOptionItems",
            "privateChangeFont",
            "visibilityChange",
            -> replyBridge(wv, cbId, action)
            else -> replyBridge(wv, cbId, action, ok = false, status = 501)
        }
    }

    dispatchBridgeTransfer = { wv, raw ->
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json != null) {
            val scene = json.optString("scene")
            if (scene == "SCENE_MESSAGEQUEUE") {
                val items = runCatching { JSONArray(json.optString("data")) }.getOrNull()
                if (items != null) {
                    for (i in 0 until items.length()) {
                        items.optJSONObject(i)?.let { handleBridgeAction(wv, it) }
                    }
                }
            } else {
                extractAltFromBridgeItem(json)?.let { submitAltOnce(it) }
            }
        }
    }

    val bridge = remember(url) {
        object {
            fun invoke() {
                val wv = webViewRef ?: return
                run {
                    wv.evaluateJavaScript("window.__WeiboJSBridge && window.__WeiboJSBridge._messageQueue();")
                }
            }

            fun processHTML(html: String?, title: String?) = Unit

            fun transferData(raw: String) {
                val wv = webViewRef ?: return
                run { dispatchBridgeTransfer(wv, raw) }
            }
        }
    }

    Box(modifier = modifier) {
        WeiboWebView(
            url = url,
            modifier = Modifier.fillMaxSize(),
            useSessionCookie = false,
            clearCookies = false,
            userAgent = shareWebUa,
            onConfigure = { wv ->
                webViewRef = wv
                onBindReload {
                    loadError = null
                    wv.reload()
                }
                
            },
            onUrlLoading = { wv, target ->
                // 只拦截带明确动作的跳转（alt / close / bridge / 拆包）；普通 http(s) 页面放行。
                val action = parseVerifyRedirectAction(target)
                if (action != VerifyRedirectAction.Ignore) {
                    handleVerifyRedirect(wv, target)
                } else {
                    false
                }
            },
            onCustomScheme = { target ->
                handleVerifyRedirect(webViewRef, target)
            },
            onPageStarted = { _, _ ->
                loadError = null
            },
            onPageCommitVisible = { wv, committedUrl ->
                if (!handleVerifyRedirect(wv, committedUrl)) {
                    injectWbjs(wv)
                }
            },
            onProgressChanged = { wv, newProgress ->
                progress = newProgress
                if (newProgress >= 10) injectWbjs(wv)
            },
            onPageFinished = { finishedUrl ->
                val wv = webViewRef ?: return@WeiboWebView
                if (handleVerifyRedirect(wv, finishedUrl)) return@WeiboWebView
                injectWbjs(wv)
                if (isPostVerifyLandingUrl(finishedUrl)) {
                    val cookies = CookieVault.readDomains()
                    val header = CookieVault.authHeader(cookies, "m.weibo.cn")
                    if (header.contains("SUB=")) {
                        submitWebCookieOnce(header, cookies)
                    }
                }
            },
            onCloseWindow = {
                closeAndRetryOnce()
            },
            onReceivedError = { _, code, desc ->
                loadError = if (desc.isNotBlank()) "验证页面加载异常（$code）：$desc" else "验证页面加载异常（$code）"
            },
        )
        if (progress in 1..99) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(progress / 100f)
                    .height(2.dp)
                    .background(COUITheme.colorScheme.primary),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(COUITheme.colorScheme.background.copy(alpha = 0.94f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!loadError.isNullOrBlank()) {
                Text(
                    text = loadError.orEmpty(),
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "重新加载",
                    onClick = {
                        loadError = null
                        webViewRef?.reload()
                    },
                    modifier = Modifier.testTag("web_verify_retry_load"),
                )
                TextButton(
                    text = "已完成验证，继续登录",
                    onClick = { closeAndRetryOnce() },
                    modifier = Modifier.testTag("web_verify_continue"),
                )
            }
        }
    }
}

internal sealed interface VerifyRedirectAction {
    data class SubmitAlt(val alt: String) : VerifyRedirectAction
    data class LoadUrl(val url: String) : VerifyRedirectAction
    data object CloseAndRetry : VerifyRedirectAction
    data object BridgeInvoke : VerifyRedirectAction
    data class BridgeTransfer(val scene: String, val data: String) : VerifyRedirectAction
    data object Ignore : VerifyRedirectAction
}

internal fun parseVerifyRedirectAction(target: String): VerifyRedirectAction {
    val trimmed = target.trim()
    if (trimmed.isBlank()) return VerifyRedirectAction.Ignore
    val lower = trimmed.lowercase()
    if (lower.startsWith("sinaweibo://bridge_invoke_message")) {
        return VerifyRedirectAction.BridgeInvoke
    }
    if (lower.startsWith("sinaweibo://bridge_transfer_data/")) {
        val payload = trimmed.substring("sinaweibo://bridge_transfer_data/".length)
        val sep = payload.indexOf('&')
        if (sep > 0) {
            val scene = payload.substring(0, sep)
            val encoded = payload.substring(sep + 1)
            val decoded = runCatching {
                base64Decode(encoded).decodeToString()
            }.getOrDefault(encoded)
            return VerifyRedirectAction.BridgeTransfer(scene = scene, data = decoded)
        }
    }
    extractAltFromUrl(trimmed)?.let { return VerifyRedirectAction.SubmitAlt(it) }
    if (lower.startsWith("sinaweibo://browser/close") ||
        lower.startsWith("sinaweibo://close") ||
        lower.startsWith("sinaweibo://finish") ||
        (lower.startsWith("sinaweibo://") && urlParam(trimmed, "type").equals("close", ignoreCase = true))
    ) {
        return VerifyRedirectAction.CloseAndRetry
    }
    extractWebViewFallbackUrl(trimmed)?.let { inner ->
        extractAltFromUrl(inner)?.let { return VerifyRedirectAction.SubmitAlt(it) }
        return VerifyRedirectAction.LoadUrl(inner)
    }
    return VerifyRedirectAction.Ignore
}

internal fun extractAltFromUrl(raw: String, depth: Int = 0): String? {
    if (raw.isBlank() || depth > 3) return null
    val direct = urlParam(raw, "alt")
    if (!direct.isNullOrBlank()) return direct
    for (nestedKey in listOf("scheme", "url", "r", "backurl")) {
        val nested = urlParam(raw, nestedKey) ?: continue
        val found = extractAltFromUrl(nested, depth + 1)
        if (!found.isNullOrBlank()) return found
    }
    return null
}

internal fun extractAltFromBridgeItem(item: JSONObject): String? {
    item.optString("alt").takeIf { it.isNotBlank() }?.let { return it }
    val paramsObj = parseJsonObjectField(item, "params")
    if (paramsObj != null) {
        paramsObj.optString("alt").takeIf { it.isNotBlank() }?.let { return it }
        parseJsonObjectField(paramsObj, "data")?.optString("alt")?.takeIf { it.isNotBlank() }?.let { return it }
        for (key in listOf("url", "scheme")) {
            paramsObj.optString(key).takeIf { it.isNotBlank() }?.let { candidate ->
                extractAltFromUrl(candidate)?.let { return it }
            }
        }
    }
    parseJsonObjectField(item, "data")?.optString("alt")?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}

internal fun extractBridgeTargetUrl(item: JSONObject): String? {
    val paramsObj = parseJsonObjectField(item, "params")
    for (source in listOfNotNull(paramsObj, item)) {
        for (key in listOf("url", "scheme", "href")) {
            val value = source.optString(key).trim()
            if (value.isNotBlank()) return value
        }
    }
    return null
}

internal fun isPostVerifyLandingUrl(raw: String): Boolean {
    val host = runCatching { SmsParsedUri(raw.trim()).host?.lowercase() }.getOrNull().orEmpty()
    return host == "m.weibo.cn" || host == "weibo.com" || host == "www.weibo.com"
}

private fun parseJsonObjectField(obj: JSONObject, key: String): JSONObject? {
    val value = obj.opt(key) ?: return null
    return when (value) {
        is JSONObject -> value
        is String -> value.trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
        else -> null
    }
}

private fun urlParam(raw: String, name: String): String? {
    val sections = buildList {
        val qIdx = raw.indexOf('?')
        val hIdx = raw.indexOf('#')
        if (qIdx >= 0) {
            val queryEnd = if (hIdx > qIdx) hIdx else raw.length
            add(raw.substring(qIdx + 1, queryEnd))
        }
        if (hIdx >= 0 && hIdx + 1 < raw.length) {
            val frag = raw.substring(hIdx + 1)
            add(frag.substringAfter('?', frag))
        }
    }
    for (section in sections) {
        for (part in section.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            if (!part.substring(0, eq).equals(name, ignoreCase = true)) continue
            val encoded = part.substring(eq + 1)
            val decoded = runCatching { urlDecode(encoded) }.getOrDefault(encoded).trim()
            if (decoded.isNotBlank()) return decoded
        }
    }
    return null
}

private class BooleanBox(var value: Boolean) {
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (value != expect) return false
        value = update
        return true
    }
    fun get(): Boolean = value
}

private class SmsParsedUri(raw: String) {
    val host: String? = parseUrlHost(raw).ifBlank { null }
    val path: String? = parseUrlPath(raw).ifBlank { null }
    val rawQuery: String? = raw.substringAfter('?', "").substringBefore('#').ifBlank { null }
    val scheme: String? = raw.substringBefore("://", "").takeIf { it.isNotBlank() && raw.contains("://") }
}
