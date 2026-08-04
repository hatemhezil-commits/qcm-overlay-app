package com.qcm.overlay.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.qcm.overlay.data.Question
import com.qcm.overlay.data.QuestionRepository
import com.qcm.overlay.databinding.ActivityQuizBinding

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODULE = "module"
        const val EXTRA_LESSON = "lesson"

        fun start(context: Context, module: String, lesson: String) {
            val intent = Intent(context, QuizActivity::class.java)
            intent.putExtra(EXTRA_MODULE, module)
            intent.putExtra(EXTRA_LESSON, lesson)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityQuizBinding
    private lateinit var questions: List<Question>
    private var index = 0
    private var correctCount = 0

    private val radioButtons = mutableListOf<RadioButton>()
    private val checkBoxes = mutableListOf<CheckBox>()
    private var radioGroup: RadioGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val module = intent.getStringExtra(EXTRA_MODULE) ?: ""
        val lesson = intent.getStringExtra(EXTRA_LESSON) ?: ""
        binding.tvLessonTitle.text = "$module • $lesson"

        questions = QuestionRepository(applicationContext).getQuestionsForLesson(module, lesson)

        if (questions.isEmpty()) {
            binding.tvQuestion.text = "لا توجد أسئلة في هذا الدرس."
            binding.bottomBar.visibility = View.GONE
            return
        }

        showQuestion()

        binding.btnValidate.setOnClickListener { validateAnswer() }
        binding.btnNext.setOnClickListener {
            index++
            if (index < questions.size) showQuestion() else showSummary()
        }
    }

    private fun showQuestion() {
        val q = questions[index]
        binding.tvProgress.text = "سؤال ${index + 1} من ${questions.size}   •   الصحيح حتى الآن: $correctCount"
        binding.tvQuestion.text = q.text
        binding.tvResult.visibility = View.GONE
        binding.btnValidate.visibility = View.VISIBLE
        binding.btnValidate.isEnabled = true
        binding.btnNext.visibility = View.GONE

        binding.optionsContainer.removeAllViews()
        radioButtons.clear()
        checkBoxes.clear()
        radioGroup = null

        if (q.isMultiAnswer) {
            q.options.forEachIndexed { i, text ->
                val cb = CheckBox(this)
                cb.text = text
                cb.setTextColor(Color.BLACK)
                binding.optionsContainer.addView(cb)
                checkBoxes.add(cb)
            }
        } else {
            val rg = RadioGroup(this)
            rg.orientation = android.widget.LinearLayout.VERTICAL
            q.options.forEachIndexed { i, text ->
                val rb = RadioButton(this)
                rb.id = i
                rb.text = text
                rb.setTextColor(Color.BLACK)
                rg.addView(rb)
                radioButtons.add(rb)
            }
            binding.optionsContainer.addView(rg)
            radioGroup = rg
        }
    }

    private fun validateAnswer() {
        val q = questions[index]
        val selected: List<Int> = if (q.isMultiAnswer) {
            checkBoxes.indices.filter { checkBoxes[it].isChecked }
        } else {
            val checkedId = radioGroup?.checkedRadioButtonId ?: -1
            if (checkedId >= 0) listOf(checkedId) else emptyList()
        }

        val isCorrect = selected.toSet() == q.correctOptionIds.toSet()
        if (isCorrect) correctCount++

        val correctText = q.correctOptionIds.map { q.options.getOrElse(it) { "" } }.joinToString(" | ")
        binding.tvResult.visibility = View.VISIBLE
        binding.tvResult.text = if (isCorrect) {
            "✅ صحيح!"
        } else {
            "❌ خطأ. الجواب الصحيح: $correctText" +
                if (q.explanation.isNotBlank()) "\n${q.explanation}" else ""
        }
        binding.tvResult.setTextColor(if (isCorrect) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))

        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnNext.text = if (index == questions.size - 1) "إنهاء وعرض النتيجة 🏁" else "السؤال التالي ⟵"
        radioButtons.forEach { it.isEnabled = false }
        checkBoxes.forEach { it.isEnabled = false }
    }

    private fun showSummary() {
        binding.tvProgress.text = ""
        binding.optionsContainer.removeAllViews()
        binding.tvResult.visibility = View.GONE
        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.GONE

        val percent = (correctCount * 100) / questions.size
        binding.tvQuestion.text = "🏁 انتهى التدريب!\n\nالنتيجة: $correctCount من ${questions.size} ($percent%)"
    }
}
