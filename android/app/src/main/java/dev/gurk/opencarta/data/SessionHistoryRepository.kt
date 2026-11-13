package dev.gurk.opencarta.data

import android.content.Context
import dev.gurk.opencarta.ble.CartaBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class DabSessionEntry(
    val id: String,
    val date: String,       // YYYY-MM-DD
    val time: String,       // HH:mm (end/log time)
    val temp: Int,
    val unitSymbol: String, // °C or °F
    val durationSec: Int,
    val source: String,     // "Live" or "Device Sync"
    val startTime: String = time,
    val timestampMs: Long = 0L,
    val startBattery: Int = -1,
    val completedNaturally: Boolean = true
)

data class MonthStats(
    val totalDabs: Int,
    val activeDays: Int,
    val avgPerActiveDay: Double,
    val peakDate: String?,
    val peakCount: Int,
    val avgTempC: Int?,
    val avgDurationSec: Int?
)

data class SessionHistoryState(
    val sessions: List<DabSessionEntry> = emptyList(),
    val dailyOverrides: Map<String, Int> = emptyMap()
) {
    fun countForDay(date: LocalDate): Int {
        val key = date.toString()
        val recorded = sessions.count { it.date == key }
        val override = dailyOverrides[key] ?: 0
        return maxOf(recorded, override)
    }

    fun sessionsForDay(date: LocalDate): List<DabSessionEntry> {
        val key = date.toString()
        return sessions.filter { it.date == key }.sortedByDescending { it.time }
    }

    fun statsForMonth(ym: YearMonth): MonthStats {
        val daysInMonth = ym.lengthOfMonth()
        var total = 0
        var active = 0
        var peakDate: String? = null
        var peakCount = 0

        for (d in 1..daysInMonth) {
            val date = ym.atDay(d)
            val c = countForDay(date)
            if (c > 0) {
                total += c
                active++
                if (c > peakCount) {
                    peakCount = c
                    peakDate = date.toString()
                }
            }
        }

        val prefix = ym.toString() // YYYY-MM
        val monthSessions = sessions.filter { it.date.startsWith(prefix) }
        val avgTempC = if (monthSessions.isNotEmpty()) {
            monthSessions.map {
                if (it.unitSymbol == "°F") CurveRepository.fToC(it.temp) else it.temp
            }.average().roundToInt()
        } else null

        val avgDur = if (monthSessions.isNotEmpty()) {
            monthSessions.map { it.durationSec }.average().roundToInt()
        } else null

        return MonthStats(
            totalDabs = total,
            activeDays = active,
            avgPerActiveDay = if (active > 0) total.toDouble() / active else 0.0,
            peakDate = peakDate,
            peakCount = peakCount,
            avgTempC = avgTempC,
            avgDurationSec = avgDur
        )
    }
}

class SessionHistoryRepository(
    context: Context,
    private val bleManager: CartaBleManager
) {
    private val prefs = context.getSharedPreferences("opencarta_history", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _history = MutableStateFlow(loadFromPrefs())
    val history: StateFlow<SessionHistoryState> = _history.asStateFlow()

    private var lastRemSec = 0
    private var lastCapturedTemp = 230
    private var lastCapturedTimer = 55
    private var lastCapturedUnit = 0x22
    private var sessionStartMs = 0L
    private var sessionStartTime = ""
    private var sessionStartBattery = -1
    private var sessionMinRemSec = Int.MAX_VALUE

    init {
        scope.launch {
            bleManager.telemetryTicks.collect { tick ->
                if (tick.remainingSec > 0) {
                    if (lastRemSec == 0) {
                        sessionStartMs = System.currentTimeMillis()
                        sessionStartTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        sessionStartBattery = bleManager.state.value.batteryPercent
                        sessionMinRemSec = tick.remainingSec
                    } else {
                        sessionMinRemSec = minOf(sessionMinRemSec, tick.remainingSec)
                    }
                    lastCapturedTemp = tick.waxTemp
                    lastCapturedTimer = bleManager.state.value.waxTimerSec
                    lastCapturedUnit = tick.unitByte
                }
                if (lastRemSec > 0 && tick.remainingSec == 0) {
                    val nowMs = System.currentTimeMillis()
                    val today = LocalDate.now().toString()
                    val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val unitStr = if (lastCapturedUnit == 0x11) "°F" else "°C"
                    val naturalFinish = sessionMinRemSec <= 2
                    val actualElapsed = if (sessionStartMs > 0L) {
                        ((nowMs - sessionStartMs) / 1000L).toInt().coerceAtLeast(1)
                    } else {
                        lastCapturedTimer
                    }
                    addSession(
                        DabSessionEntry(
                            id = "live_$nowMs",
                            date = today,
                            time = nowTime,
                            temp = lastCapturedTemp,
                            unitSymbol = unitStr,
                            durationSec = if (naturalFinish) lastCapturedTimer else actualElapsed,
                            source = "Live",
                            startTime = sessionStartTime.ifEmpty { nowTime },
                            timestampMs = if (sessionStartMs > 0L) sessionStartMs else nowMs,
                            startBattery = sessionStartBattery,
                            completedNaturally = naturalFinish
                        )
                    )
                    sessionStartMs = 0L
                    sessionStartTime = ""
                    sessionStartBattery = -1
                    sessionMinRemSec = Int.MAX_VALUE
                }
                lastRemSec = tick.remainingSec
            }
        }

        scope.launch {
            bleManager.offlineSessions.collect { rec ->
                if (rec.year in 2020..2100 && rec.month in 1..12 && rec.day in 1..31) {
                    val date = "%04d-%02d-%02d".format(rec.year, rec.month, rec.day)
                    val time = "%02d:%02d".format(rec.hour, rec.minute)
                    val unitStr = if (rec.unitByte == 0x11) "°F" else "°C"
                    val id = "sync_${date}_${time}_${rec.temp}_${rec.durationSec}"
                    addSession(
                        DabSessionEntry(
                            id = id,
                            date = date,
                            time = time,
                            temp = rec.temp,
                            unitSymbol = unitStr,
                            durationSec = rec.durationSec,
                            source = "Device Sync"
                        )
                    )
                }
            }
        }

        scope.launch {
            bleManager.state.collect { s ->
                val localToday = LocalDate.now()
                val utcToday = LocalDate.now(ZoneOffset.UTC)
                if (s.isConnected && s.dabCounter2 > 0 && localToday == utcToday) {
                    val today = localToday.toString()
                    val daily = s.dabCounter2.toInt()
                    val cur = _history.value.dailyOverrides[today] ?: 0
                    if (daily != cur) {
                        val updatedOverrides = _history.value.dailyOverrides.toMutableMap()
                        updatedOverrides[today] = daily
                        val next = _history.value.copy(dailyOverrides = updatedOverrides)
                        _history.value = next
                        saveToPrefs(next)
                    }
                }
            }
        }
    }

    private fun addSession(entry: DabSessionEntry) {
        val current = _history.value
        if (current.sessions.any { it.id == entry.id }) return
        val next = current.copy(sessions = (current.sessions + entry).takeLast(1000))
        _history.value = next
        saveToPrefs(next)
    }

    private fun loadFromPrefs(): SessionHistoryState {
        val sessions = mutableListOf<DabSessionEntry>()
        val overrides = mutableMapOf<String, Int>()
        try {
            val sArr = JSONArray(prefs.getString("sessions_json", "[]") ?: "[]")
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                val timeStr = o.getString("time")
                sessions.add(
                    DabSessionEntry(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        time = timeStr,
                        temp = o.getInt("temp"),
                        unitSymbol = o.optString("unitSymbol", "°C"),
                        durationSec = o.getInt("durationSec"),
                        source = o.optString("source", "Live"),
                        startTime = o.optString("startTime", timeStr),
                        timestampMs = o.optLong("timestampMs", 0L),
                        startBattery = o.optInt("startBattery", -1),
                        completedNaturally = o.optBoolean("completedNaturally", true)
                    )
                )
            }
            val oObj = JSONObject(prefs.getString("daily_overrides_json", "{}") ?: "{}")
            oObj.keys().forEach { k ->
                overrides[k] = oObj.getInt(k)
            }
        } catch (_: Exception) {
        }
        return SessionHistoryState(sessions, overrides)
    }

    private fun saveToPrefs(state: SessionHistoryState) {
        val sArr = JSONArray()
        for (s in state.sessions) {
            sArr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("date", s.date)
                    .put("time", s.time)
                    .put("temp", s.temp)
                    .put("unitSymbol", s.unitSymbol)
                    .put("durationSec", s.durationSec)
                    .put("source", s.source)
                    .put("startTime", s.startTime)
                    .put("timestampMs", s.timestampMs)
                    .put("startBattery", s.startBattery)
                    .put("completedNaturally", s.completedNaturally)
            )
        }
        val oObj = JSONObject()
        state.dailyOverrides.forEach { (k, v) -> oObj.put(k, v) }
        prefs.edit()
            .putString("sessions_json", sArr.toString())
            .putString("daily_overrides_json", oObj.toString())
            .apply()
    }
}

