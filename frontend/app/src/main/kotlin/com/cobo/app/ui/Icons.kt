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

    /** 超话前缀：实心菱形钻石。 */
    val SuperTopic: ImageVector by lazy {
        ImageVector.Builder(
            name = "SuperTopic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 2.6f)
                lineTo(21.2f, 9.6f)
                lineTo(12f, 21.4f)
                lineTo(2.8f, 9.6f)
                close()
            }
        }.build()
    }

    /** 视频前缀：圆角矩形挖出一个播放三角。 */
    val VideoBadge: ImageVector by lazy {
        ImageVector.Builder(
            name = "VideoBadge",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(8.4f, 5.2f)
                horizontalLineTo(15.6f)
                curveTo(17.9f, 5.2f, 19.6f, 6.9f, 19.6f, 9.2f)
                verticalLineTo(14.8f)
                curveTo(19.6f, 17.1f, 17.9f, 18.8f, 15.6f, 18.8f)
                horizontalLineTo(8.4f)
                curveTo(6.1f, 18.8f, 4.4f, 17.1f, 4.4f, 14.8f)
                verticalLineTo(9.2f)
                curveTo(4.4f, 6.9f, 6.1f, 5.2f, 8.4f, 5.2f)
                close()
                moveTo(10.5f, 9.1f)
                lineTo(15.3f, 12f)
                lineTo(10.5f, 14.9f)
                close()
            }
        }.build()
    }
}
