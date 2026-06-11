package mirujam.nekomemo.ui.model

import androidx.compose.runtime.Immutable
import mirujam.nekomemo.domain.model.Question

@Immutable
data class QuestionUiModel(
    val id: Long,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val type: String
) {
    companion object {
        fun fromDomainModel(question: Question): QuestionUiModel = QuestionUiModel(
            id = question.id,
            text = question.text,
            options = question.options,
            correctIndices = question.correctIndices,
            type = question.type
        )

        fun fromDomainModels(questions: List<Question>): List<QuestionUiModel> =
            questions.map { fromDomainModel(it) }
    }
}

data class ScoreModel(
    val correct: Int,
    val wrong: Int,
    val unanswered: Int,
    val total: Int,
    val percentage: Int
) {
    companion object {
        fun calculate(
            questions: List<QuestionUiModel>,
            selectedAnswers: Map<Int, Set<Int>>
        ): ScoreModel {
            var correct = 0
            var wrong = 0
            var unanswered = 0
            questions.forEachIndexed { index, question ->
                val selected = selectedAnswers[index]
                when {
                    selected == null || selected.isEmpty() -> unanswered++
                    selected == question.correctIndices.toSet() -> correct++
                    else -> wrong++
                }
            }
            val total = questions.size
            val percentage = if (total > 0) (correct * 100) / total else 0
            return ScoreModel(correct, wrong, unanswered, total, percentage)
        }
    }
}
