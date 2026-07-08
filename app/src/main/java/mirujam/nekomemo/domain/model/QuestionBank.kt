package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class QuestionBank(
    val id: Long = 0,
    val title: String,
    val categoryId: Long,
    val createdAt: Long = 0
)
