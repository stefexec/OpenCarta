package dev.gurk.opencarta.data

import android.content.Context
import dev.gurk.opencarta.ble.CartaBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.roundToInt

enum class CurveInterpMode(val id: String, val label: String) {
    SMOOTH("smooth", "Smooth Ramp"),
    STEP("step", "Stepped")
}

enum class CurveGenShape(val id: String, val label: String) {
    EXP("exp", "Exponential (Cooling / Decay)"),
    LINEAR("linear", "Linear Ramp"),
    LADDER("ladder", "3-Step Terp Ladder"),
    EASE_IN("ease_in", "Flavor Hold → Late Ramp")
}

data class CurvePoint(
    val t: Int,
    val tempC: Int
)

data class TempCurve(
    val id: String,
    val name: String,
    val mode: CurveInterpMode,
    val desc: String,
    val pointsC: List<CurvePoint>
) {
    val isCustom: Boolean get() = id.startsWith("custom_")
    val durationSec: Int get() = (pointsC.maxOfOrNull { it.t } ?: 55).coerceIn(10, 120)
}

enum class CurvePhase { IDLE, PREHEAT, ACTIVE }

data class TracePoint(val t: Int, val temp: Int)

data class CurveRunnerState(
    val running: Boolean = false,
    val phase: CurvePhase = CurvePhase.IDLE,
    val durationSec: Int = 55,
    val elapsedSec: Int = 0,
    val lastSentTemp: Int? = null,
    val liveTrace: List<TracePoint> = emptyList()
)

class CurveRepository(
    context: Context,
    private val bleManager: CartaBleManager
) {
    private val prefs = context.getSharedPreferences("opencarta_curves", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val builtinCurves = listOf(
        TempCurve(
            id = "quartz_cooling",
            name = "Quartz Banger Cooling Curve (55s)",
            mode = CurveInterpMode.SMOOTH,
            desc = "Gentle Newton cooling curve: starts at 245°C (473°F) to melt cleanly, then decays exponentially down to 215°C (419°F) for cool, terp-rich pulls.",
            pointsC = listOf(
                CurvePoint(0, 245), CurvePoint(8, 237), CurvePoint(18, 230),
                CurvePoint(30, 223), CurvePoint(42, 218), CurvePoint(55, 215)
            )
        ),
        TempCurve(
            id = "rosin_ladder",
            name = "Live Rosin Terpene Ladder (55s)",
            mode = CurveInterpMode.STEP,
            desc = "Low-temp connoisseur steps: 210°C (delicate monoterpenes), 222°C (mid-spectrum body), 235°C (clean cannabinoid finish without harshness).",
            pointsC = listOf(
                CurvePoint(0, 210), CurvePoint(18, 222), CurvePoint(38, 235), CurvePoint(55, 235)
            )
        ),
        TempCurve(
            id = "cold_start_ramp",
            name = "Cold-Start Smooth Ramp (55s)",
            mode = CurveInterpMode.SMOOTH,
            desc = "Continuous ascending ramp (205°C → 235°C) that sweeps gently through terpene boiling points like a low-temp cold-start.",
            pointsC = listOf(
                CurvePoint(0, 205), CurvePoint(20, 217), CurvePoint(40, 228), CurvePoint(55, 235)
            )
        ),
        TempCurve(
            id = "resin_cloud",
            name = "Live Resin / BHO Flavor → Cloud (50s)",
            mode = CurveInterpMode.STEP,
            desc = "Starts at 220°C for bright terps, steps to 232°C for balanced vapor, and finishes at 245°C to clear the chamber.",
            pointsC = listOf(
                CurvePoint(0, 220), CurvePoint(15, 232), CurvePoint(35, 245), CurvePoint(50, 245)
            )
        ),
        TempCurve(
            id = "flash_mellow",
            name = "Flash Melt → Mellow Finish (55s)",
            mode = CurveInterpMode.SMOOTH,
            desc = "Starts at 240°C for a quick initial melt, then coasts down to 212°C for smooth, cool backend pulls.",
            pointsC = listOf(
                CurvePoint(0, 240), CurvePoint(12, 228), CurvePoint(30, 219), CurvePoint(55, 212)
            )
        )
    )

    private val _allCurves = MutableStateFlow<List<TempCurve>>(emptyList())
    val allCurves: StateFlow<List<TempCurve>> = _allCurves.asStateFlow()

    private val _activeCurve = MutableStateFlow(builtinCurves.first())
    val activeCurve: StateFlow<TempCurve> = _activeCurve.asStateFlow()

    private val _runnerState = MutableStateFlow(CurveRunnerState())
    val runnerState: StateFlow<CurveRunnerState> = _runnerState.asStateFlow()

    init {
        reloadAllCurves()
        scope.launch {
            bleManager.telemetryTicks.collect { tick ->
                onTelemetryTick(tick.currentTemp, tick.remainingSec, tick.unitByte == 0x11)
            }
        }
    }

    private fun loadCustomCurves(): List<TempCurve> {
        val raw = prefs.getString("custom_curves", "[]") ?: "[]"
        val out = mutableListOf<TempCurve>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ptsArr = obj.getJSONArray("pointsC")
                val pts = mutableListOf<CurvePoint>()
                for (j in 0 until ptsArr.length()) {
                    val p = ptsArr.getJSONObject(j)
                    pts.add(CurvePoint(p.getInt("t"), p.getInt("tempC")))
                }
                if (pts.size >= 2) {
                    out.add(
                        TempCurve(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            mode = if (obj.optString("mode") == "step") CurveInterpMode.STEP else CurveInterpMode.SMOOTH,
                            desc = obj.optString("desc", "User-created custom curve."),
                            pointsC = pts.sortedBy { it.t }
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveCustomCurvesList(custom: List<TempCurve>) {
        val arr = JSONArray()
        for (c in custom) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("mode", c.mode.id)
            obj.put("desc", c.desc)
            val ptsArr = JSONArray()
            for (p in c.pointsC) {
                ptsArr.put(JSONObject().put("t", p.t).put("tempC", p.tempC))
            }
            obj.put("pointsC", ptsArr)
            arr.put(obj)
        }
        prefs.edit().putString("custom_curves", arr.toString()).apply()
    }

    private fun reloadAllCurves() {
        _allCurves.value = builtinCurves + loadCustomCurves()
    }

    fun selectCurve(id: String) {
        _allCurves.value.firstOrNull { it.id == id }?.let {
            _activeCurve.value = it
        }
    }

    fun setInterpMode(mode: CurveInterpMode) {
        _activeCurve.update { it.copy(mode = mode) }
    }

    fun updatePoint(idx: Int, t: Int? = null, tempC: Int? = null) {
        _activeCurve.update { curve ->
            val pts = curve.pointsC.toMutableList()
            if (idx !in pts.indices) return@update curve
            val old = pts[idx]
            pts[idx] = CurvePoint(
                t = (t ?: old.t).coerceIn(0, 120),
                tempC = (tempC ?: old.tempC).coerceIn(150, 340)
            )
            curve.copy(pointsC = pts.sortedBy { it.t })
        }
    }

    fun updatePointFromDrag(idx: Int, newT: Int?, newTempC: Int) {
        _activeCurve.update { curve ->
            val pts = curve.pointsC.toMutableList()
            if (idx !in pts.indices) return@update curve
            val old = pts[idx]
            val clampedT = if (newT != null && idx > 0 && idx < pts.lastIndex) {
                newT.coerceIn(pts[idx - 1].t + 1, pts[idx + 1].t - 1)
            } else {
                old.t
            }
            pts[idx] = CurvePoint(clampedT, newTempC.coerceIn(150, 340))
            curve.copy(pointsC = pts)
        }
    }

    fun addPoint() {
        _activeCurve.update { curve ->
            val last = curve.pointsC.lastOrNull() ?: CurvePoint(55, 220)
            val newPts = curve.pointsC + CurvePoint((last.t + 10).coerceAtMost(120), last.tempC)
            curve.copy(pointsC = newPts.sortedBy { it.t })
        }
    }

    fun removePoint(idx: Int) {
        _activeCurve.update { curve ->
            if (curve.pointsC.size <= 2 || idx !in curve.pointsC.indices) return@update curve
            val pts = curve.pointsC.toMutableList()
            pts.removeAt(idx)
            curve.copy(pointsC = pts)
        }
    }

    fun generateFromRange(startC: Int, endC: Int, durSec: Int, shape: CurveGenShape) {
        val sC = startC.coerceIn(150, 340)
        val eC = endC.coerceIn(150, 340)
        val dur = durSec.coerceIn(15, 120)
        val pts = mutableListOf<CurvePoint>()
        var mode = CurveInterpMode.SMOOTH
        val desc: String

        when (shape) {
            CurveGenShape.LINEAR -> {
                pts.add(CurvePoint(0, sC))
                pts.add(CurvePoint(dur, eC))
                desc = "Generated linear ramp over ${dur}s."
            }
            CurveGenShape.LADDER -> {
                mode = CurveInterpMode.STEP
                val midC = ((sC + eC) / 2.0).roundToInt()
                pts.add(CurvePoint(0, sC))
                pts.add(CurvePoint((dur * 0.33).roundToInt(), midC))
                pts.add(CurvePoint((dur * 0.68).roundToInt(), eC))
                pts.add(CurvePoint(dur, eC))
                desc = "Generated 3-step terp ladder over ${dur}s."
            }
            CurveGenShape.EXP -> {
                val k = 2.6
                val denom = 1.0 - exp(-k)
                for (i in 0..5) {
                    val u = i / 5.0
                    val factor = (exp(-k * u) - exp(-k)) / denom
                    pts.add(CurvePoint((u * dur).roundToInt(), (eC + (sC - eC) * factor).roundToInt()))
                }
                desc = "Generated exponential cooling/approach curve over ${dur}s."
            }
            CurveGenShape.EASE_IN -> {
                for (i in 0..5) {
                    val u = i / 5.0
                    val factor = u * u
                    pts.add(CurvePoint((u * dur).roundToInt(), (sC + (eC - sC) * factor).roundToInt()))
                }
                desc = "Generated flavor-hold curve over ${dur}s."
            }
        }
        _activeCurve.update { it.copy(mode = mode, desc = desc, pointsC = pts) }
    }

    fun saveAsCustom(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val cur = _activeCurve.value
        val newEntry = TempCurve(
            id = "custom_${System.currentTimeMillis()}",
            name = trimmed,
            mode = cur.mode,
            desc = "User-created custom curve.",
            pointsC = cur.pointsC.sortedBy { it.t }
        )
        val updated = loadCustomCurves() + newEntry
        saveCustomCurvesList(updated)
        reloadAllCurves()
        _activeCurve.value = newEntry
    }

    fun renameCustom(newName: String) {
        val cur = _activeCurve.value
        val trimmed = newName.trim()
        if (!cur.isCustom || trimmed.isEmpty()) return
        val updated = loadCustomCurves().map {
            if (it.id == cur.id) it.copy(name = trimmed) else it
        }
        saveCustomCurvesList(updated)
        reloadAllCurves()
        _activeCurve.value = cur.copy(name = trimmed)
    }

    fun deleteCustom() {
        val cur = _activeCurve.value
        if (!cur.isCustom) return
        val updated = loadCustomCurves().filterNot { it.id == cur.id }
        saveCustomCurvesList(updated)
        reloadAllCurves()
        _activeCurve.value = builtinCurves.first()
    }

    fun evalCurveTempC(elapsedSec: Int): Int {
        val curve = _activeCurve.value
        val pts = curve.pointsC.sortedBy { it.t }
        if (pts.isEmpty()) return 230
        if (elapsedSec <= pts.first().t) return pts.first().tempC
        if (elapsedSec >= pts.last().t) return pts.last().tempC
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            if (elapsedSec in a.t..b.t) {
                if (curve.mode == CurveInterpMode.STEP || b.t == a.t) {
                    return a.tempC
                }
                val frac = (elapsedSec - a.t).toDouble() / (b.t - a.t).toDouble()
                return (a.tempC + frac * (b.tempC - a.tempC)).roundToInt()
            }
        }
        return pts.last().tempC
    }

    fun evalCurveTempUnit(elapsedSec: Int, fahrenheit: Boolean): Int {
        val c = evalCurveTempC(elapsedSec)
        return if (fahrenheit) cToF(c) else c
    }

    fun startCurveSession() {
        val curve = _activeCurve.value
        val dur = curve.durationSec
        val fahrenheit = bleManager.state.value.isFahrenheit
        val startTemp = evalCurveTempUnit(0, fahrenheit)

        _runnerState.value = CurveRunnerState(
            running = true,
            phase = CurvePhase.PREHEAT,
            durationSec = dur,
            elapsedSec = 0,
            lastSentTemp = startTemp,
            liveTrace = emptyList()
        )
        bleManager.sendCcCommand(
            actionByte = 0xA5,
            targetPresetSlot = 0x00,
            waxTemp = startTemp,
            waxTimerSec = dur
        )
    }

    fun stopCurveSession() {
        _runnerState.update {
            it.copy(running = false, phase = CurvePhase.IDLE)
        }
        bleManager.sendCcCommand(actionByte = 0xAF, targetPresetSlot = 0x00)
    }

    private fun onTelemetryTick(curTemp: Int, remSec: Int, fahrenheit: Boolean) {
        val runner = _runnerState.value
        if (runner.running) {
            if (remSec == 0 && runner.phase == CurvePhase.ACTIVE) {
                _runnerState.update { it.copy(running = false, phase = CurvePhase.IDLE) }
                return
            }
            if (remSec > 0) {
                val elapsed = (runner.durationSec - remSec).coerceAtLeast(0)
                val phase = if (elapsed > 0) CurvePhase.ACTIVE else CurvePhase.PREHEAT
                val trace = (runner.liveTrace + TracePoint(elapsed, curTemp)).takeLast(300)
                val desired = evalCurveTempUnit(elapsed, fahrenheit)
                val sentTemp = if (desired != runner.lastSentTemp) {
                    bleManager.sendCcCommand(
                        actionByte = 0x00,
                        targetPresetSlot = 0x00,
                        waxTemp = desired,
                        waxTimerSec = runner.durationSec
                    )
                    desired
                } else {
                    runner.lastSentTemp
                }
                _runnerState.value = runner.copy(
                    phase = phase,
                    elapsedSec = elapsed,
                    lastSentTemp = sentTemp,
                    liveTrace = trace
                )
            }
        } else if (remSec > 0) {
            val dur = bleManager.state.value.waxTimerSec.coerceAtLeast(10)
            val elapsed = (dur - remSec).coerceAtLeast(0)
            _runnerState.update {
                it.copy(
                    elapsedSec = elapsed,
                    liveTrace = (it.liveTrace + TracePoint(elapsed, curTemp)).takeLast(300)
                )
            }
        }
    }

    companion object {
        fun cToF(tempC: Int): Int = (tempC * 9.0 / 5.0 + 32.0).roundToInt()
        fun fToC(tempF: Int): Int = ((tempF - 32.0) * 5.0 / 9.0).roundToInt()
    }
}

