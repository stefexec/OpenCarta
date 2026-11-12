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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.CartaState
import dev.gurk.opencarta.ble.HeatPreset
import dev.gurk.opencarta.ui.components.CartaSleekSlider
import dev.gurk.opencarta.ui.theme.CartaColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeshPresetScreen(
    state: CartaState,
    onBack: () -> Unit,
    onSelectHeatPreset: (Int) -> Unit,
    onSavePreset: (slot: Int, waxTemp: Int, waxTime: Int, herbTemp: Int, herbTime: Int) -> Unit
) {
    var editingSlot by remember { mutableIntStateOf(state.activeHeatPreset.coerceIn(1, 5)) }
    var isHerbMode by remember { mutableStateOf(false) }

    val waxTable = if (state.isFahrenheit) state.concentratePresetsF else state.concentratePresetsC
    val herbTable = if (state.isFahrenheit) state.herbPresetsF else state.herbPresetsC

    val defaultWax = waxTable[editingSlot] ?: HeatPreset(if (state.isFahrenheit) 446 else 230, 55)
    val defaultHerb = herbTable[editingSlot] ?: HeatPreset(state.herbTemp, state.herbTimerSec.coerceIn(30, 240))

    var waxTemp by remember(editingSlot, state.unitByte) { mutableIntStateOf(defaultWax.temp) }
    var waxTime by remember(editingSlot) { mutableIntStateOf(defaultWax.timeSec.coerceIn(10, 120)) }
    var herbTemp by remember(editingSlot, state.unitByte) { mutableIntStateOf(defaultHerb.temp) }
    var herbTime by remember(editingSlot) { mutableIntStateOf(defaultHerb.timeSec.coerceIn(30, 240)) }

    val activeTemp = if (isHerbMode) herbTemp else waxTemp
    val activeTime = if (isHerbMode) herbTime else waxTime
    val minTemp = if (isHerbMode) (if (state.isFahrenheit) 212 else 100) else (if (state.isFahrenheit) 300 else 150)
    val maxTemp = if (isHerbMode) (if (state.isFahrenheit) 450 else 240) else (if (state.isFahrenheit) 650 else 340)
    val minTime = if (isHerbMode) 30 else 10
    val maxTime = if (isHerbMode) 240 else 120

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CartaColors.SurfaceElevated)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = CartaColors.TextPrimary)
                }
                Text(
                    text = "PRESET #$editingSlot",
                    color = CartaColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Concentrate / Herb mode pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(50))
                    .padding(3.dp)
            ) {
                listOf(false to "WAX", true to "HERB").forEach { (herb, label) ->
                    val sel = isHerbMode == herb
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) CartaColors.Mint else Color.Transparent)
                            .clickable { isHerbMode = herb }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(label, color = if (sel) Color.Black else CartaColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Preset #1..#5 Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (slot in 1..5) {
                val pTemp = (if (isHerbMode) herbTable[slot]?.temp else waxTable[slot]?.temp) ?: waxTemp
                val selected = editingSlot == slot
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CartaColors.Surface)
                        .border(1.5.dp, if (selected) CartaColors.Mint else CartaColors.Border, RoundedCornerShape(12.dp))
                        .clickable {
                            editingSlot = slot
                            onSelectHeatPreset(slot)
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$pTemp°",
                        color = if (selected) CartaColors.TextPrimary else CartaColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PRESET #$slot",
                        color = if (selected) CartaColors.Mint else CartaColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Big Readouts + Visual Gauges (matching Screenshot 5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Temperature Readout + Vertical Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$activeTemp", color = CartaColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                    Text(state.unitSymbol, color = CartaColors.TextSecondary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
                }
                Text("$activeTemp${state.unitSymbol}", color = CartaColors.Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(14.dp))
                val tempFrac = ((activeTemp - minTemp).toFloat() / (maxTemp - minTemp).toFloat()).coerceIn(0.08f, 1f)
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CartaColors.SurfaceElevated),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(tempFrac)
                            .clip(RoundedCornerShape(50))
                            .background(CartaColors.Mint)
                    )
                }
            }

            // Duration Readout + Segmented Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$activeTime", color = CartaColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                    Text("s", color = CartaColors.TextSecondary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
                }
                val m = activeTime / 60
                val s = activeTime % 60
                Text("$m:${s.toString().padStart(2, '0')}", color = CartaColors.Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(28.dp))
                val segCount = 12
                val activeSegs = ((activeTime.toFloat() / maxTime.toFloat()) * segCount).roundToInt().coerceIn(1, segCount)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until segCount) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (i < activeSegs) CartaColors.Mint else CartaColors.SurfaceElevated)
                        )
                    }
                }
            }
        }

        // Temperature Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "TEMPERATURE",
                color = CartaColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleStepButton("-") {
                    if (isHerbMode) herbTemp = (herbTemp - 1).coerceIn(minTemp, maxTemp)
                    else waxTemp = (waxTemp - 1).coerceIn(minTemp, maxTemp)
                }
                Text("$activeTemp°", color = CartaColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                CircleStepButton("+") {
                    if (isHerbMode) herbTemp = (herbTemp + 1).coerceIn(minTemp, maxTemp)
                    else waxTemp = (waxTemp + 1).coerceIn(minTemp, maxTemp)
                }
            }
            CartaSleekSlider(
                value = activeTemp.toFloat(),
                onValueChange = {
                    val v = it.roundToInt()
                    if (isHerbMode) herbTemp = v else waxTemp = v
                },
                valueRange = minTemp.toFloat()..maxTemp.toFloat()
            )
        }

        // Duration Card
        val mins = activeTime / 60
        val secs = activeTime % 60
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "DURATION",
                color = CartaColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleStepButton("-") {
                    if (isHerbMode) herbTime = (herbTime - 5).coerceIn(minTime, maxTime)
                    else waxTime = (waxTime - 5).coerceIn(minTime, maxTime)
                }
                Text("$mins:${secs.toString().padStart(2, '0')}", color = CartaColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                CircleStepButton("+") {
                    if (isHerbMode) herbTime = (herbTime + 5).coerceIn(minTime, maxTime)
                    else waxTime = (waxTime + 5).coerceIn(minTime, maxTime)
                }
            }
            CartaSleekSlider(
                value = activeTime.toFloat(),
                onValueChange = {
                    val v = it.roundToInt()
                    if (isHerbMode) herbTime = v else waxTime = v
                },
                valueRange = minTime.toFloat()..maxTime.toFloat()
            )
        }

        // DONE Button
        Button(
            onClick = {
                onSavePreset(editingSlot, waxTemp, waxTime, herbTemp, herbTime)
                onBack()
            },
            enabled = state.isConnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = CartaColors.Mint,
                contentColor = Color.Black,
                disabledContainerColor = CartaColors.SurfaceElevated,
                disabledContentColor = CartaColors.TextMuted
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("DONE", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        }
    }
}

