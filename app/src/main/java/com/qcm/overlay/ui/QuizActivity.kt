package com.qcm.overlay.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.qcm.overlay.R
import com.qcm.overlay.data.Question
import com.qcm.overlay.data.QuestionRepository
import com.qcm.overlay.databinding.ActivityQuizBinding
import kotlin.random.Random

private enum class AnswerStatus { FULL, PARTIAL, WRONG }

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

    // Per-question answer memory, so "Précédent" can restore what was picked.
    private lateinit var selectedSets: Array<Set<Int>?>
    private lateinit var answeredFlags: BooleanArray

    private val checkBoxes = mutableListOf<CheckBox>()
    private val optionRows = mutableListOf<FrameLayout>()

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

        selectedSets = arrayOfNulls(questions.size)
        answeredFlags = BooleanArray(questions.size)

        showQuestion()

        binding.btnValidate.setOnClickListener { validateAnswer() }
        binding.btnNext.setOnClickListener {
            index++
            if (index < questions.size) showQuestion() else showSummary()
        }
        binding.btnPrevious.setOnClickListener {
            if (index > 0) {
                index--
                answeredFlags[index] = false
                selectedSets[index] = null
                showQuestion()
            }
        }
    }

    private fun classify(selected: Set<Int>, correct: Set<Int>): AnswerStatus {
        if (selected.isEmpty()) return AnswerStatus.WRONG
        if (selected == correct) return AnswerStatus.FULL
        if (selected.all { it in correct }) return AnswerStatus.PARTIAL
        return AnswerStatus.WRONG
    }

    private fun countCorrect(): Int =
        questions.indices.count { i ->
            answeredFlags[i] && classify(selectedSets[i] ?: emptySet(), questions[i].correctOptionIds.toSet()) != AnswerStatus.WRONG
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** A little burst of sparkles falling from the tapped option, purely decorative. */
    private fun burstSparkles(container: FrameLayout) {
        val chars = listOf("✨", "⭐", "💫")
        repeat(5) { i ->
            val sparkle = TextView(this)
            sparkle.text = chars[Random.nextInt(chars.size)]
            sparkle.textSize = 13f
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.marginEnd = dp(6 + Random.nextInt(24))
            sparkle.layoutParams = lp
            sparkle.alpha = 1f
            container.addView(sparkle)
            sparkle.animate()
                .translationY(dp(30 + Random.nextInt(20)).toFloat())
                .alpha(0f)
                .setStartDelay((i * 50).toLong())
                .setDuration(600)
                .withEndAction { container.removeView(sparkle) }
                .start()
        }
    }

    private fun showQuestion() {
        val q = questions[index]
        binding.progressBar.progress = ((index + 1) * 100) / questions.size
        binding.tvProgress.text = "Question ${index + 1} sur ${questions.size}   •   Bonnes réponses : ${countCorrect()}"
        binding.tvQuestion.text = q.text
        binding.tvResult.visibility = View.GONE
        binding.ivSticker.visibility = View.GONE
        binding.tvCaption.visibility = View.GONE
        binding.btnPrevious.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE

        binding.optionsContainer.removeAllViews()
        checkBoxes.clear()
        optionRows.clear()

        val alreadyAnswered = answeredFlags[index]
        val savedSelection = selectedSets[index]

        q.options.forEachIndexed { i, text ->
            val row = FrameLayout(this)
            row.background = ContextCompat.getDrawable(this, R.drawable.bg_option_unselected)
            row.setPadding(dp(16), dp(14), dp(16), dp(14))
            val rowParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rowParams.bottomMargin = dp(10)
            row.layoutParams = rowParams

            val innerContent = LinearLayout(this)
            innerContent.orientation = LinearLayout.HORIZONTAL
            innerContent.gravity = Gravity.CENTER_VERTICAL
            innerContent.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)

            val cb = CheckBox(this)
            cb.text = "${('A' + i)}. $text"
            cb.setTextColor(Color.BLACK)
            cb.buttonDrawable = null
            cb.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val butterfly = TextView(this)
            butterfly.text = "🦋"
            butterfly.textSize = 18f
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
            binding.optionsContainer.addView(row)
            checkBoxes.add(cb)
            optionRows.add(row)
        }

        if (alreadyAnswered && savedSelection != null) {
            checkBoxes.forEachIndexed { i, cb -> cb.isChecked = savedSelection.contains(i) }
            checkBoxes.forEach { it.isEnabled = false }
            colorRows(q, savedSelection)
            showResultUi(classify(savedSelection, q.correctOptionIds.toSet()), animate = false)
            binding.btnValidate.visibility = View.GONE
            binding.btnNext.visibility = View.VISIBLE
            binding.btnNext.text = if (index == questions.size - 1) "Terminer 🏁" else "Suivant ⟶"
        } else {
            binding.btnValidate.visibility = View.VISIBLE
            binding.btnValidate.isEnabled = true
            binding.btnNext.visibility = View.GONE
        }
    }

    /** Colors each option row: green = correctly picked, red = wrongly picked,
     *  orange = correct answer you missed, unchanged otherwise. */
    private fun colorRows(q: Question, selected: Set<Int>) {
        val correct = q.correctOptionIds.toSet()
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
    }

    private fun validateAnswer() {
        val q = questions[index]
        val selected = checkBoxes.indices.filter { checkBoxes[it].isChecked }.toSet()
        selectedSets[index] = selected
        answeredFlags[index] = true

        checkBoxes.forEach { it.isEnabled = false }
        colorRows(q, selected)
        showResultUi(classify(selected, q.correctOptionIds.toSet()), animate = true)

        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnNext.text = if (index == questions.size - 1) "Terminer 🏁" else "Suivant ⟶"
        binding.tvProgress.text = "Question ${index + 1} sur ${questions.size}   •   Bonnes réponses : ${countCorrect()}"
    }

    private fun showResultUi(status: AnswerStatus, animate: Boolean) {
        val counted = status != AnswerStatus.WRONG

        binding.tvResult.visibility = View.VISIBLE
        binding.tvResult.text = if (counted) "✅ Correct !" else "❌ Faux !"
        binding.tvResult.setTextColor(Color.parseColor(if (counted) "#2E7D32" else "#C62828"))

        binding.ivSticker.setImageResource(if (counted) R.drawable.sticker_correct else R.drawable.sticker_wrong)
        binding.tvCaption.text = if (counted) "Nice dida !" else "My baby didn't sleep well"
        binding.ivSticker.visibility = View.VISIBLE
        binding.tvCaption.visibility = View.VISIBLE
        if (animate) {
            binding.ivSticker.translationY = 260f
            binding.tvCaption.alpha = 0f
            binding.ivSticker.animate().translationY(0f).setDuration(380).start()
            binding.tvCaption.animate().alpha(1f).setStartDelay(200).setDuration(200).start()
        } else {
            binding.ivSticker.translationY = 0f
            binding.tvCaption.alpha = 1f
        }
    }

    private fun showSummary() {
        binding.tvProgress.text = ""
        binding.progressBar.progress = 100
        binding.optionsContainer.removeAllViews()
        binding.tvResult.visibility = View.GONE
        binding.ivSticker.visibility = View.GONE
        binding.tvCaption.visibility = View.GONE
        binding.btnValidate.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        binding.btnPrevious.visibility = View.GONE

        val correct = countCorrect()
        val percent = (correct * 100) / questions.size
        binding.tvQuestion.text = "🏁 Entraînement terminé !\n\nScore : $correct sur ${questions.size} ($percent%)"
    }
}
