package com.qcm.overlay.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.qcm.overlay.R
import com.qcm.overlay.data.QuestionRepository

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var repository: QuestionRepository

    companion object {
        private const val CHANNEL_ID = "qcm_overlay_channel"
        private const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = QuestionRepository(applicationContext)
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showQuestionOverlay()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Rappels QCM", NotificationManager.IMPORTANCE_MIN
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QCM Overlay")
            .setContentText("Prêt à afficher une question")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showQuestionOverlay() {
        if (overlayView != null) return // already showing one

        val settings = com.qcm.overlay.data.SettingsStore(this)
        val question = repository.getRandomQuestion(settings.getSelectedLessons()) ?: return stopSelf()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_question, null)
        overlayView = view

        val tvModuleLesson = view.findViewById<TextView>(R.id.tvModuleLesson)
        val tvQuestion = view.findViewById<TextView>(R.id.tvQuestion)
        val optionsContainer = view.findViewById<LinearLayout>(R.id.optionsContainer)
        val tvResult = view.findViewById<TextView>(R.id.tvResult)
        val btnValidate = view.findViewById<android.widget.Button>(R.id.btnValidate)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        tvModuleLesson.text = "${question.module} • ${question.lesson}"
        tvQuestion.text = question.text

        // Always render checkboxes, whether the question has one or several
        // correct answers -> the UI never reveals which type it is.
        // Each option is prefixed with a letter (A, B, C...) and shows a
        // small butterfly when selected.
        val checkBoxes = mutableListOf<CheckBox>()
        question.options.forEachIndexed { i, optionText ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL

            val cb = CheckBox(this)
            cb.text = "${('A' + i)}. $optionText"
            cb.setTextColor(Color.BLACK)
            cb.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val butterfly = TextView(this)
            butterfly.text = "🦋"
            butterfly.textSize = 16f
            butterfly.visibility = View.GONE

            cb.setOnCheckedChangeListener { _, checked ->
                butterfly.visibility = if (checked) View.VISIBLE else View.GONE
            }

            row.addView(cb)
            row.addView(butterfly)
            optionsContainer.addView(row)
            checkBoxes.add(cb)
        }

        val ivSticker = view.findViewById<android.widget.ImageView>(R.id.ivSticker)
        val tvCaption = view.findViewById<TextView>(R.id.tvCaption)
        val resultStickerRow = view.findViewById<LinearLayout>(R.id.resultStickerRow)

        btnValidate.setOnClickListener {
            val selected: List<Int> = checkBoxes.indices.filter { checkBoxes[it].isChecked }

            val isCorrect = selected.toSet() == question.correctOptionIds.toSet()
            val correctLetters = question.correctOptionIds.sorted().joinToString(", ") { ('A' + it).toString() }

            tvResult.visibility = View.VISIBLE
            tvResult.text = if (isCorrect) {
                "✅ Correct !"
            } else {
                "❌ Faux. Bonne réponse : $correctLetters" +
                    if (question.explanation.isNotBlank()) "\n${question.explanation}" else ""
            }
            tvResult.setTextColor(if (isCorrect) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))

            ivSticker.setImageResource(if (isCorrect) R.drawable.sticker_correct else R.drawable.sticker_wrong)
            tvCaption.text = if (isCorrect) "Nice dida !" else "My baby didn't sleep well"
            resultStickerRow.visibility = View.VISIBLE
            resultStickerRow.translationX = 400f
            resultStickerRow.animate().translationX(0f).setDuration(350).start()

            btnValidate.visibility = View.GONE
            checkBoxes.forEach { it.isEnabled = false }
        }

        btnClose.setOnClickListener { removeOverlay() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP
        params.y = 100

        windowManager.addView(view, params)
        vibrateOnAppear()
    }

    private fun vibrateOnAppear() {
        try {
            val vibrator: android.os.Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val manager = getSystemService(android.os.VibratorManager::class.java)
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (_: Exception) {
            // vibration is a nice-to-have; never crash the overlay because of it
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        stopSelf()
    }
}
