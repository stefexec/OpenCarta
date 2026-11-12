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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Achievement(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeSub: String,
    val progress: Int,
    val target: Int,
    val isStreak: Boolean = false
) {
    val unlocked: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

data class AchievementsSummary(
    val allTimeDabs: Int = 0,
    val dailyDabs: Int = 0,
    val monthlyDabs: Int = 0,
    val currentStreakDays: Int = 0,
    val achievements: List<Achievement> = emptyList()
)

class AchievementsRepository(
    context: Context,
    private val bleManager: CartaBleManager
) {
    private val prefs = context.getSharedPreferences("opencarta_achievements", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _summary = MutableStateFlow(AchievementsSummary())
    val summary: StateFlow<AchievementsSummary> = _summary.asStateFlow()

    private var lastObservedAllTime: Int = prefs.getInt("last_all_time_dabs", 0)
    private var lastObservedDaily: Int = prefs.getInt("last_daily_dabs", 0)
    private var lastObservedMonthly: Int = prefs.getInt("last_monthly_dabs", 0)
    private var lastRemSec: Int = 0

    init {
        recompute()
        scope.launch {
            bleManager.state.collect { s ->
                // 0xaa counters: [0]=AllTime, [1]=Daily, [2]=Weekly/Sub, [3]=Monthly
                val devAllTime = s.dabCounter1.toInt()
                val devDaily = s.dabCounter2.toInt()
                val devMonthly = s.dabCounter4.toInt()

                if (devAllTime > 0 || devDaily > 0 || devMonthly > 0) {
                    if (devAllTime > lastObservedAllTime && lastObservedAllTime > 0) {
                        recordTodayActivity()
                    }
                    lastObservedAllTime = devAllTime
                    lastObservedDaily = devDaily
                    lastObservedMonthly = devMonthly
                    prefs.edit()
                        .putInt("last_all_time_dabs", devAllTime)
                        .putInt("last_daily_dabs", devDaily)
                        .putInt("last_monthly_dabs", devMonthly)
                        .apply()
                    recompute()
                }
            }
        }
        scope.launch {
            bleManager.telemetryTicks.collect { tick ->
                if (lastRemSec > 0 && tick.remainingSec == 0) {
                    recordTodayActivity()
                    recompute()
                }
                lastRemSec = tick.remainingSec
            }
        }
    }

    private fun recordTodayActivity() {
        val todayKey = LocalDate.now().toString()
        val activeDates = (prefs.getStringSet("active_dates", emptySet()) ?: emptySet()).toMutableSet()
        if (activeDates.add(todayKey)) {
            prefs.edit().putStringSet("active_dates", activeDates).apply()
        }
    }

    private fun computeStreakDays(): Int {
        val raw = prefs.getStringSet("active_dates", emptySet()) ?: emptySet()
        if (raw.isEmpty()) return if (lastObservedDaily > 0) 1 else 0
        val dates = raw.mapNotNull {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }.sortedDescending()
        if (dates.isEmpty()) return if (lastObservedDaily > 0) 1 else 0

        val today = LocalDate.now()
        val latest = dates.first()
        if (ChronoUnit.DAYS.between(latest, today) > 1) return if (lastObservedDaily > 0) 1 else 0

        var streak = 1
        for (i in 0 until dates.lastIndex) {
            val diff = ChronoUnit.DAYS.between(dates[i + 1], dates[i])
            if (diff == 1L) streak++ else break
        }
        return streak
    }

    private fun recompute() {
        val s = bleManager.state.value
        val allTime = if (s.dabCounter1 > 0) s.dabCounter1.toInt() else lastObservedAllTime
        val daily = if (s.isConnected && s.dabCounter1 > 0) s.dabCounter2.toInt() else lastObservedDaily
        val monthly = if (s.isConnected && s.dabCounter1 > 0) s.dabCounter4.toInt() else lastObservedMonthly
        val streak = computeStreakDays()

        val list = listOf(
            Achievement("dab_100", "100 Dab Club", "Complete 100 sessions", "100", allTime.coerceAtMost(100), 100),
            Achievement("dab_500", "500 Dab Master", "Complete 500 sessions", "500", allTime.coerceAtMost(500), 500),
            Achievement("dab_1000", "1000 Dab Legend", "Complete 1,000 sessions", "1000", allTime.coerceAtMost(1000), 1000),
            Achievement("dab_2000", "2000 Dab Warrior", "Complete 2,000 sessions", "2000", allTime.coerceAtMost(2000), 2000),
            Achievement("dab_3000", "3000 Dab Elite", "Complete 3,000 sessions", "3000", allTime.coerceAtMost(3000), 3000),
            Achievement("streak_7", "One Week Streak", "7 consecutive days", "7D", streak.coerceAtMost(7), 7, isStreak = true),
            Achievement("streak_31", "One Month Streak", "31 consecutive days", "31D", streak.coerceAtMost(31), 31, isStreak = true)
        )

        _summary.value = AchievementsSummary(
            allTimeDabs = allTime,
            dailyDabs = daily,
            monthlyDabs = monthly,
            currentStreakDays = streak,
            achievements = list
        )
    }
}
