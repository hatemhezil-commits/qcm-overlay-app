package com.qcm.overlay.data

data class Question(
    val module: String,
    val lesson: String,
    val text: String,
    val options: List<String>,
    val correctOptionIds: List<Int>,
    val explanation: String
) {
    val isMultiAnswer: Boolean get() = correctOptionIds.size > 1
    val lessonKey: String get() = "$module||$lesson"
}
