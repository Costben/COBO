package com.cobo.app.e2e

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cobo.app.App
import com.cobo.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before

/**
 * Base class for tests that talk to the live m.weibo.cn API with the session that is
 * already stored on the device. These tests never write to the account.
 *
 * The activity is kept in the foreground while requests run: some OEM ROMs throttle
 * network access for processes without a visible activity, which turns a two-second
 * API call into a read timeout.
 */
abstract class DataE2eTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext()

    protected val cookie: String
        get() = App.repo.cookie().orEmpty()

    @Before
    fun requireSession() {
        App.init(context)
        assumeTrue("设备上没有登录会话（session.json），跳过数据测试", App.repo.hasSession())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() {
        runCatching { scenario?.onActivity { it.finish() } }
        scenario = null
    }

    protected fun <T> io(block: suspend () -> T): T = runBlocking { block() }

    /** Read-only call with one retry for flaky mobile connections. */
    protected fun <T> net(block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                return io(block)
            } catch (t: Throwable) {
                last = t
                if (attempt == 0) Thread.sleep(800)
            }
        }
        throw last ?: IllegalStateException("网络请求失败")
    }
}
