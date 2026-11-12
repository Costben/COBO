package com.cobo.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 续看快照的落盘行为：同一个账号读得回来，别的账号读不到，退出登录要真的删干净。
 *
 * `clear()` 曾经对「账号子目录」调 `File.delete()`，非空目录直接返回 false，于是退出登录
 * 只删掉空气：换个人进来还能读到上一个人的时间线。这里把这条钉住。
 */
class FeedSnapshotStoreTest {

    private lateinit var dir: File
    private lateinit var store: FeedSnapshotStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("feed-snapshots").toFile()
        store = FeedSnapshotStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun saveThenLoadReturnsTheSameFeed() {
        store.save(Owner, "friends", listOf(status("5000000000000001")), sinceId = "4999", end = false)

        val snapshot = store.load(Owner, "friends")

        assertEquals(listOf("5000000000000001"), snapshot?.items?.map { it.id })
        assertEquals("4999", snapshot?.sinceId)
        assertEquals(false, snapshot?.end)
    }

    @Test
    fun snapshotOfAnotherAccountIsNotVisible() {
        store.save("1000000001", "friends", listOf(status("1")), sinceId = null, end = false)

        assertNull(store.load("1000000002", "friends"))
        assertTrue(store.load("1000000001", "friends")?.items?.isNotEmpty() == true)
    }

    @Test
    fun clearRemovesEveryAccountDirectory() {
        store.save("1000000001", "friends", listOf(status("1")), sinceId = null, end = false)
        store.save("1000000001", "group_5012345", listOf(status("2")), sinceId = null, end = false)
        store.save("1000000002", "friends", listOf(status("3")), sinceId = null, end = false)

        store.clear()

        assertNull(store.load("1000000001", "friends"))
        assertNull(store.load("1000000001", "group_5012345"))
        assertNull(store.load("1000000002", "friends"))
        // load() 会顺手把账号目录建回来，所以这里看的是「一个快照文件都不剩」。
        assertTrue(dir.walkTopDown().none { it.isFile })
    }

    @Test
    fun expiredSnapshotIsIgnored() {
        val ownerDir = File(dir, Owner).apply { mkdirs() }
        val savedAt = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000
        File(ownerDir, "friends.json").writeText(
            """{"v":1,"savedAt":$savedAt,"sinceId":null,"end":false,""" +
                """"items":[{"id":"1","text":"一周前的微博"}]}""",
        )

        assertNull(store.load(Owner, "friends"))
    }

    @Test
    fun saveKeepsAtMostOneFilePerStreamAndAccount() {
        store.save(Owner, "friends", listOf(status("1")), sinceId = null, end = false)
        store.save(Owner, "friends", listOf(status("1"), status("2")), sinceId = null, end = true)

        val files = File(dir, Owner).listFiles().orEmpty()
        assertEquals(1, files.size)
        assertEquals(2, store.load(Owner, "friends")?.items?.size)
        assertEquals(true, store.load(Owner, "friends")?.end)
    }

    private fun status(id: String) = Status(
        id = id,
        mid = id,
        textHtml = "第 $id 条",
        createdAt = "",
        source = "",
        user = User(id = "1234567", name = "小明", avatar = ""),
    )

    private companion object {
        const val Owner = "5123456789"
    }
}
