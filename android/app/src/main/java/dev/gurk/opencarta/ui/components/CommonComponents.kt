package dev.gurk.opencarta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gurk.opencarta.ble.BleLogEntry
import dev.gurk.opencarta.ble.CartaPackets
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun OpenCartaBrandLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ӨPENCARTA",
            color = CartaColors.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.2.sp
        )
    }
}

fun batteryIconFor(percent: Int) = when {
    percent <= 5 -> Icons.Default.Battery0Bar
    percent < 20 -> Icons.Default.Battery1Bar
    percent < 35 -> Icons.Default.Battery2Bar
    percent < 50 -> Icons.Default.Battery3Bar
    percent < 68 -> Icons.Default.Battery4Bar
    percent < 83 -> Icons.Default.Battery5Bar
    percent < 96 -> Icons.Default.Battery6Bar
    else -> Icons.Default.BatteryFull
}

@Composable
fun BatteryBadge(percent: Int, isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (isConnected) "$percent%" else "--%",
            color = CartaColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = if (isConnected) batteryIconFor(percent) else Icons.Default.Battery0Bar,
            contentDescription = "Battery",
            tint = when {
                !isConnected -> CartaColors.TextMuted
                percent <= 20 -> CartaColors.Danger
                else -> CartaColors.Mint
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun CartaSliderThumb() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(CartaColors.Background)
            .border(2.5.dp, CartaColors.Mint, CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaSleekSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {}
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CartaColors.Background)
                    .border(2.5.dp, CartaColors.Mint, CircleShape)
            )
        },
        track = { _ ->
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
            val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A1E21))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0A4F40),
                                    Color(0xFF139E7C),
                                    CartaColors.Mint
                                )
                            )
                        )
                )
            }
        }
    )
}

@Composable
fun PacketInspectorDialog(
    logs: List<BleLogEntry>,
    hideRepetitive: Boolean,
    isConnected: Boolean,
    onToggleHideRepetitive: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onSendRawHex: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember { mutableStateOf("") }
    var hexError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CartaColors.Background)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLE Packet Log & Raw Hex",
                    color = CartaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = CartaColors.TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = CartaColors.TextPrimary)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hide repetitive 0x99 / 0xdd01",
                    color = CartaColors.TextSecondary,
                    fontSize = 13.sp
                )
                Switch(
                    checked = hideRepetitive,
                    onCheckedChange = onToggleHideRepetitive,
                    colors = SwitchDefaults.colors(checkedTrackColor = CartaColors.Mint)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        hexInput = it
                        hexError = null
                    },
                    placeholder = { Text("11 0c 00 22 08 00 03 05 00 08 00 11", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CartaColors.Mint,
                        unfocusedBorderColor = CartaColors.Border,
                        focusedTextColor = CartaColors.TextPrimary,
                        unfocusedTextColor = CartaColors.TextPrimary
                    )
                )
                Button(
                    onClick = {
                        val parsed = CartaPackets.parseHexString(hexInput)
                        if (parsed == null) {
                            hexError = "Invalid even-length hex"
                        } else {
                            onSendRawHex(parsed)
                        }
                    },
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Mint, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                }
            }
            if (hexError != null) {
                Text(hexError!!, color = CartaColors.Danger, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(logs, key = { it.id }) { entry ->
                        val color = when (entry.direction) {
                            "TX" -> CartaColors.Blue
                            "RX" -> CartaColors.Mint
                            else -> CartaColors.Warning
                        }
                        Text(
                            text = "[${entry.timestamp}] ${entry.direction}: ${entry.message}",
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

