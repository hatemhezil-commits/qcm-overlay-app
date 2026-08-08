package com.qcm.overlay.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
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

    // Always checkboxes, whether the question has one or several correct
    // answers -> the UI never reveals which type it is.
    private val checkBoxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val module = intent.getStringExtra(EXTRA_MODULE) ?: ""
        val lesson = intent.getStringExtra(EXTRA_LESSON) ?: ""
        binding.tvLessonTitle.text = "$module • $lesson"

        questions = QuestionRepository(applicationContext).getQuestionsForLesson(module, lesson)

        if (questions.isEmpty()) {
            binding.tvQuestion.text = "Aucune question dans cette leçon."
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
        binding.tvProgress.text = "Question ${index + 1} sur ${questions.size}   •   Bonnes réponses : $correctCount"
        binding.tvQuestion.text = q.text
        binding.tvResult.visibility = View.GONE
        binding.btnValidate.visibility = View.VISIBLE
        binding.btnValidate.isEnabled = true
        binding.btnNext.visibility = View.GONE

        binding.optionsContainer.removeAllViews()
        checkBoxes.clear()

        q.options.forEach { text ->
            val cb = CheckBox(this)
            cb.text = text
            cb.setTextColor(Color.BLACK)
            binding.optionsContainer.addView(cb)
            checkBoxes.add(cb)
        }
    }

    private fun validateAnswer() {
        val q = questions[index]
        val selected: List<Int> = checkBoxes.indices.filter { checkBoxes[it].isChecked }

        val isCorrect = selected.toSet() == q.correctOptionIds.toSet()
        if (isCorrect) correctCount++

        val correctText = q.correctOptionIds.map { q.options.getOrElse(it) { "" } }.joinToString(" | ")
        binding.tvResult.visibility = View.VISIBLE
        binding.tvResult.text = if (isCorrect) {
            "✅ Correct !"
        } else {
            "❌ Faux. Bonne réponse : $correctText" +
                if (q.explanation.isNotBlank()) "\n${q.explanation}" else ""
        }
        binding.tvResult.setTextColor(if (isCorrect) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))

        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnNext.text = if (index == questions.size - 1) "Terminer et voir le score 🏁" else "Question suivante ⟶"
        checkBoxes.forEach { it.isEnabled = false }
    }

    private fun showSummary() {
        binding.tvProgress.text = ""
        binding.optionsContainer.removeAllViews()
        binding.tvResult.visibility = View.GONE
        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.GONE

        val percent = (correctCount * 100) / questions.size
        binding.tvQuestion.text = "🏁 Entraînement terminé !\n\nScore : $correctCount sur ${questions.size} ($percent%)"
    }
}
