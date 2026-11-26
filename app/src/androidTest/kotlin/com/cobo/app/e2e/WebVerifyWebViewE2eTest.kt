package com.cobo.app.e2e

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.ClientLogin
import com.cobo.app.screen.VerifyRedirectAction
import com.cobo.app.screen.extractAltFromBridgeItem
import com.cobo.app.screen.extractBridgeTargetUrl
import com.cobo.app.screen.parseVerifyRedirectAction
import com.cobo.app.ui.components.cleanMobileChromeUserAgent
import com.cobo.app.ui.components.extractWebViewFallbackUrl
import com.cobo.app.ui.components.isStandardWebScheme
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 真机 Chromium WebView 实测：
 * 验证 wbjs.js 注入、loginWeiboAccount 未登录回退 &nojsbridge=1、loginWithALT、
 * sinaweibo://browser?url= 拆包、window.open 同窗加载与 window.close 回调。
 */
@RunWith(AndroidJUnit4::class)
class WebVerifyWebViewE2eTest {

    private lateinit var server: ServerSocket
    private var port: Int = 0
    private val running = AtomicBoolean(true)
    private lateinit var serverThread: Thread
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    @Volatile
    private var routes: Map<String, String> = emptyMap()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.cobo.app.platform.AndroidPlatformHolder.initApplication(ctx)
        App.init(com.cobo.app.platform.PlatformContext())
        server = ServerSocket(0)
        port = server.localPort
        serverThread = Thread {
            while (running.get()) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    sock.use { s ->
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        val path = requestLine.split(" ").getOrNull(1).orEmpty()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = routes[path] ?: routes[path.substringBefore('?')] ?: "<html><body>404:$path</body></html>"
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val out = s.getOutputStream()
                        out.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${bytes.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8),
                        )
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    @After
    fun tearDown() {
        running.set(false)
        runCatching { server.close() }
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    @Test
    fun realChromiumWebView_handlesWeiboBridgeAndRedirectsWithoutBlankScreen() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val wbjsBody = ctx.assets.open("wbjs.js").bufferedReader().use { it.readText() }
            .trim().removePrefix("!")

        val step2Url = "http://127.0.0.1:$port/step2"
        val step2Encoded = URLEncoder.encode(step2Url, "UTF-8")
        val popupUrl = "http://127.0.0.1:$port/popup_done"

        routes = mapOf(
            // 1. 复刻实测抓到的 passport.weibo.cn 空 body + loginWeiboAccount 页面
            "/passport_entry?entry=wapsso" to """
                <!doctype html><html><body><script>
                function bridgeReady() {
                    WeiboJSBridge.invoke("loginWeiboAccount", {"needForceUpdate": true}, function(params, success, code) {
                        if (success) {
                            document.title = "STUCK_BLANK";
                        } else {
                            window.location.replace(window.location.href + "&nojsbridge=1");
                        }
                    });
                }
                if (window.WeiboJSBridge) { bridgeReady(); }
                else { document.addEventListener("WeiboJSBridgeReady", bridgeReady); }
                </script></body></html>
            """.trimIndent(),
            // 2. &nojsbridge=1 后跳 sinaweibo://browser?url=...（验证拆包继续加载）
            "/passport_entry?entry=wapsso&nojsbridge=1" to """
                <!doctype html><html><body><script>
                window.location.href = "sinaweibo://browser?url=$step2Encoded";
                </script></body></html>
            """.trimIndent(),
            // 3. step2 通过 window.open 打开下一步页面（验证 onCreateWindow 同窗接管）
            "/step2" to """
                <!doctype html><html><body><script>
                window.open("$popupUrl", "_blank");
                </script></body></html>
            """.trimIndent(),
            // 4. popup_done 通过 WeiboJSBridge.invoke("loginWithALT") 回传 alt
            "/popup_done" to """
                <!doctype html><html><body><script>
                function sendAlt() {
                    WeiboJSBridge.invoke("loginWithALT", {"alt": "REAL_CHROMIUM_ALT_OK"}, function(){});
                }
                if (window.WeiboJSBridge) { sendAlt(); }
                else { document.addEventListener("WeiboJSBridgeReady", sendAlt); }
                </script></body></html>
            """.trimIndent(),
        )

        val receivedAlt = AtomicReference<String>("")
        val altLatch = CountDownLatch(1)
        val visited = mutableListOf<String>()
        val trace = mutableListOf<String>()
        fun log(msg: String) {
            synchronized(trace) { trace.add(msg) }
        }

        mainHandler.post {
            val wv = WebView(ctx)
            webView = wv
            val defaultUa = WebSettings.getDefaultUserAgent(ctx)
            val cleaned = cleanMobileChromeUserAgent(defaultUa)
            assertFalse("clean UA should strip wv: $cleaned", cleaned.contains("; wv"))

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.javaScriptCanOpenWindowsAutomatically = true
            wv.settings.setSupportMultipleWindows(true)
            wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            wv.settings.userAgentString = "$defaultUa Weibo (${Api.clientUa()})"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            fun injectWbjs(view: WebView) {
                val env = JSONObject().put("aid", "test_aid").put("initTextPercent", 100)
                view.evaluateJavascript("if(!window.WeiboJSBridge){($wbjsBody)($env);} ({bridge:!!window.WeiboJSBridge, dt:typeof window.WeiboJSBridgeDataTransfer, inv:typeof (window.WeiboJSBridgeDataTransfer&&window.WeiboJSBridgeDataTransfer.invoke), td:typeof (window.WeiboJSBridgeDataTransfer&&window.WeiboJSBridgeDataTransfer.transferData)})") { res ->
                    log("injectRes=$res")
                }
            }

            fun replyBridge(view: WebView, cbId: String, eventName: String, params: JSONObject = JSONObject(), ok: Boolean = true, status: Int = if (ok) 200 else 500) {
                if (cbId.isBlank()) return
                val msg = JSONObject()
                    .put("__msg_type", "callback")
                    .put("__callback_id", cbId)
                    .put("__event_name", eventName)
                    .put("status", status)
                    .put("failed", !ok)
                    .put("params", params)
                log("reply:$eventName:$cbId:ok=$ok")
                mainHandler.post { view.evaluateJavascript("window.__WeiboJSBridge && window.__WeiboJSBridge._handleMessage($msg);", null) }
            }

            fun handleAction(view: WebView, item: JSONObject) {
                val action = item.optString("action")
                val cbId = item.optString("__callback_id")
                log("action:$action:$cbId")
                when (action) {
                    "loginWeiboAccount" -> replyBridge(view, cbId, action, ok = false, status = 500)
                    "loginWithALT" -> {
                        val alt = extractAltFromBridgeItem(item).orEmpty()
                        replyBridge(view, cbId, action, ok = alt.isNotBlank(), status = if (alt.isNotBlank()) 200 else 400)
                        if (alt.isNotBlank()) {
                            receivedAlt.set(alt)
                            altLatch.countDown()
                        }
                    }
                    "getBrowserInfo" -> replyBridge(view, cbId, action, JSONObject().put("from", ClientLogin.FROM))
                    "openURL" -> {
                        val target = extractBridgeTargetUrl(item).orEmpty()
                        replyBridge(view, cbId, action)
                        if (target.isNotBlank()) view.loadUrl(target)
                    }
                    else -> replyBridge(view, cbId, action, ok = false, status = 501)
                }
            }

            val bridge = object {
                @JavascriptInterface
                fun invoke() {
                    log("native.invoke()")
                    mainHandler.post { wv.evaluateJavascript("window.__WeiboJSBridge && window.__WeiboJSBridge._messageQueue();", null) }
                }

                @JavascriptInterface
                fun processHTML(html: String?, title: String?) = Unit

                @JavascriptInterface
                fun transferData(raw: String) {
                    log("native.transferData:${raw.take(160)}")
                    mainHandler.post {
                        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@post
                        if (json.optString("scene") == "SCENE_MESSAGEQUEUE") {
                            val items = runCatching { JSONArray(json.optString("data")) }.getOrNull() ?: return@post
                            for (i in 0 until items.length()) {
                                items.optJSONObject(i)?.let { handleAction(wv, it) }
                            }
                        }
                    }
                }
            }
            wv.addJavascriptInterface(bridge, "WeiboJSBridgeDataTransfer")

            wv.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(cm: android.webkit.ConsoleMessage): Boolean {
                    log("console:${cm.messageLevel()}:${cm.message()}")
                    return false
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    log("progress:$newProgress")
                    if (newProgress >= 10) injectWbjs(view)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?,
                ): Boolean {
                    log("onCreateWindow")
                    val parent = view ?: return false
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val popup = WebView(parent.context)
                    var routed = false
                    fun routeOnce(target: String) {
                        if (routed || target.isBlank() || target.equals("about:blank", true)) return
                        routed = true
                        log("popupRoute:$target")
                        popup.stopLoading()
                        parent.loadUrl(target)
                        popup.post { popup.destroy() }
                    }
                    popup.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(p: WebView, req: android.webkit.WebResourceRequest): Boolean {
                            val target = req.url?.toString().orEmpty()
                            if (target.isBlank() || target.equals("about:blank", true)) return false
                            routeOnce(target)
                            return true
                        }

                        override fun onPageStarted(p: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            routeOnce(url.orEmpty())
                        }
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }

            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    val target = request.url?.toString().orEmpty()
                    synchronized(visited) { visited.add(target) }
                    log("override:$target")
                    when (val action = parseVerifyRedirectAction(target)) {
                        is VerifyRedirectAction.SubmitAlt -> {
                            receivedAlt.set(action.alt)
                            altLatch.countDown()
                            return true
                        }
                        is VerifyRedirectAction.LoadUrl -> {
                            view.loadUrl(action.url)
                            return true
                        }
                        else -> Unit
                    }
                    if (isStandardWebScheme(target)) return false
                    extractWebViewFallbackUrl(target)?.let { view.loadUrl(it) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    synchronized(visited) { visited.add("finished:${url.orEmpty()}") }
                    log("finished:${url.orEmpty()}")
                    injectWbjs(view)
                }
            }

            wv.loadUrl("http://127.0.0.1:$port/passport_entry?entry=wapsso")
        }

        val ok = altLatch.await(15, TimeUnit.SECONDS)
        assertTrue("未收到 loginWithALT 回调，visited=$visited, trace=$trace", ok)
        assertEquals("REAL_CHROMIUM_ALT_OK", receivedAlt.get())
    }
}
