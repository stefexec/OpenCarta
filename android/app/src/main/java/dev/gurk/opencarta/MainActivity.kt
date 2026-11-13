package dev.gurk.opencarta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.gurk.opencarta.ble.CartaBleManager
import dev.gurk.opencarta.data.AchievementsRepository
import dev.gurk.opencarta.data.CurveRepository
import dev.gurk.opencarta.data.SessionHistoryRepository
import dev.gurk.opencarta.ui.components.PacketInspectorDialog
import dev.gurk.opencarta.ui.screens.*
import dev.gurk.opencarta.ui.theme.CartaColors
import dev.gurk.opencarta.ui.theme.OpenCartaTheme

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("HOME", Icons.Default.Home),
    CURVES("CURVES", Icons.AutoMirrored.Filled.ShowChart),
    CALENDAR("CALENDAR", Icons.Default.CalendarMonth),
    DEVICE("DEVICE", Icons.Default.Memory),
    ACHIEVEMENTS("BADGES", Icons.Default.EmojiEvents),
    ABOUT("ABOUT", Icons.Default.Info)
}

enum class HomeSubScreen {
    MAIN,
    LED_EDITOR,
    SESH_EDITOR
}

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: CartaBleManager
    private lateinit var curveRepo: CurveRepository
    private lateinit var achievementsRepo: AchievementsRepository
    private lateinit var historyRepo: SessionHistoryRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        bleManager = CartaBleManager(applicationContext)
        curveRepo = CurveRepository(applicationContext, bleManager)
        historyRepo = SessionHistoryRepository(applicationContext, bleManager)
        achievementsRepo = AchievementsRepository(applicationContext, bleManager, historyRepo, curveRepo)

        requestBlePermissionsAndScan()

        setContent {
            OpenCartaTheme {
                OpenCartaRoot(
                    bleManager = bleManager,
                    curveRepo = curveRepo,
                    achievementsRepo = achievementsRepo,
                    historyRepo = historyRepo,
                    onRequestScan = { requestBlePermissionsAndScan() }
                )
            }
        }
    }

    private fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun requestBlePermissionsAndScan() {
        val perms = requiredBlePermissions()
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            bleManager.startScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
        bleManager.disconnect()
    }
}

@Composable
private fun OpenCartaRoot(
    bleManager: CartaBleManager,
    curveRepo: CurveRepository,
    achievementsRepo: AchievementsRepository,
    historyRepo: SessionHistoryRepository,
    onRequestScan: () -> Unit
) {
    val state by bleManager.state.collectAsState()
    val scannedDevices by bleManager.scannedDevices.collectAsState()
    val showAllBle by bleManager.showAllBleDevices.collectAsState()
    val logs by bleManager.logs.collectAsState()
    val hideRepetitiveLogs by bleManager.hideRepetitiveLogs.collectAsState()

    val allCurves by curveRepo.allCurves.collectAsState()
    val activeCurve by curveRepo.activeCurve.collectAsState()
    val runnerState by curveRepo.runnerState.collectAsState()
    val achievements by achievementsRepo.summary.collectAsState()
    val sessionHistory by historyRepo.history.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var homeSubScreen by remember { mutableStateOf(HomeSubScreen.MAIN) }
    var showPacketInspector by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("opencarta_ui", android.content.Context.MODE_PRIVATE) }
    var showCalendarTab by remember { mutableStateOf(prefs.getBoolean("show_calendar_tab", false)) }

    val visibleTabs = remember(showCalendarTab) {
        if (showCalendarTab) MainTab.entries else MainTab.entries.filter { it != MainTab.CALENDAR }
    }

    BackHandler(enabled = currentTab == MainTab.HOME && homeSubScreen != HomeSubScreen.MAIN) {
        homeSubScreen = HomeSubScreen.MAIN
    }

    Scaffold(
        containerColor = CartaColors.Background,
        bottomBar = {
            Column(modifier = Modifier.background(CartaColors.Background).navigationBarsPadding()) {
                HorizontalDivider(color = CartaColors.Border, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .background(CartaColors.Background),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTabs.forEach { tab ->
                        val selected = currentTab == tab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    currentTab = tab
                                    if (tab == MainTab.HOME) {
                                        homeSubScreen = HomeSubScreen.MAIN
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) CartaColors.Mint else CartaColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = if (selected) CartaColors.Mint else CartaColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.HOME -> {
                    when {
                        !state.isConnected && !state.continuedOffline -> {
                            ConnectScreen(
                                connectionStatus = state.connectionStatus,
                                scannedDevices = scannedDevices,
                                showAllBle = showAllBle,
                                bluetoothEnabled = bleManager.isBluetoothEnabled(),
                                onToggleShowAll = { bleManager.setShowAllBleDevices(it) },
                                onStartScan = onRequestScan,
                                onConnect = { dev -> bleManager.connect(dev.address, dev.name) },
                                onContinueOffline = { bleManager.continueOffline() }
                            )
                        }
                        homeSubScreen == HomeSubScreen.LED_EDITOR -> {
                            LedEditorScreen(
                                state = state,
                                onBack = { homeSubScreen = HomeSubScreen.MAIN },
                                onSetLedOff = { bleManager.setLedSlot(0) },
                                onSelectActiveLedSlot = { slot -> bleManager.setLedSlot(slot) },
                                onSaveLedConfig = { slot, cfg -> bleManager.sendLedConfig(slot, cfg) }
                            )
                        }
                        homeSubScreen == HomeSubScreen.SESH_EDITOR -> {
                            SeshPresetScreen(
                                state = state,
                                onBack = { homeSubScreen = HomeSubScreen.MAIN },
                                onSelectHeatPreset = { slot -> bleManager.selectHeatPreset(slot) },
                                onSavePreset = { slot, waxTemp, waxTime, herbTemp, herbTime ->
                                    bleManager.sendCcCommand(
                                        actionByte = 0x00,
                                        targetPresetSlot = slot,
                                        waxTemp = waxTemp,
                                        waxTimerSec = waxTime,
                                        herbTemp = herbTemp,
                                        herbTimerSec = herbTime
                                    )
                                }
                            )
                        }
                        else -> {
                            HomeScreen(
                                state = state,
                                achievements = achievements,
                                onOpenLedEditor = { homeSubScreen = HomeSubScreen.LED_EDITOR },
                                onOpenSeshEditor = { homeSubScreen = HomeSubScreen.SESH_EDITOR },
                                onOpenPacketLog = { showPacketInspector = true },
                                onReconnectClick = {
                                    bleManager.returnToConnectScreen()
                                    onRequestScan()
                                },
                                onTempLiveChange = { temp ->
                                    bleManager.sendCcCommand(actionByte = 0x00, targetPresetSlot = 0x00, waxTemp = temp)
                                },
                                onTimerLiveChange = { sec ->
                                    bleManager.sendCcCommand(actionByte = 0x00, targetPresetSlot = 0x00, waxTimerSec = sec)
                                },
                                onStartSession = { bleManager.sendCcCommand(actionByte = 0xA5) },
                                onStopSession = { bleManager.sendCcCommand(actionByte = 0xAF) }
                            )
                        }
                    }
                }

                MainTab.CURVES -> {
                    CurvesScreen(
                        state = state,
                        allCurves = allCurves,
                        activeCurve = activeCurve,
                        runnerState = runnerState,
                        onSelectCurve = { curveRepo.selectCurve(it) },
                        onSetInterpMode = { curveRepo.setInterpMode(it) },
                        onUpdateCurvePoint = { idx, t, tempC -> curveRepo.updatePoint(idx, t, tempC) },
                        onDragCurvePoint = { idx, t, tempC -> curveRepo.updatePointFromDrag(idx, t, tempC) },
                        onAddCurvePoint = { curveRepo.addPoint() },
                        onRemoveCurvePoint = { curveRepo.removePoint(it) },
                        onGenerateCurve = { s, e, d, shape -> curveRepo.generateFromRange(s, e, d, shape) },
                        onSaveCustomCurve = { curveRepo.saveAsCustom(it) },
                        onRenameCustomCurve = { curveRepo.renameCustom(it) },
                        onDeleteCustomCurve = { curveRepo.deleteCustom() },
                        onStartCurve = { curveRepo.startCurveSession() },
                        onStopCurve = { curveRepo.stopCurveSession() }
                    )
                }

                MainTab.CALENDAR -> {
                    CalendarScreen(
                        history = sessionHistory,
                        achievements = achievements,
                        isFahrenheit = state.isFahrenheit,
                        isConnected = state.isConnected,
                        onSyncHistory = { bleManager.syncSessionHistory() }
                    )
                }

                MainTab.DEVICE -> {
                    DeviceScreen(
                        state = state,
                        showCalendarTab = showCalendarTab,
                        onToggleShowCalendarTab = { enabled ->
                            showCalendarTab = enabled
                            prefs.edit().putBoolean("show_calendar_tab", enabled).apply()
                            if (!enabled && currentTab == MainTab.CALENDAR) {
                                currentTab = MainTab.DEVICE
                            }
                        },
                        onToggleLowPower = { bleManager.setLowPowerMode(it) },
                        onToggleUnit = { bleManager.setUnit(it) },
                        onSetVibeLevel = { bleManager.setVibeLevel(it) },
                        onSetTimeouts = { led, sleep -> bleManager.sendTimeouts(led, sleep) },
                        onRenameDevice = { bleManager.sendRename(it) },
                        onSyncTime = { bleManager.sendTimeSync() },
                        onOpenPacketLog = { showPacketInspector = true },
                        onPowerOff = { bleManager.powerOffDevice() },
                        onDisconnectOrConnect = {
                            if (state.isConnected) {
                                bleManager.disconnect()
                            } else {
                                currentTab = MainTab.HOME
                                bleManager.returnToConnectScreen()
                                onRequestScan()
                            }
                        }
                    )
                }

                MainTab.ACHIEVEMENTS -> {
                    AchievementsScreen(summary = achievements)
                }

                MainTab.ABOUT -> {
                    AboutScreen(onOpenPacketLog = { showPacketInspector = true })
                }
            }
        }
    }

    if (showPacketInspector) {
        PacketInspectorDialog(
            logs = logs,
            hideRepetitive = hideRepetitiveLogs,
            isConnected = state.isConnected,
            onToggleHideRepetitive = { bleManager.setHideRepetitiveLogs(it) },
            onClearLogs = { bleManager.clearLogs() },
            onSendRawHex = { bytes -> bleManager.enqueuePacket(bytes, "Raw Hex") },
            onDismiss = { showPacketInspector = false }
        )
    }
}

