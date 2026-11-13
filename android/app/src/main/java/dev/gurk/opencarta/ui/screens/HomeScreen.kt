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

    val activePreset = if (state.isFahrenheit) {
        state.concentratePresetsF[state.activeHeatPreset]
    } else {
        state.concentratePresetsC[state.activeHeatPreset]
    }
    val syncedTemp = if (state.isConnected) state.waxTemp else (activePreset?.temp ?: state.waxTemp)
    val syncedTimer = if (state.isConnected) state.waxTimerSec else (activePreset?.timeSec ?: state.waxTimerSec)

    var sliderTemp by remember { mutableIntStateOf(syncedTemp) }
    var sliderTimer by remember { mutableIntStateOf(syncedTimer.coerceIn(10, 120)) }
    var isDraggingTemp by remember { mutableStateOf(false) }
    var isDraggingTimer by remember { mutableStateOf(false) }

    LaunchedEffect(syncedTemp, state.unitByte) {
        if (!isDraggingTemp) {
            sliderTemp = syncedTemp
        }
    }
    LaunchedEffect(syncedTimer) {
        if (!isDraggingTimer) {
            sliderTimer = syncedTimer.coerceIn(10, 120)
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
                BatteryBadge(state.batteryPercent, state.isConnected, state.isCharging)
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
            Column(
                modifier = Modifier.width(142.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CartaDeviceGraphic(
                    modifier = Modifier
                        .width(142.dp)
                        .height(180.dp),
                    ledColors = activeLedColors,
                    ledsEnabled = state.activeLedPreset > 0,
                    isHeating = state.isSessionActive
                )

                // Sleek Auto-Off / Idle Counter aligned next to Live Temp
                val maxSleepSec = state.sleepTimeoutSec.coerceAtLeast(1)
                val idleSec = (state.standbyCountdown * 2.5f).roundToInt().coerceAtMost(maxSleepSec)
                val idleFraction = when {
                    !state.isConnected -> 0f
                    state.isSessionActive -> 1f
                    state.standbyCountdown <= 0 -> 1f
                    else -> (idleSec.toFloat() / maxSleepSec.toFloat()).coerceIn(0f, 1f)
                }
                val idleText = when {
                    !state.isConnected -> "IDLE --"
                    state.isSessionActive -> "HEATING"
                    state.standbyCountdown <= 0 && maxSleepSec > 600 -> "IDLE 10:00+"
                    state.standbyCountdown <= 0 -> {
                        val m = maxSleepSec / 60
                        val s = maxSleepSec % 60
                        "IDLE $m:${s.toString().padStart(2, '0')}"
                    }
                    else -> {
                        val m = idleSec / 60
                        val s = idleSec % 60
                        "IDLE $m:${s.toString().padStart(2, '0')}"
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = idleText,
                        color = CartaColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CartaColors.SurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(idleFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (!state.isConnected) CartaColors.TextMuted.copy(alpha = 0.3f)
                                    else if (state.isSessionActive) CartaColors.Danger.copy(alpha = 0.8f)
                                    else CartaColors.TextMuted.copy(alpha = 0.7f)
                                )
                        )
                    }
                }
            }

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
                    val modelLabel = remember(state.modelNumber, state.deviceName) {
                        val model = state.modelNumber.trim().uppercase()
                        val name = state.deviceName.trim().uppercase()
                        when {
                            name.contains("CARTA 2") || name.contains("CARTA2") -> "CARTA 2"
                            model.contains("SPORT") || model == "S3BL" || name.contains("SPORT") -> "SPORT"
                            name.contains("CARTA") -> "CARTA"
                            else -> "CARTA"
                        }
                    }
                    Column {
                        Text(modelLabel, color = CartaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
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
                val noAtomizer = state.isConnected && !state.hasAtomizer
                val tempPhase = when {
                    noAtomizer -> "NO ATOMIZER"
                    state.isPreheating -> "PRE-HEAT ${state.remainingSec}s"
                    state.isSessionActive -> "ACTIVE ${state.remainingSec}s"
                    else -> "AMBIENT"
                }
                val tempDotColor = when {
                    noAtomizer -> CartaColors.Warning
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
                        text = if (state.isConnected && state.hasAtomizer) "${state.currentTemp}${state.unitSymbol}" else "--${state.unitSymbol}",
                        color = CartaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tempPhase,
                        color = if (noAtomizer) CartaColors.Warning else CartaColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
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
            val heatingEnabled = !(state.isConnected && !state.hasAtomizer)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(14.dp))
                    .clickable(enabled = heatingEnabled, onClick = onOpenSeshEditor)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SESH\nPRESET",
                    color = if (heatingEnabled) CartaColors.TextSecondary else CartaColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (heatingEnabled) CartaColors.Blue else CartaColors.TextMuted.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${state.activeHeatPreset}",
                        color = if (heatingEnabled) CartaColors.TextPrimary else CartaColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (heatingEnabled) CartaColors.TextSecondary else CartaColors.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val heatingEnabled = !(state.isConnected && !state.hasAtomizer)

        // Temperature Slider Row
        SliderControlBlock(
            label = "TEMPERATURE",
            valueText = "$sliderTemp°",
            value = sliderTemp.toFloat(),
            valueRange = minTemp.toFloat()..maxTemp.toFloat(),
            enabled = heatingEnabled,
            onMinus = {
                sliderTemp = (sliderTemp - 1).coerceIn(minTemp, maxTemp)
                onTempLiveChange(sliderTemp)
            },
            onPlus = {
                sliderTemp = (sliderTemp + 1).coerceIn(minTemp, maxTemp)
                onTempLiveChange(sliderTemp)
            },
            onValueChange = {
                isDraggingTemp = true
                sliderTemp = it.roundToInt()
            },
            onValueChangeFinished = {
                isDraggingTemp = false
                onTempLiveChange(sliderTemp)
            }
        )

        // Duration Slider Row
        val mins = sliderTimer / 60
        val secs = sliderTimer % 60
        SliderControlBlock(
            label = "DURATION",
            valueText = "$mins:${secs.toString().padStart(2, '0')}",
            value = sliderTimer.toFloat(),
            valueRange = 10f..120f,
            enabled = heatingEnabled,
            onMinus = {
                sliderTimer = (sliderTimer - 5).coerceIn(10, 120)
                onTimerLiveChange(sliderTimer)
            },
            onPlus = {
                sliderTimer = (sliderTimer + 5).coerceIn(10, 120)
                onTimerLiveChange(sliderTimer)
            },
            onValueChange = {
                isDraggingTimer = true
                sliderTimer = it.roundToInt()
            },
            onValueChangeFinished = {
                isDraggingTimer = false
                onTimerLiveChange(sliderTimer)
            }
        )

        // Primary CTA Button: TAP TO WARM UP / STOP SESSION
        Button(
            onClick = { if (state.isSessionActive) onStopSession() else onStartSession() },
            enabled = state.isConnected && state.hasAtomizer,
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
                text = when {
                    state.isSessionActive -> "STOP SESSION (${state.remainingSec}s)"
                    state.isConnected && !state.hasAtomizer -> "NO ATOMIZER"
                    else -> "TAP TO WARM UP"
                },
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
    enabled: Boolean = true,
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
            Text(
                label,
                color = if (enabled) CartaColors.TextSecondary else CartaColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircleStepButton("-", enabled = enabled, onClick = onMinus)
                Text(
                    text = valueText,
                    color = if (enabled) CartaColors.TextPrimary else CartaColors.TextMuted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                CircleStepButton("+", enabled = enabled, onClick = onPlus)
            }
        }
        CartaSleekSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CircleStepButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) CartaColors.SurfaceElevated else CartaColors.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = if (enabled) CartaColors.TextPrimary else CartaColors.TextMuted.copy(alpha = 0.45f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

