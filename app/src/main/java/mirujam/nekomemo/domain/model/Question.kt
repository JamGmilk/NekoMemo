package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Question(
    val id: Long = 0,
    val questionBankId: Long,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val type: QuestionType
)
