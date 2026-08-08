package com.qcm.overlay.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qcm.overlay.R
import com.qcm.overlay.data.QuestionRepository
import kotlin.random.Random

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    /** A little burst of sparkles falling from the tapped option, purely decorative. */
    private fun burstSparkles(container: FrameLayout) {
        val chars = listOf("✨", "⭐", "💫")
        repeat(5) { i ->
            val sparkle = TextView(this)
            sparkle.text = chars[Random.nextInt(chars.size)]
            sparkle.textSize = 12f
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.marginEnd = dp(6 + Random.nextInt(24))
            sparkle.layoutParams = lp
            sparkle.alpha = 1f
            container.addView(sparkle)
            sparkle.animate()
                .translationY(dp(26 + Random.nextInt(16)).toFloat())
                .alpha(0f)
                .setStartDelay((i * 50).toLong())
                .setDuration(550)
                .withEndAction { container.removeView(sparkle) }
                .start()
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
        // small butterfly + sparkle burst when selected.
        val checkBoxes = mutableListOf<CheckBox>()
        val optionRows = mutableListOf<FrameLayout>()

        question.options.forEachIndexed { i, optionText ->
            val row = FrameLayout(this)
            row.background = ContextCompat.getDrawable(this, R.drawable.bg_option_unselected)
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            val rowParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rowParams.bottomMargin = dp(8)
            row.layoutParams = rowParams

            val innerContent = LinearLayout(this)
            innerContent.orientation = LinearLayout.HORIZONTAL
            innerContent.gravity = Gravity.CENTER_VERTICAL
            innerContent.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)

            val cb = CheckBox(this)
            cb.text = "${('A' + i)}. $optionText"
            cb.setTextColor(Color.BLACK)
            cb.buttonDrawable = null
            cb.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val butterfly = TextView(this)
            butterfly.text = "🦋"
            butterfly.textSize = 16f
            butterfly.visibility = View.GONE

            cb.setOnCheckedChangeListener { _, checked ->
                row.background = ContextCompat.getDrawable(
                    this, if (checked) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                butterfly.visibility = if (checked) View.VISIBLE else View.GONE
                if (checked) burstSparkles(row)
            }

            innerContent.addView(cb)
            innerContent.addView(butterfly)
            row.addView(innerContent)
            optionsContainer.addView(row)
            checkBoxes.add(cb)
            optionRows.add(row)
        }

        val ivSticker = view.findViewById<android.widget.ImageView>(R.id.ivSticker)
        val tvCaption = view.findViewById<TextView>(R.id.tvCaption)

        btnValidate.setOnClickListener {
            val selected: Set<Int> = checkBoxes.indices.filter { checkBoxes[it].isChecked }.toSet()
            val correct = question.correctOptionIds.toSet()

            val isFull = selected == correct
            val isPartial = !isFull && selected.isNotEmpty() && selected.all { it in correct }
            val counted = isFull || isPartial

            // Color each row: green = correctly picked, red = wrongly picked,
            // orange = correct answer you missed. No separate text needed.
            optionRows.forEachIndexed { i, row ->
                val isSelected = i in selected
                val isCorrect = i in correct
                val bgRes = when {
                    isSelected && isCorrect -> R.drawable.bg_option_correct_selected
                    isSelected && !isCorrect -> R.drawable.bg_option_wrong_selected
                    !isSelected && isCorrect -> R.drawable.bg_option_missed
                    else -> R.drawable.bg_option_unselected
                }
                row.background = ContextCompat.getDrawable(this, bgRes)
            }

            tvResult.visibility = View.VISIBLE
            tvResult.text = if (counted) "✅ Correct !" else "❌ Faux !"
            tvResult.setTextColor(Color.parseColor(if (counted) "#2E7D32" else "#C62828"))

            ivSticker.setImageResource(if (counted) R.drawable.sticker_correct else R.drawable.sticker_wrong)
            tvCaption.text = if (counted) "Nice dida !" else "My baby didn't sleep well"
            ivSticker.visibility = View.VISIBLE
            tvCaption.visibility = View.VISIBLE
            ivSticker.translationY = 200f
            tvCaption.alpha = 0f
            ivSticker.animate().translationY(0f).setDuration(380).start()
            tvCaption.animate().alpha(1f).setStartDelay(200).setDuration(200).start()

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
        params.gravity = Gravity.TOP
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
