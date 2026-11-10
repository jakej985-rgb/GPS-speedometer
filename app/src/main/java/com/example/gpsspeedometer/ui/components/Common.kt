package com.example.gpsspeedometer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedDisplay(
    mph: Float,
    kmh: Float,
    useMph: Boolean,
    isAlert: Boolean = false,
    modifier: Modifier = Modifier
) {
    val displayColor = if (isAlert) Color.Red else MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (useMph) {
            // Large MPH
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold)) {
                        append(String.format("%.0f", mph))
                    }
                    withStyle(style = SpanStyle(fontSize = 24.sp, baselineShift = BaselineShift.Superscript)) {
                        append(" MPH")
                    }
                },
                color = displayColor
            )
            // Smaller KM/H
            Text(
                text = String.format("%.0f km/h", kmh),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
             // Large KM/H
             Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold)) {
                        append(String.format("%.0f", kmh))
                    }
                    withStyle(style = SpanStyle(fontSize = 24.sp, baselineShift = BaselineShift.Superscript)) {
                        append(" KM/H")
                    }
                },
                color = displayColor
            )
            // Smaller MPH
            Text(
                text = String.format("%.0f mph", mph),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun InfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SpeedChart(
    history: List<Pair<Long, Float>>, // Timestamp to MPH
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val pathColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Find min/max for scaling
        val maxSpeed = history.maxOfOrNull { it.second } ?: 10f
        val maxY = if (maxSpeed < 10f) 10f else maxSpeed * 1.2f

        val minTime = history.minOfOrNull { it.first } ?: 0L
        val maxTime = history.maxOfOrNull { it.first } ?: 0L
        val timeRange = (maxTime - minTime).coerceAtLeast(1)

        val path = Path()

        history.forEachIndexed { index, point ->
            val x = ((point.first - minTime).toFloat() / timeRange) * width
            val y = height - ((point.second / maxY) * height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = pathColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
