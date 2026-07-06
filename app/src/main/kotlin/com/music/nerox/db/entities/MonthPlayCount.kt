package com.music.nerox.db.entities

import androidx.room.ColumnInfo

data class MonthPlayCount(
    @ColumnInfo(name = "monthKey") val monthKey: Int,
    @ColumnInfo(name = "totalCount") val totalCount: Int
) {
    val year: Int get() = monthKey / 100
    val month: Int get() = monthKey % 100
}
