package com.cobo.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CoboIcons {

    val Repost: ImageVector by lazy {
        ImageVector.Builder(
            name = "Repost",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 8.5f)
                horizontalLineTo(17f)
                moveTo(14f, 5.2f)
                lineTo(17.3f, 8.5f)
                lineTo(14f, 11.8f)
                moveTo(19.5f, 15.5f)
                horizontalLineTo(7f)
                moveTo(10f, 12.2f)
                lineTo(6.7f, 15.5f)
                lineTo(10f, 18.8f)
            }
        }.build()
    }

    val Comment: ImageVector by lazy {
        ImageVector.Builder(
            name = "Comment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 4.5f)
                horizontalLineTo(18.5f)
                curveTo(19.6f, 4.5f, 20.5f, 5.4f, 20.5f, 6.5f)
                verticalLineTo(15f)
                curveTo(20.5f, 16.1f, 19.6f, 17f, 18.5f, 17f)
                horizontalLineTo(11f)
                lineTo(6.5f, 20.5f)
                verticalLineTo(17f)
                horizontalLineTo(5.5f)
                curveTo(4.4f, 17f, 3.5f, 16.1f, 3.5f, 15f)
                verticalLineTo(6.5f)
                curveTo(3.5f, 5.4f, 4.4f, 4.5f, 5.5f, 4.5f)
                close()
            }
        }.build()
    }

    val Heart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Heart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 20f)
                curveTo(10.4f, 18.6f, 3.5f, 14f, 3.5f, 9.3f)
                curveTo(3.5f, 6.6f, 5.6f, 4.5f, 8.2f, 4.5f)
                curveTo(9.7f, 4.5f, 11.1f, 5.3f, 12f, 6.6f)
                curveTo(12.9f, 5.3f, 14.3f, 4.5f, 15.8f, 4.5f)
                curveTo(18.4f, 4.5f, 20.5f, 6.6f, 20.5f, 9.3f)
                curveTo(20.5f, 14f, 13.6f, 18.6f, 12f, 20f)
                close()
            }
        }.build()
    }

    val HeartFill: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 20.4f)
                curveTo(10.2f, 18.8f, 3f, 14f, 3f, 9.2f)
                curveTo(3f, 6.3f, 5.3f, 4f, 8.1f, 4f)
                curveTo(9.7f, 4f, 11.1f, 4.8f, 12f, 6.2f)
                curveTo(12.9f, 4.8f, 14.3f, 4f, 15.9f, 4f)
                curveTo(18.7f, 4f, 21f, 6.3f, 21f, 9.2f)
                curveTo(21f, 14f, 13.8f, 18.8f, 12f, 20.4f)
                close()
            }
        }.build()
    }

    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "Star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.6f)
                lineTo(14.6f, 8.9f)
                lineTo(20.4f, 9.7f)
                lineTo(16.2f, 13.8f)
                lineTo(17.2f, 19.6f)
                lineTo(12f, 16.8f)
                lineTo(6.8f, 19.6f)
                lineTo(7.8f, 13.8f)
                lineTo(3.6f, 9.7f)
                lineTo(9.4f, 8.9f)
                close()
            }
        }.build()
    }

    val StarFill: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3.2f)
                lineTo(14.8f, 8.7f)
                lineTo(20.9f, 9.6f)
                lineTo(16.4f, 13.9f)
                lineTo(17.5f, 20f)
                lineTo(12f, 17.1f)
                lineTo(6.5f, 20f)
                lineTo(7.6f, 13.9f)
                lineTo(3.1f, 9.6f)
                lineTo(9.2f, 8.7f)
                close()
            }
        }.build()
    }

    val Emoji: ImageVector by lazy {
        ImageVector.Builder(
            name = "Emoji",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                close()
                moveTo(9f, 9.5f)
                lineTo(9f, 9.6f)
                moveTo(15f, 9.5f)
                lineTo(15f, 9.6f)
                moveTo(8f, 14f)
                curveTo(9f, 15.5f, 10.4f, 16.3f, 12f, 16.3f)
                curveTo(13.6f, 16.3f, 15f, 15.5f, 16f, 14f)
            }
        }.build()
    }

    val At: ImageVector by lazy {
        ImageVector.Builder(
            name = "At",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(19.5f, 12f)
                curveTo(19.5f, 16.14f, 16.14f, 19.5f, 12f, 19.5f)
                curveTo(7.86f, 19.5f, 4.5f, 16.14f, 4.5f, 12f)
                curveTo(4.5f, 7.86f, 7.86f, 4.5f, 12f, 4.5f)
                curveTo(16.14f, 4.5f, 19.5f, 7.86f, 19.5f, 12f)
                verticalLineTo(13.2f)
                curveTo(19.5f, 14.9f, 20.3f, 15.8f, 21.4f, 15.8f)
                curveTo(22.5f, 15.8f, 23f, 14.8f, 23f, 13.2f)
                verticalLineTo(12f)
                moveTo(15.8f, 12f)
                curveTo(15.8f, 14.1f, 14.1f, 15.8f, 12f, 15.8f)
                curveTo(9.9f, 15.8f, 8.2f, 14.1f, 8.2f, 12f)
                curveTo(8.2f, 9.9f, 9.9f, 8.2f, 12f, 8.2f)
                curveTo(14.1f, 8.2f, 15.8f, 9.9f, 15.8f, 12f)
                verticalLineTo(12f)
            }
        }.build()
    }

    val Topic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Topic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9.5f, 3.5f)
                lineTo(7.5f, 20.5f)
                moveTo(16.5f, 3.5f)
                lineTo(14.5f, 20.5f)
                moveTo(4f, 9f)
                horizontalLineTo(20.5f)
                moveTo(3.5f, 15f)
                horizontalLineTo(20f)
            }
        }.build()
    }

    // ---- 底栏图标：线性 / 面性成对 ----
    //
    // COUI 17 的底栏规范是「未选中线性、选中面性」，而 COUI 图标集里这三个 glyph 只有单一形态，
    // 所以两态都由同一条轮廓路径生成：线性 = 描边那条轮廓，面性 = 填充同一条轮廓。
    // 这样「把线性围起来的地方填平」在几何上严格成立，两态的字面和重心也完全一致。

    private fun outline(name: String, shape: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = shape,
            )
        }.build()

    private fun solid(name: String, shape: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
                pathBuilder = shape,
            )
        }.build()

    /** 房子轮廓，底部留出门口缺口。 */
    private val FeedShape: PathBuilder.() -> Unit = {
        moveTo(12f, 3f)
        lineTo(20.7f, 10.4f)
        lineTo(20.7f, 17.8f)
        curveTo(20.7f, 19.2f, 20.3f, 20.2f, 19.1f, 20.6f)
        curveTo(18.4f, 20.85f, 17.4f, 20.85f, 15.3f, 20.85f)
        lineTo(14.4f, 20.85f)
        lineTo(14.4f, 16.6f)
        curveTo(14.4f, 15.3f, 13.3f, 14.2f, 12f, 14.2f)
        curveTo(10.7f, 14.2f, 9.6f, 15.3f, 9.6f, 16.6f)
        lineTo(9.6f, 20.85f)
        lineTo(8.7f, 20.85f)
        curveTo(6.6f, 20.85f, 5.6f, 20.85f, 4.9f, 20.6f)
        curveTo(3.7f, 20.2f, 3.3f, 19.2f, 3.3f, 17.8f)
        lineTo(3.3f, 10.4f)
        close()
    }

    /** 气泡轮廓，左下挂一个尾巴。 */
    private val MessagesShape: PathBuilder.() -> Unit = {
        moveTo(6.9f, 4.2f)
        horizontalLineTo(17.1f)
        curveTo(19.2f, 4.2f, 20.8f, 5.8f, 20.8f, 7.9f)
        verticalLineTo(13.5f)
        curveTo(20.8f, 15.6f, 19.2f, 17.2f, 17.1f, 17.2f)
        horizontalLineTo(11.3f)
        lineTo(7.1f, 20.6f)
        verticalLineTo(17.2f)
        horizontalLineTo(6.9f)
        curveTo(4.8f, 17.2f, 3.2f, 15.6f, 3.2f, 13.5f)
        verticalLineTo(7.9f)
        curveTo(3.2f, 5.8f, 4.8f, 4.2f, 6.9f, 4.2f)
        close()
    }

    /** 人形轮廓：头 + 肩。 */
    private val ProfileShape: PathBuilder.() -> Unit = {
        moveTo(12f, 4.3f)
        curveTo(14.2f, 4.3f, 16f, 6.1f, 16f, 8.3f)
        curveTo(16f, 10.5f, 14.2f, 12.3f, 12f, 12.3f)
        curveTo(9.8f, 12.3f, 8f, 10.5f, 8f, 8.3f)
        curveTo(8f, 6.1f, 9.8f, 4.3f, 12f, 4.3f)
        close()
        moveTo(4.4f, 20.9f)
        curveTo(4.4f, 16.7f, 7.8f, 13.9f, 12f, 13.9f)
        curveTo(16.2f, 13.9f, 19.6f, 16.7f, 19.6f, 20.9f)
        close()
    }

    val FeedOutline: ImageVector by lazy { outline("FeedOutline", FeedShape) }
    val FeedFilled: ImageVector by lazy { solid("FeedFilled", FeedShape) }
    val MessagesOutline: ImageVector by lazy { outline("MessagesOutline", MessagesShape) }
    val MessagesFilled: ImageVector by lazy { solid("MessagesFilled", MessagesShape) }
    val ProfileOutline: ImageVector by lazy { outline("ProfileOutline", ProfileShape) }
    val ProfileFilled: ImageVector by lazy { solid("ProfileFilled", ProfileShape) }

    /** 超话前缀：微博官方双环螺旋钻石轮廓。 */
    val SuperTopic: ImageVector by lazy {
        ImageVector.Builder(
            name = "SuperTopic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12.0f, 15.1f)
                lineTo(6.7f, 9.8f)
                lineTo(8.3f, 7.5f)
                lineTo(15.7f, 7.5f)
                lineTo(17.3f, 9.8f)
                lineTo(9.8f, 17.3f)
                lineTo(2.5f, 9.8f)
                lineTo(6.4f, 4.3f)
                lineTo(17.6f, 4.3f)
                lineTo(21.5f, 9.8f)
                lineTo(12.0f, 19.7f)
            }
        }.build()
    }

    /** 视频前缀：线性摄影机图标（圆角机身 + 右侧镜头锥）。 */
    val VideoCamera: ImageVector by lazy {
        ImageVector.Builder(
            name = "VideoCamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 机身圆角矩形 (2.5..15.0, 6.2..17.8, r=2.3)
                moveTo(4.8f, 6.2f)
                horizontalLineTo(12.7f)
                curveTo(13.97f, 6.2f, 15.0f, 7.23f, 15.0f, 8.5f)
                verticalLineTo(15.5f)
                curveTo(15.0f, 16.77f, 13.97f, 17.8f, 12.7f, 17.8f)
                horizontalLineTo(4.8f)
                curveTo(3.53f, 17.8f, 2.5f, 16.77f, 2.5f, 15.5f)
                verticalLineTo(8.5f)
                curveTo(2.5f, 7.23f, 3.53f, 6.2f, 4.8f, 6.2f)
                close()
                // 右侧镜头梯形锥（接机身右边，不重复画竖线）
                moveTo(15.0f, 10.2f)
                lineTo(20.4f, 7.1f)
                curveTo(21.05f, 6.72f, 21.5f, 7.12f, 21.5f, 7.9f)
                verticalLineTo(16.1f)
                curveTo(21.5f, 16.88f, 21.05f, 17.28f, 20.4f, 16.9f)
                lineTo(15.0f, 13.8f)
            }
        }.build()
    }

    val Poll: ImageVector by lazy {
        ImageVector.Builder(
            name = "Poll",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 19f)
                lineTo(5f, 11f)
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(19f, 19f)
                lineTo(19f, 14f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12.5f)
                lineTo(9.5f, 17.5f)
                lineTo(19.5f, 7.5f)
            }
        }.build()
    }

    /** 网页查看：地球经纬线轮廓，外径与多线视觉字重对齐 COUI Regular 顶栏图标。 */
    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "Globe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.45f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20.8f, 12f)
                curveTo(20.8f, 16.86f, 16.86f, 20.8f, 12f, 20.8f)
                curveTo(7.14f, 20.8f, 3.2f, 16.86f, 3.2f, 12f)
                curveTo(3.2f, 7.14f, 7.14f, 3.2f, 12f, 3.2f)
                curveTo(16.86f, 3.2f, 20.8f, 7.14f, 20.8f, 12f)
                close()
                moveTo(15.6f, 12f)
                curveTo(15.6f, 16.86f, 13.99f, 20.8f, 12f, 20.8f)
                curveTo(10.01f, 20.8f, 8.4f, 16.86f, 8.4f, 12f)
                curveTo(8.4f, 7.14f, 10.01f, 3.2f, 12f, 3.2f)
                curveTo(13.99f, 3.2f, 15.6f, 7.14f, 15.6f, 12f)
                close()
                moveTo(3.73f, 9f)
                horizontalLineTo(20.27f)
                moveTo(3.73f, 15f)
                horizontalLineTo(20.27f)
            }
        }.build()
    }

    /** 列表排序用：竖直箭头。线宽与 [Check] 对齐，缩到 18dp 也不会糊成一块。 */
    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19.5f)
                lineTo(12f, 4.8f)
                moveTo(5.8f, 11f)
                lineTo(12f, 4.8f)
                lineTo(18.2f, 11f)
            }
        }.build()
    }

    /** [ArrowUp] 的镜像，指向下方。 */
    val ArrowDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                lineTo(12f, 19.2f)
                moveTo(5.8f, 13f)
                lineTo(12f, 19.2f)
                lineTo(18.2f, 13f)
            }
        }.build()
    }

    /** 收到的赞：线性大拇指轮廓。 */
    val ThumbUp: ImageVector by lazy {
        outline("ThumbUp") {
            // 左侧袖口
            moveTo(4.0f, 11.2f)
            curveTo(4.0f, 10.4f, 4.6f, 9.8f, 5.4f, 9.8f)
            horizontalLineTo(7.6f)
            verticalLineTo(20.0f)
            horizontalLineTo(5.4f)
            curveTo(4.6f, 20.0f, 4.0f, 19.4f, 4.0f, 18.6f)
            close()
            // 右侧大拇指与手掌外框
            moveTo(7.6f, 10.6f)
            lineTo(11.3f, 4.4f)
            curveTo(11.7f, 3.7f, 12.6f, 3.5f, 13.3f, 3.9f)
            curveTo(13.9f, 4.3f, 14.2f, 5.0f, 14.0f, 5.7f)
            lineTo(13.1f, 9.2f)
            horizontalLineTo(18.1f)
            curveTo(19.5f, 9.2f, 20.5f, 10.4f, 20.2f, 11.8f)
            lineTo(18.9f, 18.0f)
            curveTo(18.6f, 19.2f, 17.6f, 20.0f, 16.4f, 20.0f)
            horizontalLineTo(7.6f)
        }
    }

    /** 发出的评论：纸张与正在书写的笔（纯线性）。 */
    val WriteComment: ImageVector by lazy {
        outline("WriteComment") {
            // 纸张外框（右下角为笔留出开口）
            moveTo(15.2f, 8.2f)
            verticalLineTo(5.6f)
            curveTo(15.2f, 4.4f, 14.3f, 3.5f, 13.1f, 3.5f)
            horizontalLineTo(6.1f)
            curveTo(4.9f, 3.5f, 4.0f, 4.4f, 4.0f, 5.6f)
            verticalLineTo(18.4f)
            curveTo(4.0f, 19.6f, 4.9f, 20.5f, 6.1f, 20.5f)
            horizontalLineTo(13.1f)
            curveTo(14.3f, 20.5f, 15.2f, 19.6f, 15.2f, 18.4f)
            verticalLineTo(16.6f)
            // 纸面文字线
            moveTo(7.0f, 8.2f)
            horizontalLineTo(12.2f)
            moveTo(7.0f, 11.8f)
            horizontalLineTo(11.0f)
            // 右侧斜笔在纸面书写
            moveTo(18.8f, 7.6f)
            lineTo(20.6f, 9.4f)
            lineTo(13.4f, 16.6f)
            lineTo(10.6f, 17.4f)
            lineTo(11.4f, 14.6f)
            close()
        }
    }

    /** 浏览历史：线性时钟。 */
    val History: ImageVector by lazy {
        outline("History") {
            moveTo(20.5f, 12.0f)
            curveTo(20.5f, 16.69f, 16.69f, 20.5f, 12.0f, 20.5f)
            curveTo(7.31f, 20.5f, 3.5f, 16.69f, 3.5f, 12.0f)
            curveTo(3.5f, 7.31f, 7.31f, 3.5f, 12.0f, 3.5f)
            curveTo(16.69f, 3.5f, 20.5f, 7.31f, 20.5f, 12.0f)
            close()
            moveTo(12.0f, 7.6f)
            verticalLineTo(12.0f)
            lineTo(15.2f, 13.9f)
        }
    }

    /** 附近：线性定位标。 */
    val LocationOutline: ImageVector by lazy {
        outline("LocationOutline") {
            moveTo(12.0f, 21.0f)
            curveTo(12.0f, 21.0f, 18.5f, 14.9f, 18.5f, 9.8f)
            curveTo(18.5f, 6.21f, 15.59f, 3.3f, 12.0f, 3.3f)
            curveTo(8.41f, 3.3f, 5.5f, 6.21f, 5.5f, 9.8f)
            curveTo(5.5f, 14.9f, 12.0f, 21.0f, 12.0f, 21.0f)
            close()
            moveTo(14.5f, 9.8f)
            curveTo(14.5f, 11.18f, 13.38f, 12.3f, 12.0f, 12.3f)
            curveTo(10.62f, 12.3f, 9.5f, 11.18f, 9.5f, 9.8f)
            curveTo(9.5f, 8.42f, 10.62f, 7.3f, 12.0f, 7.3f)
            curveTo(13.38f, 7.3f, 14.5f, 8.42f, 14.5f, 9.8f)
            close()
        }
    }

    /** 显示与交互：线性三滑块调节图标。 */
    val TuneOutline: ImageVector by lazy {
        outline("TuneOutline") {
            // 第一行滑杆
            moveTo(4.0f, 6.5f)
            horizontalLineTo(12.5f)
            moveTo(16.5f, 6.5f)
            horizontalLineTo(20.0f)
            moveTo(16.5f, 6.5f)
            curveTo(16.5f, 7.6f, 15.6f, 8.5f, 14.5f, 8.5f)
            curveTo(13.4f, 8.5f, 12.5f, 7.6f, 12.5f, 6.5f)
            curveTo(12.5f, 5.4f, 13.4f, 4.5f, 14.5f, 4.5f)
            curveTo(15.6f, 4.5f, 16.5f, 5.4f, 16.5f, 6.5f)
            close()
            // 第二行滑杆
            moveTo(4.0f, 12.0f)
            horizontalLineTo(7.5f)
            moveTo(11.5f, 12.0f)
            horizontalLineTo(20.0f)
            moveTo(11.5f, 12.0f)
            curveTo(11.5f, 13.1f, 10.6f, 14.0f, 9.5f, 14.0f)
            curveTo(8.4f, 14.0f, 7.5f, 13.1f, 7.5f, 12.0f)
            curveTo(7.5f, 10.9f, 8.4f, 10.0f, 9.5f, 10.0f)
            curveTo(10.6f, 10.0f, 11.5f, 10.9f, 11.5f, 12.0f)
            close()
            // 第三行滑杆
            moveTo(4.0f, 17.5f)
            horizontalLineTo(13.5f)
            moveTo(17.5f, 17.5f)
            horizontalLineTo(20.0f)
            moveTo(17.5f, 17.5f)
            curveTo(17.5f, 18.6f, 16.6f, 19.5f, 15.5f, 19.5f)
            curveTo(14.4f, 19.5f, 13.5f, 18.6f, 13.5f, 17.5f)
            curveTo(13.5f, 16.4f, 14.4f, 15.5f, 15.5f, 15.5f)
            curveTo(16.6f, 15.5f, 17.5f, 16.4f, 17.5f, 17.5f)
            close()
        }
    }

    /** 更新：线性圆环箭头。 */
    val UpdateOutline: ImageVector by lazy {
        outline("UpdateOutline") {
            moveTo(20.0f, 12.0f)
            curveTo(20.0f, 16.42f, 16.42f, 20.0f, 12.0f, 20.0f)
            curveTo(7.58f, 20.0f, 4.0f, 16.42f, 4.0f, 12.0f)
            curveTo(4.0f, 7.58f, 7.58f, 4.0f, 12.0f, 4.0f)
            curveTo(16.42f, 4.0f, 20.0f, 7.58f, 20.0f, 12.0f)
            close()
            moveTo(12.0f, 15.6f)
            verticalLineTo(8.4f)
            moveTo(8.9f, 11.3f)
            lineTo(12.0f, 8.2f)
            lineTo(15.1f, 11.3f)
        }
    }

    /** 链接前缀：线性图片框图标（圆角边框 + 山峰 + 太阳），用于「查看图片」。 */
    val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "Image",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.8f, 4.4f)
                horizontalLineTo(18.2f)
                curveTo(19.64f, 4.4f, 20.8f, 5.56f, 20.8f, 7.0f)
                verticalLineTo(17.0f)
                curveTo(20.8f, 18.44f, 19.64f, 19.6f, 18.2f, 19.6f)
                horizontalLineTo(5.8f)
                curveTo(4.36f, 19.6f, 3.2f, 18.44f, 3.2f, 17.0f)
                verticalLineTo(7.0f)
                curveTo(3.2f, 5.56f, 4.36f, 4.4f, 5.8f, 4.4f)
                close()
                moveTo(3.6f, 16.5f)
                lineTo(8.8f, 11.4f)
                lineTo(13.2f, 15.6f)
                lineTo(16.0f, 12.9f)
                lineTo(20.5f, 17.2f)
                moveTo(17.0f, 8.7f)
                curveTo(17.0f, 9.47f, 16.37f, 10.1f, 15.6f, 10.1f)
                curveTo(14.83f, 10.1f, 14.2f, 9.47f, 14.2f, 8.7f)
                curveTo(14.2f, 7.93f, 14.83f, 7.3f, 15.6f, 7.3f)
                curveTo(16.37f, 7.3f, 17.0f, 7.93f, 17.0f, 8.7f)
                close()
            }
        }.build()
    }

    /** 链接前缀：线性微博眼睛与双波纹图标，用于「微博正文」。 */
    val Weibo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Weibo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10.5f, 19.6f)
                curveTo(6.1f, 19.6f, 2.8f, 17.5f, 2.8f, 14.3f)
                curveTo(2.8f, 12.1f, 4.7f, 9.4f, 7.6f, 7.7f)
                curveTo(9.8f, 6.4f, 11.7f, 6.9f, 11.1f, 8.8f)
                curveTo(10.8f, 9.6f, 11.4f, 10.0f, 12.4f, 9.6f)
                curveTo(14.6f, 8.7f, 16.5f, 9.3f, 16.0f, 11.0f)
                curveTo(15.8f, 11.7f, 16.2f, 12.1f, 17.0f, 12.5f)
                curveTo(18.0f, 13.1f, 18.4f, 14.0f, 18.4f, 14.9f)
                curveTo(18.4f, 17.7f, 14.9f, 19.6f, 10.5f, 19.6f)
                close()
                moveTo(12.4f, 14.7f)
                curveTo(12.4f, 15.95f, 11.4f, 16.95f, 10.2f, 16.95f)
                curveTo(9.0f, 16.95f, 8.0f, 15.95f, 8.0f, 14.7f)
                curveTo(8.0f, 13.45f, 9.0f, 12.45f, 10.2f, 12.45f)
                curveTo(11.4f, 12.45f, 12.4f, 13.45f, 12.4f, 14.7f)
                close()
                moveTo(15.2f, 6.7f)
                curveTo(17.1f, 6.7f, 18.7f, 8.3f, 18.7f, 10.2f)
                moveTo(15.4f, 3.8f)
                curveTo(18.8f, 3.8f, 21.5f, 6.5f, 21.5f, 9.9f)
            }
        }.build()
    }

    /** 实况图声音开启：线性扬声器与双声波。 */
    val VolumeOn: ImageVector by lazy {
        outline("VolumeOn") {
            moveTo(11.0f, 5.5f)
            lineTo(6.8f, 9.0f)
            horizontalLineTo(3.8f)
            verticalLineTo(15.0f)
            horizontalLineTo(6.8f)
            lineTo(11.0f, 18.5f)
            close()
            moveTo(14.8f, 8.8f)
            curveTo(16.2f, 10.2f, 16.2f, 13.8f, 14.8f, 15.2f)
            moveTo(17.6f, 6.2f)
            curveTo(20.1f, 9.1f, 20.1f, 14.9f, 17.6f, 17.8f)
        }
    }

    /** 实况图静音：线性扬声器与叉号。 */
    val VolumeOff: ImageVector by lazy {
        outline("VolumeOff") {
            moveTo(11.0f, 5.5f)
            lineTo(6.8f, 9.0f)
            horizontalLineTo(3.8f)
            verticalLineTo(15.0f)
            horizontalLineTo(6.8f)
            lineTo(11.0f, 18.5f)
            close()
            moveTo(15.2f, 9.6f)
            lineTo(19.8f, 14.4f)
            moveTo(19.8f, 9.6f)
            lineTo(15.2f, 14.4f)
        }
    }

    /** 关注用户：人形轮廓 + 右侧加号。 */
    val Follow: ImageVector by lazy {
        outline("Follow") {
            moveTo(9.5f, 4.5f)
            curveTo(11.32f, 4.5f, 12.8f, 5.98f, 12.8f, 7.8f)
            curveTo(12.8f, 9.62f, 11.32f, 11.1f, 9.5f, 11.1f)
            curveTo(7.68f, 11.1f, 6.2f, 9.62f, 6.2f, 7.8f)
            curveTo(6.2f, 5.98f, 7.68f, 4.5f, 9.5f, 4.5f)
            close()
            moveTo(3.5f, 19.5f)
            curveTo(3.5f, 16.0f, 6.1f, 13.8f, 9.5f, 13.8f)
            curveTo(12.9f, 13.8f, 15.5f, 16.0f, 15.5f, 19.5f)
            moveTo(18.5f, 8.8f)
            verticalLineTo(14.2f)
            moveTo(15.8f, 11.5f)
            horizontalLineTo(21.2f)
        }
    }

    /** 取消关注用户：人形轮廓 + 右侧减号。 */
    val Unfollow: ImageVector by lazy {
        outline("Unfollow") {
            moveTo(9.5f, 4.5f)
            curveTo(11.32f, 4.5f, 12.8f, 5.98f, 12.8f, 7.8f)
            curveTo(12.8f, 9.62f, 11.32f, 11.1f, 9.5f, 11.1f)
            curveTo(7.68f, 11.1f, 6.2f, 9.62f, 6.2f, 7.8f)
            curveTo(6.2f, 5.98f, 7.68f, 4.5f, 9.5f, 4.5f)
            close()
            moveTo(3.5f, 19.5f)
            curveTo(3.5f, 16.0f, 6.1f, 13.8f, 9.5f, 13.8f)
            curveTo(12.9f, 13.8f, 15.5f, 16.0f, 15.5f, 19.5f)
            moveTo(15.8f, 11.5f)
            horizontalLineTo(21.2f)
        }
    }

    /** 应用内视频小窗：外框 + 右下角小窗。 */
    val FloatingWindow: ImageVector by lazy {
        outline("FloatingWindow") {
            moveTo(4.5f, 5.2f)
            horizontalLineTo(19.5f)
            curveTo(20.4f, 5.2f, 21.0f, 5.8f, 21.0f, 6.7f)
            verticalLineTo(17.3f)
            curveTo(21.0f, 18.2f, 20.4f, 18.8f, 19.5f, 18.8f)
            horizontalLineTo(4.5f)
            curveTo(3.6f, 18.8f, 3.0f, 18.2f, 3.0f, 17.3f)
            verticalLineTo(6.7f)
            curveTo(3.0f, 5.8f, 3.6f, 5.2f, 4.5f, 5.2f)
            close()
            moveTo(12.6f, 11.6f)
            horizontalLineTo(18.2f)
            verticalLineTo(16.2f)
            horizontalLineTo(12.6f)
            close()
        }
    }

    /** 系统画中画：外框 + 指向右下角子窗的箭头。 */
    val PictureInPicture: ImageVector by lazy {
        outline("PictureInPicture") {
            moveTo(19.0f, 10.6f)
            verticalLineTo(6.7f)
            curveTo(19.0f, 5.8f, 18.4f, 5.2f, 17.5f, 5.2f)
            horizontalLineTo(4.5f)
            curveTo(3.6f, 5.2f, 3.0f, 5.8f, 3.0f, 6.7f)
            verticalLineTo(17.3f)
            curveTo(3.0f, 18.2f, 3.6f, 18.8f, 4.5f, 18.8f)
            horizontalLineTo(10.0f)
            moveTo(13.0f, 13.0f)
            horizontalLineTo(20.5f)
            verticalLineTo(18.8f)
            horizontalLineTo(13.0f)
            close()
            moveTo(7.2f, 8.8f)
            lineTo(10.8f, 12.2f)
            moveTo(10.8f, 9.4f)
            verticalLineTo(12.2f)
            horizontalLineTo(8.0f)
        }
    }

    /** 还原全屏：左下与右上对角双向展开箭头。 */
    val ExpandFullscreen: ImageVector by lazy {
        outline("ExpandFullscreen") {
            moveTo(14.0f, 4.8f)
            horizontalLineTo(19.2f)
            verticalLineTo(10.0f)
            moveTo(19.2f, 4.8f)
            lineTo(13.4f, 10.6f)
            moveTo(10.0f, 19.2f)
            horizontalLineTo(4.8f)
            verticalLineTo(14.0f)
            moveTo(4.8f, 19.2f)
            lineTo(10.6f, 13.4f)
        }
    }

    /** 稍后再看：书签轮廓。 */
    val Bookmark: ImageVector by lazy {
        outline("Bookmark") {
            moveTo(6.4f, 4.2f)
            horizontalLineTo(17.6f)
            curveTo(18.5f, 4.2f, 19.1f, 4.8f, 19.1f, 5.7f)
            verticalLineTo(19.8f)
            lineTo(12.0f, 15.6f)
            lineTo(4.9f, 19.8f)
            verticalLineTo(5.7f)
            curveTo(4.9f, 4.8f, 5.5f, 4.2f, 6.4f, 4.2f)
            close()
        }
    }

    /** 微博账号收藏：云朵轮廓，用来与星形本地收藏区分。 */
    val Cloud: ImageVector by lazy {
        outline("Cloud") {
            moveTo(7.2f, 18.2f)
            horizontalLineTo(17.1f)
            curveTo(19.2f, 18.2f, 20.8f, 16.6f, 20.8f, 14.6f)
            curveTo(20.8f, 12.7f, 19.4f, 11.2f, 17.5f, 11.0f)
            curveTo(17.0f, 7.9f, 14.3f, 5.8f, 11.2f, 5.8f)
            curveTo(8.1f, 5.8f, 5.6f, 8.1f, 5.3f, 11.1f)
            curveTo(3.9f, 11.5f, 3.0f, 12.8f, 3.0f, 14.4f)
            curveTo(3.0f, 16.5f, 4.8f, 18.2f, 7.2f, 18.2f)
            close()
        }
    }

    /** 已收藏到微博账号：实心云朵。 */
    val CloudFill: ImageVector by lazy {
        solid("CloudFill") {
            moveTo(7.2f, 18.5f)
            horizontalLineTo(17.1f)
            curveTo(19.4f, 18.5f, 21.1f, 16.8f, 21.1f, 14.6f)
            curveTo(21.1f, 12.6f, 19.6f, 10.9f, 17.6f, 10.7f)
            curveTo(17.0f, 7.5f, 14.3f, 5.4f, 11.2f, 5.4f)
            curveTo(8.0f, 5.4f, 5.4f, 7.8f, 5.1f, 10.9f)
            curveTo(3.6f, 11.4f, 2.7f, 12.8f, 2.7f, 14.4f)
            curveTo(2.7f, 16.7f, 4.6f, 18.5f, 7.2f, 18.5f)
            close()
        }
    }

    /** 已加入稍后再看：实心书签。 */
    val BookmarkFill: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookmarkFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.2f, 3.6f)
                horizontalLineTo(17.8f)
                curveTo(18.9f, 3.6f, 19.6f, 4.3f, 19.6f, 5.4f)
                verticalLineTo(20.1f)
                curveTo(19.6f, 20.6f, 19.0f, 20.9f, 18.6f, 20.6f)
                lineTo(12.0f, 16.7f)
                lineTo(5.4f, 20.6f)
                curveTo(5.0f, 20.9f, 4.4f, 20.6f, 4.4f, 20.1f)
                verticalLineTo(5.4f)
                curveTo(4.4f, 4.3f, 5.1f, 3.6f, 6.2f, 3.6f)
                close()
            }
        }.build()
    }
}
