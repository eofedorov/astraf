package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astraf.hrgpslogger.ui.theme.RideHeartRateCardContainer
import com.astraf.hrgpslogger.ui.theme.RideHeartRateCardOnContainer
import com.astraf.hrgpslogger.ui.theme.RideSpeedCardContainer
import com.astraf.hrgpslogger.ui.theme.RideSpeedCardOnContainer
import kotlin.math.min

private const val CardBackgroundAlpha = 0.72f
private val CardHeight = 40.dp
private val CardCornerRadius = 12.dp
private val CardWidthFraction = 0.22f
private const val PreviewValueText = "99.9"
private const val PreviewHeartRateText = "199"

@Composable
fun RideTopMetricCards(
    speedValue: String,
    speedUnit: String?,
    heartRateValue: String,
    heartRateUnit: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RideMetricOverlayCard(
            value = speedValue,
            unit = speedUnit,
            previewText = PreviewValueText,
            containerColor = RideSpeedCardContainer,
            contentColor = RideSpeedCardOnContainer,
            contentDescription = buildString {
                append(speedValue)
                speedUnit?.let { append(' ').append(it) }
            },
            modifier = Modifier.fillMaxWidth(CardWidthFraction),
        )
        RideMetricOverlayCard(
            value = heartRateValue,
            unit = heartRateUnit,
            previewText = PreviewHeartRateText,
            containerColor = RideHeartRateCardContainer,
            contentColor = RideHeartRateCardOnContainer,
            contentDescription = buildString {
                append(heartRateValue)
                heartRateUnit?.let { append(' ').append(it) }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(10.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(CardWidthFraction),
        )
    }
}

@Composable
private fun RideMetricOverlayCard(
    value: String,
    unit: String?,
    previewText: String,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .height(CardHeight)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(CardCornerRadius),
        color = containerColor.copy(alpha = CardBackgroundAlpha),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val iconWidthPx = if (trailingIcon != null) {
                with(density) { 12.dp.roundToPx() }
            } else {
                0
            }
            val unitText = unit?.uppercase().orEmpty()
            val unitWidthPx = if (unitText.isNotEmpty()) {
                with(density) { 28.dp.roundToPx() }
            } else {
                0
            }
            val valueMaxWidthPx = (constraints.maxWidth - unitWidthPx - iconWidthPx).coerceAtLeast(1)
            val valueMaxHeightPx = constraints.maxHeight.coerceAtLeast(1)
            val valueFontSize = rememberFittingOverlayValueFontSize(
                previewText = previewText,
                maxWidthPx = valueMaxWidthPx,
                maxHeightPx = valueMaxHeightPx,
            )
            val valueStyle = overlayValueTextStyle(valueFontSize, contentColor)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = value,
                    style = valueStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
                if (unitText.isNotEmpty()) {
                    Text(
                        text = unitText,
                        color = contentColor.copy(alpha = 0.9f),
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                trailingIcon?.invoke()
            }
        }
    }
}

@Composable
private fun rememberFittingOverlayValueFontSize(
    previewText: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
    maxSp: Float = 18f,
    minSp: Float = 12f,
): Float {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    return remember(previewText, maxWidthPx, maxHeightPx) {
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return@remember minSp
        var size = min(maxWidthPx / previewText.length.toFloat(), maxHeightPx * 0.9f) / density
        size = size.coerceIn(minSp, maxSp)
        val constraints = Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx)
        while (size >= minSp) {
            val result = textMeasurer.measure(
                text = previewText,
                style = overlayValueTextStyle(size, Color.White),
                constraints = constraints,
            )
            if (result.size.width <= maxWidthPx && result.size.height <= maxHeightPx) {
                break
            }
            size -= 0.5f
        }
        size.coerceAtLeast(minSp)
    }
}

private fun overlayValueTextStyle(fontSizeSp: Float, color: Color): TextStyle =
    TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp + 2f).sp,
        fontWeight = FontWeight.Bold,
        color = color,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
