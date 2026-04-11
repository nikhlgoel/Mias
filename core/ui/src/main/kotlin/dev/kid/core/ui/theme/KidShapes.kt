package dev.kid.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * {Kid} Shape System — rounded corners that feel organic.
 */
object KidShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val ExtraLarge = RoundedCornerShape(32.dp)
    val Full = RoundedCornerShape(50)

    /** Message bubble shapes with directional corners. */
    val BubbleUser = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 4.dp,
    )

    val BubbleKid = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 4.dp,
        bottomEnd = 20.dp,
    )

    /** Glass panel shape. */
    val Glass = RoundedCornerShape(20.dp)

    /** Bottom sheet handle. */
    val BottomSheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}
