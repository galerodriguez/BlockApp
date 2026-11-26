package com.blockapp.data

import java.util.Calendar

object Schedule {
    /** true si los bloqueos deben aplicarse ahora mismo (horario general). */
    fun isActive(now: Calendar = Calendar.getInstance()): Boolean {
        if (!Prefs.scheduleEnabled) return true
        return inWindow(Prefs.scheduleStart, Prefs.scheduleEnd, Prefs.scheduleDays, now)
    }

    /**
     * true si [now] está dentro de la franja start–end (minutos desde medianoche) de los días dados.
     * Si end < start la franja cruza la medianoche (ej. 22:00 → 07:00) y la madrugada
     * cuenta como del día anterior.
     */
    fun inWindow(start: Int, end: Int, days: Set<Int>, now: Calendar = Calendar.getInstance()): Boolean {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = dayIndex(now)
        return if (start <= end) {
            today in days && minute in start until end
        } else {
            val yesterday = if (today == 1) 7 else today - 1
            (today in days && minute >= start) || (yesterday in days && minute < end)
        }
    }

    /** Calendar usa 1 = domingo; aquí 1 = lunes … 7 = domingo. */
    private fun dayIndex(c: Calendar): Int = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

    /** Ej. "L-V", "S, D", "Todos los días". */
    fun formatDays(days: Set<Int>): String = when (days) {
        (1..7).toSet() -> "todos los días"
        (1..5).toSet() -> "L-V"
        setOf(6, 7) -> "fines de semana"
        else -> days.sorted().joinToString(", ") { DAY_LETTERS[it - 1] }
    }
}

/** Horario PERMITIDO de una app: fuera de él la app queda bloqueada. */
data class AppSchedule(val start: Int, val end: Int, val days: Set<Int>) {
    fun allowsNow(): Boolean = Schedule.inWindow(start, end, days)

    fun describe(): String = "${Schedule.format(start)}–${Schedule.format(end)} · ${Schedule.formatDays(days)}"

    fun encode(): String = "$start,$end,${days.sorted().joinToString("")}"

    companion object {
        val DEFAULT = AppSchedule(18 * 60, 20 * 60, (1..7).toSet())

        fun decode(s: String): AppSchedule? {
            val parts = s.split(",")
            if (parts.size != 3) return null
            val start = parts[0].toIntOrNull() ?: return null
            val end = parts[1].toIntOrNull() ?: return null
            val days = parts[2].mapNotNull { it.digitToIntOrNull() }.filter { it in 1..7 }.toSet()
            if (start !in 0 until 24 * 60 || end !in 0 until 24 * 60 || days.isEmpty()) return null
            return AppSchedule(start, end, days)
        }
    }
}
