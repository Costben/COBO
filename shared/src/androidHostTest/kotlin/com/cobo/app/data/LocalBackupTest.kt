package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalBackupTest {

    @Test
    fun encodeAndDecodeRoundTripsAllSectionsWithoutCredentials() {
        val status = Status(
            id = "5001234567",
            mid = "5001234567",
            textHtml = "收藏一条测试微博",
            createdAt = "Thu Nov 26 10:00:00 +0800 2026",
            source = "COBO",
            user = User(id = "1001", name = "Tester", avatar = "https://example.com/a.jpg"),
            favorited = true,
        )
        val settings = SettingsBackup(
            darkMode = 2,
            fontScale = 1.15f,
            accentColor = 0xFF1A73E8.toInt(),
            meIconStyleKey = MeIconStyle.Accent.key,
            blurEffects = false,
            allowLandscape = false,
            animationSpeedKey = AnimationSpeed.Fast.key,
            searchPanelAnimationKey = SearchPanelAnimation.Vertical.key,
            predictiveBack = true,
            predictiveBackCardFling = true,
            predictiveBackImageFling = true,
            predictiveBackBlur = true,
            autoHideFloatingBars = true,
            feedTabTapToTopAndReturn = true,
            bottomLeftActionKey = BottomLeftAction.Favorites.key,
            feedLeftTabKey = FeedTabItem.Recommend.key,
            feedCenterTabKey = FeedTabItem.Follow.key,
            feedRightTabKey = FeedTabItem.None.key,
            collapseFollowGroups = false,
            hotBoardKeys = listOf(HotBoard.Realtime.filterType, HotBoard.Life.filterType),
            reverseBrowse = true,
            feedCardFrame = false,
            detailCardFrame = false,
            foldLongText = false,
            showVipLevel = false,
            showFanLevel = false,
            showVerifiedBadge = false,
            confirmLike = true,
            inAppBrowser = false,
            quickSwitchAccount = false,
            profileTabAvatar = false,
            profileShowStatuses = true,
            imageBrowseQualityKey = ImageBrowseQuality.Original.key,
            livePhotoAutoPlay = false,
            livePhotoSound = false,
            videoAutoFloatOnBack = true,
            videoAutoCloseOnComplete = false,
            videoCenterSwipeActions = false,
            localMode = true,
            localFollowUid = "1001",
            nearbyCustom = true,
            nearbyPlaceName = "成都",
            nearbyPlaceLat = 30.67f,
            nearbyPlaceLon = 104.06f,
            autoCheckUpdate = false,
            acceptPrerelease = false,
            inAppUpdate = true,
        )
        val bundle = LocalBackupBundle(
            version = LocalBackupSchemaVersion,
            exportedAt = 1_795_660_000_000L,
            appVersion = "1.2.9-pre",
            settings = settings,
            searchHistory = listOf("Android", "Compose"),
            drafts = listOf(Draft("草稿内容", 1_795_650_000_000L)),
            history = listOf(HistoryItem("5001234567", "5001234567", "测试历史", "Tester", 1_795_655_000_000L, "<p>测试历史</p>")),
            favorites = listOf(status),
            followTags = mapOf("1001" to FollowTag.Special, "1002" to FollowTag.Local),
        )

        val json = encodeLocalBackupJson(bundle)
        val root = JSONObject(json)
        assertEquals(LocalBackupFormatId, root.getString("format"))
        listOf("cookie", "cookies", "gsid", "accessToken", "validator", "accounts", "session").forEach { forbidden ->
            assertFalse("Backup JSON must not expose $forbidden", root.has(forbidden))
            assertFalse("Backup settings must not expose $forbidden", root.getJSONObject("settings").has(forbidden))
        }

        val decoded = decodeLocalBackupJson(json)
        assertEquals(bundle.exportedAt, decoded.exportedAt)
        assertEquals(bundle.appVersion, decoded.appVersion)
        assertEquals(settings, decoded.settings)
        assertEquals(bundle.searchHistory, decoded.searchHistory)
        assertEquals(bundle.drafts, decoded.drafts)
        assertEquals(bundle.history, decoded.history)
        assertEquals(1, decoded.favorites?.size)
        assertEquals("5001234567", decoded.favorites?.first()?.id)
        assertTrue(decoded.favorites?.first()?.favorited == true)
        assertEquals(bundle.followTags, decoded.followTags)

        val summaryText = formatBackupSummary(decoded.summary())
        assertTrue(summaryText.contains("偏好设置"))
        assertTrue(summaryText.contains("1 条收藏"))
        assertFalse(summaryText.contains('\u00B7'))
    }

    @Test
    fun decodePreservesEmptyHotBoardsAndPartialSettings() {
        val raw = """
            {
              "format": "$LocalBackupFormatId",
              "version": 1,
              "exportedAt": 1000,
              "appVersion": "1.2.9-pre",
              "settings": {
                "darkMode": 2,
                "hotBoardKeys": []
              }
            }
        """.trimIndent()
        val decoded = decodeLocalBackupJson(raw)
        assertEquals(2, decoded.settings?.darkMode)
        assertEquals(emptyList<String>(), decoded.settings?.hotBoardKeys)
        assertNull(decoded.settings?.blurEffects)
        assertNull(decoded.favorites)
    }

    @Test
    fun decodeRejectsInvalidJsonWrongFormatOrUnsupportedVersion() {
        for (bad in listOf(
            "not json",
            """{"format": "other-app", "version": 1}""",
            """{"format": "$LocalBackupFormatId", "version": 99}""",
            """{"format": "$LocalBackupFormatId", "version": 0}""",
        )) {
            try {
                decodeLocalBackupJson(bad)
                fail("Expected IllegalArgumentException for: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun mergeHelpersDeduplicateAndRespectLimits() {
        val mergedSearch = mergeBackupSearchHistory(
            existing = listOf("Compose", "Kotlin"),
            incoming = listOf("Kotlin", "COBO", "Android"),
            limit = 3,
        )
        assertEquals(listOf("Compose", "Kotlin", "COBO"), mergedSearch)

        val mergedDrafts = mergeBackupDrafts(
            existing = listOf(Draft("同一条草稿", 100L), Draft("本地草稿", 200L)),
            incoming = listOf(Draft("同一条草稿", 300L), Draft("导入草稿", 150L)),
            limit = 50,
        )
        assertEquals(
            listOf(Draft("同一条草稿", 300L), Draft("本地草稿", 200L), Draft("导入草稿", 150L)),
            mergedDrafts,
        )

        val mergedHistory = mergeBackupHistory(
            existing = listOf(HistoryItem("1", "1", "old", "u", 100L)),
            incoming = listOf(HistoryItem("1", "1", "newer", "u", 500L), HistoryItem("2", "2", "second", "u", 300L)),
            limit = 100,
        )
        assertEquals(listOf("1", "2"), mergedHistory.map { it.id })
        assertEquals("newer", mergedHistory.first().text)

        val favA = Status(id = "10", mid = "10", textHtml = "a", createdAt = "", source = "", user = null, favorited = false)
        val favB = Status(id = "20", mid = "20", textHtml = "b", createdAt = "", source = "", user = null, favorited = true)
        val mergedFavs = mergeBackupFavorites(existing = listOf(favA), incoming = listOf(favA, favB), limit = 500)
        assertEquals(listOf("10", "20"), mergedFavs.map { it.id })
        assertTrue(mergedFavs.all { it.favorited })

        val mergedTags = mergeBackupFollowTags(
            existing = mapOf("1" to FollowTag.Local, "2" to FollowTag.Special),
            incoming = mapOf("2" to FollowTag.Secret, "3" to FollowTag.Local),
        )
        assertEquals(
            mapOf("1" to FollowTag.Local, "2" to FollowTag.Secret, "3" to FollowTag.Local),
            mergedTags,
        )
    }

    @Test
    fun defaultBackupFileNameUsesExpectedPattern() {
        val name = defaultBackupFileName(1_795_660_000_000L)
        assertTrue("Unexpected backup filename: $name", name.matches(Regex("""cobo-backup-\d{8}-\d{4}\.json""")))
    }
}
