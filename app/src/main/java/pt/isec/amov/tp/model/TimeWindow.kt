package pt.isec.amov.tp.model

import java.util.Calendar

data class TimeWindow(
    val name: String = "",
    val daysOfWeek: List<Int> = emptyList(),
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59
) {
    fun isActiveNow(): Boolean {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        // 1. Verificar Dia
        if (daysOfWeek.isNotEmpty() && !daysOfWeek.contains(currentDay)) {
            return false
        }

        // 2. Verificar Hora de Início
        if (currentHour < startHour || (currentHour == startHour && currentMinute < startMinute)) {
            return false
        }

        // 3. Verificar Hora de Fim
        if (currentHour > endHour || (currentHour == endHour && currentMinute > endMinute)) {
            return false
        }

        return true
    }
}