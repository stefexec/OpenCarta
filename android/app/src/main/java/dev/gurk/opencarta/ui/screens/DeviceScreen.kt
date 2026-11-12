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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gurk.opencarta.ble.CartaState
import dev.gurk.opencarta.ui.components.BatteryBadge
import dev.gurk.opencarta.ui.components.OpenCartaBrandLogo
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun DeviceScreen(
    state: CartaState,
    showCalendarTab: Boolean,
    onToggleShowCalendarTab: (Boolean) -> Unit,
    onToggleLowPower: (Boolean) -> Unit,
    onToggleUnit: (Boolean) -> Unit,
    onSetVibeLevel: (Int) -> Unit,
    onSetTimeouts: (ledMin: Int, sleepMin: Int) -> Unit,
    onRenameDevice: (String) -> Unit,
    onSyncTime: () -> Unit,
    onOpenPacketLog: () -> Unit,
    onPowerOff: () -> Unit,
    onDisconnectOrConnect: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    val ledMin = (state.ledTimeoutSec / 60).coerceIn(1, 15)
    val sleepMin = (state.sleepTimeoutSec / 60).coerceIn(1, 15)
    val vibeStep = (state.vibeLevel / 2).coerceIn(1, 5) // 1..5 -> 20%..100%

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CartaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar (matching Screenshot 6)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OpenCartaBrandLogo()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryBadge(state.batteryPercent, state.isConnected)
                CircleActionIcon(
                    icon = Icons.Default.Bluetooth,
                    tint = if (state.isConnected) CartaColors.Mint else CartaColors.TextMuted,
                    onClick = onDisconnectOrConnect
                )
                CircleActionIcon(
                    icon = Icons.Default.PowerSettingsNew,
                    tint = if (state.isConnected) CartaColors.Danger else CartaColors.TextMuted,
                    onClick = { if (state.isConnected) showShutdownDialog = true }
                )
                CircleActionIcon(
                    icon = Icons.Default.Settings,
                    tint = CartaColors.TextSecondary,
                    onClick = onOpenPacketLog
                )
            }
        }

        // Device Name + Serial + Firmware Version
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(enabled = state.isConnected) { showRenameDialog = true }
            ) {
                Text(
                    text = state.deviceName.uppercase(),
                    color = CartaColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = if (state.isConnected) CartaColors.TextSecondary else CartaColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SERIAL: ${state.serialNumber}",
                color = CartaColors.Mint.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 1.8.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "VERSION: ${state.firmwareVersion}",
                color = CartaColors.Mint.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 1.8.sp
            )
        }

        // LOW POWER Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(
                    1.dp,
                    if (state.lowPowerMode) CartaColors.Mint else CartaColors.Border,
                    RoundedCornerShape(16.dp)
                )
                .clickable(enabled = state.isConnected) { onToggleLowPower(!state.lowPowerMode) }
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = if (state.lowPowerMode) CartaColors.Mint else CartaColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("LOW POWER", color = CartaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (state.lowPowerMode) "ON (20% LED)" else "OFF",
                color = if (state.lowPowerMode) CartaColors.Mint else CartaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Unit & Vibe 2-Column Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // °C / °F Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                    .clickable(enabled = state.isConnected) { onToggleUnit(!state.isFahrenheit) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Thermostat, contentDescription = null, tint = CartaColors.TextSecondary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(state.unitSymbol, color = CartaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            // VIBE Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CartaColors.Surface)
                    .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = CartaColors.TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("VIBE", color = CartaColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallArrowBox(isLeft = true, enabled = state.isConnected && vibeStep > 1) {
                        onSetVibeLevel((vibeStep - 1) * 2)
                    }
                    Text(
                        text = "$vibeStep",
                        color = CartaColors.Mint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SmallArrowBox(isLeft = false, enabled = state.isConnected && vibeStep < 5) {
                        onSetVibeLevel((vibeStep + 1) * 2)
                    }
                }
            }
        }

        // SLEEP MODE & LED TIMEOUT Steppers (0xd1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeoutStepperColumn(
                title = "SLEEP MODE",
                valueText = "$sleepMin min",
                canDecrease = state.isConnected && sleepMin > 1,
                canIncrease = state.isConnected && sleepMin < 15,
                onDecrease = { onSetTimeouts(ledMin, sleepMin - 1) },
                onIncrease = { onSetTimeouts(ledMin, sleepMin + 1) }
            )
            TimeoutStepperColumn(
                title = "LED TIMEOUT",
                valueText = "$ledMin min",
                canDecrease = state.isConnected && ledMin > 1,
                canIncrease = state.isConnected && ledMin < 15,
                onDecrease = { onSetTimeouts(ledMin - 1, sleepMin) },
                onIncrease = { onSetTimeouts(ledMin + 1, sleepMin) }
            )
        }

        // Action List Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CartaColors.Surface)
                .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
                .clickable { onToggleShowCalendarTab(!showCalendarTab) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CartaColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = CartaColors.Mint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Calendar Tab", color = CartaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Sessions are still recorded when hidden", color = CartaColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = showCalendarTab,
                onCheckedChange = onToggleShowCalendarTab,
                colors = SwitchDefaults.colors(checkedTrackColor = CartaColors.Mint, checkedThumbColor = Color.Black)
            )
        }

        DeviceActionRow(
            icon = Icons.Default.Sync,
            title = "Sync Time & Dump Tables",
            subtitle = "Send 0xdd UTC handshake & refresh presets",
            enabled = state.isConnected,
            onClick = onSyncTime
        )
        DeviceActionRow(
            icon = Icons.Default.Terminal,
            title = "BLE Packet Log & Raw Hex",
            subtitle = "Inspect TX/RX frames or send raw GATT writes",
            enabled = true,
            onClick = onOpenPacketLog
        )
        DeviceActionRow(
            icon = if (state.isConnected) Icons.Default.BluetoothDisabled else Icons.Default.BluetoothSearching,
            title = if (state.isConnected) "Disconnect Device" else "Connect a Device",
            subtitle = if (state.isConnected) "Close GATT connection to ${state.deviceName}" else "Open BLE scanner",
            enabled = true,
            onClick = onDisconnectOrConnect
        )
    }

    if (showRenameDialog) {
        var nameInput by remember { mutableStateOf(state.deviceName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = CartaColors.SurfaceElevated,
            title = { Text("Rename Device", color = CartaColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Max 29 characters (sent via 0xac + 0xaf)", color = CartaColors.TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 29) nameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CartaColors.Mint,
                            unfocusedBorderColor = CartaColors.Border,
                            focusedTextColor = CartaColors.TextPrimary,
                            unfocusedTextColor = CartaColors.TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameDevice(nameInput)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Mint, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = CartaColors.TextSecondary)
                }
            }
        )
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            containerColor = CartaColors.SurfaceElevated,
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = CartaColors.Danger) },
            title = { Text("Power Off Device?", color = CartaColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Send remote shutdown command (0x11 0xaf) to ${state.deviceName}? This will turn off the device and disconnect Bluetooth.",
                    color = CartaColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPowerOff()
                        showShutdownDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CartaColors.Danger, contentColor = Color.White)
                ) {
                    Text("Power Off", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text("Cancel", color = CartaColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CircleActionIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(CartaColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SmallArrowBox(isLeft: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(CartaColors.SurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) CartaColors.TextPrimary else CartaColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TimeoutStepperColumn(
    title: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = CartaColors.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallArrowBox(isLeft = true, enabled = canDecrease, onClick = onDecrease)
            Text(valueText, color = CartaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            SmallArrowBox(isLeft = false, enabled = canIncrease, onClick = onIncrease)
        }
    }
}

@Composable
private fun DeviceActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CartaColors.Surface)
            .border(1.dp, CartaColors.Border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CartaColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) CartaColors.TextSecondary else CartaColors.TextMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) CartaColors.TextPrimary else CartaColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = CartaColors.TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CartaColors.Mint)
    }
}

