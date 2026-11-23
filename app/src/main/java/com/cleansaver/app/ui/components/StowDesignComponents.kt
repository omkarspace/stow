package com.cleansaver.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineMuted

/**
 * Signature BRIK-inspired Top Wordmark Block:
 * Deep pine rounded block with "STOW®" on the left and a lavender pill badge on the right.
 */
@Composable
fun StowTopBarBlock(
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STOW®",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.2.sp
                ),
                color = StowInkOnDark
            )
            StowLavenderBadge(text = badgeText)
        }
    }
}

/**
 * Deep pine modular rounded block (28dp radius) sitting on the light bone canvas.
 */
@Composable
fun StowDarkBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            content = content
        )
    }
}

/**
 * Signature Lavender Pill Badge (#CFC7FA with deep pine uppercase text).
 */
@Composable
fun StowLavenderBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = StowLavender
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StowPineDark,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * High-contrast dark pill badge for use on the light bone canvas (e.g. "7 PLATFORMS", "ZERO ADS").
 */
@Composable
fun StowDarkBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = StowPineDark
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StowInkOnDark,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

/**
 * Signature BRIK-style vertical segmented progress bar:
 * Big crisp percentage numeral paired with vertical rounded lavender bars.
 */
@Composable
fun StowSegmentedProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    segmentCount: Int = 14
) {
    val clamped = percent.coerceIn(0, 100)
    val filledCount = ((clamped / 100f) * segmentCount).toInt().coerceIn(0, segmentCount)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$clamped%",
            style = MaterialTheme.typography.displaySmall,
            color = StowInkOnDark
        )
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until segmentCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < filledCount) StowLavender else StowPineMuted)
                )
            }
        }
    }
}

/**
 * Paired dark bento stat tile (matches bottom "Best score" / "Reaction speed" cards).
 */
@Composable
fun StowStatTile(
    title: String,
    subtitle: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = StowInkOnDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = StowInkMutedOnDark
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = StowInkOnDark
            )
        }
    }
}

/**
 * Crisp dark square-rounded CTA button (matches "START" button on the light canvas).
 */
@Composable
fun StowActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StowPineDark,
            contentColor = StowInkOnDark,
            disabledContainerColor = StowPineMuted,
            disabledContentColor = StowInkMutedOnDark
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
