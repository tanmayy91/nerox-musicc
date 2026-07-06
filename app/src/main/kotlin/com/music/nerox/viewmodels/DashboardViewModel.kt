package com.music.nerox.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.nerox.db.MusicDatabase
import com.music.nerox.db.entities.HourPlayTime
import com.music.nerox.db.entities.MonthPlayCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {

    private fun dayBoundsUtcMs(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return start to end
    }

    val today: LocalDate = LocalDate.now()

    val todayPlayTimeMs: StateFlow<Long> = run {
        val (start, end) = dayBoundsUtcMs(today)
        database.getTotalPlayTimeInRange(start, end)
            .map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0L)
    }

    val hourlyPlayTime: StateFlow<List<HourPlayTime>> = run {
        val (start, end) = dayBoundsUtcMs(today)
        database.getHourlyPlayTime(start, end)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    val weeklyPlayTimes: StateFlow<List<Long>> = combine(
        (0..6).map { offset ->
            val day = today.minusDays((6 - offset).toLong())
            val (start, end) = dayBoundsUtcMs(day)
            database.getTotalPlayTimeInRange(start, end).map { it ?: 0L }
        }
    ) { array -> array.toList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, List(7) { 0L })

    val monthlyPlayCounts: StateFlow<List<MonthPlayCount>> =
        database.getMonthlyPlayCounts()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val peakMonth: StateFlow<MonthPlayCount?> =
        database.getMonthlyPlayCounts()
            .map { list -> list.maxByOrNull { it.totalCount } }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val yearMonthlyPlayTimes: StateFlow<List<Long>> = run {
        val currentYear = today.year
        combine(
            (1..12).map { month ->
                val ym = YearMonth.of(currentYear, month)
                val start = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                val end = ym.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
                database.getTotalPlayTimeInRange(start, end).map { it ?: 0L }
            }
        ) { array -> array.toList() }
            .stateIn(viewModelScope, SharingStarted.Lazily, List(12) { 0L })
    }

    val allTimePlayTimeMs: StateFlow<Long> = database.getTotalPlayTimeInRange(0L, Long.MAX_VALUE / 2)
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val allTimeSongsCount: StateFlow<Int> = database.getUniqueSongCountInRange(0L, Long.MAX_VALUE / 2)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val allTimeArtistsCount: StateFlow<Int> = database.getUniqueArtistCountInRange(0L, Long.MAX_VALUE / 2)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
}
