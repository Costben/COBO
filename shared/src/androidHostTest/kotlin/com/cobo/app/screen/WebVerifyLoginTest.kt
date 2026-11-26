package com.cobo.app.screen

import com.cobo.app.ui.components.cleanMobileChromeUserAgent
import com.cobo.app.ui.components.extractWebViewFallbackUrl
import com.cobo.app.ui.components.isStandardWebScheme
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WebVerifyLoginTest {

    @Test
    fun `extractWebViewFallbackUrl unwraps sinaweibo browser and intent fallback urls`() {
        assertEquals(
            "https://security.weibo.com/captcha/step2?id=42",
            extractWebViewFallbackUrl("sinaweibo://browser?url=https%3A%2F%2Fsecurity.weibo.com%2Fcaptcha%2Fstep2%3Fid%3D42"),
        )
        assertEquals(
            "https://passport.weibo.cn/signin/secondverify/index?id=1",
            extractWebViewFallbackUrl("sinaweibo://slidebrowser?url=https%3A%2F%2Fpassport.weibo.cn%2Fsignin%2Fsecondverify%2Findex%3Fid%3D1"),
        )
        assertEquals(
            "https://m.weibo.cn/security/check",
            extractWebViewFallbackUrl("intent://open#Intent;S.browser_fallback_url=https%3A%2F%2Fm.weibo.cn%2Fsecurity%2Fcheck;end"),
        )
        assertNull(extractWebViewFallbackUrl("sinaweibo://browser/close"))
    }

    @Test
    fun `isStandardWebScheme allows subframe utility schemes and blocks custom app schemes`() {
        assertTrue(isStandardWebScheme("https://passport.weibo.com/sso/signin"))
        assertTrue(isStandardWebScheme("http://h5.sinaimg.cn/test.js"))
        assertTrue(isStandardWebScheme("about:blank"))
        assertTrue(isStandardWebScheme("javascript:void(0)"))
        assertTrue(isStandardWebScheme("blob:https://passport.weibo.com/123"))
        assertTrue(isStandardWebScheme("data:text/html,ok"))
        assertFalse(isStandardWebScheme("sinaweibo://browser?url=https%3A%2F%2Fm.weibo.cn"))
        assertFalse(isStandardWebScheme("intent://foo"))
    }

    @Test
    fun `cleanMobileChromeUserAgent strips wv and Version markers`() {
        val raw = "Mozilla/5.0 (Linux; Android 16; 24129PN74C Build/BP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 16; 24129PN74C Build/BP2A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36",
            cleanMobileChromeUserAgent(raw),
        )
    }

    @Test
    fun `parseVerifyRedirectAction handles alt close browser unwrap and bridge iframe fallback`() {
        assertEquals(
            VerifyRedirectAction.SubmitAlt("ALT_DIRECT_1"),
            parseVerifyRedirectAction("https://passport.weibo.cn/callback?alt=ALT_DIRECT_1&from=1"),
        )
        assertEquals(
            VerifyRedirectAction.SubmitAlt("ALT_NESTED_2"),
            parseVerifyRedirectAction("sinaweibo://browser/close?scheme=sinaweibo%3A%2F%2Flogin%3Falt%3DALT_NESTED_2"),
        )
        assertEquals(
            VerifyRedirectAction.SubmitAlt("ALT_FRAG_3"),
            parseVerifyRedirectAction("https://passport.weibo.cn/done#state=ok&alt=ALT_FRAG_3"),
        )
        assertEquals(
            VerifyRedirectAction.CloseAndRetry,
            parseVerifyRedirectAction("sinaweibo://browser/close"),
        )
        assertEquals(
            VerifyRedirectAction.CloseAndRetry,
            parseVerifyRedirectAction("sinaweibo://verify?type=close"),
        )
        assertEquals(
            VerifyRedirectAction.LoadUrl("https://passport.weibo.cn/signin/secondverify/sms?k=1"),
            parseVerifyRedirectAction("sinaweibo://browser?url=https%3A%2F%2Fpassport.weibo.cn%2Fsignin%2Fsecondverify%2Fsms%3Fk%3D1"),
        )
        assertEquals(
            VerifyRedirectAction.BridgeInvoke,
            parseVerifyRedirectAction("sinaweibo://bridge_invoke_message"),
        )
        val payload = """[{"action":"loginWithALT","params":{"alt":"ALT_B64"}}]"""
        val b64 = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        assertEquals(
            VerifyRedirectAction.BridgeTransfer("SCENE_MESSAGEQUEUE", payload),
            parseVerifyRedirectAction("sinaweibo://bridge_transfer_data/SCENE_MESSAGEQUEUE&$b64"),
        )
    }

    @Test
    fun `extractAltFromBridgeItem supports object and stringified params`() {
        val objParams = JSONObject("""{"action":"loginWithALT","params":{"alt":"ALT_OBJ"}}""")
        assertEquals("ALT_OBJ", extractAltFromBridgeItem(objParams))

        val strParams = JSONObject("""{"action":"loginWithALT","params":"{\"alt\":\"ALT_STR\"}"}""")
        assertEquals("ALT_STR", extractAltFromBridgeItem(strParams))

        val nestedUrl = JSONObject("""{"action":"openURL","params":{"url":"sinaweibo://login?alt=ALT_URL"}}""")
        assertEquals("ALT_URL", extractAltFromBridgeItem(nestedUrl))
    }

    @Test
    fun `isPostVerifyLandingUrl matches main weibo hosts only`() {
        assertTrue(isPostVerifyLandingUrl("https://m.weibo.cn/"))
        assertTrue(isPostVerifyLandingUrl("https://weibo.com/u/12345"))
        assertFalse(isPostVerifyLandingUrl("https://passport.weibo.cn/signin/login"))
        assertFalse(isPostVerifyLandingUrl("https://passport.weibo.com/sso/signin"))
        assertFalse(isPostVerifyLandingUrl("https://security.weibo.com/captcha/yidun"))
    }
}
