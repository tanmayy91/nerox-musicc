package com.music.vivi.ui.screens.wrapped
import java.util.Calendar

object WrappedConstants {
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

    val YEAR = if (currentMonth == Calendar.JANUARY) currentYear - 1 else currentYear
    val PLAYLIST_NAME = "Nerox Wrapped $YEAR"
}
