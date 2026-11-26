package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAvatarTest {

    @Test
    fun skipsPlaceholderAvatarHdAndFallsBackToProfileImage() {
        val user = User.from(
            JSONObject(
                """
                {
                  "id": 7451120220,
                  "screen_name": "psy常同学",
                  "profile_image_url": "https://tvax1.sinaimg.cn/default/images/default_avatar_male_180.gif?KID=imgbed,tva&Expires=1795459984&ssig=5XgtlMouns",
                  "avatar_hd": "https://ss1.sinaimg.cn/orj480/default_avatar_male_180&690"
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(
            "https://tvax1.sinaimg.cn/default/images/default_avatar_male_180.gif?KID=imgbed,tva&Expires=1795459984&ssig=5XgtlMouns",
            user.avatar,
        )
    }

    @Test
    fun keepsRealAvatarHd() {
        val user = User.from(
            JSONObject(
                """
                {
                  "id": 1,
                  "screen_name": "a",
                  "avatar_hd": "https://tvax4.sinaimg.cn/crop.0.0.1036.1036.180/007I2iyyly8hu3a17bdxej30ss0ssta7.jpg?KID=imgbed,tva&Expires=1795456676&ssig=Y1yiFFv%2B0Z"
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(
            "https://tvax4.sinaimg.cn/crop.0.0.1036.1036.180/007I2iyyly8hu3a17bdxej30ss0ssta7.jpg?KID=imgbed,tva&Expires=1795456676&ssig=Y1yiFFv%2B0Z",
            user.avatar,
        )
    }
}
