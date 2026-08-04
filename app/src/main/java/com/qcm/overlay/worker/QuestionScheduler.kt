package com.qcm.overlay.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.qcm.overlay.data.Intensity
import com.qcm.overlay.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Instead of fixed daily alarms, this schedules ONE random-delay job at a time.
 * Each time QuestionWorker fires, it immediately schedules the next one with a
 * new random delay. The delay range comes from the *effective* intensity:
 * if the user set an exam date for one of the currently-selected modules, the
 * nearest such date overrides the manually chosen Intensity (closer exam =
 * more frequent reminders). Otherwise the manual Intensity radio applies.
 */
object QuestionScheduler {

    private const val WORK_TAG = "qcm_question_chain"

    fun start(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        scheduleNext(context)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    fun scheduleNext(context: Context) {
        val intensity = computeEffectiveIntensity(context)
        val request = if (intensity == Intensity.HIGH) {
            val delayMinutes = Random.nextLong(20, 61)
            OneTimeWorkRequestBuilder<QuestionWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
        } else {
            val delayHours = Random.nextLong(intensity.minHours, intensity.maxHours + 1)
            OneTimeWorkRequestBuilder<QuestionWorker>()
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .addTag(WORK_TAG)
                .build()
        }
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * If an exam date is set for any module currently relevant to the user's
     * lesson selection, use the nearest one to decide intensity:
     * <=3 days away -> HIGH, <=14 days -> MEDIUM, further -> LOW.
     * Falls back to the manually chosen Intensity when no relevant exam date exists.
     */
    fun computeEffectiveIntensity(context: Context): Intensity {
        val settings = SettingsStore(context)
        val selectedLessons = settings.getSelectedLessons()
        val examModules = settings.getExamModules()

        val relevantModules = if (selectedLessons == null) {
            examModules
        } else {
            examModules.filter { module -> selectedLessons.any { it.startsWith("$module||") } }.toSet()
        }

        if (relevantModules.isEmpty()) return settings.getIntensity()

        val now = System.currentTimeMillis()
        val minDays = relevantModules
            .mapNotNull { settings.getExamDate(it) }
            .minOfOrNull { examMillis -> (examMillis - now) / (1000 * 60 * 60 * 24) }
            ?: return settings.getIntensity()

        return when {
            minDays <= 3 -> Intensity.HIGH
            minDays <= 14 -> Intensity.MEDIUM
            else -> Intensity.LOW
        }
    }
}
