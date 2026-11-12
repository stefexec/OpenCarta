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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.CartaState
import dev.gurk.opencarta.data.*
import dev.gurk.opencarta.ui.components.BatteryBadge
import dev.gurk.opencarta.ui.components.CartaDeviceGraphic
import dev.gurk.opencarta.ui.components.CartaSleekSlider
import dev.gurk.opencarta.ui.components.OpenCartaBrandLogo
import dev.gurk.opencarta.ui.theme.CartaColors
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: CartaState,
    achievements: AchievementsSummary,
    onOpenLedEditor: () -> Unit,
    onOpenSeshEditor: () -> Unit,
    onOpenPacketLog: () -> Unit,
    onReconnectClick: () -> Unit,
    onTempLiveChange: (Int) -> Unit,
    onTimerLiveChange: (Int) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    val scrollState = rememberScrollState()
    val minTemp = if (state.isFahrenheit) 300 else 150
    val maxTemp = if (state.isFahrenheit) 650 else 340

    var sliderTemp by remember(state.waxTemp, state.unitByte) { mutableIntStateOf(state.waxTemp) }
    var sliderTimer by remember(state.waxTimerSec) { mutableIntStateOf(state.waxTimerSec.coerceIn(10, 120)) }

    // Debounced live preview (0xcc with action=0x00, slot=0x00)
    LaunchedEffect(sliderTemp) {
        if (sliderTemp != state.waxTemp && state.isConnected) {
            delay(150)
            onTempLiveChange(sliderTemp)
        }
    }

    val activeLedColors = state.ledPresets[state.activeLedPreset]?.composeColors()
        ?: listOf(CartaColors.Mint, CartaColors.Blue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OpenCartaBrandLogo()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BatteryBadge(state.batteryPercent, state.isConnected)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CartaColors.SurfaceElevated)
                        .clickable(onClick = onOpenPacketLog),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "BLE Log",
                        tint = CartaColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Hero Section: Device Graphic + Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CartaDeviceGraphic(
                modifier = Modifier
                    .width(142.dp)
                    .height(198.dp),
                ledColors = activeLedColors,
                ledsEnabled = state.activeLedPreset > 0,
                isHeating = state.isSessionActive
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SPORT", color = CartaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("iNTELLi-CORE", color = CartaColors.TextSecondary, fontSize = 11.sp, letterSpacing = 0.8.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !state.isConnected, onClick = onReconnectClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (state.isConnected) CartaColors.Mint else CartaColors.Warning,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (state.isConnected) "CONNECTED" else "CONNECT",
                            color = if (state.isConnected) CartaColors.Mint else CartaColors.Warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // 2x2 Stats Grid
                val numFmt = remember { NumberFormat.getNumberInstance(Locale.US) }
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("DAILY", "${achievements.dailyDabs}", "DABS", Icons.Default.WbSunny, Modifier.weight(1f))
                    StatCell("MONTHLY", "${achievements.monthlyDabs}", "DABS", Icons.Default.CalendarMonth, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("ALL TIME", numFmt.format(achievements.allTimeDabs), "DABS", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                    StatCell("STREAK", "${achievements.currentStreakDays}", "DAYS", Icons.Default.AutoAwesome, Modifier.weight(1f))
                }

                // Live Temp Pill
                val tempPhase = when {
                    state.isPreheating -> "PRE-HEAT ${state.remainingSec}s"
                    state.isSessionActive -> "ACTIVE ${state.remainingSec}s"
                    else -> "AMBIENT"
                }
                val tempDotColor = when {
                    state.isSessionActive -> CartaColors.Danger
                    state.isConnected -> CartaColors.Mint
                    else -> CartaColors.TextMuted
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CartaColors.Surface)
                        .border(1.dp, CartaColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(tempDotColor))
                        Text("LIVE TEMP", color = CartaColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = if (state.isConnected) "${state.currentTemp}${state.unitSymbol}" else "--${state.unitSymbol}",
                        color = CartaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(tempPhase, color = CartaColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // LED PRESET & SESH PRESET Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LED Preset Card
            val ledBorderColor = activeLedColors.firstOrNull() ?: CartaColors.Danger
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenLedEditor)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LED\nPRESET", color = CartaColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = if (state.activeLedPreset == 0) CartaColors.TextMuted else ledBorderColor,
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.activeLedPreset == 0) "OFF" else "${state.activeLedPreset}",
                        color = CartaColors.TextPrimary,
                        fontSize = if (state.activeLedPreset == 0) 11.sp else 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CartaColors.TextSecondary, modifier = Modifier.size(18.dp))
            }

            // Sesh Preset Card
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenSeshEditor)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SESH\nPRESET", color = CartaColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(2.dp, CartaColors.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${state.activeHeatPreset}",
                        color = CartaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CartaColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // Temperature Slider Row
        SliderControlBlock(
            label = "TEMPERATURE",
            valueText = "$sliderTemp°",
            value = sliderTemp.toFloat(),
            valueRange = minTemp.toFloat()..maxTemp.toFloat(),
            onMinus = {
                sliderTemp = (sliderTemp - 1).coerceIn(minTemp, maxTemp)
            },
            onPlus = {
                sliderTemp = (sliderTemp + 1).coerceIn(minTemp, maxTemp)
            },
            onValueChange = { sliderTemp = it.roundToInt() }
        )

        // Duration Slider Row
        val mins = sliderTimer / 60
        val secs = sliderTimer % 60
        SliderControlBlock(
            label = "DURATION",
            valueText = "$mins:${secs.toString().padStart(2, '0')}",
            value = sliderTimer.toFloat(),
            valueRange = 10f..120f,
            onMinus = {
                sliderTimer = (sliderTimer - 5).coerceIn(10, 120)
                onTimerLiveChange(sliderTimer)
            },
            onPlus = {
                sliderTimer = (sliderTimer + 5).coerceIn(10, 120)
                onTimerLiveChange(sliderTimer)
            },
            onValueChange = {
                sliderTimer = it.roundToInt()
            },
            onValueChangeFinished = {
                onTimerLiveChange(sliderTimer)
            }
        )

        // Primary CTA Button: TAP TO WARM UP / STOP SESSION
        Button(
            onClick = { if (state.isSessionActive) onStopSession() else onStartSession() },
            enabled = state.isConnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isSessionActive) CartaColors.Danger else CartaColors.Mint,
                contentColor = if (state.isSessionActive) Color.White else Color.Black,
                disabledContainerColor = CartaColors.SurfaceElevated,
                disabledContentColor = CartaColors.TextMuted
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = if (state.isSessionActive) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isSessionActive) "STOP SESSION (${state.remainingSec}s)" else "TAP TO WARM UP",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = CartaColors.Mint.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
        Text(label, color = CartaColors.TextMuted, fontSize = 9.sp, letterSpacing = 0.6.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = CartaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(unit, color = CartaColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 1.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderControlBlock(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = CartaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircleStepButton("-", onMinus)
                Text(
                    text = valueText,
                    color = CartaColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                CircleStepButton("+", onPlus)
            }
        }
        CartaSleekSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CircleStepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(CartaColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = CartaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

