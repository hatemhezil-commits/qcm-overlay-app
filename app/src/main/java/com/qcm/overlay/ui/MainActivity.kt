package com.qcm.overlay.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qcm.overlay.R
import com.qcm.overlay.data.Intensity
import com.qcm.overlay.data.QuestionRepository
import com.qcm.overlay.data.SettingsStore
import com.qcm.overlay.worker.QuestionScheduler
import com.qcm.overlay.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore

    // lessonKey ("module||lesson") -> its CheckBox
    private val lessonCheckBoxes = mutableMapOf<String, CheckBox>()
    // module -> the TextView showing its exam date / countdown
    private val examDateLabels = mutableMapOf<String, TextView>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsStore(this)

        buildLessonsList()
        setupIntensityRadios()
        refreshUi()

        binding.btnPermission.setOnClickListener {
            if (!hasOverlayPermission()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.btnTestNow.setOnClickListener {
            if (!hasOverlayPermission()) return@setOnClickListener
            saveLessonSelection()
            val intent = Intent(this, com.qcm.overlay.service.OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.btnToggleService.setOnClickListener {
            if (!hasOverlayPermission()) return@setOnClickListener
            saveLessonSelection()
            val isRunning = settings.isRunning()
            if (isRunning) {
                QuestionScheduler.stop(this)
            } else {
                QuestionScheduler.start(this)
            }
            settings.setRunning(!isRunning)
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    /**
     * Builds, per module: a bold header, an exam-date row (set/clear date +
     * countdown label), then one indented checkbox per lesson in that module.
     */
    private fun buildLessonsList() {
        val moduleLessonMap = QuestionRepository(applicationContext).getModuleLessonMap()
        val selected = settings.getSelectedLessons()
        binding.modulesContainer.removeAllViews()
        lessonCheckBoxes.clear()
        examDateLabels.clear()

        for ((module, lessons) in moduleLessonMap) {
            val header = TextView(this)
            header.text = module
            header.setTextColor(Color.parseColor("#0A2540"))
            header.setTypeface(null, Typeface.BOLD)
            header.textSize = 15f
            header.setPadding(0, 20, 0, 2)
            binding.modulesContainer.addView(header)

            // --- exam date row ---
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val dateBtn = Button(this)
            dateBtn.text = "📅 تاريخ الامتحان"
            dateBtn.textSize = 12f
            dateBtn.setPadding(12, 4, 12, 4)
            dateBtn.setOnClickListener { showDatePicker(module) }
            row.addView(dateBtn)

            val label = TextView(this)
            label.textSize = 12f
            label.setPadding(12, 0, 0, 0)
            label.setTextColor(Color.parseColor("#2E7D32"))
            examDateLabels[module] = label
            row.addView(label)

            binding.modulesContainer.addView(row)
            updateExamLabel(module)

            for (lesson in lessons) {
                val key = "$module||$lesson"

                val lessonRow = LinearLayout(this)
                lessonRow.orientation = LinearLayout.HORIZONTAL
                lessonRow.gravity = Gravity.CENTER_VERTICAL

                val cb = CheckBox(this)
                cb.text = lesson
                cb.isChecked = selected == null || selected.contains(key)
                cb.setPadding(32, 0, 0, 0)
                lessonCheckBoxes[key] = cb

                val trainBtn = Button(this)
                trainBtn.text = "🎯 تدريب"
                trainBtn.textSize = 11f
                trainBtn.setPadding(8, 2, 8, 2)
                trainBtn.setOnClickListener {
                    com.qcm.overlay.ui.QuizActivity.start(this, module, lesson)
                }

                lessonRow.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                lessonRow.addView(trainBtn)
                binding.modulesContainer.addView(lessonRow)
            }
        }
    }

    private fun showDatePicker(module: String) {
        val cal = Calendar.getInstance()
        val existing = settings.getExamDate(module)
        if (existing != null) cal.timeInMillis = existing

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth, 0, 0, 0)
                settings.setExamDate(module, picked.timeInMillis)
                updateExamLabel(module)
                if (settings.isRunning()) QuestionScheduler.start(this)
                refreshUi()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateExamLabel(module: String) {
        val label = examDateLabels[module] ?: return
        val examDate = settings.getExamDate(module)
        if (examDate == null) {
            label.text = "غير محدد"
            label.setTextColor(Color.parseColor("#999999"))
            return
        }
        val daysLeft = TimeUnit.MILLISECONDS.toDays(examDate - System.currentTimeMillis())
        label.text = when {
            daysLeft < 0 -> "${dateFormat.format(examDate)} (انتهى)"
            daysLeft == 0L -> "${dateFormat.format(examDate)} (اليوم!)"
            else -> "${dateFormat.format(examDate)} (باقي $daysLeft يوم)"
        }
        label.setTextColor(
            when {
                daysLeft in 0..3 -> Color.parseColor("#C62828")
                daysLeft in 4..14 -> Color.parseColor("#EF6C00")
                else -> Color.parseColor("#2E7D32")
            }
        )
    }

    private fun saveLessonSelection() {
        val checkedCount = lessonCheckBoxes.values.count { it.isChecked }
        if (checkedCount == 0 || checkedCount == lessonCheckBoxes.size) {
            settings.setSelectedLessons(emptySet())
        } else {
            val chosen = lessonCheckBoxes.filterValues { it.isChecked }.keys
            settings.setSelectedLessons(chosen)
        }
    }

    private fun setupIntensityRadios() {
        when (settings.getIntensity()) {
            Intensity.HIGH -> binding.radioHigh.isChecked = true
            Intensity.MEDIUM -> binding.radioMedium.isChecked = true
            Intensity.LOW -> binding.radioLow.isChecked = true
        }

        binding.radioIntensity.setOnCheckedChangeListener { _, checkedId ->
            val intensity = when (checkedId) {
                binding.radioHigh.id -> Intensity.HIGH
                binding.radioLow.id -> Intensity.LOW
                else -> Intensity.MEDIUM
            }
            settings.setIntensity(intensity)
            if (settings.isRunning()) {
                QuestionScheduler.start(this)
            }
            refreshUi()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun refreshUi() {
        val granted = hasOverlayPermission()
        val isRunning = settings.isRunning()
        val effectiveIntensity = QuestionScheduler.computeEffectiveIntensity(this)

        binding.tvStatus.text = when {
            !granted -> getString(R.string.permission_needed)
            isRunning -> "التذكير يعمل ✅ — ${effectiveIntensity.label}"
            else -> "التذكير متوقف"
        }
        binding.btnPermission.visibility = if (granted) View.GONE else View.VISIBLE
        binding.btnToggleService.text = getString(if (isRunning) R.string.stop_service else R.string.start_service)
    }
}
