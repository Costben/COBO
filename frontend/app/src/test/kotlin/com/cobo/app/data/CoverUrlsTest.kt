package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverUrlsTest {

    @Test
    fun cropUrl_becomesLargeOriginal() {
        assertEquals(
            listOf("https://wx3.sinaimg.cn/large/b06cab5egy1ideik76lxhj21o01o0kjl.jpg"),
            coverUrls("https://wx3.sinaimg.cn/crop.0.0.640.640.640/b06cab5egy1ideik76lxhj21o01o0kjl.jpg"),
        )
    }

    @Test
    fun semicolonJoinedCovers_areSplit() {
        assertEquals(
            listOf(
                "https://wx1.sinaimg.cn/large/74ada239gy1igm8i0gv8lj20wi0wi756.jpg",
                "https://wx1.sinaimg.cn/large/74ada239gy1ih1kzb2d5qj20wi0wi41y.jpg",
            ),
            coverUrls(
                "https://wx1.sinaimg.cn/crop.0.0.640.640.640/74ada239gy1igm8i0gv8lj20wi0wi756.jpg;" +
                    "https://wx1.sinaimg.cn/crop.0.0.640.640.640/74ada239gy1ih1kzb2d5qj20wi0wi41y.jpg",
            ),
        )
    }

    @Test
    fun blank_isEmpty() {
        assertEquals(emptyList<String>(), coverUrls(""))
    }
}
