package dev.gurk.opencarta.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.data.Achievement
import dev.gurk.opencarta.data.AchievementsSummary
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun AchievementsScreen(summary: AchievementsSummary) {
    val unlockedCount = summary.achievements.count { it.unlocked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements",
                color = CartaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$unlockedCount / ${summary.achievements.size} Unlocked",
                    color = CartaColors.Mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Achievement rows
        summary.achievements.forEach { item ->
            AchievementRow(item = item)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AchievementRow(item: Achievement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Badge Emblem
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            val ringColor = if (item.unlocked) CartaColors.Mint else CartaColors.TextMuted.copy(alpha = 0.6f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0xFF1A1D20))
                drawCircle(color = ringColor, style = Stroke(width = 2.5.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (item.isStreak) Icons.Default.CalendarToday else Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (item.unlocked) CartaColors.Mint else CartaColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.badgeSub,
                    color = if (item.unlocked) CartaColors.Mint else CartaColors.TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title, Subtitle, Progress Bar
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = CartaColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (item.unlocked) "Unlocked" else item.subtitle,
                color = if (item.unlocked) CartaColors.Mint else CartaColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = CartaColors.Mint,
                trackColor = CartaColors.SurfaceElevated
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Counter + Check
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (item.unlocked) CartaColors.Mint.copy(alpha = 0.2f) else CartaColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (item.unlocked) CartaColors.Mint else CartaColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${item.progress}/${item.target}",
                color = CartaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
