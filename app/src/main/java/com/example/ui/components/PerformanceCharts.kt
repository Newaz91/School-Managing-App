package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class RadarAxisMetric(
    val label: String,
    val percentage: Int
)

data class PieTierSlice(
    val label: String,
    val count: Int,
    val color: Color
)

data class ClassBarMetric(
    val shortClassLabel: String,
    val attendancePct: Int,
    val examAvgPct: Int
)

@Composable
fun FiveAngleRadarChart(
    axes: List<RadarAxisMetric>,
    modifier: Modifier = Modifier
) {
    val safeAxes = if (axes.size == 5) axes else List(5) { RadarAxisMetric("Metric ${it + 1}", 0) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(220.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = min(size.width, size.height) * 0.42f
                val angles = List(5) { idx -> (-90.0 + idx * 72.0) * PI / 180.0 }

                // Draw 5 concentric pentagons (20%..100%)
                for (ring in 1..5) {
                    val r = maxRadius * (ring / 5f)
                    val ringPath = Path()
                    angles.forEachIndexed { idx, angle ->
                        val px = cx + (r * cos(angle)).toFloat()
                        val py = cy + (r * sin(angle)).toFloat()
                        if (idx == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
                    }
                    ringPath.close()
                    drawPath(
                        path = ringPath,
                        color = gridColor,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // Draw 5 radial axis lines
                angles.forEach { angle ->
                    val ex = cx + (maxRadius * cos(angle)).toFloat()
                    val ey = cy + (maxRadius * sin(angle)).toFloat()
                    drawLine(
                        color = gridColor,
                        start = Offset(cx, cy),
                        end = Offset(ex, ey),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw performance pentagon polygon
                val dataPath = Path()
                val vertices = angles.mapIndexed { idx, angle ->
                    val frac = (safeAxes[idx].percentage.coerceIn(0, 100)) / 100f
                    val r = maxRadius * maxOf(frac, 0.04f)
                    Offset(
                        x = cx + (r * cos(angle)).toFloat(),
                        y = cy + (r * sin(angle)).toFloat()
                    )
                }
                vertices.forEachIndexed { idx, pt ->
                    if (idx == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
                }
                dataPath.close()

                drawPath(
                    path = dataPath,
                    color = primaryColor.copy(alpha = 0.28f)
                )
                drawPath(
                    path = dataPath,
                    color = primaryColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                vertices.forEach { pt ->
                    drawCircle(
                        color = secondaryColor,
                        radius = 4.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        // 5-Angle Pill Legend below pentagon for crystal-clear reading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            safeAxes.forEach { axis ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${axis.percentage}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = axis.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerformancePieChart(
    slices: List<PieTierSlice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(142.dp)) {
                val strokeWidth = 26.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                if (slices.all { it.count == 0 }) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var startAngle = -90f
                    slices.filter { it.count > 0 }.forEach { slice ->
                        val sweep = (slice.count.toFloat() / total.toFloat()) * 360f
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                val pct = if (slices.sumOf { it.count } > 0) {
                    (slice.count * 100) / total
                } else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(slice.color, CircleShape)
                    )
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${slice.count} ($pct%)",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ClassComparisonBarChart(
    bars: List<ClassBarMetric>,
    modifier: Modifier = Modifier
) {
    val attColor = MaterialTheme.colorScheme.primary
    val examColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            if (bars.isEmpty()) return@Canvas
            val groupWidth = size.width / bars.size
            val barWidth = (groupWidth * 0.28f).coerceAtMost(24.dp.toPx())
            val chartHeight = size.height - 16.dp.toPx()

            // Horizontal reference lines
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val y = chartHeight * (1f - frac) + 8.dp.toPx()
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            bars.forEachIndexed { idx, bar ->
                val centerX = groupWidth * idx + groupWidth / 2f
                val attHeight = chartHeight * (bar.attendancePct.coerceIn(0, 100) / 100f)
                val examHeight = chartHeight * (bar.examAvgPct.coerceIn(0, 100) / 100f)

                drawRect(
                    color = attColor,
                    topLeft = Offset(centerX - barWidth - 2.dp.toPx(), chartHeight - attHeight + 8.dp.toPx()),
                    size = Size(barWidth, attHeight)
                )
                drawRect(
                    color = examColor,
                    topLeft = Offset(centerX + 2.dp.toPx(), chartHeight - examHeight + 8.dp.toPx()),
                    size = Size(barWidth, examHeight)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.shortClassLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(attColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("Attendance %", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(examColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("Exam Performance %", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

data class HorizontalBarMetric(
    val label: String,
    val percentage: Int,
    val subtitle: String = ""
)

@Composable
fun HorizontalMetricBarChart(
    items: List<HorizontalBarMetric>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val pct = item.percentage.coerceIn(0, 100)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.subtitle.isNotBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "$pct%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = barColor
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    val fillW = size.width * (pct / 100f)
                    if (fillW > 0f) {
                        drawRoundRect(
                            color = barColor,
                            size = Size(fillW, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
