package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable
import mirujam.nekomemo.domain.validator.DataValidator

@Immutable
data class ExtractedQuestion(
    val type: QuestionType,
    val content: String,
    val options: List<String>,
    val correctAnswer: String,
    val correctIndices: List<Int>
) {
    companion object {
        fun sanitizeContent(content: String): String = DataValidator.sanitizeContent(content)
    }
}

@Immutable
data class ExtractedQuestionBank(
    val name: String,
    val questions: List<ExtractedQuestion>,
    val category: String? = null,
    val skippedCount: Int = 0,
    val unsupportedTypeCount: Int = 0
)
