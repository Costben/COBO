package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主页「添加到关注分组」和「基本资料」用的两条网页版数据：
 * 前者来自 `ajax/profile/getGroupList`，后者来自 `ajax/profile/detail`。
 * 两个接口的字段都换过形状，这里把能认的几种都钉住。
 */
class ProfileDetailParseTest {

    @Test
    fun parseGroupIds_readsArrayPayloadAndDropsUngrouped() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": [
                {"id": 4541216424989965, "idstr": "4541216424989965", "name": "同事"},
                {"idstr": "4541216424989966", "name": "朋友"},
                {"id": 0, "idstr": "0", "name": "未分组"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("4541216424989965", "4541216424989966"), parseGroupIds(root))
    }

    @Test
    fun parseGroupIds_acceptsDataListsShape() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "lists": [
                  {"idstr": "100", "name": "同事"},
                  {"id": 101, "name": "朋友"}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(listOf("100", "101"), parseGroupIds(root))
    }

    @Test
    fun parseGroupIds_returnsEmptyOnMissingPayload() {
        assertEquals(emptyList<String>(), parseGroupIds(null))
        assertEquals(emptyList<String>(), parseGroupIds(JSONObject("{}")))
    }

    /** `setGroup` 两个 id 参数：空选择必须落成 `0`（未分组），否则服务端不会摘掉旧分组。 */
    @Test
    fun groupIdsParam_fallsBackToUngrouped() {
        assertEquals("0", groupIdsParam(emptyList()))
        assertEquals("0", groupIdsParam(listOf("", " ")))
        assertEquals("4541216424989965", groupIdsParam(listOf("4541216424989965")))
        assertEquals(
            "4541216424989965,4541216424989966",
            groupIdsParam(listOf("4541216424989965", "4541216424989966")),
        )
    }

    @Test
    fun clientGroupMemberForm_usesSingularUidKey() {
        val form = clientGroupMemberForm("5368844760908634", "1642909335")
        assertEquals(mapOf("list_id" to "5368844760908634", "uid" to "1642909335"), form)
        assertTrue("客户端通道必须传单数 uid，不能传 uids", "uids" !in form)
    }

    @Test
    fun diffProfileGroupIds_computesAddsRemovesAndSkipsDeleted() {
        val (toAdd, toRemove) = diffProfileGroupIds(
            selected = listOf("g2", "g3", ""),
            origin = listOf("g1", "g2", "g_deleted"),
            deleted = setOf("g_deleted"),
        )
        assertEquals(setOf("g3"), toAdd)
        assertEquals(setOf("g1"), toRemove)
    }

    @Test
    fun parseProfileDetail_mapsKnownFields() {
        val data = JSONObject(
            """
            {
              "gender": "m",
              "birthday": "1990-01-01",
              "location": "北京 海淀区",
              "ip_location": "北京",
              "created_at": "2011-11-24",
              "education": {"name": "清华大学"},
              "company": "新浪",
              "sunshine_credit": {"level": "信用极好"},
              "labels": [{"name": "数码"}, "摄影"],
              "description": "一个简介"
            }
            """.trimIndent()
        )

        val detail = parseProfileDetail(data)

        assertEquals("男", detail.gender)
        assertEquals("1990-01-01", detail.birthday)
        assertEquals("北京 海淀区", detail.location)
        assertEquals("北京", detail.ipLocation)
        assertEquals("2011-11-24", detail.createdAt)
        assertEquals("清华大学", detail.education)
        assertEquals("新浪", detail.company)
        assertEquals("信用极好", detail.credit)
        assertEquals(listOf("数码", "摄影"), detail.labels)
        assertEquals("一个简介", detail.description)
    }

    @Test
    fun parseProfileDetail_toleratesMissingAndNullFields() {
        val detail = parseProfileDetail(
            JSONObject(
                """
                {"gender": null, "birthday": null, "labels": []}
                """.trimIndent()
            )
        )

        assertEquals("", detail.gender)
        assertEquals("", detail.birthday)
        assertTrue(detail.labels.isEmpty())
        assertEquals(ProfileDetail(), detail)
    }
}
