package com.cobo.app.data

import com.cobo.app.screen.FeedFilter
import com.cobo.app.ui.FeedTabSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTabCustomizationTest {

    @Test
    fun fromKey_resolvesKnownKeys() {
        assertEquals(FeedTabItem.None, FeedTabItem.fromKey("none", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Follow, FeedTabItem.fromKey("follow", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Circle, FeedTabItem.fromKey("circle", FeedTabItem.Recommend))
        assertEquals(FeedTabItem.Recommend, FeedTabItem.fromKey("recommend", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Local, FeedTabItem.fromKey("local", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Original, FeedTabItem.fromKey("original", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Special, FeedTabItem.fromKey("special", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Secret, FeedTabItem.fromKey("secret", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Favorites, FeedTabItem.fromKey("favorites", FeedTabItem.Circle))
        assertEquals(FeedTabItem.Group, FeedTabItem.fromKey("group", FeedTabItem.Circle))
    }

    @Test
    fun fromKey_fallsBackOnUnknownOrNull() {
        assertEquals(FeedTabItem.Circle, FeedTabItem.fromKey(null, FeedTabItem.Circle))
        assertEquals(FeedTabItem.Recommend, FeedTabItem.fromKey("unknown_key", FeedTabItem.Recommend))
    }

    @Test
    fun defaultCombination_hasThreeTabs() {
        val left = FeedTabSelection(FeedTabItem.Circle)
        val right = FeedTabSelection(FeedTabItem.Recommend)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(3, tabs.size)
        assertEquals("好友圈", tabs[0].label)
        assertEquals("feed_tab_circle", tabs[0].tag)
        assertFalse(tabs[0].isFollow)

        assertEquals("关注", tabs[1].label)
        assertEquals("feed_tab_follow", tabs[1].tag)
        assertTrue(tabs[1].isFollow)

        assertEquals("推荐", tabs[2].label)
        assertEquals("feed_tab_recommend", tabs[2].tag)
        assertFalse(tabs[2].isFollow)
    }

    @Test
    fun hideLeft_resultsInTwoTabs() {
        val left = FeedTabSelection(FeedTabItem.None)
        val right = FeedTabSelection(FeedTabItem.Recommend)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(2, tabs.size)
        assertEquals("关注", tabs[0].label)
        assertTrue(tabs[0].isFollow)
        assertEquals("推荐", tabs[1].label)
        assertFalse(tabs[1].isFollow)
    }

    @Test
    fun hideRight_resultsInTwoTabs() {
        val left = FeedTabSelection(FeedTabItem.Circle)
        val right = FeedTabSelection(FeedTabItem.None)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(2, tabs.size)
        assertEquals("好友圈", tabs[0].label)
        assertFalse(tabs[0].isFollow)
        assertEquals("关注", tabs[1].label)
        assertTrue(tabs[1].isFollow)
    }

    @Test
    fun hideBoth_resultsInSingleTabWithoutTabBar() {
        val left = FeedTabSelection(FeedTabItem.None)
        val right = FeedTabSelection(FeedTabItem.None)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(1, tabs.size)
        assertEquals("关注", tabs[0].label)
        assertTrue(tabs[0].isFollow)
        // 左右都隐藏时，不渲染分段 Tab（feedHasTabs 为 false）
        val feedHasTabs = tabs.size > 1
        assertFalse("左右隐藏时不应显示顶部分段胶囊", feedHasTabs)
    }

    @Test
    fun sameTabsOnLeftAndRight_supported() {
        val left = FeedTabSelection(FeedTabItem.Local)
        val right = FeedTabSelection(FeedTabItem.Local)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(3, tabs.size)
        assertEquals("left_local", tabs[0].key)
        assertEquals("本地关注", tabs[0].label)
        assertEquals("feed_tab_local", tabs[0].tag)

        assertEquals("follow", tabs[1].key)
        assertEquals("关注", tabs[1].label)

        assertEquals("right_local", tabs[2].key)
        assertEquals("本地关注", tabs[2].label)
        assertEquals("feed_tab_local", tabs[2].tag)
    }

    @Test
    fun customLeftAndRight_supported() {
        val left = FeedTabSelection(FeedTabItem.Local)
        val right = FeedTabSelection(FeedTabItem.Circle)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left, right, filter)
        assertEquals(3, tabs.size)
        assertEquals("本地关注", tabs[0].label)
        assertEquals("关注", tabs[1].label)
        assertEquals("好友圈", tabs[2].label)
    }

    @Test
    fun customCenter_supported() {
        val left = FeedTabSelection(FeedTabItem.None)
        val center = FeedTabSelection(FeedTabItem.Recommend)
        val right = FeedTabSelection(FeedTabItem.None)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left = left, center = center, right = right, filter = filter)
        assertEquals(1, tabs.size)
        assertEquals("推荐", tabs[0].label)
        assertFalse(tabs[0].isFollow)
    }

    @Test
    fun groupSelection_showsGroupNameAndOwnKey() {
        val left = FeedTabSelection(FeedTabItem.None)
        val center = FeedTabSelection(FeedTabItem.Group, FollowGroup("5012345", "同事"))
        val right = FeedTabSelection(FeedTabItem.None)
        val filter = FeedFilter.Following

        val tabs = buildTabs(left = left, center = center, right = right, filter = filter)
        assertEquals(1, tabs.size)
        assertEquals("center_group_5012345", tabs[0].key)
        assertEquals("同事", tabs[0].label)
        assertEquals("feed_tab_group_5012345", tabs[0].tag)
        assertEquals("「同事」还没有内容", tabs[0].emptyText)
    }

    @Test
    fun groupSelection_switchingGroupSwitchesTabKey() {
        val first = FeedTabSelection(FeedTabItem.Group, FollowGroup("a", "同事"))
        val second = FeedTabSelection(FeedTabItem.Group, FollowGroup("b", "朋友"))
        assertTrue("换一个分组就是换了一条流", first.key != second.key)
        assertEquals("关注分组", FeedTabSelection(FeedTabItem.Group).label)
    }

    @Test
    fun feedTabOptions_listsBuiltInsThenGroups() {
        val groups = listOf(FollowGroup("a", "同事"), FollowGroup("b", "朋友"))
        val options = feedTabOptions(groups, allowNone = true)

        assertEquals(FeedTabItem.None, options.first().item)
        assertEquals(
            FeedTabItem.entries.count { it != FeedTabItem.None && it != FeedTabItem.Group },
            options.count { !it.isGroup && it.item != FeedTabItem.None },
        )
        assertEquals(listOf("同事", "朋友"), options.filter { it.isGroup }.map { it.label })
        assertTrue(options.none { it.item == FeedTabItem.Group && it.group == null })
    }

    @Test
    fun feedTabOptions_keepsCurrentGroupWhenItIsMissing() {
        val current = FeedTabSelection(FeedTabItem.Group, FollowGroup("gone", "已删除的分组"))
        val options = feedTabOptions(emptyList(), allowNone = false, current = current)

        assertTrue(options.contains(current))
    }

    private fun buildTabs(
        left: FeedTabSelection,
        right: FeedTabSelection,
        filter: FeedFilter,
        guest: Boolean = false,
    ): List<FeedTabSpec> = buildTabs(left, FeedTabSelection(FeedTabItem.Follow), right, filter, guest)

    private fun buildTabs(
        left: FeedTabSelection,
        center: FeedTabSelection,
        right: FeedTabSelection,
        filter: FeedFilter,
        guest: Boolean = false,
    ): List<FeedTabSpec> = buildList {
        if (left.item != FeedTabItem.None) {
            if (left.item == FeedTabItem.Follow) {
                add(FeedTabSpec.Follow(filter, guest, "left"))
            } else {
                add(FeedTabSpec.Custom(left, "left", guest))
            }
        }
        if (center.item == FeedTabItem.Follow) {
            add(FeedTabSpec.Follow(filter, guest, "center"))
        } else {
            add(FeedTabSpec.Custom(center, "center", guest))
        }
        if (right.item != FeedTabItem.None) {
            if (right.item == FeedTabItem.Follow) {
                add(FeedTabSpec.Follow(filter, guest, "right"))
            } else {
                add(FeedTabSpec.Custom(right, "right", guest))
            }
        }
    }
}
