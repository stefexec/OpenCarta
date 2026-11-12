package dev.gurk.opencarta.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.CartaState
import dev.gurk.opencarta.ble.LedAnimationMode
import dev.gurk.opencarta.ble.LedPresetConfig
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun LedEditorScreen(
    state: CartaState,
    onBack: () -> Unit,
    onSetLedOff: () -> Unit,
    onSelectActiveLedSlot: (Int) -> Unit,
    onSaveLedConfig: (Int, LedPresetConfig) -> Unit
) {
    var editingSlot by remember {
        mutableIntStateOf(if (state.activeLedPreset in 1..5) state.activeLedPreset else 5)
    }
    val initialConfig = state.ledPresets[editingSlot] ?: LedPresetConfig()
    var mode by remember(editingSlot) { mutableStateOf(initialConfig.mode) }
    var colors by remember(editingSlot) { mutableStateOf(initialConfig.colors.toMutableList()) }
    var selectedColorIdx by remember { mutableIntStateOf(0) }

    // HSV state for selected color stop
    val currentRgb = colors.getOrElse(selectedColorIdx) { 0x00F028 }
    val hsv = remember(currentRgb) {
        val arr = FloatArray(3)
        AndroidColor.RGBToHSV((currentRgb shr 16) and 0xFF, (currentRgb shr 8) and 0xFF, currentRgb and 0xFF, arr)
        arr
    }
    var hue by remember(selectedColorIdx, editingSlot) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(selectedColorIdx, editingSlot) { mutableFloatStateOf(hsv[1]) }
    var value by remember(selectedColorIdx, editingSlot) { mutableFloatStateOf(hsv[2]) }
    var hexInput by remember(currentRgb) { mutableStateOf("%06X".format(currentRgb and 0xFFFFFF)) }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        val argb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        val rgb = argb and 0xFFFFFF
        colors = colors.toMutableList().also { it[selectedColorIdx] = rgb }
        hexInput = "%06X".format(rgb)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar
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

        // Slot Selector Pills (OFF + Preset #1..#5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // OFF pill
            val isOff = state.activeLedPreset == 0
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, if (isOff) CartaColors.Mint else CartaColors.Border, RoundedCornerShape(12.dp))
                    .clickable { onSetLedOff() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = CartaColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("OFF", color = CartaColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            for (slot in 1..5) {
                val cfg = state.ledPresets[slot] ?: LedPresetConfig()
                val selected = editingSlot == slot && state.activeLedPreset != 0
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CartaColors.Surface)
                        .border(1.5.dp, if (selected) CartaColors.Mint else CartaColors.Border, RoundedCornerShape(12.dp))
                        .clickable {
                            editingSlot = slot
                            onSelectActiveLedSlot(slot)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        cfg.composeColors().take(3).forEach { c ->
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(c))
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "PRESET #$slot",
                        color = if (selected) CartaColors.TextPrimary else CartaColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2D Saturation / Value Color Picker Square
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hue, selectedColorIdx) {
                        detectTapGestures { offset ->
                            val s = (offset.x / size.width).coerceIn(0f, 1f)
                            val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            updateFromHsv(hue, s, v)
                        }
                    }
                    .pointerInput(hue, selectedColorIdx) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val s = (change.position.x / size.width).coerceIn(0f, 1f)
                            val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            updateFromHsv(hue, s, v)
                        }
                    }
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.White, hueColor))
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                )
                val cx = sat * size.width
                val cy = (1f - value) * size.height
                drawCircle(
                    color = Color(0xFF000000 or currentRgb.toLong()),
                    radius = 14.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 14.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Rainbow Hue Slider Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(50))
        ) {
            val rainbow = remember {
                (0..360 step 30).map { h -> Color(AndroidColor.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f))) }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sat, value, selectedColorIdx) {
                        detectTapGestures { offset ->
                            val h = ((offset.x / size.width).coerceIn(0f, 1f)) * 360f
                            updateFromHsv(h, sat, value)
                        }
                    }
                    .pointerInput(sat, value, selectedColorIdx) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val h = ((change.position.x / size.width).coerceIn(0f, 1f)) * 360f
                            updateFromHsv(h, sat, value)
                        }
                    }
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(rainbow),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                )
                val hx = (hue / 360f) * size.width
                drawCircle(
                    color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                    radius = 13.dp.toPx(),
                    center = Offset(hx, size.height / 2f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 13.dp.toPx(),
                    center = Offset(hx, size.height / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Animation Mode Selector
        Text(
            text = "ANIMATION  —  ${mode.label}",
            color = CartaColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedAnimationMode.entries.forEach { anim ->
                val selected = mode == anim
                val icon = when (anim) {
                    LedAnimationMode.ROTATE -> Icons.Default.Refresh
                    LedAnimationMode.PULSE -> Icons.Default.MonitorHeart
                    LedAnimationMode.FADE -> Icons.Default.BlurOn
                    LedAnimationMode.SPARKLE -> Icons.Default.AutoAwesome
                    LedAnimationMode.STATIC -> Icons.Default.HorizontalRule
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CartaColors.Surface)
                        .border(1.dp, if (selected) CartaColors.Mint else CartaColors.Border, RoundedCornerShape(12.dp))
                        .clickable { mode = anim }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) CartaColors.Mint else CartaColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = anim.label,
                        color = if (selected) CartaColors.Mint else CartaColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5 Color Stop Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEachIndexed { idx, rgb ->
                val selected = idx == selectedColorIdx
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CartaColors.Surface)
                        .border(1.dp, if (selected) CartaColors.Mint else CartaColors.Border, RoundedCornerShape(10.dp))
                        .clickable { selectedColorIdx = idx }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000 or rgb.toLong()))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%06X".format(rgb and 0xFFFFFF),
                        color = if (selected) CartaColors.TextPrimary else CartaColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Hex Input Bar
        OutlinedTextField(
            value = hexInput,
            onValueChange = { raw ->
                val clean = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                hexInput = clean
                if (clean.length == 6) {
                    val rgb = clean.toInt(16)
                    colors = colors.toMutableList().also { it[selectedColorIdx] = rgb }
                    val arr = FloatArray(3)
                    AndroidColor.RGBToHSV((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, arr)
                    hue = arr[0]; sat = arr[1]; value = arr[2]
                }
            },
            leadingIcon = { Text("#", color = CartaColors.TextMuted, fontWeight = FontWeight.Bold) },
            trailingIcon = { Text("SLOT ${selectedColorIdx + 1}", color = CartaColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CartaColors.Mint,
                unfocusedBorderColor = CartaColors.Border,
                focusedContainerColor = CartaColors.Surface,
                unfocusedContainerColor = CartaColors.Surface,
                focusedTextColor = CartaColors.TextPrimary,
                unfocusedTextColor = CartaColors.TextPrimary
            )
        )

        // Save Button
        Button(
            onClick = { onSaveLedConfig(editingSlot, LedPresetConfig(mode, colors.toList())) },
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
            Text(
                text = "SAVE TO PRESET #$editingSlot",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

