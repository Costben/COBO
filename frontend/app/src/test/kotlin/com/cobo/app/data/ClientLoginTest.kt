package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class ClientLoginTest {

    @Test
    fun `解析 Share 返回的四域 cookie 表并过滤属性字段`() {
        val raw = JSONObject(
            """
            {
              "uid": "6067178131",
              "gsid": "_2A25H6H2gDeRxGeBO7VUQ9ybNyD2IHXVivPZorDV6PUJbkdAYLVilkWpNRciUayM",
              "sut": "_2A95H6H2gDeRxGeBO",
              "cookie": {
                "uid": "6067178131",
                "cookie": {
                  ".sina.com.cn": "SUB=sub_sina; expires=Saturday, 05-Dec-2026 05:02:40 GMT; path=/; domain=.sina.com.cn; secure; httponly\nSUBP=subp_sina; expires=Friday, 05-Nov-2027 05:03:52 GMT; path=/; domain=.sina.com.cn; secure",
                  ".sina.cn": "SUB=sub_sinacn; path=/; domain=.sina.cn",
                  ".weibo.com": "SUB=sub_com; path=/; domain=.weibo.com\nSUBP=subp_com; path=/; domain=.weibo.com",
                  ".weibo.cn": "SUB=sub_cn; expires=Saturday, 05-Dec-2026 05:02:40 GMT; path=/; domain=.weibo.cn; httponly\nSUBP=subp_cn; expires=Friday, 05-Nov-2027 05:03:52 GMT; path=/; domain=.weibo.cn"
                }
              },
              "oauth2.0": {
                "access_token": "2.00nLQbc",
                "expires": 2202935
              }
            }
            """.trimIndent()
        )

        val result = ClientLogin.parse(raw)
        assertNotNull(result)
        result!!
        assertEquals("6067178131", result.uid)
        assertTrue(result.gsid.startsWith("_2A25"))
        assertEquals("2.00nLQbc", result.accessToken)
        assertEquals(2202935L, result.expires)
        assertEquals(4, result.cookies.size)
        assertEquals("SUB=sub_cn\nSUBP=subp_cn", result.cookies[".weibo.cn"])
        assertEquals("SUB=sub_com\nSUBP=subp_com", result.cookies[".weibo.com"])
        assertEquals("SUB=sub_sina\nSUBP=subp_sina", result.cookies[".sina.com.cn"])
        assertEquals("SUB=sub_cn; SUBP=subp_cn", result.cookie)
    }

    @Test
    fun `splitPairs 兼容旧式分号拼接并忽略 cookie 属性`() {
        val pairs = ClientLogin.splitPairs(
            "SUB=abc; expires=Sat, 05 Dec 2026; path=/; domain=.weibo.cn; secure; httponly\nSUBP=def; max-age=31536000"
        )
        assertEquals(listOf("SUB=abc", "SUBP=def"), pairs)
        assertEquals(listOf("SUB=abc", "SUBP=def"), ClientLogin.splitPairs("SUB=abc; SUBP=def"))
    }

    @Test
    fun `encryptPassword 按 Share 公钥分块 RSA 加密输出 Base64`() {
        val cipherB64 = ClientLogin.encryptPassword("p@ssw0rd123!")
        val raw = Base64.getDecoder().decode(cipherB64)
        assertEquals(128, raw.size)
        assertTrue(cipherB64.isNotBlank())
    }

    @Test
    fun `parseResponse 解出 -1007 登录保护短信验证与去重手机号列表`() {
        val raw = JSONObject(
            """
            {
              "errno": -1007,
              "errmsg": "需要短信验证",
              "retcode": "rt_abc123",
              "phone": "13800138000",
              "code": "86",
              "phone_list": [
                {"phone": "13800138000", "code": "86"},
                {"phone": "13900139001", "code": "86"}
              ]
            }
            """.trimIndent()
        )
        val parsed = ClientLogin.parseResponse(raw) as ClientLogin.ParsedResponse.Challenge
        val safety = parsed.outcome as ClientLogin.Outcome.SafetyVerify
        assertEquals("rt_abc123", safety.challenge.retcode)
        assertEquals(2, safety.challenge.phones.size)
        assertEquals("13800138000", safety.challenge.phones[0].phone)
        assertEquals("86", safety.challenge.phones[0].code)
        assertEquals("13900139001", safety.challenge.phones[1].phone)
        assertEquals("138*****000", ClientLogin.maskPhone("13800138000"))
    }

    @Test
    fun `parseResponse 解出 -1005 图形验证码与 errurl 网页验证`() {
        val captchaJson = JSONObject(
            """
            {
              "errno": -1005,
              "errmsg": "请输入验证码",
              "annotations": {
                "pic": "https://example.com/cpt.png",
                "cpt": "cpt_token",
                "entry": "client"
              }
            }
            """.trimIndent()
        )
        val captchaParsed = ClientLogin.parseResponse(captchaJson) as ClientLogin.ParsedResponse.Challenge
        val captcha = captchaParsed.outcome as ClientLogin.Outcome.Captcha
        assertEquals("https://example.com/cpt.png", captcha.challenge.picUrl)
        assertEquals("cpt_token", captcha.challenge.cpt)

        val urlJson = JSONObject(
            """
            {
              "errno": -200,
              "errmsg": "请完成安全验证",
              "errurl": "https://passport.weibo.cn/verify?id=1"
            }
            """.trimIndent()
        )
        val urlParsed = ClientLogin.parseResponse(urlJson) as ClientLogin.ParsedResponse.Challenge
        val web = urlParsed.outcome as ClientLogin.Outcome.WebVerify
        assertEquals("https://passport.weibo.cn/verify?id=1", web.url)

        val errJson = JSONObject("""{"errno": -100, "errmsg": "用户名或密码错误"}""")
        try {
            ClientLogin.parseResponse(errJson)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("-100", e.errno)
            assertEquals("用户名或密码错误", e.message)
        }
    }
}
