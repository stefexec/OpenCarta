package dev.gurk.opencarta.ble

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

object CartaUuids {
    val CARTA_SERVICE: UUID = UUID.fromString("1011123e-8535-b5a0-7140-a304d2495cb7")
    val CARTA_NOTIFY: UUID = UUID.fromString("1011123e-8535-b5a0-7140-a304d2495cb8")
    val CARTA_WRITE: UUID = UUID.fromString("1011123e-8535-b5a0-7140-a304d2495cb9")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val DIS_MODEL: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val DIS_SERIAL: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val DIS_FIRMWARE: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    const val TELINK_MANUFACTURER_ID = 0x0211
}

enum class LedAnimationMode(val byteValue: Int, val label: String) {
    ROTATE(0x13, "ROTATE"),
    PULSE(0x23, "PULSE"),
    FADE(0x33, "FADE"),
    SPARKLE(0x43, "SPARKLE"),
    STATIC(0x53, "STATIC");

    companion object {
        fun fromByte(b: Int): LedAnimationMode =
            entries.firstOrNull { it.byteValue == (b and 0xFF) } ?: FADE
    }
}

data class HeatPreset(
    val temp: Int,
    val timeSec: Int
)

data class LedPresetConfig(
    val mode: LedAnimationMode = LedAnimationMode.FADE,
    val colors: List<Int> = listOf(0x00F028, 0x0000F9, 0x00F413, 0x0800E1, 0x0FE300)
) {
    fun composeColors(): List<Color> = colors.map { Color(0xFF000000 or it.toLong()) }
}

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class CartaAdvertisement(
    val modelNumber: String?,
    val serialNumber: String?,
    val batteryPercent: Int?
)

/**
 * Parses Telink (0x0211) scan-response manufacturer data:
 * `{<model>|<serial>~<battery_uint8>^<status_uint8>}`
 */
fun parseCartaAdvertisement(data: ByteArray?): CartaAdvertisement? {
    if (data == null || data.size < 8) return null
    if (data[0] != '{'.code.toByte() || data[data.lastIndex] != '}'.code.toByte()) return null

    val pipeIdx = data.indexOf('|'.code.toByte())
    val tildeIdx = data.indexOf('~'.code.toByte())
    val caretIdx = data.indexOf('^'.code.toByte())
    if (pipeIdx <= 1 || tildeIdx <= pipeIdx + 1 || caretIdx != tildeIdx + 2 || caretIdx >= data.lastIndex) {
        return null
    }

    val model = String(data, 1, pipeIdx - 1, Charsets.US_ASCII).trim().ifEmpty { null }
    val serial = String(data, pipeIdx + 1, tildeIdx - pipeIdx - 1, Charsets.US_ASCII).trim().ifEmpty { null }
    val rawBattery = data[tildeIdx + 1].toInt() and 0xFF
    val battery = if (rawBattery in 0..100) rawBattery else null

    return CartaAdvertisement(
        modelNumber = model,
        serialNumber = serial,
        batteryPercent = battery
    )
}

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isCartaMatch: Boolean,
    val batteryPercent: Int? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null
)

data class BleLogEntry(
    val id: Long,
    val timestamp: String,
    val direction: String, // TX, RX, INFO
    val message: String
)

data class CartaState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val continuedOffline: Boolean = false,
    val deviceName: String = "Carta",
    val deviceAddress: String = "",
    val modelNumber: String = "--",
    val serialNumber: String = "--",
    val firmwareVersion: String = "--",

    // Live Telemetry (0x99)
    val unitByte: Int = 0x22, // 0x22 = °C, 0x11 = °F
    val currentTemp: Int = 17,
    val herbTemp: Int = 165,
    val waxTemp: Int = 230,
    val remainingSec: Int = 0,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val hasAtomizer: Boolean = false,
    val activeHeatPreset: Int = 1, // 1..5 (0 = manual)
    val activeLedPreset: Int = 5,  // 0 = OFF, 1..5
    val ledBrightness: Int = 8,    // 2 = 20%, 8 = 80%
    val herbTimerSec: Int = 180,
    val waxTimerSec: Int = 55,
    val vibeLevel: Int = 8,        // 2, 4, 6, 8, 10 (20%..100%)
    val lowPowerMode: Boolean = false,
    val standbyCountdown: Int = 0,

    // Dab Counters (0xaa)
    val dabCounter1: Long = 0,
    val dabCounter2: Long = 0,
    val dabCounter3: Long = 0,
    val dabCounter4: Long = 0,

    // Timeouts (0xd2)
    val ledTimeoutSec: Int = 480,   // 8 min
    val sleepTimeoutSec: Int = 600, // 10 min

    // Stored Preset Tables
    val concentratePresetsC: Map<Int, HeatPreset> = defaultPresetsC(),
    val concentratePresetsF: Map<Int, HeatPreset> = defaultPresetsF(),
    val herbPresetsC: Map<Int, HeatPreset> = emptyMap(),
    val herbPresetsF: Map<Int, HeatPreset> = emptyMap(),
    val ledPresets: Map<Int, LedPresetConfig> = defaultLedPresets(),

    // High-rate session phase (0xdd 0x01)
    val sessionHeatingPhase: Int = 0
) {
    val isFahrenheit: Boolean get() = unitByte == 0x11
    val unitSymbol: String get() = if (isFahrenheit) "°F" else "°C"
    val isSessionActive: Boolean get() = remainingSec > 0
    val isPreheating: Boolean get() = remainingSec > 0 && currentTemp < waxTemp - 2
    val isConnected: Boolean get() = connectionStatus == ConnectionStatus.CONNECTED

    companion object {
        fun defaultPresetsC() = mapOf(
            1 to HeatPreset(230, 55),
            2 to HeatPreset(257, 55),
            3 to HeatPreset(268, 50),
            4 to HeatPreset(279, 50),
            5 to HeatPreset(296, 45)
        )

        fun defaultPresetsF() = mapOf(
            1 to HeatPreset(446, 55),
            2 to HeatPreset(495, 55),
            3 to HeatPreset(514, 50),
            4 to HeatPreset(534, 50),
            5 to HeatPreset(565, 45)
        )

        fun defaultLedPresets() = mapOf(
            1 to LedPresetConfig(LedAnimationMode.ROTATE, listOf(0x00F028, 0xFF0000, 0x0000FF, 0x1ED7A6, 0xFFFFFF)),
            2 to LedPresetConfig(LedAnimationMode.PULSE, listOf(0x00F028, 0xFF0000, 0x0000FF, 0x00FFAA, 0xFFAA00)),
            3 to LedPresetConfig(LedAnimationMode.FADE, listOf(0x00F028, 0xFF0000, 0x0000FF, 0xAA00FF, 0x00AAFF)),
            4 to LedPresetConfig(LedAnimationMode.SPARKLE, listOf(0x00F028, 0xFF0000, 0x0000FF, 0xFF00AA, 0xAAFF00)),
            5 to LedPresetConfig(LedAnimationMode.ROTATE, listOf(0x00F028, 0x0000F9, 0x00F413, 0x0800E1, 0x0FE300))
        )
    }
}

object CartaPackets {
    fun buildTimeSyncUtc(): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return byteArrayOf(
            0xDD.toByte(), 0x0C,
            ((year shr 8) and 0xFF).toByte(), (year and 0xFF).toByte(),
            month.toByte(), day.toByte(), hour.toByte(), minute.toByte(),
            0x00, 0x00, 0x00,
            0xDD.toByte()
        )
    }

    fun buildSettings11(
        unitByte: Int,
        brightness: Int,
        heatPreset: Int,
        ledPreset: Int,
        vibeLevel: Int,
        lowPowerMode: Boolean,
        powerByte: Int = 0x00
    ): ByteArray {
        return byteArrayOf(
            0x11, 0x0C,
            powerByte.toByte(),
            unitByte.toByte(),
            brightness.coerceIn(0x02, 0x0A).toByte(),
            0x00,
            heatPreset.coerceIn(1, 5).toByte(),
            ledPreset.coerceIn(0, 5).toByte(),
            0x00,
            vibeLevel.coerceIn(0x02, 0x0A).toByte(),
            (if (lowPowerMode) 0x01 else 0x00).toByte(),
            0x11
        )
    }

    fun buildCcCommand(
        herbTemp: Int,
        waxTemp: Int,
        unitByte: Int,
        herbTimerSec: Int,
        waxTimerSec: Int,
        presetSlot: Int, // 0x00 = preview/curve, 1..5 = save to preset
        actionByte: Int  // 0x00 = save/adjust, 0xA5 = start sesh, 0xAF = stop sesh
    ): ByteArray {
        return byteArrayOf(
            0xCC.toByte(), 0x10,
            ((herbTemp shr 8) and 0xFF).toByte(), (herbTemp and 0xFF).toByte(),
            ((waxTemp shr 8) and 0xFF).toByte(), (waxTemp and 0xFF).toByte(),
            unitByte.toByte(),
            ((herbTimerSec shr 8) and 0xFF).toByte(), (herbTimerSec and 0xFF).toByte(),
            ((waxTimerSec shr 8) and 0xFF).toByte(), (waxTimerSec and 0xFF).toByte(),
            0x00,
            presetSlot.coerceIn(0, 5).toByte(),
            actionByte.toByte(),
            0x00,
            0xCC.toByte()
        )
    }

    fun buildTimeoutsD1(ledTimeoutSec: Int, sleepTimeoutSec: Int): ByteArray {
        return byteArrayOf(
            0xD1.toByte(), 0x07,
            ((ledTimeoutSec shr 8) and 0xFF).toByte(), (ledTimeoutSec and 0xFF).toByte(),
            ((sleepTimeoutSec shr 8) and 0xFF).toByte(), (sleepTimeoutSec and 0xFF).toByte(),
            0xD1.toByte()
        )
    }

    fun buildRenamePackets(newName: String): Pair<ByteArray, ByteArray> {
        val raw = newName.trim().toByteArray(Charsets.UTF_8)
        val p1 = ByteArray(20)
        p1[0] = 0xAC.toByte()
        p1[1] = 0x14
        val len1 = minOf(raw.size, 17)
        System.arraycopy(raw, 0, p1, 2, len1)
        p1[19] = 0xAC.toByte()

        val p2 = ByteArray(15)
        p2[0] = 0xAF.toByte()
        p2[1] = 0x0F
        if (raw.size > 17) {
            val len2 = minOf(raw.size - 17, 12)
            System.arraycopy(raw, 17, p2, 2, len2)
        }
        p2[14] = 0xAF.toByte()
        return p1 to p2
    }

    fun buildLedPresetConfig(slot: Int, config: LedPresetConfig): ByteArray {
        val op = (0xE0 + (slot.coerceIn(1, 5) - 1)).toByte()
        val pkt = ByteArray(20)
        pkt[0] = op
        pkt[1] = 0x14
        pkt[2] = 0xAA.toByte()
        pkt[3] = config.mode.byteValue.toByte()
        for (i in 0 until 5) {
            val rgb = config.colors.getOrElse(i) { 0x00FF00 }
            pkt[4 + i * 3] = ((rgb shr 16) and 0xFF).toByte()
            pkt[5 + i * 3] = ((rgb shr 8) and 0xFF).toByte()
            pkt[6 + i * 3] = (rgb and 0xFF).toByte()
        }
        pkt[19] = op
        return pkt
    }

    fun buildQuerySessionFa(total: Int, index: Int): ByteArray {
        return byteArrayOf(
            0xFA.toByte(), 0x07,
            ((total shr 8) and 0xFF).toByte(), (total and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(), (index and 0xFF).toByte(),
            0xFA.toByte()
        )
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    fun parseHexString(input: String): ByteArray? {
        val clean = input.replace(Regex("[^0-9a-fA-F]"), "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

