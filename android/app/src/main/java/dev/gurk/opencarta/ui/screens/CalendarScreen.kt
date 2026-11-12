package dev.gurk.opencarta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.data.AchievementsSummary
import dev.gurk.opencarta.data.CurveRepository
import dev.gurk.opencarta.data.SessionHistoryState
import dev.gurk.opencarta.ui.theme.CartaColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    history: SessionHistoryState,
    achievements: AchievementsSummary,
    isFahrenheit: Boolean,
    isConnected: Boolean,
    onSyncHistory: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }

    val monthStats = remember(history, currentMonth) { history.statsForMonth(currentMonth) }
    val isCurrentMonth = currentMonth == YearMonth.from(today)
    val displayMonthTotal = if (isCurrentMonth) maxOf(monthStats.totalDabs, achievements.monthlyDabs) else monthStats.totalDabs

    val selectedCount = history.countForDay(selectedDate)
    val selectedSessions = remember(history, selectedDate) { history.sessionsForDay(selectedDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calendar",
                color = CartaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CartaColors.Surface)
                            .border(1.dp, CartaColors.Border, RoundedCornerShape(50))
                            .clickable(onClick = onSyncHistory)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Sync", color = CartaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CartaColors.Surface)
                        .border(1.dp, CartaColors.Border, RoundedCornerShape(50))
                        .clickable {
                            currentMonth = YearMonth.from(today)
                            selectedDate = today
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Today", color = CartaColors.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Calendar Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Month Navigator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Month", tint = CartaColors.TextPrimary)
                }
                val monthTitle = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${currentMonth.year}"
                Text(
                    text = monthTitle,
                    color = CartaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = CartaColors.TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Day of Week Headers (Mon..Sun)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { dow ->
                    Text(
                        text = dow,
                        color = CartaColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            val firstOfMonth = currentMonth.atDay(1)
            val startOffset = firstOfMonth.dayOfWeek.value - 1 // Monday=0 .. Sunday=6
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - startOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = currentMonth.atDay(dayNum)
                                val count = history.countForDay(date)
                                val isSelected = date == selectedDate
                                val isToday = date == today

                                val heatAlpha = when {
                                    count <= 0 -> 0f
                                    count == 1 -> 0.16f
                                    count <= 3 -> 0.28f
                                    else -> 0.42f
                                }
                                val bgColor = if (count > 0) {
                                    CartaColors.Mint.copy(alpha = heatAlpha)
                                } else {
                                    CartaColors.SurfaceElevated.copy(alpha = 0.55f)
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgColor)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = when {
                                                isSelected -> CartaColors.Mint
                                                isToday -> CartaColors.TextSecondary.copy(alpha = 0.6f)
                                                else -> Color.Transparent
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDate = date }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isSelected || count > 0) CartaColors.TextPrimary else CartaColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CartaColors.Mint)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$count",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(46.dp))
                            }
                        }
                    }
                }
            }
        }

        // Extensive Monthly Stats Section
        Text(
            text = "MONTHLY STATS",
            color = CartaColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        val unitStr = if (isFahrenheit) "°F" else "°C"
        val avgTempDisp = monthStats.avgTempC?.let {
            val v = if (isFahrenheit) CurveRepository.cToF(it) else it
            "$v$unitStr"
        } ?: "--"
        val avgDurDisp = monthStats.avgDurationSec?.let { "${it}s" } ?: "--"
        val peakDisp = if (monthStats.peakCount > 0 && monthStats.peakDate != null) {
            val d = LocalDate.parse(monthStats.peakDate)
            "${monthStats.peakCount} (${d.monthValue}/${d.dayOfMonth})"
        } else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonthStatBox("TOTAL DABS", "$displayMonthTotal", Modifier.weight(1f))
            MonthStatBox("ACTIVE DAYS", "${monthStats.activeDays}/${currentMonth.lengthOfMonth()}", Modifier.weight(1f))
            MonthStatBox("DAILY AVG", String.format(Locale.US, "%.1f", monthStats.avgPerActiveDay), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonthStatBox("PEAK DAY", peakDisp, Modifier.weight(1f))
            MonthStatBox("AVG TEMP", avgTempDisp, Modifier.weight(1f))
            MonthStatBox("AVG SESH", avgDurDisp, Modifier.weight(1f))
        }

        // Selected Day Breakdown
        val dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateLabel, color = CartaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CartaColors.Mint.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$selectedCount ${if (selectedCount == 1) "dab" else "dabs"}",
                        color = CartaColors.Mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedSessions.isEmpty()) {
                Text(
                    text = if (selectedCount > 0) {
                        "$selectedCount session(s) recorded by the device counter on this day."
                    } else {
                        "No sessions recorded on this day."
                    },
                    color = CartaColors.TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                selectedSessions.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CartaColors.SurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(CartaColors.Mint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = CartaColors.Mint, modifier = Modifier.size(15.dp))
                            }
                            Column {
                                Text("${s.temp}${s.unitSymbol}  •  ${s.durationSec}s", color = CartaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(s.source, color = CartaColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                        Text(s.time, color = CartaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun MonthStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CartaColors.Surface)
            .border(1.dp, CartaColors.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = CartaColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = CartaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

