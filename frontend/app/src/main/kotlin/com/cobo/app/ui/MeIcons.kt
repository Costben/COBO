package com.cobo.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * ColorOS 17 设置页同款双色图标功能色系（浅色 / 深色双套值，暗底略提亮保证对比度）：
 * - [Blue]：核心主干、连接、账号身份与个人记录
 * - [Orange]：个性化外观、交互体验与创作反馈
 * - [Yellow]：珍藏星标与热点发现聚光
 * - [Green]：对话通信、空间定位与应用浏览行为
 * - [Red]：强情感 / 高警示（极度克制，仅用于点赞爱心）
 * - [Neutral]：系统更新、关于与杂项兜底（对齐 ColorOS「系统与更新 / 关于本机」）
 */
internal enum class MeIconTint(val light: Color, val dark: Color) {
    Green(Color(0xFF00BD13), Color(0xFF24B232)),
    Blue(Color(0xFF0080FF), Color(0xFF1A8CFF)),
    Orange(Color(0xFFFF7700), Color(0xFFF08222)),
    Yellow(Color(0xFFFFB200), Color(0xFFE5A100)),
    Red(Color(0xFFEB3B2F), Color(0xFFEB493D)),
    Neutral(Color(0xFF5C6066), Color(0xFF9EA4AD)),
    ;

    fun resolve(dark: Boolean): Color = if (dark) this.dark else light
}

/**
 * 「我」页面 ColorOS 17 经典双色面性入口图标。
 *
 * 与 BiliCO `NavAndMeIcons` 保持一致的几何与透明度规范：
 * 24dp 画布，次级层使用 `SECONDARY_ALPHA = 0.38f`，重叠处通过 `EvenOdd` 负空间镂空，
 * 主视觉层保持 `1f` 实心以保证列表扫描对比度。
 */
internal object MeIcons {

    /** 我的微博：圆角文稿页 + 三行正文条。 */
    val Statuses: ImageVector by lazy {
        val bar1 = roundedRectPath(7.3f, 7.1f, 9.4f, 1.9f, 0.95f)
        val bar2 = roundedRectPath(7.3f, 11.05f, 9.4f, 1.9f, 0.95f)
        val bar3 = roundedRectPath(7.3f, 15.0f, 6.2f, 1.9f, 0.95f)
        dualToneVector(
            name = "MeStatuses",
            layers = listOf(
                MeLayer(
                    path = roundedRectPath(3.8f, 2.8f, 16.4f, 18.4f, 4.0f) + bar1 + bar2 + bar3,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = bar1 + bar2 + bar3),
            ),
        )
    }

    /** 我的收藏 / 本地收藏：文件夹 + 实心五角星。 */
    val Favorites: ImageVector by lazy {
        val folder = "M2.5,7.2C2.5,5.1 4.1,3.5 6.2,3.5H9.1C10.15,3.5 11.1,4.02 11.68,4.88" +
            "L12.4,5.95H17.8C19.9,5.95 21.5,7.55 21.5,9.65V16.8C21.5,18.9 19.9,20.5 17.8,20.5" +
            "H6.2C4.1,20.5 2.5,18.9 2.5,16.8V7.2Z"
        val star = "M11.55,9.15C11.75,8.72 12.25,8.72 12.45,9.15L13.45,11.22L15.72,11.55" +
            "C16.18,11.62 16.36,12.18 16.03,12.5L14.39,14.1L14.78,16.36C14.86,16.82 14.38,17.16 13.97,16.95" +
            "L12,15.9L10.03,16.95C9.62,17.16 9.14,16.82 9.22,16.36L9.61,14.1L7.97,12.5" +
            "C7.64,12.18 7.82,11.62 8.28,11.55L10.55,11.22L11.55,9.15Z"
        dualToneVector(
            name = "MeFavorites",
            layers = listOf(
                MeLayer(path = folder + star, alpha = SECONDARY_ALPHA, evenOdd = true),
                MeLayer(path = star),
            ),
        )
    }

    /** 我赞过的：柔透爱心底托 + 居中实心对勾（表达「我已赞过」）。 */
    val MyLikes: ImageVector by lazy {
        val heart = "M12.0,6.15C10.9,4.6 9.3,3.75 7.5,3.75C4.75,3.75 2.6,5.9 2.6,8.65" +
            "C2.6,13.2 7.45,17.0 11.1,19.75C11.63,20.15 12.37,20.15 12.9,19.75" +
            "C16.55,17.0 21.4,13.2 21.4,8.65C21.4,5.9 19.25,3.75 16.5,3.75" +
            "C14.7,3.75 13.1,4.6 12.0,6.15Z"
        val check = "M9.11,11.09L11.0,12.99L15.09,8.89A1.0,1.0 0 0,1 16.51,10.31" +
            "L11.71,15.11C11.32,15.5 10.68,15.5 10.29,15.11L7.69,12.51" +
            "A1.0,1.0 0 0,1 9.11,11.09Z"
        dualToneVector(
            name = "MeMyLikes",
            layers = listOf(
                MeLayer(path = heart + check, alpha = SECONDARY_ALPHA, evenOdd = true),
                MeLayer(path = check),
            ),
        )
    }

    /** 收到的赞：半透明袖口 + 实心拇指手掌。 */
    val ReceivedLikes: ImageVector by lazy {
        dualToneVector(
            name = "MeReceivedLikes",
            layers = listOf(
                MeLayer(
                    path = roundedRectPath(2.8f, 9.8f, 4.3f, 10.4f, 2.0f),
                    alpha = SECONDARY_ALPHA,
                ),
                MeLayer(
                    path = "M8.4,10.3C8.4,9.8 8.58,9.3 8.88,8.9L12.15,4.15" +
                        "C12.62,3.46 13.55,3.25 14.28,3.68C14.95,4.08 15.25,4.88 15.0,5.62L14.0,8.65H18.35" +
                        "C19.95,8.65 21.15,10.05 20.85,11.62L19.65,17.85C19.35,19.25 18.12,20.2 16.7,20.2" +
                        "H10.2C9.2,20.2 8.4,19.4 8.4,18.4V10.3Z",
                ),
            ),
        )
    }

    /** 收到的评论：半透明气泡 + 实心双条纹。 */
    val ReceivedComments: ImageVector by lazy {
        val barTop = roundedRectPath(6.8f, 7.9f, 10.4f, 1.95f, 0.975f)
        val barBottom = roundedRectPath(6.8f, 11.75f, 7.2f, 1.95f, 0.975f)
        dualToneVector(
            name = "MeReceivedComments",
            layers = listOf(
                MeLayer(
                    path = "M6.6,3.2H17.4C20.0,3.2 21.4,4.6 21.4,7.2V14.0C21.4,16.6 20.0,18.0 17.4,18.0" +
                        "H10.2C9.1,18.0 8.5,18.18 7.6,18.82L4.65,20.92C3.5,21.74 2.6,21.12 2.6,19.72" +
                        "V7.2C2.6,4.6 4.0,3.2 6.6,3.2Z" + barTop + barBottom,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = barTop + barBottom),
            ),
        )
    }

    /** 发出的评论：带缺口文稿页 + 实心斜笔。 */
    val SentComments: ImageVector by lazy {
        val line1 = roundedRectPath(5.7f, 6.9f, 6.2f, 1.85f, 0.925f)
        val line2 = roundedRectPath(5.7f, 10.4f, 4.3f, 1.85f, 0.925f)
        val sheetWithNotch = "M6.4,3.2H11.8A3.2,3.2 0 0,1 15.0,6.4V7.6" +
            "C15.0,8.1 14.8,8.58 14.45,8.93L9.8,13.58C9.42,13.96 9.15,14.43 9.0,14.94" +
            "L8.45,16.82C8.15,17.85 9.1,18.8 10.13,18.5L12.1,17.92" +
            "C12.6,17.77 13.06,17.5 13.43,17.13L14.15,16.41" +
            "C14.52,16.04 15.0,16.3 15.0,16.82V17.6A3.2,3.2 0 0,1 11.8,20.8" +
            "H6.4A3.2,3.2 0 0,1 3.2,17.6V6.4A3.2,3.2 0 0,1 6.4,3.2Z"
        val pen = "M18.55,6.55A1.485,1.485 0 0,1 20.65,8.65L13.1,16.2" +
            "L10.25,17.38C9.7,17.61 9.29,17.2 9.52,16.65L11.0,14.1L18.55,6.55Z"
        dualToneVector(
            name = "MeSentComments",
            layers = listOf(
                MeLayer(
                    path = sheetWithNotch + line1 + line2,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = line1 + line2 + pen),
            ),
        )
    }

    /** 草稿箱：带右上斜角缺口的圆角方框 + 实心编辑笔。 */
    val Drafts: ImageVector by lazy {
        val boxWithNotch = "M7.2,5.4H13.7C14.25,5.4 14.52,6.06 14.14,6.45L10.3,10.29" +
            "C9.92,10.67 9.65,11.14 9.5,11.65L8.95,13.53C8.65,14.56 9.6,15.51 10.63,15.21" +
            "L12.6,14.63C13.1,14.48 13.56,14.21 13.93,13.84L17.55,10.22" +
            "C17.94,9.83 18.6,10.11 18.6,10.66V16.8A4.0,4.0 0 0,1 14.6,20.8" +
            "H7.2A4.0,4.0 0 0,1 3.2,16.8V9.4A4.0,4.0 0 0,1 7.2,5.4Z"
        val pencil = "M19.4,2.8A1.273,1.273 0 0,1 21.2,4.6L13.6,12.2" +
            "L10.45,13.5C9.88,13.74 9.46,13.32 9.7,12.75L11.8,10.4L19.4,2.8Z"
        dualToneVector(
            name = "MeDrafts",
            layers = listOf(
                MeLayer(path = boxWithNotch, alpha = SECONDARY_ALPHA),
                MeLayer(path = pencil),
            ),
        )
    }

    /** 浏览历史：圆盘时钟 + 实心指针。 */
    val History: ImageVector by lazy {
        val handsCut = "M12,7.2C12.55,7.2 13,7.65 13,8.2V11.58L15.8,13.28" +
            "C16.27,13.56 16.42,14.16 16.14,14.63C15.86,15.1 15.26,15.25 14.79,14.97" +
            "L11.5,12.98C11.19,12.79 11,12.46 11,12.1V8.2C11,7.65 11.45,7.2 12,7.2Z"
        dualToneVector(
            name = "MeHistory",
            layers = listOf(
                MeLayer(
                    path = circlePath(12f, 12f, 9.5f) + handsCut,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = handsCut),
            ),
        )
    }

    /** 话题 / 超话：圆形衬底 + 实心井号。 */
    val Topic: ImageVector by lazy {
        // 用矩形拼成一体化镂空井号，使底盘与前景严丝合缝无重叠混色
        val hash =
            "M9.75,6.4C10.22,6.4 10.6,6.78 10.6,7.25V8.95H13.4V7.25" +
                "C13.4,6.78 13.78,6.4 14.25,6.4C14.72,6.4 15.1,6.78 15.1,7.25V8.95H16.75" +
                "C17.22,8.95 17.6,9.33 17.6,9.8C17.6,10.27 17.22,10.65 16.75,10.65H15.1V13.35H16.75" +
                "C17.22,13.35 17.6,13.73 17.6,14.2C17.6,14.67 17.22,15.05 16.75,15.05H15.1V16.75" +
                "C15.1,17.22 14.72,17.6 14.25,17.6C13.78,17.6 13.4,17.22 13.4,16.75V15.05H10.6V16.75" +
                "C10.6,17.22 10.22,17.6 9.75,17.6C9.28,17.6 8.9,17.22 8.9,16.75V15.05H7.25" +
                "C6.78,15.05 6.4,14.67 6.4,14.2C6.4,13.73 6.78,13.35 7.25,13.35H8.9V10.65H7.25" +
                "C6.78,10.65 6.4,10.27 6.4,9.8C6.4,9.33 6.78,8.95 7.25,8.95H8.9V7.25" +
                "C8.9,6.78 9.28,6.4 9.75,6.4Z" +
                "M10.6,10.65V13.35H13.4V10.65H10.6Z"
        dualToneVector(
            name = "MeTopic",
            layers = listOf(
                MeLayer(
                    path = circlePath(12f, 12f, 9.5f) + hash,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = hash, evenOdd = true),
            ),
        )
    }

    /** 附近：实心定位水滴 + 柔透内圆点。 */
    val Nearby: ImageVector by lazy {
        val pin = "M12.0,2.7C8.0,2.7 4.75,5.95 4.75,9.95C4.75,14.55 9.15,18.75 11.2,20.58" +
            "C11.65,20.98 12.35,20.98 12.8,20.58C14.85,18.75 19.25,14.55 19.25,9.95" +
            "C19.25,5.95 16.0,2.7 12.0,2.7Z"
        val dot = circlePath(12f, 9.9f, 3.25f)
        dualToneVector(
            name = "MeNearby",
            layers = listOf(
                MeLayer(path = pin + dot, evenOdd = true),
                MeLayer(path = dot, alpha = SECONDARY_ALPHA),
            ),
        )
    }

    /** 显示与交互：三轨分段滑杆 + 实心滑块圆点。 */
    val DisplaySettings: ImageVector by lazy {
        val tracks =
            roundedRectPath(3.2f, 5.2f, 8.2f, 2.0f, 1.0f) +
                roundedRectPath(18.6f, 5.2f, 2.2f, 2.0f, 1.0f) +
                roundedRectPath(3.2f, 11.0f, 2.2f, 2.0f, 1.0f) +
                roundedRectPath(12.6f, 11.0f, 8.2f, 2.0f, 1.0f) +
                roundedRectPath(3.2f, 16.8f, 7.2f, 2.0f, 1.0f) +
                roundedRectPath(17.6f, 16.8f, 3.2f, 2.0f, 1.0f)
        val knobs =
            circlePath(15.0f, 6.2f, 2.55f) +
                circlePath(9.0f, 12.0f, 2.55f) +
                circlePath(14.0f, 17.8f, 2.55f)
        dualToneVector(
            name = "MeDisplaySettings",
            layers = listOf(
                MeLayer(path = tracks, alpha = SECONDARY_ALPHA),
                MeLayer(path = knobs),
            ),
        )
    }

    /** 微博设置：房体轮廓 + 实心拱门。 */
    val FeedSettings: ImageVector by lazy {
        val houseWithDoor = "M2.4,11.1C2.4,9.5 2.98,8.7 4.22,7.66L10.08,2.74" +
            "C11.18,1.82 12.82,1.82 13.92,2.74L19.78,7.66C21.02,8.7 21.6,9.5 21.6,11.1" +
            "V17.6C21.6,20.2 20.2,21.6 17.6,21.6H15.3V16.4" +
            "C15.3,14.58 13.82,13.1 12.0,13.1C10.18,13.1 8.7,14.58 8.7,16.4V21.6" +
            "H6.4C3.8,21.6 2.4,20.2 2.4,17.6V11.1Z"
        val door = "M9.6,16.5C9.6,15.17 10.67,14.1 12.0,14.1" +
            "C13.33,14.1 14.4,15.17 14.4,16.5V21.6H9.6V16.5Z"
        dualToneVector(
            name = "MeFeedSettings",
            layers = listOf(
                MeLayer(path = houseWithDoor, alpha = SECONDARY_ALPHA),
                MeLayer(path = door),
            ),
        )
    }

    /** 账号与内容：实心头像 + 柔透肩部。 */
    val AccountSettings: ImageVector by lazy {
        dualToneVector(
            name = "MeAccountSettings",
            layers = listOf(
                MeLayer(
                    path = "M3.5,18.7C3.5,15.05 7.05,13.05 12.0,13.05C16.95,13.05 20.5,15.05 20.5,18.7" +
                        "C20.5,20.45 19.3,21.6 17.25,21.6H6.75C4.7,21.6 3.5,20.45 3.5,18.7Z",
                    alpha = SECONDARY_ALPHA,
                ),
                MeLayer(path = circlePath(12f, 7.25f, 4.0f)),
            ),
        )
    }

    /** 更新：圆盘衬底 + 实心向上箭头。 */
    val UpdateSettings: ImageVector by lazy {
        val upArrow = "M12.0,6.8C12.32,6.8 12.62,6.93 12.84,7.16L16.55,10.88" +
            "C17.0,11.33 17.0,12.06 16.55,12.51C16.1,12.96 15.37,12.96 14.92,12.51" +
            "L13.15,10.74V16.25C13.15,16.89 12.64,17.4 12.0,17.4" +
            "C11.36,17.4 10.85,16.89 10.85,16.25V10.74L9.08,12.51" +
            "C8.63,12.96 7.9,12.96 7.45,12.51C7.0,12.06 7.0,11.33 7.45,10.88" +
            "L11.16,7.16C11.38,6.93 11.68,6.8 12.0,6.8Z"
        dualToneVector(
            name = "MeUpdateSettings",
            layers = listOf(
                MeLayer(
                    path = circlePath(12f, 12f, 9.5f) + upArrow,
                    alpha = SECONDARY_ALPHA,
                    evenOdd = true,
                ),
                MeLayer(path = upArrow),
            ),
        )
    }

    /** 其他设置：六齿对称齿轮 + 柔透内孔。 */
    val OtherSettings: ImageVector by lazy {
        val gearOuter = "M9.226,5.302Q9.586,3.85 10.223,2.413Q12.0,1.9 13.777,2.413Q14.414,3.85 14.774,5.302" +
            "Q15.55,5.851 16.414,6.248Q17.851,5.834 19.414,5.668Q20.747,6.95 21.191,8.745" +
            "Q20.265,10.016 19.188,11.054Q19.1,12.0 19.188,12.946Q20.265,13.984 21.191,15.255" +
            "Q20.747,17.05 19.414,18.332Q17.851,18.166 16.414,17.752Q15.55,18.149 14.774,18.698" +
            "Q14.414,20.15 13.777,21.587Q12.0,22.1 10.223,21.587Q9.586,20.15 9.226,18.698" +
            "Q8.45,18.149 7.586,17.752Q6.149,18.166 4.586,18.332Q3.253,17.05 2.809,15.255" +
            "Q3.735,13.984 4.812,12.946Q4.9,12.0 4.812,11.054Q3.735,10.016 2.809,8.745" +
            "Q3.253,6.95 4.586,5.668Q6.149,5.834 7.586,6.248Q8.45,5.851 9.226,5.302Z"
        val hole = circlePath(12f, 12f, 3.85f)
        dualToneVector(
            name = "MeOtherSettings",
            layers = listOf(
                MeLayer(path = gearOuter + hole, evenOdd = true),
                MeLayer(path = hole, alpha = 0.34f),
            ),
        )
    }

    private data class MeLayer(
        val path: String,
        val alpha: Float = 1f,
        val evenOdd: Boolean = false,
    )

    private fun dualToneVector(name: String, layers: List<MeLayer>): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            layers.forEach { layer ->
                addPath(
                    pathData = addPathNodes(layer.path),
                    fill = SolidColor(Color.Black),
                    fillAlpha = layer.alpha,
                    pathFillType = if (layer.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        }.build()

    private fun circlePath(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},$cy A$r,$r 0 1,0 ${cx + r},$cy A$r,$r 0 1,0 ${cx - r},$cy Z"

    private fun roundedRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): String {
        val rr = r.coerceAtMost(minOf(w, h) / 2f)
        val x2 = x + w
        val y2 = y + h
        return "M${x + rr},$y H${x2 - rr} A$rr,$rr 0 0,1 $x2,${y + rr} V${y2 - rr} " +
            "A$rr,$rr 0 0,1 ${x2 - rr},$y2 H${x + rr} A$rr,$rr 0 0,1 $x,${y2 - rr} " +
            "V${y + rr} A$rr,$rr 0 0,1 ${x + rr},$y Z"
    }

    private const val SECONDARY_ALPHA = 0.38f
}
