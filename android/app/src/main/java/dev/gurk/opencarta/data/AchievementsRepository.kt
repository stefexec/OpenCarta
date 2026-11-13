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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    private val bleManager: CartaBleManager,
    private val historyRepo: SessionHistoryRepository,
    private val curveRepo: CurveRepository
) {
    private val prefs = context.getSharedPreferences("opencarta_achievements", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _summary = MutableStateFlow(AchievementsSummary())
    val summary: StateFlow<AchievementsSummary> = _summary.asStateFlow()

    private var lastObservedAllTime: Int = prefs.getInt("last_all_time_dabs", 0)
    private var lastDailyDate: String? = prefs.getString("last_daily_date", null)
    private var lastObservedDaily: Int = if (lastDailyDate == LocalDate.now().toString()) {
        prefs.getInt("last_daily_dabs", 0)
    } else {
        0
    }
    private var lastObservedMonthly: Int = prefs.getInt("last_monthly_dabs", 0)
    private var lastRemSec: Int = 0
    private var curveMasterUnlocked: Boolean = prefs.getBoolean("ach_curve_master", false)
    private var glowUpUnlocked: Boolean = prefs.getBoolean("ach_glow_up", false)
    private val unlockedIds: MutableSet<String> =
        (prefs.getStringSet("unlocked_ids", emptySet()) ?: emptySet()).toMutableSet()

    init {
        recompute()
        scope.launch {
            historyRepo.history.collect {
                recompute()
            }
        }
        scope.launch {
            bleManager.ledCustomized.collect {
                if (!glowUpUnlocked) {
                    glowUpUnlocked = true
                    prefs.edit().putBoolean("ach_glow_up", true).apply()
                    recompute()
                }
            }
        }
        scope.launch {
            curveRepo.runnerState.collect { rs ->
                if (rs.running && curveRepo.activeCurve.value.isCustom && !curveMasterUnlocked) {
                    curveMasterUnlocked = true
                    prefs.edit().putBoolean("ach_curve_master", true).apply()
                    recompute()
                }
            }
        }
        scope.launch {
            bleManager.state.collect { s ->
                // 0xaa counters: [0]=AllTime, [1]=Daily (UTC day on device), [2]=Weekly/Sub, [3]=Monthly
                val devAllTime = s.dabCounter1.toInt()
                val devDaily = s.dabCounter2.toInt()
                val devMonthly = s.dabCounter4.toInt()
                val localToday = LocalDate.now()
                val utcToday = LocalDate.now(ZoneOffset.UTC)
                val todayKey = localToday.toString()

                if (lastDailyDate != todayKey) {
                    lastDailyDate = todayKey
                    lastObservedDaily = 0
                    prefs.edit()
                        .putString("last_daily_date", todayKey)
                        .putInt("last_daily_dabs", 0)
                        .apply()
                }

                if (devAllTime > 0 || devDaily > 0 || devMonthly > 0) {
                    if (devAllTime > lastObservedAllTime && lastObservedAllTime > 0) {
                        recordTodayActivity()
                    }
                    lastObservedAllTime = devAllTime
                    if (localToday == utcToday) {
                        lastObservedDaily = devDaily
                        prefs.edit().putInt("last_daily_dabs", devDaily).apply()
                    }
                    lastObservedMonthly = devMonthly
                    prefs.edit()
                        .putInt("last_all_time_dabs", devAllTime)
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
                    if (LocalDate.now() != LocalDate.now(ZoneOffset.UTC)) {
                        val todayKey = LocalDate.now().toString()
                        if (lastDailyDate != todayKey) {
                            lastDailyDate = todayKey
                            lastObservedDaily = 0
                        }
                        lastObservedDaily += 1
                        prefs.edit()
                            .putString("last_daily_date", todayKey)
                            .putInt("last_daily_dabs", lastObservedDaily)
                            .apply()
                    }
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

    private fun entryEpochMs(entry: DabSessionEntry): Long {
        if (entry.timestampMs > 0L) return entry.timestampMs
        return try {
            val d = LocalDate.parse(entry.date)
            val t = LocalTime.parse(entry.time)
            LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun isTimeBetween(timeStr: String, start: LocalTime, end: LocalTime): Boolean {
        return try {
            val t = LocalTime.parse(timeStr)
            !t.isBefore(start) && !t.isAfter(end)
        } catch (_: Exception) {
            false
        }
    }

    private fun isRitualTime(timeStr: String): Boolean = timeStr == "04:20" || timeStr == "16:20"

    private fun maxScientistChain(completed: List<DabSessionEntry>): Int {
        val sorted = completed.mapNotNull { e ->
            val ts = entryEpochMs(e)
            if (ts > 0L) {
                val tc = if (e.unitSymbol == "°F") CurveRepository.fToC(e.temp) else e.temp
                ts to tc
            } else null
        }.sortedBy { it.first }
        if (sorted.isEmpty()) return 0
        var best = 1
        for (i in sorted.indices) {
            if (i >= 2 &&
                sorted[i].first - sorted[i - 2].first <= 3_600_000L &&
                sorted[i - 2].second < sorted[i - 1].second &&
                sorted[i - 1].second < sorted[i].second
            ) {
                return 3
            }
            if (i >= 1 &&
                sorted[i].first - sorted[i - 1].first <= 3_600_000L &&
                sorted[i - 1].second < sorted[i].second
            ) {
                best = maxOf(best, 2)
            }
        }
        return best
    }

    private fun maxRolling30MinCount(sessions: List<DabSessionEntry>): Int {
        val ts = sessions.map { entryEpochMs(it) }.filter { it > 0L }.sorted()
        if (ts.isEmpty()) return 0
        var left = 0
        var best = 1
        for (right in ts.indices) {
            while (ts[right] - ts[left] > 1_800_000L) {
                left++
            }
            best = maxOf(best, right - left + 1)
        }
        return best
    }

    private fun maxWeekendCount(history: SessionHistoryState): Int {
        val saturdays = mutableSetOf<LocalDate>()
        history.sessions.forEach { e ->
            try {
                val d = LocalDate.parse(e.date)
                when (d.dayOfWeek) {
                    DayOfWeek.SATURDAY -> saturdays.add(d)
                    DayOfWeek.SUNDAY -> saturdays.add(d.minusDays(1))
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        history.dailyOverrides.keys.forEach { k ->
            try {
                val d = LocalDate.parse(k)
                when (d.dayOfWeek) {
                    DayOfWeek.SATURDAY -> saturdays.add(d)
                    DayOfWeek.SUNDAY -> saturdays.add(d.minusDays(1))
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        return saturdays.maxOfOrNull { sat ->
            history.countForDay(sat) + history.countForDay(sat.plusDays(1))
        } ?: 0
    }

    private fun maxSingleDayCount(history: SessionHistoryState, currentDaily: Int): Int {
        val dates = mutableSetOf<LocalDate>()
        history.sessions.forEach { e ->
            try { dates.add(LocalDate.parse(e.date)) } catch (_: Exception) {}
        }
        history.dailyOverrides.keys.forEach { k ->
            try { dates.add(LocalDate.parse(k)) } catch (_: Exception) {}
        }
        val historyMax = dates.maxOfOrNull { history.countForDay(it) } ?: 0
        return maxOf(historyMax, currentDaily)
    }

    private fun recompute() {
        val s = bleManager.state.value
        val history = historyRepo.history.value
        val sessions = history.sessions
        val completed = sessions.filter { it.completedNaturally }

        val localToday = LocalDate.now()
        val utcToday = LocalDate.now(ZoneOffset.UTC)
        val allTime = if (s.dabCounter1 > 0) s.dabCounter1.toInt() else lastObservedAllTime
        val daily = if (s.isConnected && s.dabCounter1 > 0 && localToday == utcToday) {
            s.dabCounter2.toInt()
        } else {
            if (lastDailyDate == localToday.toString()) lastObservedDaily else 0
        }
        val monthly = if (s.isConnected && s.dabCounter1 > 0) s.dabCounter4.toInt() else lastObservedMonthly
        val streak = computeStreakDays()

        val hasFirstFlight = sessions.isNotEmpty()
        val hasRitual = sessions.any { isRitualTime(it.time) || isRitualTime(it.startTime) }
        val has710Date = sessions.any { it.date.endsWith("-07-10") }
        val hasWakeAndBake = completed.any {
            isTimeBetween(it.time, LocalTime.of(5, 0), LocalTime.of(8, 0)) ||
                isTimeBetween(it.startTime, LocalTime.of(5, 0), LocalTime.of(8, 0))
        }
        val hasNightOwl = sessions.any {
            isTimeBetween(it.startTime, LocalTime.of(2, 0), LocalTime.of(4, 30))
        }
        val hasTerp = completed.any {
            if (it.unitSymbol == "°F") it.temp < 440 else it.temp < 225
        }
        val hasLava = completed.any {
            if (it.unitSymbol == "°F") it.temp >= 560 else it.temp >= 295
        }
        val maxCompletedDur = completed.maxOfOrNull { it.durationSec } ?: 0
        val scientistChain = maxScientistChain(completed)
        val rolling30m = maxRolling30MinCount(sessions)
        val weekendMax = maxWeekendCount(history)
        val dayMax = maxSingleDayCount(history, daily)
        val hasLowBatteryFinish = completed.any { it.startBattery in 1..10 }

        val rawList = listOf(
            // Session Count Milestones
            Achievement("first_flight", "First Flight", "Complete your first session recorded in the app", "1ST", if (hasFirstFlight) 1 else 0, 1),
            Achievement("dab_100", "100 Dab Club", "Complete 100 sessions", "100", allTime.coerceAtMost(100), 100),
            Achievement("high_council", "The High Council", "Reach 420 total sessions", "420", allTime.coerceAtMost(420), 420),
            Achievement("dab_500", "500 Dab Master", "Complete 500 sessions", "500", allTime.coerceAtMost(500), 500),
            Achievement("club_710", "710 Club", "Reach 710 total sessions or log one on July 10th", "710", if (has710Date) 710 else allTime.coerceAtMost(710), 710),
            Achievement("dab_1000", "1000 Dab Legend", "Complete 1,000 sessions", "1000", allTime.coerceAtMost(1000), 1000),
            Achievement("dab_2000", "2000 Dab Warrior", "Complete 2,000 sessions", "2000", allTime.coerceAtMost(2000), 2000),
            Achievement("dab_3000", "3000 Dab Elite", "Complete 3,000 sessions", "3000", allTime.coerceAtMost(3000), 3000),

            // Culture & Time of Day
            Achievement("the_ritual", "The Ritual", "Hit a session at exactly 4:20 AM or 4:20 PM", "4:20", if (hasRitual) 1 else 0, 1),
            Achievement("wake_and_bake", "Wake & Bake", "Complete a session between 5:00 AM and 8:00 AM", "5-8A", if (hasWakeAndBake) 1 else 0, 1),
            Achievement("night_owl", "Night Owl", "Start a session between 2:00 AM and 4:30 AM", "2-4A", if (hasNightOwl) 1 else 0, 1),

            // Temperature & Inhalation Styles
            Achievement("terp_connoisseur", "Terp Connoisseur", "Complete a low-temp session under 440°F / 225°C", "LOW", if (hasTerp) 1 else 0, 1),
            Achievement("lava_walker", "Lava Walker", "Complete a high-temp roast at 560°F+ / 295°C+", "HOT", if (hasLava) 1 else 0, 1),
            Achievement("iron_lungs", "Iron Lungs", "Run a continuous session of 60 seconds or longer", "60S+", maxCompletedDur.coerceAtMost(60), 60),
            Achievement("curve_master", "Curve Master", "Save and execute your first custom heating curve", "CRV", if (curveMasterUnlocked) 1 else 0, 1),
            Achievement("the_scientist", "The Scientist", "3 consecutive escalating-temp sessions within 1 hour", "SCI", scientistChain.coerceAtMost(3), 3),

            // Endurance & Streaks
            Achievement("chain_smoker", "Chain Smoker", "Log 5 sessions within a rolling 30-minute window", "5/30", rolling30m.coerceAtMost(5), 5),
            Achievement("centurion", "Centurion", "Complete 10 sessions in a single 24-hour day", "10D", dayMax.coerceAtMost(10), 10),
            Achievement("weekend_warrior", "Weekend Warrior", "Log 15+ sessions across a single Saturday & Sunday", "15W", weekendMax.coerceAtMost(15), 15),
            Achievement("streak_7", "One Week Streak", "7 consecutive days", "7D", streak.coerceAtMost(7), 7, isStreak = true),
            Achievement("streak_31", "One Month Streak", "31 consecutive days", "31D", streak.coerceAtMost(31), 31, isStreak = true),

            // Hardware & App Exploration
            Achievement("living_on_the_edge", "Living on the Edge", "Finish a session starting with ≤ 10% battery", "≤10%", if (hasLowBatteryFinish) 1 else 0, 1),
            Achievement("glow_up", "Glow Up", "Customize and upload an RGB LED light profile", "RGB", if (glowUpUnlocked) 1 else 0, 1)
        )

        var unlockedChanged = false
        val list = rawList.map { ach ->
            if (ach.unlocked) {
                if (unlockedIds.add(ach.id)) unlockedChanged = true
                ach
            } else if (ach.id in unlockedIds) {
                ach.copy(progress = ach.target)
            } else {
                ach
            }
        }
        if (unlockedChanged) {
            prefs.edit().putStringSet("unlocked_ids", unlockedIds.toSet()).apply()
        }

        _summary.value = AchievementsSummary(
            allTimeDabs = allTime,
            dailyDabs = daily,
            monthlyDabs = monthly,
            currentStreakDays = streak,
            achievements = list
        )
    }
}
