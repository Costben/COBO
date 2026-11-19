package com.cobo.app.screen

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.Api
import com.cobo.app.data.ClientLogin
import com.cobo.app.data.Repo
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.WeiboWebView
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    var useDirectSms by remember { mutableStateOf(false) }
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
            if (relogin != null) "登录「$relogin」，完成后会升级为可自动续签的长期会话"
            else "输入账号与密码登录；如触发登录保护，只需收一次短信验证码即可长期保活",
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

    BackHandler(enabled = stage != ClientAuthStage.Credentials) {
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
                        else -> "客户端登录"
                    },
                    navigationIcon = {
                        BackIconButton {
                            if (stage != ClientAuthStage.Credentials) stage = ClientAuthStage.Credentials
                            else nav.pop()
                        }
                    },
                )
            }
        },
    ) { padding ->
        when (val current = stage) {
            is ClientAuthStage.WebVerify -> {
                WebAltVerifyView(
                    url = current.url,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onAlt = { alt ->
                        stage = ClientAuthStage.Credentials
                        submitPassword(alt = alt)
                    },
                    onBrowserClose = {
                        stage = ClientAuthStage.Credentials
                        submitPassword()
                    },
                )
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
                    Text(
                        "登录后只保存长期凭证 gsid 与四域 Cookie，到期自动无感续签，不保存登录密码。",
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
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
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = if (useDirectSms) "账号密码登录" else "验证码登录",
                            onClick = {
                                useDirectSms = !useDirectSms
                                hint = if (useDirectSms) {
                                    "输入手机号，收到短信验证码后登录（如已开启登录保护建议用账号密码）"
                                } else {
                                    "输入账号与密码登录；如触发登录保护，只需收一次短信验证码即可长期保活"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("sms_toggle_mode"),
                        )
                        TextButton(
                            text = "网页登录",
                            onClick = {
                                val prev = nav.stack.dropLast(1).lastOrNull()?.route
                                if (prev is Route.Login) nav.pop()
                                else nav.push(Route.Login(addAccount = addAccount, fresh = addAccount, relogin = relogin))
                            },
                            modifier = Modifier.weight(1f).testTag("sms_to_web"),
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
    onAlt: (String) -> Unit,
    onBrowserClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var handled by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val shareWebUa = remember(context) {
        val base = runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }
            .getOrDefault(Api.WEB_UA)
        "$base Weibo (${Api.clientUa()})"
    }
    val wbjsBody = remember {
        runCatching {
            App.context.assets.open("wbjs.js").bufferedReader().use { it.readText() }
                .trim().removePrefix("!")
        }.getOrDefault("")
    }

    fun injectWbjs(wv: WebView) {
        if (wbjsBody.isBlank()) return
        val env = JSONObject()
            .put("aid", App.store.clientAid())
            .put("initTextPercent", 100)
        val initJs = "if(!window.WeiboJSBridge){($wbjsBody)($env);}"
        wv.evaluateJavascript(initJs, null)
    }

    fun submitAltOnce(alt: String) {
        if (alt.isBlank() || handled) return
        handled = true
        scope.launch { onAlt(alt) }
    }

    fun closeAndRetryOnce() {
        if (handled) return
        handled = true
        scope.launch { onBrowserClose() }
    }

    fun replyBridge(
        wv: WebView,
        cbId: String,
        eventName: String = "",
        params: JSONObject = JSONObject(),
        ok: Boolean = true,
    ) {
        if (cbId.isBlank()) return
        val msg = JSONObject()
            .put("__msg_type", "callback")
            .put("__callback_id", cbId)
            .put("__event_name", eventName)
            .put("status", if (ok) 200 else 500)
            .put("failed", !ok)
            .put("params", params)
        val script = "window.__WeiboJSBridge && window.__WeiboJSBridge._handleMessage($msg);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    fun handleBridgeAction(wv: WebView, item: JSONObject) {
        val action = item.optString("action")
        val cbId = item.optString("__callback_id")
        when (action) {
            "getAid" -> replyBridge(wv, cbId, action, JSONObject().put("aid", App.store.clientAid()))
            "getWM" -> replyBridge(wv, cbId, action, JSONObject().put("wm", "2468_1001"))
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
                val alt = item.optJSONObject("params")?.optString("alt").orEmpty()
                replyBridge(wv, cbId, action)
                submitAltOnce(alt)
            }
            else -> replyBridge(wv, cbId, action)
        }
    }

    val bridge = remember(onAlt) {
        object {
            @JavascriptInterface
            fun invoke() {
                val wv = webViewRef ?: return
                wv.post {
                    wv.evaluateJavascript("window.__WeiboJSBridge && window.__WeiboJSBridge._messageQueue();", null)
                }
            }

            @JavascriptInterface
            fun processHTML(html: String?, title: String?) = Unit

            @JavascriptInterface
            fun transferData(raw: String) {
                val wv = webViewRef ?: return
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
                val scene = json.optString("scene")
                if (scene == "SCENE_MESSAGEQUEUE") {
                    val items = runCatching { JSONArray(json.optString("data")) }.getOrNull() ?: return
                    for (i in 0 until items.length()) {
                        items.optJSONObject(i)?.let { handleBridgeAction(wv, it) }
                    }
                    return
                }
                val directAlt = json.optString("alt")
                    .ifBlank { json.optJSONObject("params")?.optString("alt").orEmpty() }
                    .ifBlank { json.optJSONObject("data")?.optString("alt").orEmpty() }
                submitAltOnce(directAlt)
            }
        }
    }

    fun handleVerifyRedirect(target: String): Boolean {
        val uri = runCatching { Uri.parse(target) }.getOrNull()
        val altParam = uri?.getQueryParameter("alt").orEmpty()
        if (altParam.isNotBlank()) {
            submitAltOnce(altParam)
            return true
        }
        if (target.startsWith("sinaweibo://browser/close", ignoreCase = true) ||
            (uri?.scheme.equals("sinaweibo", ignoreCase = true) && uri?.getQueryParameter("type") == "close")
        ) {
            val schemeParam = uri?.getQueryParameter("scheme").orEmpty()
            val nestedAlt = runCatching { Uri.parse(schemeParam).getQueryParameter("alt") }.getOrNull().orEmpty()
            if (nestedAlt.isNotBlank()) submitAltOnce(nestedAlt) else closeAndRetryOnce()
            return true
        }
        return false
    }

    WeiboWebView(
        url = url,
        modifier = modifier,
        useSessionCookie = false,
        clearCookies = false,
        userAgent = shareWebUa,
        onConfigure = { wv ->
            webViewRef = wv
            wv.addJavascriptInterface(bridge, "WeiboJSBridgeDataTransfer")
        },
        onCustomScheme = { target ->
            handleVerifyRedirect(target)
        },
        onProgressChanged = { wv, progress ->
            if (progress >= 30) injectWbjs(wv)
        },
        onPageFinished = { finishedUrl ->
            if (handleVerifyRedirect(finishedUrl)) return@WeiboWebView
            val wv = webViewRef ?: return@WeiboWebView
            injectWbjs(wv)
        },
    )
}
