package com.example.ui.screens.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.AvatarImage

@Composable
fun StatusRingAvatar(
    uri: String?,
    name: String,
    statusCount: Int,
    isAllViewed: Boolean = false,
    avatarSize: Dp = 52.dp,
    ringColor: Color = if (isAllViewed) Color.Gray.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val ringPadding = 4.dp
    val totalSize = avatarSize + (ringPadding * 2)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(totalSize)
    ) {
        if (statusCount > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val centerOffset = center

                if (statusCount == 1) {
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    val gapDegrees = 6f
                    val sweepDegrees = (360f - (gapDegrees * statusCount)) / statusCount.toFloat()
                    var startAngle = -90f

                    for (i in 0 until statusCount) {
                        drawArc(
                            color = ringColor,
                            startAngle = startAngle + (gapDegrees / 2f),
                            sweepAngle = sweepDegrees,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweepDegrees + gapDegrees
                    }
                }
            }
        }

        AvatarImage(
            uri = uri,
            name = name,
            size = avatarSize
        )
    }
}
