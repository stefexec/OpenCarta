package dev.gurk.opencarta.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.ConnectionStatus
import dev.gurk.opencarta.ble.ScannedDevice
import dev.gurk.opencarta.ui.components.CartaDeviceGraphic
import dev.gurk.opencarta.ui.components.OpenCartaBrandLogo
import dev.gurk.opencarta.ui.components.batteryIconFor
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun ConnectScreen(
    connectionStatus: ConnectionStatus,
    scannedDevices: List<ScannedDevice>,
    showAllBle: Boolean,
    bluetoothEnabled: Boolean,
    onToggleShowAll: (Boolean) -> Unit,
    onStartScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onContinueOffline: () -> Unit
) {
    var selectedIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(scannedDevices.size) {
        if (selectedIdx >= scannedDevices.size) selectedIdx = 0
    }

    val pulseTransition = rememberInfiniteTransition(label = "radar")
    val radarAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Concentric stadium rings in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.48f
            val ringSteps = listOf(
                Size(size.width * 0.56f, size.height * 0.40f),
                Size(size.width * 0.74f, size.height * 0.52f),
                Size(size.width * 0.92f, size.height * 0.64f),
                Size(size.width * 1.10f, size.height * 0.76f)
            )
            ringSteps.forEachIndexed { idx, sz ->
                val alpha = if (scannedDevices.isEmpty()) {
                    (0.14f - idx * 0.025f) * radarAlpha * 1.4f
                } else {
                    0.12f - idx * 0.02f
                }
                drawRoundRect(
                    color = CartaColors.Mint.copy(alpha = alpha.coerceIn(0.03f, 0.25f)),
                    topLeft = Offset(cx - sz.width / 2f, cy - sz.height / 2f),
                    size = sz,
                    cornerRadius = CornerRadius(sz.width / 2f, sz.width / 2f),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Top Header
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OpenCartaBrandLogo()
        }

        // Center Content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!bluetoothEnabled) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = CartaColors.Danger,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Bluetooth is turned off",
                    color = CartaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable Bluetooth to discover your Carta.",
                    color = CartaColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else if (connectionStatus == ConnectionStatus.CONNECTING) {
                CircularProgressIndicator(color = CartaColors.Mint, modifier = Modifier.size(42.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connecting to Carta...",
                    color = CartaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (scannedDevices.isEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    tint = CartaColors.Mint,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (connectionStatus == ConnectionStatus.SCANNING) {
                        "Searching for devices.."
                    } else {
                        "Tap to search for devices"
                    },
                    color = CartaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (connectionStatus != ConnectionStatus.SCANNING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartScan,
                        colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Mint, contentColor = Color.Black),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Scan Now", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val device = scannedDevices[selectedIdx]
                // Stadium-shaped found device card matching Screenshot 2
                Column(
                    modifier = Modifier
                        .width(235.dp)
                        .clip(RoundedCornerShape(118.dp))
                        .background(Color(0xFF161819))
                        .border(1.dp, CartaColors.Border, RoundedCornerShape(118.dp))
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CartaDeviceGraphic(
                            modifier = Modifier.size(width = 96.dp, height = 112.dp),
                            ledsEnabled = true
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val battery = device.batteryPercent
                        if (battery != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = batteryIconFor(battery),
                                    contentDescription = "Battery $battery%",
                                    tint = if (battery <= 20) CartaColors.Danger else CartaColors.Mint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "$battery%",
                                    color = CartaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.BatteryStd,
                                contentDescription = null,
                                tint = CartaColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = device.name,
                        color = CartaColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = device.address,
                        color = CartaColors.TextSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CartaColors.Mint)
                            .clickable { onConnect(device) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Connect",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (scannedDevices.size > 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = {
                            selectedIdx = (selectedIdx - 1 + scannedDevices.size) % scannedDevices.size
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = CartaColors.TextSecondary)
                        }
                        Text(
                            text = "${selectedIdx + 1} / ${scannedDevices.size} found",
                            color = CartaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        IconButton(onClick = {
                            selectedIdx = (selectedIdx + 1) % scannedDevices.size
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = CartaColors.TextSecondary)
                        }
                    }
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onToggleShowAll(!showAllBle) }
            ) {
                Checkbox(
                    checked = showAllBle,
                    onCheckedChange = onToggleShowAll,
                    colors = CheckboxDefaults.colors(
                        checkedColor = CartaColors.Mint,
                        uncheckedColor = CartaColors.TextMuted,
                        checkmarkColor = Color.Black
                    )
                )
                Text(
                    text = "Show all BLE devices",
                    color = CartaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            TextButton(onClick = onContinueOffline) {
                Text(
                    text = "Continue without a device",
                    color = CartaColors.Mint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

