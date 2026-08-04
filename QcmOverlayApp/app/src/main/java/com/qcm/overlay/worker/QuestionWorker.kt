package com.qcm.overlay.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.qcm.overlay.service.OverlayService

class QuestionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val intent = Intent(applicationContext, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        // Reschedule the next random-time question right after firing this one
        QuestionScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}
