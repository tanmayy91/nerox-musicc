package com.music.nerox.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.nerox.LocalPlayerAwareWindowInsets
import com.music.nerox.R
import com.music.nerox.db.entities.HourPlayTime
import com.music.nerox.viewmodels.DashboardViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val todayMs by viewModel.todayPlayTimeMs.collectAsState()
    val hourly by viewModel.hourlyPlayTime.collectAsState()
    val weekly by viewModel.weeklyPlayTimes.collectAsState()
    val yearMonthly by viewModel.yearMonthlyPlayTimes.collectAsState()
    val peakMonth by viewModel.peakMonth.collectAsState()
    val allTimeMs by viewModel.allTimePlayTimeMs.collectAsState()
    val allTimeSongs by viewModel.allTimeSongsCount.collectAsState()
    val allTimeArtists by viewModel.allTimeArtistsCount.collectAsState()

    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("blend") }) {
                        Icon(painterResource(R.drawable.group_outlined), contentDescription = "Blend")
                    }
                }
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TodayCard(todayMs = todayMs, accentColor = accentColor)
            }

            item {
                SectionCard(title = "TODAY BY HOUR") {
                    HourlyBarChart(hourly = hourly, accentColor = accentColor)
                }
            }

            item {
                SectionCard(title = "THIS WEEK") {
                    val today = LocalDate.now()
                    val dayLabels = (0..6).map { offset ->
                        today.minusDays((6 - offset).toLong())
                            .dayOfWeek
                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            .take(3)
                    }
                    WeeklyBarChart(
                        values = weekly,
                        labels = dayLabels,
                        accentColor = accentColor
                    )
                }
            }

            item {
                SectionCard(title = "THIS YEAR") {
                    val monthLabels = listOf("J","F","M","A","M","J","J","A","S","O","N","D")
                    YearlyBarChart(
                        values = yearMonthly,
                        labels = monthLabels,
                        accentColor = accentColor,
                        peakMonthIndex = yearMonthly.indices.maxByOrNull { yearMonthly[it] }
                    )
                    if (peakMonth != null) {
                        val monthNames = listOf(
                            "January","February","March","April","May","June",
                            "July","August","September","October","November","December"
                        )
                        val name = monthNames.getOrNull(peakMonth!!.month - 1) ?: ""
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔥", fontSize = 16.sp)
                            Text(
                                text = "Peak: $name ${peakMonth!!.year}  •  ${peakMonth!!.totalCount} plays",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                AllTimeCard(
                    totalMs = allTimeMs,
                    songs = allTimeSongs,
                    artists = allTimeArtists,
                    colorScheme = colorScheme
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TodayCard(todayMs: Long, accentColor: Color) {
    val hours = todayMs / 3_600_000L
    val minutes = (todayMs % 3_600_000L) / 60_000L
    val timeString = when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "listening time",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun HourlyBarChart(hourly: List<HourPlayTime>, accentColor: Color) {
    val hourMap = hourly.associateBy { it.hour }
    val maxMs = hourly.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val barColor = accentColor
    val emptyColor = accentColor.copy(alpha = 0.12f)
    val currentHour = java.time.LocalTime.now().hour

    BarChart(
        bars = (0..23).map { h ->
            BarData(
                value = (hourMap[h]?.totalMs ?: 0L).toFloat() / maxMs,
                label = if (h % 6 == 0) "${h}h" else "",
                isHighlighted = h == currentHour,
                isEmpty = (hourMap[h]?.totalMs ?: 0L) == 0L
            )
        },
        barColor = barColor,
        emptyColor = emptyColor,
        height = 100.dp
    )
}

@Composable
private fun WeeklyBarChart(values: List<Long>, labels: List<String>, accentColor: Color) {
    val maxMs = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val todayIndex = 6
    BarChart(
        bars = values.mapIndexed { i, v ->
            BarData(
                value = v.toFloat() / maxMs,
                label = labels.getOrElse(i) { "" },
                isHighlighted = i == todayIndex,
                isEmpty = v == 0L
            )
        },
        barColor = accentColor,
        emptyColor = accentColor.copy(alpha = 0.12f),
        height = 120.dp
    )
}

@Composable
private fun YearlyBarChart(
    values: List<Long>,
    labels: List<String>,
    accentColor: Color,
    peakMonthIndex: Int?
) {
    val maxMs = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val currentMonth = java.time.LocalDate.now().monthValue - 1
    BarChart(
        bars = values.mapIndexed { i, v ->
            BarData(
                value = v.toFloat() / maxMs,
                label = labels.getOrElse(i) { "" },
                isHighlighted = i == currentMonth,
                isPeak = i == peakMonthIndex,
                isEmpty = v == 0L
            )
        },
        barColor = accentColor,
        emptyColor = accentColor.copy(alpha = 0.12f),
        height = 120.dp
    )
}

data class BarData(
    val value: Float,
    val label: String,
    val isHighlighted: Boolean = false,
    val isPeak: Boolean = false,
    val isEmpty: Boolean = false
)

@Composable
private fun BarChart(
    bars: List<BarData>,
    barColor: Color,
    emptyColor: Color,
    height: Dp
) {
    val peakColor = Color(0xFFFF6B35)
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val labelAreaHeight = 20f
        val chartHeight = totalHeight - labelAreaHeight
        val barCount = bars.size
        val barSpacing = totalWidth * 0.015f
        val barWidth = (totalWidth - barSpacing * (barCount - 1)) / barCount
        val cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f)
        val minBarHeight = 4f

        bars.forEachIndexed { i, bar ->
            val x = i * (barWidth + barSpacing)
            val fillColor = when {
                bar.isPeak -> peakColor
                bar.isEmpty -> emptyColor
                else -> barColor
            }
            val barH = max(if (bar.isEmpty) minBarHeight else minBarHeight, bar.value * chartHeight)
            val y = chartHeight - barH

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = cornerRadius
            )

            if (bar.isHighlighted) {
                drawRoundRect(
                    color = fillColor.copy(alpha = 0.25f),
                    topLeft = Offset(x - 2, 0f),
                    size = Size(barWidth + 4, chartHeight),
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barH),
                    cornerRadius = cornerRadius
                )
            }

            if (bar.label.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    x + barWidth / 2,
                    totalHeight,
                    android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.6f).toArgb()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}

@Composable
private fun AllTimeCard(
    totalMs: Long,
    songs: Int,
    artists: Int,
    colorScheme: ColorScheme
) {
    val totalHours = totalMs / 3_600_000L
    val totalMinutes = (totalMs % 3_600_000L) / 60_000L
    val timeStr = if (totalHours > 0) "${totalHours}h ${totalMinutes}m" else "${totalMinutes}m"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ALL TIME",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(value = timeStr, label = "Listened")
                VerticalDivider(modifier = Modifier.height(48.dp))
                StatColumn(value = songs.toString(), label = "Songs")
                VerticalDivider(modifier = Modifier.height(48.dp))
                StatColumn(value = artists.toString(), label = "Artists")
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
