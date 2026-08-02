package mirujam.nekomemo.ui.model

import androidx.compose.runtime.Immutable
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.util.AnswerNormalizer

@Immutable
data class QuestionUiModel(
    val id: Long,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val type: QuestionType,
    val isFavorite: Boolean = false
) {
    companion object {
        fun fromDomainModel(question: Question): QuestionUiModel = QuestionUiModel(
            id = question.id,
            text = question.text,
            options = question.options,
            correctIndices = question.correctIndices,
            type = question.type,
            isFavorite = question.isFavorite
        )

        fun fromDomainModels(questions: List<Question>): List<QuestionUiModel> =
            questions.map { fromDomainModel(it) }
    }
}

@Immutable
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
            selectedAnswers: Map<Int, Set<Int>>,
            textAnswers: Map<Int, List<String>> = emptyMap()
        ): ScoreModel {
            var correct = 0
            var wrong = 0
            var unanswered = 0
            questions.forEachIndexed { index, question ->
                when (evaluateQuestion(question, selectedAnswers[index], textAnswers[index])) {
                    AnswerResult.CORRECT -> correct++
                    AnswerResult.WRONG -> wrong++
                    AnswerResult.UNANSWERED -> unanswered++
                }
            }
            val total = questions.size
            val percentage = if (total > 0) (correct * 100) / total else 0
            return ScoreModel(correct, wrong, unanswered, total, percentage)
        }

        fun evaluateQuestion(
            question: QuestionUiModel,
            selected: Set<Int>?,
            textAnswer: List<String>?
        ): AnswerResult {
            return when (question.type) {
                QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> {
                    val answers = textAnswer.orEmpty()
                    val hasInput = answers.any { it.isNotBlank() }
                    when {
                        !hasInput -> AnswerResult.UNANSWERED
                        isTextAnswerCorrect(question, answers) -> AnswerResult.CORRECT
                        else -> AnswerResult.WRONG
                    }
                }
                else -> {
                    when {
                        selected.isNullOrEmpty() -> AnswerResult.UNANSWERED
                        selected == question.correctIndices.toSet() -> AnswerResult.CORRECT
                        else -> AnswerResult.WRONG
                    }
                }
            }
        }

        fun isTextAnswerCorrect(question: QuestionUiModel, userAnswers: List<String>): Boolean {
            return when (question.type) {
                QuestionType.FILL_BLANK ->
                    AnswerNormalizer.fillBlanksMatch(userAnswers, question.options)
                QuestionType.SHORT_ANSWER -> {
                    val expected = question.options.firstOrNull().orEmpty()
                    val user = userAnswers.firstOrNull().orEmpty()
                    AnswerNormalizer.answersMatch(user, expected)
                }
                else -> false
            }
        }
    }
}

enum class AnswerResult {
    CORRECT,
    WRONG,
    UNANSWERED
}
