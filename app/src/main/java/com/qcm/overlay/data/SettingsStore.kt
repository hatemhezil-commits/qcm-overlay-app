package com.qcm.overlay.data

import android.content.Context

enum class Intensity(val label: String, val minHours: Long, val maxHours: Long) {
    HIGH("Proche de l'examen (intensif)", 0, 1),   // every 0-60 min
    MEDIUM("Normal", 2, 5),                         // every 2-5h
    LOW("Éloigné (léger)", 6, 10);                  // every 6-10h

    companion object {
        fun fromName(name: String?): Intensity =
            values().firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Simple SharedPreferences wrapper for user-chosen filters:
 * which modules/lessons to draw questions from, and how often
 * (intensity) the overlay should appear.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("qcm_settings", Context.MODE_PRIVATE)

    fun getSelectedLessons(): Set<String>? {
        val stored = prefs.getStringSet("selected_lessons", null)
        return if (stored == null || stored.isEmpty()) null // null = all lessons
        else stored
    }

    fun setSelectedLessons(lessonKeys: Set<String>) {
        prefs.edit().putStringSet("selected_lessons", lessonKeys).apply()
    }

    fun getIntensity(): Intensity = Intensity.fromName(prefs.getString("intensity", null))

    fun setIntensity(intensity: Intensity) {
        prefs.edit().putString("intensity", intensity.name).apply()
    }

    fun isRunning(): Boolean = prefs.getBoolean("running", false)

    fun setRunning(running: Boolean) {
        prefs.edit().putBoolean("running", running).apply()
    }

    // --- Exam dates per module: used to auto-scale reminder intensity ---

    fun getExamModules(): Set<String> = prefs.getStringSet("exam_modules", emptySet()) ?: emptySet()

    fun getExamDate(module: String): Long? {
        val v = prefs.getLong("exam_date_$module", -1L)
        return if (v <= 0) null else v
    }

    fun setExamDate(module: String, epochMillis: Long) {
        prefs.edit().putLong("exam_date_$module", epochMillis).apply()
        val updated = getExamModules().toMutableSet()
        updated.add(module)
        prefs.edit().putStringSet("exam_modules", updated).apply()
    }

    fun clearExamDate(module: String) {
        prefs.edit().remove("exam_date_$module").apply()
        val updated = getExamModules().toMutableSet()
        updated.remove(module)
        prefs.edit().putStringSet("exam_modules", updated).apply()
    }
}
