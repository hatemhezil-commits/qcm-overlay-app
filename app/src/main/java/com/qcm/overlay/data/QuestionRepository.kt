package com.qcm.overlay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads every *.json file placed in assets/qcm/ (each file = one module,
 * exactly like the format: { "module": "...", "lessons": { "lessonName": [ ... ] } }),
 * flattens all questions into one list, and hands out random questions while
 * avoiding repeating the last N shown (persisted in SharedPreferences so it
 * survives app restarts).
 */
class QuestionRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("qcm_history", Context.MODE_PRIVATE)
    private val historyLimit = 30 // avoid repeating the last 30 questions shown

    private val allQuestions: List<Question> by lazy { loadAll() }

    /** Map of module name -> its distinct lesson names, for the settings UI. */
    fun getModuleLessonMap(): Map<String, List<String>> =
        allQuestions
            .groupBy { it.module }
            .mapValues { (_, questions) -> questions.map { it.lesson }.distinct().sorted() }
            .toSortedMap()

    /** All questions belonging to exactly one lesson, shuffled — used by the training mode. */
    fun getQuestionsForLesson(module: String, lesson: String): List<Question> =
        allQuestions.filter { it.module == module && it.lesson == lesson }.shuffled()

    fun getRandomQuestion(selectedLessonKeys: Set<String>? = null): Question? {
        val sourcePool = if (selectedLessonKeys.isNullOrEmpty()) {
            allQuestions
        } else {
            allQuestions.filter { it.lessonKey in selectedLessonKeys }
        }
        if (sourcePool.isEmpty()) return null

        val shownRecently = getHistory()
        var pool = sourcePool.filterIndexed { _, q -> !shownRecently.contains(allQuestions.indexOf(q)) }
        if (pool.isEmpty()) {
            pool = sourcePool
            clearHistory()
        }

        val picked = pool.random()
        val pickedGlobalIndex = allQuestions.indexOf(picked)
        addToHistory(pickedGlobalIndex)
        return picked
    }

    private fun loadAll(): List<Question> {
        val result = mutableListOf<Question>()
        val assetManager = context.assets
        val files = assetManager.list("qcm") ?: emptyArray()

        for (fileName in files) {
            if (!fileName.endsWith(".json")) continue
            val jsonText = assetManager.open("qcm/$fileName").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val module = root.optString("module", fileName.removeSuffix(".json"))
            val lessons = root.optJSONObject("lessons") ?: continue

            for (lessonName in lessons.keys()) {
                val questionsArray: JSONArray = lessons.optJSONArray(lessonName) ?: continue
                for (i in 0 until questionsArray.length()) {
                    val q = questionsArray.optJSONObject(i) ?: continue
                    val optionsArray = q.optJSONArray("options") ?: JSONArray()
                    val options = (0 until optionsArray.length()).map { optionsArray.getString(it) }

                    val correctArray = q.optJSONArray("correct_option_ids") ?: JSONArray()
                    val correctIds = (0 until correctArray.length()).map { correctArray.getInt(it) }

                    result.add(
                        Question(
                            module = module,
                            lesson = lessonName,
                            text = q.optString("question"),
                            options = options,
                            correctOptionIds = correctIds,
                            explanation = q.optString("explanation")
                        )
                    )
                }
            }
        }
        return result
    }

    private fun getHistory(): Set<Int> =
        prefs.getStringSet("recent_indices", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()

    private fun addToHistory(index: Int) {
        val history = getHistory().toMutableList()
        history.add(index)
        val trimmed = if (history.size > historyLimit) history.takeLast(historyLimit) else history
        prefs.edit().putStringSet("recent_indices", trimmed.map { it.toString() }.toSet()).apply()
    }

    private fun clearHistory() {
        prefs.edit().remove("recent_indices").apply()
    }
}
