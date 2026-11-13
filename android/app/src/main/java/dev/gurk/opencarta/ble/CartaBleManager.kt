package dev.gurk.opencarta.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class TelemetryTick(
    val currentTemp: Int,
    val waxTemp: Int,
    val remainingSec: Int,
    val unitByte: Int
)

data class OfflineSessionRecord(
    val totalRecords: Int,
    val index: Int,
    val flags: Int,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val durationSec: Int,
    val temp: Int,
    val unitByte: Int
)

@SuppressLint("MissingPermission")
class CartaBleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CartaState())
    val state: StateFlow<CartaState> = _state.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val logs: StateFlow<List<BleLogEntry>> = _logs.asStateFlow()

    private val _hideRepetitiveLogs = MutableStateFlow(true)
    val hideRepetitiveLogs: StateFlow<Boolean> = _hideRepetitiveLogs.asStateFlow()

    private val _showAllBleDevices = MutableStateFlow(false)
    val showAllBleDevices: StateFlow<Boolean> = _showAllBleDevices.asStateFlow()

    private val _telemetryTicks = MutableSharedFlow<TelemetryTick>(extraBufferCapacity = 32)
    val telemetryTicks = _telemetryTicks.asSharedFlow()

    private val _offlineSessions = MutableSharedFlow<OfflineSessionRecord>(extraBufferCapacity = 32)
    val offlineSessions = _offlineSessions.asSharedFlow()

    private val _ledCustomized = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val ledCustomized = _ledCustomized.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val disReadQueue = ConcurrentLinkedQueue<BluetoothGattCharacteristic>()
    private val writeQueue = ConcurrentLinkedQueue<Pair<ByteArray, String>>()
    private val isWriting = AtomicBoolean(false)
    private val logIdGen = AtomicLong(1)
    private var last99Key = ""
    private var isScanning = false
    private val discoveredMap = LinkedHashMap<String, Pair<ScannedDevice, Long>>()
    private var pruneJob: Job? = null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setShowAllBleDevices(showAll: Boolean) {
        _showAllBleDevices.value = showAll
        if (isScanning) {
            stopScan()
            startScan()
        }
    }

    fun setHideRepetitiveLogs(hide: Boolean) {
        _hideRepetitiveLogs.value = hide
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun continueOffline() {
        stopScan()
        _state.update { it.copy(continuedOffline = true) }
    }

    fun returnToConnectScreen() {
        _state.update { it.copy(continuedOffline = false) }
        if (!_state.value.isConnected) {
            startScan()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val scanRecord = result.scanRecord
            val advName = scanRecord?.deviceName ?: device.name ?: ""
            val cleanName = advName.replace("\u0000", "").trim()
            val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val parsedAdv = parseCartaAdvertisement(
                scanRecord?.getManufacturerSpecificData(CartaUuids.TELINK_MANUFACTURER_ID)
            )

            val now = System.currentTimeMillis()
            synchronized(discoveredMap) {
                val existing = discoveredMap[address]?.first
                val isCarta = parsedAdv != null ||
                    serviceUuids.contains(CartaUuids.CARTA_SERVICE) ||
                    cleanName.startsWith("Pfeifi", ignoreCase = true) ||
                    cleanName.startsWith("CARTA", ignoreCase = true) ||
                    cleanName.startsWith("Focus", ignoreCase = true) ||
                    existing?.isCartaMatch == true

                if (!_showAllBleDevices.value && !isCarta) return

                val displayName = when {
                    cleanName.isNotEmpty() -> cleanName
                    existing != null && !existing.name.startsWith("Unknown BLE") -> existing.name
                    else -> "Unknown BLE ($address)"
                }

                discoveredMap[address] = ScannedDevice(
                    address = address,
                    name = displayName,
                    rssi = result.rssi,
                    isCartaMatch = isCarta,
                    batteryPercent = parsedAdv?.batteryPercent ?: existing?.batteryPercent,
                    modelNumber = parsedAdv?.modelNumber ?: existing?.modelNumber,
                    serialNumber = parsedAdv?.serialNumber ?: existing?.serialNumber
                ) to now
                val sorted = discoveredMap.values
                    .map { it.first }
                    .sortedWith(
                        compareByDescending<ScannedDevice> { it.isCartaMatch }.thenByDescending { it.rssi }
                    )
                _scannedDevices.value = sorted
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            addLog("INFO", "BLE scan failed (code $errorCode)")
            _state.update {
                if (it.connectionStatus == ConnectionStatus.SCANNING) {
                    it.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
                } else it
            }
        }
    }

    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            addLog("INFO", "Bluetooth is disabled")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        if (isScanning) return

        synchronized(discoveredMap) {
            discoveredMap.clear()
            _scannedDevices.value = emptyList()
        }
        isScanning = true
        _state.update { it.copy(connectionStatus = ConnectionStatus.SCANNING) }
        addLog("INFO", "Scanning for Carta devices...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)

        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isScanning) {
                delay(2500)
                val cutoff = System.currentTimeMillis() - 6000
                synchronized(discoveredMap) {
                    val removed = discoveredMap.entries.removeIf { it.value.second < cutoff }
                    if (removed) {
                        _scannedDevices.value = discoveredMap.values
                            .map { it.first }
                            .sortedWith(
                                compareByDescending<ScannedDevice> { it.isCartaMatch }.thenByDescending { it.rssi }
                            )
                    }
                }
            }
        }
    }

    fun stopScan() {
        pruneJob?.cancel()
        pruneJob = null
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _state.update {
            if (it.connectionStatus == ConnectionStatus.SCANNING) {
                it.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
            } else it
        }
    }

    fun connect(address: String, knownName: String? = null) {
        stopScan()
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        disconnect()

        val cleanName = (knownName ?: device.name ?: "Carta").replace("\u0000", "").trim()
        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                continuedOffline = false,
                deviceAddress = address,
                deviceName = cleanName
            )
        }
        addLog("INFO", "Connecting to $cleanName ($address)...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        writeQueue.clear()
        disReadQueue.clear()
        isWriting.set(false)
        writeChar = null
        notifyChar = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        synchronized(discoveredMap) {
            discoveredMap.clear()
            _scannedDevices.value = emptyList()
        }
        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                remainingSec = 0
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                addLog("INFO", "GATT connected, discovering services...")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                addLog("INFO", "Disconnected (status=$status)")
                writeQueue.clear()
                disReadQueue.clear()
                isWriting.set(false)
                writeChar = null
                notifyChar = null
                try { g.close() } catch (_: Exception) {}
                if (gatt == g) gatt = null
                synchronized(discoveredMap) {
                    discoveredMap.clear()
                    _scannedDevices.value = emptyList()
                }
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        remainingSec = 0
                    )
                }
                if (!_state.value.continuedOffline) {
                    mainHandler.post { startScan() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                addLog("INFO", "Service discovery failed: $status")
                disconnect()
                return
            }

            val cartaService = g.getService(CartaUuids.CARTA_SERVICE)
            if (cartaService == null) {
                addLog("INFO", "Carta GATT service not found on device")
                disconnect()
                return
            }

            notifyChar = cartaService.getCharacteristic(CartaUuids.CARTA_NOTIFY)
            writeChar = cartaService.getCharacteristic(CartaUuids.CARTA_WRITE)

            if (notifyChar == null || writeChar == null) {
                addLog("INFO", "Carta notify/write characteristics missing")
                disconnect()
                return
            }

            // Queue DIS reads for after CCCD write completes
            disReadQueue.clear()
            g.getService(CartaUuids.DIS_SERVICE)?.let { dis ->
                dis.getCharacteristic(CartaUuids.DIS_MODEL)?.let { disReadQueue.add(it) }
                dis.getCharacteristic(CartaUuids.DIS_SERIAL)?.let { disReadQueue.add(it) }
                dis.getCharacteristic(CartaUuids.DIS_FIRMWARE)?.let { disReadQueue.add(it) }
            }

            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar?.getDescriptor(CartaUuids.CCCD)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            } else {
                onReadyForSync(g)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CartaUuids.CCCD) {
                onReadyForSync(g)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: byteArrayOf()
            handleDisRead(g, characteristic.uuid, bytes, status)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleDisRead(g, characteristic.uuid, value, status)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleNotification(bytes)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            isWriting.set(false)
            drainWriteQueue()
        }
    }

    private fun onReadyForSync(g: BluetoothGatt) {
        _state.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
        addLog("INFO", "Notifications enabled! Reading device info & syncing...")
        if (!readNextDis(g)) {
            sendTimeSync()
        }
    }

    private fun readNextDis(g: BluetoothGatt): Boolean {
        val next = disReadQueue.poll() ?: return false
        return g.readCharacteristic(next)
    }

    private fun handleDisRead(g: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
            val text = String(value, Charsets.UTF_8).replace("\u0000", "").trim()
            val label = when (uuid) {
                CartaUuids.DIS_MODEL -> "Model"
                CartaUuids.DIS_SERIAL -> "Serial"
                CartaUuids.DIS_FIRMWARE -> "Firmware"
                else -> uuid.toString()
            }
            addLog("INFO", "DIS $label: $text")
            _state.update {
                when (uuid) {
                    CartaUuids.DIS_MODEL -> it.copy(modelNumber = text)
                    CartaUuids.DIS_SERIAL -> it.copy(serialNumber = text)
                    CartaUuids.DIS_FIRMWARE -> it.copy(firmwareVersion = text)
                    else -> it
                }
            }
        }
        if (!readNextDis(g)) {
            sendTimeSync()
        }
    }

    // ==================== COMMANDS ====================

    fun enqueuePacket(bytes: ByteArray, label: String = "") {
        if (!_state.value.isConnected || writeChar == null || gatt == null) return
        writeQueue.add(bytes to label)
        drainWriteQueue()
    }

    private fun drainWriteQueue() {
        if (!isWriting.compareAndSet(false, true)) return
        val next = writeQueue.poll()
        if (next == null) {
            isWriting.set(false)
            return
        }
        val (bytes, label) = next
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null) {
            isWriting.set(false)
            return
        }

        addLog("TX", "${CartaPackets.toHex(bytes)}${if (label.isNotEmpty()) "  // $label" else ""}")

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }

        if (!ok) {
            isWriting.set(false)
            addLog("INFO", "Write failed for $label")
        } else {
            // Fallback unlock in case onCharacteristicWrite isn't triggered rapidly on some stacks
            mainHandler.postDelayed({
                if (isWriting.compareAndSet(true, false)) {
                    drainWriteQueue()
                }
            }, 90)
        }
    }

    fun sendTimeSync() {
        enqueuePacket(CartaPackets.buildTimeSyncUtc(), "Time Sync UTC (0xdd)")
    }

    fun syncSessionHistory(totalRecords: Int = _state.value.dabCounter1.toInt()) {
        if (totalRecords <= 0 || !_state.value.isConnected) return
        val startIdx = maxOf(1, totalRecords - 30)
        scope.launch {
            for (idx in totalRecords downTo startIdx) {
                enqueuePacket(
                    CartaPackets.buildQuerySessionFa(totalRecords, idx),
                    "Query Session #$idx/$totalRecords (0xfa)"
                )
                delay(65)
            }
        }
    }

    fun send11Settings(
        unitByte: Int = _state.value.unitByte,
        brightness: Int = _state.value.ledBrightness,
        heatPreset: Int = _state.value.activeHeatPreset,
        ledPreset: Int = _state.value.activeLedPreset,
        vibeLevel: Int = _state.value.vibeLevel,
        lowPowerMode: Boolean = _state.value.lowPowerMode
    ) {
        _state.update {
            it.copy(
                unitByte = unitByte,
                ledBrightness = brightness,
                activeHeatPreset = heatPreset,
                activeLedPreset = ledPreset,
                vibeLevel = vibeLevel,
                lowPowerMode = lowPowerMode
            )
        }
        val pkt = CartaPackets.buildSettings11(
            unitByte = unitByte,
            brightness = brightness,
            heatPreset = heatPreset,
            ledPreset = ledPreset,
            vibeLevel = vibeLevel,
            lowPowerMode = lowPowerMode
        )
        enqueuePacket(
            pkt,
            "Settings (unit=0x${unitByte.toString(16)}, P$heatPreset, LED=$ledPreset, vibe=${vibeLevel * 10}%, LP=${if (lowPowerMode) 1 else 0})"
        )
    }

    fun selectHeatPreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        val cur = _state.value
        val tbl = if (cur.isFahrenheit) cur.concentratePresetsF else cur.concentratePresetsC
        val preset = tbl[s]
        _state.update {
            it.copy(
                activeHeatPreset = s,
                isManualHeat = false,
                waxTemp = preset?.temp ?: it.waxTemp,
                waxTimerSec = preset?.timeSec ?: it.waxTimerSec
            )
        }
        send11Settings(heatPreset = s)
    }

    fun setUnit(fahrenheit: Boolean) {
        val unit = if (fahrenheit) 0x11 else 0x22
        send11Settings(unitByte = unit)
    }

    fun setLedSlot(slot: Int) {
        send11Settings(ledPreset = slot.coerceIn(0, 5))
    }

    fun setVibeLevel(level: Int) {
        // level in {2, 4, 6, 8, 10}
        send11Settings(vibeLevel = level.coerceIn(2, 10))
    }

    fun setLowPowerMode(enabled: Boolean) {
        val brightness = if (enabled) 0x02 else 0x08
        send11Settings(lowPowerMode = enabled, brightness = brightness)
    }

    fun powerOffDevice() {
        val cur = _state.value
        val pkt = CartaPackets.buildSettings11(
            unitByte = cur.unitByte,
            brightness = cur.ledBrightness,
            heatPreset = cur.activeHeatPreset,
            ledPreset = cur.activeLedPreset,
            vibeLevel = cur.vibeLevel,
            lowPowerMode = cur.lowPowerMode,
            powerByte = 0xAF
        )
        enqueuePacket(pkt, "POWER OFF (0x11 af)")
    }

    fun sendCcCommand(
        actionByte: Int,
        targetPresetSlot: Int? = null,
        waxTemp: Int = _state.value.waxTemp,
        waxTimerSec: Int = _state.value.waxTimerSec,
        herbTemp: Int = _state.value.herbTemp,
        herbTimerSec: Int = _state.value.herbTimerSec
    ) {
        val cur = _state.value
        if (actionByte == 0xA5 && cur.isConnected && !cur.hasAtomizer) {
            addLog("SYS", "Blocked START SESH: no atomizer attached")
            return
        }
        val slot = targetPresetSlot ?: cur.activeHeatPreset
        val pkt = CartaPackets.buildCcCommand(
            herbTemp = herbTemp,
            waxTemp = waxTemp,
            unitByte = cur.unitByte,
            herbTimerSec = herbTimerSec,
            waxTimerSec = waxTimerSec,
            presetSlot = slot,
            actionByte = actionByte
        )
        val label = when (actionByte) {
            0xA5 -> "START SESH"
            0xAF -> "STOP SESH"
            else -> if (slot == 0) "LIVE TEMP ADJUST" else "SAVE TO PRESET"
        }
        _state.update {
            it.copy(
                waxTemp = waxTemp,
                waxTimerSec = waxTimerSec,
                herbTemp = herbTemp,
                herbTimerSec = herbTimerSec
            )
        }
        enqueuePacket(pkt, "$label (${waxTemp}°, ${waxTimerSec}s, P$slot)")
    }

    fun sendTimeouts(ledMinutes: Int, sleepMinutes: Int) {
        val ledSec = ledMinutes.coerceIn(1, 15) * 60
        val sleepSec = sleepMinutes.coerceIn(1, 15) * 60
        _state.update { it.copy(ledTimeoutSec = ledSec, sleepTimeoutSec = sleepSec) }
        enqueuePacket(
            CartaPackets.buildTimeoutsD1(ledSec, sleepSec),
            "Set Timeouts (LED=${ledMinutes}m, Sleep=${sleepMinutes}m)"
        )
    }

    fun sendRename(newName: String) {
        val trimmed = newName.trim().take(29)
        if (trimmed.isEmpty()) return
        val (p1, p2) = CartaPackets.buildRenamePackets(trimmed)
        _state.update { it.copy(deviceName = trimmed) }
        enqueuePacket(p1, "Rename Part 1 (\"$trimmed\")")
        scope.launch {
            delay(180)
            enqueuePacket(p2, "Rename Part 2")
        }
    }

    fun sendLedConfig(slot: Int, config: LedPresetConfig) {
        val s = slot.coerceIn(1, 5)
        _ledCustomized.tryEmit(Unit)
        _state.update {
            val updated = it.ledPresets.toMutableMap()
            updated[s] = config
            it.copy(activeLedPreset = s, ledPresets = updated)
        }
        send11Settings(ledPreset = s)
        scope.launch {
            delay(150)
            enqueuePacket(
                CartaPackets.buildLedPresetConfig(s, config),
                "LED Slot $s Config (mode=0x${config.mode.byteValue.toString(16)})"
            )
        }
    }

    // ==================== NOTIFICATIONS ====================

    private fun handleNotification(b: ByteArray) {
        if (b.isEmpty()) return
        val op = b[0].toInt() and 0xFF
        val hideSpam = _hideRepetitiveLogs.value

        when {
            op == 0x99 && b.size == 20 -> {
                val atomizerAttached = (b[2].toInt() and 0x40) != 0
                val u = b[3].toInt() and 0xFF
                val rawPreset = b[4].toInt() and 0x0F
                val remSec = b[5].toInt() and 0xFF
                val curTemp = ((b[6].toInt() and 0xFF) shl 8) or (b[7].toInt() and 0xFF)
                val herbTemp = ((b[8].toInt() and 0xFF) shl 8) or (b[9].toInt() and 0xFF)
                val waxTemp = ((b[10].toInt() and 0xFF) shl 8) or (b[11].toInt() and 0xFF)
                val battery = b[12].toInt() and 0xFF
                val ledPreset = (b[13].toInt() and 0xF0) ushr 4
                val ledBright = b[13].toInt() and 0x0F
                val herbTimer = b[14].toInt() and 0xFF
                val waxTimer = b[15].toInt() and 0xFF
                val charging = (b[16].toInt() and 0xFF) != 0
                val vibe = (b[17].toInt() and 0xF0) ushr 4
                val lowPower = (b[17].toInt() and 0x0F) != 0
                val standby = b[18].toInt() and 0xFF

                val key = "${b[2].toInt() and 0xFF}-$u-$rawPreset-$remSec-$waxTemp-$battery-${b[13].toInt() and 0xFF}-${b[16].toInt() and 0xFF}-${b[17].toInt() and 0xFF}"
                if (!hideSpam || key != last99Key) {
                    addLog("RX", "${CartaPackets.toHex(b)}  // Status (0x99)")
                    last99Key = key
                }

                _state.update {
                    val isManual = rawPreset == 0
                    val presetSlot = if (rawPreset in 1..5) rawPreset else it.activeHeatPreset
                    val presetTbl = if (u == 0x11) it.concentratePresetsF else it.concentratePresetsC
                    val activePreset = if (!isManual) presetTbl[presetSlot] else null
                    it.copy(
                        unitByte = u,
                        hasAtomizer = atomizerAttached,
                        activeHeatPreset = presetSlot,
                        isManualHeat = isManual,
                        remainingSec = remSec,
                        currentTemp = curTemp,
                        herbTemp = herbTemp,
                        waxTemp = activePreset?.temp ?: waxTemp,
                        batteryPercent = battery,
                        isCharging = charging,
                        activeLedPreset = ledPreset,
                        ledBrightness = if (ledBright > 0) ledBright else it.ledBrightness,
                        herbTimerSec = herbTimer,
                        waxTimerSec = activePreset?.timeSec ?: waxTimer,
                        vibeLevel = if (vibe > 0) vibe else it.vibeLevel,
                        lowPowerMode = lowPower,
                        standbyCountdown = standby
                    )
                }
                _telemetryTicks.tryEmit(TelemetryTick(curTemp, waxTemp, remSec, u))
            }

            op == 0xDD && b.size == 20 && (b[1].toInt() and 0xFF) == 0x01 -> {
                val phase = b[2].toInt() and 0xFF
                if (!hideSpam) {
                    addLog("RX", "${CartaPackets.toHex(b)}  // Session Stream (phase=$phase)")
                }
                _state.update { it.copy(sessionHeatingPhase = phase) }
            }

            op == 0xAA && b.size == 19 -> {
                fun u32(off: Int): Long =
                    ((b[off].toLong() and 0xFF) shl 24) or
                        ((b[off + 1].toLong() and 0xFF) shl 16) or
                        ((b[off + 2].toLong() and 0xFF) shl 8) or
                        (b[off + 3].toLong() and 0xFF)

                val c1 = u32(2)
                val c2 = u32(6)
                val c3 = u32(10)
                val c4 = u32(14)
                addLog("RX", "${CartaPackets.toHex(b)}  // Dab Counters: $c1, $c2, $c3, $c4")
                val prevTotal = _state.value.dabCounter1
                _state.update {
                    it.copy(dabCounter1 = c1, dabCounter2 = c2, dabCounter3 = c3, dabCounter4 = c4)
                }
                if (prevTotal == 0L && c1 > 0L) {
                    syncSessionHistory(c1.toInt())
                }
            }

            (op == 0x22 || op == 0x33 || op == 0x44 || op == 0x55) && b.size == 18 -> {
                val map = mutableMapOf<Int, HeatPreset>()
                for (i in 0 until 5) {
                    val off = 2 + i * 3
                    val temp = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
                    val time = b[off + 2].toInt() and 0xFF
                    map[i + 1] = HeatPreset(temp, time)
                }
                addLog("RX", "${CartaPackets.toHex(b)}  // Preset Table (0x${op.toString(16)})")
                _state.update {
                    when (op) {
                        0x22 -> it.copy(concentratePresetsC = map)
                        0x33 -> it.copy(concentratePresetsF = map)
                        0x44 -> it.copy(herbPresetsC = map)
                        0x55 -> it.copy(herbPresetsF = map)
                        else -> it
                    }
                }
            }

            op in 0xE6..0xEA && b.size == 20 -> {
                val slot = 0xEA - op + 1
                val mode = LedAnimationMode.fromByte(b[3].toInt() and 0xFF)
                val colors = (0 until 5).map { i ->
                    val off = 4 + i * 3
                    ((b[off].toInt() and 0xFF) shl 16) or
                        ((b[off + 1].toInt() and 0xFF) shl 8) or
                        (b[off + 2].toInt() and 0xFF)
                }
                addLog("RX", "${CartaPackets.toHex(b)}  // Stored LED Slot $slot")
                _state.update {
                    val updated = it.ledPresets.toMutableMap()
                    updated[slot] = LedPresetConfig(mode, colors)
                    it.copy(ledPresets = updated)
                }
            }

            op == 0xD2 && b.size == 7 -> {
                val ledSec = ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
                val sleepSec = ((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)
                addLog("RX", "${CartaPackets.toHex(b)}  // Timeouts: LED=${ledSec}s, Sleep=${sleepSec}s")
                _state.update {
                    it.copy(ledTimeoutSec = ledSec, sleepTimeoutSec = sleepSec)
                }
            }

            op == 0xFC && b.size == 18 -> {
                val total = ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
                val idx = ((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)
                val flags = b[6].toInt() and 0xFF
                val year = ((b[7].toInt() and 0xFF) shl 8) or (b[8].toInt() and 0xFF)
                val month = b[9].toInt() and 0xFF
                val day = b[10].toInt() and 0xFF
                val hour = b[11].toInt() and 0xFF
                val minute = b[12].toInt() and 0xFF
                val dur = b[13].toInt() and 0xFF
                val temp = ((b[14].toInt() and 0xFF) shl 8) or (b[15].toInt() and 0xFF)
                val unit = b[16].toInt() and 0xFF
                addLog("RX", "${CartaPackets.toHex(b)}  // Session #$idx/$total ($year-$month-$day $hour:$minute, ${temp}°, ${dur}s)")
                _offlineSessions.tryEmit(
                    OfflineSessionRecord(total, idx, flags, year, month, day, hour, minute, dur, temp, unit)
                )
            }

            else -> {
                addLog("RX", CartaPackets.toHex(b))
            }
        }
    }

    private fun addLog(dir: String, msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = BleLogEntry(logIdGen.getAndIncrement(), ts, dir, msg)
        _logs.update { current ->
            (current + entry).takeLast(250)
        }
    }
}

