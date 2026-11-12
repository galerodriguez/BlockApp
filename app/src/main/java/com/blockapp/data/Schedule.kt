package com.blockapp.data

import java.util.Calendar

object Schedule {
    /** true si los bloqueos deben aplicarse ahora mismo. */
    fun isActive(now: Calendar = Calendar.getInstance()): Boolean {
        if (!Prefs.scheduleEnabled) return true
        val start = Prefs.scheduleStart
        val end = Prefs.scheduleEnd
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = dayIndex(now)

        return if (start <= end) {
            today in Prefs.scheduleDays && minute in start until end
        } else {
            // Horario nocturno, ej. 22:00 → 07:00: la parte de madrugada cuenta como del día anterior
            val yesterday = if (today == 1) 7 else today - 1
            (today in Prefs.scheduleDays && minute >= start) ||
                (yesterday in Prefs.scheduleDays && minute < end)
        }
    }

    /** Calendar usa 1 = domingo; aquí 1 = lunes … 7 = domingo. */
    private fun dayIndex(c: Calendar): Int = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
